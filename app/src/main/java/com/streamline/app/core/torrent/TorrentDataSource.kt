package com.streamline.app.core.torrent

import android.net.Uri
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.streamline.app.core.logging.AppLog
import androidx.media3.datasource.TransferListener
import org.libtorrent4j.Priority
import java.io.IOException
import java.io.RandomAccessFile
import kotlin.math.max
import kotlin.math.min





@UnstableApi
class TorrentDataSourceFactory(
    private val playback: TorrentPlayback,
) : DataSource.Factory {
    override fun createDataSource(): DataSource = RoutingTorrentDataSource(playback)
}

@UnstableApi
private class RoutingTorrentDataSource(
    private val playback: TorrentPlayback,
) : DataSource {
    private val listeners = mutableListOf<TransferListener>()
    private var delegate: TorrentDataSource? = null

    override fun addTransferListener(listener: TransferListener) {
        listeners += listener
        delegate?.addTransferListener(listener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val source = TorrentDataSource(resolveStream(dataSpec.uri))
        listeners.forEach(source::addTransferListener)
        delegate = source
        return source.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        delegate?.read(buffer, offset, length) ?: C.RESULT_END_OF_INPUT

    override fun getUri(): Uri? = delegate?.uri

    override fun close() {
        delegate?.close()
        delegate = null
    }

    private fun resolveStream(uri: Uri?): TorrentFileStream {
        if (uri?.host == SUBTITLE_HOST) {
            val index = uri.lastPathSegment?.toIntOrNull()
            if (index != null) return playback.subtitles.getOrElse(index) { playback.video }
        }
        return playback.video
    }

    companion object {
        const val SUBTITLE_HOST = "subtitle"
    }
}







@UnstableApi
class TorrentDataSource(
    private val stream: TorrentFileStream,
) : BaseDataSource(false) {
    private var file: RandomAccessFile? = null
    private var position: Long = 0
    private var readSeekGeneration: Long = 0
    private var bytesRemaining: Long = 0
    private var uri: Uri? = null
    private var lastPriorityAt: Long = 0
    private var priorityStart: Int? = null
    private var priorityEnd: Int? = null
    @Volatile private var closed = false

    
    
    private val readAheadPieces = (READ_AHEAD_BYTES / stream.pieceLength.coerceAtLeast(1))
        .toInt()
        .coerceIn(MIN_READ_AHEAD_PIECES, MAX_READ_AHEAD_PIECES)

    
    private val isAuxiliary = stream.fileSize <= AUXILIARY_STREAM_BYTES ||
        stream.lastPiece - stream.firstPiece <= SMALL_STREAM_PIECES

    




    private val waitTimeoutMillis = if (stream.fileSize <= AUXILIARY_STREAM_BYTES) {
        AUXILIARY_WAIT_TIMEOUT_MILLIS
    } else {
        PIECE_WAIT_TIMEOUT_MILLIS
    }

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        uri = dataSpec.uri

        if (dataSpec.position > stream.fileSize) {
            throw IOException("Torrent stream offset is outside the selected file.")
        }
        if (!stream.file.exists()) {
            waitForFile()
        }

        val accessFile = try {
            RandomAccessFile(stream.file, "r")
        } catch (error: IOException) {
            throw IOException("The torrent file is not available for reading yet.", error)
        }
        accessFile.seek(dataSpec.position)
        file = accessFile
        position = dataSpec.position
        readSeekGeneration = stream.seekGeneration
        val targetPiece = pieceForFileOffset(position)
        val targetCached = runCatching {
            stream.handle.havePiece(targetPiece)
        }.getOrDefault(false)
        AppLog.event(
            "DATA",
            "seek_open",
            fields = mapOf(
                "position" to position,
                "piece" to targetPiece,
                "cached" to targetCached,
                "fileSize" to stream.fileSize,
            ),
        )
        bytesRemaining = if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
            stream.fileSize - dataSpec.position
        } else {
            min(dataSpec.length, stream.fileSize - dataSpec.position)
        }
        closed = false
        if (stream.lastPiece - stream.firstPiece <= SMALL_STREAM_PIECES) {
            
            prioritizeRange(stream.firstPiece, stream.lastPiece)
        } else {
            prioritizeAround(pieceForFileOffset(position), force = true)
        }
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        if (closed) throw IOException("Torrent data source was closed.")

        val absoluteOffset = stream.fileTorrentOffset + position
        val pieceIndex = (absoluteOffset / stream.pieceLength).toInt()
            .coerceIn(stream.firstPiece, stream.lastPiece)
        awaitPiece(pieceIndex)
        if (stream.seekGeneration != readSeekGeneration) {
            throw IOException("Player seek interrupted the torrent read.")
        }

        val pieceStart = pieceIndex.toLong() * stream.pieceLength
        val offsetInPiece = absoluteOffset - pieceStart
        val pieceSize = stream.pieceSize(pieceIndex).toLong()
        val bytesLeftInPiece = (pieceSize - offsetInPiece).coerceAtLeast(1L)
        val toRead = min(length.toLong(), min(bytesRemaining, bytesLeftInPiece)).toInt()

        val accessFile = file ?: throw IOException("Torrent data source is not open.")
        val read = try {
            accessFile.read(buffer, offset, toRead)
        } catch (error: IOException) {
            throw IOException("Could not read from the torrent file.", error)
        }
        if (read < 0) throw IOException("Unexpected end of the torrent file.")
        position += read
        bytesRemaining -= read
        bytesTransferred(read)
        prioritizeAround(pieceForFileOffset(position), force = false)
        return read
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        closed = true
        val accessFile = file
        file = null
        var error: IOException? = null
        try {
            accessFile?.close()
        } catch (io: IOException) {
            error = io
        } finally {
            transferEnded()
        }
        if (error != null) throw error
    }

    private fun pieceForFileOffset(offset: Long): Int =
        stream.timeline(0L).pieceForOffset(offset)

    private fun awaitPiece(pieceIndex: Int) {
        val deadline = SystemClock.elapsedRealtime() + waitTimeoutMillis
        val waitStartedAt = SystemClock.elapsedRealtime()
        var waitLogged = false
        while (!closed) {
            if (Thread.currentThread().isInterrupted) {
                throw IOException("Torrent read was interrupted.")
            }
            if (stream.seekGeneration != readSeekGeneration) {
                throw IOException("Player seek interrupted the torrent read.")
            }
            if (stream.pieceWaiter.isCancelled()) {
                throw IOException("Torrent playback was stopped.")
            }
            if (runCatching { stream.handle.havePiece(pieceIndex) }.getOrDefault(false)) {
                if (waitLogged) {
                    AppLog.event(
                        "DATA",
                        "piece_wait_done",
                        fields = mapOf(
                            "piece" to pieceIndex,
                            "waitedMs" to (SystemClock.elapsedRealtime() - waitStartedAt),
                        ),
                    )
                }
                return
            }
            if (!waitLogged) {
                waitLogged = true
                AppLog.warn(
                    "DATA",
                    "piece_wait_start",
                    fields = mapOf(
                        "piece" to pieceIndex,
                        "position" to position,
                    ),
                )
            }
            val remaining = deadline - SystemClock.elapsedRealtime()
            if (remaining <= 0L) {
                throw IOException("Timed out waiting for torrent pieces. Check peer availability.")
            }
            prioritizeAround(pieceIndex, force = false)
            
            
            stream.pieceWaiter.await(pieceIndex, minOf(remaining, 500L))
            if (Thread.currentThread().isInterrupted) {
                throw IOException("Torrent read was interrupted.")
            }
            if (stream.seekGeneration != readSeekGeneration) {
                throw IOException("Player seek interrupted the torrent read.")
            }
        }
        throw IOException("Torrent playback was stopped.")
    }

    private fun prioritizeAround(startPiece: Int, force: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastPriorityAt < PRIORITY_REFRESH_MILLIS) return
        
        
        val playhead = stream.playheadPiece.takeIf { it in stream.firstPiece..stream.lastPiece }
        val first = if (playhead != null) minOf(playhead, startPiece) else startPiece
        val end = if (playhead != null) maxOf(playhead, startPiece) else startPiece
        prioritizeRange(first, min(stream.lastPiece, end + readAheadPieces), now)
    }

    private fun prioritizeRange(
        startPiece: Int,
        endPiece: Int,
        now: Long = SystemClock.elapsedRealtime(),
    ) {
        lastPriorityAt = now
        runCatching {
            stream.handle.resume()
            val first = max(stream.firstPiece, startPiece)
            val last = min(stream.lastPiece, endPiece)
            
            
            
            if (!isAuxiliary) {
                for (piece in stream.firstPiece..stream.lastPiece) {
                    if (
                        piece !in first..last &&
                        piece !in stream.protectedPieces &&
                        !stream.handle.havePiece(piece)
                    ) {
                        runCatching { stream.handle.piecePriority(piece, Priority.IGNORE) }
                    }
                }
            }
            priorityStart = first
            priorityEnd = last
            runCatching { stream.handle.setSequentialRange(first, last) }
            for (piece in first..last) {
                if (!stream.handle.havePiece(piece)) {
                    runCatching {
                        stream.handle.piecePriority(piece, Priority.TOP_PRIORITY)
                        
                        
                        val deadlineMs = if (piece <= first + 3) 800 else 1_500
                        stream.handle.setPieceDeadline(piece, deadlineMs)
                    }
                }
            }
        }
    }

    private fun waitForFile() {
        val deadline = SystemClock.elapsedRealtime() + min(FILE_WAIT_TIMEOUT_MILLIS, waitTimeoutMillis)
        while (!stream.file.exists()) {
            if (
                closed ||
                stream.pieceWaiter.isCancelled() ||
                SystemClock.elapsedRealtime() > deadline
            ) {
                throw IOException("The torrent file has not been created on disk yet.")
            }
            SystemClock.sleep(150)
        }
    }

    private companion object {
        const val PIECE_WAIT_TIMEOUT_MILLIS = 120_000L
        const val FILE_WAIT_TIMEOUT_MILLIS = 30_000L
        const val PRIORITY_REFRESH_MILLIS = 1_000L
        const val READ_AHEAD_BYTES = 32L * 1024 * 1024
        const val MIN_READ_AHEAD_PIECES = 4
        const val MAX_READ_AHEAD_PIECES = 64

        
        const val SMALL_STREAM_PIECES = 32

        
        const val AUXILIARY_STREAM_BYTES = 8L * 1024 * 1024
        const val AUXILIARY_WAIT_TIMEOUT_MILLIS = 15_000L
    }
}

package com.streamline.app.core.torrent

import android.net.Uri
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import org.libtorrent4j.Priority
import java.io.IOException
import java.io.RandomAccessFile
import kotlin.math.max
import kotlin.math.min

/**
 * Routes Media3 opens to the right file inside the torrent: the video stream for the main URI and
 * sideloaded subtitle streams for `streamline-torrent://subtitle/<index>` URIs.
 */
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

/**
 * A Media3 [DataSource] backed by a live libtorrent file.
 *
 * Media3 asks for ranges as it demuxes; a read blocks until the corresponding torrent piece has
 * passed verification. Seeking also re-prioritises pieces around the new position.
 */
@UnstableApi
class TorrentDataSource(
    private val stream: TorrentFileStream,
) : BaseDataSource(false) {
    private var file: RandomAccessFile? = null
    private var position: Long = 0
    private var bytesRemaining: Long = 0
    private var uri: Uri? = null
    private var lastPriorityAt: Long = 0
    private var priorityStart: Int? = null
    private var priorityEnd: Int? = null
    @Volatile private var closed = false

    // Scale the request window to the piece size: ~32 MB of read-ahead regardless of whether the
    // torrent uses 512 KB pieces (old rips) or 8-16 MB pieces (4K remuxes).
    private val readAheadPieces = (READ_AHEAD_BYTES / stream.pieceLength.coerceAtLeast(1))
        .toInt()
        .coerceIn(MIN_READ_AHEAD_PIECES, MAX_READ_AHEAD_PIECES)

    /**
     * Tiny files (subtitle sidecars) get a short wait. They are merged with the video source, so a
     * sidecar stuck behind a cold swarm would otherwise hold up the first frame; the loader treats
     * the resulting failure as end-of-stream and playback continues without subtitles.
     */
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
        bytesRemaining = if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
            stream.fileSize - dataSpec.position
        } else {
            min(dataSpec.length, stream.fileSize - dataSpec.position)
        }
        closed = false
        if (stream.lastPiece - stream.firstPiece <= SMALL_STREAM_PIECES) {
            // Small sidecars (subtitles): fetch the whole file at once.
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

    private fun pieceForFileOffset(offset: Long): Int {
        if (stream.pieceLength <= 0) return stream.firstPiece
        return ((stream.fileTorrentOffset + offset.coerceIn(0, stream.fileSize)) / stream.pieceLength)
            .toInt()
            .coerceIn(stream.firstPiece, stream.lastPiece)
    }

    private fun awaitPiece(pieceIndex: Int) {
        val deadline = SystemClock.elapsedRealtime() + waitTimeoutMillis
        while (!closed) {
            if (stream.pieceWaiter.isCancelled()) {
                throw IOException("Torrent playback was stopped.")
            }
            if (runCatching { stream.handle.havePiece(pieceIndex) }.getOrDefault(false)) return
            val remaining = deadline - SystemClock.elapsedRealtime()
            if (remaining <= 0L) {
                throw IOException("Timed out waiting for torrent pieces. Check peer availability.")
            }
            prioritizeAround(pieceIndex, force = false)
            // The libtorrent piece alert wakes this wait immediately. The bounded wait is a
            // safety net for a missed alert or a session that is still starting up.
            stream.pieceWaiter.await(pieceIndex, minOf(remaining, 500L))
        }
        throw IOException("Torrent playback was stopped.")
    }

    private fun prioritizeAround(startPiece: Int, force: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastPriorityAt < PRIORITY_REFRESH_MILLIS) return
        prioritizeRange(startPiece, min(stream.lastPiece, startPiece + readAheadPieces), now)
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
            val oldStart = priorityStart
            val oldEnd = priorityEnd
            if (oldStart != null && oldEnd != null) {
                for (piece in oldStart..oldEnd) {
                    if (piece !in first..last && !stream.handle.havePiece(piece)) {
                        runCatching { stream.handle.piecePriority(piece, Priority.DEFAULT) }
                    }
                }
            }
            priorityStart = first
            priorityEnd = last
            runCatching { stream.handle.setSequentialRange(first, stream.lastPiece) }
            for (piece in first..last) {
                if (!stream.handle.havePiece(piece)) {
                    stream.handle.piecePriority(piece, Priority.TOP_PRIORITY)
                    stream.handle.setPieceDeadline(piece, 1_000)
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

        // Streams this small (subtitle sidecars) are boosted in full on open.
        const val SMALL_STREAM_PIECES = 32

        // Anything at or below this size is a sidecar, never the feature video.
        const val AUXILIARY_STREAM_BYTES = 8L * 1024 * 1024
        const val AUXILIARY_WAIT_TIMEOUT_MILLIS = 15_000L
    }
}

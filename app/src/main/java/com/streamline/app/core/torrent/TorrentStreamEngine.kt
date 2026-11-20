package com.streamline.app.core.torrent

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.system.Os
import android.util.Log
import com.streamline.app.core.logging.AppLog
import com.streamline.app.core.util.readBytesBounded
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.libtorrent4j.AddTorrentParams
import org.libtorrent4j.AlertListener
import org.libtorrent4j.Priority
import org.libtorrent4j.alerts.Alert
import org.libtorrent4j.alerts.AlertType
import org.libtorrent4j.alerts.PeerConnectAlert
import org.libtorrent4j.alerts.PieceFinishedAlert
import org.libtorrent4j.alerts.SaveResumeDataAlert
import org.libtorrent4j.alerts.SaveResumeDataFailedAlert
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.TorrentFlags
import org.libtorrent4j.TorrentHandle
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.libtorrent4j.TorrentInfo
import org.libtorrent4j.swig.settings_pack
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.math.min


class TorrentFileStream(
    val handle: TorrentHandle,
    val file: File,
    val fileName: String,
    val fileIndex: Int,
    val fileSize: Long,
    val pieceLength: Int,
    val fileTorrentOffset: Long,
    val firstPiece: Int,
    val lastPiece: Int,
    val pieceWaiter: TorrentPieceWaiter,
    private val pieceSizes: IntArray,
) {
    
    @Volatile var playheadPiece: Int = -1

    
    @Volatile var seekGeneration: Long = 0L

    
    @Volatile var protectedPieces: Set<Int> = emptySet()

    
    fun pieceSize(piece: Int): Int {
        val index = piece - firstPiece
        return if (index in pieceSizes.indices) pieceSizes[index] else pieceLength
    }

    fun timeline(durationMs: Long): PieceTimeline = PieceTimeline(
        fileSize = fileSize,
        durationMs = durationMs,
        pieceLength = pieceLength,
        fileTorrentOffset = fileTorrentOffset,
        firstPiece = firstPiece,
        lastPiece = lastPiece,
    )

    val extension: String
        get() = fileName.substringAfterLast('.', "").lowercase()
}

data class TorrentVideoFile(
    val index: Int,
    val name: String,
    val size: Long,
)

data class TorrentPlayback(
    val video: TorrentFileStream,
    val videoFiles: List<TorrentVideoFile>,
    val artwork: TorrentFileStream?,
    val subtitles: List<TorrentFileStream>,
    val cacheDir: File,
    val pieceWaiter: TorrentPieceWaiter,
) {
    private val seekCounter = java.util.concurrent.atomic.AtomicLong(0L)

    
    fun beginSeek() {
        val generation = seekCounter.incrementAndGet()
        video.seekGeneration = generation
        pieceWaiter.wake()
    }

    val handle: TorrentHandle get() = video.handle
    val file: File get() = video.file
    val fileName: String get() = video.fileName
    val fileIndex: Int get() = video.fileIndex
    val fileSize: Long get() = video.fileSize
    val pieceLength: Int get() = video.pieceLength
    val fileTorrentOffset: Long get() = video.fileTorrentOffset
    val firstPiece: Int get() = video.firstPiece
    val lastPiece: Int get() = video.lastPiece

    val mimeType: String
        get() = when (video.extension) {
            "mp4", "m4v" -> "video/mp4"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            "mov" -> "video/quicktime"
            "ts" -> "video/mp2t"
            else -> "video/*"
        }
}

enum class TorrentFailureKind {
    METADATA,
    NO_PEERS,
    CONTAINER,
    FILE,
    SESSION,
    TIMEOUT,
    UNKNOWN,
}

sealed interface TorrentState {
    data object Idle : TorrentState
    data class FetchingMetadata(val magnetName: String?) : TorrentState
    data class Buffering(
        val fileName: String,
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val targetBytes: Long,
        val downloadRate: Long,
        val peers: Int,
        val seeds: Int = 0,
        val stage: String? = null,
        val playback: TorrentPlayback? = null,
    ) : TorrentState

    data class Ready(val playback: TorrentPlayback) : TorrentState
    data class Failed(
        val message: String,
        val kind: TorrentFailureKind = TorrentFailureKind.UNKNOWN,
    ) : TorrentState
}








class TorrentStreamEngine(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val generation = AtomicLong(0)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
    private val _state = MutableStateFlow<TorrentState>(TorrentState.Idle)
    val state: StateFlow<TorrentState> = _state.asStateFlow()

    
    var onStreamingStateChanged: ((Boolean) -> Unit)? = null

    private val streamsRoot = File(appContext.filesDir, "streams")
    private val cacheMaintenance = scope.launch {
        streamsRoot.listFiles()?.forEach { path ->
            if (!path.name.startsWith(CACHE_DIR_PREFIX)) runCatching { path.deleteRecursively() }
        }
        evictCache(keep = null)
    }
    private var sessionManager: SessionManager? = null
    private var activeJob: Job? = null
    private var teardownJob: Job? = null
    private var telemetryJob: Job? = null
    private var activeHandle: TorrentHandle? = null
    private var activePlayback: TorrentPlayback? = null
    private var backgroundCleanupJob: Job? = null
    private var resumeCheckpointJob: Job? = null
    private var alertListener: AlertListener? = null

    private val pendingResumeSaves = ConcurrentHashMap<String, ResumeSave>()

    
    @Volatile private var phaseStartedAt = 0L
    @Volatile private var firstPeerLogged = false
    @Volatile private var firstPieceLogged = false
    @Volatile private var streamingActive = false

    



    fun updatePlayhead(positionMs: Long, bufferedPositionMs: Long, durationMs: Long) {
        val playback = activePlayback ?: return
        if (durationMs <= 0L || positionMs < 0L) return
        val first = playback.firstPiece
        val last = playback.lastPiece
        if (playback.pieceLength <= 0 || last < first) return
        val clamped = positionMs.coerceAtMost(durationMs)
        val timeline = playback.video.timeline(durationMs)
        val absolute = timeline.pieceForTime(clamped)
        val buffered = (bufferedPositionMs - positionMs).coerceAtLeast(0L)
        val bufferedPiece = timeline.pieceForTime(
            (clamped + buffered).coerceAtMost(durationMs),
        )
        playback.video.playheadPiece = absolute
        applyPlayheadWindow(playback, absolute, bufferedPiece)
    }

    private fun applyPlayheadWindow(
        playback: TorrentPlayback,
        playhead: Int,
        bufferedPiece: Int,
    ) {
        val first = playback.firstPiece
        val last = playback.lastPiece
        if (last < first) return
        val readAhead = (PLAYHEAD_READ_AHEAD_BYTES / playback.pieceLength.coerceAtLeast(1))
            .toInt()
            .coerceIn(MIN_PLAYHEAD_READ_AHEAD_PIECES, MAX_PLAYHEAD_READ_AHEAD_PIECES)
        val windowStart = playhead.coerceIn(first, last)
        
        val prefetchStart = maxOf(windowStart, bufferedPiece)
        val windowEnd = minOf(last, prefetchStart + readAhead)
        runCatching {
            playback.handle.resume()
            for (piece in first..last) {
                if (
                    piece !in windowStart..windowEnd &&
                    piece !in playback.video.protectedPieces &&
                    !playback.handle.havePiece(piece)
                ) {
                    playback.handle.piecePriority(piece, Priority.IGNORE)
                }
            }
            playback.handle.setSequentialRange(windowStart, windowEnd)
            for (piece in windowStart..windowEnd) {
                if (!playback.handle.havePiece(piece)) {
                    playback.handle.piecePriority(piece, Priority.TOP_PRIORITY)
                    playback.handle.setPieceDeadline(
                        piece,
                        if (piece <= windowStart + 3) 800 else 1_500,
                    )
                }
            }
        }
    }

    



    suspend fun loadArtwork(playback: TorrentPlayback): File? = withContext(Dispatchers.IO) {
        val artwork = playback.artwork ?: return@withContext null
        val extension = artwork.fileName.substringAfterLast('.', "jpg").lowercase()
        val target = File(playback.cacheDir, "cover." + extension)
        if (target.isFile && target.length() >= artwork.fileSize) return@withContext target
        val deadline = SystemClock.elapsedRealtime() + ARTWORK_WAIT_TIMEOUT_MS
        while (SystemClock.elapsedRealtime() < deadline) {
            val piecesReady = (artwork.firstPiece..artwork.lastPiece).all { piece ->
                runCatching { artwork.handle.havePiece(piece) }.getOrDefault(false)
            }
            if (piecesReady) break
            runCatching {
                artwork.handle.resume()
                for (piece in artwork.firstPiece..artwork.lastPiece) {
                    artwork.handle.piecePriority(piece, Priority.TOP_PRIORITY)
                }
            }
            delay(250L)
        }
        val piecesReady = (artwork.firstPiece..artwork.lastPiece).all { piece ->
            runCatching { artwork.handle.havePiece(piece) }.getOrDefault(false)
        }
        if (!piecesReady) {
            AppLog.warn(
                "ARTWORK",
                "torrent_cover_timeout",
                fields = mapOf(
                    "file" to artwork.fileName,
                    "pieces" to (artwork.lastPiece - artwork.firstPiece + 1),
                ),
            )
            return@withContext null
        }
        runCatching {
            val temp = File(playback.cacheDir, "cover.tmp")
            artwork.file.copyTo(temp, overwrite = true)
            if (temp.length() <= 0L) return@runCatching null
            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) return@runCatching null
            AppLog.event(
                "ARTWORK",
                "torrent_cover_loaded",
                fields = mapOf("file" to artwork.fileName, "bytes" to target.length()),
            )
            target
        }.getOrNull()
    }

    fun play(
        sourceUri: String,
        directTorrentUrl: String? = null,
        preferredFileIndex: Int? = null,
    ) {
        AppLog.event(
            "STREAM",
            "play_start",
            fields = mapOf(
                "source" to sourceNameFrom(sourceUri),
                "directTorrent" to (directTorrentUrl != null),
                "preferredFileIndex" to preferredFileIndex,
            ),
        )
        activeJob?.cancel()
        val myGeneration = generation.incrementAndGet()
        activeJob = scope.launch {
            val playbackStartedAt = SystemClock.elapsedRealtime()
            phaseStartedAt = playbackStartedAt
            firstPeerLogged = false
            firstPieceLogged = false
            _state.value = TorrentState.FetchingMetadata(sourceNameFrom(sourceUri))
            cacheMaintenance.join()
            
            
            teardownJob?.join()
            clearHandle()
            var cacheDir: File? = null
            try {
                val manager = ensureSession()
                logPhase(playbackStartedAt, "Session ready")
                ensureCurrent(myGeneration)

                val metadata = resolveMetadata(
                    manager = manager,
                    sourceUri = sourceUri,
                    directTorrentUrl = directTorrentUrl,
                )
                logPhase(playbackStartedAt, "Metadata ready")
                ensureCurrent(myGeneration)

                val info = TorrentInfo.bdecode(metadata)
                if (!info.isValid) {
                    throw TorrentFailure("The torrent metadata is invalid.", TorrentFailureKind.METADATA)
                }
                
                
                val saveDir = File(streamsRoot, CACHE_DIR_PREFIX + info.infoHash().toHex())
                    .apply { mkdirs() }
                saveDir.setLastModified(System.currentTimeMillis())
                cacheDir = saveDir
                evictCache(keep = saveDir)
                val videos = videoFiles(info)
                val fileIndex = chooseVideoFile(videos, preferredFileIndex)
                if (fileIndex < 0) {
                    throw TorrentFailure(
                        "This torrent contains no playable video file. " +
                            "Found: " + summarizeContents(info) + ". " +
                            "Supported formats: " + VIDEO_EXTENSIONS.joinToString(", ") + ".",
                        TorrentFailureKind.FILE,
                    )
                }

                val fileStorage = info.files()
                val selectedSize = fileStorage.fileSize(fileIndex)
                if (selectedSize <= 0L) {
                    throw TorrentFailure("The selected video file is empty.", TorrentFailureKind.FILE)
                }
                val pieceLength = info.pieceLength()
                if (pieceLength <= 0) throw TorrentFailure("The torrent has an invalid piece size.")

                val subtitleIndexes = chooseSubtitleFiles(info, fileIndex)
                val artworkIndex = chooseArtworkIndex(info, fileIndex)

                val priorities = Priority.array(Priority.IGNORE, info.numFiles())
                priorities[fileIndex] = Priority.DEFAULT
                subtitleIndexes.forEach { index -> priorities[index] = Priority.DEFAULT }
                if (artworkIndex != null) priorities[artworkIndex] = Priority.DEFAULT
                awaitTorrentRemoved(manager, info.infoHash(), myGeneration)
                val resumeFile = File(saveDir, RESUME_FILE_NAME)
                try {
                    manager.download(
                        info,
                        saveDir,
                        resumeFile.takeIf { it.isFile },
                        priorities,
                        null,
                        TorrentFlags.SEQUENTIAL_DOWNLOAD,
                    )
                } catch (unreadableResume: IllegalArgumentException) {
                    
                    
                    Log.w("StreamlineTorrent", "Discarding unreadable resume data", unreadableResume)
                    resumeFile.delete()
                    manager.download(
                        info,
                        saveDir,
                        null,
                        priorities,
                        null,
                        TorrentFlags.SEQUENTIAL_DOWNLOAD,
                    )
                }

                val handle = awaitHandle(manager, info, myGeneration)
                logPhase(playbackStartedAt, "Handle ready (fastresume=" + resumeFile.isFile + ")")
                activeHandle = handle
                if (generation.get() != myGeneration) {
                    runCatching {
                        handle.pause()
                        manager.remove(handle)
                    }
                    return@launch
                }
                ensureCurrent(myGeneration)

                handle.prioritizeFiles(priorities)
                val firstPiece = fileStorage.pieceIndexAtFile(fileIndex)
                val lastPiece = fileStorage.lastPieceIndexAtFile(fileIndex)
                
                
                handle.setSequentialRange(
                    firstPiece,
                    minOf(lastPiece, firstPiece + INITIAL_PRIORITY_PIECES),
                )
                handle.resume()
                TrackerLists.addPeerDiscoveryTrackers(handle)
                boostInitialWindow(handle, firstPiece, lastPiece)
                boostSubtitlePieces(handle, info, subtitleIndexes)
                setStreamingActive(true)

                val pieceWaiter = TorrentPieceWaiter()
                val videoStream = buildStream(
                    handle = handle,
                    info = info,
                    saveDir = saveDir,
                    fileIndex = fileIndex,
                    pieceWaiter = pieceWaiter,
                ) ?: throw TorrentFailure(
                    "The torrent contains an unsafe file path.",
                    TorrentFailureKind.FILE,
                )
                val subtitleStreams = subtitleIndexes.mapNotNull { index ->
                    buildStream(handle, info, saveDir, index, pieceWaiter)
                }
                val artworkStream = artworkIndex?.let { index ->
                    buildStream(handle, info, saveDir, index, pieceWaiter)
                }
                videoStream.protectedPieces = artworkStream?.let { artwork ->
                    (artwork.firstPiece..artwork.lastPiece).toSet()
                }.orEmpty()
                val playback = TorrentPlayback(
                    video = videoStream,
                    videoFiles = videos,
                    artwork = artworkStream,
                    subtitles = subtitleStreams,
                    cacheDir = saveDir,
                    pieceWaiter = pieceWaiter,
                )
                activePlayback = playback

                
                
                
                applyPlayheadWindow(playback, firstPiece, firstPiece)

                
                
                awaitContainerMetadata(playback, myGeneration)
                logPhase(playbackStartedAt, "Container preflight ready")
                ensureCurrent(myGeneration)
                
                
                applyPlayheadWindow(playback, firstPiece, firstPiece)
                awaitInitialBuffer(playback, myGeneration)
                logPhase(playbackStartedAt, "Initial buffer ready")
                ensureCurrent(myGeneration)
                _state.value = TorrentState.Ready(playback)
                AppLog.event(
                    "STREAM",
                    "ready",
                    fields = mapOf(
                        "file" to playback.fileName,
                        "size" to playback.fileSize,
                        "pieces" to (playback.lastPiece - playback.firstPiece + 1),
                        "subtitles" to subtitleStreams.size,
                    ),
                )
                Log.i(
                    "StreamlineTorrent",
                    "Torrent ready: ${videoStream.fileName} (${videoStream.fileSize} bytes) " +
                        "subtitles=${subtitleStreams.size}",
                )
                telemetryJob?.cancel()
                telemetryJob = scope.launch {
                    while (isActive) {
                        val current = activePlayback ?: break
                        val status = runCatching { current.handle.status() }.getOrNull()
                        if (status != null) {
                            AppLog.event(
                                "STREAM",
                                "telemetry",
                                fields = mapOf(
                                    "state" to _state.value.javaClass.simpleName,
                                    "seeds" to status.numSeeds(),
                                    "peers" to status.numPeers(),
                                    "downloadRate" to status.downloadRate(),
                                    "uploadRate" to status.uploadRate(),
                                    "fileProgress" to runCatching {
                                        current.handle.fileProgress()[current.fileIndex]
                                    }.getOrDefault(0L),
                                    "wanted" to status.progress(),
                                ),
                            )
                        }
                        delay(5_000L)
                    }
                }
            } catch (cancelled: CancellationException) {
                activePlayback?.pieceWaiter?.cancel()
                activeHandle?.let { handle -> runCatching { handle.pause() } }
                setStreamingActive(false)
                persistCache(cacheDir)
                throw cancelled
            } catch (failure: TorrentFailure) {
                activePlayback?.pieceWaiter?.cancel()
                activeHandle?.let { handle -> runCatching { handle.pause() } }
                setStreamingActive(false)
                persistCache(cacheDir)
                Log.w("StreamlineTorrent", "Torrent failed: ${failure.message}")
                AppLog.warn(
                    "STREAM",
                    "failed",
                    message = failure.message.orEmpty(),
                    fields = mapOf("kind" to failure.kind.name),
                )
                if (generation.get() == myGeneration) {
                    _state.value = TorrentState.Failed(
                        failure.message ?: "Torrent playback failed.",
                        failure.kind,
                    )
                }
            } catch (error: UnsatisfiedLinkError) {
                activePlayback?.pieceWaiter?.cancel()
                activeHandle?.let { handle -> runCatching { handle.pause() } }
                setStreamingActive(false)
                persistCache(cacheDir)
                if (generation.get() == myGeneration) {
                    _state.value = TorrentState.Failed(
                        "The torrent engine is not available for this device ABI " +
                            "(${Build.SUPPORTED_ABIS.joinToString()}).",
                    )
                }
            } catch (error: Throwable) {
                activePlayback?.pieceWaiter?.cancel()
                activeHandle?.let { handle -> runCatching { handle.pause() } }
                setStreamingActive(false)
                persistCache(cacheDir)
                Log.w("StreamlineTorrent", "Torrent error", error)
                if (generation.get() == myGeneration) {
                    _state.value = TorrentState.Failed(
                        error.message?.takeIf { it.isNotBlank() }
                            ?: "Torrent playback failed. Check the source and your connection.",
                    )
                }
            } finally {
                if (generation.get() == myGeneration) activeJob = null
            }
        }
    }

    



    fun warmUp() {
        scope.launch { runCatching { ensureSession() } }
    }

    private fun logPhase(playbackStartedAt: Long, label: String) {
        Log.i(
            "StreamlineTorrent",
            label + " in " + (SystemClock.elapsedRealtime() - playbackStartedAt) + " ms",
        )
    }

    private fun setStreamingActive(active: Boolean) {
        if (streamingActive == active) return
        streamingActive = active
        if (active) startResumeCheckpoints() else resumeCheckpointJob?.cancel()
        runCatching { onStreamingStateChanged?.invoke(active) }
    }

    



    private fun startResumeCheckpoints() {
        resumeCheckpointJob?.cancel()
        resumeCheckpointJob = scope.launch {
            while (isActive) {
                delay(RESUME_CHECKPOINT_INTERVAL_MILLIS)
                val handle = activeHandle ?: continue
                val directory = activePlayback?.cacheDir ?: continue
                saveResumeData(handle, directory, flush = false)
            }
        }
    }

    private fun buildStream(
        handle: TorrentHandle,
        info: TorrentInfo,
        saveDir: File,
        fileIndex: Int,
        pieceWaiter: TorrentPieceWaiter,
    ): TorrentFileStream? {
        val fileStorage = info.files()
        val path = fileStorage.filePath(fileIndex)
        val size = fileStorage.fileSize(fileIndex)
        val pieceLength = info.pieceLength()
        val firstPiece = fileStorage.pieceIndexAtFile(fileIndex)
        val lastPiece = fileStorage.lastPieceIndexAtFile(fileIndex)
        val file = File(saveDir, path.replace('\\', '/')).canonicalFile
        val canonicalSaveDir = saveDir.canonicalFile
        if (!file.path.startsWith(canonicalSaveDir.path + File.separator)) return null

        val torrentFile = runCatching { handle.torrentFile() }.getOrNull() ?: info
        val pieceCount = (lastPiece - firstPiece + 1).coerceAtLeast(1)
        val pieceSizes = IntArray(pieceCount) { offset ->
            runCatching { torrentFile.pieceSize(firstPiece + offset) }.getOrDefault(pieceLength)
        }
        return TorrentFileStream(
            handle = handle,
            file = file,
            fileName = path.substringAfterLast('/').substringAfterLast('\\'),
            fileIndex = fileIndex,
            fileSize = size,
            pieceLength = pieceLength,
            fileTorrentOffset = fileStorage.fileOffset(fileIndex),
            firstPiece = firstPiece,
            lastPiece = lastPiece,
            pieceWaiter = pieceWaiter,
            pieceSizes = pieceSizes,
        )
    }

    private suspend fun resolveMetadata(
        manager: SessionManager,
        sourceUri: String,
        directTorrentUrl: String?,
    ): ByteArray {
        val infoHash = infoHashFrom(sourceUri)
        readCachedMetadata(infoHash)?.let { cached ->
            Log.i("StreamlineTorrent", "Metadata loaded from cache")
            return cached
        }

        if (!directTorrentUrl.isNullOrBlank()) {
            val direct = withTimeoutOrNull(DIRECT_METADATA_TIMEOUT_MS) {
                runCatching { downloadTorrentFile(directTorrentUrl) }.getOrNull()
            }
            if (direct != null && isValidMetadata(direct, infoHash)) {
                writeCachedMetadata(infoHash, direct)
                Log.i("StreamlineTorrent", "Metadata loaded from direct torrent URL")
                return direct
            }
        }

        if (!sourceUri.startsWith("magnet:", ignoreCase = true)) {
            return downloadTorrentFile(sourceUri)
        }

        val metadataDir = File(appContext.filesDir, "torrent-metadata").apply { mkdirs() }
        val fetched = manager.fetchMagnet(
            TrackerLists.withTrackers(sourceUri),
            METADATA_TIMEOUT_SECONDS,
            metadataDir,
        ) ?: throw TorrentFailure(
            "Could not fetch torrent metadata. The magnet may have no active peers.",
            TorrentFailureKind.METADATA,
        )
        if (!isValidMetadata(fetched, infoHash)) {
            throw TorrentFailure(
                "The metadata returned by the swarm does not match this magnet. Try another release.",
            )
        }
        writeCachedMetadata(infoHash, fetched)
        return fetched
    }

    private fun infoHashFrom(sourceUri: String): String? = Regex("""(?i)btih:([a-z0-9]+)""")
        .find(sourceUri)
        ?.groupValues
        ?.get(1)
        ?.let(::normalizeInfoHash)

    private fun normalizeInfoHash(raw: String): String {
        val upper = raw.trim().uppercase()
        if (HEX_INFO_HASH.matches(upper)) return upper
        if (BASE32_INFO_HASH.matches(upper)) {
            return runCatching {
                val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
                val output = ByteArray(20)
                var buffer = 0
                var bits = 0
                var index = 0
                upper.forEach { char ->
                    val value = alphabet.indexOf(char)
                    if (value < 0) return upper
                    buffer = (buffer shl 5) or value
                    bits += 5
                    while (bits >= 8) {
                        bits -= 8
                        if (index < output.size) {
                            output[index++] = ((buffer shr bits) and 0xFF).toByte()
                        }
                    }
                }
                if (index != output.size) return upper
                output.joinToString("") { byte -> "%02X".format(byte.toInt() and 0xFF) }
            }.getOrDefault(upper)
        }
        return upper
    }

    private fun readCachedMetadata(infoHash: String?): ByteArray? {
        if (infoHash.isNullOrBlank()) return null
        val file = File(appContext.filesDir, "torrent-metadata/$infoHash.torrent")
        if (!file.exists()) return null
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
        if (!isValidMetadata(bytes, infoHash)) {
            runCatching { file.delete() }
            return null
        }
        return bytes
    }

    private fun writeCachedMetadata(infoHash: String?, bytes: ByteArray) {
        if (infoHash.isNullOrBlank()) return
        runCatching {
            val directory = File(appContext.filesDir, "torrent-metadata").apply { mkdirs() }
            File(directory, "$infoHash.torrent").writeBytes(bytes)
        }
    }

    private fun isValidMetadata(bytes: ByteArray, expectedHash: String?): Boolean = runCatching {
        val info = TorrentInfo.bdecode(bytes)
        if (!info.isValid) return@runCatching false
        if (expectedHash.isNullOrBlank()) return@runCatching true
        info.infoHash().toHex().equals(expectedHash, ignoreCase = true)
    }.getOrDefault(false)

    private suspend fun downloadTorrentFile(url: String): ByteArray = withContext(Dispatchers.IO) {
        if (!url.startsWith("https://", ignoreCase = true)) {
            throw TorrentFailure("The torrent file URL must use HTTPS.")
        }
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/x-bittorrent, application/octet-stream")
            .header("User-Agent", "Streamline/1.0")
            .build()
        val call = httpClient.newCall(request)
        try {
            suspendCancellableCoroutine<ByteArray> { continuation ->
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        response.use { bodyResponse ->
                            try {
                                if (!bodyResponse.isSuccessful) {
                                    throw TorrentFailure(
                                        "The torrent file could not be downloaded (HTTP " +
                                            bodyResponse.code + ").",
                                    )
                                }
                                val body = bodyResponse.body
                                    ?: throw TorrentFailure("The torrent file was empty.")
                                val declared = body.contentLength()
                                if (declared > MAX_TORRENT_FILE_BYTES) {
                                    throw TorrentFailure("That torrent file is unexpectedly large.")
                                }
                                val bytes = body.readBytesBounded(MAX_TORRENT_FILE_BYTES)
                                if (bytes.size > MAX_TORRENT_FILE_BYTES) {
                                    throw TorrentFailure("That torrent file is unexpectedly large.")
                                }
                                if (bytes.isEmpty()) {
                                    throw TorrentFailure("The torrent file was empty.")
                                }
                                if (continuation.isActive) continuation.resume(bytes)
                            } catch (error: Throwable) {
                                if (continuation.isActive) continuation.resumeWithException(error)
                            }
                        }
                    }
                })
            }
        } catch (cancelled: CancellationException) {
            call.cancel()
            throw cancelled
        } catch (failure: TorrentFailure) {
            throw failure
        } catch (io: IOException) {
            throw TorrentFailure("The torrent file could not be downloaded. Check the URL and your connection.")
        }
    }

    fun stop() {
        telemetryJob?.cancel()
        telemetryJob = null
        generation.incrementAndGet()
        activeJob?.cancel()
        activeJob = null
        val handle = activeHandle
        val playback = activePlayback
        playback?.pieceWaiter?.cancel()
        activeHandle = null
        activePlayback = null
        setStreamingActive(false)
        _state.value = TorrentState.Idle
        if (handle != null) {
            teardownJob = scope.launch {
                saveResumeData(handle, playback?.cacheDir)
                runCatching {
                    handle.pause()
                    sessionManager?.remove(handle)
                }
            }
        }
    }

    fun onBackgrounded() {
        backgroundCleanupJob?.cancel()
        backgroundCleanupJob = scope.launch {
            delay(BACKGROUND_CLEANUP_DELAY_MS)
            if (activeJob != null || activePlayback != null) stop()
        }
    }

    fun onForegrounded() {
        backgroundCleanupJob?.cancel()
        backgroundCleanupJob = null
    }

    fun pauseDownloads() {
        val handle = activeHandle ?: return
        scope.launch { runCatching { handle.pause() } }
    }

    fun resumeDownloads() {
        val handle = activeHandle ?: return
        scope.launch { runCatching { handle.resume() } }
    }

    suspend fun clearDownloadedData(): Long = withContext(Dispatchers.IO) {
        val directory = streamsRoot
        val metadataDir = File(appContext.filesDir, "torrent-metadata")
        
        
        val bytes = diskUsage(directory) +
            diskUsage(metadataDir) +
            diskUsage(appContext.cacheDir)
        stop()
        teardownJob?.join()
        directory.deleteRecursively()
        metadataDir.deleteRecursively()
        
        
        appContext.cacheDir.listFiles()?.forEach { child ->
            runCatching { child.deleteRecursively() }
        }
        bytes
    }

    private suspend fun clearHandle() {
        val handle = activeHandle
        val playback = activePlayback
        activeHandle = null
        activePlayback = null
        if (handle != null) {
            saveResumeData(handle, playback?.cacheDir)
            runCatching {
                handle.pause()
                sessionManager?.remove(handle)
            }
        }
    }

    
    private fun persistCache(directory: File?) {
        val handle = activeHandle ?: return
        if (directory == null) return
        scope.launch { saveResumeData(handle, directory) }
    }

    



    private suspend fun saveResumeData(handle: TorrentHandle, directory: File?, flush: Boolean = true) {
        if (directory == null) return
        val infoHash = runCatching { handle.infoHash().toHex() }.getOrNull() ?: return
        val done = CompletableDeferred<Boolean>()
        val request = ResumeSave(infoHash, directory, done)
        pendingResumeSaves[infoHash] = request
        val flags = if (flush) TorrentHandle.FLUSH_DISK_CACHE else TorrentHandle.ONLY_IF_MODIFIED
        val requested = runCatching { handle.saveResumeData(flags) }.isSuccess
        if (!requested) {
            pendingResumeSaves.remove(infoHash, request)
            return
        }
        val saved = withTimeoutOrNull(RESUME_SAVE_TIMEOUT_MILLIS) { done.await() } ?: false
        pendingResumeSaves.remove(infoHash, request)
        if (flush) Log.i("StreamlineTorrent", "Fastresume saved=$saved for $infoHash")
    }

    private fun writeResumeData(alert: SaveResumeDataAlert) {
        val hash = runCatching { alert.handle().infoHash().toHex() }.getOrNull() ?: return
        val request = pendingResumeSaves.remove(hash) ?: return
        val written = runCatching {
            val target = File(request.directory, RESUME_FILE_NAME)
            val temporary = File(request.directory, RESUME_FILE_NAME + ".tmp")
            temporary.writeBytes(AddTorrentParams.writeResumeDataBuf(alert.params()))
            temporary.renameTo(target)
        }.getOrDefault(false)
        request.done.complete(written)
    }

    



    private fun evictCache(keep: File?) {
        val directories = streamsRoot.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith(CACHE_DIR_PREFIX) }
            ?: return
        val limit = cacheLimitBytes()
        var total = directories.sumOf { diskUsage(it) }
        if (total <= limit) return
        directories.filter { it != keep }
            .sortedBy { it.lastModified() }
            .forEach { directory ->
                if (total <= limit) return@forEach
                val size = diskUsage(directory)
                if (runCatching { directory.deleteRecursively() }.getOrDefault(false)) {
                    total -= size
                    Log.i("StreamlineTorrent", "Evicted piece cache ${directory.name} ($size bytes)")
                }
            }
    }

    private fun cacheLimitBytes(): Long {
        val free = runCatching { streamsRoot.usableSpace }.getOrDefault(0L)
        return (free / CACHE_FREE_SPACE_DIVISOR).coerceIn(MIN_CACHE_LIMIT_BYTES, MAX_CACHE_LIMIT_BYTES)
    }

    
    
    private fun diskUsage(directory: File): Long {
        if (!directory.exists()) return 0L
        return directory.walkTopDown()
            .filter { it.isFile }
            .sumOf { file ->
                runCatching { Os.stat(file.absolutePath).st_blocks * 512L }
                    .getOrDefault(file.length())
            }
    }

    @Synchronized
    private fun ensureSession(): SessionManager {
        sessionManager?.let { return it }
        val settings = SessionParams().settings
        settings.setEnableDht(true)
        settings.setEnableLsd(true)
        settings.connectionsLimit(500)
        settings.activeLimit(30)
        settings.activeTrackerLimit(100)
        settings.activeLsdLimit(50)
        settings.maxPeerlistSize(10_000)
        settings.setDhtBootstrapNodes(TrackerLists.DHT_BOOTSTRAP_NODES)
        
        settings.uploadRateLimit(STREAM_UPLOAD_LIMIT_BPS)
        settings.setBoolean(settings_pack.bool_types.enable_upnp.swigValue(), true)
        settings.setBoolean(settings_pack.bool_types.enable_natpmp.swigValue(), true)
        settings.setBoolean(settings_pack.bool_types.announce_to_all_trackers.swigValue(), true)
        settings.setBoolean(settings_pack.bool_types.announce_to_all_tiers.swigValue(), true)
        settings.setBoolean(settings_pack.bool_types.prefer_udp_trackers.swigValue(), true)
        settings.setBoolean(settings_pack.bool_types.use_dht_as_fallback.swigValue(), false)
        settings.setBoolean(settings_pack.bool_types.enable_outgoing_utp.swigValue(), true)
        settings.setBoolean(settings_pack.bool_types.enable_incoming_utp.swigValue(), true)
        settings.setBoolean(settings_pack.bool_types.enable_outgoing_tcp.swigValue(), true)
        settings.setBoolean(settings_pack.bool_types.enable_incoming_tcp.swigValue(), true)
        val manager = SessionManager()
        manager.start(SessionParams(settings))
        if (alertListener == null) {
            val listener = object : AlertListener {
                override fun types(): IntArray = intArrayOf(
                    AlertType.PIECE_FINISHED.swig(),
                    AlertType.PEER_CONNECT.swig(),
                    AlertType.SAVE_RESUME_DATA.swig(),
                    AlertType.SAVE_RESUME_DATA_FAILED.swig(),
                )

                override fun alert(alert: Alert<*>) {
                    val playback = activePlayback
                    when (alert) {
                        is SaveResumeDataAlert -> writeResumeData(alert)

                        is SaveResumeDataFailedAlert -> {
                            val hash = runCatching { alert.handle().infoHash().toHex() }
                                .getOrNull()
                            if (hash != null) {
                                pendingResumeSaves.remove(hash)?.done?.complete(false)
                            }
                        }

                        is PeerConnectAlert -> {
                            if (playback == null) return
                            if (alert.handle().infoHash() != playback.handle.infoHash()) return
                            if (!firstPeerLogged) {
                                firstPeerLogged = true
                                Log.i(
                                    "StreamlineTorrent",
                                    "First peer connected in " +
                                        (SystemClock.elapsedRealtime() - phaseStartedAt) + " ms",
                                )
                            }
                        }

                        is PieceFinishedAlert -> {
                            if (playback == null) return
                            if (alert.handle().infoHash() != playback.handle.infoHash()) return
                            if (!firstPieceLogged) {
                                firstPieceLogged = true
                                Log.i(
                                    "StreamlineTorrent",
                                    "First piece finished in " +
                                        (SystemClock.elapsedRealtime() - phaseStartedAt) + " ms",
                                )
                            }
                            playback.pieceWaiter.signal(alert.pieceIndex())
                        }
                    }
                }
            }
            alertListener = listener
            manager.addListener(listener)
        }
        sessionManager = manager
        return manager
    }

    private suspend fun awaitTorrentRemoved(
        manager: SessionManager,
        infoHash: org.libtorrent4j.Sha1Hash,
        myGeneration: Long,
    ) {
        repeat(20) {
            ensureCurrent(myGeneration)
            val existing = manager.find(infoHash)
            if (existing == null || !existing.isValid) return
            runCatching { existing.pause() }
            runCatching { manager.remove(existing) }
            delay(100)
        }
        throw TorrentFailure("The previous torrent session could not be closed. Try again.")
    }

    private fun ensureCurrent(myGeneration: Long) {
        if (generation.get() != myGeneration) throw CancellationException("Torrent request superseded")
    }

    private suspend fun awaitHandle(
        manager: SessionManager,
        info: TorrentInfo,
        myGeneration: Long,
    ): TorrentHandle {
        repeat(100) {
            ensureCurrent(myGeneration)
            val handle = manager.find(info.infoHash())
            if (handle != null && handle.isValid) return handle
            delay(100)
        }
        throw TorrentFailure("The torrent could not be added to the session.")
    }

    private fun videoFiles(info: TorrentInfo): List<TorrentVideoFile> {
        val files = info.files()
        return (0 until files.numFiles()).mapNotNull { index ->
            if (files.padFileAt(index)) return@mapNotNull null
            val path = files.filePath(index)
            val extension = path.substringAfterLast('.', "").lowercase()
            if (extension !in VIDEO_EXTENSIONS) return@mapNotNull null
            val size = files.fileSize(index)
            if (size <= 0L) return@mapNotNull null
            TorrentVideoFile(
                index = index,
                name = path.substringAfterLast('/').substringAfterLast(java.io.File.separatorChar),
                size = size,
            )
        }
    }

    private fun chooseVideoFile(
        videos: List<TorrentVideoFile>,
        preferredFileIndex: Int?,
    ): Int {
        if (preferredFileIndex != null) {
            videos.firstOrNull { it.index == preferredFileIndex }?.let { return it.index }
        }
        return videos.maxByOrNull { it.size }?.index ?: -1
    }

    private fun summarizeContents(info: TorrentInfo): String {
        val files = info.files()
        return (0 until files.numFiles())
            .filterNot { files.padFileAt(it) }
            .take(12)
            .map { files.fileName(it) }
            .joinToString(", ")
            .ifBlank { "an empty torrent" }
    }

    



    



    private fun chooseArtworkIndex(info: TorrentInfo, videoIndex: Int): Int? {
        val files = info.files()
        data class Candidate(val index: Int, val score: Int, val size: Long)
        val candidates = mutableListOf<Candidate>()
        for (index in 0 until files.numFiles()) {
            if (index == videoIndex || files.padFileAt(index)) continue
            val path = files.filePath(index)
            val extension = path.substringAfterLast('.', "").lowercase()
            if (extension !in IMAGE_EXTENSIONS) continue
            val size = files.fileSize(index)
            if (size < MIN_ARTWORK_BYTES || size > MAX_ARTWORK_BYTES) continue
            val name = path.substringAfterLast('/').substringAfterLast('\\').lowercase()
            val score = when {
                name.contains("poster") || name.contains("cover") ||
                    name.contains("folder") || name.contains("front") -> 3
                name.contains("fanart") || name.contains("artwork") ||
                    name.contains("backdrop") -> 2
                else -> 1
            }
            candidates += Candidate(index, score, size)
        }
        return candidates
            .sortedWith(compareByDescending<Candidate> { it.score }.thenByDescending { it.size })
            .firstOrNull()
            ?.index
    }

    private fun chooseSubtitleFiles(info: TorrentInfo, videoIndex: Int): List<Int> {
        val files = info.files()
        val videoPath = files.filePath(videoIndex)
        val videoStem = videoPath.substringAfterLast('/').substringAfterLast('\\')
            .substringBeforeLast('.')
            .lowercase()
        val videoDir = videoPath.substringBeforeLast('/', "")

        data class Candidate(val index: Int, val score: Int)
        val candidates = mutableListOf<Candidate>()
        for (index in 0 until files.numFiles()) {
            if (index == videoIndex || files.padFileAt(index)) continue
            val path = files.filePath(index)
            val extension = path.substringAfterLast('.', "").lowercase()
            if (extension !in SUBTITLE_EXTENSIONS) continue
            if (files.fileSize(index) > MAX_SUBTITLE_FILE_BYTES) continue
            val name = path.substringAfterLast('/').substringAfterLast('\\')
            val stem = name.substringBeforeLast('.').lowercase()
            val dir = path.substringBeforeLast('/', "")
            val score = when {
                stem.startsWith(videoStem) && dir == videoDir -> 0
                dir == videoDir -> 1
                else -> 2
            }
            candidates += Candidate(index, score)
        }
        return candidates.sortedWith(compareBy({ it.score }, { it.index }))
            .take(MAX_SUBTITLE_FILES)
            .map { it.index }
    }

    private suspend fun awaitInitialBuffer(playback: TorrentPlayback, myGeneration: Long) {
        val targetBytes = (playback.fileSize * BUFFER_FILE_FRACTION).toLong()
            .coerceIn(MIN_BUFFER_BYTES, MAX_BUFFER_BYTES)
            .coerceAtMost(playback.fileSize)
        val startedAt = SystemClock.elapsedRealtime()
        var lastProgressAt = startedAt
        var lastBytes = 0L

        while (scope.isActive) {
            ensureCurrent(myGeneration)
            val status = runCatching { playback.handle.status() }.getOrNull()
            val fileProgress = runCatching {
                playback.handle.fileProgress()[playback.fileIndex]
            }.getOrDefault(lastBytes)
            val elapsed = SystemClock.elapsedRealtime() - startedAt
            val progress = if (targetBytes <= 0) 1f else (fileProgress.toFloat() / targetBytes).coerceIn(0f, 1f)
            _state.value = TorrentState.Buffering(
                fileName = playback.fileName,
                progress = progress,
                downloadedBytes = fileProgress,
                totalBytes = playback.fileSize,
                targetBytes = targetBytes,
                downloadRate = status?.downloadRate()?.toLong() ?: 0L,
                peers = status?.numPeers() ?: 0,
                seeds = status?.numSeeds() ?: 0,
                stage = "opening buffer",
                playback = playback,
            )

            val firstPieceReady = runCatching { playback.handle.havePiece(playback.firstPiece) }
                .getOrDefault(false)
            if (fileProgress >= targetBytes && firstPieceReady) return

            if (fileProgress > lastBytes) {
                lastBytes = fileProgress
                lastProgressAt = SystemClock.elapsedRealtime()
            }
            if (elapsed > BUFFERING_TIMEOUT_MILLIS) {
                throw TorrentFailure(
                    "The torrent is taking too long to buffer. Try a source with more peers.",
                    TorrentFailureKind.TIMEOUT,
                )
            }
            if (SystemClock.elapsedRealtime() - lastProgressAt > STALL_TIMEOUT_MILLIS) {
                val noPeers = (status?.numPeers() ?: 0) == 0
                throw TorrentFailure(
                    if (noPeers) {
                        "No peers were found for this torrent. Check the link or try another source."
                    } else {
                        "Download stalled before playback could start. Please retry."
                    },
                    if (noPeers) TorrentFailureKind.NO_PEERS else TorrentFailureKind.TIMEOUT,
                )
            }
            
            boostInitialWindow(playback.handle, playback.firstPiece, playback.lastPiece)
            delay(500)
        }
    }

    private suspend fun awaitContainerMetadata(
        playback: TorrentPlayback,
        myGeneration: Long,
    ) {
        if (Mp4MetadataInspector.isMp4(playback.fileName)) {
            awaitFileRange(
                playback = playback,
                start = 0L,
                end = minOf(playback.fileSize, PREFLIGHT_HEAD_BYTES),
                myGeneration = myGeneration,
                publishState = false,
            )
        }
        val layout = Mp4MetadataInspector.inspect(playback.file, playback.fileSize)
        Log.i(
            "StreamlineTorrent",
            "MP4 layout: moov=" + (layout.moovOffset != null) +
                " size=" + layout.moovSize + " needsTail=" + layout.needsTail,
        )
        if (!layout.isMp4) return

        val headMoov = layout.moovOffset
        if (headMoov != null) {
            val moovSize = layout.moovSize ?: return
            val end = headMoov + moovSize
            if (end > playback.fileSize) {
                throw TorrentFailure("The MP4 metadata range is outside the video file.")
            }
            awaitFileRange(
                playback = playback,
                start = headMoov,
                end = end,
                myGeneration = myGeneration,
                publishState = false,
            )
            return
        }
        if (!layout.needsTail) return

        val tailStart = (playback.fileSize - Mp4MetadataInspector.TAIL_WINDOW_BYTES)
            .coerceAtLeast(0L)
        val tailFirstPiece = (
            (playback.fileTorrentOffset + tailStart) / playback.pieceLength
            ).toInt().coerceIn(playback.firstPiece, playback.lastPiece)
        val needed = tailFirstPiece..playback.lastPiece
        Log.i(
            "StreamlineTorrent",
            "Waiting for MP4 tail pieces " + tailFirstPiece + ".." + playback.lastPiece + " for moov",
        )
        var waited = 0L
        while (waited < CONTAINER_METADATA_TIMEOUT_MILLIS) {
            ensureCurrent(myGeneration)
            val missing = needed.firstOrNull { piece ->
                !runCatching { playback.handle.havePiece(piece) }.getOrDefault(false)
            }
            if (missing == null) break
            needed.forEach { piece ->
                if (!runCatching { playback.handle.havePiece(piece) }.getOrDefault(false)) {
                    runCatching {
                        playback.handle.piecePriority(piece, Priority.TOP_PRIORITY)
                        playback.handle.setPieceDeadline(piece, 1_000)
                    }
                }
            }
            boostInitialWindow(playback.handle, playback.firstPiece, playback.lastPiece)
            val status = playback.handle.status()
            val progress = runCatching {
                playback.handle.fileProgress()[playback.fileIndex]
            }.getOrDefault(0L)
            _state.value = TorrentState.Buffering(
                fileName = playback.fileName,
                progress = 1f,
                downloadedBytes = progress,
                totalBytes = playback.fileSize,
                targetBytes = 0L,
                downloadRate = status.downloadRate().toLong(),
                peers = status.numPeers(),
                seeds = status.numSeeds(),
                stage = "container metadata",
                playback = playback,
            )
            delay(200)
            waited += 200
        }
        val after = Mp4MetadataInspector.inspect(playback.file, playback.fileSize)
        Log.i(
            "StreamlineTorrent",
            "MP4 tail check: moov=" + (after.moovOffset != null) + " waited=" + waited + "ms",
        )
        if (after.moovOffset == null) {
            throw TorrentFailure(
                "This MP4's metadata could not be read. The release may use an unsupported " +
                    "container, or the pieces needed to open it are unavailable. Try another release.",
                TorrentFailureKind.CONTAINER,
            )
        }
    }

    private suspend fun awaitFileRange(
        playback: TorrentPlayback,
        start: Long,
        end: Long,
        myGeneration: Long,
        publishState: Boolean = true,
    ) {
        val firstPiece = pieceForFileOffset(playback, start)
        val lastPiece = pieceForFileOffset(playback, end.coerceAtLeast(start + 1) - 1)
        val needed = firstPiece..lastPiece
        var waited = 0L
        while (waited < CONTAINER_METADATA_TIMEOUT_MILLIS) {
            ensureCurrent(myGeneration)
            val missing = needed.firstOrNull { piece ->
                !runCatching { playback.handle.havePiece(piece) }.getOrDefault(false)
            }
            if (missing == null) return
            boostInitialWindow(playback.handle, playback.firstPiece, playback.lastPiece)
            val status = playback.handle.status()
            val progress = runCatching {
                playback.handle.fileProgress()[playback.fileIndex]
            }.getOrDefault(0L)
            if (publishState) {
                _state.value = TorrentState.Buffering(
                    fileName = playback.fileName,
                    progress = 1f,
                    downloadedBytes = progress,
                    totalBytes = playback.fileSize,
                    targetBytes = 0L,
                    downloadRate = status.downloadRate().toLong(),
                    peers = status.numPeers(),
                    seeds = status.numSeeds(),
                    stage = "container metadata",
                    playback = playback,
                )
            }
            delay(200)
            waited += 200
        }
        Log.w(
            "StreamlineTorrent",
            "MP4 metadata range incomplete after " + waited + "ms; player will continue requesting it",
        )
    }

    private fun pieceForFileOffset(playback: TorrentPlayback, offset: Long): Int {
        if (playback.pieceLength <= 0) return playback.firstPiece
        return ((playback.fileTorrentOffset + offset) / playback.pieceLength)
            .toInt()
            .coerceIn(playback.firstPiece, playback.lastPiece)
    }

    private fun boostInitialWindow(handle: TorrentHandle, firstPiece: Int, lastPiece: Int) {
        runCatching {
            val headEnd = min(lastPiece, firstPiece + INITIAL_PRIORITY_PIECES)
            for (piece in firstPiece..headEnd) {
                if (!handle.havePiece(piece)) {
                    handle.piecePriority(piece, Priority.TOP_PRIORITY)
                    handle.setPieceDeadline(piece, 1_000)
                }
            }
            
            
        }
    }

    




    private fun boostSubtitlePieces(
        handle: TorrentHandle,
        info: TorrentInfo,
        subtitleIndexes: List<Int>,
    ) {
        if (subtitleIndexes.isEmpty()) return
        runCatching {
            val files = info.files()
            subtitleIndexes.forEach { index ->
                val first = files.pieceIndexAtFile(index)
                val last = files.lastPieceIndexAtFile(index)
                for (piece in first..last) {
                    if (!handle.havePiece(piece)) {
                        handle.piecePriority(piece, Priority.TOP_PRIORITY)
                        handle.setPieceDeadline(piece, 1_000)
                    }
                }
            }
        }
    }

    private fun sourceNameFrom(source: String): String? {
        if (source.startsWith("magnet:", ignoreCase = true)) {
            return source
                .substringAfter("dn=", missingDelimiterValue = "")
                .substringBefore("&")
                .replace("+", " ")
                .takeIf { it.isNotBlank() }
        }
        return runCatching { URI(source).path }
            .getOrNull()
            ?.substringAfterLast('/')
            ?.takeIf { it.isNotBlank() }
    }

    private class TorrentFailure(
        message: String,
        val kind: TorrentFailureKind = TorrentFailureKind.UNKNOWN,
    ) : Exception(message)

    private class ResumeSave(
        val infoHash: String,
        val directory: File,
        val done: CompletableDeferred<Boolean>,
    )

    private companion object {
        const val PREFLIGHT_HEAD_BYTES = 256L * 1024L
        const val PLAYHEAD_READ_AHEAD_BYTES = 32L * 1024L * 1024L
        const val MIN_PLAYHEAD_READ_AHEAD_PIECES = 4
        const val MAX_PLAYHEAD_READ_AHEAD_PIECES = 64
        
        
        val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "webm", "m4v", "mov", "ts")
        val SUBTITLE_EXTENSIONS = setOf("srt", "vtt", "ass", "ssa")
        val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
        const val MIN_ARTWORK_BYTES = 8L * 1024
        const val MAX_ARTWORK_BYTES = 12L * 1024 * 1024
        const val ARTWORK_WAIT_TIMEOUT_MS = 15_000L
        const val MAX_SUBTITLE_FILES = 8
        const val MAX_SUBTITLE_FILE_BYTES = 4L * 1024 * 1024
        const val METADATA_TIMEOUT_SECONDS = 45
        const val DIRECT_METADATA_TIMEOUT_MS = 4_000L
        const val BACKGROUND_CLEANUP_DELAY_MS = 60_000L
        const val CONTAINER_METADATA_TIMEOUT_MILLIS = 15_000L
        const val MAX_TORRENT_FILE_BYTES = 8L * 1024L * 1024L
        val HEX_INFO_HASH = Regex("[A-F0-9]{40}")
        val BASE32_INFO_HASH = Regex("[A-Z2-7]{32}")

        
        
        const val BUFFER_FILE_FRACTION = 0.002
        const val MIN_BUFFER_BYTES = 1L * 1024 * 1024
        const val MAX_BUFFER_BYTES = 6L * 1024 * 1024

        const val BUFFERING_TIMEOUT_MILLIS = 5 * 60 * 1000L
        const val STALL_TIMEOUT_MILLIS = 75 * 1000L
        const val INITIAL_PRIORITY_PIECES = 24
        const val STREAM_UPLOAD_LIMIT_BPS = 512 * 1024

        
        
        const val CACHE_DIR_PREFIX = "cache-"
        const val RESUME_FILE_NAME = ".fastresume"
        const val RESUME_SAVE_TIMEOUT_MILLIS = 3_000L
        const val RESUME_CHECKPOINT_INTERVAL_MILLIS = 60_000L
        const val CACHE_FREE_SPACE_DIVISOR = 8L
        const val MIN_CACHE_LIMIT_BYTES = 512L * 1024 * 1024
        const val MAX_CACHE_LIMIT_BYTES = 4L * 1024 * 1024 * 1024
    }
}

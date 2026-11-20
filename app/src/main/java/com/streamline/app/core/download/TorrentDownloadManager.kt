package com.streamline.app.core.download

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.streamline.app.core.logging.AppLog
import com.streamline.app.core.torrent.TrackerLists
import com.streamline.app.domain.model.PlaybackRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.libtorrent4j.AddTorrentParams
import org.libtorrent4j.Priority
import org.libtorrent4j.AlertListener
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo
import org.libtorrent4j.TorrentFlags
import org.libtorrent4j.TorrentStatus
import org.libtorrent4j.alerts.Alert
import org.libtorrent4j.alerts.AlertType
import org.libtorrent4j.alerts.FileErrorAlert
import org.libtorrent4j.alerts.SaveResumeDataAlert
import org.libtorrent4j.alerts.SaveResumeDataFailedAlert
import org.libtorrent4j.alerts.TorrentErrorAlert
import org.libtorrent4j.swig.settings_pack
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap





class TorrentDownloadManager(private val appContext: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store = DownloadStore(appContext)
    private val active = ConcurrentHashMap<String, ActiveDownload>()
    private val exporting = ConcurrentHashMap.newKeySet<String>()
    private val commandMutex = Mutex()
    
    
    private val privateDownloadsRoot = File(appContext.filesDir, "downloads")
    private val publicDownloadsRoot = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        "Streamline",
    )
    private val metadataDir = File(appContext.cacheDir, "download-metadata")
    private val _downloads = MutableStateFlow<List<DownloadItem>>(emptyList())
    val downloads: StateFlow<List<DownloadItem>> = _downloads.asStateFlow()

    private var sessionManager: SessionManager? = null
    private var alertListener: AlertListener? = null
    private var lastResumeSaveAt = 0L
    private val pendingResumeSaves = ConcurrentHashMap<String, CompletableDeferred<ByteArray>>()
    private var foregroundStarted = false
    private var lastPersistAt = 0L

    @Volatile
    private var streamingThrottleActive = false

    init {
        val restored = store.load()
        val normalized = sort(
            restored.map { item ->
                if (item.status.isActive) {
                    item.copy(status = DownloadStatus.PAUSED, downloadRate = 0L, peers = 0)
                } else {
                    item
                }
            },
        )
        _downloads.value = normalized
        scope.launch {
            recoverOrphanedFiles(normalized)
            if (_downloads.value != normalized) persist()
            monitorLoop()
        }
    }

    private fun recoverOrphanedFiles(known: List<DownloadItem>) {
        val knownIds = known.mapTo(HashSet()) { it.id }
        val recovered = (publicDownloadsRoot.listFiles().orEmpty().asSequence() +
            privateDownloadsRoot.listFiles().orEmpty().asSequence())
            .filter { it.isDirectory && it.name !in knownIds }
            .filter { directory -> completeMarkerFile(directory).exists() }
            ?.mapNotNull { directory ->
                val video = directory.walkTopDown()
                    .filter { it.isFile && it.length() > 0L }
                    .filter { file ->
                        file.extension.lowercase() in setOf(
                            "mp4", "mkv", "webm", "m4v", "mov", "avi", "ts",
                        )
                    }
                    .maxByOrNull { it.length() }
                    ?: return@mapNotNull null
                DownloadItem(
                    id = directory.name,
                    title = video.nameWithoutExtension,
                    magnetUrl = "",
                    sourceLabel = "Recovered file",
                    status = DownloadStatus.COMPLETED,
                    downloadedBytes = video.length(),
                    totalBytes = video.length(),
                    savedPath = video.absolutePath,
                    publicUri = fileProviderUri(video).takeIf { isPublicFile(video) },
                )
            }
            ?.toList()
            .orEmpty()
        if (recovered.isNotEmpty()) {
            _downloads.update { current -> sort(recovered + current) }
        }
    }

    fun isDirectStorageAvailable(): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                Environment.isExternalStorageManager()
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ->
                ContextCompat.checkSelfPermission(
                    appContext,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                ) == PackageManager.PERMISSION_GRANTED
            else -> false
        }
    }

    private fun downloadRoot(): File {
        val root = if (isDirectStorageAvailable()) publicDownloadsRoot else privateDownloadsRoot
        root.mkdirs()
        return root
    }

    private fun completeMarkerFile(directory: File): File = File(directory, COMPLETE_MARKER)

    private fun isPublicFile(file: File): Boolean = runCatching {
        file.canonicalPath.startsWith(publicDownloadsRoot.canonicalPath + File.separator)
    }.getOrDefault(false)

    private fun fileProviderUri(file: File): String? = runCatching {
        FileProvider.getUriForFile(
            appContext,
            appContext.packageName + ".fileprovider",
            file,
        ).toString()
    }.getOrNull()

    fun enqueue(
        request: PlaybackRequest,
        selectedFileIndexes: List<Int> = emptyList(),
    ): String {
        val id = infoHash(request.magnetUrl) ?: UUID.randomUUID().toString()
        AppLog.event(
            "DOWNLOAD",
            "enqueue",
            fields = mapOf(
                "id" to id,
                "title" to request.title,
                "quality" to request.quality,
                "source" to request.sourceLabel,
                "selectedFiles" to selectedFileIndexes.size,
            ),
        )
        scope.launch {
            commandMutex.withLock { enqueueLocked(request, id, selectedFileIndexes) }
        }
        return id
    }

    suspend fun inspectFiles(request: PlaybackRequest): Result<List<DownloadFileOption>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val manager = ensureSession()
                metadataDir.mkdirs()
                val metadata = manager.fetchMagnet(
                    TrackerLists.withTrackers(request.magnetUrl),
                    METADATA_TIMEOUT_SECONDS,
                    metadataDir,
                ) ?: error("Could not find peers for this magnet.")
                val info = TorrentInfo.bdecode(metadata)
                if (!info.isValid) error("The torrent metadata is invalid.")
                val files = info.files()
                (0 until files.numFiles()).mapNotNull { index ->
                    if (files.padFileAt(index)) return@mapNotNull null
                    val path = files.filePath(index)
                    val extension = path.substringAfterLast('.', "").lowercase()
                    DownloadFileOption(
                        index = index,
                        path = path,
                        size = files.fileSize(index),
                        isVideo = extension in VIDEO_EXTENSIONS,
                        isSubtitle = extension in SUBTITLE_EXTENSIONS,
                    )
                }
            }
        }

    private fun enqueueLocked(
        request: PlaybackRequest,
        id: String,
        selectedFileIndexes: List<Int>,
    ) {
        val existing = current(id)
        if (existing != null) {
            AppLog.event(
                "DOWNLOAD",
                "enqueue_existing",
                fields = mapOf("id" to id, "status" to existing.status.name),
            )
            if (existing.canResume) resumeLocked(id)
            return
        }
        val item = DownloadItem(
            id = id,
            title = request.title,
            magnetUrl = request.magnetUrl,
            posterUrl = request.posterUrl,
            sourceLabel = request.sourceLabel,
            quality = request.quality,
            selectedFileIndexes = selectedFileIndexes,
            status = DownloadStatus.QUEUED,
        )
        _downloads.update { current -> sort(listOf(item) + current) }
        persist()
        startLocked(id)
    }

    fun pause(id: String) {
        scope.launch { commandMutex.withLock { pauseLocked(id) } }
    }

    private suspend fun pauseLocked(id: String) {
        val item = current(id) ?: return
        if (!item.canPause) return
        val session = active[id]
        session?.cancelled = true
        session?.job?.cancel()
        session?.job = null
        session?.handle?.let { handle ->
            saveResumeData(handle, downloadDirectory(id))
            runCatching { handle.pause() }
        }
        if (session?.handle == null) active.remove(id)
        update(id) {
            it.copy(status = DownloadStatus.PAUSED, downloadRate = 0L, peers = 0)
        }
        persist()
        syncForegroundService()
    }

    fun resume(id: String) {
        scope.launch { commandMutex.withLock { resumeLocked(id) } }
    }

    private fun resumeLocked(id: String) {
        val item = current(id) ?: return
        if (item.isComplete) return
        val session = active[id]
        val handle = session?.handle
        if (handle != null) {
            runCatching { handle.resume() }
            update(id) {
                it.copy(
                    status = DownloadStatus.DOWNLOADING,
                    error = null,
                    downloadRate = 0L,
                    peers = 0,
                )
            }
            persist()
            return
        }
        active.remove(id)
        update(id) {
            it.copy(status = DownloadStatus.QUEUED, error = null, downloadRate = 0L, peers = 0)
        }
        persist()
        startLocked(id)
    }

    fun remove(id: String) {
        scope.launch { commandMutex.withLock { removeLocked(id) } }
    }

    private fun removeLocked(id: String) {
        val session = active.remove(id)
        session?.cancelled = true
        session?.job?.cancel()
        session?.handle?.let { handle -> runCatching { session.manager?.remove(handle) } }
        current(id)?.publicUri?.let { uri ->
            runCatching {
                appContext.contentResolver.delete(Uri.parse(uri), null, null)
            }
        }
        safeDeleteDownloadDir(privateDownloadsRoot, id)
        safeDeleteDownloadDir(publicDownloadsRoot, id)
        _downloads.update { current -> current.filterNot { it.id == id } }
        persist()
        syncForegroundService()
    }

    private fun downloadDirectory(id: String): File? =
        listOf(publicDownloadsRoot, privateDownloadsRoot)
            .map { File(it, id) }
            .firstOrNull { it.isDirectory }

    private suspend fun saveResumeData(handle: TorrentHandle, directory: File?): Boolean {
        if (directory == null) return false
        val hash = runCatching { handle.infoHash().toHex() }.getOrNull() ?: return false
        val deferred = CompletableDeferred<ByteArray>()
        pendingResumeSaves[hash] = deferred
        val requested = runCatching { handle.saveResumeData() }.isSuccess
        if (!requested) {
            pendingResumeSaves.remove(hash)
            return false
        }
        val bytes = withTimeoutOrNull(RESUME_SAVE_TIMEOUT_MILLIS) { deferred.await() }
        pendingResumeSaves.remove(hash)
        if (bytes == null || bytes.isEmpty()) return false
        return runCatching {
            File(directory, RESUME_FILE_NAME).writeBytes(bytes)
            true
        }.getOrDefault(false)
    }

    private suspend fun failActiveByHash(hash: String, message: String) {
        commandMutex.withLock {
            val entry = active.entries.firstOrNull { (_, session) ->
                runCatching { session.handle?.infoHash()?.toHex() }.getOrNull()
                    ?.equals(hash, ignoreCase = true) == true
            } ?: return@withLock
            val (id, session) = entry
            session.cancelled = true
            session.job?.cancel()
            session.handle?.let { handle -> runCatching { session.manager?.remove(handle) } }
            session.handle = null
            active.remove(id)
            update(id) {
                it.copy(
                    status = DownloadStatus.FAILED,
                    error = message,
                    downloadRate = 0L,
                    peers = 0,
                )
            }
            persist()
            syncForegroundService()
        }
    }

    fun shutdown() {
        scope.launch {
            commandMutex.withLock {
                active.values.toList().forEach { session ->
                    session.cancelled = true
                    session.job?.cancel()
                    session.handle?.let { handle ->
                        saveResumeData(handle, downloadDirectory(session.id))
                        runCatching { handle.pause() }
                        runCatching { session.manager?.remove(handle) }
                    }
                    session.handle = null
                }
                active.clear()
                runCatching { sessionManager?.stop() }
                sessionManager = null
                alertListener = null
                foregroundStarted = false
                DownloadForegroundService.stop(appContext)
            }
        }
    }

    private fun markComplete(id: String) {
        listOf(privateDownloadsRoot, publicDownloadsRoot).forEach { root ->
            val directory = File(root, id)
            if (directory.isDirectory) {
                runCatching {
                    completeMarkerFile(directory).writeText("complete")
                }
            }
        }
    }

    private fun safeDeleteDownloadDir(root: File, id: String) {
        if (id.isBlank()) return
        runCatching {
            val canonicalRoot = root.canonicalFile
            val target = File(canonicalRoot, id).canonicalFile
            if (target.parentFile == canonicalRoot && target.name == id) {
                target.deleteRecursively()
            }
        }
    }

    private fun extensionOf(path: String): String =
        path.substringAfterLast('.', "").lowercase()

    private fun defaultDownloadIndexes(info: TorrentInfo): List<Int> {
        val files = info.files()
        val videos = mutableListOf<Int>()
        val subtitles = mutableListOf<Int>()
        for (index in 0 until files.numFiles()) {
            if (files.padFileAt(index)) continue
            val path = files.filePath(index)
            val extension = extensionOf(path)
            when {
                extension in VIDEO_EXTENSIONS && !isExtraVideo(path) -> videos += index
                extension in SUBTITLE_EXTENSIONS -> subtitles += index
            }
        }
        return buildList {
            addAll(videos)
            addAll(subtitles.take(8))
        }
    }

    private fun isExtraVideo(path: String): Boolean {
        val normalized = path.lowercase()
        val name = normalized.substringAfterLast('/')
        return normalized.contains("/extras/") ||
            normalized.startsWith("extras/") ||
            normalized.contains("/bd menus/") ||
            normalized.startsWith("bd menus/") ||
            normalized.contains("/drama cds/") ||
            normalized.contains("/animate benefits/") ||
            normalized.contains("creditless") ||
            normalized.contains("cast credit") ||
            name.contains("trailer") ||
            name.contains("sample") ||
            name.contains("teaser") ||
            name.contains("menu")
    }

    private fun selectDownloadFile(info: TorrentInfo): String {
        val files = info.files()
        var bestPath: String? = null
        var bestSize = 0L
        for (index in 0 until files.numFiles()) {
            if (files.padFileAt(index)) continue
            val path = files.filePath(index)
            val extension = path.substringAfterLast('.', "").lowercase()
            if (extension !in VIDEO_EXTENSIONS) continue
            val size = files.fileSize(index)
            if (size > bestSize) {
                bestSize = size
                bestPath = path
            }
        }
        return bestPath ?: info.name()
    }

    private fun syncForegroundService() {
        val shouldRun = active.values.any { it.handle != null || it.job?.isActive == true }
        if (shouldRun && !foregroundStarted) {
            foregroundStarted = true
            DownloadForegroundService.start(appContext)
        } else if (!shouldRun && foregroundStarted) {
            foregroundStarted = false
            DownloadForegroundService.stop(appContext)
        }
    }

    private fun startLocked(id: String) {
        if (active[id]?.job?.isActive == true) return
        val item = current(id) ?: return
        if (item.isComplete) return

        val session = ActiveDownload(id)
        active[id] = session
        update(id) {
            it.copy(status = DownloadStatus.QUEUED, error = null, downloadRate = 0L, peers = 0)
        }
        persist()
        syncForegroundService()

        session.job = scope.launch {
            try {
                if (
                    session.cancelled ||
                    active[id] !== session ||
                    current(id)?.status == DownloadStatus.PAUSED
                ) {
                    return@launch
                }
                val manager = ensureSession()
                update(id) { it.copy(status = DownloadStatus.FETCHING, error = null) }
                persist()

                metadataDir.mkdirs()
                val metadata = manager.fetchMagnet(
                    TrackerLists.withTrackers(item.magnetUrl),
                    METADATA_TIMEOUT_SECONDS,
                    metadataDir,
                ) ?: throw DownloadFailure("Could not find peers for this magnet.")
                currentCoroutineContext().ensureActive()
                if (session.cancelled || active[id] !== session || current(id) == null) {
                    return@launch
                }

                val info = TorrentInfo.bdecode(metadata)
                if (!info.isValid) throw DownloadFailure("The torrent metadata is invalid.")
                val saveDir = File(downloadRoot(), id).apply { mkdirs() }
                val files = info.files()
                val selectedIndexes = item.selectedFileIndexes
                    .filter { it in 0 until files.numFiles() && !files.padFileAt(it) }
                    .ifEmpty { defaultDownloadIndexes(info) }
                val selectedBytes = selectedIndexes.sumOf { files.fileSize(it) }
                session.totalBytes = selectedBytes
                session.manager = manager

                val canonicalSaveDir = saveDir.canonicalFile
                val selectedPaths = selectedIndexes.map { files.filePath(it) }
                val selectedFile = selectedPaths
                    .firstOrNull { extensionOf(it) in VIDEO_EXTENSIONS }
                    ?.let { File(canonicalSaveDir, it).canonicalFile }
                    ?: File(canonicalSaveDir, selectDownloadFile(info)).canonicalFile
                if (!selectedFile.path.startsWith(canonicalSaveDir.path + File.separator)) {
                    throw DownloadFailure("The torrent contains an unsafe file path.")
                }
                val priorities = Priority.array(Priority.IGNORE, files.numFiles())
                selectedIndexes.forEach { priorities[it] = Priority.DEFAULT }
                update(id) {
                    it.copy(
                        totalBytes = selectedBytes,
                        savedPath = selectedFile.absolutePath,
                    )
                }
                val resumeFile = File(saveDir, RESUME_FILE_NAME)
                manager.download(
                    info,
                    saveDir,
                    resumeFile.takeIf { it.isFile },
                    priorities,
                    null,
                    TorrentFlags.NEED_SAVE_RESUME,
                )
                val handle = awaitHandle(manager, info)
                if (
                    session.cancelled ||
                    active[id] !== session ||
                    current(id) == null
                ) {
                    runCatching { manager.remove(handle) }
                    return@launch
                }
                session.handle = handle
                TrackerLists.addPeerDiscoveryTrackers(handle)
                handle.resume()
                update(id) { it.copy(status = DownloadStatus.DOWNLOADING, error = null) }
                persist()
                AppLog.event(
                    "DOWNLOAD",
                    "started",
                    fields = mapOf(
                        "id" to id,
                        "name" to info.name(),
                        "size" to selectedBytes,
                        "files" to selectedIndexes.size,
                    ),
                )
                Log.i(TAG, "Download started: " + info.name())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                commandMutex.withLock {
                    if (active[id] === session) {
                        session.handle?.let { runCatching { session.manager?.remove(it) } }
                        session.handle = null
                        active.remove(id, session)
                        update(id) {
                            it.copy(
                                status = DownloadStatus.FAILED,
                                error = error.message?.takeIf { message -> message.isNotBlank() }
                                    ?: "The download could not start.",
                                downloadRate = 0L,
                                peers = 0,
                            )
                        }
                        persist()
                        AppLog.exception(
                            "DOWNLOAD",
                            "start_failed",
                            error,
                            fields = mapOf("id" to id),
                        )
                    }
                }
            } finally {
                if (session.cancelled || active[id] !== session) {
                    session.handle?.let { runCatching { session.manager?.remove(it) } }
                    session.handle = null
                }
                syncForegroundService()
            }
        }
        syncForegroundService()
    }

    private suspend fun monitorLoop() {
        while (currentCoroutineContext().isActive) {
            delay(1_000)
            runCatching { tick() }
                .onFailure { Log.w(TAG, "Download monitor tick failed", it) }
            val now = System.currentTimeMillis()
            if (now - lastResumeSaveAt >= RESUME_CHECKPOINT_INTERVAL_MILLIS) {
                lastResumeSaveAt = now
                runCatching { saveActiveResumeData() }
                    .onFailure { Log.w(TAG, "Download resume checkpoint failed", it) }
            }
        }
    }

    private suspend fun saveActiveResumeData() {
        active.values.toList().take(3).forEach { session ->
            val handle = session.handle ?: return@forEach
            saveResumeData(handle, downloadDirectory(session.id))
        }
    }

    private suspend fun tick() {
        active.values.toList().forEach { session ->
            val handle = session.handle ?: return@forEach
            val item = current(session.id) ?: return@forEach
            if (item.status == DownloadStatus.PAUSED) return@forEach

            val status = runCatching { handle.status() }.getOrNull() ?: return@forEach
            val state = status.state()
            val total = status.totalWanted().takeIf { it > 0L } ?: item.totalBytes
            val done = status.totalWantedDone().coerceAtLeast(0L)
                .let { bytes -> if (total > 0L) bytes.coerceAtMost(total) else bytes }

            if (
                status.isFinished ||
                state == TorrentStatus.State.FINISHED ||
                state == TorrentStatus.State.SEEDING
            ) {
                complete(session, total)
                return@forEach
            }

            val nextStatus = when (state) {
                TorrentStatus.State.DOWNLOADING -> DownloadStatus.DOWNLOADING
                TorrentStatus.State.CHECKING_FILES,
                TorrentStatus.State.CHECKING_RESUME_DATA,
                -> DownloadStatus.DOWNLOADING
                TorrentStatus.State.DOWNLOADING_METADATA -> DownloadStatus.FETCHING
                else -> item.status
            }
            update(session.id) {
                it.copy(
                    status = nextStatus,
                    downloadedBytes = done,
                    totalBytes = total,
                    downloadRate = status.downloadRate().toLong(),
                    peers = status.numPeers(),
                    error = null,
                )
            }
        }

    }

    private suspend fun complete(session: ActiveDownload, reportedTotal: Long) {
        commandMutex.withLock {
            val item = current(session.id) ?: return@withLock
            if (active[session.id] !== session) return@withLock
            val total = reportedTotal.takeIf { it > 0L } ?: item.totalBytes
            runCatching { session.handle?.pause() }
            session.handle?.let { handle -> runCatching { session.manager?.remove(handle) } }
            session.handle = null
            active.remove(session.id, session)
            val completedFile = current(session.id)?.savedPath?.let(::File)
            val directPublic = completedFile != null &&
                isPublicFile(completedFile) &&
                completedFile.exists()
            update(session.id) {
                it.copy(
                    status = DownloadStatus.COMPLETED,
                    downloadedBytes = if (total > 0L) total else it.totalBytes,
                    totalBytes = if (total > 0L) total else it.totalBytes,
                    downloadRate = 0L,
                    peers = 0,
                    error = null,
                    publicUri = if (directPublic) {
                        fileProviderUri(completedFile!!) ?: it.publicUri
                    } else {
                        it.publicUri
                    },
                )
            }
            markComplete(session.id)
            persist()
            syncForegroundService()
            AppLog.event(
                "DOWNLOAD",
                "completed",
                fields = mapOf("id" to session.id, "title" to item.title),
            )
            Log.i(TAG, "Download complete: " + item.title)
        }
        
        
        if (File(privateDownloadsRoot, session.id).exists()) {
            scope.launch { exportInternal(session.id, deleteInternal = true) }
        }
    }

    



    suspend fun exportToPublic(id: String): Result<String> = withContext(Dispatchers.IO) {
        exportInternal(id, deleteInternal = true)
    }

    private fun exportInternal(id: String, deleteInternal: Boolean): Result<String> {
        val item = current(id) ?: return Result.failure(IllegalStateException("Download not found."))
        item.publicUri?.let { return Result.success(it) }
        if (!exporting.add(id)) {
            return Result.failure(IllegalStateException("This download is already being saved."))
        }
        val source = resolveExportSource(item)
        if (source == null) {
            exporting.remove(id)
            return Result.failure(IllegalStateException("The completed file is no longer on disk."))
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (!isDirectStorageAvailable()) {
                exporting.remove(id)
                return Result.failure(
                    IllegalStateException("Storage permission is needed to save this file."),
                )
            }
            return try {
                val directory = File(publicDownloadsRoot, id).apply { mkdirs() }
                if (!isInsideDownloadRoots(directory)) {
                    return Result.failure(IllegalStateException("Unsafe download destination."))
                }
                val target = File(directory, source.name)
                source.copyTo(target, overwrite = true)
                val uri = fileProviderUri(target)
                    ?: return Result.failure(IllegalStateException("Could not expose the saved file."))
                update(id) { it.copy(publicUri = uri, savedPath = target.absolutePath) }
                persist()
                runCatching { File(privateDownloadsRoot, id).deleteRecursively() }
                Result.success(uri)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Result.failure(error)
            } finally {
                exporting.remove(id)
            }
        }
        return try {
            val uri = publishToPublicDownloads(source)
                ?: return Result.failure(IllegalStateException("The file could not be saved to Downloads."))
            if (current(id) == null) {
                runCatching { appContext.contentResolver.delete(uri, null, null) }
                return Result.failure(IllegalStateException("The download was removed."))
            }
            update(id) { it.copy(publicUri = uri.toString(), error = null) }
            persist()
            if (deleteInternal) {
                runCatching { File(privateDownloadsRoot, id).deleteRecursively() }
            }
            Log.i(TAG, "Download published: " + uri)
            Result.success(uri.toString())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Log.w(TAG, "Could not publish download", error)
            Result.failure(error)
        } finally {
            exporting.remove(id)
        }
    }

    private fun resolveExportSource(item: DownloadItem): File? {
        val saved = item.savedPath?.let(::File)
        if (saved?.isFile == true && isInsideDownloadRoots(saved)) return saved
        val directories = listOfNotNull(
            saved?.takeIf { it.isDirectory },
            File(privateDownloadsRoot, item.id).takeIf { it.isDirectory },
            File(publicDownloadsRoot, item.id).takeIf { it.isDirectory },
        ).filter(::isInsideDownloadRoots)
        return directories.asSequence()
            .flatMap { it.walkTopDown() }
            .filter { it.isFile && it.length() > 0L }
            .filter { it.extension.lowercase() in VIDEO_EXTENSIONS }
            .filter(::isInsideDownloadRoots)
            .maxByOrNull { it.length() }
    }

    private fun isInsideDownloadRoots(file: File): Boolean = runCatching {
        val candidate = file.canonicalPath
        val privatePrefix = privateDownloadsRoot.canonicalPath + File.separator
        val publicPrefix = publicDownloadsRoot.canonicalPath + File.separator
        candidate.startsWith(privatePrefix) || candidate.startsWith(publicPrefix)
    }.getOrDefault(false)

    private fun publishToPublicDownloads(source: File): Uri? {
        val resolver = appContext.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, source.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType(source.name))
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/Streamline",
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val uri = resolver.insert(collection, values) ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: throw IllegalStateException("Could not open the Downloads destination.")
            val ready = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            if (resolver.update(uri, ready, null, null) <= 0) {
                throw IllegalStateException("The Downloads entry could not be finalized.")
            }
            uri
        } catch (error: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw error
        }
    }

    private fun mimeType(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "mp4", "m4v" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "mov" -> "video/quicktime"
        "avi" -> "video/x-msvideo"
        else -> "application/octet-stream"
    }

    private fun ensureSession(): SessionManager {
        sessionManager?.let { return it }
        val settings = SessionParams().settings
        settings.setEnableDht(true)
        settings.setEnableLsd(true)
        settings.connectionsLimit(300)
        settings.activeLimit(20)
        settings.activeTrackerLimit(100)
        settings.activeLsdLimit(50)
        settings.maxPeerlistSize(8_000)
        settings.setDhtBootstrapNodes(TrackerLists.DHT_BOOTSTRAP_NODES)
        settings.uploadRateLimit(DOWNLOAD_UPLOAD_LIMIT_BPS)
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
        sessionManager = manager
        if (alertListener == null) {
            val listener = object : AlertListener {
                override fun types(): IntArray = intArrayOf(
                    AlertType.SAVE_RESUME_DATA.swig(),
                    AlertType.SAVE_RESUME_DATA_FAILED.swig(),
                    AlertType.TORRENT_ERROR.swig(),
                    AlertType.FILE_ERROR.swig(),
                )

                override fun alert(alert: Alert<*>) {
                    when (alert) {
                        is SaveResumeDataAlert -> {
                            val hash = runCatching { alert.handle().infoHash().toHex() }.getOrNull()
                            if (hash != null) {
                                pendingResumeSaves.remove(hash)?.complete(
                                    runCatching {
                                        AddTorrentParams.writeResumeDataBuf(alert.params())
                                    }.getOrDefault(ByteArray(0)),
                                )
                            }
                        }
                        is SaveResumeDataFailedAlert -> {
                            val hash = runCatching { alert.handle().infoHash().toHex() }.getOrNull()
                            if (hash != null) pendingResumeSaves.remove(hash)?.complete(ByteArray(0))
                        }
                        is TorrentErrorAlert -> {
                            val hash = runCatching { alert.handle().infoHash().toHex() }.getOrNull()
                            val message = runCatching { alert.error().message }.getOrNull()
                                ?: "The torrent engine reported an error."
                            if (hash != null) {
                                scope.launch { failActiveByHash(hash, message) }
                            }
                        }
                        is FileErrorAlert -> {
                            val hash = runCatching { alert.handle().infoHash().toHex() }.getOrNull()
                            val message = runCatching { alert.error().message }.getOrNull()
                                ?: "The download could not write its file."
                            if (hash != null) {
                                scope.launch { failActiveByHash(hash, message) }
                            }
                        }
                    }
                }
            }
            alertListener = listener
            manager.addListener(listener)
        }
        if (streamingThrottleActive) {
            runCatching {
                manager.applySettings(SettingsPack().downloadRateLimit(STREAMING_THROTTLE_BPS))
            }
        }
        return manager
    }

    



    fun setStreamingThrottle(active: Boolean) {
        if (streamingThrottleActive == active) return
        streamingThrottleActive = active
        val manager = sessionManager ?: return
        scope.launch {
            runCatching {
                manager.applySettings(
                    SettingsPack().downloadRateLimit(
                        if (active) STREAMING_THROTTLE_BPS else 0,
                    ),
                )
            }
            Log.i(TAG, if (active) "Throttled for streaming" else "Throttle released")
        }
    }

    private suspend fun awaitHandle(
        manager: SessionManager,
        info: TorrentInfo,
    ): TorrentHandle {
        repeat(100) {
            currentCoroutineContext().ensureActive()
            val handle = manager.find(info.infoHash())
            if (handle != null && handle.isValid) return handle
            delay(100)
        }
        throw DownloadFailure("The torrent could not be added to the download session.")
    }

    private fun current(id: String): DownloadItem? =
        _downloads.value.firstOrNull { it.id == id }

    private fun update(id: String, transform: (DownloadItem) -> DownloadItem) {
        _downloads.update { items ->
            items.map { item -> if (item.id == id) transform(item) else item }
        }
    }

    private fun sort(items: List<DownloadItem>): List<DownloadItem> = items.sortedWith(
        compareByDescending<DownloadItem> { it.status.isActive }
            .thenByDescending { it.addedAt },
    )

    @Synchronized
    private fun persist() {
        runCatching { store.save(_downloads.value) }
    }

    private fun infoHash(magnet: String): String? =
        INFO_HASH.find(magnet)?.groupValues?.getOrNull(1)?.lowercase()

    private class ActiveDownload(val id: String) {
        @Volatile var cancelled = false
        var job: Job? = null
        var manager: SessionManager? = null
        var handle: TorrentHandle? = null
        var totalBytes: Long = 0L
    }

    private class DownloadFailure(message: String) : Exception(message)

    private companion object {
        const val TAG = "StreamlineDownloads"
        const val METADATA_TIMEOUT_SECONDS = 90
        const val PERSIST_INTERVAL_MILLIS = 3_000L
        val INFO_HASH = Regex("(?i)btih:([a-z0-9]{40})")
        val RECOVERABLE_ID = Regex("(?i)[a-f0-9]{32,40}")
        val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "webm", "m4v", "mov", "avi", "ts")
        val SUBTITLE_EXTENSIONS = setOf("srt", "vtt", "ass", "ssa")
        const val COMPLETE_MARKER = ".streamline-complete"
        const val RESUME_FILE_NAME = ".fastresume"
        const val RESUME_SAVE_TIMEOUT_MILLIS = 3_000L
        const val RESUME_CHECKPOINT_INTERVAL_MILLIS = 30_000L

        
        
        const val DOWNLOAD_UPLOAD_LIMIT_BPS = 1024 * 1024

        
        const val STREAMING_THROTTLE_BPS = 384 * 1024
    }
}

package com.streamline.app.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.streamline.app.core.playback.PlaybackHistoryStore
import com.streamline.app.core.playback.PlaybackPositionStore
import com.streamline.app.core.settings.SettingsRepository
import com.streamline.app.core.torrent.TorrentPlayback
import com.streamline.app.core.torrent.TorrentState
import com.streamline.app.core.torrent.TorrentStreamEngine
import com.streamline.app.domain.model.PlaybackAlternative
import com.streamline.app.domain.model.PlaybackRequest
import com.streamline.app.domain.model.VideoQuality
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlayerUiState(
    val displayTitle: String? = null,
    val artworkUrl: String? = null,
    val sourceLabel: String? = null,
    val qualityLabel: String? = null,
    val torrentState: TorrentState = TorrentState.Idle,
    val fatalError: String? = null,
    val hardwareDecoding: Boolean = true,
    val showStreamStats: Boolean = false,
    val startAtMs: Long = 0L,
    val startDurationMs: Long = 0L,
    val videoFileExplicit: Boolean = false,
) {
    val title: String
        get() = displayTitle ?: "Now playing"
}


class PlayerViewModel(
    private val request: PlaybackRequest,
    private val settingsRepository: SettingsRepository,
    private val torrentEngine: TorrentStreamEngine,
    private val positionStore: PlaybackPositionStore,
    private val historyStore: PlaybackHistoryStore,
) : ViewModel() {

    private val initialResume = positionStore.get(
        positionStore.keyFor(request.magnetUrl, request.videoFileIndex),
    )

    private val _uiState = MutableStateFlow(
        PlayerUiState(
            displayTitle = request.title,
            artworkUrl = request.posterUrl,
            sourceLabel = request.sourceLabel,
            qualityLabel = request.quality,
            hardwareDecoding = settingsRepository.settings.value.hardwareDecoding,
            showStreamStats = settingsRepository.settings.value.showStreamStats,
            startAtMs = initialResume?.positionMs ?: 0L,
            startDurationMs = initialResume?.durationMs ?: 0L,
            videoFileExplicit = request.videoFileIndex != null,
        ),
    )
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var activeRequest: PlaybackRequest = request
    private var alternativeIndex: Int = -1
    val alternativeOptions = request.alternatives
        .distinctBy { it.magnetUrl }
        .sortedWith(
            compareByDescending<PlaybackAlternative> { VideoQuality.from(it.quality).height }
                .thenByDescending { it.seeds ?: 0 },
        )
        .take(5)
    private var preferredFileIndex: Int? = request.videoFileIndex
    private var artworkKey: String? = null
    private var artworkJob: Job? = null
    private var activeResumeKey: String? =
        positionStore.keyFor(request.magnetUrl, request.videoFileIndex)

    init {
        torrentEngine.play(
            activeRequest.magnetUrl,
            activeRequest.torrentUrl,
            preferredFileIndex = preferredFileIndex,
        )
        viewModelScope.launch {
            torrentEngine.state.collect { torrent ->
                _uiState.update { it.copy(torrentState = torrent) }
                if (torrent is TorrentState.Ready) {
                    adoptPlaybackFile(torrent.playback)
                    if (activeRequest.posterUrl.isNullOrBlank()) {
                        loadTorrentArtwork(torrent.playback)
                    }
                }
            }
        }
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.update {
                    it.copy(
                        hardwareDecoding = settings.hardwareDecoding,
                        showStreamStats = settings.showStreamStats,
                    )
                }
            }
        }
    }

    fun savePosition(positionMs: Long, durationMs: Long) {
        if (durationMs <= 0L) return
        positionStore.save(activeResumeKey, PlaybackPositionStore.Entry(positionMs, durationMs))
        historyStore.record(activeRequest, positionMs, durationMs)
    }

    fun clearPosition() {
        positionStore.clear(activeResumeKey)
        historyStore.remove(request.id)
    }

    fun updatePlayhead(positionMs: Long, bufferedPositionMs: Long, durationMs: Long) {
        torrentEngine.updatePlayhead(positionMs, bufferedPositionMs, durationMs)
    }

    private fun adoptPlaybackFile(playback: TorrentPlayback) {
        val index = playback.video.fileIndex
        if (activeRequest.videoFileIndex == index) return
        val oldKey = activeResumeKey
        activeRequest = activeRequest.copy(videoFileIndex = index)
        val newKey = positionStore.keyFor(activeRequest.magnetUrl, index)
        if (newKey != oldKey) {
            val legacy = positionStore.get(oldKey)
            if (legacy != null && positionStore.get(newKey) == null) {
                positionStore.save(newKey, legacy)
            }
        }
        activeResumeKey = newKey
    }

    private fun loadTorrentArtwork(playback: TorrentPlayback) {
        val key = playback.video.file.absolutePath
        if (artworkKey == key) return
        artworkKey = key
        artworkJob?.cancel()
        artworkJob = viewModelScope.launch {
            val file = torrentEngine.loadArtwork(playback)
            if (file != null && artworkKey == key) {
                _uiState.update { state ->
                    state.copy(artworkUrl = file.toURI().toString())
                }
            }
        }
    }

    fun switchVideoFile(index: Int) {
        if (index < 0) return
        preferredFileIndex = index
        activeRequest = activeRequest.copy(videoFileIndex = index)
        activeResumeKey = positionStore.keyFor(activeRequest.magnetUrl, index)
        artworkKey = null
        artworkJob?.cancel()
        artworkJob = null
        _uiState.update {
            it.copy(
                startAtMs = 0L,
                startDurationMs = 0L,
                fatalError = null,
                videoFileExplicit = true,
            )
        }
        torrentEngine.play(
            activeRequest.magnetUrl,
            activeRequest.torrentUrl,
            preferredFileIndex = index,
        )
    }

    fun retryTorrent() {
        torrentEngine.play(
            activeRequest.magnetUrl,
            activeRequest.torrentUrl,
            preferredFileIndex = preferredFileIndex,
        )
    }

    fun hasAlternativeStreams(): Boolean = alternativeOptions.isNotEmpty()

    fun selectAlternative(index: Int) {
        if (index !in alternativeOptions.indices) return
        val alternative = alternativeOptions[index]
        alternativeIndex = index
        preferredFileIndex = null
        activeRequest = request.copy(
            magnetUrl = alternative.magnetUrl,
            torrentUrl = alternative.torrentUrl,
            quality = alternative.quality ?: request.quality,
            sourceLabel = alternative.sourceLabel ?: request.sourceLabel,
            videoFileIndex = null,
        )
        activeResumeKey = positionStore.keyFor(alternative.magnetUrl)
        artworkKey = null
        artworkJob?.cancel()
        artworkJob = null
        _uiState.update {
            it.copy(
                qualityLabel = activeRequest.quality,
                sourceLabel = activeRequest.sourceLabel,
                artworkUrl = null,
                startAtMs = 0L,
                videoFileExplicit = false,
            )
        }
        torrentEngine.play(activeRequest.magnetUrl, activeRequest.torrentUrl)
    }

    fun tryNextAlternative(): Boolean {
        val next = alternativeIndex + 1
        if (next >= alternativeOptions.size) return false
        selectAlternative(next)
        return true
    }

    fun onAppBackgrounded() {
        torrentEngine.onBackgrounded()
    }

    fun onAppForegrounded() {
        torrentEngine.onForegrounded()
    }

    fun pauseDownloads() {
        torrentEngine.pauseDownloads()
    }

    fun resumeDownloads() {
        torrentEngine.resumeDownloads()
    }

    override fun onCleared() {
        torrentEngine.stop()
    }

    companion object {
        fun sessionFactory(
            request: PlaybackRequest,
            settings: SettingsRepository,
            torrentEngine: TorrentStreamEngine,
            positionStore: PlaybackPositionStore,
            historyStore: PlaybackHistoryStore,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                PlayerViewModel(
                    request = request,
                    settingsRepository = settings,
                    torrentEngine = torrentEngine,
                    positionStore = positionStore,
                    historyStore = historyStore,
                )
            }
        }
    }
}

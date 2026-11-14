package com.streamline.app.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.streamline.app.core.playback.PlaybackPositionStore
import com.streamline.app.core.settings.SettingsRepository
import com.streamline.app.core.torrent.TorrentState
import com.streamline.app.core.torrent.TorrentStreamEngine
import com.streamline.app.domain.model.PlaybackRequest
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
) {
    val title: String
        get() = displayTitle ?: "Now playing"
}

/** A one-shot magnet handoff from the link library. */
class PlayerViewModel(
    private val request: PlaybackRequest,
    private val settingsRepository: SettingsRepository,
    private val torrentEngine: TorrentStreamEngine,
    private val positionStore: PlaybackPositionStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        PlayerUiState(
            displayTitle = request.title,
            artworkUrl = request.posterUrl,
            sourceLabel = request.sourceLabel,
            qualityLabel = request.quality,
            hardwareDecoding = settingsRepository.settings.value.hardwareDecoding,
            showStreamStats = settingsRepository.settings.value.showStreamStats,
        ),
    )
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    /** Stable per-torrent key for resume bookkeeping; null when the magnet carries no info hash. */
    val resumeKey: String? = positionStore.keyFor(request.magnetUrl)

    /** Where a previous session stopped, or 0 to start from the beginning. */
    val resumePositionMs: Long = positionStore.get(resumeKey)?.positionMs ?: 0L

    init {
        torrentEngine.play(request.magnetUrl, request.torrentUrl)
        viewModelScope.launch {
            torrentEngine.state.collect { torrent ->
                _uiState.update { it.copy(torrentState = torrent) }
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
        positionStore.save(resumeKey, PlaybackPositionStore.Entry(positionMs, durationMs))
    }

    fun clearPosition() {
        positionStore.clear(resumeKey)
    }

    fun retryTorrent() {
        torrentEngine.play(request.magnetUrl, request.torrentUrl)
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
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                PlayerViewModel(
                    request = request,
                    settingsRepository = settings,
                    torrentEngine = torrentEngine,
                    positionStore = positionStore,
                )
            }
        }
    }
}

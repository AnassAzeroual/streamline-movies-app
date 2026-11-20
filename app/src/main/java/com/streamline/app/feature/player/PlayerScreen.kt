package com.streamline.app.feature.player

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.SystemClock
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.streamline.app.player.mkv.CachedMatroskaExtractor
import com.streamline.app.player.mkv.MatroskaCueCache
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import androidx.media3.exoplayer.util.EventLogger
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.streamline.app.BuildConfig
import com.streamline.app.R
import com.streamline.app.core.designsystem.Artwork
import com.streamline.app.core.designsystem.StreamlineColors
import com.streamline.app.core.designsystem.isTelevision
import com.streamline.app.core.di.AppContainer
import com.streamline.app.core.torrent.TorrentDataSourceFactory
import com.streamline.app.core.tv.TvRemotePlayback
import com.streamline.app.core.logging.AppLog
import com.streamline.app.core.torrent.SeekSnapper
import com.streamline.app.core.torrent.TorrentFailureKind
import com.streamline.app.core.designsystem.formatBytes
import com.streamline.app.core.torrent.TorrentPlayback
import com.streamline.app.core.torrent.TorrentVideoFile
import com.streamline.app.core.torrent.TorrentState
import com.streamline.app.core.util.findActivity
import com.streamline.app.domain.model.PlaybackAlternative
import com.streamline.app.domain.model.VideoQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@UnstableApi
@Composable
fun PlayerSessionRoute(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val request = remember { container.playbackSessionRepository.consume() }
    if (request == null) {
        LaunchedEffect(Unit) { onBack() }
        Box(Modifier.fillMaxSize().background(Color.Black))
        return
    }
    val viewModel: PlayerViewModel = viewModel(
        key = "playback-session-" + request.id,
        factory = PlayerViewModel.sessionFactory(
            request = request,
            settings = container.settingsRepository,
            torrentEngine = container.torrentEngine,
            positionStore = container.playbackPositionStore,
            historyStore = container.playbackHistoryStore,
        ),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val resumePositionMs = state.startAtMs

    PlayerScreen(
        state = state,
        resumePositionMs = resumePositionMs,
        resumeDurationMs = state.startDurationMs,
        onSavePosition = viewModel::savePosition,
        onPlayheadUpdate = viewModel::updatePlayhead,
        onClearPosition = viewModel::clearPosition,
        onSelectVideo = viewModel::switchVideoFile,
        alternatives = viewModel.alternativeOptions,
        onSelectAlternative = viewModel::selectAlternative,
        onBack = {
            AppLog.event("PLAYER", "back")
            onBack()
        },
        onRetryTorrent = {
            AppLog.event("PLAYER", "retry")
            viewModel.retryTorrent()
        },
        onPauseDownloads = viewModel::pauseDownloads,
        onResumeDownloads = viewModel::resumeDownloads,
        onAppBackgrounded = viewModel::onAppBackgrounded,
        onAppForegrounded = viewModel::onAppForegrounded,
    )
}

private fun snapSeekTarget(
    playback: TorrentPlayback?,
    requestedMs: Long,
    durationMs: Long,
): Long {
    if (playback == null || durationMs <= 0L) return requestedMs
    val result = runCatching {
        SeekSnapper.snap(playback, requestedMs, durationMs, windowPieces = 2)
    }.getOrNull() ?: return requestedMs
    if (result.snapped) {
        AppLog.event(
            "PLAYER",
            "seek_snapped",
            fields = mapOf(
                "requestedMs" to result.requestedMs,
                "playbackMs" to result.playbackMs,
                "requestedPiece" to result.requestedPiece,
                "playbackPiece" to result.playbackPiece,
                "reason" to result.reason,
            ),
        )
    }
    return result.playbackMs
}

private fun buildPieceMap(
    playback: TorrentPlayback,
    positionMs: Long,
    durationMs: Long,
): PieceMap? {
    if (playback.pieceLength <= 0) return null
    val first = playback.firstPiece
    val last = playback.lastPiece
    if (last < first) return null
    val count = last - first + 1
    val downloaded = BooleanArray(count) { index ->
        runCatching { playback.handle.havePiece(first + index) }.getOrDefault(false)
    }
    val current = if (durationMs > 0L && positionMs > 0L) {
        val clamped = positionMs.coerceAtMost(durationMs)
        val fileByte = playback.fileSize * clamped / durationMs
        val absolute = ((playback.fileTorrentOffset + fileByte) / playback.pieceLength).toInt()
        (absolute - first).coerceIn(0, count - 1)
    } else {
        -1
    }
    return PieceMap(downloaded = downloaded, currentIndex = current)
}

@UnstableApi
@Composable
fun PlayerScreen(
    state: PlayerUiState,
    resumePositionMs: Long,
    resumeDurationMs: Long,
    onSavePosition: (positionMs: Long, durationMs: Long) -> Unit,
    onPlayheadUpdate: (positionMs: Long, bufferedPositionMs: Long, durationMs: Long) -> Unit,
    onClearPosition: () -> Unit,
    onSelectVideo: (Int) -> Unit,
    alternatives: List<PlaybackAlternative>,
    onSelectAlternative: (Int) -> Unit,
    onBack: () -> Unit,
    onRetryTorrent: () -> Unit,
    onPauseDownloads: () -> Unit,
    onResumeDownloads: () -> Unit,
    onAppBackgrounded: () -> Unit,
    onAppForegrounded: () -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isTv = isTelevision()
    val controlsFocus = remember { FocusRequester() }
    val rootFocus = remember { FocusRequester() }

    val originalOrientation = remember {
        activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
    val originalBrightness = remember {
        activity?.window?.attributes?.screenBrightness ?: -1f
    }

    var brightness by remember {
        mutableFloatStateOf(if (originalBrightness >= 0f) originalBrightness else 0.5f)
    }
    var volume by remember { mutableFloatStateOf(1f) }
    var lastAudibleVolume by remember { mutableFloatStateOf(1f) }
    var controlsVisible by remember { mutableStateOf(true) }
    var controlsHaveFocus by remember { mutableStateOf(false) }
    var centralPlayFocused by remember { mutableStateOf(false) }
    var interactionNonce by remember { mutableIntStateOf(0) }
    var showTracks by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(state.showStreamStats) }
    LaunchedEffect(state.showStreamStats) { showStats = state.showStreamStats }
    var stats by remember { mutableStateOf<StreamStats?>(null) }
    var hud by remember { mutableStateOf<PlayerHudState?>(null) }
    var playerError by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var lastPlaybackKey by remember { mutableStateOf<String?>(null) }
    var autoRetryAttempts by remember { mutableIntStateOf(0) }
    var resumeAfterBackground by remember { mutableStateOf(false) }

    var isPlaying by remember { mutableStateOf(false) }
    var playbackState by remember { mutableIntStateOf(Player.STATE_IDLE) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(C.TIME_UNSET) }
    var bufferedMs by remember { mutableLongStateOf(0L) }
    var currentTracks by remember { mutableStateOf(Tracks.EMPTY) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    var droppedFrames by remember { mutableIntStateOf(0) }

    
    
    val torrentPlayback: TorrentPlayback? = when (val torrent = state.torrentState) {
        is TorrentState.Ready -> torrent.playback
        is TorrentState.Buffering -> torrent.playback
        else -> null
    }
    val latestTorrentState by rememberUpdatedState(state.torrentState)
    val autoRetryKey = when (val torrent = state.torrentState) {
        is TorrentState.Failed -> "engine:" + torrent.message
        else -> playerError?.let { "player:" + it }
    }

    LaunchedEffect(autoRetryKey) {
        if (autoRetryKey == null || autoRetryAttempts >= MAX_AUTO_RETRIES) return@LaunchedEffect
        val failure = state.torrentState as? TorrentState.Failed
        val terminalFailure = failure?.kind == TorrentFailureKind.FILE ||
            failure?.kind == TorrentFailureKind.CONTAINER ||
            failure?.kind == TorrentFailureKind.NO_PEERS
        AppLog.warn(
            "PLAYBACK",
            "failure_detected",
            message = failure?.message ?: playerError ?: "Player error",
            fields = mapOf(
                "kind" to (failure?.kind?.name ?: "PLAYER"),
                "attempt" to autoRetryAttempts,
                "terminal" to terminalFailure,
            ),
        )
        if (terminalFailure) return@LaunchedEffect
        delay(AUTO_RETRY_DELAY_MS)
        if (autoRetryAttempts >= MAX_AUTO_RETRIES - 1) return@LaunchedEffect
        autoRetryAttempts++
        AppLog.event(
            "PLAYBACK",
            "auto_retry",
            fields = mapOf("attempt" to autoRetryAttempts),
        )
        playerError = null
        reloadKey++
        onRetryTorrent()
    }

    val artworkUrl = state.artworkUrl
    val playbackKey = torrentPlayback?.let { "torrent:" + it.file.absolutePath }
    val videoFiles = torrentPlayback?.videoFiles.orEmpty()
    val sortedVideoFiles = remember(videoFiles) {
        videoFiles.sortedWith(
            compareByDescending<TorrentVideoFile> { it.size }
                .thenBy { it.name },
        )
    }
    var showVideoPicker by remember { mutableStateOf(false) }
    var showAlternatives by remember { mutableStateOf(false) }
    var hasAutoShownVideoPicker by remember { mutableStateOf(false) }
    LaunchedEffect(torrentPlayback) {
        if (!hasAutoShownVideoPicker && !state.videoFileExplicit && videoFiles.size > 1) {
            hasAutoShownVideoPicker = true
            showVideoPicker = true
        }
    }
    var hasRenderedFrame by remember(playbackKey, reloadKey, state.hardwareDecoding) {
        mutableStateOf(false)
    }
    var pendingResumeMs by remember(playbackKey, reloadKey) {
        mutableLongStateOf(resumePositionMs.coerceAtLeast(0L))
    }

    LaunchedEffect(hasRenderedFrame) {
        if (hasRenderedFrame) autoRetryAttempts = 0
    }

    val renderersFactory = remember(state.hardwareDecoding) {
        createRenderersFactory(context, state.hardwareDecoding)
    }
    val player = remember(renderersFactory) {
        ExoPlayer.Builder(context, renderersFactory)
            
            
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        MIN_BUFFER_MS,
                        MAX_BUFFER_MS,
                        BUFFER_FOR_PLAYBACK_MS,
                        BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
                    )
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build(),
            )
            .setSeekBackIncrementMs(SEEK_INCREMENT_MS)
            .setSeekForwardIncrementMs(SEEK_INCREMENT_MS)
            .build()
            .apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(),
                    true,
                )
                setHandleAudioBecomingNoisy(true)
                addAnalyticsListener(object : AnalyticsListener {
                    override fun onDroppedVideoFrames(
                        eventTime: AnalyticsListener.EventTime,
                        count: Int,
                        elapsedMs: Long,
                    ) {
                        droppedFrames += count
                    }
                })
                if (BuildConfig.DEBUG) addAnalyticsListener(EventLogger())
            }
    }

    
    LaunchedEffect(player, playbackKey) {
        if (playbackKey == null) return@LaunchedEffect
        while (isActive) {
            val duration = player.duration
            if (duration > 0L) {
                onPlayheadUpdate(
                    player.currentPosition.coerceAtLeast(0L),
                    player.bufferedPosition.coerceAtLeast(0L),
                    duration,
                )
            }
            delay(1_000L)
        }
    }

    
    
    
    LaunchedEffect(player, playbackKey, reloadKey, hasRenderedFrame) {
        if (!hasRenderedFrame) return@LaunchedEffect
        var lastBuffered = -1L
        var lastPosition = -1L
        var lastProgressAt = SystemClock.elapsedRealtime()
        while (isActive) {
            delay(2_000L)
            if (
                playbackKey == null ||
                player.playbackState != Player.STATE_BUFFERING ||
                !player.playWhenReady
            ) {
                lastBuffered = player.bufferedPosition
                lastPosition = player.currentPosition
                lastProgressAt = SystemClock.elapsedRealtime()
                continue
            }
            val buffered = player.bufferedPosition
            val position = player.currentPosition
            val progressed = buffered > lastBuffered + 64 * 1024L ||
                position > lastPosition + 500L
            if (progressed) {
                lastBuffered = buffered
                lastPosition = position
                lastProgressAt = SystemClock.elapsedRealtime()
                continue
            }
            if (SystemClock.elapsedRealtime() - lastProgressAt < STALL_TIMEOUT_MS) continue

            AppLog.warn(
                "PLAYBACK",
                "stall_timeout",
                fields = mapOf(
                    "positionMs" to position,
                    "bufferedMs" to buffered,
                    "pieces" to (state.torrentState as? TorrentState.Buffering)?.let { "buffering" },
                ),
            )
            playerError =
                "Playback is stalled because the next pieces are unavailable. " +
                    "Choose another stream."
            lastProgressAt = SystemClock.elapsedRealtime()
            break
        }
    }

    DisposableEffect(player) {
        onDispose {
            player.release()
        }
    }

    DisposableEffect(Unit) {
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())

        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.requestedOrientation = originalOrientation
            val windowNow = activity?.window
            if (windowNow != null) {
                val attributes = windowNow.attributes
                attributes.screenBrightness = if (originalBrightness >= 0f) originalBrightness else -1f
                windowNow.attributes = attributes
            }
        }
    }

    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    resumeAfterBackground = player.isPlaying ||
                        player.playbackState == Player.STATE_BUFFERING
                    onSavePosition(player.currentPosition.coerceAtLeast(0L), player.duration)
                    player.pause()
                    onPauseDownloads()
                    onAppBackgrounded()
                }
                Lifecycle.Event.ON_START -> {
                    onAppForegrounded()
                    onResumeDownloads()
                    if (resumeAfterBackground) {
                        resumeAfterBackground = false
                        if (latestTorrentState is TorrentState.Idle) {
                            controlsVisible = true
                            onRetryTorrent()
                        } else {
                            player.play()
                        }
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(player, playbackKey, reloadKey) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
                if (state == Player.STATE_ENDED) {
                    controlsVisible = true
                    onClearPosition()
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (!playing && player.playbackState == Player.STATE_READY) {
                    controlsVisible = true
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                AppLog.error(
                    "PLAYBACK",
                    "player_error",
                    message = error.message ?: "PlaybackException",
                    fields = mapOf(
                        "code" to error.errorCodeName,
                        "cause" to error.cause?.javaClass?.name,
                    ),
                )
                playerError = friendlyPlaybackError(error)
                controlsVisible = true
            }

            override fun onTracksChanged(tracks: Tracks) {
                currentTracks = tracks
            }

            override fun onRenderedFirstFrame() {
                hasRenderedFrame = true
            }

            override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
                playbackSpeed = playbackParameters.speed
            }

            override fun onEvents(player: Player, events: Player.Events) {
                positionMs = player.currentPosition.coerceAtLeast(0L)
                durationMs = player.duration
                bufferedMs = player.bufferedPosition.coerceAtLeast(0L)
                playbackState = player.playbackState
                isPlaying = player.isPlaying
                currentTracks = player.currentTracks
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(player, playbackKey, reloadKey) {
        playerError = null
        hasRenderedFrame = false
        droppedFrames = 0
        controlsVisible = true
        if (playbackKey == null) return@LaunchedEffect
        val isRetry = reloadKey > 0 && lastPlaybackKey == playbackKey
        val retryAt = if (isRetry) {
            player.currentPosition.coerceAtLeast(0L)
        } else {
            pendingResumeMs.coerceAtLeast(0L)
        }
        lastPlaybackKey = playbackKey
        player.stop()
        player.clearMediaItems()
        val playback = checkNotNull(torrentPlayback)
        val dataSourceFactory = TorrentDataSourceFactory(playback)
        val startAt = retryAt
        val item = MediaItem.Builder()
            .setUri("streamline-torrent://local/${UriUtils.encode(playback.fileName)}")
            .setMimeType(playback.mimeType)
            .build()
        pendingResumeMs = 0L
        val cueCache = MatroskaCueCache(
            java.io.File(
                playback.cacheDir,
                "matroska-cues-" + playback.fileIndex + "-" + playback.fileSize + ".idx",
            ),
        )
        val extractorsFactory = ExtractorsFactory {
            arrayOf<Extractor>(
                CachedMatroskaExtractor(
                    DefaultSubtitleParserFactory(),
                    0,
                    cueCache,
                ),
            ) + DefaultExtractorsFactory().createExtractors()
        }
        val videoSource = ProgressiveMediaSource
            .Factory(dataSourceFactory, extractorsFactory)
            .createMediaSource(item)
        val mediaSource = if (playback.subtitles.isEmpty()) {
            videoSource
        } else {
            
            
            val subtitleSources = playback.subtitles.mapIndexed { index, stream ->
                SingleSampleMediaSource.Factory(dataSourceFactory)
                    .setTreatLoadErrorsAsEndOfStream(true)
                    .createMediaSource(
                        MediaItem.SubtitleConfiguration.Builder(
                            android.net.Uri.parse(SUBTITLE_URI_PREFIX + index),
                        )
                            .setMimeType(subtitleMimeType(stream.extension))
                            .setLabel(stream.fileName)
                            .setLanguage(subtitleLanguage(stream.fileName))
                            .build(),
                        C.TIME_UNSET,
                    )
            }
            MergingMediaSource(videoSource, *subtitleSources.toTypedArray())
        }
        player.setMediaSource(mediaSource, startAt)
        player.prepare()
        player.playWhenReady = true
    }

    LaunchedEffect(playbackKey, hasRenderedFrame, playerError) {
        if (playbackKey == null || hasRenderedFrame || playerError != null) {
            return@LaunchedEffect
        }
        delay(FIRST_FRAME_TIMEOUT_MS)
        if (!hasRenderedFrame && playerError == null) {
            AppLog.warn(
                "PLAYBACK",
                "first_frame_timeout",
                fields = mapOf(
                    "positionMs" to player.currentPosition,
                    "bufferedMs" to player.bufferedPosition,
                ),
            )
            playerError =
                "This release cannot initialize its video frame with the current decoder. " +
                    "Choose another release."
            autoRetryAttempts = MAX_AUTO_RETRIES
        }
    }

    LaunchedEffect(player, isPlaying) {
        var lastSavedAt = 0L
        while (isActive) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            durationMs = player.duration
            bufferedMs = player.bufferedPosition.coerceAtLeast(0L)
            playbackState = player.playbackState
            isPlaying = player.isPlaying
            val now = SystemClock.elapsedRealtime()
            if (isPlaying && now - lastSavedAt >= POSITION_SAVE_INTERVAL_MS) {
                lastSavedAt = now
                onSavePosition(positionMs, durationMs)
            }
            delay(if (isPlaying) 250L else 600L)
        }
    }

    LaunchedEffect(showStats, torrentPlayback) {
        val handle = torrentPlayback?.handle
        if (!showStats || handle == null) {
            stats = null
            return@LaunchedEffect
        }
        var lastStatsLogAt = 0L
        while (isActive) {
            val rawPosition = player.currentPosition
            val rawDuration = player.duration
            val positionMs = if (rawPosition > 0L) rawPosition else resumePositionMs
            val durationMs = if (rawDuration > 0L) rawDuration else resumeDurationMs
            val sample = withContext(Dispatchers.Default) {
                val status = runCatching { handle.status() }.getOrNull()
                val pieces = status?.let {
                    buildPieceMap(
                        playback = torrentPlayback,
                        positionMs = positionMs,
                        durationMs = durationMs,
                    )
                }
                status to pieces
            }
            val now = SystemClock.elapsedRealtime()
            if (now - lastStatsLogAt >= 5_000L) {
                lastStatsLogAt = now
                sample.first?.let { status ->
                    AppLog.event(
                        "STREAM",
                        "stats",
                        fields = mapOf(
                            "positionMs" to positionMs,
                            "bufferAheadMs" to (player.bufferedPosition - positionMs).coerceAtLeast(0L),
                            "seeds" to status.numSeeds(),
                            "peers" to status.numPeers(),
                            "downloadRate" to status.downloadRate(),
                            "uploadRate" to status.uploadRate(),
                            "pieces" to (sample.second?.downloadedCount ?: -1),
                            "totalPieces" to (sample.second?.downloaded?.size ?: -1),
                        ),
                    )
                }
            }
            stats = sample.first?.let { status ->
                StreamStats(
                    seeds = status.numSeeds(),
                    peers = status.numPeers(),
                    downloadRate = status.downloadRate().toLong(),
                    uploadRate = status.uploadRate().toLong(),
                    progress = status.progress(),
                    videoFormat = player.videoFormat?.let { format ->
                        format.width.toString() + "x" + format.height +
                            (format.frameRate.takeIf { it > 0f }?.let { " · " + it.roundToInt() + " fps" } ?: "")
                    },
                    droppedFrames = droppedFrames,
                    bufferedAheadMs = (player.bufferedPosition - player.currentPosition)
                        .coerceAtLeast(0L),
                    pieceMap = sample.second,
                )
            }
            delay(STATS_POLL_INTERVAL_MS)
        }
    }

    val keepControlsVisible = !isPlaying ||
        showTracks ||
        playbackState == Player.STATE_BUFFERING ||
        playerError != null

    LaunchedEffect(controlsVisible, interactionNonce, keepControlsVisible) {
        if (controlsVisible && !keepControlsVisible) {
            delay(3600)
            controlsVisible = false
        }
    }

    LaunchedEffect(hud) {
        if (hud != null) {
            delay(1200)
            hud = null
        }
    }

    BackHandler {
        if (showTracks) {
            showTracks = false
        } else {
            onBack()
        }
    }

    val bumpInteraction: () -> Unit = {
        interactionNonce++
    }

    fun applyBrightness(value: Float) {
        brightness = value
        val window = activity?.window ?: return
        val attributes = window.attributes
        attributes.screenBrightness = value.coerceIn(0.02f, 1f)
        window.attributes = attributes
    }

    fun applyVolume(value: Float) {
        volume = value
        player.volume = value
        if (value > 0.01f) lastAudibleVolume = value
    }

    fun seekByOffset(deltaMs: Long) {
        if (durationMs <= 0L) return
        val target = (player.currentPosition + deltaMs).coerceIn(0L, durationMs)
        val actual = snapSeekTarget(torrentPlayback, target, durationMs)
        onPlayheadUpdate(
            actual,
            player.bufferedPosition.coerceAtLeast(0L),
            durationMs,
        )
        torrentPlayback?.beginSeek()
        player.seekTo(actual)
        controlsVisible = true
        interactionNonce++
    }

    fun togglePlayback() {
        if (player.playbackState == Player.STATE_ENDED) {
            player.seekTo(0L)
            player.play()
        } else if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
        controlsVisible = true
        interactionNonce++
    }

    val remoteKeyHandler = rememberUpdatedState<(KeyEvent) -> Boolean> { event ->
        if (event.action != KeyEvent.ACTION_DOWN) {
            return@rememberUpdatedState false
        }
        if (
            playbackKey == null ||
            playerError != null ||
            showTracks ||
            showVideoPicker ||
            showAlternatives
        ) {
            return@rememberUpdatedState false
        }
        when (event.keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            -> {
                togglePlayback()
                true
            }
            KeyEvent.KEYCODE_MEDIA_REWIND,
            KeyEvent.KEYCODE_DPAD_LEFT,
            -> {
                if (!controlsVisible || !controlsHaveFocus || centralPlayFocused) {
                    seekByOffset(-10_000L)
                    true
                } else {
                    false
                }
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            -> {
                if (!controlsVisible || !controlsHaveFocus || centralPlayFocused) {
                    seekByOffset(10_000L)
                    true
                } else {
                    false
                }
            }
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            -> {
                if (!controlsVisible) {
                    controlsVisible = true
                    interactionNonce++
                    true
                } else if (!controlsHaveFocus) {
                    togglePlayback()
                    true
                } else {
                    false
                }
            }
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            -> {
                if (!controlsVisible) {
                    controlsVisible = true
                    interactionNonce++
                    true
                } else if (!controlsHaveFocus) {
                    runCatching { controlsFocus.requestFocus() }
                    true
                } else {
                    false
                }
            }
            else -> false
        }
    }

    DisposableEffect(Unit) {
        val token = TvRemotePlayback.install { event -> remoteKeyHandler.value(event) }
        onDispose { TvRemotePlayback.clear(token) }
    }

    LaunchedEffect(controlsVisible, hasRenderedFrame, isTv) {
        if (!isTv) return@LaunchedEffect
        if (controlsVisible && hasRenderedFrame) {
            runCatching { controlsFocus.requestFocus() }
        } else {
            runCatching { rootFocus.requestFocus() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || playerError != null) {
                    return@onPreviewKeyEvent false
                }
                when (event.key) {
                    Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> {
                        togglePlayback()
                        true
                    }
                    Key.DirectionCenter, Key.Enter -> {
                        if (!controlsVisible) {
                            controlsVisible = true
                            interactionNonce++
                            true
                        } else {
                            false
                        }
                    }
                    Key.DirectionLeft -> {
                        if (!controlsVisible) {
                            seekByOffset(-10_000L)
                            true
                        } else {
                            false
                        }
                    }
                    Key.DirectionRight -> {
                        if (!controlsVisible) {
                            seekByOffset(10_000L)
                            true
                        } else {
                            false
                        }
                    }
                    Key.DirectionUp, Key.DirectionDown -> {
                        if (!controlsVisible) {
                            controlsVisible = true
                            interactionNonce++
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            },
    ) {
        if (playbackKey == null) {
            PlayerBackdrop(
                title = state.title,
                posterUrl = artworkUrl,
            )
        }

        if (playbackKey != null) {
            AndroidView(
                factory = { ctx ->
                    (LayoutInflater.from(ctx).inflate(R.layout.view_player, null) as PlayerView).apply {
                        useController = false
                        setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                        this.player = player
                        keepScreenOn = true
                        subtitleView?.apply {
                            setApplyEmbeddedStyles(false)
                            setApplyEmbeddedFontSizes(false)
                            setStyle(
                                CaptionStyleCompat(
                                    android.graphics.Color.WHITE,
                                    android.graphics.Color.TRANSPARENT,
                                    android.graphics.Color.TRANSPARENT,
                                    CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                                    android.graphics.Color.BLACK,
                                    null,
                                ),
                            )
                        }
                    }
                },
                update = { view -> view.player = player },
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (playbackKey != null) {
            PlayerGestureLayer(
                currentPositionMs = { player.currentPosition.coerceAtLeast(0L) },
                durationMs = durationMs,
                brightness = brightness,
                volume = volume,
                onBrightnessChange = ::applyBrightness,
                onVolumeChange = ::applyVolume,
                onSeekTo = { target ->
                    if (durationMs > 0) {
                        val clamped = target.coerceIn(0L, durationMs)
                        val actual = snapSeekTarget(torrentPlayback, clamped, durationMs)
                        AppLog.event(
                            "PLAYER",
                            "seek",
                            fields = mapOf(
                                "fromMs" to player.currentPosition,
                                "requestedMs" to clamped,
                                "toMs" to actual,
                            ),
                        )
                        
                        
                        onPlayheadUpdate(
                            actual,
                            player.bufferedPosition.coerceAtLeast(0L),
                            durationMs,
                        )
                        torrentPlayback?.beginSeek()
                        player.seekTo(actual)
                    }
                },
                onToggleControls = {
                    controlsVisible = !controlsVisible
                    interactionNonce++
                },
                onInteraction = bumpInteraction,
                onHudChange = { hud = it },
                modifier = Modifier.fillMaxSize(),
            )

            if (
                playbackState == Player.STATE_BUFFERING &&
                hasRenderedFrame &&
                !controlsVisible &&
                playerError == null
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(48.dp)
                        .background(Color.Black.copy(alpha = 0.55f), androidx.compose.foundation.shape.CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = StreamlineColors.Gold,
                        strokeWidth = 2.dp,
                    )
                }
            }

            AnimatedVisibility(
                visible = controlsVisible && hasRenderedFrame && playerError == null,
                enter = fadeIn(tween(170)),
                exit = fadeOut(tween(280)),
            ) {
                PlayerControlsOverlay(
                    title = state.title,
                    sourceLabel = buildString {
                        append("Torrent")
                        state.qualityLabel?.takeIf { it.isNotBlank() }?.let { raw ->
                            val quality = VideoQuality.from(raw)
                            append(" · ")
                            append(if (quality == VideoQuality.UNKNOWN) raw else quality.label)
                        }
                        state.sourceLabel?.takeIf { it.isNotBlank() }?.let {
                            append(" · ").append(it)
                        }
                        if (length == "Torrent".length) append(" · sequential")
                    },
                    isPlaying = isPlaying,
                    isBuffering = playbackState == Player.STATE_BUFFERING,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    bufferedMs = bufferedMs,
                    volume = volume,
                    speed = playbackSpeed,
                    isFullscreen = isLandscape,
                    onBack = onBack,
                    onTogglePlay = {
                        AppLog.event(
                            "PLAYER",
                            if (player.isPlaying) "pause_click" else "play_click",
                        )
                        if (player.playbackState == Player.STATE_ENDED) {
                            AppLog.event("PLAYER", "replay_from_end")
                            player.seekTo(0)
                            player.play()
                        } else if (player.isPlaying) {
                            player.pause()
                        } else {
                            player.play()
                        }
                        bumpInteraction()
                    },
                    onSeekBy = { delta ->
                        if (durationMs > 0) {
                            val target = (player.currentPosition + delta).coerceIn(0L, durationMs)
                            val actual = snapSeekTarget(torrentPlayback, target, durationMs)
                            AppLog.event(
                                "PLAYER",
                                "seek_by",
                                fields = mapOf(
                                    "deltaMs" to delta,
                                    "requestedMs" to target,
                                    "toMs" to actual,
                                ),
                            )
                            onPlayheadUpdate(
                                actual,
                                player.bufferedPosition.coerceAtLeast(0L),
                                durationMs,
                            )
                            torrentPlayback?.beginSeek()
                        player.seekTo(actual)
                        }
                        bumpInteraction()
                    },
                    onSeekTo = { target ->
                        if (durationMs > 0) {
                            val clamped = target.coerceIn(0L, durationMs)
                            val actual = snapSeekTarget(torrentPlayback, clamped, durationMs)
                            AppLog.event(
                                "PLAYER",
                                "seek",
                                fields = mapOf(
                                    "fromMs" to player.currentPosition,
                                    "requestedMs" to clamped,
                                    "toMs" to actual,
                                ),
                            )
                            onPlayheadUpdate(
                                actual,
                                player.bufferedPosition.coerceAtLeast(0L),
                                durationMs,
                            )
                            torrentPlayback?.beginSeek()
                        player.seekTo(actual)
                        }
                        bumpInteraction()
                    },
                    onToggleMute = {
                        if (volume > 0.01f) {
                            applyVolume(0f)
                        } else {
                            applyVolume(lastAudibleVolume.coerceAtLeast(0.2f))
                        }
                        bumpInteraction()
                    },
                    onOpenTracks = {
                        showTracks = true
                        bumpInteraction()
                    },
                    onOpenVideos = if (videoFiles.size > 1) {
                        {
                            showVideoPicker = true
                            bumpInteraction()
                        }
                    } else {
                        null
                    },
                    onToggleStats = {
                        showStats = !showStats
                        AppLog.event("PLAYER", "stats_toggle", fields = mapOf("visible" to showStats))
                        bumpInteraction()
                    },
                    onToggleFullscreen = {
                        if (isLandscape) {
                            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                        } else {
                            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        }
                        bumpInteraction()
                    },
                    onInteraction = bumpInteraction,
                    initialFocusRequester = controlsFocus,
                    onControlsFocusChanged = { controlsHaveFocus = it },
                    onPlayFocusChanged = { centralPlayFocused = it },
                )
            }

            if (showStats && stats != null) {
                PlayerStatsOverlay(
                    stats = requireNotNull(stats),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(top = 62.dp, end = 12.dp),
                )
            }
        }

        if (playbackKey == null && playerError == null) {
            TorrentPreparationOverlay(
                state = state.torrentState,
                fatalError = state.fatalError,
                title = state.title,
                posterUrl = artworkUrl,
                firstFramePending = false,
                onBack = onBack,
                onRetry = {
                    autoRetryAttempts = 0
                    onRetryTorrent()
                },
                hasAlternativeStreams = alternatives.isNotEmpty(),
                onChooseAlternative = { showAlternatives = true },
            )
        }

        if (playbackKey != null && !hasRenderedFrame && playerError == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = StreamlineColors.Gold,
                        modifier = Modifier.size(42.dp),
                    )
                    if (resumePositionMs > 0L) {
                        Spacer(Modifier.height(18.dp))
                        Text(
                            text = "Resuming at " + formatResumeTime(resumePositionMs),
                            color = StreamlineColors.TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        val total = (resumeDurationMs.takeIf { it > 0L } ?: durationMs)
                        if (total > 0L) {
                            Spacer(Modifier.height(10.dp))
                            val fraction = (resumePositionMs.toFloat() / total)
                                .coerceIn(0f, 1f)
                            Canvas(
                                modifier = Modifier
                                    .width(240.dp)
                                    .height(12.dp),
                            ) {
                                val y = size.height / 2f
                                drawLine(
                                    color = Color.White.copy(alpha = 0.22f),
                                    start = Offset(0f, y),
                                    end = Offset(size.width, y),
                                    strokeWidth = 3.dp.toPx(),
                                    cap = StrokeCap.Round,
                                )
                                drawLine(
                                    color = StreamlineColors.Gold,
                                    start = Offset(0f, y),
                                    end = Offset(size.width * fraction, y),
                                    strokeWidth = 3.dp.toPx(),
                                    cap = StrokeCap.Round,
                                )
                                drawCircle(
                                    color = StreamlineColors.Gold,
                                    radius = 5.dp.toPx(),
                                    center = Offset(size.width * fraction, y),
                                )
                            }
                        }
                    }
                }
            }
        }

        if (playerError != null) {
            PlayerErrorOverlay(
                message = playerError.orEmpty(),
                onRetry = {
                    autoRetryAttempts = 0
                    playerError = null
                    reloadKey++
                    onRetryTorrent()
                },
                onBack = onBack,
                hasAlternativeStreams = alternatives.isNotEmpty(),
                onChooseAlternative = { showAlternatives = true },
            )
        }

        PlayerGestureHud(
            state = hud,
            modifier = Modifier.fillMaxSize(),
        )
    }

    if (showTracks) {
        TrackSelectionSheet(
            tracks = currentTracks,
            playbackSpeed = playbackSpeed,
            onAudioSelected = { group, index ->
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index))
                    .build()
            },
            onTextSelected = { group, index ->
                val builder = player.trackSelectionParameters.buildUpon()
                if (group == null || index == null) {
                    builder
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                } else {
                    builder
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index))
                }
                player.trackSelectionParameters = builder.build()
            },
            onSpeedSelected = { speed ->
                player.setPlaybackSpeed(speed)
                playbackSpeed = speed
            },
            onDismiss = { showTracks = false },
        )
    }

    if (showVideoPicker && sortedVideoFiles.size > 1) {
        AlertDialog(
            onDismissRequest = { showVideoPicker = false },
            title = {
                Column {
                    Text("Choose video")
                    Text(
                        text = sortedVideoFiles.size.toString() + " video files in this torrent",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            text = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(sortedVideoFiles, key = { it.index }) { video ->
                        val selected = video.index == torrentPlayback?.video?.fileIndex
                        val extension = video.name.substringAfterLast('.', "").uppercase()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (selected) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                    },
                                )
                                .border(
                                    1.dp,
                                    if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                                    },
                                    RoundedCornerShape(14.dp),
                                )
                                .clickable {
                                    showVideoPicker = false
                                    if (!selected) {
                                        AppLog.event(
                                            "PLAYER",
                                            "video_file_select",
                                            fields = mapOf(
                                                "index" to video.index,
                                                "name" to video.name,
                                            ),
                                        )
                                        player.pause()
                                        onSelectVideo(video.index)
                                    }
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                tint = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(24.dp),
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 10.dp),
                            ) {
                                Text(
                                    text = video.name,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 3,
                                )
                                Row(
                                    modifier = Modifier.padding(top = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (extension.isNotBlank()) {
                                        Text(
                                            text = extension,
                                            color = StreamlineColors.Gold,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(StreamlineColors.Gold.copy(alpha = 0.12f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }
                                    Text(
                                        text = formatBytes(video.size),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    if (selected) {
                                        Text(
                                            text = "Playing",
                                            color = MaterialTheme.colorScheme.primary,
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showVideoPicker = false }) {
                    Text("Close")
                }
            },
        )
    }

    if (showAlternatives && alternatives.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showAlternatives = false },
            title = { Text("Other streams") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(alternatives.size) { index ->
                        val alternative = alternatives[index]
                        TextButton(
                            onClick = {
                                showAlternatives = false
                                playerError = null
                                autoRetryAttempts = 0
                                onSelectAlternative(index)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = (alternative.quality ?: "Unknown quality") +
                                        " · " + (alternative.sourceLabel ?: "Unknown source"),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = listOfNotNull(
                                        alternative.seeds?.let { it.toString() + " seeds" },
                                        alternative.sizeLabel,
                                    ).joinToString(" · "),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAlternatives = false }) {
                    Text("Close")
                }
            },
        )
    }
}

@Composable
private fun PlayerBackdrop(
    title: String,
    posterUrl: String?,
) {
    Box(Modifier.fillMaxSize()) {
        Artwork(
            url = posterUrl,
            contentDescription = null,
            fallbackSeed = title,
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(0.dp),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.5f),
                            Color.Black.copy(alpha = 0.74f),
                            StreamlineColors.Ink.copy(alpha = 0.96f),
                        ),
                    ),
                ),
        )
    }
}

@UnstableApi
private fun formatResumeTime(positionMs: Long): String {
    val seconds = (positionMs.coerceAtLeast(0L) / 1_000L).toInt()
    val hours = seconds / 3_600
    val minutes = (seconds % 3_600) / 60
    val remaining = seconds % 60
    return if (hours > 0) {
        hours.toString() + ":" + minutes.toString().padStart(2, '0') + ":" +
            remaining.toString().padStart(2, '0')
    } else {
        minutes.toString() + ":" + remaining.toString().padStart(2, '0')
    }
}

private fun createRenderersFactory(context: Context, hardwareDecoding: Boolean): DefaultRenderersFactory {
    val factory = DefaultRenderersFactory(context)
        .setEnableDecoderFallback(true)
        .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
    if (!hardwareDecoding) {
        factory.setMediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
            val decoders = runCatching {
                MediaCodecUtil.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
            }.getOrDefault(emptyList())
            val software = if (Build.VERSION.SDK_INT >= 29) {
                decoders.filter { it.softwareOnly }
            } else {
                decoders
            }
            software.ifEmpty { decoders }
        }
    }
    return factory
}

private fun friendlyPlaybackError(error: PlaybackException): String = when (error.errorCode) {
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
    PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
    -> "The source could not be reached. Check your connection, the URL, and whether the stream is still live."

    PlaybackException.ERROR_CODE_DECODING_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
    -> "This video or audio codec isn't supported on this device."

    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
    PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
    -> "The source refused or lost access. Try a different stream or check your source settings."

    else -> "The player hit an unexpected problem with this source."
}

private object UriUtils {
    fun encode(value: String): String = android.net.Uri.encode(value)
}


private const val SUBTITLE_URI_PREFIX = "streamline-torrent://subtitle/"



private const val FIRST_FRAME_TIMEOUT_MS = 90_000L
private const val AUTO_RETRY_DELAY_MS = 3_000L
private const val STALL_TIMEOUT_MS = 10_000L
private const val MAX_AUTO_RETRIES = 3
private const val MIN_BUFFER_MS = 5_000
private const val MAX_BUFFER_MS = 120_000
private const val BUFFER_FOR_PLAYBACK_MS = 1_000
private const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 3_000
private const val SEEK_INCREMENT_MS = 10_000L
private const val POSITION_SAVE_INTERVAL_MS = 5_000L
private const val STATS_POLL_INTERVAL_MS = 500L

private val SUBTITLE_LANGUAGES = mapOf(
    "eng" to "en", "english" to "en",
    "ara" to "ar", "arabic" to "ar",
    "fre" to "fr", "french" to "fr",
    "ger" to "de", "german" to "de",
    "spa" to "es", "spanish" to "es",
    "ita" to "it", "italian" to "it",
    "por" to "pt", "portuguese" to "pt",
    "rus" to "ru", "russian" to "ru",
    "tur" to "tr", "turkish" to "tr",
    "hin" to "hi", "hindi" to "hi",
    "jpn" to "ja", "japanese" to "ja",
    "kor" to "ko", "korean" to "ko",
    "chi" to "zh", "chinese" to "zh",
    "dut" to "nl", "dutch" to "nl",
    "pol" to "pl", "polish" to "pl",
    "swe" to "sv", "swedish" to "sv",
)

private fun subtitleMimeType(extension: String): String = when (extension) {
    "vtt" -> "text/vtt"
    "ass", "ssa" -> "text/x-ssa"
    else -> "application/x-subrip"
}


private fun subtitleLanguage(fileName: String): String? = fileName
    .lowercase()
    .split(Regex("[^a-z]+"))
    .firstNotNullOfOrNull { token -> SUBTITLE_LANGUAGES[token] }

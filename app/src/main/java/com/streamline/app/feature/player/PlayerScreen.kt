package com.streamline.app.feature.player

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import androidx.media3.exoplayer.util.EventLogger
import androidx.media3.ui.PlayerView
import com.streamline.app.BuildConfig
import com.streamline.app.R
import com.streamline.app.core.designsystem.Artwork
import com.streamline.app.core.designsystem.StreamlineColors
import com.streamline.app.core.di.AppContainer
import com.streamline.app.core.torrent.TorrentDataSourceFactory
import com.streamline.app.core.torrent.TorrentPlayback
import com.streamline.app.core.torrent.TorrentState
import com.streamline.app.core.util.findActivity
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
        ),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val resumePositionMs = remember(viewModel) { viewModel.resumePositionMs }

    PlayerScreen(
        state = state,
        resumePositionMs = resumePositionMs,
        onSavePosition = viewModel::savePosition,
        onClearPosition = viewModel::clearPosition,
        onBack = onBack,
        onRetryTorrent = viewModel::retryTorrent,
        onPauseDownloads = viewModel::pauseDownloads,
        onResumeDownloads = viewModel::resumeDownloads,
        onAppBackgrounded = viewModel::onAppBackgrounded,
        onAppForegrounded = viewModel::onAppForegrounded,
    )
}

@UnstableApi
@Composable
fun PlayerScreen(
    state: PlayerUiState,
    resumePositionMs: Long,
    onSavePosition: (positionMs: Long, durationMs: Long) -> Unit,
    onClearPosition: () -> Unit,
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
    var interactionNonce by remember { mutableIntStateOf(0) }
    var showTracks by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(state.showStreamStats) }
    LaunchedEffect(state.showStreamStats) { showStats = state.showStreamStats }
    var stats by remember { mutableStateOf<StreamStats?>(null) }
    var hud by remember { mutableStateOf<PlayerHudState?>(null) }
    var playerError by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }
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

    // Attach the Media3 source as soon as the engine has a file mapping. The data source waits
    // for the file/pieces itself, so the player can open while the opening buffer is still filling.
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
        delay(AUTO_RETRY_DELAY_MS)
        autoRetryAttempts++
        playerError = null
        reloadKey++
        onRetryTorrent()
    }

    val artworkUrl = state.artworkUrl
    val playbackKey = torrentPlayback?.let { "torrent:" + it.file.absolutePath }
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
            // A torrent source refills slowly after a seek, so start playback after a short buffer
            // and let the player grow towards two minutes ahead while pieces keep arriving.
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
        player.stop()
        player.clearMediaItems()
        val playback = checkNotNull(torrentPlayback)
        val dataSourceFactory = TorrentDataSourceFactory(playback)
        val item = MediaItem.Builder()
            .setUri("streamline-torrent://local/${UriUtils.encode(playback.fileName)}")
            .setMimeType(playback.mimeType)
            .build()
        val videoSource = ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(item)
        val mediaSource = if (playback.subtitles.isEmpty()) {
            videoSource
        } else {
            // Sideload the sidecars explicitly: the player's own subtitle loader cannot resolve the
            // custom scheme, and this way they are read through the same torrent data source.
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
        player.setMediaSource(mediaSource)
        val resumeAt = pendingResumeMs
        if (resumeAt > 0L) {
            // Seek before prepare: the piece priorities follow the position once loading starts.
            player.seekTo(resumeAt)
            pendingResumeMs = 0L
        }
        player.prepare()
        player.playWhenReady = true
    }

    LaunchedEffect(playbackKey, hasRenderedFrame, playerError) {
        if (playbackKey == null || hasRenderedFrame || playerError != null) {
            return@LaunchedEffect
        }
        delay(FIRST_FRAME_TIMEOUT_MS)
        if (!hasRenderedFrame && playerError == null) {
            playerError =
                "The stream did not produce a video frame. Its container metadata may be " +
                    "unreadable, or the swarm did not provide the first pieces. Try another release."
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
        while (isActive) {
            val status = withContext(Dispatchers.Default) {
                runCatching { handle.status() }.getOrNull()
            }
            stats = status?.let { status ->
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
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
                    if (durationMs > 0) player.seekTo(target.coerceIn(0L, durationMs))
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
                        if (player.playbackState == Player.STATE_ENDED) {
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
                            player.seekTo(target)
                        }
                        bumpInteraction()
                    },
                    onSeekTo = { target ->
                        if (durationMs > 0) player.seekTo(target.coerceIn(0L, durationMs))
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
                    onToggleStats = {
                        showStats = !showStats
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

        if (playbackKey == null || (!hasRenderedFrame && playerError == null)) {
            TorrentPreparationOverlay(
                state = state.torrentState,
                fatalError = state.fatalError,
                title = state.title,
                posterUrl = artworkUrl,
                firstFramePending = playbackKey != null,
                onBack = onBack,
                onRetry = {
                    autoRetryAttempts = 0
                    onRetryTorrent()
                },
            )
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

/** Host + index of a subtitle sidecar; matches the routing rule in `TorrentDataSource`. */
private const val SUBTITLE_URI_PREFIX = "streamline-torrent://subtitle/"

// Start as soon as roughly five seconds of media is buffered; keep filling towards the larger
// buffer while playback runs. This is the quick-start threshold, not the steady-state buffer.
private const val FIRST_FRAME_TIMEOUT_MS = 45_000L
private const val AUTO_RETRY_DELAY_MS = 3_000L
private const val MAX_AUTO_RETRIES = 20
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

/** Best-effort language tag from a sidecar file name like `Movie.eng.forced.srt`. */
private fun subtitleLanguage(fileName: String): String? = fileName
    .lowercase()
    .split(Regex("[^a-z]+"))
    .firstNotNullOfOrNull { token -> SUBTITLE_LANGUAGES[token] }

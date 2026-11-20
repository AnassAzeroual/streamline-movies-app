package com.streamline.app.feature.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import com.streamline.app.core.designsystem.Artwork
import com.streamline.app.core.designsystem.GlassIconButton
import com.streamline.app.core.designsystem.MetaPill
import com.streamline.app.core.designsystem.PrimaryActionButton
import com.streamline.app.core.designsystem.SecondaryActionButton
import com.streamline.app.core.designsystem.StreamlineColors
import com.streamline.app.core.designsystem.formatBytes
import com.streamline.app.core.designsystem.formatPlaybackTime
import com.streamline.app.core.torrent.TorrentState
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@UnstableApi
@Composable
fun PlayerControlsOverlay(
    title: String,
    sourceLabel: String,
    isPlaying: Boolean,
    isBuffering: Boolean,
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long,
    volume: Float,
    speed: Float,
    isFullscreen: Boolean,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleMute: () -> Unit,
    onOpenTracks: () -> Unit,
    onOpenVideos: (() -> Unit)? = null,
    onToggleStats: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier,
    initialFocusRequester: FocusRequester? = null,
    onControlsFocusChanged: (Boolean) -> Unit = {},
    onPlayFocusChanged: (Boolean) -> Unit = {},
) {
    var previewPosition by remember { mutableStateOf<Long?>(null) }
    val shownPosition = previewPosition ?: positionMs
    val playedFraction = getFraction(shownPosition, durationMs)
    val bufferedFraction = getFraction(bufferedMs, durationMs)

    Box(
        modifier = modifier
            .fillMaxSize()
            .onFocusChanged { onControlsFocusChanged(it.hasFocus) },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f)),
                    ),
                ),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayerCircleButton(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                onClick = {
                    onInteraction()
                    onBack()
                },
                size = 42.dp,
                filled = true,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = StreamlineColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Box(
                        Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(StreamlineColors.Gold),
                    )
                    Text(
                        text = sourceLabel,
                        color = StreamlineColors.TextTertiary,
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp),
                        maxLines = 1,
                    )
                }
            }
            PlayerCircleButton(
                icon = if (isFullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                contentDescription = if (isFullscreen) "Exit fullscreen" else "Enter fullscreen",
                onClick = {
                    onInteraction()
                    onToggleFullscreen()
                },
                filled = true,
            )
        }

        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            PlayerCircleButton(
                icon = Icons.Rounded.Replay10,
                contentDescription = "Rewind 10 seconds",
                onClick = {
                    onInteraction()
                    onSeekBy(-10_000)
                },
                size = 50.dp,
            )
            var playFocused by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .then(
                        if (initialFocusRequester != null) {
                            Modifier.focusRequester(initialFocusRequester)
                        } else {
                            Modifier
                        },
                    )
                    .size(72.dp)
                    .onFocusChanged {
                        playFocused = it.isFocused
                        onPlayFocusChanged(it.isFocused)
                    }
                    .clip(CircleShape)
                    .background(
                        if (playFocused) StreamlineColors.Gold.copy(alpha = 0.22f)
                        else Color.Black.copy(alpha = 0.52f),
                    )
                    .border(
                        if (playFocused) 2.dp else 1.dp,
                        if (playFocused) StreamlineColors.Gold else StreamlineColors.Gold.copy(alpha = 0.55f),
                        CircleShape,
                    )
                    .clickable {
                        onInteraction()
                        onTogglePlay()
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (isBuffering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(27.dp),
                        color = StreamlineColors.Gold,
                        strokeWidth = 2.4.dp,
                    )
                } else {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = StreamlineColors.Gold,
                        modifier = Modifier.size(34.dp),
                    )
                }
            }
            PlayerCircleButton(
                icon = Icons.Rounded.Forward10,
                contentDescription = "Forward 10 seconds",
                onClick = {
                    onInteraction()
                    onSeekBy(10_000)
                },
                size = 50.dp,
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            PlayerProgressSlider(
                playedFraction = playedFraction,
                bufferedFraction = bufferedFraction,
                enabled = durationMs > C.TIME_UNSET && durationMs > 0,
                onPreview = { fraction ->
                    previewPosition = (fraction * durationMs).toLong()
                    onInteraction()
                },
                onClearPreview = { previewPosition = null },
                onSeek = { fraction ->
                    previewPosition = null
                    onSeekTo((fraction * durationMs).toLong())
                    onInteraction()
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatPlaybackTime(shownPosition),
                    color = if (previewPosition != null) StreamlineColors.Gold else StreamlineColors.TextPrimary,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 11.sp,
                        letterSpacing = 0.4.sp,
                    ),
                )
                Text(
                    text = "  /  ${if (durationMs > 0) formatPlaybackTime(durationMs) else "—:—"}",
                    color = StreamlineColors.TextTertiary,
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                )
                Spacer(Modifier.weight(1f))
                if (previewPosition != null) {
                    MetaPill(text = "Preview", tone = StreamlineColors.Gold)
                }
            }
            Spacer(Modifier.height(7.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                PlayerCircleButton(
                    icon = if (volume <= 0.01f) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                    contentDescription = if (volume <= 0.01f) "Unmute" else "Mute",
                    onClick = {
                        onInteraction()
                        onToggleMute()
                    },
                    filled = true,
                )
                Spacer(Modifier.weight(1f))
                PlayerCircleButton(
                    icon = Icons.Rounded.Insights,
                    contentDescription = "Toggle stream statistics",
                    onClick = {
                        onInteraction()
                        onToggleStats()
                    },
                    size = 38.dp,
                    filled = true,
                )
                PlayerCircleButton(
                    icon = Icons.Rounded.Speed,
                    contentDescription = "Playback speed",
                    onClick = {
                        onInteraction()
                        onOpenTracks()
                    },
                    size = 38.dp,
                    filled = true,
                )
                PlayerCircleButton(
                    icon = Icons.Rounded.Subtitles,
                    contentDescription = "Audio and subtitles",
                    onClick = {
                        onInteraction()
                        onOpenTracks()
                    },
                    size = 38.dp,
                    filled = true,
                )
                if (onOpenVideos != null) {
                    PlayerCircleButton(
                        icon = Icons.Rounded.VideoLibrary,
                        contentDescription = "Choose video file",
                        onClick = {
                            onInteraction()
                            onOpenVideos()
                        },
                        size = 38.dp,
                        filled = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 44.dp,
    filled: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(size)
            .onFocusChanged { focused = it.isFocused }
            .clip(CircleShape)
            .background(
                if (focused) StreamlineColors.Gold.copy(alpha = 0.22f)
                else if (filled) Color.White.copy(alpha = 0.09f)
                else Color.Transparent,
            )
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) StreamlineColors.Gold else Color.Transparent,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White.copy(alpha = 0.94f),
            modifier = Modifier.size(if (size > 50.dp) 27.dp else 23.dp),
        )
    }
}

@Composable
private fun PlayerProgressSlider(
    playedFraction: Float,
    bufferedFraction: Float,
    enabled: Boolean,
    onPreview: (Float) -> Unit,
    onClearPreview: () -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragging by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .height(32.dp)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = playedFraction,
                    range = 0f..1f,
                )
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var currentFraction = (down.position.x / size.width).coerceIn(0f, 1f)
                    dragging = true
                    onPreview(currentFraction)

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        currentFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        onPreview(currentFraction)
                        change.consume()
                    }

                    dragging = false
                    onSeek(currentFraction)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp),
        ) {
            val centerY = size.height / 2f
            val trackHeight = if (dragging) 5.dp.toPx() else 3.dp.toPx()
            val trackColor = Color.White.copy(alpha = if (enabled) 0.18f else 0.08f)
            val bufferedColor = Color.White.copy(alpha = if (enabled) 0.32f else 0.12f)

            drawLine(
                color = trackColor,
                start = Offset(0f, centerY),
                end = Offset(size.width, centerY),
                strokeWidth = trackHeight,
                cap = StrokeCap.Round,
            )
            if (bufferedFraction > 0f) {
                drawLine(
                    color = bufferedColor,
                    start = Offset(0f, centerY),
                    end = Offset(size.width * bufferedFraction.coerceIn(0f, 1f), centerY),
                    strokeWidth = trackHeight,
                    cap = StrokeCap.Round,
                )
            }
            if (playedFraction > 0f) {
                drawLine(
                    color = StreamlineColors.Gold,
                    start = Offset(0f, centerY),
                    end = Offset(size.width * playedFraction.coerceIn(0f, 1f), centerY),
                    strokeWidth = trackHeight,
                    cap = StrokeCap.Round,
                )
            }
            if (enabled) {
                drawCircle(
                    color = StreamlineColors.Gold,
                    radius = if (dragging) 7.dp.toPx() else 5.dp.toPx(),
                    center = Offset(size.width * playedFraction.coerceIn(0f, 1f), centerY),
                )
                drawCircle(
                    color = StreamlineColors.Ink.copy(alpha = 0.5f),
                    radius = if (dragging) 2.8.dp.toPx() else 2.dp.toPx(),
                    center = Offset(size.width * playedFraction.coerceIn(0f, 1f), centerY),
                )
            }
        }
    }
}

sealed interface PlayerHudState {
    data class Seek(val targetMs: Long, val deltaMs: Long) : PlayerHudState
    data class Volume(val level: Float) : PlayerHudState
    data class Brightness(val level: Float) : PlayerHudState
    data class DoubleTapSeek(val deltaMs: Long, val targetMs: Long) : PlayerHudState
}

@Composable
fun PlayerGestureHud(
    state: PlayerHudState?,
    modifier: Modifier = Modifier,
) {
    if (state == null) return
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(Color.Black.copy(alpha = 0.76f))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(18.dp))
                .padding(horizontal = 22.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (state) {
                is PlayerHudState.Seek -> {
                    Text(
                        text = (if (state.deltaMs >= 0) "+" else "−") + formatDelta(state.deltaMs),
                        color = StreamlineColors.Gold,
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = formatPlaybackTime(state.targetMs),
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                    )
                }
                is PlayerHudState.Volume -> {
                    Icon(
                        imageVector = if (state.level <= 0.01f) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.height(9.dp))
                    LevelBar(level = state.level)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "${(state.level * 100).roundToInt()}%",
                        color = StreamlineColors.TextSecondary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                is PlayerHudState.Brightness -> {
                    Icon(
                        imageVector = Icons.Rounded.BrightnessHigh,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.height(9.dp))
                    LevelBar(level = state.level)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "${(state.level * 100).roundToInt()}%",
                        color = StreamlineColors.TextSecondary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                is PlayerHudState.DoubleTapSeek -> {
                    Icon(
                        imageVector = if (state.deltaMs >= 0) Icons.Rounded.Forward10 else Icons.Rounded.Replay10,
                        contentDescription = null,
                        tint = StreamlineColors.Gold,
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = if (state.deltaMs >= 0) "+10 seconds" else "−10 seconds",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun LevelBar(level: Float) {
    Box(
        modifier = Modifier
            .width(112.dp)
            .height(4.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.2f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(level.coerceIn(0f, 1f))
                .height(4.dp)
                .clip(CircleShape)
                .background(StreamlineColors.Gold),
        )
    }
}

@Composable
fun TorrentPreparationOverlay(
    state: TorrentState,
    fatalError: String?,
    title: String,
    posterUrl: String? = null,
    firstFramePending: Boolean = false,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    hasAlternativeStreams: Boolean,
    onChooseAlternative: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val heading: String
    val message: String
    var progress = 0f
    var stats: String? = null
    var failed = false
    val metadataStage = state is TorrentState.Buffering && state.stage == "container metadata"
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(1_000)
            elapsedSeconds++
        }
    }

    when {
        fatalError != null -> {
            heading = "No stream here"
            message = fatalError
            failed = true
        }
        state is TorrentState.Failed -> {
            heading = "This stream could not start"
            message = state.message
            failed = true
        }
        state is TorrentState.FetchingMetadata -> {
            heading = "Opening the stream"
            message = "Finding peers and torrent metadata. " + elapsedSeconds + "s elapsed."
        }
        state is TorrentState.Buffering -> {
            heading = "Opening the stream"
            progress = state.progress
            message = if (state.stage == "container metadata") {
                "Reading the MP4 container metadata from the end of the file."
            } else {
                "Buffering the opening seconds. Playback starts as soon as the first frame is ready."
            }
            
            
            val openingTarget = state.targetBytes.coerceAtLeast(1L)
            val openingReady = state.downloadedBytes.coerceAtMost(openingTarget)
            stats = buildString {
                append(formatBytes(openingReady))
                append(" of ")
                append(formatBytes(openingTarget))
                append(" opening buffer")
                if (state.downloadRate > 0) {
                    append("  ·  ")
                    append(formatBytes(state.downloadRate))
                    append("/s")
                    val remaining = (openingTarget - openingReady).coerceAtLeast(0L)
                    if (remaining > 0L) {
                        append("  ·  ~")
                        append(formatEta(remaining / state.downloadRate))
                        append(" left")
                    }
                }
                append("  ·  ")
                append(state.seeds)
                append(if (state.seeds == 1) " seed" else " seeds")
                append("  ·  ")
                append(state.peers)
                append(if (state.peers == 1) " peer" else " peers")
            }
        }
        firstFramePending -> {
            heading = "Opening the stream"
            message = "Preparing the first video frame. " + elapsedSeconds + "s elapsed."
        }
        else -> {
            heading = "Opening the stream"
            message = "Preparing the stream…"
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.5f),
                        StreamlineColors.Ink.copy(alpha = 0.92f),
                        Color.Black.copy(alpha = 0.96f),
                    ),
                ),
            ),
    ) {
        if (posterUrl != null) {
            Artwork(
                url = posterUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(0.dp),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.58f)),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 18.dp, vertical = 8.dp),
        ) {
            GlassIconButton(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                onClick = onBack,
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MetaPill(
                text = when {
                    firstFramePending -> "Torrent · Opening"
                    state is TorrentState.Buffering -> "Torrent · Sequential"
                    else -> "Torrent engine"
                },
                tone = StreamlineColors.Gold,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = title,
                color = StreamlineColors.TextPrimary,
                style = MaterialTheme.typography.headlineLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = heading.uppercase(Locale.US),
                color = StreamlineColors.Gold,
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
            )
            Spacer(Modifier.height(13.dp))

            if (failed) {
                Icon(
                    imageVector = Icons.Rounded.WifiOff,
                    contentDescription = null,
                    tint = StreamlineColors.Danger,
                    modifier = Modifier.size(36.dp),
                )
                Spacer(Modifier.height(10.dp))
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(30.dp),
                    color = StreamlineColors.Gold,
                    strokeWidth = 2.dp,
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = message,
                color = StreamlineColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            if (!failed && state is TorrentState.FetchingMetadata) {
                Spacer(Modifier.height(18.dp))
                LinearProgressIndicator(
                    modifier = Modifier.width(180.dp),
                    color = StreamlineColors.Gold,
                    trackColor = Color.White.copy(alpha = 0.12f),
                )
            }
            if (
                !failed &&
                firstFramePending &&
                state !is TorrentState.Buffering &&
                state !is TorrentState.FetchingMetadata
            ) {
                Spacer(Modifier.height(18.dp))
                LinearProgressIndicator(
                    modifier = Modifier.width(180.dp),
                    color = StreamlineColors.Gold,
                    trackColor = Color.White.copy(alpha = 0.12f),
                )
            }
            if (!failed && metadataStage) {
                Spacer(Modifier.height(18.dp))
                LinearProgressIndicator(
                    modifier = Modifier.width(180.dp),
                    color = StreamlineColors.Gold,
                    trackColor = Color.White.copy(alpha = 0.12f),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Waiting for container metadata",
                    color = StreamlineColors.TextTertiary,
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.2.sp),
                )
            }
            if (!failed && state is TorrentState.Buffering && !metadataStage) {
                Spacer(Modifier.height(18.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .height(5.dp)
                            .clip(CircleShape)
                            .background(StreamlineColors.Gold),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "${(progress * 100).roundToInt()}% of opening buffer  ·  ${stats.orEmpty()}",
                    color = StreamlineColors.TextTertiary,
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.2.sp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 2,
                )
            }
            Spacer(Modifier.height(24.dp))
            if (failed) {
                if (hasAlternativeStreams) {
                    PrimaryActionButton(
                        text = "Choose another stream",
                        onClick = onChooseAlternative,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    SecondaryActionButton(
                        text = "Back to library",
                        onClick = onBack,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    PrimaryActionButton(
                        text = "Back to library",
                        onClick = onBack,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (fatalError == null) {
                    Spacer(Modifier.height(8.dp))
                    SecondaryActionButton(
                        text = "Try this torrent again",
                        onClick = onRetry,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                SecondaryActionButton(
                    text = "Cancel",
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Streamline streams sequentially from the first piece and prioritises the end " +
                    "for container metadata. No file is exposed outside app storage.",
                color = StreamlineColors.TextTertiary.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelSmall.copy(lineHeight = 15.sp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
fun PlayerErrorOverlay(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    hasAlternativeStreams: Boolean,
    onChooseAlternative: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.84f))
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Rounded.WifiOff,
                contentDescription = null,
                tint = StreamlineColors.Danger,
                modifier = Modifier.size(38.dp),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Playback interrupted",
                color = StreamlineColors.TextPrimary,
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                color = StreamlineColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(22.dp))
            PrimaryActionButton(
                text = if (hasAlternativeStreams) "Choose another stream" else "Try again",
                onClick = if (hasAlternativeStreams) onChooseAlternative else onRetry,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            SecondaryActionButton(
                text = if (hasAlternativeStreams) "Try this stream again" else "Back to library",
                onClick = if (hasAlternativeStreams) onRetry else onBack,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackSelectionSheet(
    tracks: Tracks,
    playbackSpeed: Float,
    onAudioSelected: (Tracks.Group, Int) -> Unit,
    onTextSelected: (Tracks.Group?, Int?) -> Unit,
    onSpeedSelected: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val audioOptions = remember(tracks) { buildTrackOptions(tracks, C.TRACK_TYPE_AUDIO) }
    val textOptions = remember(tracks) { buildTrackOptions(tracks, C.TRACK_TYPE_TEXT) }
    val textSelected = textOptions.any { it.selected }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = StreamlineColors.Surface,
        contentColor = StreamlineColors.TextPrimary,
        scrimColor = Color.Black.copy(alpha = 0.7f),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f)),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 600.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 22.dp)
                .padding(top = 8.dp, bottom = 28.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Audio & subtitles",
                        color = StreamlineColors.TextPrimary,
                        style = MaterialTheme.typography.headlineLarge,
                    )
                    Text(
                        text = "Tune the room to your language.",
                        color = StreamlineColors.TextTertiary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                GlassIconButton(
                    icon = Icons.Rounded.Close,
                    contentDescription = "Close",
                    onClick = onDismiss,
                    size = 38.dp,
                )
            }

            Spacer(Modifier.height(22.dp))
            TrackSectionTitle("AUDIO")
            Spacer(Modifier.height(8.dp))
            if (audioOptions.isEmpty()) {
                EmptyTrackText("No alternate audio tracks in this stream.")
            } else {
                audioOptions.forEach { option ->
                    TrackOptionRow(
                        option = option,
                        onClick = {
                            option.group?.let { group -> onAudioSelected(group, option.index) }
                        },
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            TrackSectionTitle("SUBTITLES")
            Spacer(Modifier.height(8.dp))
            TrackOptionRow(
                option = TrackOption(
                    group = null,
                    index = -1,
                    title = "Off",
                    detail = "Hide subtitles",
                    selected = !textSelected,
                    supported = true,
                ),
                onClick = { onTextSelected(null, null) },
            )
            if (textOptions.isEmpty()) {
                EmptyTrackText("No subtitle tracks in this stream.")
            } else {
                textOptions.forEach { option ->
                    TrackOptionRow(
                        option = option,
                        onClick = { onTextSelected(option.group, option.index) },
                    )
                }
            }

            Spacer(Modifier.height(22.dp))
            TrackSectionTitle("PLAYBACK SPEED")
            Spacer(Modifier.height(9.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)) { speed ->
                    val selected = kotlin.math.abs(playbackSpeed - speed) < 0.01f
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (selected) StreamlineColors.Gold else Color.White.copy(alpha = 0.055f),
                            )
                            .border(
                                1.dp,
                                if (selected) Color.Transparent else Color.White.copy(alpha = 0.08f),
                                RoundedCornerShape(50),
                            )
                            .clickable { onSpeedSelected(speed) }
                            .padding(horizontal = 15.dp, vertical = 9.dp),
                    ) {
                        Text(
                            text = "${formatSpeed(speed)}×",
                            color = if (selected) StreamlineColors.Ink else StreamlineColors.TextSecondary,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                text = "Subtitle availability depends on the container and the source. " +
                    "Streamline does not generate or download subtitles.",
                color = StreamlineColors.TextTertiary,
                style = MaterialTheme.typography.labelSmall.copy(lineHeight = 15.sp),
            )
        }
    }
}

private data class TrackOption(
    val group: Tracks.Group?,
    val index: Int,
    val title: String,
    val detail: String,
    val selected: Boolean,
    val supported: Boolean,
)

private fun buildTrackOptions(tracks: Tracks, type: Int): List<TrackOption> {
    val options = mutableListOf<TrackOption>()
    tracks.groups.filter { it.type == type }.forEach { group ->
        for (index in 0 until group.length) {
            val format = group.getTrackFormat(index)
            val language = format.language
                ?.takeIf { it.isNotBlank() && it != "und" }
                ?.let { tag -> Locale.forLanguageTag(tag).displayLanguage.takeIf(String::isNotBlank) }
            val label = format.label?.takeIf(String::isNotBlank)
                ?: language
                ?: "Track ${index + 1}"
            val codec = format.sampleMimeType
                ?.substringAfter('/')
                ?.replace("x-", "")
                ?.uppercase(Locale.US)
            val channels = format.channelCount.takeIf { it > 2 }?.let { "${it}ch" }
            val detail = listOfNotNull(codec, channels).joinToString(" · ").ifBlank { "Stereo" }
            options += TrackOption(
                group = group,
                index = index,
                title = label,
                detail = detail,
                selected = group.isTrackSelected(index),
                supported = group.isTrackSupported(index),
            )
        }
    }
    return options
}

@Composable
private fun TrackSectionTitle(text: String) {
    Text(
        text = text,
        color = StreamlineColors.TextTertiary,
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.8.sp),
    )
}

@Composable
private fun EmptyTrackText(text: String) {
    Text(
        text = text,
        color = StreamlineColors.TextTertiary,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(vertical = 5.dp),
    )
}

@Composable
private fun TrackOptionRow(
    option: TrackOption,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(11.dp))
            .background(
                if (focused) StreamlineColors.Gold.copy(alpha = 0.16f)
                else if (option.selected) StreamlineColors.Gold.copy(alpha = 0.09f)
                else Color.Transparent,
            )
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) StreamlineColors.Gold else Color.Transparent,
                shape = RoundedCornerShape(11.dp),
            )
            .clickable(enabled = option.supported, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = option.title + if (!option.supported) " (unsupported)" else "",
                color = if (option.supported) StreamlineColors.TextPrimary else StreamlineColors.TextTertiary,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = option.detail,
                color = StreamlineColors.TextTertiary,
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.3.sp),
            )
        }
        if (option.selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = "Selected",
                tint = StreamlineColors.Gold,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private fun getFraction(value: Long, duration: Long): Float {
    if (duration <= 0L) return 0f
    return (value.toDouble() / duration.toDouble()).toFloat().coerceIn(0f, 1f)
}

private fun formatEta(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0L)
    return when {
        safe < 60L -> safe.toString() + "s"
        safe < 3600L -> (safe / 60L).toString() + "m " + (safe % 60L) + "s"
        else -> (safe / 3600L).toString() + "h " + ((safe % 3600L) / 60L) + "m"
    }
}

private fun formatSpeed(speed: Float): String {
    val rounded = (speed * 100).roundToInt() / 100f
    return if (rounded % 1f == 0f) rounded.toInt().toString() else rounded.toString()
}

private fun formatDelta(deltaMs: Long): String {
    val seconds = (abs(deltaMs) / 1000).coerceAtLeast(1)
    return if (seconds >= 60) {
        "${seconds / 60}m ${seconds % 60}s"
    } else {
        "${seconds}s"
    }
}


@Composable
fun PlayerGestureLayer(
    currentPositionMs: () -> Long,
    durationMs: Long,
    brightness: Float,
    volume: Float,
    onBrightnessChange: (Float) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleControls: () -> Unit,
    onInteraction: () -> Unit,
    onHudChange: (PlayerHudState?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragMode by remember { mutableStateOf<GestureMode>(GestureMode.NONE) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val liveBrightness = rememberUpdatedState(brightness)
    val liveVolume = rememberUpdatedState(volume)
    val livePosition = rememberUpdatedState(currentPositionMs)
    val liveDuration = rememberUpdatedState(durationMs)

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var lastTapTime = 0L
                var lastTapPosition = Offset.Zero
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startX = down.position.x
                    var totalX = 0f
                    var totalY = 0f
                    var basePosition = 0L
                    var baseBrightness = 0f
                    var baseVolume = 0f
                    var targetPosition = 0L
                    var dragging = false
                    var upTime = down.uptimeMillis

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            upTime = change.uptimeMillis
                            break
                        }
                        val amount = change.positionChange()
                        totalX += amount.x
                        totalY += amount.y
                        if (!dragging && (abs(totalX) + abs(totalY)) > viewConfiguration.touchSlop) {
                            dragging = true
                            basePosition = livePosition.value()
                            baseBrightness = liveBrightness.value
                            baseVolume = liveVolume.value
                            dragMode = GestureMode.UNDECIDED
                            onInteraction()
                        }
                        if (dragging) {
                            change.consume()
                            if (dragMode == GestureMode.UNDECIDED) {
                                dragMode = if (abs(totalX) > abs(totalY)) {
                                    GestureMode.SEEK
                                } else if (startX < size.width / 2f) {
                                    GestureMode.BRIGHTNESS
                                } else {
                                    GestureMode.VOLUME
                                }
                            }
                            when (dragMode) {
                                GestureMode.SEEK -> {
                                    val duration = liveDuration.value
                                    if (duration > 0) {
                                        val delta = (totalX / size.width * SEEK_GESTURE_SPAN_MS).toLong()
                                        targetPosition = (basePosition + delta).coerceIn(0L, duration)
                                        onHudChange(PlayerHudState.Seek(targetPosition, targetPosition - basePosition))
                                    }
                                }
                                GestureMode.BRIGHTNESS -> {
                                    val next = (baseBrightness - totalY / size.height).coerceIn(0f, 1f)
                                    onBrightnessChange(next)
                                    onHudChange(PlayerHudState.Brightness(next))
                                }
                                GestureMode.VOLUME -> {
                                    val next = (baseVolume - totalY / size.height).coerceIn(0f, 1f)
                                    onVolumeChange(next)
                                    onHudChange(PlayerHudState.Volume(next))
                                }
                                else -> Unit
                            }
                        }
                    }

                    if (dragging) {
                        if (dragMode == GestureMode.SEEK && liveDuration.value > 0) {
                            onSeekTo(targetPosition)
                        }
                        dragMode = GestureMode.NONE
                        scope.launch {
                            kotlinx.coroutines.delay(700)
                            onHudChange(null)
                        }
                    } else {
                        val isDoubleTap = upTime - lastTapTime in 1..viewConfiguration.doubleTapTimeoutMillis &&
                            (down.position - lastTapPosition).getDistance() < viewConfiguration.touchSlop * 4
                        if (isDoubleTap) {
                            lastTapTime = 0L
                            onInteraction()
                            val delta = if (down.position.x < size.width / 2f) -10_000L else 10_000L
                            val current = livePosition.value()
                            val maxPosition = liveDuration.value.coerceAtLeast(0L)
                            val target = if (maxPosition > 0) {
                                (current + delta).coerceIn(0L, maxPosition)
                            } else {
                                (current + delta).coerceAtLeast(0L)
                            }
                            onSeekTo(target)
                            onHudChange(PlayerHudState.DoubleTapSeek(delta, target))
                            scope.launch {
                                kotlinx.coroutines.delay(750)
                                onHudChange(null)
                            }
                        } else {
                            lastTapTime = upTime
                            lastTapPosition = down.position
                            onInteraction()
                            onToggleControls()
                        }
                    }
                }
            },
    )
}

private enum class GestureMode { NONE, UNDECIDED, SEEK, VOLUME, BRIGHTNESS }

private const val SEEK_GESTURE_SPAN_MS = 120_000f


data class PieceMap(
    val downloaded: BooleanArray,
    val currentIndex: Int = -1,
) {
    val downloadedCount: Int get() = downloaded.count { it }
    val progress: Float
        get() = if (downloaded.isEmpty()) 0f else downloadedCount.toFloat() / downloaded.size
}


data class StreamStats(
    val seeds: Int,
    val peers: Int,
    val downloadRate: Long,
    val uploadRate: Long,
    val progress: Float,
    val videoFormat: String?,
    val droppedFrames: Int,
    val bufferedAheadMs: Long,
    val pieceMap: PieceMap? = null,
)

@Composable
fun PlayerStatsOverlay(
    stats: StreamStats,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.72f))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        StatsRow("Swarm", stats.seeds.toString() + " seeds · " + stats.peers + " peers")
        StatsRow("Download", formatBytes(stats.downloadRate) + "/s")
        StatsRow("Upload", formatBytes(stats.uploadRate) + "/s")
        StatsRow("Wanted", (stats.progress * 100).roundToInt().toString() + "%")
        stats.pieceMap?.let { pieces ->
            Spacer(Modifier.height(5.dp))
            Text(
                text = "PIECES",
                color = StreamlineColors.TextTertiary,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    letterSpacing = 1.2.sp,
                ),
            )
            Spacer(Modifier.height(4.dp))
            PieceProgressBar(pieces)
            Spacer(Modifier.height(2.dp))
            Text(
                text = pieces.downloadedCount.toString() + " / " + pieces.downloaded.size +
                    " · " + (pieces.progress * 100).roundToInt() + "%" +
                    (pieces.currentIndex.takeIf { it >= 0 }
                        ?.let { " · playhead " + (it + 1) }
                        ?: ""),
                color = StreamlineColors.TextTertiary,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            )
            Text(
                text = "green = downloaded · gold = playhead",
                color = StreamlineColors.TextTertiary.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
            )
        }
        StatsRow("Buffer", formatPlaybackTime(stats.bufferedAheadMs) + " ahead")
        stats.videoFormat?.let { StatsRow("Video", it) }
        StatsRow("Dropped", stats.droppedFrames.toString() + " frames")
    }
}

@Composable
private fun PieceProgressBar(pieces: PieceMap) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(16.dp)
            .clip(RoundedCornerShape(4.dp)),
    ) {
        if (pieces.downloaded.isEmpty()) return@Canvas
        drawRect(color = Color.White.copy(alpha = 0.08f))
        val slot = size.width / pieces.downloaded.size
        val segmentWidth = slot.coerceAtLeast(0.8f)
        pieces.downloaded.forEachIndexed { index, downloaded ->
            if (downloaded) {
                drawRect(
                    color = Color(0xFF6DDC8A),
                    topLeft = Offset(index * slot, 0f),
                    size = Size(segmentWidth, size.height),
                )
            }
        }
        val current = pieces.currentIndex
        if (current in pieces.downloaded.indices) {
            drawRect(
                color = StreamlineColors.Gold,
                topLeft = Offset(current * slot, 0f),
                size = Size(segmentWidth.coerceAtLeast(2f), size.height),
            )
        }
    }
}

@Composable
private fun StatsRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            color = StreamlineColors.TextTertiary,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            modifier = Modifier.width(74.dp),
        )
        Text(
            text = value,
            color = StreamlineColors.TextPrimary,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
        )
    }
}

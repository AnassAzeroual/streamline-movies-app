package com.streamline.app.feature.magnets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.streamline.app.core.designsystem.Artwork
import com.streamline.app.core.designsystem.MetaPill
import com.streamline.app.core.designsystem.StreamlineColors
import com.streamline.app.core.designsystem.isTelevision
import com.streamline.app.core.util.PlaybackQualityAdvisor
import com.streamline.app.core.util.QualityRules
import com.streamline.app.domain.model.MagnetRelease
import com.streamline.app.domain.model.MagnetTitle
import com.streamline.app.domain.model.VideoQuality
import kotlinx.coroutines.delay

enum class QualityAction { PLAY, DOWNLOAD }

@Composable
fun PlaybackQualitySheet(
    item: MagnetTitle,
    action: QualityAction,
    onSelect: (MagnetRelease) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val releases = remember(item.id) {
        item.playableReleases().sortedWith(
            compareByDescending<MagnetRelease> { it.videoQuality.height }
                .thenBy { it.isMatroska },
        )
    }
    val screenQuality = remember { PlaybackQualityAdvisor.screenQuality(context) }
    val networkQuality = remember { PlaybackQualityAdvisor.networkQuality(context) }
    val transport = remember { PlaybackQualityAdvisor.transportLabel(context) }
    val autoPick = remember(releases, screenQuality, networkQuality, action) {
        if (action == QualityAction.PLAY) {
            QualityRules.choose(releases, screenQuality, networkQuality)
        } else {
            
            QualityRules.choose(releases, VideoQuality.UHD_2160, networkQuality)
        }
    }
    val qualityFocus = remember(releases) {
        List(releases.size + 1) { FocusRequester() }
    }
    var focusedQualityIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) {
        delay(450)
        val focusIndex = when {
            autoPick != null -> 0
            releases.isNotEmpty() -> 1
            else -> -1
        }
        if (focusIndex >= 0) {
            runCatching { qualityFocus[focusIndex].requestFocus() }
            focusedQualityIndex = focusIndex
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
                .padding(if (isTelevision()) 48.dp else 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxWidth()
                    .heightIn(max = 700.dp),
                shape = RoundedCornerShape(24.dp),
                color = StreamlineColors.Surface,
                border = BorderStroke(1.dp, StreamlineColors.Hairline),
            ) {
                Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .onPreviewKeyEvent { event ->
                    val current = focusedQualityIndex ?: if (event.key == Key.DirectionDown) 0 else null
                    if (
                        event.type == KeyEventType.KeyDown &&
                        current != null &&
                        (event.key == Key.DirectionDown || event.key == Key.DirectionUp)
                    ) {
                        val next = if (event.key == Key.DirectionDown) current + 1 else current - 1
                        if (next in qualityFocus.indices) {
                            qualityFocus[next].requestFocus()
                            true
                        } else {
                            false
                        }
                    } else {
                        false
                    }
                }
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 6.dp, bottom = 20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(
                    url = item.posterUrl,
                    contentDescription = "Poster for " + item.title,
                    fallbackSeed = item.title,
                    modifier = Modifier
                        .width(58.dp)
                        .height(87.dp),
                    shape = RoundedCornerShape(12.dp),
                )
                Spacer(Modifier.width(13.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        color = StreamlineColors.TextPrimary,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        MetaPill(
                            text = item.source.label,
                            tone = StreamlineColors.Gold,
                        )
                        Text(
                            text = releases.size.toString() + if (releases.size == 1) " quality" else " qualities",
                            color = StreamlineColors.TextTertiary,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                text = if (action == QualityAction.PLAY) "PLAYBACK QUALITY" else "DOWNLOAD QUALITY",
                color = StreamlineColors.Gold,
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.8.sp),
            )
            Spacer(Modifier.height(9.dp))

            AutoQualityRow(
                autoPick = autoPick,
                screenQuality = screenQuality,
                networkQuality = networkQuality,
                transport = transport,
                action = action,
                onClick = { autoPick?.let(onSelect) },
                onFocused = { focused ->
                    if (focused) focusedQualityIndex = 0
                },
                modifier = Modifier.focusRequester(qualityFocus[0]),
            )

            Spacer(Modifier.height(17.dp))
            Text(
                text = "AVAILABLE RELEASES",
                color = StreamlineColors.TextTertiary,
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.6.sp),
            )
            Spacer(Modifier.height(8.dp))

            releases.forEachIndexed { index, release ->
                QualityRow(
                    release = release,
                    bestFit = autoPick?.magnetUrl == release.magnetUrl,
                    action = action,
                    onClick = { onSelect(release) },
                    onFocused = { focused ->
                        if (focused) focusedQualityIndex = index + 1
                    },
                    modifier = Modifier.focusRequester(qualityFocus[index + 1]),
                )
                Spacer(Modifier.height(7.dp))
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = if (action == QualityAction.PLAY) {
                    "Auto uses this screen (${screenQuality.displayLabel}) and the current " +
                        "$transport estimate" +
                        (networkQuality?.let { " (up to ${it.displayLabel})" } ?: "") +
                        " and prefers releases with live seeds. It selects before playback starts; " +
                        "choose a release above to override it."
                } else {
                    "Auto uses the current $transport estimate" +
                        (networkQuality?.let { " (up to ${it.displayLabel})" } ?: "") +
                        " and prefers releases with live seeds. It picks the file before the " +
                        "download starts; choose a release above to override it."
                },
                color = StreamlineColors.TextTertiary,
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 17.sp),
            )
                }
            }
        }
    }
}

@Composable
private fun AutoQualityRow(
    autoPick: MagnetRelease?,
    screenQuality: VideoQuality,
    networkQuality: VideoQuality?,
    transport: String,
    action: QualityAction,
    onClick: () -> Unit,
    onFocused: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged {
                focused = it.isFocused
                onFocused(it.isFocused)
            }
            .clip(shape)
            .background(StreamlineColors.Gold.copy(alpha = if (focused) 0.18f else 0.12f))
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) StreamlineColors.Gold else StreamlineColors.Gold.copy(alpha = 0.55f),
                shape,
            )
            .clickable(enabled = autoPick != null, onClick = onClick)
            .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(StreamlineColors.Gold.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = StreamlineColors.Gold,
                modifier = Modifier.size(21.dp),
            )
        }
        Spacer(Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Auto",
                    color = StreamlineColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.width(7.dp))
                MetaPill(text = "Recommended", tone = StreamlineColors.Gold)
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = if (autoPick != null) {
                    (if (action == QualityAction.PLAY) "Plays " else "Downloads ") +
                        autoPick.displayLabel
                } else {
                    "No playable release"
                },
                color = StreamlineColors.Gold,
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (action == QualityAction.PLAY) {
                    "Screen ${screenQuality.label} · $transport" +
                        (networkQuality?.let { " · ${it.label} fit" } ?: "")
                } else {
                    "$transport" + (networkQuality?.let { " · ${it.label} fit" } ?: "")
                },
                color = StreamlineColors.TextTertiary,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = if (action == QualityAction.PLAY) {
                Icons.Rounded.PlayArrow
            } else {
                Icons.Rounded.Download
            },
            contentDescription = null,
            tint = StreamlineColors.Gold,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun QualityRow(
    release: MagnetRelease,
    bestFit: Boolean,
    action: QualityAction,
    onClick: () -> Unit,
    onFocused: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged {
                focused = it.isFocused
                onFocused(it.isFocused)
            },
        shape = shape,
        color = if (focused) {
            StreamlineColors.SurfaceHigh
        } else {
            StreamlineColors.SurfaceRaised.copy(alpha = 0.72f)
        },
        border = BorderStroke(
            width = if (focused) 2.dp else 1.dp,
            color = if (focused) {
                StreamlineColors.Gold
            } else if (bestFit) {
                StreamlineColors.Success.copy(alpha = 0.4f)
            } else {
                StreamlineColors.Hairline.copy(alpha = 0.7f)
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(37.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.05f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Speed,
                    contentDescription = null,
                    tint = if (bestFit) StreamlineColors.Success else StreamlineColors.TextSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = release.displayLabel,
                        color = StreamlineColors.TextPrimary,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    if (bestFit) {
                        Spacer(Modifier.width(7.dp))
                        MetaPill(text = "Best fit", tone = StreamlineColors.Success)
                    }
                    release.containerLabel?.let { container ->
                        Spacer(Modifier.width(7.dp))
                        MetaPill(
                            text = if (release.isMatroska) "MKV · slower seek" else container,
                            tone = StreamlineColors.TextTertiary,
                        )
                    }
                }
                val meta = listOfNotNull(
                    release.sizeLabel,
                    release.seeds?.let { it.toString() + " seeds" },
                ).joinToString(" · ")
                if (meta.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = meta,
                        color = StreamlineColors.TextTertiary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = if (action == QualityAction.PLAY) {
                    Icons.Rounded.PlayArrow
                } else {
                    Icons.Rounded.Download
                },
                contentDescription = (if (action == QualityAction.PLAY) "Play " else "Download ") +
                    release.displayLabel,
                tint = StreamlineColors.Gold,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

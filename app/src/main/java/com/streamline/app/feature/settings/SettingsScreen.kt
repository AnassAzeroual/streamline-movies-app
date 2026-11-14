package com.streamline.app.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Hardware
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Stream
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.streamline.app.BuildConfig
import com.streamline.app.core.designsystem.CinematicBackground
import com.streamline.app.core.designsystem.GlassIconButton
import com.streamline.app.core.designsystem.MetaPill
import com.streamline.app.core.designsystem.PrimaryActionButton
import com.streamline.app.core.designsystem.StreamlineColors
import com.streamline.app.core.designsystem.StreamlineWordmark
import com.streamline.app.core.designsystem.formatBytes
import com.streamline.app.core.designsystem.isTelevision
import com.streamline.app.core.designsystem.tvEdgePadding
import com.streamline.app.core.di.AppContainer
import kotlinx.coroutines.launch

@Composable
fun SettingsRoute(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle()
    SettingsScreen(
        container = container,
        hardwareDecoding = settings.hardwareDecoding,
        showStreamStats = settings.showStreamStats,
        hideAdult = settings.hideAdultContent,
        onBack = onBack,
        onHardwareDecodingChanged = container.settingsRepository::setHardwareDecodingEnabled,
        onStreamStatsChanged = container.settingsRepository::setStreamStatsEnabled,
        onHideAdultChanged = container.settingsRepository::setHideAdultContent,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    hardwareDecoding: Boolean,
    showStreamStats: Boolean,
    hideAdult: Boolean,
    onBack: () -> Unit,
    onHardwareDecodingChanged: (Boolean) -> Unit,
    onStreamStatsChanged: (Boolean) -> Unit,
    onHideAdultChanged: (Boolean) -> Unit,
) {
    var clearingCache by remember { mutableStateOf(false) }
    var cacheMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val isTv = isTelevision()
    val edgePadding = tvEdgePadding(isTv)

    CinematicBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = edgePadding)
                    .padding(top = topInset + if (isTv) 96.dp else 64.dp),
            ) {
                Text(
                    text = "THE CONTROL\nROOM",
                    color = StreamlineColors.TextPrimary,
                    style = MaterialTheme.typography.displayMedium.copy(lineHeight = 44.sp),
                )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Playback, storage, and the open link library.",
                color = StreamlineColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(26.dp))

            SettingsPanel(title = "LINK LIBRARY") {
                SettingRow(
                    icon = Icons.Rounded.Shield,
                    title = "No adult",
                    subtitle = "Hide adult categories and adult tags from every source in the archive.",
                    trailing = {
                        Switch(
                            checked = hideAdult,
                            onCheckedChange = onHideAdultChanged,
                            colors = streamlineSwitchColors(),
                        )
                    },
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Streamline opens directly into the magnet archive. Posters, quality, " +
                        "seed counts, language tags, and torrent metadata come from the public indexes; " +
                        "there is no account and no external movie API.",
                    color = StreamlineColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetaPill(text = "8 sources", tone = StreamlineColors.Gold)
                    MetaPill(text = "No API key", tone = StreamlineColors.Success)
                }
            }

            Spacer(Modifier.height(14.dp))
            SettingsPanel(title = "PLAYBACK") {
                SettingRow(
                    icon = Icons.Rounded.Hardware,
                    title = "Hardware decoding",
                    subtitle = "Use the device decoder when available for smoother 4K playback.",
                    trailing = {
                        Switch(
                            checked = hardwareDecoding,
                            onCheckedChange = onHardwareDecodingChanged,
                            colors = streamlineSwitchColors(),
                        )
                    },
                )
                Spacer(Modifier.height(12.dp))
                SettingRow(
                    icon = Icons.Rounded.Insights,
                    title = "Stream statistics overlay",
                    subtitle = "Show live seeds, peers, transfer rates and buffer depth over the player. " +
                        "The insights button in the player toggles it for one session.",
                    trailing = {
                        Switch(
                            checked = showStreamStats,
                            onCheckedChange = onStreamStatsChanged,
                            colors = streamlineSwitchColors(),
                        )
                    },
                )
                Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.height(14.dp))
            SettingsPanel(title = "TORRENT ENGINE") {
                SettingRow(
                    icon = Icons.Rounded.Stream,
                    title = "Sequential streaming",
                    subtitle = "Magnet and .torrent links — the largest video file is prioritised from first " +
                        "to last piece. A custom Media3 DataSource blocks for bytes as the player needs them. " +
                        "Pieces stay cached per torrent so replays open instantly; the oldest caches are " +
                        "evicted when storage runs low.",
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PrimaryActionButton(
                        text = if (clearingCache) "Clearing…" else "Clear stream cache",
                        onClick = {
                            if (clearingCache) return@PrimaryActionButton
                            clearingCache = true
                            scope.launch {
                                try {
                                    val cleared = container.torrentEngine.clearDownloadedData()
                                    cacheMessage = if (cleared > 0) {
                                        "Cleared ${formatBytes(cleared)} of cached stream data."
                                    } else {
                                        "There are no downloaded streams to clear."
                                    }
                                } finally {
                                    clearingCache = false
                                }
                            }
                        },
                        icon = Icons.Rounded.DeleteSweep,
                        enabled = !clearingCache,
                    )
                    if (clearingCache) {
                        Spacer(Modifier.width(12.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = StreamlineColors.Gold,
                            strokeWidth = 1.6.dp,
                        )
                    }
                }
                if (cacheMessage != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = cacheMessage.orEmpty(),
                        color = StreamlineColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Partial torrent data lives in app-private storage. It never appears in your gallery " +
                        "and is removed with this action or when the app is uninstalled.",
                    color = StreamlineColors.TextTertiary,
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 17.sp),
                )
            }

            Spacer(Modifier.height(14.dp))
            SettingsPanel(title = "ABOUT") {
                Text(
                    text = "Streamline ${BuildConfig.VERSION_NAME.removeSuffix("-debug")}",
                    color = StreamlineColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "A native Compose magnet library built around Media3 and libtorrent4j.",
                    color = StreamlineColors.TextTertiary,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = StreamlineColors.Success,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Local-first · No account · No analytics",
                        color = StreamlineColors.TextTertiary,
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.5.sp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Artwork and magnet metadata belong to their respective rights holders. " +
                        "Streamline does not host or distribute media files.",
                    color = StreamlineColors.TextTertiary.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.labelSmall.copy(lineHeight = 16.sp, letterSpacing = 0.2.sp),
                )
            }

                Spacer(Modifier.height(36.dp))
            }
            SettingsTopBar(
                onBack = onBack,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

@Composable
private fun SettingsTopBar(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    0f to StreamlineColors.Ink,
                    0.62f to StreamlineColors.Ink.copy(alpha = 0.97f),
                    1f to Color.Transparent,
                ),
            )
            .pointerInput(Unit) {
                detectTapGestures { }
            }
            .statusBarsPadding()
            .padding(start = 20.dp, end = 18.dp, top = 9.dp, bottom = 18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassIconButton(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                onClick = onBack,
            )
            Spacer(Modifier.width(14.dp))
            StreamlineWordmark(compact = true)
        }
    }
}

@Composable
private fun SettingsPanel(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(StreamlineColors.Surface.copy(alpha = 0.86f))
            .border(1.dp, StreamlineColors.Hairline.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
            .padding(16.dp),
    ) {
        Text(
            text = title,
            color = StreamlineColors.TextTertiary,
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.8.sp),
        )
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun SettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.05f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = StreamlineColors.Gold,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = StreamlineColors.TextPrimary,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = subtitle,
                color = StreamlineColors.TextTertiary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            trailing()
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = StreamlineColors.TextPrimary,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = subtitle,
                color = StreamlineColors.TextTertiary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = streamlineSwitchColors(),
        )
    }
}

@Composable
private fun streamlineSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = StreamlineColors.Ink,
    checkedTrackColor = StreamlineColors.Gold,
    uncheckedThumbColor = StreamlineColors.TextTertiary,
    uncheckedTrackColor = StreamlineColors.SurfaceHigh,
    uncheckedBorderColor = StreamlineColors.Hairline,
)



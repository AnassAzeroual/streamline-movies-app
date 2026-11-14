package com.streamline.app.feature.downloads

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.streamline.app.core.designsystem.Artwork
import java.io.File
import com.streamline.app.core.designsystem.CinematicBackground
import com.streamline.app.core.designsystem.EmptyOrErrorState
import com.streamline.app.core.designsystem.GlassIconButton
import com.streamline.app.core.designsystem.MetaPill
import com.streamline.app.core.designsystem.StreamlineColors
import com.streamline.app.core.designsystem.StreamlineWordmark
import com.streamline.app.core.designsystem.isTelevision
import com.streamline.app.core.designsystem.tvBarPadding
import com.streamline.app.core.designsystem.tvEdgePadding
import com.streamline.app.core.designsystem.tvTopPadding
import com.streamline.app.core.di.AppContainer
import com.streamline.app.core.download.DownloadItem
import com.streamline.app.core.download.DownloadStatus
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import android.widget.Toast
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun DownloadsRoute(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenMagnets: () -> Unit,
) {
    val downloads by container.downloadManager.downloads.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    var directStorage by remember {
        mutableStateOf(container.downloadManager.isDirectStorageAvailable())
    }
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { directStorage = container.downloadManager.isDirectStorageAvailable() }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(Unit) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                directStorage = container.downloadManager.isDirectStorageAvailable()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DownloadsScreen(
        downloads = downloads,
        onBack = onBack,
        onOpenMagnets = onOpenMagnets,
        onPause = container.downloadManager::pause,
        onResume = container.downloadManager::resume,
        onRemove = container.downloadManager::remove,
        directStorageAvailable = directStorage,
        onEnableDirectStorage = {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> runCatching {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:" + context.packageName),
                        ),
                    )
                }
                Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> {
                    storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
        },
        onSaveToPhone = { id ->
            scope.launch {
                val result = container.downloadManager.exportToPublic(id)
                Toast.makeText(
                    context,
                    result.fold(
                        onSuccess = { "Saved to Downloads/Streamline" },
                        onFailure = { it.message ?: "Could not save this download." },
                    ),
                    Toast.LENGTH_LONG,
                ).show()
            }
        },
        onOpenFile = { item ->
            val publicRoot = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "Streamline",
            ).absolutePath
            val relative = item.savedPath
                ?.takeIf { it.startsWith(publicRoot + File.separator) }
                ?.removePrefix(Environment.getExternalStorageDirectory().absolutePath + File.separator)
                ?.substringBeforeLast(File.separator)
            runCatching {
                context.startActivity(context.streamlineFolderIntent(relative))
            }.onFailure {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW).setData(
                            DocumentsContract.buildRootUri(
                                "com.android.externalstorage.documents",
                                "primary",
                            ),
                        ),
                    )
                }
            }
        },
        onDemoDownload = {
            container.downloadManager.enqueue(
                com.streamline.app.domain.model.PlaybackRequest(
                    id = "open-sintel-demo",
                    title = "Sintel · legal demo",
                    magnetUrl = com.streamline.app.core.util.OpenMedia.DEMO_MAGNET,
                    sourceLabel = "Open movie",
                    quality = "1080p",
                ),
            )
        },
    )
}

private fun Context.streamlineFolderIntent(relativePath: String?): Intent {
    val folderUri = DocumentsContract.buildDocumentUri(
        "com.android.externalstorage.documents",
        "primary:" + (relativePath ?: "Download/Streamline"),
    )
    return Intent(Intent.ACTION_VIEW)
        .setDataAndType(folderUri, DocumentsContract.Document.MIME_TYPE_DIR)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}

@Composable
fun DownloadsScreen(
    downloads: List<DownloadItem>,
    onBack: () -> Unit,
    onOpenMagnets: () -> Unit,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onRemove: (String) -> Unit,
    directStorageAvailable: Boolean,
    onEnableDirectStorage: () -> Unit,
    onSaveToPhone: (String) -> Unit,
    onOpenFile: (DownloadItem) -> Unit,
    onDemoDownload: (() -> Unit)? = null,
) {
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val isTv = isTelevision()
    val edgePadding = tvEdgePadding(isTv)
    val headerOffset = if (isTv) 96.dp else 66.dp
    var removeTarget by remember { mutableStateOf<DownloadItem?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        CinematicBackground {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = edgePadding,
                    end = edgePadding,
                    top = topInset + headerOffset,
                    bottom = bottomInset + if (isTv) 40.dp else 30.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "downloads-header") {
                    DownloadsHeader(downloads = downloads)
                }

                if (!directStorageAvailable && Build.VERSION.SDK_INT != Build.VERSION_CODES.Q) {
                    item(key = "direct-storage-banner") {
                        DirectStorageBanner(onEnableDirectStorage)
                    }
                }

                if (downloads.isEmpty()) {
                    item(key = "downloads-empty") {
                        Column {
                            EmptyOrErrorState(
                                title = "Nothing in the queue",
                                message = "Tap the download button on a magnet. Playback stays separate " +
                                    "and streams without adding a file here.",
                                icon = Icons.Rounded.Download,
                                actionLabel = "Open archive",
                                onAction = onOpenMagnets,
                                tone = StreamlineColors.Gold,
                            )
                            if (onDemoDownload != null) {
                                TextButton(
                                    onClick = onDemoDownload,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = "Try a legal Sintel demo download",
                                        color = StreamlineColors.TextTertiary,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }
                    }
                } else {
                    items(items = downloads, key = { it.id }) { item ->
                        DownloadCard(
                            item = item,
                            onPause = { onPause(item.id) },
                            onResume = { onResume(item.id) },
                            onRemove = { removeTarget = item },
                            onSaveToPhone = { onSaveToPhone(item.id) },
                            onOpenFile = { onOpenFile(item) },
                        )
                    }
                }
            }
        }

        DownloadsTopBar(
            onBack = onBack,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }

    removeTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = {
                Text(
                    text = "Remove download?",
                    color = StreamlineColors.TextPrimary,
                )
            },
            text = {
                Text(
                    text = if (target.isComplete) {
                        "This removes the downloaded file from this device."
                    } else {
                        "This stops the download and removes its partial file."
                    },
                    color = StreamlineColors.TextSecondary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemove(target.id)
                        removeTarget = null
                    },
                ) {
                    Text("Remove", color = StreamlineColors.Gold)
                }
            },
            dismissButton = {
                TextButton(onClick = { removeTarget = null }) {
                    Text("Keep", color = StreamlineColors.TextSecondary)
                }
            },
            containerColor = StreamlineColors.SurfaceHigh,
        )
    }
}

@Composable
private fun DownloadsTopBar(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isTv = isTelevision()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to StreamlineColors.Ink,
                    0.62f to StreamlineColors.Ink.copy(alpha = 0.97f),
                    1f to Color.Transparent,
                ),
            )
            .pointerInput(Unit) { detectTapGestures { } }
            .statusBarsPadding()
            .padding(
                start = tvBarPadding(isTv),
                end = tvBarPadding(isTv),
                top = tvTopPadding(isTv),
                bottom = 18.dp,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassIconButton(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                onClick = onBack,
                size = if (isTv) 48.dp else 42.dp,
            )
            Spacer(Modifier.width(14.dp))
            StreamlineWordmark(compact = true)
            Spacer(Modifier.weight(1f))
            MetaPill(
                text = "queue",
                tone = StreamlineColors.Gold,
                icon = Icons.Rounded.Download,
            )
        }
    }
}

@Composable
private fun DownloadsHeader(downloads: List<DownloadItem>) {
    val active = downloads.count { it.status.isActive }
    val totalSpeed = downloads.filter { it.status.isActive }.sumOf { it.downloadRate }
    val stored = downloads.sumOf { item ->
        if (item.isComplete) item.totalBytes else item.downloadedBytes
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "DOWNLOAD QUEUE",
            color = StreamlineColors.Gold,
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "DOWNLOADS",
            color = StreamlineColors.TextPrimary,
            style = MaterialTheme.typography.displayMedium.copy(lineHeight = 42.sp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Completed files are saved to Downloads/Streamline on your phone. Play still " +
                "streams separately and never adds a file to this queue.",
            color = StreamlineColors.TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(15.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MetaPill(
                text = active.toString() + " active",
                tone = if (active > 0) StreamlineColors.Gold else StreamlineColors.TextTertiary,
            )
            if (totalSpeed > 0L) {
                MetaPill(
                    text = formatBytes(totalSpeed) + "/s",
                    tone = StreamlineColors.Success,
                )
            }
            if (stored > 0L) {
                Text(
                    text = formatBytes(stored) + " on disk",
                    color = StreamlineColors.TextTertiary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun DirectStorageBanner(onEnable: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = StreamlineColors.Gold.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, StreamlineColors.Gold.copy(alpha = 0.3f)),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "Download straight to Downloads/Streamline",
                color = StreamlineColors.TextPrimary,
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Without All-files access Android makes Streamline download privately and copy " +
                    "the file afterward. Grant access once to remove that copy step.",
                color = StreamlineColors.TextTertiary,
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 17.sp),
            )
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = onEnable) {
                Text("Enable direct downloads", color = StreamlineColors.Gold)
            }
        }
    }
}

@Composable
private fun DownloadCard(
    item: DownloadItem,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRemove: () -> Unit,
    onSaveToPhone: () -> Unit,
    onOpenFile: () -> Unit,
) {
    val accent = statusTone(item.status)
    val shape = RoundedCornerShape(18.dp)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        color = StreamlineColors.Surface.copy(alpha = 0.9f),
        border = BorderStroke(1.dp, StreamlineColors.Hairline.copy(alpha = 0.65f)),
    ) {
        Column(modifier = Modifier.padding(11.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Artwork(
                    url = item.posterUrl,
                    contentDescription = "Poster for " + item.title,
                    fallbackSeed = item.title,
                    modifier = Modifier
                        .width(62.dp)
                        .height(93.dp),
                    shape = RoundedCornerShape(11.dp),
                )
                Spacer(Modifier.width(11.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            text = item.title,
                            color = StreamlineColors.TextPrimary,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(7.dp))
                        MetaPill(text = item.status.label, tone = accent)
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = listOfNotNull(
                            item.sourceLabel,
                            item.quality,
                            item.totalBytes.takeIf { it > 0L }?.let(::formatBytes),
                        ).joinToString(" · ").ifBlank { "Magnet download" },
                        color = StreamlineColors.TextTertiary,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(9.dp))
                    DownloadProgress(
                        progress = item.progress,
                        indeterminate = item.status == DownloadStatus.QUEUED ||
                            item.status == DownloadStatus.FETCHING,
                        accent = accent,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = downloadInfo(item),
                        color = if (item.status == DownloadStatus.FAILED) {
                            StreamlineColors.Danger
                        } else {
                            StreamlineColors.TextSecondary
                        },
                        style = MaterialTheme.typography.labelSmall.copy(lineHeight = 16.sp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (item.canPause) {
                    DownloadAction(
                        label = "Pause",
                        icon = Icons.Rounded.Pause,
                        tint = StreamlineColors.TextSecondary,
                        onClick = onPause,
                    )
                }
                if (item.canResume) {
                    DownloadAction(
                        label = if (item.status == DownloadStatus.FAILED) "Retry" else "Resume",
                        icon = Icons.Rounded.PlayArrow,
                        tint = StreamlineColors.Gold,
                        onClick = onResume,
                    )
                }
                if (item.isComplete) {
                    if (item.publicUri != null) {
                        DownloadAction(
                            label = "Open folder",
                            icon = Icons.AutoMirrored.Rounded.OpenInNew,
                            tint = StreamlineColors.Gold,
                            onClick = onOpenFile,
                        )
                    } else {
                        DownloadAction(
                            label = "Save to phone",
                            icon = Icons.Rounded.SaveAlt,
                            tint = StreamlineColors.Success,
                            onClick = onSaveToPhone,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                DownloadAction(
                    label = "Remove",
                    icon = Icons.Rounded.Delete,
                    tint = StreamlineColors.TextTertiary,
                    onClick = onRemove,
                )
            }
        }
    }
}

@Composable
private fun DownloadProgress(
    progress: Float,
    indeterminate: Boolean,
    accent: Color,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(5.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.08f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(if (indeterminate) 0.28f else progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(accent),
        )
    }
}

@Composable
private fun DownloadAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(9.dp))
            .background(
                if (focused) StreamlineColors.Gold.copy(alpha = 0.14f) else Color.Transparent,
            )
            .border(
                if (focused) 2.dp else 0.dp,
                if (focused) StreamlineColors.Gold else Color.Transparent,
                RoundedCornerShape(9.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            color = tint,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

private fun downloadInfo(item: DownloadItem): String = when (item.status) {
    DownloadStatus.QUEUED -> "Waiting for the download session…"
    DownloadStatus.FETCHING -> "Finding peers and torrent metadata…"
    DownloadStatus.FAILED -> item.error ?: "The download failed."
    DownloadStatus.PAUSED -> {
        val progress = if (item.totalBytes > 0L) item.percent.toString() + "% · " else ""
        progress + "Paused · " + formatBytes(item.downloadedBytes) + " saved"
    }
    DownloadStatus.COMPLETED -> {
        val name = item.savedPath?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        listOfNotNull(
            "Complete",
            name,
        ).joinToString(" · ")
    }
    DownloadStatus.DOWNLOADING -> {
        val parts = mutableListOf<String>()
        parts += if (item.totalBytes > 0L) {
            formatBytes(item.downloadedBytes) + " / " + formatBytes(item.totalBytes)
        } else {
            formatBytes(item.downloadedBytes)
        }
        if (item.downloadRate > 0L) parts += formatBytes(item.downloadRate) + "/s"
        if (item.peers > 0) parts += item.peers.toString() + " peers"
        etaSeconds(item)?.let { seconds ->
            parts += when {
                seconds < 60 -> seconds.toString() + "s left"
                seconds < 3_600 -> (seconds / 60).toString() + "m left"
                else -> (seconds / 3_600).toString() + "h left"
            }
        }
        parts.joinToString(" · ")
    }
}

private fun etaSeconds(item: DownloadItem): Long? {
    if (!item.status.isActive || item.downloadRate <= 0L || item.totalBytes <= 0L) return null
    val remaining = item.totalBytes - item.downloadedBytes
    if (remaining <= 0L) return null
    return (remaining / item.downloadRate).coerceAtLeast(1L)
}

private fun statusTone(status: DownloadStatus): Color = when (status) {
    DownloadStatus.COMPLETED -> StreamlineColors.Success
    DownloadStatus.FAILED -> StreamlineColors.Danger
    DownloadStatus.PAUSED -> StreamlineColors.TextSecondary
    DownloadStatus.QUEUED,
    DownloadStatus.FETCHING,
    DownloadStatus.DOWNLOADING,
    -> StreamlineColors.Gold
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024.0 && index < units.lastIndex) {
        value /= 1024.0
        index++
    }
    return if (value >= 100.0 || index == 0) {
        String.format(Locale.US, "%.0f %s", value, units[index])
    } else {
        String.format(Locale.US, "%.1f %s", value, units[index])
    }
}

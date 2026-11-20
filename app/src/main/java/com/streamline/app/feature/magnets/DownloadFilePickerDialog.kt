package com.streamline.app.feature.magnets

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import com.streamline.app.core.designsystem.formatBytes
import com.streamline.app.core.download.DownloadFileOption
import com.streamline.app.core.download.DownloadItem

@Composable
fun DownloadFilePickerDialog(
    files: List<DownloadFileOption>,
    selected: Set<Int>,
    loading: Boolean,
    error: String?,
    onToggle: (Int) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val firstFileFocus = remember { FocusRequester() }
    LaunchedEffect(files.size, loading, error) {
        if (files.isNotEmpty() && !loading && error == null) {
            runCatching { firstFileFocus.requestFocus() }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose download files") },
        text = {
            when {
                loading -> CircularProgressIndicator()
                error != null -> Text(error)
                else -> LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    itemsIndexed(files, key = { _, file -> file.index }) { index, file ->
                        val checked = file.index in selected
                        var focused by remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (index == 0) Modifier.focusRequester(firstFileFocus) else Modifier,
                                )
                                .onFocusChanged { focused = it.isFocused }
                                .border(
                                    width = if (focused) 2.dp else 0.dp,
                                    color = if (focused) MaterialTheme.colorScheme.primary
                                    else androidx.compose.ui.graphics.Color.Transparent,
                                    shape = RoundedCornerShape(8.dp),
                                )
                                .clickable { onToggle(file.index) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, onCheckedChange = { onToggle(file.index) })
                            Column(modifier = Modifier.padding(start = 8.dp)) {
                                Text(
                                    text = file.path.substringAfterLast('/'),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                )
                                Text(
                                    text = fileType(file) + " · " + formatBytes(file.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !loading && error == null && selected.isNotEmpty(),
            ) {
                Text("Download selected")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

fun defaultDownloadSelection(files: List<DownloadFileOption>): Set<Int> {
    val videos = files.filter {
        it.isVideo && !isExtraVideo(it.path)
    }.map { it.index }
    val subtitles = files.filter { it.isSubtitle }.map { it.index }
    return (videos + subtitles).toSet()
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

private fun fileType(file: DownloadFileOption): String = when {
    file.isVideo -> "Video"
    file.isSubtitle -> "Subtitle"
    else -> "Other"
}

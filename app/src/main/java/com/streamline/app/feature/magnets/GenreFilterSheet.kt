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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MovieFilter
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.streamline.app.core.designsystem.StreamlineColors
import com.streamline.app.core.designsystem.isTelevision
import com.streamline.app.domain.model.MovieGenre
import kotlinx.coroutines.delay

@Composable
fun GenreFilterSheet(
    current: MovieGenre?,
    onApply: (MovieGenre?) -> Unit,
    onDismiss: () -> Unit,
) {
    val options: List<MovieGenre?> = listOf(null) + MovieGenre.entries
    val focus = remember(options.size) { List(options.size) { FocusRequester() } }
    var focusedIndex by remember { mutableStateOf<Int?>(null) }
    val startIndex = options.indexOf(current).takeIf { it >= 0 } ?: 0

    LaunchedEffect(Unit) {
        delay(350)
        runCatching { focus[startIndex].requestFocus() }
        focusedIndex = startIndex
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
                    .widthIn(max = 620.dp)
                    .fillMaxWidth()
                    .heightIn(max = 640.dp),
                shape = RoundedCornerShape(24.dp),
                color = StreamlineColors.Surface,
                border = BorderStroke(1.dp, StreamlineColors.Hairline),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onPreviewKeyEvent { event ->
                            val index = focusedIndex
                                ?: if (event.key == Key.DirectionDown) 0 else null
                            if (
                                event.type == KeyEventType.KeyDown &&
                                index != null &&
                                (event.key == Key.DirectionDown || event.key == Key.DirectionUp)
                            ) {
                                val next = if (event.key == Key.DirectionDown) index + 2 else index - 2
                                if (next in focus.indices) {
                                    focus[next].requestFocus()
                                    true
                                } else {
                                    false
                                }
                            } else {
                                false
                            }
                        }
                        .padding(horizontal = 20.dp)
                        .padding(top = 18.dp, bottom = 20.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(StreamlineColors.Gold.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MovieFilter,
                                contentDescription = null,
                                tint = StreamlineColors.Gold,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(Modifier.width(11.dp))
                        Column {
                            Text(
                                text = "MOVIE TYPE",
                                color = StreamlineColors.Gold,
                                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.8.sp),
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = "Genre filter",
                                color = StreamlineColors.TextPrimary,
                                style = MaterialTheme.typography.headlineSmall,
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Choose a type, or All genres. Tags come from source categories and " +
                            "release names; untagged releases may not match a specific genre.",
                        color = StreamlineColors.TextTertiary,
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 17.sp),
                    )
                    Spacer(Modifier.height(14.dp))

                    options.chunked(2).forEachIndexed { rowIndex, row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            row.forEachIndexed { column, genre ->
                                val index = rowIndex * 2 + column
                                GenreChip(
                                    label = genre?.label ?: "All genres",
                                    selected = if (genre == null) current == null else current == genre,
                                    onClick = { onApply(genre) },
                                    onFocused = { if (it) focusedIndex = index },
                                    onDirectionKey = { key ->
                                        val next = when (key) {
                                            Key.DirectionDown -> index + 2
                                            Key.DirectionUp -> index - 2
                                            else -> null
                                        }
                                        if (next != null && next in focus.indices) {
                                            focus[next].requestFocus()
                                            true
                                        } else {
                                            false
                                        }
                                    },
                                    modifier = Modifier
                                        .onFocusChanged { if (it.isFocused) focusedIndex = index }
                                        .focusRequester(focus[index]),
                                )
                            }
                        }
                        if (rowIndex < (options.size + 1) / 2 - 1) {
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GenreChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    onFocused: (Boolean) -> Unit,
    onDirectionKey: (Key) -> Boolean,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .onKeyEvent { event ->
                event.type == KeyEventType.KeyDown && onDirectionKey(event.key)
            }
            .onFocusChanged {
                focused = it.isFocused
                onFocused(it.isFocused)
            }
            .clip(shape)
            .background(
                if (selected || focused) StreamlineColors.Gold.copy(alpha = 0.13f)
                else Color.White.copy(alpha = 0.04f),
            )
            .border(
                if (focused) 2.dp else 1.dp,
                when {
                    focused -> StreamlineColors.Gold
                    selected -> StreamlineColors.Gold.copy(alpha = 0.42f)
                    else -> Color.White.copy(alpha = 0.08f)
                },
                shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = if (selected || focused) StreamlineColors.TextPrimary else StreamlineColors.TextSecondary,
            style = MaterialTheme.typography.labelMedium,
        )
        if (selected) {
            Spacer(Modifier.width(5.dp))
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = StreamlineColors.Gold,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

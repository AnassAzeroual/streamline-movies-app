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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Language
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
import androidx.compose.ui.focus.focusProperties
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
import com.streamline.app.core.designsystem.PrimaryActionButton
import com.streamline.app.core.designsystem.StreamlineColors
import com.streamline.app.core.designsystem.isTelevision
import com.streamline.app.domain.model.Language
import com.streamline.app.domain.model.LanguageFilter
import com.streamline.app.domain.model.LanguageRole
import kotlinx.coroutines.delay

@Composable
fun LanguageFilterSheet(
    current: LanguageFilter,
    onApply: (LanguageFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedLanguage by remember { mutableStateOf(current.language) }
    var selectedRole by remember { mutableStateOf(current.role) }
    val languageOptions: List<Language?> = listOf(null) + Language.entries
    val languageFocus = remember(languageOptions.size) {
        List(languageOptions.size) { FocusRequester() }
    }
    var focusedLanguageIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) {
        delay(450)
        runCatching { languageFocus.firstOrNull()?.requestFocus() }
        focusedLanguageIndex = 0
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
                    val current = focusedLanguageIndex ?: if (event.key == Key.DirectionDown) 0 else null
                    if (
                        event.type == KeyEventType.KeyDown &&
                        current != null &&
                        (event.key == Key.DirectionDown || event.key == Key.DirectionUp)
                    ) {
                        val next = if (event.key == Key.DirectionDown) current + 2 else current - 2
                        if (next in languageFocus.indices) {
                            languageFocus[next].requestFocus()
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
                .padding(top = 6.dp, bottom = 18.dp),
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
                        imageVector = Icons.Rounded.Language,
                        contentDescription = null,
                        tint = StreamlineColors.Gold,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(11.dp))
                Column {
                    Text(
                        text = "AUDIO & SUBTITLES",
                        color = StreamlineColors.Gold,
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.8.sp),
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "Language filter",
                        color = StreamlineColors.TextPrimary,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = "Choose a language, then whether you want it as the audio track or " +
                    "as subtitles. Tags are read from release names; untagged magnets cannot " +
                    "match a specific language.",
                color = StreamlineColors.TextTertiary,
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 17.sp),
            )

            Spacer(Modifier.height(18.dp))
            Text(
                text = "LANGUAGE",
                color = StreamlineColors.TextTertiary,
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.6.sp),
            )
            Spacer(Modifier.height(7.dp))
            languageOptions.chunked(2).forEachIndexed { rowIndex, row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    row.forEachIndexed { column, language ->
                        val index = rowIndex * 2 + column
                        LanguageChip(
                            label = language?.label ?: "All languages",
                            selected = if (language == null) {
                                selectedLanguage == null
                            } else {
                                selectedLanguage == language
                            },
                            onClick = {
                                if (language == null) {
                                    selectedLanguage = null
                                    selectedRole = LanguageRole.ANY
                                } else {
                                    selectedLanguage = language
                                }
                            },
                            onFocused = { focused ->
                                if (focused) focusedLanguageIndex = index
                            },
                            onDirectionKey = { key ->
                                val next = when (key) {
                                    Key.DirectionDown -> index + 2
                                    Key.DirectionUp -> index - 2
                                    else -> null
                                }
                                if (next != null && next in languageFocus.indices) {
                                    languageFocus[next].requestFocus()
                                    true
                                } else {
                                    false
                                }
                            },
                            modifier = Modifier
                                .onFocusChanged { if (it.isFocused) focusedLanguageIndex = index }
                                .focusRequester(languageFocus[index])
                                .focusProperties {
                                    if (index >= 2) up = languageFocus[index - 2]
                                    if (index + 2 < languageFocus.size) down = languageFocus[index + 2]
                                    if (column > 0) left = languageFocus[index - 1]
                                    if (column == 0 && index + 1 < languageFocus.size) {
                                        right = languageFocus[index + 1]
                                    }
                                },
                        )
                    }
                }
                if (rowIndex < (languageOptions.size + 1) / 2 - 1) {
                    Spacer(Modifier.height(5.dp))
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = "MATCH THIS LANGUAGE AS",
                color = StreamlineColors.TextTertiary,
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.6.sp),
            )
            Spacer(Modifier.height(7.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                LanguageRole.entries.forEach { role ->
                    LanguageRoleChip(
                        label = role.label,
                        selected = selectedRole == role,
                        enabled = selectedLanguage != null,
                        onClick = { selectedRole = role },
                        onFocused = { focused ->
                            if (focused) focusedLanguageIndex = null
                        },
                    )
                }
            }
            if (selectedLanguage == null) {
                Spacer(Modifier.height(5.dp))
                Text(
                    text = "Pick a language to enable audio / subtitle matching.",
                    color = StreamlineColors.TextTertiary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }

            Spacer(Modifier.height(12.dp))
            PrimaryActionButton(
                text = if (selectedLanguage == null) "Show all languages" else "Apply language",
                onClick = {
                    onApply(
                        LanguageFilter(
                            language = selectedLanguage,
                            role = if (selectedLanguage == null) {
                                LanguageRole.ANY
                            } else {
                                selectedRole
                            },
                        ),
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { if (it.isFocused) focusedLanguageIndex = null },
            )
                }
            }
        }
    }
}

@Composable
private fun LanguageChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    onFocused: (Boolean) -> Unit = {},
    onDirectionKey: (Key) -> Boolean = { false },
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
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = if (selected) StreamlineColors.TextPrimary else StreamlineColors.TextSecondary,
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

@Composable
private fun LanguageRoleChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onFocused: (Boolean) -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    val accent = if (selected || focused) StreamlineColors.Gold else StreamlineColors.TextSecondary
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .onFocusChanged {
                focused = it.isFocused
                onFocused(it.isFocused)
            }
            .clip(shape)
            .background(
                if (selected || focused) StreamlineColors.Gold.copy(alpha = 0.14f)
                else Color.White.copy(alpha = 0.045f),
            )
            .border(
                if (focused) 2.dp else 1.dp,
                if (selected || focused) StreamlineColors.Gold.copy(alpha = 0.5f)
                else Color.White.copy(alpha = 0.08f),
                shape,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = if (enabled) accent else StreamlineColors.TextTertiary.copy(alpha = 0.55f),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

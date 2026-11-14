package com.streamline.app.feature.magnets

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MovieFilter
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streamline.app.core.designsystem.Artwork
import com.streamline.app.core.designsystem.GlassIconButton
import com.streamline.app.core.designsystem.MetaPill
import com.streamline.app.core.designsystem.RatingBadge
import com.streamline.app.core.designsystem.SectionHeading
import com.streamline.app.core.designsystem.ShimmerBlock
import com.streamline.app.core.designsystem.StreamlineColors
import com.streamline.app.core.designsystem.StreamlineWordmark
import com.streamline.app.core.designsystem.isTelevision
import com.streamline.app.core.designsystem.tvBarPadding
import com.streamline.app.core.designsystem.tvTopPadding
import com.streamline.app.domain.model.LanguageFilter
import com.streamline.app.domain.model.LanguageMatch
import com.streamline.app.domain.model.MagnetSource
import com.streamline.app.domain.model.MagnetTitle
import com.streamline.app.domain.model.MovieGenre
import java.util.Locale

@Composable
fun MagnetTopBar(
    onOpenDownloads: () -> Unit,
    onOpenSettings: () -> Unit,
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
            .pointerInput(Unit) {
                detectTapGestures { }
            }
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
            StreamlineWordmark(compact = true)
            Spacer(Modifier.weight(1f))
            GlassIconButton(
                icon = Icons.Rounded.Download,
                contentDescription = "Downloads",
                onClick = onOpenDownloads,
                tint = StreamlineColors.Gold,
                size = if (isTv) 48.dp else 38.dp,
            )
            Spacer(Modifier.width(8.dp))
            GlassIconButton(
                icon = Icons.Rounded.Settings,
                contentDescription = "Settings",
                onClick = onOpenSettings,
                tint = StreamlineColors.TextSecondary,
                size = if (isTv) 48.dp else 38.dp,
            )
        }
    }
}

@Composable
fun MagnetArchiveHeader(
    state: MagnetCatalogUiState,
    onSourceSelected: (MagnetSource) -> Unit,
    onLanguageFilterClick: () -> Unit,
    onGenreFilterClick: () -> Unit,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onRefresh: () -> Unit,
) {
    val isAllYears = state.source == MagnetSource.ALL &&
        !state.isSearching &&
        state.query.isBlank()
    val count = state.totalResults ?: state.items.size.takeIf { it > 0 }
    val countLabel = count?.let { String.format(Locale.US, "%,d", it) }
    val eyebrow = buildString {
        append(
            when {
                isAllYears -> "ALL SOURCES · ALL YEARS"
                state.source == MagnetSource.ALL -> "ALL SOURCES"
                state.isSearching -> "SEARCH"
                state.source.requiresQuery && state.query.isBlank() ->
                    state.source.label.uppercase(Locale.US) + " · SEARCH"
                else -> state.source.label.uppercase(Locale.US) + " · LATEST"
            },
        )
        if (countLabel != null) {
            append("  ·  ")
            append(countLabel)
            append(
                if (state.source == MagnetSource.YTS || isAllYears) " TITLES" else " LINKS",
            )
        }
    }
    val title = when {
        state.isSearching -> "Links for “" + state.activeQuery + "”"
        isAllYears -> state.genreFilter?.let { it.label + " · All years" } ?: "All releases"
        state.source == MagnetSource.ALL -> "Across the indexes"
        state.source.requiresQuery && state.query.isBlank() -> "Search " + state.source.label
        state.source == MagnetSource.YTS -> "Fresh magnets"
        else -> "Latest from " + state.source.label
    }
    val searchPlaceholder = if (state.source == MagnetSource.ALL) {
        "Search all eight sources…"
    } else {
        "Search " + state.source.label + " releases…"
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        MagnetSourceSelector(
            selected = state.source,
            onSelected = onSourceSelected,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LanguageFilterPill(
                filter = state.languageFilter,
                onClick = onLanguageFilterClick,
            )
            GenreFilterPill(
                genre = state.genreFilter,
                onClick = onGenreFilterClick,
            )
        }
        Spacer(Modifier.height(12.dp))
        MagnetSearchField(
            query = state.query,
            isPending = state.isSearchPending,
            placeholder = searchPlaceholder,
            onQueryChange = onQueryChange,
            onClear = onClearQuery,
        )
        Spacer(Modifier.height(18.dp))
        SectionHeading(
            eyebrow = eyebrow,
            title = title,
            actionLabel = "Refresh",
            onAction = onRefresh,
        )
    }
}

@Composable
private fun LanguageFilterPill(
    filter: LanguageFilter,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val active = !filter.isAll
    val shape = RoundedCornerShape(50)
    Surface(
        onClick = onClick,
        modifier = Modifier.onFocusChanged { focused = it.isFocused },
        shape = shape,
        color = if (active || focused) {
            StreamlineColors.Gold.copy(alpha = 0.13f)
        } else {
            Color.White.copy(alpha = 0.04f)
        },
        border = BorderStroke(
            if (focused) 2.dp else 1.dp,
            if (active || focused) StreamlineColors.Gold.copy(alpha = 0.55f)
            else Color.White.copy(alpha = 0.08f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Language,
                contentDescription = null,
                tint = if (active) StreamlineColors.Gold else StreamlineColors.TextTertiary,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = filter.shortLabel,
                color = if (active) StreamlineColors.TextPrimary else StreamlineColors.TextSecondary,
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Rounded.ExpandMore,
                contentDescription = "Language filter",
                tint = if (active) StreamlineColors.Gold else StreamlineColors.TextTertiary,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

@Composable
private fun GenreFilterPill(
    genre: MovieGenre?,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val active = genre != null
    val shape = RoundedCornerShape(50)
    Surface(
        onClick = onClick,
        modifier = Modifier.onFocusChanged { focused = it.isFocused },
        shape = shape,
        color = if (active || focused) {
            StreamlineColors.Gold.copy(alpha = 0.13f)
        } else {
            Color.White.copy(alpha = 0.04f)
        },
        border = BorderStroke(
            if (focused) 2.dp else 1.dp,
            if (active || focused) StreamlineColors.Gold.copy(alpha = 0.55f)
            else Color.White.copy(alpha = 0.08f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.MovieFilter,
                contentDescription = null,
                tint = if (active || focused) StreamlineColors.Gold else StreamlineColors.TextTertiary,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = genre?.label ?: "All genres",
                color = if (active || focused) StreamlineColors.TextPrimary else StreamlineColors.TextSecondary,
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Rounded.ExpandMore,
                contentDescription = "Genre filter",
                tint = if (active || focused) StreamlineColors.Gold else StreamlineColors.TextTertiary,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

@Composable
private fun MagnetSourceSelector(
    selected: MagnetSource,
    onSelected: (MagnetSource) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(end = 12.dp),
    ) {
        items(MagnetSource.entries, key = { it.name }) { source ->
            val accent = sourceAccent(source)
            val isSelected = source == selected
            var focused by remember(source) { mutableStateOf(false) }
            Surface(
                onClick = { onSelected(source) },
                modifier = Modifier.onFocusChanged { focused = it.isFocused },
                shape = RoundedCornerShape(50),
                color = if (isSelected || focused) {
                    accent.copy(alpha = 0.16f)
                } else {
                    Color.White.copy(alpha = 0.045f)
                },
                contentColor = if (isSelected || focused) accent else StreamlineColors.TextSecondary,
                border = BorderStroke(
                    width = if (focused) 2.dp else 1.dp,
                    color = if (isSelected || focused) {
                        accent.copy(alpha = 0.55f)
                    } else {
                        Color.White.copy(alpha = 0.08f)
                    },
                ),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = sourceIcon(source),
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = source.label,
                        color = if (isSelected) StreamlineColors.TextPrimary else StreamlineColors.TextSecondary,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun MagnetSearchField(
    query: String,
    isPending: Boolean,
    placeholder: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val shape = RoundedCornerShape(15.dp)
    val borderColor by animateColorAsState(
        targetValue = if (focused) {
            StreamlineColors.Gold.copy(alpha = 0.55f)
        } else {
            StreamlineColors.Hairline.copy(alpha = 0.65f)
        },
        label = "archive-search-border",
    )

    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused },
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = StreamlineColors.TextPrimary),
        singleLine = true,
        cursorBrush = SolidColor(StreamlineColors.Gold),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(shape)
                    .background(StreamlineColors.SurfaceRaised.copy(alpha = 0.74f))
                    .border(1.dp, borderColor, shape)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = if (focused) StreamlineColors.Gold else StreamlineColors.TextTertiary,
                    modifier = Modifier.size(19.dp),
                )
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isBlank()) {
                        Text(
                            text = placeholder,
                            color = StreamlineColors.TextTertiary,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
                when {
                    isPending -> CircularProgressIndicator(
                        modifier = Modifier.size(15.dp),
                        color = StreamlineColors.Gold,
                        strokeWidth = 1.7.dp,
                    )
                    query.isNotEmpty() -> Box(
                        modifier = Modifier
                            .size(27.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                            .clickable(onClick = onClear),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Clear search",
                            tint = StreamlineColors.TextSecondary,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }
        },
    )
}

@Composable
fun MagnetCard(
    item: MagnetTitle,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onTrailer: () -> Unit,
    languageFilter: LanguageFilter = LanguageFilter(),
    modifier: Modifier = Modifier,
) {
    val accent = sourceAccent(item.source)
    val languageMatch = remember(item.title, languageFilter) {
        if (languageFilter.isAll) null else languageFilter.match(item.title)
    }
    val shape = RoundedCornerShape(18.dp)
    val rating = item.rating
    val quality = item.quality
    val sizeLabel = item.sizeLabel
    val seeds = item.seeds
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(StreamlineColors.Surface.copy(alpha = 0.84f))
            .border(1.dp, StreamlineColors.Hairline.copy(alpha = 0.62f), shape),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f),
        ) {
            Artwork(
                url = item.posterUrl,
                contentDescription = "Poster for " + item.title,
                fallbackSeed = item.title,
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(86.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)),
                        ),
                    ),
            )
            SourceBadge(
                source = item.source,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
            )
            if (rating != null && rating > 0.0) {
                RatingBadge(
                    rating = item.ratingLabel,
                    compact = true,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                )
            } else if (!quality.isNullOrBlank()) {
                MetaPill(
                    text = quality,
                    tone = accent,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                )
            }
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 9.dp, bottom = 34.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                MagnetOverlayButton(
                    icon = Icons.Rounded.Movie,
                    contentDescription = "Watch trailer on YouTube",
                    tint = Color(0xFFFF4D4D),
                    onClick = onTrailer,
                )
                MagnetOverlayButton(
                    icon = Icons.Rounded.PlayArrow,
                    contentDescription = "Play in player",
                    tint = accent,
                    onClick = onPlay,
                )
                MagnetOverlayButton(
                    icon = Icons.Rounded.Download,
                    contentDescription = "Download movie",
                    tint = StreamlineColors.Gold,
                    onClick = onDownload,
                )
            }
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 9.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!quality.isNullOrBlank()) {
                    Text(
                        text = quality,
                        color = accent,
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.7.sp),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                if (!sizeLabel.isNullOrBlank()) {
                    Text(
                        text = sizeLabel,
                        color = Color.White.copy(alpha = 0.82f),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (seeds != null) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowUpward,
                        contentDescription = null,
                        tint = StreamlineColors.Success,
                        modifier = Modifier.size(11.dp),
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = seeds.toString(),
                        color = StreamlineColors.Success,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = item.title,
                color = StreamlineColors.TextPrimary,
                style = MaterialTheme.typography.titleSmall,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = listOfNotNull(
                    item.year?.toString(),
                    item.genreLabel,
                ).joinToString(" · "),
                color = StreamlineColors.TextTertiary,
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.3.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (languageMatch?.any == true) {
                Spacer(Modifier.height(5.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (languageMatch.audio) {
                        LanguageMatchBadge(
                            label = "AUDIO",
                            tone = StreamlineColors.Gold,
                            icon = Icons.AutoMirrored.Rounded.VolumeUp,
                        )
                    }
                    if (languageMatch.subtitle) {
                        LanguageMatchBadge(
                            label = "SUBS",
                            tone = StreamlineColors.Ice,
                            icon = Icons.Rounded.Subtitles,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguageMatchBadge(
    label: String,
    tone: Color,
    icon: ImageVector,
) {
    val shape = RoundedCornerShape(5.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(tone.copy(alpha = 0.13f))
            .border(1.dp, tone.copy(alpha = 0.4f), shape)
            .padding(horizontal = 5.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tone,
            modifier = Modifier.size(9.dp),
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = label,
            color = tone,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 8.sp,
                letterSpacing = 0.7.sp,
            ),
        )
    }
}

@Composable
private fun SourceBadge(
    source: MagnetSource,
    modifier: Modifier = Modifier,
) {
    val accent = sourceAccent(source)
    val shape = RoundedCornerShape(7.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.72f))
            .border(1.dp, accent.copy(alpha = 0.5f), shape)
            .padding(horizontal = 7.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(accent),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = source.label.uppercase(),
            color = accent,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.sp,
                letterSpacing = 1.sp,
            ),
        )
    }
}

@Composable
private fun MagnetOverlayButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = CircleShape
    Box(
        modifier = modifier
            .size(if (isTelevision()) 48.dp else 44.dp)
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(Color.Black.copy(alpha = if (focused) 0.9f else 0.74f))
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) StreamlineColors.Gold else Color.White.copy(alpha = 0.22f),
                shape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(23.dp),
        )
    }
}

@Composable
fun MagnetCardSkeleton(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(StreamlineColors.Surface.copy(alpha = 0.6f))
            .border(1.dp, StreamlineColors.Hairline.copy(alpha = 0.5f), shape),
    ) {
        ShimmerBlock(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f),
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        )
        Column(modifier = Modifier.padding(10.dp)) {
            ShimmerBlock(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp),
                shape = RoundedCornerShape(4.dp),
            )
            Spacer(Modifier.height(6.dp))
            ShimmerBlock(
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .height(9.dp),
                shape = RoundedCornerShape(4.dp),
            )
            Spacer(Modifier.height(12.dp))
            ShimmerBlock(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(12.dp),
            )
        }
    }
}

@Composable
fun ArchiveFooter(
    state: MagnetCatalogUiState,
    onLoadMore: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            state.isLoadingMore -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = StreamlineColors.Gold,
                    strokeWidth = 1.6.dp,
                )
                Text(
                    text = "Finding more links…",
                    color = StreamlineColors.TextTertiary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            state.loadMoreError != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = state.loadMoreError,
                    color = StreamlineColors.TextTertiary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                TextButton(onClick = onLoadMore) {
                    Text("Try again", color = StreamlineColors.Gold)
                }
            }
            state.canLoadMore -> Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onLoadMore)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "Load more links",
                    color = StreamlineColors.TextSecondary,
                    style = MaterialTheme.typography.labelMedium,
                )
                Icon(
                    imageVector = Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = StreamlineColors.TextTertiary,
                    modifier = Modifier.size(16.dp),
                )
            }
            else -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HorizontalDivider(
                    modifier = Modifier.width(30.dp),
                    color = StreamlineColors.Hairline,
                )
                Text(
                    text = if (state.source == MagnetSource.ALL) {
                        "Top matches · switch source for more"
                    } else {
                        "That's every link"
                    },
                    color = StreamlineColors.TextTertiary,
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp),
                )
                HorizontalDivider(
                    modifier = Modifier.width(30.dp),
                    color = StreamlineColors.Hairline,
                )
            }
        }
    }
}

@Composable
fun InlineArchiveError(
    message: String,
    onRetry: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(StreamlineColors.Danger.copy(alpha = 0.08f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.WifiOff,
            contentDescription = null,
            tint = StreamlineColors.Danger,
            modifier = Modifier.size(17.dp),
        )
        Spacer(Modifier.width(9.dp))
        Text(
            text = message,
            color = StreamlineColors.TextSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "Retry",
            color = StreamlineColors.Gold,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onRetry)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

private fun sourceAccent(source: MagnetSource): Color = when (source) {
    MagnetSource.ALL -> StreamlineColors.Gold
    MagnetSource.YTS -> StreamlineColors.Gold
    MagnetSource.X1337 -> StreamlineColors.Ice
    MagnetSource.TPB -> StreamlineColors.Success
    MagnetSource.TORRENTS_CSV -> Color(0xFFB7A6E8)
    MagnetSource.KNABEN -> Color(0xFF7CC4FF)
    MagnetSource.NYAA -> Color(0xFFFFB86B)
    MagnetSource.BTDIGG -> Color(0xFF8ED1C8)
    MagnetSource.KICKASS -> StreamlineColors.Coral
}

private fun sourceIcon(source: MagnetSource): ImageVector = when (source) {
    MagnetSource.ALL -> Icons.Rounded.TravelExplore
    MagnetSource.YTS -> Icons.Rounded.Movie
    MagnetSource.X1337 -> Icons.Rounded.Bolt
    MagnetSource.TPB -> Icons.Rounded.Public
    MagnetSource.TORRENTS_CSV -> Icons.Rounded.Storage
    MagnetSource.KNABEN -> Icons.Rounded.Search
    MagnetSource.NYAA -> Icons.Rounded.Animation
    MagnetSource.BTDIGG -> Icons.Rounded.Language
    MagnetSource.KICKASS -> Icons.Rounded.Whatshot
}

package com.streamline.app.feature.magnets

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MovieFilter
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.streamline.app.core.designsystem.CinematicBackground
import com.streamline.app.core.designsystem.EmptyOrErrorState
import com.streamline.app.core.designsystem.StreamlineColors
import com.streamline.app.core.designsystem.isTelevision
import com.streamline.app.core.designsystem.tvEdgePadding
import com.streamline.app.core.di.AppContainer
import com.streamline.app.domain.model.MagnetSource
import com.streamline.app.domain.model.MagnetTitle
import com.streamline.app.domain.model.MovieGenre
import com.streamline.app.domain.model.PlaybackRequest
import kotlinx.coroutines.launch

private fun Context.openYouTubeTrailer(item: MagnetTitle) {
    val query = listOfNotNull(
        item.title,
        item.year?.toString(),
        "trailer",
    ).joinToString(" ")
    val url = "https://www.youtube.com/results?search_query=" + Uri.encode(query)
    runCatching {
        startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

@Composable
fun MagnetCatalogRoute(
    container: AppContainer,
    onOpenSettings: () -> Unit,
    onOpenPlayer: (PlaybackRequest) -> Unit,
    onDownload: (PlaybackRequest) -> Unit,
    onOpenDownloads: () -> Unit,
) {
    val viewModel: MagnetCatalogViewModel = viewModel(
        factory = MagnetCatalogViewModel.factory(
            container.magnetCatalogRepository,
            container.settingsRepository,
        ),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    MagnetCatalogScreen(
        state = state,
        onSourceSelected = viewModel::onSourceSelected,
        onQueryChange = viewModel::onQueryChange,
        onClearQuery = viewModel::clearQuery,
        onRefresh = viewModel::refresh,
        onRetry = viewModel::retry,
        onLoadMore = viewModel::loadNextPage,
        onLanguageFilterChange = viewModel::onLanguageFilterChange,
        onGenreFilterChange = viewModel::onGenreFilterChange,
        onClearFilters = viewModel::clearFilters,
        onTrailer = { item -> context.openYouTubeTrailer(item) },
        onOpenSettings = onOpenSettings,
        onOpenPlayer = onOpenPlayer,
        onDownload = onDownload,
        onOpenDownloads = onOpenDownloads,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MagnetCatalogScreen(
    state: MagnetCatalogUiState,
    onSourceSelected: (MagnetSource) -> Unit,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onLanguageFilterChange: (com.streamline.app.domain.model.LanguageFilter) -> Unit,
    onGenreFilterChange: (MovieGenre?) -> Unit,
    onClearFilters: () -> Unit,
    onTrailer: (MagnetTitle) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPlayer: (PlaybackRequest) -> Unit,
    onDownload: (PlaybackRequest) -> Unit,
    onOpenDownloads: () -> Unit,
) {
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val isTv = isTelevision()
    val edgePadding = tvEdgePadding(isTv)
    val headerOffset = if (isTv) 96.dp else 66.dp
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    var qualityTarget by remember { mutableStateOf<MagnetTitle?>(null) }
    var qualityAction by remember { mutableStateOf(QualityAction.PLAY) }
    var showLanguageSheet by remember { mutableStateOf(false) }
    var showGenreSheet by remember { mutableStateOf(false) }
    val latestState by rememberUpdatedState(state)
    val latestLoadMore by rememberUpdatedState(onLoadMore)
    val showScrollTop by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex > 1 || gridState.firstVisibleItemScrollOffset > 320
        }
    }

    LaunchedEffect(gridState) {
        snapshotFlow {
            val layout = gridState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible to layout.totalItemsCount
        }.collect { (lastVisible, total) ->
            val current = latestState
            if (
                current.canLoadMore &&
                !current.isLoading &&
                !current.isLoadingMore &&
                !current.isRefreshing &&
                total > 0 &&
                lastVisible >= total - 3
            ) {
                latestLoadMore()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CinematicBackground {
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = edgePadding,
                        end = edgePadding,
                        top = topInset + headerOffset,
                        bottom = bottomInset + if (isTv) 40.dp else 28.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    item(
                        key = "archive-header",
                        span = { GridItemSpan(maxLineSpan) },
                    ) {
                        MagnetArchiveHeader(
                            state = state,
                            onSourceSelected = onSourceSelected,
                            onLanguageFilterClick = { showLanguageSheet = true },
                            onGenreFilterClick = { showGenreSheet = true },
                            onQueryChange = onQueryChange,
                            onClearQuery = onClearQuery,
                            onRefresh = onRefresh,
                        )
                    }

                    if (state.sourceWarnings.isNotEmpty()) {
                        item(
                            key = "archive-source-warnings",
                            span = { GridItemSpan(maxLineSpan) },
                        ) {
                            SourceWarningsBanner(state.sourceWarnings)
                        }
                    }

                    if (state.isLoading) {
                        items(6, key = { index -> "archive-skeleton-" + index }) {
                            MagnetCardSkeleton()
                        }
                    } else if (state.items.isEmpty()) {
                        item(
                            key = "archive-empty",
                            span = { GridItemSpan(maxLineSpan) },
                        ) {
                            val blocked = state.source == MagnetSource.X1337 &&
                                state.errorMessage.orEmpty().contains("blocking", ignoreCase = true)
                            val languageActive = !state.languageFilter.isAll
                            val genreActive = state.genreFilter != null
                            val filtersActive = languageActive || genreActive
                            val requiresQuery = state.source.requiresQuery &&
                                !state.isSearching &&
                                state.query.isBlank()
                            val sourceName = if (state.source == MagnetSource.ALL) {
                                "all eight sources"
                            } else {
                                state.source.label
                            }
                            EmptyOrErrorState(
                                title = when {
                                    state.errorMessage != null ->
                                        if (blocked) "The 1337x door is shut" else "No signal from the archive"
                                    requiresQuery -> "Search " + state.source.label
                                    languageActive && genreActive ->
                                        "No " + state.languageFilter.shortLabel + " " +
                                            state.genreFilter?.label + " links"
                                    languageActive -> "No " + state.languageFilter.shortLabel + " links"
                                    genreActive -> "No " + state.genreFilter?.label + " links"
                                    else -> "No links in this cut"
                                },
                                message = when {
                                    state.errorMessage != null -> state.errorMessage.orEmpty()
                                    requiresQuery ->
                                        "Type a title above to search " + state.source.label +
                                            " for copy-ready magnets."
                                    languageActive && genreActive ->
                                        "No releases on this page match both filters. Try another " +
                                            "language or type, or clear one of them."
                                    languageActive ->
                                        "No releases in this batch carry this audio/subtitle tag. " +
                                            "Load more, try another language, or clear the filter."
                                    genreActive ->
                                        "No releases on this page match this movie type. " +
                                            "Try another genre, or clear the filter."
                                    state.isSearching ->
                                        "No release matched “" + state.activeQuery + "” on " +
                                            sourceName + ". Try a shorter search or switch sources."
                                    else ->
                                        "This source returned no copy-ready magnets. Refresh or switch sources."
                                },
                                icon = when {
                                    blocked -> Icons.Rounded.Shield
                                    state.errorMessage != null -> Icons.Rounded.CloudOff
                                    requiresQuery -> Icons.Rounded.Search
                                    languageActive -> Icons.Rounded.Language
                                    genreActive -> Icons.Rounded.MovieFilter
                                    else -> Icons.Rounded.SearchOff
                                },
                                actionLabel = if (requiresQuery) {
                                    null
                                } else {
                                    when {
                                        state.errorMessage != null -> "Try again"
                                        filtersActive && state.canLoadMore -> "Load more"
                                        filtersActive -> "Clear filters"
                                        state.isSearching -> "Clear search"
                                        else -> "Refresh"
                                    }
                                },
                                onAction = if (requiresQuery) {
                                    null
                                } else {
                                    when {
                                        state.errorMessage != null -> onRetry
                                        filtersActive && state.canLoadMore -> onLoadMore
                                        filtersActive -> onClearFilters
                                        state.isSearching -> onClearQuery
                                        else -> onRefresh
                                    }
                                },
                                tone = if (blocked) StreamlineColors.Ice else StreamlineColors.Gold,
                            )
                        }
                    } else {
                        if (state.errorMessage != null) {
                            item(
                                key = "archive-inline-error",
                                span = { GridItemSpan(maxLineSpan) },
                            ) {
                                InlineArchiveError(
                                    message = state.errorMessage.orEmpty(),
                                    onRetry = onRetry,
                                )
                            }
                        }
                        items(
                            items = state.items,
                            key = { it.id },
                        ) { item ->
                            MagnetCard(
                                item = item,
                                onPlay = {
                                    val releases = item.playableReleases()
                                    if (releases.size == 1) {
                                        onOpenPlayer(item.playbackRequest(releases.first()))
                                    } else {
                                        qualityAction = QualityAction.PLAY
                                        qualityTarget = item
                                    }
                                },
                                onTrailer = { onTrailer(item) },
                                onDownload = {
                                    qualityAction = QualityAction.DOWNLOAD
                                    qualityTarget = item
                                },
                                languageFilter = state.languageFilter,
                            )
                        }
                        item(
                            key = "archive-footer",
                            span = { GridItemSpan(maxLineSpan) },
                        ) {
                            ArchiveFooter(
                                state = state,
                                onLoadMore = onLoadMore,
                            )
                        }
                    }
                }
            }
        }

        MagnetTopBar(
            onOpenDownloads = onOpenDownloads,
            onOpenSettings = onOpenSettings,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        AnimatedVisibility(
            visible = showScrollTop,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = edgePadding,
                    bottom = bottomInset + if (isTv) 96.dp else 86.dp,
                ),
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
        ) {
            ScrollTopButton(
                isTv = isTv,
                onClick = {
                    scope.launch { gridState.animateScrollToItem(0) }
                },
            )
        }

    }

    if (showLanguageSheet) {
        LanguageFilterSheet(
            current = state.languageFilter,
            onApply = { filter ->
                onLanguageFilterChange(filter)
                showLanguageSheet = false
            },
            onDismiss = { showLanguageSheet = false },
        )
    }

    if (showGenreSheet) {
        GenreFilterSheet(
            current = state.genreFilter,
            onApply = { genre ->
                onGenreFilterChange(genre)
                showGenreSheet = false
            },
            onDismiss = { showGenreSheet = false },
        )
    }

    qualityTarget?.let { target ->
        PlaybackQualitySheet(
            item = target,
            action = qualityAction,
            onSelect = { release ->
                val request = target.playbackRequest(release)
                if (qualityAction == QualityAction.PLAY) {
                    onOpenPlayer(request)
                } else {
                    onDownload(request)
                }
                qualityTarget = null
            },
            onDismiss = { qualityTarget = null },
        )
    }
}

@Composable
private fun SourceWarningsBanner(warnings: List<String>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = StreamlineColors.Gold.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, StreamlineColors.Gold.copy(alpha = 0.3f)),
    ) {
        Text(
            text = "Some sources are unavailable: " + warnings.joinToString(" · "),
            color = StreamlineColors.TextSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun ScrollTopButton(
    isTv: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = Modifier.onFocusChanged { focused = it.isFocused },
        shape = CircleShape,
        color = StreamlineColors.Gold,
        contentColor = StreamlineColors.Ink,
        border = BorderStroke(
            if (focused) 2.dp else 1.dp,
            if (focused) StreamlineColors.TextPrimary else StreamlineColors.GoldDeep,
        ),
        shadowElevation = 10.dp,
    ) {
        Box(
            modifier = Modifier.size(if (isTv) 54.dp else 48.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowUp,
                contentDescription = "Scroll to top",
                tint = StreamlineColors.Ink,
                modifier = Modifier.size(if (isTv) 28.dp else 26.dp),
            )
        }
    }
}
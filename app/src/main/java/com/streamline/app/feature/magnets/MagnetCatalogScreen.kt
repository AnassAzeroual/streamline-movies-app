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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.streamline.app.core.designsystem.Artwork
import com.streamline.app.core.designsystem.CinematicBackground
import com.streamline.app.core.designsystem.EmptyOrErrorState
import com.streamline.app.core.designsystem.StreamlineColors
import com.streamline.app.core.designsystem.isTelevision
import com.streamline.app.core.designsystem.tvEdgePadding
import com.streamline.app.core.di.AppContainer
import com.streamline.app.core.download.DownloadFileOption
import com.streamline.app.core.logging.AppLog
import com.streamline.app.core.tv.TvRemotePlayback
import com.streamline.app.core.playback.PlaybackHistoryEntry
import com.streamline.app.domain.model.MagnetSource
import com.streamline.app.domain.model.MagnetTitle
import com.streamline.app.domain.model.PlaybackAlternative
import com.streamline.app.domain.model.MovieGenre
import com.streamline.app.domain.model.PlaybackRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun normalizedCatalogTitle(title: String): String = title
    .lowercase()
    .replace(Regex("\\b(19|20)\\d{2}\\b"), " ")
    .replace(
        Regex(
            "\\b(2160p|1080p|720p|480p|4k|uhd|bluray|blu-ray|webrip|web-dl|webdl|" +
                "x264|x265|hevc|avc|aac|dts|hdr|remux)\\b",
        ),
        " ",
    )
    .replace(Regex("[^a-z0-9]+"), " ")
    .trim()
    .replace(Regex("\\s+"), " ")

private fun playbackWithCatalogAlternatives(
    item: MagnetTitle,
    release: com.streamline.app.domain.model.MagnetRelease?,
    catalog: List<MagnetTitle>,
): PlaybackRequest {
    val base = item.playbackRequest(release)
    if (catalog.size <= 1) return base
    val key = normalizedCatalogTitle(item.title)
    if (key.isBlank()) return base
    val extras = catalog.asSequence()
        .filter { it.id != item.id && normalizedCatalogTitle(it.title) == key }
        .flatMap { other ->
            other.playableReleases().asSequence().map { alternative ->
                PlaybackAlternative(
                    magnetUrl = alternative.magnetUrl,
                    torrentUrl = alternative.torrentUrl,
                    quality = alternative.quality,
                    sourceLabel = other.source.label,
                    seeds = alternative.seeds,
                    sizeLabel = alternative.sizeLabel,
                )
            }
        }
        .filter { alternative ->
            alternative.magnetUrl != base.magnetUrl &&
                base.alternatives.none { it.magnetUrl == alternative.magnetUrl }
        }
        .distinctBy { it.magnetUrl }
        .take(8)
        .toList()
    return base.copy(alternatives = (base.alternatives + extras).distinctBy { it.magnetUrl })
}

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
    onOpenFavorites: () -> Unit,
    onOpenPlayer: (PlaybackRequest) -> Unit,
    onDownload: (PlaybackRequest, List<Int>) -> Unit,
    onOpenDownloads: () -> Unit,
) {
    val viewModel: MagnetCatalogViewModel = viewModel(
        factory = MagnetCatalogViewModel.factory(
            container.magnetCatalogRepository,
            container.settingsRepository,
        ),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val history by container.playbackHistoryStore.items.collectAsStateWithLifecycle()
    val favorites by container.favoriteStore.items.collectAsStateWithLifecycle()
    val favoriteIds = remember(favorites) { favorites.mapTo(HashSet()) { it.item.id } }
    val context = LocalContext.current
    val pickerScope = rememberCoroutineScope()
    var downloadRequest by remember { mutableStateOf<PlaybackRequest?>(null) }
    var downloadFiles by remember { mutableStateOf<List<DownloadFileOption>>(emptyList()) }
    var downloadSelection by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var downloadLoading by remember { mutableStateOf(false) }
    var downloadError by remember { mutableStateOf<String?>(null) }

    MagnetCatalogScreen(
        state = state,
        history = history,
        favoriteIds = favoriteIds,
        onToggleFavorite = { item ->
            val added = container.favoriteStore.toggle(item)
            AppLog.event(
                "FAVORITES",
                if (added) "add" else "remove",
                fields = mapOf("id" to item.id, "title" to item.title),
            )
        },
        onOpenHistory = { entry ->
            AppLog.event("UI", "continue_watching_click", fields = mapOf("title" to entry.title))
            onOpenPlayer(entry.request())
        },
        onSourceSelected = { source ->
            AppLog.event("UI", "source_select", fields = mapOf("source" to source.label))
            viewModel.onSourceSelected(source)
        },
        onQueryChange = { query ->
            AppLog.event("UI", "query_change", fields = mapOf("query" to query))
            viewModel.onQueryChange(query)
        },
        onClearQuery = {
            AppLog.event("UI", "query_clear")
            viewModel.clearQuery()
        },
        onRefresh = {
            AppLog.event("UI", "refresh", fields = mapOf("source" to state.source.label))
            viewModel.refresh()
        },
        onRetry = {
            AppLog.event("UI", "retry_load", fields = mapOf("source" to state.source.label))
            viewModel.retry()
        },
        onLoadMore = {
            AppLog.event("UI", "load_more", fields = mapOf("page" to state.page))
            viewModel.loadNextPage()
        },
        onLanguageFilterChange = { filter ->
            AppLog.event("UI", "language_filter", fields = mapOf("filter" to filter.shortLabel))
            viewModel.onLanguageFilterChange(filter)
        },
        onGenreFilterChange = { genre ->
            AppLog.event("UI", "genre_filter", fields = mapOf("genre" to (genre?.label ?: "All")))
            viewModel.onGenreFilterChange(genre)
        },
        onClearFilters = {
            AppLog.event("UI", "clear_filters")
            viewModel.clearFilters()
        },
        onTrailer = { item ->
            AppLog.event("UI", "trailer_click", fields = mapOf("title" to item.title))
            context.openYouTubeTrailer(item)
        },
        onOpenSettings = {
            AppLog.event("UI", "settings_click")
            onOpenSettings()
        },
        onOpenFavorites = {
            AppLog.event("UI", "favorites_click")
            onOpenFavorites()
        },
        onOpenPlayer = { request ->
            AppLog.event(
                "PLAYBACK",
                "open_request",
                fields = mapOf(
                    "title" to request.title,
                    "quality" to request.quality,
                    "source" to request.sourceLabel,
                    "alternatives" to request.alternatives.size,
                ),
            )
            onOpenPlayer(request)
        },
        onDownload = { request ->
            AppLog.event(
                "DOWNLOAD",
                "enqueue_click",
                fields = mapOf(
                    "title" to request.title,
                    "quality" to request.quality,
                    "source" to request.sourceLabel,
                ),
            )
            if (!downloadLoading) {
                downloadRequest = request
                downloadFiles = emptyList()
                downloadSelection = emptySet()
                downloadError = null
                downloadLoading = true
                pickerScope.launch {
                    container.downloadManager.inspectFiles(request)
                        .onSuccess { files ->
                            if (downloadRequest?.id != request.id) return@onSuccess
                            downloadFiles = files
                            downloadSelection = defaultDownloadSelection(files)
                            downloadLoading = false
                        }
                        .onFailure { error ->
                            downloadError = error.message ?: "Could not read the torrent file list."
                            downloadLoading = false
                        }
                }
            }
        },
        onOpenDownloads = {
            AppLog.event("UI", "downloads_click")
            onOpenDownloads()
        },
    )

    downloadRequest?.let { request ->
        DownloadFilePickerDialog(
            files = downloadFiles,
            selected = downloadSelection,
            loading = downloadLoading,
            error = downloadError,
            onToggle = { index ->
                downloadSelection = if (index in downloadSelection) {
                    downloadSelection - index
                } else {
                    downloadSelection + index
                }
            },
            onDismiss = {
                downloadRequest = null
                downloadLoading = false
                downloadError = null
            },
            onConfirm = {
                val selected = downloadSelection.toList()
                if (selected.isNotEmpty()) {
                    downloadRequest = null
                    downloadLoading = false
                    onDownload(request, selected)
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MagnetCatalogScreen(
    state: MagnetCatalogUiState,
    history: List<PlaybackHistoryEntry>,
    favoriteIds: Set<String>,
    onToggleFavorite: (MagnetTitle) -> Unit,
    onOpenHistory: (PlaybackHistoryEntry) -> Unit,
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
    onOpenFavorites: () -> Unit,
    onOpenPlayer: (PlaybackRequest) -> Unit,
    onDownload: (PlaybackRequest) -> Unit,
    onOpenDownloads: () -> Unit,
) {
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val isTv = isTelevision()
    val sourceFocus = remember { FocusRequester() }
    val topBarFocus = remember { FocusRequester() }
    var initialFocusDone by remember { mutableStateOf(false) }
    LaunchedEffect(isTv) {
        if (!isTv) return@LaunchedEffect
        repeat(6) {
            delay(400)
            if (runCatching { sourceFocus.requestFocus() }.isSuccess) return@LaunchedEffect
        }
        runCatching { topBarFocus.requestFocus() }
    }
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
                    if (history.isNotEmpty()) {
                        item(
                            key = "continue-watching",
                            span = { GridItemSpan(maxLineSpan) },
                        ) {
                            ContinueWatchingRail(history, onOpenHistory)
                        }
                    }

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
                            initialFocusRequester = sourceFocus,
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
                                "all sources"
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
                        itemsIndexed(
                            items = state.items,
                            key = { _, item -> item.id },
                        ) { index, item ->
                            MagnetCard(
                                item = item,
                                modifier = Modifier,
                                onPlay = {
                                    val releases = item.playableReleases()
                                    if (releases.size == 1) {
                                        onOpenPlayer(playbackWithCatalogAlternatives(item, releases.first(), state.items))
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
                                isFavorite = item.id in favoriteIds,
                                onToggleFavorite = { onToggleFavorite(item) },
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
            onOpenFavorites = onOpenFavorites,
            onOpenDownloads = onOpenDownloads,
            onOpenSettings = onOpenSettings,
            modifier = Modifier.align(Alignment.TopCenter),
            initialFocusRequester = topBarFocus,
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
                    onOpenPlayer(playbackWithCatalogAlternatives(target, release, state.items))
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
private fun ContinueWatchingRail(
    history: List<PlaybackHistoryEntry>,
    onOpen: (PlaybackHistoryEntry) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "CONTINUE WATCHING",
            color = StreamlineColors.Gold,
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.6.sp),
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 8.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            history.take(10).forEach { entry ->
                Surface(
                    onClick = { onOpen(entry) },
                    modifier = Modifier.width(150.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = StreamlineColors.Surface,
                    border = BorderStroke(1.dp, StreamlineColors.Hairline),
                ) {
                    Column {
                        Artwork(
                            url = entry.posterUrl,
                            contentDescription = entry.title,
                            fallbackSeed = entry.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(84.dp),
                            shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
                        )
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = entry.title,
                                color = StreamlineColors.TextPrimary,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(5.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .background(StreamlineColors.Gold.copy(alpha = 0.25f)),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(entry.progress)
                                        .height(3.dp)
                                        .background(StreamlineColors.Gold),
                                )
                            }
                        }
                    }
                }
            }
        }
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
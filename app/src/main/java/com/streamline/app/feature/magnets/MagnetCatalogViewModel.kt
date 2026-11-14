package com.streamline.app.feature.magnets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.streamline.app.core.settings.SettingsRepository
import com.streamline.app.domain.model.LanguageFilter
import com.streamline.app.domain.model.MagnetPage
import com.streamline.app.domain.model.MagnetSource
import com.streamline.app.domain.model.MovieGenre
import com.streamline.app.domain.model.MagnetTitle
import com.streamline.app.domain.repository.MagnetCatalogException
import com.streamline.app.domain.repository.MagnetCatalogRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

data class MagnetCatalogUiState(
    val source: MagnetSource = MagnetSource.YTS,
    val query: String = "",
    val activeQuery: String = "",
    val languageFilter: LanguageFilter = LanguageFilter(),
    val genreFilter: MovieGenre? = null,
    val hideAdult: Boolean = true,
    val items: List<MagnetTitle> = emptyList(),
    val page: Int = 0,
    val totalResults: Int? = null,
    val canLoadMore: Boolean = false,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isSearchPending: Boolean = false,
    val errorMessage: String? = null,
    val loadMoreError: String? = null,
    val sourceWarnings: List<String> = emptyList(),
) {
    val isSearching: Boolean
        get() = activeQuery.isNotBlank()
}

class MagnetCatalogViewModel(
    private val repository: MagnetCatalogRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        MagnetCatalogUiState(hideAdult = settingsRepository.settings.value.hideAdultContent),
    )
    val uiState: StateFlow<MagnetCatalogUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var searchJob: Job? = null
    private var requestEpoch: Int = 0

    init {
        loadFirstPage()
        viewModelScope.launch {
            settingsRepository.settings
                .map { it.hideAdultContent }
                .distinctUntilChanged()
                .collect { hidden ->
                    if (_uiState.value.hideAdult == hidden) return@collect
                    _uiState.update { it.copy(hideAdult = hidden) }
                    loadFirstPage()
                }
        }
    }

    fun onSourceSelected(source: MagnetSource) {
        if (_uiState.value.source == source) return
        searchJob?.cancel()
        val pendingQuery = _uiState.value.query.trim()
        _uiState.update {
            it.copy(
                source = source,
                activeQuery = pendingQuery,
                isSearchPending = false,
            )
        }
        loadFirstPage()
    }

    fun onQueryChange(value: String) {
        _uiState.update {
            it.copy(
                query = value,
                isSearchPending = value.trim() != it.activeQuery,
            )
        }
        searchJob?.cancel()
        val trimmed = value.trim()
        if (trimmed.isBlank()) {
            if (_uiState.value.activeQuery.isNotBlank()) {
                _uiState.update { it.copy(activeQuery = "", isSearchPending = false) }
                loadFirstPage()
            } else {
                _uiState.update { it.copy(isSearchPending = false) }
            }
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MILLIS)
            if (_uiState.value.query.trim() == trimmed) {
                _uiState.update { it.copy(activeQuery = trimmed, isSearchPending = false) }
                loadFirstPage()
            }
        }
    }

    fun clearQuery() {
        searchJob?.cancel()
        val wasSearching = _uiState.value.activeQuery.isNotBlank()
        _uiState.update { it.copy(query = "", activeQuery = "", isSearchPending = false) }
        if (wasSearching) loadFirstPage()
    }

    fun onLanguageFilterChange(filter: LanguageFilter) {
        if (_uiState.value.languageFilter == filter) return
        _uiState.update { it.copy(languageFilter = filter) }
        loadFirstPage()
    }

    fun onGenreFilterChange(genre: MovieGenre?) {
        if (_uiState.value.genreFilter == genre) return
        _uiState.update { it.copy(genreFilter = genre) }
        loadFirstPage()
    }

    fun clearFilters() {
        if (_uiState.value.languageFilter.isAll && _uiState.value.genreFilter == null) {
            return
        }
        _uiState.update {
            it.copy(languageFilter = LanguageFilter(), genreFilter = null)
        }
        loadFirstPage()
    }

    fun refresh() {
        loadFirstPage(keepContent = true)
    }

    fun retry() {
        loadFirstPage(keepContent = _uiState.value.items.isNotEmpty())
    }

    fun loadNextPage() {
        val snapshot = _uiState.value
        if (
            snapshot.isLoading ||
            snapshot.isRefreshing ||
            snapshot.isLoadingMore ||
            snapshot.isSearchPending ||
            !snapshot.canLoadMore
        ) {
            return
        }
        val nextPage = snapshot.page + 1
        val epoch = requestEpoch
        val hasNarrowingFilter = !snapshot.languageFilter.isAll ||
            snapshot.genreFilter != null
        val filtersActive = hasNarrowingFilter || snapshot.hideAdult
        val allFeed = snapshot.source == MagnetSource.ALL &&
            snapshot.activeQuery.isBlank()
        _uiState.update { it.copy(isLoadingMore = true, loadMoreError = null) }
        loadJob = viewModelScope.launch {
            try {
                val page = browseFiltered(
                    snapshot = snapshot,
                    startPage = nextPage,
                    maxPages = if (filtersActive) 5 else 1,
                )
                if (epoch != requestEpoch) return@launch
                val filtered = page.items
                _uiState.update { state ->
                    val knownIds = state.items.mapTo(HashSet()) { it.id }
                    val added = filtered.filterNot { it.id in knownIds }
                    val reportedTotal = if (!hasNarrowingFilter && !allFeed) {
                        page.totalResults ?: state.totalResults
                    } else {
                        (state.totalResults ?: state.items.size) + added.size
                    }
                    state.copy(
                        items = state.items + added,
                        page = page.page,
                        totalResults = reportedTotal,
                        canLoadMore = page.hasMore,
                        isLoadingMore = false,
                        loadMoreError = null,
                        sourceWarnings = (state.sourceWarnings + page.warnings).distinct(),
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                if (epoch != requestEpoch) return@launch
                _uiState.update {
                    it.copy(isLoadingMore = false, loadMoreError = error.userMessage())
                }
            }
        }
    }

    private suspend fun browseFiltered(
        snapshot: MagnetCatalogUiState,
        startPage: Int,
        maxPages: Int,
    ): MagnetPage {
        var pageNumber = startPage
        var lastPage: MagnetPage? = null
        val matches = mutableListOf<MagnetTitle>()
        val warnings = mutableListOf<String>()
        var attempts = 0
        while (attempts < maxPages) {
            val page = repository.browse(
                source = snapshot.source,
                query = snapshot.activeQuery,
                page = pageNumber,
            )
            lastPage = page
            warnings += page.warnings
            matches += page.items.filter {
                (!snapshot.hideAdult || !it.isAdult) &&
                    snapshot.languageFilter.matches(it.title) &&
                    (snapshot.genreFilter == null || snapshot.genreFilter.matches(it))
            }
            if (matches.isNotEmpty() || !page.hasMore) break
            pageNumber++
            attempts++
        }
        val finalPage = lastPage
            ?: throw MagnetCatalogException("No catalog response was returned.")
        return finalPage.copy(
            items = matches.distinctBy { it.id },
            warnings = warnings.distinct(),
        )
    }

    private fun loadFirstPage(keepContent: Boolean = false) {
        searchJob?.cancel()
        loadJob?.cancel()
        val epoch = ++requestEpoch
        val snapshot = _uiState.value
        if (keepContent) {
            _uiState.update {
                it.copy(
                    isRefreshing = true,
                    isLoading = false,
                    isLoadingMore = false,
                    isSearchPending = false,
                    errorMessage = null,
                    loadMoreError = null,
                    sourceWarnings = emptyList(),
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    items = emptyList(),
                    page = 0,
                    totalResults = null,
                    canLoadMore = false,
                    isLoading = true,
                    isRefreshing = false,
                    isLoadingMore = false,
                    isSearchPending = false,
                    errorMessage = null,
                    loadMoreError = null,
                    sourceWarnings = emptyList(),
                )
            }
        }

        val hasNarrowingFilter = !snapshot.languageFilter.isAll ||
            snapshot.genreFilter != null
        val filtersActive = hasNarrowingFilter || snapshot.hideAdult
        val allFeed = snapshot.source == MagnetSource.ALL &&
            snapshot.activeQuery.isBlank()
        loadJob = viewModelScope.launch {
            try {
                val page = browseFiltered(
                    snapshot = snapshot,
                    startPage = 1,
                    maxPages = if (filtersActive) 5 else 1,
                )
                if (epoch != requestEpoch) return@launch
                val filtered = page.items
                _uiState.update {
                    it.copy(
                        items = filtered,
                        page = page.page,
                        totalResults = if (!hasNarrowingFilter && !allFeed) {
                            page.totalResults
                        } else {
                            filtered.size
                        },
                        canLoadMore = page.hasMore,
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = null,
                        sourceWarnings = page.warnings,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                if (epoch != requestEpoch) return@launch
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = error.userMessage(),
                    )
                }
            }
        }
    }

    private fun Throwable.userMessage(): String = when (this) {
        is MagnetCatalogException -> message
        is IOException -> "The archive is out of reach. Check your connection and try again."
        else -> message?.takeIf { it.isNotBlank() }
            ?: "Something interrupted the archive. Please try again."
    }

    companion object {
        private const val SEARCH_DEBOUNCE_MILLIS = 420L

        fun factory(
            repository: MagnetCatalogRepository,
            settings: SettingsRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { MagnetCatalogViewModel(repository, settings) }
        }
    }
}

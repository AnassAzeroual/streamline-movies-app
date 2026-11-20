package com.streamline.app.feature.magnets

import com.streamline.app.core.settings.AppSettings
import com.streamline.app.core.settings.SettingsRepository
import com.streamline.app.domain.model.MagnetPage
import com.streamline.app.domain.model.MagnetSource
import com.streamline.app.domain.model.MagnetTitle
import com.streamline.app.domain.repository.MagnetCatalogRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MagnetCatalogViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `refresh clears a pending load-more`() = runTest(dispatcher) {
        val firstPage = MagnetPage(
            items = listOf(torrent("clean", "Dune Part Two 2024 1080p")),
            page = 1,
            hasMore = true,
        )
        val never = CompletableDeferred<MagnetPage>()
        val repository = object : MagnetCatalogRepository {
            override suspend fun browse(
                source: MagnetSource,
                query: String,
                page: Int,
            ): MagnetPage = if (page == 1) firstPage else never.await()
        }
        val viewModel = MagnetCatalogViewModel(repository, FakeSettings())
        advanceUntilIdle()
        viewModel.loadNextPage()
        runCurrent()
        assertEquals(true, viewModel.uiState.value.isLoadingMore)
        viewModel.refresh()
        advanceUntilIdle()
        assertEquals(false, viewModel.uiState.value.isLoadingMore)
    }

    @Test
    fun `adult filtering scans past an empty page`() = runTest(dispatcher) {
        val pageOne = MagnetPage(
            items = listOf(
                torrent("adult", "XXX Compilation 2026"),
            ),
            page = 1,
            hasMore = true,
        )
        val pageTwo = MagnetPage(
            items = listOf(
                torrent("clean", "Dune Part Two 2024 1080p"),
            ),
            page = 2,
            hasMore = false,
        )
        val repository = object : MagnetCatalogRepository {
            override suspend fun browse(
                source: MagnetSource,
                query: String,
                page: Int,
            ): MagnetPage = if (page == 1) pageOne else pageTwo
        }

        val viewModel = MagnetCatalogViewModel(repository, FakeSettings())
        advanceUntilIdle()

        assertEquals(listOf("clean"), viewModel.uiState.value.items.map { it.id })
        assertEquals(2, viewModel.uiState.value.page)
        assertEquals(false, viewModel.uiState.value.canLoadMore)
    }

    private class FakeSettings : SettingsRepository {
        private val _settings = MutableStateFlow(AppSettings())
        override val settings: StateFlow<AppSettings> = _settings

        override fun setHardwareDecodingEnabled(enabled: Boolean) {
            _settings.value = _settings.value.copy(hardwareDecoding = enabled)
        }

        override fun setStreamStatsEnabled(enabled: Boolean) {
            _settings.value = _settings.value.copy(showStreamStats = enabled)
        }

        override fun setHideAdultContent(hidden: Boolean) {
            _settings.value = _settings.value.copy(hideAdultContent = hidden)
        }

        override fun setProxySettings(
            proxy: com.streamline.app.core.settings.ProxySettings,
        ) {
            _settings.value = _settings.value.copy(proxy = proxy)
        }
    }

    private fun torrent(id: String, title: String) = MagnetTitle(
        id = id,
        source = MagnetSource.YTS,
        title = title,
        magnetUrl = "magnet:?xt=urn:btih:" + id.padEnd(40, 'A'),
    )
}

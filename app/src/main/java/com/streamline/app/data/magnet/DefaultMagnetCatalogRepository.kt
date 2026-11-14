package com.streamline.app.data.magnet

import com.streamline.app.domain.model.MagnetPage
import com.streamline.app.domain.model.MagnetSource
import com.streamline.app.domain.model.MagnetTitle
import com.streamline.app.domain.repository.MagnetCatalogException
import com.streamline.app.domain.repository.MagnetCatalogRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout

class DefaultMagnetCatalogRepository(
    private val providers: Map<MagnetSource, MagnetSourceProvider>,
) : MagnetCatalogRepository {

    override suspend fun browse(
        source: MagnetSource,
        query: String,
        page: Int,
    ): MagnetPage {
        if (source == MagnetSource.ALL) {
            return if (query.isBlank()) browseAllLatest(page) else browseAll(query, page)
        }
        val provider = providers[source]
            ?: throw MagnetCatalogException("This source is not available right now.")
        return provider.browse(query, page)
    }

    private suspend fun browseAllLatest(page: Int): MagnetPage = coroutineScope {
        val queried = providers.entries
            .filter { (source, _) ->
                source.supportsLatest && (page == 1 || source.supportsPaging)
            }
        val outcomes = queried.map { (_, provider) ->
            async { runProvider(provider, "", page) }
        }.awaitAll()
        val successful = outcomes.filter { it.isSuccess }
        if (successful.isEmpty()) {
            throw MagnetCatalogException(
                "The available sources are unreachable right now. Check your connection and try again.",
            )
        }

        val warnings = queried.zip(outcomes).mapNotNull { (entry, result) ->
            result.exceptionOrNull()?.let { entry.key.label + " is unavailable" }
        }
        val pages = queried
            .zip(outcomes)
            .mapNotNull { (entry, result) ->
                result.getOrNull()?.let { entry.key to it }
            }
        val items = pages
            .flatMap { (_, pageResult) -> pageResult.items }
            .filter { item ->
                when (item.source) {
                    MagnetSource.TORRENTS_CSV,
                    MagnetSource.BTDIGG,
                    -> TorrentText.isLikelyVideoTitle(item.title)
                    else -> true
                }
            }
            .distinctBy { TorrentText.hashFromMagnet(it.magnetUrl) ?: it.id }
            .sortedWith(
                compareByDescending<MagnetTitle> { it.posterUrl != null }
                    .thenByDescending { it.seeds ?: 0 },
            )
            .take(ALL_RESULT_LIMIT)

        MagnetPage(
            items = items,
            page = page,
            hasMore = pages.any { (source, pageResult) ->
                source.supportsPaging && pageResult.hasMore
            },
            totalResults = items.size,
            warnings = warnings,
        )
    }

    private suspend fun browseAll(query: String, page: Int): MagnetPage = coroutineScope {
        if (page > 1) {
            return@coroutineScope MagnetPage(items = emptyList(), page = page, hasMore = false)
        }

        val queried = providers.entries.toList()
        val outcomes = queried.map { (_, provider) ->
            async { runProvider(provider, query, page) }
        }.awaitAll()
        val successful = outcomes.filter { it.isSuccess }
        if (successful.isEmpty()) {
            throw MagnetCatalogException(
                "All eight sources are unreachable right now. Check your connection and try again.",
            )
        }

        val warnings = queried.zip(outcomes).mapNotNull { (entry, result) ->
            result.exceptionOrNull()?.let { entry.key.label + " is unavailable" }
        }
        val pages = successful.mapNotNull { it.getOrNull() }
        val items = pages
            .flatMap { it.items }
            .distinctBy { TorrentText.hashFromMagnet(it.magnetUrl) ?: it.id }
            .sortedByDescending { it.seeds ?: 0 }
            .take(ALL_RESULT_LIMIT)
        MagnetPage(
            items = items,
            page = page,
            hasMore = false,
            totalResults = items.size,
            warnings = warnings,
        )
    }

    private suspend fun runProvider(
        provider: MagnetSourceProvider,
        query: String,
        page: Int,
    ): Result<MagnetPage> = try {
        Result.success(withTimeout(PROVIDER_TIMEOUT_MILLIS) { provider.browse(query, page) })
    } catch (timeout: TimeoutCancellationException) {
        Result.failure(timeout)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(error)
    }

    private companion object {
        const val ALL_RESULT_LIMIT = 60
        const val PROVIDER_TIMEOUT_MILLIS = 20_000L
    }
}

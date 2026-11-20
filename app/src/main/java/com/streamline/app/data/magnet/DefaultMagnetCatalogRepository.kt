package com.streamline.app.data.magnet

import com.streamline.app.domain.model.MagnetPage
import com.streamline.app.core.logging.AppLog
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
import java.util.concurrent.ConcurrentHashMap

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
        return runProvider(source, provider, query, page)
            .map { result -> result.copy(items = result.items.filterNot { TorrentText.isAv1(it.title) }) }
            .getOrElse { error -> throw error }
    }

    private suspend fun browseAllLatest(page: Int): MagnetPage = coroutineScope {
        val queried = providers.entries
            .filter { (source, _) ->
                source.supportsLatest && (page == 1 || source.supportsPaging)
            }
        val outcomes = queried.map { (source, provider) ->
            async { runProvider(source, provider, "", page) }
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
                if (TorrentText.isAv1(item.title)) {
                    false
                } else {
                    when (item.source) {
                        MagnetSource.TORRENTS_CSV,
                        MagnetSource.BTDIGG,
                        -> TorrentText.isLikelyVideoTitle(item.title)
                        else -> true
                    }
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
        val outcomes = queried.map { (source, provider) ->
            async { runProvider(source, provider, query, page) }
        }.awaitAll()
        val successful = outcomes.filter { it.isSuccess }
        if (successful.isEmpty()) {
            throw MagnetCatalogException(
                "All sources are unreachable right now. Check your connection and try again.",
            )
        }

        val warnings = queried.zip(outcomes).mapNotNull { (entry, result) ->
            result.exceptionOrNull()?.let { entry.key.label + " is unavailable" }
        }
        val pages = successful.mapNotNull { it.getOrNull() }
        val items = pages
            .flatMap { it.items }
            .filterNot { TorrentText.isAv1(it.title) }
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

    private val unavailableUntil = ConcurrentHashMap<MagnetSource, Long>()
    private val cachedPages = ConcurrentHashMap<String, CachedPage>()

    private suspend fun runProvider(
        source: MagnetSource,
        provider: MagnetSourceProvider,
        query: String,
        page: Int,
    ): Result<MagnetPage> {
        val now = System.currentTimeMillis()
        val blockedUntil = unavailableUntil[source] ?: 0L
        if (blockedUntil > now) {
            AppLog.event(
                "PROVIDER",
                "cooldown",
                fields = mapOf(
                    "provider" to source.label,
                    "remainingMs" to (blockedUntil - now),
                ),
            )
            return Result.failure(
                MagnetCatalogException(source.label + " is temporarily unavailable."),
            )
        }
        val cacheKey = source.name + "|" + query + "|" + page
        cachedPages[cacheKey]?.takeIf { it.isFresh(now) }?.let { cached ->
            AppLog.event(
                "PROVIDER",
                "cache_hit",
                fields = mapOf("provider" to source.label, "query" to query, "page" to page),
            )
            return Result.success(cached.page)
        }

        return try {
            val result = withTimeout(PROVIDER_TIMEOUT_MILLIS) { provider.browse(query, page) }
            unavailableUntil.remove(source)
            cachedPages[cacheKey] = CachedPage(result, now + CACHE_TTL_MILLIS)
            if (cachedPages.size > MAX_CACHE_ENTRIES) {
                cachedPages.entries
                    .sortedBy { it.value.expiresAt }
                    .take(cachedPages.size - MAX_CACHE_ENTRIES)
                    .forEach { cachedPages.remove(it.key) }
            }
            AppLog.event(
                "PROVIDER",
                "success",
                fields = mapOf(
                    "provider" to source.label,
                    "query" to query,
                    "page" to result.page,
                    "items" to result.items.size,
                ),
            )
            Result.success(result)
        } catch (timeout: TimeoutCancellationException) {
            unavailableUntil[source] = now + PROVIDER_COOLDOWN_MILLIS
            AppLog.warn(
                "PROVIDER",
                "timeout",
                fields = mapOf("provider" to source.label, "query" to query),
            )
            Result.failure(timeout)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            unavailableUntil[source] = now + PROVIDER_COOLDOWN_MILLIS
            AppLog.exception(
                "PROVIDER",
                "failed",
                error,
                fields = mapOf("provider" to source.label, "query" to query),
            )
            Result.failure(error)
        }
    }

    private data class CachedPage(
        val page: MagnetPage,
        val expiresAt: Long,
    ) {
        fun isFresh(now: Long): Boolean = expiresAt > now
    }

    private companion object {
        const val ALL_RESULT_LIMIT = 60
        const val PROVIDER_TIMEOUT_MILLIS = 20_000L
        const val PROVIDER_COOLDOWN_MILLIS = 2L * 60L * 1000L
        const val CACHE_TTL_MILLIS = 60L * 1000L
        const val MAX_CACHE_ENTRIES = 128
    }
}

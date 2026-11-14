package com.streamline.app.data.magnet

import com.streamline.app.core.util.await
import com.streamline.app.core.util.readTextBounded
import com.streamline.app.domain.model.MagnetPage
import com.streamline.app.domain.model.MagnetSource
import com.streamline.app.domain.model.MagnetTitle
import com.streamline.app.domain.repository.MagnetCatalogException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder

/**
 * 1337x has no public API. This provider reads the public search/popular listing, then visits the
 * first few torrent pages for their magnet link and poster. It never downloads a torrent: the
 * output of this class is metadata plus a magnet URI.
 *
 * Cloudflare gates 1337x from many networks. When that happens the provider fails with a clear,
 * user-facing message rather than pretending the index is empty.
 */
class X1337MagnetSource(
    private val client: OkHttpClient,
    private val hosts: List<String> = DEFAULT_HOSTS,
) : MagnetSourceProvider {

    override suspend fun browse(query: String, page: Int): MagnetPage = withContext(Dispatchers.IO) {
        val normalizedQuery = query.trim()

        var lastError: Throwable? = null
        var sawChallenge = false
        var sawEmptyListing = false
        for (host in hosts) {
            try {
                val listingUrl = buildListingUrl(host, normalizedQuery, page)
                val listing = fetchPage(listingUrl, host)
                val rows = X1337HtmlParser.parseListing(listing)
                if (rows.isEmpty()) {
                    sawEmptyListing = true
                    lastError = MagnetCatalogException("No 1337x results were returned by this mirror.")
                    continue
                }

                val items = loadDetails(host, rows.take(MAX_ITEMS))
                if (items.isEmpty()) {
                    lastError = MagnetCatalogException(
                        "1337x returned a listing, but no magnet link could be read from it.",
                    )
                    continue
                }
                return@withContext MagnetPage(
                    items = items,
                    page = page,
                    hasMore = rows.size >= PAGE_SIZE,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                if (error is MagnetCatalogException && error.message == BLOCKED_MESSAGE) {
                    sawChallenge = true
                }
                lastError = error
            }
        }

        if (sawEmptyListing) {
            return@withContext MagnetPage(items = emptyList(), page = page, hasMore = false)
        }
        throw if (sawChallenge) {
            MagnetCatalogException(BLOCKED_MESSAGE, lastError)
        } else {
            MagnetCatalogException(
                "1337x isn't answering right now. Check your connection, or switch to YTS.",
                lastError,
            )
        }
    }

    private suspend fun loadDetails(
        host: String,
        rows: List<X1337HtmlParser.Row>,
    ): List<MagnetTitle> = coroutineScope {
        val gate = Semaphore(DETAIL_CONCURRENCY)
        rows.map { row ->
            async {
                gate.withPermit {
                    try {
                        loadDetail(host, row)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (ignored: Throwable) {
                        null
                    }
                }
            }
        }.awaitAll().filterNotNull()
    }

    private suspend fun loadDetail(host: String, row: X1337HtmlParser.Row): MagnetTitle? {
        val html = fetchPage(host + row.path, host)
        val detail = X1337HtmlParser.parseDetail(html, host)
        val magnet = detail.magnetUrl
            ?: detail.infoHash?.let { hash ->
                runCatching { MagnetLinks.fromInfoHash(hash, row.title) }.getOrNull()
            }
            ?: return null
        val id = row.path.substringAfter("/torrent/").substringBefore("/")
        return MagnetTitle(
            id = "1337x:${id}",
            source = MagnetSource.X1337,
            title = row.title,
            magnetUrl = magnet,
            torrentUrl = detail.torrentUrl,
            year = X1337HtmlParser.extractYear(row.title),
            posterUrl = detail.posterUrl,
            quality = X1337HtmlParser.extractQuality(row.title),
            sizeLabel = row.size,
            seeds = row.seeds,
            peers = row.leeches,
        )
    }

    private suspend fun fetchPage(url: String, host: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()
        client.await(request).use { response ->
            if (response.code == 403 || response.code == 429 || response.code == 503) {
                throw MagnetCatalogException(BLOCKED_MESSAGE)
            }
            if (!response.isSuccessful) {
                throw IOException("1337x responded with HTTP ${response.code} on $host.")
            }
            val html = response.body?.readTextBounded().orEmpty()
            if (html.isBlank()) throw IOException("1337x returned an empty page.")
            if (X1337HtmlParser.isChallengePage(html)) throw MagnetCatalogException(BLOCKED_MESSAGE)
            return html
        }
    }

    private fun buildListingUrl(host: String, query: String, page: Int): String {
        val normalizedPage = page.coerceAtLeast(1)
        val path = if (query.isBlank()) {
            "/cat/Movies/$normalizedPage/"
        } else {
            "/search/${encodePath(query)}/$normalizedPage/"
        }
        return host.trimEnd('/') + path
    }

    private fun encodePath(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private companion object {
        const val PAGE_SIZE = 20
        const val MAX_ITEMS = 20
        const val DETAIL_CONCURRENCY = 4
        const val BLOCKED_MESSAGE =
            "1337x is blocking automated requests from this network. Switch to YTS, or open 1337x in your browser."

        val DEFAULT_HOSTS = listOf(
            "https://1337x.to",
            "https://1337x.st",
            "https://x1337x.ws",
            "https://www.1377x.to",
            "https://www.1337xx.to",
        )
    }
}

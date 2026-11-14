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

/** Kickass search results need a detail-page visit for the magnet and poster. */
class KickassMagnetSource(
    private val client: OkHttpClient,
) : MagnetSourceProvider {

    override suspend fun browse(query: String, page: Int): MagnetPage = withContext(Dispatchers.IO) {
        if (query.isBlank() || page > 1) {
            return@withContext MagnetPage(emptyList(), page, false)
        }
        try {
            val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8").replace("+", "%20")
            val listing = fetchPage("https://kickasstorrents.to/search/$encodedQuery/category/movies/")
            val rows = KickassParser.parseListing(listing)
            val items = loadDetails("https://kickasstorrents.to", rows.take(MAX_ITEMS))
            if (rows.isNotEmpty() && items.isEmpty()) {
                throw MagnetCatalogException(
                    "Kickass returned results, but no magnet could be read from their detail pages.",
                )
            }
            MagnetPage(
                items = items,
                page = 1,
                hasMore = false,
                totalResults = items.size,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: MagnetCatalogException) {
            throw error
        } catch (error: Throwable) {
            throw MagnetCatalogException(
                "Kickass isn't answering right now. Try another source.",
                error,
            )
        }
    }

    private suspend fun loadDetails(
        host: String,
        rows: List<KickassParser.Row>,
    ): List<MagnetTitle> = coroutineScope {
        val gate = Semaphore(DETAIL_CONCURRENCY)
        rows.map { row ->
            async {
                gate.withPermit {
                    try {
                        loadDetail(host, row)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Throwable) {
                        null
                    }
                }
            }
        }.awaitAll().filterNotNull()
    }

    private suspend fun loadDetail(host: String, row: KickassParser.Row): MagnetTitle? {
        val html = fetchPage(host + row.path)
        val detail = KickassParser.parseDetail(html, host)
        val magnet = detail.magnetUrl ?: return null
        return MagnetTitle(
            id = "kickass:${row.path.substringAfter('/').substringBeforeLast(".html")}",
            source = MagnetSource.KICKASS,
            title = TorrentText.cleanTitle(row.title),
            magnetUrl = magnet,
            torrentUrl = detail.torrentUrl,
            year = TorrentText.year(row.title),
            posterUrl = detail.posterUrl,
            quality = TorrentText.quality(row.title),
            sizeLabel = row.size,
            seeds = row.seeds,
            peers = row.peers,
            genres = listOf("Movies"),
        )
    }

    private suspend fun fetchPage(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()
        client.await(request).use { response ->
            if (!response.isSuccessful) {
                throw IOException("Kickass responded with HTTP ${response.code}.")
            }
            val html = response.body?.readTextBounded().orEmpty()
            if (html.isBlank()) throw IOException("Kickass returned an empty page.")
            return html
        }
    }

    private companion object {
        const val MAX_ITEMS = 8
        const val DETAIL_CONCURRENCY = 4
    }
}

object KickassParser {
    data class Row(
        val path: String,
        val title: String,
        val size: String?,
        val seeds: Int?,
        val peers: Int?,
    )

    data class Detail(
        val magnetUrl: String?,
        val posterUrl: String?,
        val torrentUrl: String?,
    )

    private val ROW = Regex("""(?is)<tr[^>]*>(.*?)</tr>""")
    private val TITLE_ANCHOR = Regex(
        """(?is)<a[^>]*class=["'][^"']*cellMainLink[^"']*["'][^>]*>(.*?)</a>""",
    )
    private val HREF = Regex("""(?is)href=["']([^"']+)["']""")
    private val SIZE = Regex(
        """(?is)<td[^>]*class=["'][^"']*nobr[^"']*["'][^>]*>\s*([^<]+)</td>""",
    )
    private val SEEDS = Regex(
        """(?is)<td[^>]*class=["'][^"']*green[^"']*["'][^>]*>\s*(\d+)""",
    )
    private val PEERS = Regex(
        """(?is)<td[^>]*class=["'][^"']*red[^"']*lasttd[^"']*["'][^>]*>\s*(\d+)""",
    )
    private val MAGNET = Regex("""(?is)href=["'](magnet:\?[^"']+)["']""")
    private val TORRENT = Regex("""(?is)href=["']([^"']+\.torrent[^"']*)["']""")
    private val POSTER = Regex("""(?is)<img[^>]+src=["']([^"']*?/static/movie/[^"']+)["']""")

    fun parseListing(html: String): List<Row> = ROW.findAll(html)
        .mapNotNull { match -> parseRow(match.groupValues[1]) }
        .distinctBy { it.path }
        .toList()

    fun parseDetail(html: String, host: String): Detail {
        val magnet = MAGNET.find(html)
            ?.groupValues
            ?.get(1)
            ?.let(HtmlText::decodeEntities)
            ?.takeIf { it.startsWith("magnet:", ignoreCase = true) }
        val poster = POSTER.find(html)
            ?.groupValues
            ?.get(1)
            ?.let { normalizeUrl(it, host) }
        val torrentUrl = TORRENT.find(html)
            ?.groupValues
            ?.get(1)
            ?.let(HtmlText::decodeEntities)
            ?.let { normalizeUrl(it, host) }
        return Detail(magnetUrl = magnet, posterUrl = poster, torrentUrl = torrentUrl)
    }

    private fun parseRow(row: String): Row? {
        val anchor = TITLE_ANCHOR.find(row) ?: return null
        val href = HREF.find(anchor.value)?.groupValues?.get(1) ?: return null
        val title = HtmlText.clean(anchor.groupValues[1])
        if (!href.startsWith("/") || title.isBlank()) return null
        return Row(
            path = href,
            title = title,
            size = SIZE.find(row)?.groupValues?.get(1)?.let(HtmlText::clean)?.takeIf { it.isNotBlank() },
            seeds = SEEDS.find(row)?.groupValues?.get(1)?.toIntOrNull(),
            peers = PEERS.find(row)?.groupValues?.get(1)?.toIntOrNull(),
        )
    }

    private fun normalizeUrl(raw: String, host: String): String? {
        val value = raw.trim().takeIf { it.isNotEmpty() } ?: return null
        return when {
            value.startsWith("//") -> "https:$value"
            value.startsWith("https://", ignoreCase = true) -> value
            value.startsWith("http://", ignoreCase = true) ->
                "https://" + value.substring("http://".length)
            value.startsWith("/") -> host.trimEnd('/') + value
            else -> host.trimEnd('/') + "/" + value
        }
    }
}

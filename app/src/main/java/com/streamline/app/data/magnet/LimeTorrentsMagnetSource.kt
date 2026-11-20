package com.streamline.app.data.magnet

import com.streamline.app.core.util.await
import com.streamline.app.core.util.readTextBounded
import com.streamline.app.domain.model.MagnetPage
import com.streamline.app.domain.model.MagnetSource
import com.streamline.app.domain.model.MagnetTitle
import com.streamline.app.domain.repository.MagnetCatalogException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import java.util.Locale





class LimeTorrentsMagnetSource(
    private val client: OkHttpClient,
) : MagnetSourceProvider {

    override suspend fun browse(query: String, page: Int): MagnetPage = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext MagnetPage(emptyList(), page, false)
        val normalizedPage = page.coerceAtLeast(1)
        try {
            val encoded = URLEncoder.encode(query.trim(), "UTF-8").replace("+", "%20")
            val request = Request.Builder()
                .url("https://www.limetorrents.lol/search/all/" + encoded +
                    "/seeds/" + normalizedPage + "/")
                .header("Accept", "text/html,application/xhtml+xml")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()
            client.await(request).use { response ->
                if (!response.isSuccessful) {
                    throw IOException("LimeTorrents responded with HTTP " + response.code + ".")
                }
                val html = response.body?.readTextBounded().orEmpty()
                if (html.isBlank() || !html.contains("table2", ignoreCase = true)) {
                    throw IOException("LimeTorrents returned an unreadable page.")
                }
                LimeTorrentsParser.parse(html, normalizedPage)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: MagnetCatalogException) {
            throw error
        } catch (error: Throwable) {
            throw MagnetCatalogException(
                "LimeTorrents isn't answering right now. Try another source.",
                error,
            )
        }
    }
}

object LimeTorrentsParser {
    private val ROW = Regex("""(?is)<tr[^>]*>(.*?)</tr>""")
    private val DETAIL = Regex(
        """(?is)<a[^>]+href=["']([^"']*-torrent-\d+\.html)["'][^>]*>(.*?)</a>""",
    )
    private val HASH = Regex("""(?i)itorrents\.net/torrent/([a-f0-9]{40})\.torrent""")
    private val CELL = Regex("""(?is)<td[^>]*>(.*?)</td>""")
    private val SIZE = Regex("""(?i)\b\d+(?:\.\d+)?\s*(?:KB|MB|GB|TB)\b""")
    private val INTEGER = Regex("""\d+""")

    fun parse(html: String, page: Int): MagnetPage {
        val items = ROW.findAll(html)
            .mapNotNull { match -> parseRow(match.groupValues[1]) }
            .distinctBy { it.id }
            .take(MAX_ITEMS)
            .toList()
        return MagnetPage(
            items = items,
            page = page,
            hasMore = items.size >= 20,
            totalResults = items.size,
        )
    }

    private fun parseRow(row: String): MagnetTitle? {
        val hash = HASH.find(row)?.groupValues?.get(1) ?: return null
        val detail = DETAIL.find(row) ?: return null
        val title = HtmlText.clean(detail.groupValues[2])
        if (title.isBlank()) return null
        val cells = CELL.findAll(row)
            .map { HtmlText.clean(it.groupValues[1]) }
            .toList()
        val category = cells.getOrNull(1).orEmpty()
        if (category.isNotBlank() && !TorrentText.isVideoCategory(category)) return null
        val size = cells.firstOrNull { SIZE.containsMatchIn(it) }
        val seeds = cells.getOrNull(3)?.let { INTEGER.find(it)?.value?.toIntOrNull() }
        val peers = cells.getOrNull(4)?.let { INTEGER.find(it)?.value?.toIntOrNull() }
        return MagnetTitle(
            id = "lime:" + hash.lowercase(Locale.US),
            source = MagnetSource.LIME,
            title = TorrentText.cleanTitle(title),
            magnetUrl = MagnetLinks.fromInfoHash(hash, title),
            year = TorrentText.year(title),
            quality = TorrentText.quality(title),
            sizeLabel = size,
            seeds = seeds,
            peers = peers,
            genres = listOf(TorrentText.genreFor(category)),
        )
    }

    private const val MAX_ITEMS = 40
}

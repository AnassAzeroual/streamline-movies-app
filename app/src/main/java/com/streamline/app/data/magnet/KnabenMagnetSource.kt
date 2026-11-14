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
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import java.util.Locale

/**
 * Knaben is a public search engine over several torrent trackers. Its result table links the
 * magnet directly, so no second detail-page fetch is needed.
 */
class KnabenMagnetSource(
    private val client: OkHttpClient,
) : MagnetSourceProvider {

    override suspend fun browse(query: String, page: Int): MagnetPage = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext MagnetPage(emptyList(), page, false)
        try {
            val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8").replace("+", "%20")
            val normalizedPage = page.coerceAtLeast(1)
            val url = "https://knaben.org/search/$encodedQuery/0/$normalizedPage/seeders?hideXXX"
                .toHttpUrl()
            val request = Request.Builder()
                .url(url)
                .header("Accept", "text/html,application/xhtml+xml")
                .build()
            client.await(request).use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Knaben responded with HTTP ${response.code}.")
                }
                val html = response.body?.readTextBounded().orEmpty()
                if (html.isBlank() || !html.trimStart().startsWith("<")) {
                    throw IOException("Knaben returned an unreadable response.")
                }
                KnabenParser.parse(html, normalizedPage)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: MagnetCatalogException) {
            throw error
        } catch (error: Throwable) {
            throw MagnetCatalogException(
                "Knaben isn't answering right now. Try another source.",
                error,
            )
        }
    }
}

object KnabenParser {
    private val ROW = Regex("""(?is)<tr[^>]*data-id=["'][^"']+["'][^>]*>(.*?)</tr>""")
    private val MAGNET_ANCHOR = Regex("""(?is)<a[^>]+href=["'](magnet:\?[^"']+)["'][^>]*>(.*?)</a>""")
    private val TITLE_ATTRIBUTE = Regex("""(?is)<a[^>]+title=["']([^"']+)["'][^>]+href=["']magnet:""")
    private val SIZE = Regex("""(?is)<td[^>]+title=["'][^"']*Bytes["'][^>]*>\s*([^<]+)</td>""")
    private val SIZE_FALLBACK = Regex("""(?is)<td[^>]*>\s*(\d+(?:\.\d+)?\s*(?:KB|MB|GB|TB))\s*</td>""")
    private val STAT = Regex("""(?is)<td[^>]*style=["'][^"']*color[^"']*["'][^>]*>\s*(\d+)\s*</td>""")
    private val CATEGORY = Regex("""(?is)<td[^>]*class=["'][^"']*d-last-child[^"']*["'][^>]*>(.*?)</td>""")
    private val HASH = Regex("""(?i)btih:([a-z0-9]+)""")

    fun parse(html: String, page: Int): MagnetPage {
        val items = ROW.findAll(html)
            .mapNotNull { match -> parseRow(match.groupValues[1]) }
            .distinctBy { it.id }
            .take(60)
            .toList()

        return MagnetPage(
            items = items,
            page = page,
            hasMore = items.size >= 20,
        )
    }

    private fun parseRow(row: String): MagnetTitle? {
        val magnetAnchor = MAGNET_ANCHOR.find(row) ?: return null
        val magnet = HtmlText.decodeEntities(magnetAnchor.groupValues[1])
        val hash = HASH.find(magnet)?.groupValues?.get(1) ?: return null
        val attributeTitle = TITLE_ATTRIBUTE.find(row)
            ?.groupValues
            ?.get(1)
            ?.let(HtmlText::decodeEntities)
        val anchorTitle = HtmlText.clean(magnetAnchor.groupValues[2])
        val title = (attributeTitle ?: anchorTitle).takeIf { it.isNotBlank() } ?: return null
        val stats = STAT.findAll(row)
            .mapNotNull { it.groupValues[1].toIntOrNull() }
            .toList()
        val category = CATEGORY.find(row)
            ?.groupValues
            ?.get(1)
            ?.let(HtmlText::clean)
        if (!category.isNullOrBlank() && !TorrentText.isVideoCategory(category)) return null
        val size = SIZE.find(row)?.groupValues?.get(1)
            ?: SIZE_FALLBACK.find(row)?.groupValues?.get(1)

        return MagnetTitle(
            id = "knaben:${hash.uppercase(Locale.US)}",
            source = MagnetSource.KNABEN,
            title = TorrentText.cleanTitle(title),
            magnetUrl = magnet,
            year = TorrentText.year(title),
            quality = TorrentText.quality(title),
            sizeLabel = size?.trim()?.let(HtmlText::decodeEntities),
            seeds = stats.getOrNull(0),
            peers = stats.getOrNull(1),
            genres = listOf(TorrentText.genreFor(category)),
        )
    }
}

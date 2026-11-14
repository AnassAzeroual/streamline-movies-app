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
import java.util.Locale

/** BTDigg's DHT search returns magnets directly in each result block. */
class BtdigMagnetSource(
    private val client: OkHttpClient,
) : MagnetSourceProvider {

    override suspend fun browse(query: String, page: Int): MagnetPage = withContext(Dispatchers.IO) {
        if (query.isBlank() || page > 1) {
            return@withContext MagnetPage(emptyList(), page, false)
        }
        try {
            val url = "https://btdig.com/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", query.trim())
                .build()
            val request = Request.Builder()
                .url(url)
                .header("Accept", "text/html,application/xhtml+xml")
                .build()
            client.await(request).use { response ->
                if (!response.isSuccessful) {
                    throw IOException("BTDigg responded with HTTP ${response.code}.")
                }
                val html = response.body?.readTextBounded().orEmpty()
                if (html.isBlank() || !html.trimStart().startsWith("<")) {
                    throw IOException("BTDigg returned an unreadable response.")
                }
                BtdigParser.parse(html)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: MagnetCatalogException) {
            throw error
        } catch (error: Throwable) {
            throw MagnetCatalogException(
                "BTDigg isn't answering right now. Try another source.",
                error,
            )
        }
    }
}

object BtdigParser {
    private val RESULT_START = Regex("""(?is)<div[^>]*class=["'][^"']*\bone_result\b[^"']*["']""")
    private val MAGNET = Regex("""(?is)href=["'](magnet:\?[^"']+)["']""")
    private val TITLE = Regex(
        """(?is)<div[^>]*class=["'][^"']*torrent_name[^"']*["'][^>]*>.*?<a[^>]*>(.*?)</a>""",
    )
    private val SIZE = Regex(
        """(?is)<span[^>]*class=["'][^"']*torrent_size[^"']*["'][^>]*>([^<]+)</span>""",
    )
    private val HASH = Regex("""(?i)btih:([a-z0-9]+)""")

    fun parse(html: String): MagnetPage {
        val starts = RESULT_START.findAll(html).map { it.range.first }.toList()
        val items = starts.mapIndexedNotNull { index, start ->
            val end = starts.getOrNull(index + 1) ?: html.length
            parseBlock(html.substring(start, end))
        }.distinctBy { it.id }.take(30)

        return MagnetPage(
            items = items,
            page = 1,
            hasMore = false,
            totalResults = items.size,
        )
    }

    private fun parseBlock(block: String): MagnetTitle? {
        val magnet = MAGNET.find(block)
            ?.groupValues
            ?.get(1)
            ?.let(HtmlText::decodeEntities)
            ?: return null
        val hash = HASH.find(magnet)?.groupValues?.get(1) ?: return null
        val title = TITLE.find(block)
            ?.groupValues
            ?.get(1)
            ?.let(HtmlText::clean)
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val size = SIZE.find(block)?.groupValues?.get(1)?.trim()
        return MagnetTitle(
            id = "btdig:${hash.uppercase(Locale.US)}",
            source = MagnetSource.BTDIGG,
            title = TorrentText.cleanTitle(title),
            magnetUrl = magnet,
            year = TorrentText.year(title),
            quality = TorrentText.quality(title),
            sizeLabel = size,
            genres = listOf("Torrent"),
        )
    }
}

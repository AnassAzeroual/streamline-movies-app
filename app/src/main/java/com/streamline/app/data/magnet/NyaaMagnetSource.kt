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


class NyaaMagnetSource(
    private val client: OkHttpClient,
) : MagnetSourceProvider {

    override suspend fun browse(query: String, page: Int): MagnetPage = withContext(Dispatchers.IO) {
        try {
            val url = "https://nyaa.si/".toHttpUrl().newBuilder()
                .addQueryParameter("page", "rss")
                .apply {
                    if (query.isNotBlank()) addQueryParameter("q", query.trim())
                }
                .addQueryParameter("c", "0_0")
                .addQueryParameter("f", "0")
                .addQueryParameter("p", page.coerceAtLeast(1).toString())
                .build()
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/rss+xml,application/xml")
                .build()
            client.await(request).use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Nyaa responded with HTTP ${response.code}.")
                }
                val xml = response.body?.readTextBounded().orEmpty()
                if (xml.isBlank() || !xml.contains("<rss", ignoreCase = true)) {
                    throw IOException("Nyaa returned an unreadable feed.")
                }
                NyaaParser.parse(xml, page.coerceAtLeast(1))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: MagnetCatalogException) {
            throw error
        } catch (error: Throwable) {
            throw MagnetCatalogException(
                "Nyaa isn't answering right now. Try another source.",
                error,
            )
        }
    }
}

object NyaaParser {
    private val ITEM = Regex("""(?is)<item>(.*?)</item>""")

    fun parse(xml: String, page: Int): MagnetPage {
        val items = ITEM.findAll(xml)
            .mapNotNull { match -> parseItem(match.groupValues[1]) }
            .distinctBy { it.id }
            .take(50)
            .toList()

        return MagnetPage(
            items = items,
            page = page,
            hasMore = items.size >= 30 && page < 20,
        )
    }

    private fun parseItem(item: String): MagnetTitle? {
        val title = tag(item, "title")?.let(HtmlText::decodeEntities) ?: return null
        val hash = tag(item, "nyaa:infoHash") ?: return null
        val magnet = runCatching { MagnetLinks.fromInfoHash(hash, title) }.getOrNull() ?: return null
        val category = tag(item, "nyaa:category")
            ?.let(HtmlText::decodeEntities)
        val torrentUrl = TORRENT_FILE.find(item)?.value?.let(HtmlText::decodeEntities)
        return MagnetTitle(
            id = "nyaa:${hash.uppercase(Locale.US)}",
            source = MagnetSource.NYAA,
            title = TorrentText.cleanTitle(title),
            magnetUrl = magnet,
            torrentUrl = torrentUrl,
            year = TorrentText.year(title),
            quality = TorrentText.quality(title),
            sizeLabel = tag(item, "nyaa:size")?.let(HtmlText::decodeEntities),
            seeds = tag(item, "nyaa:seeders")?.toIntOrNull(),
            peers = tag(item, "nyaa:leechers")?.toIntOrNull(),
            genres = listOf(TorrentText.genreFor(category ?: "Anime")),
        )
    }

    private val TORRENT_FILE = Regex("""(?i)https?://[^<\s"']+\.torrent[^<\s"']*""")

    private fun tag(item: String, name: String): String? =
        Regex("""(?is)<$name[^>]*>(.*?)</$name>""")
            .find(item)
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?.removePrefix("<![CDATA[")
            ?.removeSuffix("]]>")
            ?.takeIf { it.isNotBlank() }
}

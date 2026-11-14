package com.streamline.app.data.magnet

import com.google.gson.JsonObject
import com.google.gson.JsonParser
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

/** Torrents-CSV is a public DHT index with a small JSON search API. */
class TorrentsCsvMagnetSource(
    private val client: OkHttpClient,
) : MagnetSourceProvider {

    override suspend fun browse(query: String, page: Int): MagnetPage = withContext(Dispatchers.IO) {
        if (query.isBlank() || page > 1) {
            return@withContext MagnetPage(emptyList(), page, false)
        }
        try {
            val url = "https://torrents-csv.com/service/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", query.trim())
                .addQueryParameter("size", "40")
                .build()
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build()
            client.await(request).use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Torrents-CSV responded with HTTP ${response.code}.")
                }
                val payload = response.body?.readTextBounded().orEmpty()
                if (payload.isBlank() || payload.trimStart().startsWith("<")) {
                    throw IOException("Torrents-CSV returned an unreadable response.")
                }
                TorrentsCsvParser.parse(payload)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: MagnetCatalogException) {
            throw error
        } catch (error: Throwable) {
            throw MagnetCatalogException(
                "Torrents-CSV isn't answering right now. Try another source.",
                error,
            )
        }
    }
}

object TorrentsCsvParser {
    fun parse(json: String): MagnetPage {
        val root = runCatching { JsonParser.parseString(json) }.getOrNull()
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject
            ?: return MagnetPage(emptyList(), 1, false)
        val torrents = root.get("torrents")
            ?.takeIf { it.isJsonArray }
            ?.asJsonArray
            ?: return MagnetPage(emptyList(), 1, false)

        val items = torrents.mapNotNull { element ->
            val torrent = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val hash = torrent.string("infohash") ?: return@mapNotNull null
            val name = torrent.string("name") ?: return@mapNotNull null
            val magnet = runCatching { MagnetLinks.fromInfoHash(hash, name) }.getOrNull()
                ?: return@mapNotNull null
            MagnetTitle(
                id = "csv:${hash.uppercase()}",
                source = MagnetSource.TORRENTS_CSV,
                title = TorrentText.cleanTitle(name),
                magnetUrl = magnet,
                year = TorrentText.year(name),
                quality = TorrentText.quality(name),
                sizeLabel = TorrentText.formatBytes(torrent.long("size_bytes")),
                seeds = torrent.int("seeders"),
                peers = torrent.int("leechers"),
                genres = listOf("Torrent"),
            )
        }.take(60)

        return MagnetPage(
            items = items,
            page = 1,
            hasMore = false,
            totalResults = items.size,
        )
    }

    private fun JsonObject.string(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive }?.asString?.trim()?.takeIf { it.isNotEmpty() }

    private fun JsonObject.int(key: String): Int? =
        get(key)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asInt }.getOrNull() }

    private fun JsonObject.long(key: String): Long? =
        get(key)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asLong }.getOrNull() }
}

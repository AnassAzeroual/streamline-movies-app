package com.streamline.app.data.magnet

import com.google.gson.JsonArray
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


class PirateBayMagnetSource(
    private val client: OkHttpClient,
) : MagnetSourceProvider {

    override suspend fun browse(query: String, page: Int): MagnetPage = withContext(Dispatchers.IO) {
        if (page > 1) return@withContext MagnetPage(emptyList(), page, false)
        try {
            val url = if (query.isBlank()) {
                "https://apibay.org/precompiled/data_top100_recent.json".toHttpUrl()
            } else {
                "https://apibay.org/q.php".toHttpUrl().newBuilder()
                    .addQueryParameter("q", query.trim())
                    .addQueryParameter("cat", "0")
                    .build()
            }
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build()
            client.await(request).use { response ->
                if (!response.isSuccessful) {
                    throw IOException("The Pirate Bay responded with HTTP ${response.code}.")
                }
                val payload = response.body?.readTextBounded().orEmpty()
                if (payload.isBlank() || payload.trimStart().startsWith("<")) {
                    throw IOException("The Pirate Bay returned an unreadable response.")
                }
                PirateBayParser.parse(payload)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: MagnetCatalogException) {
            throw error
        } catch (error: Throwable) {
            throw MagnetCatalogException(
                "The Pirate Bay isn't answering right now. Try another source.",
                error,
            )
        }
    }
}

object PirateBayParser {
    fun parse(json: String): MagnetPage {
        val array = runCatching { JsonParser.parseString(json) }.getOrNull()
            ?.takeIf { it.isJsonArray }
            ?.asJsonArray
            ?: return MagnetPage(emptyList(), 1, false)

        val items = array.mapNotNull { element -> element.toTitle() }
            .filter { it.magnetUrl.isNotBlank() }
            .take(60)

        return MagnetPage(
            items = items,
            page = 1,
            hasMore = false,
            totalResults = items.size,
        )
    }

    private fun com.google.gson.JsonElement.toTitle(): MagnetTitle? {
        val movie = takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val name = movie.string("name") ?: return null
        if (name.equals("No results returned", ignoreCase = true)) return null
        val category = movie.string("category").orEmpty()
        if (category.isNotEmpty() && !category.startsWith("2")) return null
        val hash = movie.string("info_hash") ?: return null
        val magnet = runCatching { MagnetLinks.fromInfoHash(hash, name) }.getOrNull() ?: return null
        val title = TorrentText.cleanTitle(name)
        return MagnetTitle(
            id = "tpb:${hash.uppercase()}",
            source = MagnetSource.TPB,
            title = title,
            magnetUrl = magnet,
            year = TorrentText.year(name),
            quality = TorrentText.quality(name),
            sizeLabel = TorrentText.formatBytes(movie.long("size")),
            seeds = movie.int("seeders"),
            peers = movie.int("leechers"),
            genres = listOf(if (category == "205" || category == "208") "TV" else "Movies"),
        )
    }

    private fun JsonObject.string(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive }?.asString?.trim()?.takeIf { it.isNotEmpty() }

    private fun JsonObject.int(key: String): Int? =
        get(key)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asInt }.getOrNull() }

    private fun JsonObject.long(key: String): Long? =
        get(key)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asLong }.getOrNull() }
}

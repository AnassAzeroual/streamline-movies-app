package com.streamline.app.data.magnet

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.streamline.app.domain.model.MagnetPage
import com.streamline.app.domain.model.MagnetRelease
import com.streamline.app.domain.model.MagnetSource
import com.streamline.app.domain.model.MagnetTitle
import java.util.Locale





object YtsResponseParser {
    const val PAGE_SIZE = 24
    private const val MAX_PAGE = 200

    fun parse(json: String, page: Int): MagnetPage {
        val root = runCatching { JsonParser.parseString(json) }.getOrNull()
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject
            ?: return empty(page)
        val data = root.objectOrNull("data") ?: return empty(page)
        val total = data.intOrNull("movie_count")
        val movies = data.arrayOrNull("movies")
            ?: return MagnetPage(items = emptyList(), page = page, hasMore = false, totalResults = total)

        val items = movies.mapIndexedNotNull { index, element ->
            val movie = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapIndexedNotNull null
            movie.toMagnetTitle(index)
        }
        val hasMore = items.isNotEmpty() &&
            page < MAX_PAGE &&
            (total == null || page * PAGE_SIZE < total)

        return MagnetPage(
            items = items,
            page = page,
            hasMore = hasMore,
            totalResults = total,
        )
    }

    private fun JsonObject.toMagnetTitle(index: Int): MagnetTitle? {
        val title = stringOrNull("title")
            ?: stringOrNull("title_long")
            ?: return null
        val year = intOrNull("year")
        val movieKey = stringOrNull("_id")
            ?: stringOrNull("id")
            ?: stringOrNull("imdb_code")
            ?: stringOrNull("slug")
            ?: "$title-$index"
        val poster = stringOrNull("large_cover_image")
            ?: stringOrNull("medium_cover_image")
            ?: stringOrNull("small_cover_image")
            ?: stringOrNull("background_image_original")
            ?: stringOrNull("background_image")
        val genres = arrayOrNull("genres")
            ?.mapNotNull { element ->
                element.takeIf { it.isJsonPrimitive }?.asString?.trim()
            }
            .orEmpty()

        val choices = arrayOrNull("torrents")
            ?.mapNotNull { element ->
                val torrent = element.takeIf { it.isJsonObject }?.asJsonObject
                    ?: return@mapNotNull null
                val hash = torrent.stringOrNull("hash") ?: return@mapNotNull null
                val quality = torrent.stringOrNull("quality")
                val displayName = buildString {
                    append(title)
                    if (year != null) append(" (").append(year).append(')')
                    if (!quality.isNullOrBlank()) append(" [").append(quality).append(']')
                    append(" [YTS]")
                }
                val magnet = runCatching { MagnetLinks.fromInfoHash(hash, displayName) }
                    .getOrNull()
                    ?: return@mapNotNull null
                TorrentChoice(
                    quality = quality,
                    sizeLabel = torrent.stringOrNull("size"),
                    seeds = torrent.intOrNull("seeds"),
                    peers = torrent.intOrNull("peers"),
                    magnetUrl = magnet,
                    torrentUrl = torrent.stringOrNull("url"),
                    qualityRank = qualityRank(quality),
                )
            }
            .orEmpty()
        if (choices.isEmpty()) return null

        val byQuality = choices
            .groupBy { it.quality?.lowercase(Locale.US) ?: "unknown" }
            .values
            .mapNotNull { group ->
                group.maxWithOrNull(
                    compareBy<TorrentChoice> { it.qualityRank }
                        .thenBy { it.seeds ?: 0 },
                )
            }
            .sortedWith(
                compareByDescending<TorrentChoice> { it.qualityRank }
                    .thenByDescending { it.seeds ?: 0 },
            )
        val best = byQuality.firstOrNull() ?: return null

        return MagnetTitle(
            id = "yts:$movieKey",
            source = MagnetSource.YTS,
            title = title,
            magnetUrl = best.magnetUrl,
            torrentUrl = best.torrentUrl,
            year = year,
            posterUrl = poster,
            rating = doubleOrNull("rating"),
            genres = genres,
            quality = best.quality,
            sizeLabel = best.sizeLabel,
            seeds = best.seeds,
            peers = best.peers,
            releases = byQuality.map { choice ->
                MagnetRelease(
                    magnetUrl = choice.magnetUrl,
                    torrentUrl = choice.torrentUrl,
                    quality = choice.quality,
                    sizeLabel = choice.sizeLabel,
                    seeds = choice.seeds,
                    peers = choice.peers,
                )
            },
        )
    }

    private fun qualityRank(quality: String?): Int = when (quality?.lowercase(Locale.US)) {
        "2160p", "4k", "uhd" -> 4
        "1080p" -> 3
        "720p" -> 2
        "480p", "3d" -> 1
        else -> 0
    }

    private fun empty(page: Int) = MagnetPage(
        items = emptyList(),
        page = page,
        hasMore = false,
    )

    private fun JsonObject.objectOrNull(key: String): JsonObject? =
        get(key)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.arrayOrNull(key: String): JsonArray? =
        get(key)?.takeIf { it.isJsonArray }?.asJsonArray

    private fun JsonObject.stringOrNull(key: String): String? =
        get(key)
            ?.takeIf { it.isJsonPrimitive }
            ?.asString
            ?.trim()
            ?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }

    private fun JsonObject.intOrNull(key: String): Int? =
        get(key)
            ?.takeIf { it.isJsonPrimitive }
            ?.let { runCatching { it.asInt }.getOrNull() }

    private fun JsonObject.doubleOrNull(key: String): Double? =
        get(key)
            ?.takeIf { it.isJsonPrimitive }
            ?.let { runCatching { it.asDouble }.getOrNull() }

    private data class TorrentChoice(
        val quality: String?,
        val sizeLabel: String?,
        val seeds: Int?,
        val peers: Int?,
        val magnetUrl: String,
        val torrentUrl: String?,
        val qualityRank: Int,
    )
}

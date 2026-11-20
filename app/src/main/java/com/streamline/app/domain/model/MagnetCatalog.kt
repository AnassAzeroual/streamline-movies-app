package com.streamline.app.domain.model

import java.net.URLDecoder
import java.util.Locale


enum class MagnetSource(
    val label: String,
    val tagline: String,
    val isAggregate: Boolean = false,
    val requiresQuery: Boolean = false,
    val supportsLatest: Boolean = false,
    val supportsPaging: Boolean = false,
) {
    ALL("All", "Search every index", isAggregate = true),
    YTS("YTS", "Movies with posters", supportsLatest = true, supportsPaging = true),
    X1337("1337x", "Movies with posters", supportsLatest = true, supportsPaging = true),
    TPB("Pirate Bay", "The Pirate Bay", supportsLatest = true),
    TORRENTS_CSV("CSV", "Torrents-CSV", requiresQuery = true),
    KNABEN("Knaben", "Meta search", requiresQuery = true),
    NYAA("Nyaa", "Anime", supportsLatest = true),
    BTDIGG("BTDigg", "DHT search", requiresQuery = true),
    KICKASS("Kickass", "General", requiresQuery = true),
    LIME("LimeTorrents", "Movies and TV", requiresQuery = true),
}


data class MagnetRelease(
    val magnetUrl: String,
    val torrentUrl: String? = null,
    val quality: String? = null,
    val sizeLabel: String? = null,
    val seeds: Int? = null,
    val peers: Int? = null,
) {
    val videoQuality: VideoQuality
        get() = VideoQuality.from(quality)

    val displayLabel: String
        get() = videoQuality.displayLabel

    val displayName: String?
        get() {
            val raw = magnetUrl.substringAfter("dn=", "")
            if (raw.isBlank()) return null
            return runCatching {
                URLDecoder.decode(raw.substringBefore('&'), "UTF-8")
            }.getOrNull()?.takeIf { it.isNotBlank() }
        }

    val isMatroska: Boolean
        get() = displayName?.endsWith(".mkv", ignoreCase = true) == true

    val containerLabel: String?
        get() = when {
            displayName?.endsWith(".mkv", ignoreCase = true) == true -> "MKV"
            displayName?.endsWith(".mp4", ignoreCase = true) == true -> "MP4"
            else -> null
        }
}





data class MagnetTitle(
    val id: String,
    val source: MagnetSource,
    val title: String,
    val magnetUrl: String,
    val torrentUrl: String? = null,
    val year: Int? = null,
    val posterUrl: String? = null,
    val rating: Double? = null,
    val genres: List<String> = emptyList(),
    val quality: String? = null,
    val sizeLabel: String? = null,
    val seeds: Int? = null,
    val peers: Int? = null,
    val releases: List<MagnetRelease> = emptyList(),
) {
    val ratingLabel: String
        get() = rating
            ?.takeIf { it > 0.0 }
            ?.let { String.format(Locale.US, "%.1f", it) }
            ?: "NR"

    val metaLine: String
        get() = listOfNotNull(year?.toString(), quality, sizeLabel)
            .joinToString(" · ")
            .ifBlank { "Magnet link" }

    val genreLabel: String
        get() = genres.firstOrNull() ?: "Feature film"

    val isAdult: Boolean
        get() {
            val categories = genres + genreLabel
            if (categories.any { field ->
                    ADULT_CATEGORY_TOKENS.any { field.contains(it, ignoreCase = true) }
                }
            ) {
                return true
            }
            return ADULT_TITLE_PATTERN.containsMatchIn(title)
        }

    fun playableReleases(): List<MagnetRelease> = releases.ifEmpty {
        listOf(
            MagnetRelease(
                magnetUrl = magnetUrl,
                torrentUrl = torrentUrl,
                quality = quality,
                sizeLabel = sizeLabel,
                seeds = seeds,
                peers = peers,
            ),
        )
    }

    private companion object {
        val ADULT_CATEGORY_TOKENS = listOf("adult", "xxx", "porn", "hentai", "erotic")
        val ADULT_TITLE_PATTERN = Regex(
            "(?i)(\\bxxx\\b|\\bporn\\w*\\b|\\bhentai\\b|\\berotic\\b|" +
                "\\bonlyfans\\b|\\bbrazzers\\b|\\bmilf\\b|\\bnsfw\\b|" +
                "\\bsex tape\\b|\\bsex video\\b|18\\+|\\bjav\\b)",
        )
    }

    fun playbackRequest(release: MagnetRelease? = null): PlaybackRequest {
        val chosen = release ?: playableReleases().firstOrNull()
        return PlaybackRequest(
            id = id,
            title = title,
            magnetUrl = chosen?.magnetUrl ?: magnetUrl,
            torrentUrl = chosen?.torrentUrl ?: torrentUrl,
            posterUrl = posterUrl,
            sourceLabel = source.label,
            quality = chosen?.quality,
            alternatives = playableReleases()
                .filter { it.magnetUrl != chosen?.magnetUrl }
                .map { alternative ->
                    PlaybackAlternative(
                        magnetUrl = alternative.magnetUrl,
                        torrentUrl = alternative.torrentUrl,
                        quality = alternative.quality,
                        sourceLabel = source.label,
                    )
                },
        )
    }
}

data class MagnetPage(
    val items: List<MagnetTitle>,
    val page: Int,
    val hasMore: Boolean,
    val totalResults: Int? = null,
    val warnings: List<String> = emptyList(),
)

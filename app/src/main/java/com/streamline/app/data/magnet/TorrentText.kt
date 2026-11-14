package com.streamline.app.data.magnet

import java.util.Locale

/** Title and size helpers shared by the non-YTS providers. */
object TorrentText {
    private val YEAR = Regex("""\b(19\d{2}|20\d{2})\b""")
    private val QUALITY = Regex("""(?i)\b(2160p|1080p|720p|480p|4k|uhd)\b""")
    private val WHITESPACE = Regex("""\s+""")
    private val FILE_EXTENSION = Regex("""(?i)\.(torrent|mkv|mp4|avi|mov)$""")
    private val VIDEO_TOKENS = listOf(
        "webrip", "web-dl", "webdl", "bluray", "blu-ray", "brrip", "bdrip",
        "hdtv", "x264", "x265", "hevc", "h264", "remux", "dvdrip", "hdrip", "uhd",
    )
    private val SOFTWARE_TOKENS = listOf(
        "activator", "crack", "keygen", "preactivated", "pre-activated",
        "downloader", "youtube to", "converter", "x64", "x86", "win64", "windows",
        "macos", "adobe", "premiere", "photoshop", "microsoft office", "utorrent",
        "vst", "plugin", "installer", "portable",
    )

    fun cleanTitle(raw: String): String = FILE_EXTENSION.replace(raw.trim(), "")
        .replace('.', ' ')
        .replace('_', ' ')
        .let { WHITESPACE.replace(it, " ") }
        .trim()

    fun year(raw: String): Int? = YEAR.findAll(raw).lastOrNull()?.value?.toIntOrNull()

    fun quality(raw: String): String? {
        val matches = QUALITY.findAll(raw).map { it.value.lowercase(Locale.US) }.toList()
        return when {
            matches.any { it == "2160p" || it == "4k" || it == "uhd" } -> "2160p"
            "1080p" in matches -> "1080p"
            "720p" in matches -> "720p"
            "480p" in matches -> "480p"
            else -> null
        }
    }

    fun formatBytes(bytes: Long?): String? {
        if (bytes == null || bytes <= 0L) return null
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format(Locale.US, "%.0f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024.0) return String.format(Locale.US, "%.1f MB", mb)
        val gb = mb / 1024.0
        return if (gb < 1024.0) {
            String.format(Locale.US, "%.2f GB", gb)
        } else {
            String.format(Locale.US, "%.2f TB", gb / 1024.0)
        }
    }

    fun hashFromMagnet(magnet: String): String? =
        Regex("""(?i)btih:([a-z0-9]+)""")
            .find(magnet)
            ?.groupValues
            ?.get(1)
            ?.uppercase(Locale.US)

    fun isLikelyVideoTitle(title: String): Boolean {
        val value = title.lowercase(Locale.US)
        if (SOFTWARE_TOKENS.any { value.contains(it) }) return false
        return quality(title) != null || VIDEO_TOKENS.any { value.contains(it) }
    }

    fun isVideoCategory(category: String?): Boolean {
        val value = category.orEmpty()
        return listOf(
            "movie", "film", "tv", "television", "anime", "documentary", "video", "adult",
        ).any { value.contains(it, ignoreCase = true) }
    }

    fun genreFor(category: String?): String {
        val value = category.orEmpty()
        return when {
            value.contains("adult", ignoreCase = true) ||
                value.contains("xxx", ignoreCase = true) ||
                value.contains("porn", ignoreCase = true) ||
                value.contains("hentai", ignoreCase = true) -> "Adult"
            value.contains("anime", ignoreCase = true) -> "Anime"
            value.contains("tv", ignoreCase = true) ||
                value.contains("television", ignoreCase = true) -> "TV"
            value.contains("movie", ignoreCase = true) ||
                value.contains("film", ignoreCase = true) -> "Movies"
            else -> "Torrent"
        }
    }
}

package com.streamline.app.data.magnet

import java.util.Locale






object X1337HtmlParser {
    data class Row(
        val path: String,
        val title: String,
        val seeds: Int?,
        val leeches: Int?,
        val size: String?,
    )

    data class Detail(
        val magnetUrl: String?,
        val infoHash: String?,
        val posterUrl: String?,
        val torrentUrl: String?,
    )

    private val TABLE = Regex(
        """(?is)<table[^>]*class=["'][^"']*\btable-list\b[^"']*["'][^>]*>(.*?)</table>""",
    )
    private val ROW = Regex("""(?is)<tr[^>]*>(.*?)</tr>""")
    private val TORRENT_LINK = Regex(
        """(?is)<a[^>]+href=["'](/torrent/\d+/[^"']+)["'][^>]*>(.*?)</a>""",
    )
    private val SEEDS = Regex(
        """(?is)class=["'][^"']*\bcoll-2\b[^"']*["'][^>]*>\s*([\d,]+)""",
    )
    private val LEECHES = Regex(
        """(?is)class=["'][^"']*\bcoll-3\b[^"']*["'][^>]*>\s*([\d,]+)""",
    )
    private val SIZE = Regex(
        """(?is)class=["'][^"']*\bcoll-4\b[^"']*["'][^>]*>\s*([^<]+)""",
    )
    private val MAGNET_HREF = Regex("""(?is)href=["'](magnet:\?[^"']+)["']""")
    private val TORRENT_FILE = Regex("""(?is)href=["']([^"']+\.torrent[^"']*)["']""")
    private val MAGNET_ANY = Regex("""(?is)magnet:\?xt=urn:btih:[^"'\s<]+""")
    private val INFOHASH_CONTAINER = Regex(
        """(?is)<(?:div|p|span)[^>]*class=["'][^"']*infohash[^"']*["'][^>]*>(.*?)</(?:div|p|span)>""",
    )
    private val INFOHASH_NEARBY = Regex("""(?is)infohash.{0,240}?([a-f0-9]{40})""")
    private val HASH = Regex("""[a-fA-F0-9]{40}""")
    private val TORRENT_IMAGE = Regex(
        """(?is)<div[^>]*class=["'][^"']*torrent-image[^"']*["'][^>]*>(.*?)</div>""",
    )
    private val IMAGE_SRC = Regex("""(?is)<img[^>]+src=["']([^"']+)["']""")
    private val OG_IMAGE = Regex("""(?is)<meta[^>]+property=["']og:image["'][^>]*>""")
    private val META_CONTENT = Regex("""(?is)content=["']([^"']+)["']""")
    private val TAG = Regex("""(?s)<[^>]+>""")
    private val WHITESPACE = Regex("""\s+""")
    private val NUMERIC_ENTITY = Regex("""&#(x?)([0-9a-fA-F]+);""")
    private val YEAR = Regex("""\b(19\d{2}|20\d{2})\b""")
    private val QUALITY = Regex("""(?i)\b(2160p|1080p|720p|480p|4k|uhd)\b""")

    fun parseListing(html: String): List<Row> {
        val table = TABLE.find(html)?.groupValues?.get(1) ?: return emptyList()
        return ROW.findAll(table)
            .mapNotNull { match -> parseRow(match.groupValues[1]) }
            .distinctBy { it.path }
            .toList()
    }

    fun parseDetail(html: String, host: String): Detail {
        val magnet = (MAGNET_HREF.find(html)?.groupValues?.get(1) ?: MAGNET_ANY.find(html)?.value)
            ?.let(::decodeEntities)
            ?.trim()
            ?.takeIf { it.startsWith("magnet:", ignoreCase = true) }
        val infoHash = INFOHASH_CONTAINER.find(html)
            ?.let { container -> HASH.find(container.groupValues[1])?.value }
            ?: INFOHASH_NEARBY.find(html)?.groupValues?.get(1)
        val poster = TORRENT_IMAGE.find(html)
            ?.let { imageBlock -> IMAGE_SRC.find(imageBlock.groupValues[1])?.groupValues?.get(1) }
            ?: OG_IMAGE.find(html)?.value?.let { tag ->
                META_CONTENT.find(tag)?.groupValues?.get(1)
            }
        val torrentUrl = TORRENT_FILE.find(html)
            ?.groupValues
            ?.get(1)
            ?.let(::decodeEntities)
            ?.let { normalizeUrl(it, host) }

        return Detail(
            magnetUrl = magnet,
            infoHash = infoHash,
            posterUrl = normalizeUrl(poster, host),
            torrentUrl = torrentUrl,
        )
    }

    fun isChallengePage(html: String): Boolean = CHALLENGE_MARKERS.any { html.contains(it, ignoreCase = true) }

    fun extractYear(title: String): Int? = YEAR.findAll(title).lastOrNull()?.value?.toIntOrNull()

    fun extractQuality(title: String): String? {
        val matches = QUALITY.findAll(title).map { it.value.lowercase(Locale.US) }.toList()
        return when {
            matches.any { it == "2160p" || it == "4k" || it == "uhd" } -> "2160p"
            "1080p" in matches -> "1080p"
            "720p" in matches -> "720p"
            "480p" in matches -> "480p"
            else -> null
        }
    }

    fun decodeEntities(input: String): String {
        if (input.isBlank()) return input
        var value = input
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
        value = NUMERIC_ENTITY.replace(value) { match ->
            val hexadecimal = match.groupValues[1].isNotEmpty()
            val code = match.groupValues[2].toIntOrNull(if (hexadecimal) 16 else 10)
            code
                ?.takeIf { it in 1..0x10FFFF }
                ?.let { String(Character.toChars(it)) }
                ?: match.value
        }
        return value
    }

    private fun parseRow(rowHtml: String): Row? {
        val titleLink = TORRENT_LINK.findAll(rowHtml)
            .map { match ->
                val path = decodeEntities(match.groupValues[1])
                val title = clean(match.groupValues[2])
                path to title
            }
            .filter { (path, title) -> path.isNotBlank() && title.isNotBlank() }
            .maxByOrNull { (path, title) -> title.length + path.length }
            ?: return null
        val (path, title) = titleLink
        return Row(
            path = path,
            title = title,
            seeds = SEEDS.find(rowHtml)?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull(),
            leeches = LEECHES.find(rowHtml)?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull(),
            size = SIZE.find(rowHtml)?.groupValues?.get(1)?.let(::clean)?.takeIf { it.isNotBlank() },
        )
    }

    private fun clean(html: String): String =
        decodeEntities(TAG.replace(html, " "))
            .let { WHITESPACE.replace(it, " ") }
            .trim()

    private fun normalizeUrl(raw: String?, host: String): String? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val absolute = when {
            value.startsWith("//") -> "https:$value"
            value.startsWith("http://", ignoreCase = true) ->
                "https://" + value.substring("http://".length)
            value.startsWith("https://", ignoreCase = true) -> value
            value.startsWith("/") -> host.trimEnd('/') + value
            else -> host.trimEnd('/') + "/" + value
        }
        return absolute.takeIf { it.startsWith("https://", ignoreCase = true) }
    }

    private val CHALLENGE_MARKERS = listOf(
        "Just a moment",
        "cf-chl",
        "Enable JavaScript and cookies",
        "security verification",
        "window.location.replace",
    )
}

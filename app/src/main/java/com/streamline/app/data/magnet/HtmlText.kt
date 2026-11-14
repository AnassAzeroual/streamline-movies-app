package com.streamline.app.data.magnet

/** Shared HTML cleanup for the lightweight torrent-index scrapers. */
object HtmlText {
    private val TAG = Regex("""(?s)<[^>]+>""")
    private val WHITESPACE = Regex("""\s+""")
    private val NUMERIC_ENTITY = Regex("""&#(x?)([0-9a-fA-F]+);""")

    fun clean(html: String): String =
        decodeEntities(TAG.replace(html, " "))
            .let { WHITESPACE.replace(it, " ") }
            .trim()

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
}

package com.streamline.app.data.magnet

import java.net.URLEncoder
import java.util.Locale

/** Builds the paste-ready magnet URI shown by the archive. No tracker is contacted here. */
object MagnetLinks {
    private val INFO_HASH = Regex("^[A-F0-9]{40}$")

    private val TRACKERS = listOf(
        "udp://tracker.opentrackr.org:1337/announce",
        "udp://open.stealth.si:80/announce",
        "udp://tracker.torrent.eu.org:451/announce",
        "udp://exodus.desync.com:6969/announce",
        "udp://tracker.openbittorrent.com:6969/announce",
    )

    fun fromInfoHash(hash: String, displayName: String): String {
        val normalized = hash
            .trim()
            .removePrefix("urn:btih:")
            .removePrefix("URN:BTIH:")
            .uppercase(Locale.US)
        require(INFO_HASH.matches(normalized)) { "Not a 40-character BitTorrent info hash." }
        val name = encode(displayName.ifBlank { "Streamline magnet" })
        return buildString {
            append("magnet:?xt=urn:btih:")
            append(normalized)
            append("&dn=")
            append(name)
            TRACKERS.forEach { tracker ->
                append("&tr=")
                append(encode(tracker))
            }
        }
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}

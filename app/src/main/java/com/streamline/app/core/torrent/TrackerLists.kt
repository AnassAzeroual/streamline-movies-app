package com.streamline.app.core.torrent

import org.libtorrent4j.AnnounceEntry
import org.libtorrent4j.TorrentHandle
import java.net.URLEncoder

/**
 * Shared peer-discovery configuration for both libtorrent sessions (streaming + downloads).
 *
 * Web seeds embedded in torrent metadata are used automatically: since libtorrent 2.x they live
 * in the torrent info dictionary and are picked up when the torrent is added, so no explicit
 * `addWebSeed` call is needed (the libtorrent4j wrapper does not expose one either).
 */
object TrackerLists {

    /** Curated from the well-known public tracker lists; UDP first, HTTPS fallbacks last. */
    val PUBLIC_TRACKERS: List<String> = listOf(
        "udp://tracker.opentrackr.org:1337/announce",
        "udp://open.stealth.si:80/announce",
        "udp://tracker.torrent.eu.org:451/announce",
        "udp://exodus.desync.com:6969/announce",
        "udp://tracker.openbittorrent.com:6969/announce",
        "udp://open.demonii.com:1337/announce",
        "udp://tracker.dler.org:6969/announce",
        "udp://tracker.theoks.net:6969/announce",
        "udp://tracker-udp.gbitt.info:80/announce",
        "udp://opentracker.io:2710/announce",
        "udp://open.tracker.cl:1337/announce",
        "udp://tracker.bittor.pw:1337/announce",
        "udp://tracker.dump.cl:6969/announce",
        "udp://tracker.monitorit4.me:6969/announce",
        "udp://tracker.qu.ax:6969/announce",
        "udp://tracker.tiny-vps.com:6969/announce",
        "udp://tracker.army:6969/announce",
        "udp://tracker.fatkhoala.org:13710/announce",
        "udp://tracker.moeking.me:6969/announce",
        "udp://tracker.tallpenguin.org:15720/announce",
        "udp://tracker.therarbg.com:6969/announce",
        "udp://tracker.tryhackx.org:6969/announce",
        "udp://tracker.yemekyedim.com:1337/announce",
        "udp://tracker1.itzmx.com:8080/announce",
        "udp://tracker2.dler.org:80/announce",
        "udp://tracker2.itzmx.com:6961/announce",
        "udp://tracker3.itzmx.com:6961/announce",
        "udp://tracker4.itzmx.com:2710/announce",
        "udp://tracker6.itzmx.com:6961/announce",
        "udp://vps02.net.orel.ru:80/announce",
        "udp://wepzone.net:6969/announce",
        "udp://explodie.org:6969/announce",
        "udp://movies.zsw.ca:6969/announce",
        "udp://ttk2.nbaonlineservice.com:6969/announce",
        "udp://tracker.filemail.com:6969/announce",
        "udp://tracker.edkj.club:6969/announce",
        "udp://public.pobify.com:6969/announce",
        "udp://bt1.archive.org:6969/announce",
        "udp://bt2.archive.org:6969/announce",
        "https://tracker.tamersunion.org:443/announce",
        "https://tracker.yemekyedim.com:443/announce",
        "https://tracker.lilithraws.org:443/announce",
    )

    const val DHT_BOOTSTRAP_NODES =
        "router.bittorrent.com:6881,router.utorrent.com:6881," +
            "dht.transmissionbt.com:6881,router.bitcomet.com:6881," +
            "dht.libtorrent.org:25401"

    /** Appends any tracker from [PUBLIC_TRACKERS] that the magnet does not already carry. */
    fun withTrackers(magnet: String): String {
        if (!magnet.startsWith("magnet:", ignoreCase = true)) return magnet
        val missing = PUBLIC_TRACKERS.filterNot { tracker ->
            magnet.contains(URLEncoder.encode(tracker, "UTF-8"), ignoreCase = true) ||
                magnet.contains(tracker, ignoreCase = true)
        }
        if (missing.isEmpty()) return magnet
        return magnet + missing.joinToString("") { tracker ->
            "&tr=" + URLEncoder.encode(tracker, "UTF-8")
        }
    }

    /** Adds the public trackers to a live handle and forces an immediate announce everywhere. */
    fun addPeerDiscoveryTrackers(handle: TorrentHandle) {
        PUBLIC_TRACKERS.forEach { url ->
            runCatching { handle.addTracker(AnnounceEntry(url)) }
        }
        runCatching { handle.forceReannounce() }
        runCatching { handle.forceDHTAnnounce() }
        runCatching { handle.forceLSDAnnounce() }
    }
}

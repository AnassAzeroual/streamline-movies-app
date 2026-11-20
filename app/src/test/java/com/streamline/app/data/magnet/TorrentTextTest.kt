package com.streamline.app.data.magnet

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TorrentTextTest {

    @Test
    fun `recognizes video release names`() {
        assertTrue(TorrentText.isLikelyVideoTitle("Dune Part Two 2026 1080p WEB-DL"))
        assertTrue(TorrentText.isLikelyVideoTitle("A Film 2025 BluRay x264"))
        assertTrue(TorrentText.isLikelyVideoTitle("Show S01E02 HDTV HEVC"))
        assertFalse(TorrentText.isLikelyVideoTitle("Adobe Premiere Pro 2026 v26.3 (x64)"))
        assertFalse(TorrentText.isLikelyVideoTitle("uTorrent Pro v3.6 2026"))
        assertFalse(TorrentText.isLikelyVideoTitle("4K YouTube to MP3 Pro 25.3.1 Incl Activator 2026"))
        assertFalse(TorrentText.isLikelyVideoTitle("4K Video Downloader Plus 25.3.1 Incl Activator 2026"))
        assertTrue(TorrentText.isLikelyVideoTitle("Dune Part Two 2026 4K UHD BluRay"))
    }

    @Test
    fun `detects av1 releases`() {
        assertTrue(TorrentText.isAv1("Demon Slayer S01-05 BD 1080p AV1"))
        assertTrue(TorrentText.isAv1("Movie 2026 2160p AV01 10bit"))
        assertFalse(TorrentText.isAv1("Movie 2026 1080p x265 HEVC"))
        assertFalse(TorrentText.isAv1("A Royal Christmas Secret 1080p x264"))
    }

    @Test
    fun `recognizes video categories`() {
        assertTrue(TorrentText.isVideoCategory("Movies / HD"))
        assertTrue(TorrentText.isVideoCategory("TV / HD"))
        assertTrue(TorrentText.isVideoCategory("Anime - English-translated"))
        assertTrue(TorrentText.isVideoCategory("Documentary"))
        assertFalse(TorrentText.isVideoCategory("Applications / Windows"))
        assertFalse(TorrentText.isVideoCategory("Games / PC Game"))
    }
}

package com.streamline.app.core.util

import com.streamline.app.domain.model.MagnetRelease
import com.streamline.app.domain.model.VideoQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QualityRulesTest {

    private fun release(quality: String, seeds: Int = 10): MagnetRelease = MagnetRelease(
        magnetUrl = "magnet:?xt=urn:btih:" + quality.padEnd(40, 'A'),
        quality = quality,
        seeds = seeds,
    )

    @Test
    fun `screen buckets map to the five quality levels`() {
        assertEquals(VideoQuality.SD_480, QualityRules.forScreenShortSide(480))
        assertEquals(VideoQuality.HD_720, QualityRules.forScreenShortSide(720))
        assertEquals(VideoQuality.FHD_1080, QualityRules.forScreenShortSide(1080))
        assertEquals(VideoQuality.QHD_1440, QualityRules.forScreenShortSide(1440))
        assertEquals(VideoQuality.UHD_2160, QualityRules.forScreenShortSide(2160))
        assertEquals(VideoQuality.UHD_2160, QualityRules.forScreenShortSide(0))
    }

    @Test
    fun `bandwidth buckets fall back when unknown`() {
        assertNull(QualityRules.forBandwidthKbps(null))
        assertNull(QualityRules.forBandwidthKbps(0))
        assertEquals(VideoQuality.SD_480, QualityRules.forBandwidthKbps(2_500))
        assertEquals(VideoQuality.HD_720, QualityRules.forBandwidthKbps(5_000))
        assertEquals(VideoQuality.FHD_1080, QualityRules.forBandwidthKbps(10_000))
        assertEquals(VideoQuality.QHD_1440, QualityRules.forBandwidthKbps(25_000))
        assertEquals(VideoQuality.UHD_2160, QualityRules.forBandwidthKbps(50_000))
    }

    @Test
    fun `auto takes the lower of screen and network`() {
        val releases = listOf(
            release("480p"),
            release("720p"),
            release("1080p"),
            release("2160p"),
        )

        assertEquals(
            "720p",
            QualityRules.choose(releases, VideoQuality.FHD_1080, VideoQuality.HD_720)?.quality,
        )
        assertEquals(
            "1080p",
            QualityRules.choose(releases, VideoQuality.FHD_1080, null)?.quality,
        )
        assertEquals(
            "2160p",
            QualityRules.choose(releases, VideoQuality.UHD_2160, VideoQuality.UHD_2160)?.quality,
        )
    }

    @Test
    fun `falls back to the lowest release when everything is above target`() {
        val releases = listOf(release("1080p"), release("2160p"))
        assertEquals(
            "1080p",
            QualityRules.choose(releases, VideoQuality.SD_480, VideoQuality.SD_480)?.quality,
        )
    }

    @Test
    fun `auto prefers a seeded lower quality over an unseeded 4K`() {
        val releases = listOf(
            release("2160p", seeds = 0),
            release("1080p", seeds = 100),
        )
        assertEquals(
            "1080p",
            QualityRules.choose(releases, VideoQuality.UHD_2160, VideoQuality.UHD_2160)?.quality,
        )
    }

    @Test
    fun `quality labels match the user-facing buckets`() {
        assertEquals("480p / SD", VideoQuality.SD_480.displayLabel)
        assertEquals("720p / HD", VideoQuality.HD_720.displayLabel)
        assertEquals("1080p / Full HD", VideoQuality.FHD_1080.displayLabel)
        assertEquals("1440p / 2K", VideoQuality.QHD_1440.displayLabel)
        assertEquals("4K / UHD", VideoQuality.UHD_2160.displayLabel)
        assertEquals("Original quality", VideoQuality.UNKNOWN.displayLabel)
        assertEquals(VideoQuality.UHD_2160, VideoQuality.from("2160p"))
        assertEquals(VideoQuality.QHD_1440, VideoQuality.from("1440p"))
        assertEquals(VideoQuality.FHD_1080, VideoQuality.from("Full HD"))
        assertEquals(VideoQuality.HD_720, VideoQuality.from("HD"))
        assertEquals(VideoQuality.SD_480, VideoQuality.from("SD"))
    }
}

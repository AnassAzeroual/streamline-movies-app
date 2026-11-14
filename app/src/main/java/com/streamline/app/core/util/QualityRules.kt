package com.streamline.app.core.util

import com.streamline.app.domain.model.MagnetRelease
import com.streamline.app.domain.model.VideoQuality

/** Pure quality-selection rules shared by the archive sheet and tests. */
object QualityRules {
    fun forScreenShortSide(pixels: Int): VideoQuality = when {
        pixels <= 0 -> VideoQuality.UHD_2160
        pixels <= 540 -> VideoQuality.SD_480
        pixels <= 720 -> VideoQuality.HD_720
        pixels <= 1080 -> VideoQuality.FHD_1080
        pixels <= 1440 -> VideoQuality.QHD_1440
        else -> VideoQuality.UHD_2160
    }

    fun forBandwidthKbps(kbps: Int?): VideoQuality? {
        if (kbps == null || kbps <= 0) return null
        return when {
            kbps < 3_500 -> VideoQuality.SD_480
            kbps < 8_000 -> VideoQuality.HD_720
            kbps < 20_000 -> VideoQuality.FHD_1080
            kbps < 40_000 -> VideoQuality.QHD_1440
            else -> VideoQuality.UHD_2160
        }
    }

    /**
     * Picks the highest available quality that fits both the screen and the estimated network.
     * If the catalogue only has a higher release, the lowest available release is used rather than
     * refusing to play.
     */
    fun choose(
        releases: List<MagnetRelease>,
        screen: VideoQuality,
        network: VideoQuality?,
    ): MagnetRelease? {
        if (releases.isEmpty()) return null
        val targetHeight = minOf(screen.height, network?.height ?: Int.MAX_VALUE)
        val sorted = releases.sortedByDescending { it.videoQuality.height }
        // Prefer releases with live seeds; a beautiful 4K option with zero seeds is worse than
        // a 1080p option that can actually buffer.
        val seeded = sorted.filter { (it.seeds ?: 0) > 0 }
        val candidates = seeded.ifEmpty { sorted }
        return candidates.firstOrNull { it.videoQuality.height in 1..targetHeight }
            ?: candidates.lastOrNull()
    }
}

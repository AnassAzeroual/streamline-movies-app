package com.streamline.app.core.util

import com.streamline.app.domain.model.MagnetRelease
import com.streamline.app.domain.model.VideoQuality


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

    




    fun choose(
        releases: List<MagnetRelease>,
        screen: VideoQuality,
        network: VideoQuality?,
    ): MagnetRelease? {
        if (releases.isEmpty()) return null
        val targetHeight = minOf(screen.height, network?.height ?: Int.MAX_VALUE)
        val sorted = releases.sortedWith(
            compareByDescending<MagnetRelease> { it.videoQuality.height }
                .thenBy { it.isMatroska }
                .thenByDescending { it.seeds ?: 0 },
        )
        
        
        val seeded = sorted.filter { (it.seeds ?: 0) > 0 }
        val candidates = seeded.ifEmpty { sorted }
        return candidates.firstOrNull { it.videoQuality.height in 1..targetHeight }
            ?: candidates.lastOrNull()
    }
}

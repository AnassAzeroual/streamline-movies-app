package com.streamline.app.domain.model

import java.util.Locale

/** The five quality buckets used by the archive's playback picker. */
enum class VideoQuality(
    val height: Int,
    val label: String,
    val tier: String,
) {
    SD_480(480, "480p", "SD"),
    HD_720(720, "720p", "HD"),
    FHD_1080(1080, "1080p", "Full HD"),
    QHD_1440(1440, "1440p", "2K"),
    UHD_2160(2160, "4K", "UHD"),
    UNKNOWN(0, "Original", "Quality"),
    ;

    val displayLabel: String
        get() = if (this == UNKNOWN) "Original quality" else label + " / " + tier

    companion object {
        fun from(raw: String?): VideoQuality {
            val value = raw.orEmpty().lowercase(Locale.US)
            val compact = value.replace(" ", "")
            return when {
                value.contains("2160") || value.contains("4k") || value.contains("uhd") -> UHD_2160
                value.contains("1440") || value.contains("2k") || value.contains("qhd") -> QHD_1440
                value.contains("1080") || value.contains("fhd") || compact.contains("fullhd") -> FHD_1080
                value.contains("720") || compact == "hd" -> HD_720
                value.contains("480") || compact == "sd" -> SD_480
                else -> UNKNOWN
            }
        }
    }
}

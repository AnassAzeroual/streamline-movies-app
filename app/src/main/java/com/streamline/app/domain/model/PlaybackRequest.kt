package com.streamline.app.domain.model


data class PlaybackAlternative(
    val magnetUrl: String,
    val torrentUrl: String? = null,
    val quality: String? = null,
    val sourceLabel: String? = null,
    val seeds: Int? = null,
    val sizeLabel: String? = null,
)

data class PlaybackRequest(
    val id: String,
    val title: String,
    val magnetUrl: String,
    val torrentUrl: String? = null,
    val posterUrl: String? = null,
    val sourceLabel: String? = null,
    val quality: String? = null,
    val videoFileIndex: Int? = null,
    val alternatives: List<PlaybackAlternative> = emptyList(),
)

package com.streamline.app.domain.model

/**
 * A one-shot handoff from a magnet listing to the player. It carries enough display metadata
 * for the player to stand on its own without any external catalog lookup.
 */
data class PlaybackRequest(
    val id: String,
    val title: String,
    val magnetUrl: String,
    val torrentUrl: String? = null,
    val posterUrl: String? = null,
    val sourceLabel: String? = null,
    val quality: String? = null,
)

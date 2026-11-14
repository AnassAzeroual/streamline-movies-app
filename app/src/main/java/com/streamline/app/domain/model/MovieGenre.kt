package com.streamline.app.domain.model

import java.util.Locale

/** Best-effort genre tags from structured genres or release-name keywords. */
enum class MovieGenre(
    val label: String,
    val aliases: List<String>,
) {
    ANIME("Anime", listOf("anime")),
    ACTION("Action", listOf("action")),
    COMEDY("Comedy", listOf("comedy")),
    DRAMA("Drama", listOf("drama")),
    HORROR("Horror", listOf("horror")),
    SCI_FI("Sci-Fi", listOf("sci fi", "scifi", "science fiction")),
    THRILLER("Thriller", listOf("thriller")),
    ROMANCE("Romance", listOf("romance", "romantic")),
    DOCUMENTARY("Documentary", listOf("documentary")),
    ANIMATION("Animation", listOf("animation", "animated")),
    FAMILY("Family", listOf("family", "kids")),
    FANTASY("Fantasy", listOf("fantasy")),
    MYSTERY("Mystery", listOf("mystery")),
    ADVENTURE("Adventure", listOf("adventure")),
    CRIME("Crime", listOf("crime")),
    ;

    fun matches(item: MagnetTitle): Boolean {
        if (this == ANIME && item.source == MagnetSource.NYAA) return true
        val genreFields = item.genres + item.genreLabel
        if (genreFields.any { field ->
                val normalized = normalize(field)
                aliases.any { normalized.hasToken(normalize(it)) }
            }
        ) {
            return true
        }
        val title = normalize(item.title)
        return aliases.any { title.hasToken(normalize(it)) }
    }

    private companion object {
        fun normalize(value: String): String =
            value.lowercase(Locale.US)
                .replace(Regex("[^a-z0-9]+"), " ")
                .trim()
                .replace(Regex("\\s+"), " ")

        fun String.hasToken(token: String): Boolean =
            token.isNotBlank() && (" " + this + " ").contains(" " + token + " ")
    }
}

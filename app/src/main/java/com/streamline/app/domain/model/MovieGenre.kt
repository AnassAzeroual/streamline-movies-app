package com.streamline.app.domain.model

import java.util.Locale


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
    WESTERN("Western", listOf("western")),
    WAR("War", listOf("war", "warfare")),
    MUSIC("Music", listOf("music", "musical")),
    HISTORY("History", listOf("history", "historical")),
    BIOGRAPHY("Biography", listOf("biography", "biopic")),
    SPORT("Sport", listOf("sport", "sports")),
    SHORT("Short", listOf("short film", "short")),
    FILM_NOIR("Film-Noir", listOf("film noir", "noir")),
    TV_MOVIE("TV Movie", listOf("tv movie", "tv film")),
    TV_SERIES("TV Series", listOf("tv series", "tv show", "complete series", "season")),
    SUPERHERO("Superhero", listOf("superhero", "super hero")),
    MARTIAL_ARTS("Martial Arts", listOf("martial arts", "kung fu", "karate")),
    DISASTER("Disaster", listOf("disaster")),
    HOLIDAY("Holiday", listOf("holiday", "christmas", "xmas")),
    CULT("Cult", listOf("cult")),
    INDIE("Indie", listOf("indie")),
    SILENT("Silent", listOf("silent film", "silent")),
    REALITY("Reality", listOf("reality")),
    TALK("Talk Show", listOf("talk show", "talkshow")),
    NEWS("News", listOf("news")),
    GAME_SHOW("Game Show", listOf("game show", "gameshow")),
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

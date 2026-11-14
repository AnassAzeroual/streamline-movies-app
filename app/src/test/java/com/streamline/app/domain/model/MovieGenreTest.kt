package com.streamline.app.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MovieGenreTest {

    private fun title(
        name: String,
        genres: List<String> = emptyList(),
        source: MagnetSource = MagnetSource.YTS,
        genreLabel: String? = null,
    ) = MagnetTitle(
        id = "id",
        source = source,
        title = name,
        magnetUrl = "magnet:?xt=urn:btih:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
        genres = if (genreLabel != null) listOf(genreLabel) + genres else genres,
    )

    @Test
    fun `matches structured yts genres`() {
        assertTrue(MovieGenre.ACTION.matches(title("Movie", listOf("Action", "Thriller"))))
        assertTrue(MovieGenre.SCI_FI.matches(title("Movie", listOf("Science Fiction"))))
        assertFalse(MovieGenre.HORROR.matches(title("Movie", listOf("Action"))))
    }

    @Test
    fun `matches keywords in release names`() {
        assertTrue(MovieGenre.COMEDY.matches(title("The Big Comedy Movie 2026 1080p")))
        assertTrue(MovieGenre.HORROR.matches(title("Horror Night 2026")))
        assertTrue(MovieGenre.SCI_FI.matches(title("Sci-Fi Collection 2026")))
        assertFalse(MovieGenre.DRAMA.matches(title("An Action Movie 2026")))
    }

    @Test
    fun `anime includes nyaa and anime categories`() {
        assertTrue(MovieGenre.ANIME.matches(title("Show 01", source = MagnetSource.NYAA)))
        assertTrue(MovieGenre.ANIME.matches(title("Show", listOf("Anime"))))
        assertFalse(MovieGenre.ANIME.matches(title("Movie", listOf("Action"))))
    }

    @Test
    fun `untagged releases do not match a specific genre`() {
        assertFalse(MovieGenre.ACTION.matches(title("Movie 2026 1080p")))
    }
}

package com.streamline.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MagnetTitleTest {

    @Test
    fun `maps an archive title to a standalone playback request`() {
        val title = MagnetTitle(
            id = "yts:1",
            source = MagnetSource.YTS,
            title = "Example Movie",
            magnetUrl = "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567",
            torrentUrl = "https://yts.example/example.torrent",
            year = 2024,
            posterUrl = "https://images.example/poster.jpg",
            quality = "1080p",
        )

        val request = title.playbackRequest()

        assertEquals("yts:1", request.id)
        assertEquals("Example Movie", request.title)
        assertEquals(title.magnetUrl, request.magnetUrl)
        assertEquals("https://images.example/poster.jpg", request.posterUrl)
        assertEquals("YTS", request.sourceLabel)
        assertEquals("1080p", request.quality)
        assertEquals("https://yts.example/example.torrent", request.torrentUrl)
    }

    @Test
    fun `detects adult categories and title tags`() {
        val base = MagnetTitle(
            id = "x",
            source = MagnetSource.KNABEN,
            title = "Movie 2026 1080p",
            magnetUrl = "magnet:?xt=urn:btih:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
        )
        assertTrue(base.copy(genres = listOf("Adult")).isAdult)
        assertTrue(base.copy(title = "XXX Compilation 2026 1080p").isAdult)
        assertTrue(base.copy(title = "Hentai Collection").isAdult)
        assertFalse(base.copy(title = "Sex Education 2020 1080p").isAdult)
    }

    @Test
    fun `selected release becomes the playback request`() {
        val title = MagnetTitle(
            id = "yts:2",
            source = MagnetSource.YTS,
            title = "Example Movie",
            magnetUrl = "magnet:?xt=urn:btih:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
            quality = "1080p",
            releases = listOf(
                MagnetRelease(
                    magnetUrl = "magnet:?xt=urn:btih:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                    quality = "1080p",
                    sizeLabel = "2 GB",
                ),
                MagnetRelease(
                    magnetUrl = "magnet:?xt=urn:btih:BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB",
                    quality = "2160p",
                    sizeLabel = "8 GB",
                ),
            ),
        )

        val request = title.playbackRequest(title.playableReleases().last())

        assertEquals("magnet:?xt=urn:btih:BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB", request.magnetUrl)
        assertEquals("2160p", request.quality)
        assertEquals(1, request.alternatives.size)
        assertEquals("1080p", request.alternatives.single().quality)
    }
}

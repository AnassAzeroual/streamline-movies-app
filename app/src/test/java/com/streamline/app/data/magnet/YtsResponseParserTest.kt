package com.streamline.app.data.magnet

import com.streamline.app.domain.model.VideoQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YtsResponseParserTest {

    private val payload = """
        {
          "data": {
            "movie_count": 42,
            "page_number": 1,
            "movies": [
              {
                "_id": "abc123",
                "title": "Example Movie",
                "year": 2024,
                "rating": 7.8,
                "genres": ["Action", "Drama"],
                "large_cover_image": "https://images.example/poster.jpg",
                "torrents": [
                  {
                    "hash": "1111111111111111111111111111111111111111",
                    "quality": "720p",
                    "size": "800 MB",
                    "seeds": 10,
                    "peers": 3
                  },
                  {
                    "hash": "2222222222222222222222222222222222222222",
                    "quality": "1080p",
                    "size": "1.4 GB",
                    "seeds": 30,
                    "peers": 5,
                    "url": "https://yts.example/torrent/example-1080.torrent"
                  }
                ]
              },
              {
                "_id": "def456",
                "title": "No Magnet",
                "year": 2020,
                "torrents": [{ "quality": "1080p" }]
              }
            ]
          }
        }
    """.trimIndent()

    @Test
    fun `maps a movie and chooses the highest quality torrent`() {
        val page = YtsResponseParser.parse(payload, page = 1)

        assertEquals(1, page.items.size)
        assertEquals(42, page.totalResults)
        assertTrue(page.hasMore)

        val movie = page.items.single()
        assertEquals("yts:abc123", movie.id)
        assertEquals("Example Movie", movie.title)
        assertEquals(2024, movie.year)
        assertEquals("7.8", movie.ratingLabel)
        assertEquals("Action", movie.genreLabel)
        assertEquals("https://images.example/poster.jpg", movie.posterUrl)
        assertEquals("1080p", movie.quality)
        assertEquals("1.4 GB", movie.sizeLabel)
        assertEquals(30, movie.seeds)
        assertEquals(5, movie.peers)
        assertEquals(
            "https://yts.example/torrent/example-1080.torrent",
            movie.torrentUrl,
        )
        assertEquals(2, movie.releases.size)
        assertEquals(listOf("1080p", "720p"), movie.releases.map { it.quality })
        assertEquals(VideoQuality.FHD_1080, movie.releases[0].videoQuality)
        assertEquals(VideoQuality.HD_720, movie.releases[1].videoQuality)
        assertTrue(movie.magnetUrl.startsWith("magnet:?xt=urn:btih:2222222222222222222222222222222222222222"))
        assertTrue(movie.magnetUrl.contains("dn=Example%20Movie%20%282024%29%20%5B1080p%5D%20%5BYTS%5D"))
    }

    @Test
    fun `returns an empty page when no movies are present`() {
        val page = YtsResponseParser.parse("""{"data":{"movie_count":0}}""", page = 1)

        assertTrue(page.items.isEmpty())
        assertEquals(0, page.totalResults)
        assertTrue(!page.hasMore)
    }

    @Test
    fun `survives malformed json without throwing`() {
        val page = YtsResponseParser.parse("<html>not json</html>", page = 1)

        assertTrue(page.items.isEmpty())
        assertEquals(1, page.page)
    }

    @Test
    fun `falls back to title_long and medium cover fields`() {
        val page = YtsResponseParser.parse(
            """
            {
              "data": {
                "movie_count": 1,
                "movies": [
                  {
                    "id": 9,
                    "title": "Short",
                    "title_long": "Short Title (1999)",
                    "medium_cover_image": "https://images.example/medium.jpg",
                    "torrents": [
                      {
                        "hash": "3333333333333333333333333333333333333333",
                        "quality": "2160p",
                        "size": "4 GB",
                        "seeds": 3,
                        "peers": 1
                      }
                    ]
                  }
                ]
              }
            }
            """.trimIndent(),
            page = 1,
        )

        assertEquals("Short", page.items.single().title)
        assertEquals("https://images.example/medium.jpg", page.items.single().posterUrl)
        assertEquals("2160p", page.items.single().quality)
    }
}

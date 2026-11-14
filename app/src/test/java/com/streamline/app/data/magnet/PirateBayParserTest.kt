package com.streamline.app.data.magnet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PirateBayParserTest {

    private val json = """
        [
          {
            "name": "Avengers.Endgame.2019.1080p.BRRip.x264-MP4",
            "info_hash": "223F7484D326AD8EFD3CF1E548DED524833CB77E",
            "leechers": "217",
            "seeders": "1127",
            "size": "3234080726",
            "category": "207"
          },
          {
            "name": "Hello.Witch.S01E06.720p.HDTV.x264",
            "info_hash": "866BDCFA006930A718ADBC21D8CAE1F2C7F9D8B3",
            "leechers": "5",
            "seeders": "7",
            "size": "88915896",
            "category": "208"
          },
          {
            "name": "Adult.Release",
            "info_hash": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
            "leechers": "0",
            "seeders": "0",
            "size": "1000",
            "category": "500"
          }
        ]
    """.trimIndent()

    @Test
    fun `maps video results and filters adult categories`() {
        val page = PirateBayParser.parse(json)

        assertEquals(2, page.items.size)
        val movie = page.items[0]
        assertEquals("tpb:223F7484D326AD8EFD3CF1E548DED524833CB77E", movie.id)
        assertEquals("Avengers Endgame 2019 1080p BRRip x264-MP4", movie.title)
        assertEquals("Movies", movie.genreLabel)
        assertEquals("1080p", movie.quality)
        assertEquals(2019, movie.year)
        assertEquals(1127, movie.seeds)
        assertEquals("3.01 GB", movie.sizeLabel)
        assertTrue(movie.magnetUrl.startsWith("magnet:?xt=urn:btih:223F7484D326AD8EFD3CF1E548DED524833CB77E"))
        assertEquals("TV", page.items[1].genreLabel)
    }
}

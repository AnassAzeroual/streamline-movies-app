package com.streamline.app.data.magnet

import org.junit.Assert.assertEquals
import org.junit.Test

class TorrentsCsvParserTest {

    @Test
    fun `maps the torrents-csv response`() {
        val page = TorrentsCsvParser.parse(
            """
            {
              "torrents": [
                {
                  "infohash": "866bdcfa006930a718adbc21d8cae1f2c7f9d8b3",
                  "name": "Avengers: Infinity War (2018)",
                  "size_bytes": 2566242959,
                  "seeders": 928,
                  "leechers": 270
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(1, page.items.size)
        val item = page.items.single()
        assertEquals("csv:866BDCFA006930A718ADBC21D8CAE1F2C7F9D8B3", item.id)
        assertEquals("Avengers: Infinity War (2018)", item.title)
        assertEquals(928, item.seeds)
        assertEquals("2.39 GB", item.sizeLabel)
        assertEquals("Torrent", item.genreLabel)
    }
}

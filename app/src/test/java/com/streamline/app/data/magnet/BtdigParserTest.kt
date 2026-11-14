package com.streamline.app.data.magnet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BtdigParserTest {

    private val html = """
        <html><body>
          <div class="one_result" style="display:table-row;background-color:#e8e8e8">
            <div class="torrent_name" style="display:table-cell">
              <a href="https://btdig.com/223f7484/avengers">Avengers Endgame 2019 1080p</a>
            </div>
            <span class="torrent_size" style="color:#666">3.01 GB</span>
            <div class="torrent_magnet">
              <a href="magnet:?xt=urn:btih:223F7484D326AD8EFD3CF1E548DED524833CB77E&amp;dn=Avengers+Endgame">magnet</a>
            </div>
          </div>
          <div class="one_result" style="display:table-row">
            <div class="torrent_name"><a href="https://btdig.com/866bdcfa/avengers">Avengers Infinity War 2018 1080p</a></div>
            <span class="torrent_size">2.39 GB</span>
            <div class="torrent_magnet">
              <a href="magnet:?xt=urn:btih:866BDCFA006930A718ADBC21D8CAE1F2C7F9D8B3&amp;dn=Avengers+Infinity+War">magnet</a>
            </div>
          </div>
        </body></html>
    """.trimIndent()

    @Test
    fun `maps btdig result blocks`() {
        val page = BtdigParser.parse(html)

        assertEquals(2, page.items.size)
        val item = page.items[0]
        assertEquals("btdig:223F7484D326AD8EFD3CF1E548DED524833CB77E", item.id)
        assertEquals("Avengers Endgame 2019 1080p", item.title)
        assertEquals("3.01 GB", item.sizeLabel)
        assertEquals("1080p", item.quality)
        assertEquals(2019, item.year)
        assertEquals("Torrent", item.genreLabel)
        assertTrue(item.magnetUrl.contains("btih:223F7484D326AD8EFD3CF1E548DED524833CB77E"))
        assertTrue(item.magnetUrl.contains("&dn=Avengers+Endgame"))
    }
}

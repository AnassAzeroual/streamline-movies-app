package com.streamline.app.data.magnet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KnabenParserTest {

    private val html = """
        <table class="table">
          <tbody>
            <tr class="text-nowrap border-start" data-id="0eb04a1bca760b909b7255cce7b9638f96915619">
              <td class="d-last-child">
                <a href="/browse/3000000/1">Movies</a> / <a href="/browse/3001000/1">HD</a>
              </td>
              <td class="text-wrap w-100">
                <a title="Avengers Endgame (2019) 1080p BRRip x264 -YTS" href="magnet:?xt=urn:btih:223F7484D326AD8EFD3CF1E548DED524833CB77E&amp;dn=Avengers+Endgame">Avengers Endgame (2019) 1080p BRRip x264 -YTS</a>
              </td>
              <td title="3231962890 Bytes">3.01 GB</td>
              <td title="2019-08-01">2019-08-01</td>
              <td style="color: #64e7e1ff">1141</td>
              <td style="color: #64e7e1ff">214</td>
              <td class="d-sm-none d-xl-table-cell"><a href="https://yts.gg/movies/avengers-endgame-2019">YTS</a></td>
            </tr>
          </tbody>
        </table>
    """.trimIndent()

    @Test
    fun `maps a knaben result row and decodes the magnet`() {
        val page = KnabenParser.parse(html, page = 1)

        assertEquals(1, page.items.size)
        val item = page.items.single()
        assertEquals("knaben:223F7484D326AD8EFD3CF1E548DED524833CB77E", item.id)
        assertEquals("Avengers Endgame (2019) 1080p BRRip x264 -YTS", item.title)
        assertEquals(1141, item.seeds)
        assertEquals(214, item.peers)
        assertEquals("3.01 GB", item.sizeLabel)
        assertEquals("Movies", item.genreLabel)
        assertEquals("1080p", item.quality)
        assertTrue(item.magnetUrl.contains("&dn=Avengers+Endgame"))
    }
}

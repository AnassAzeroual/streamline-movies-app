package com.streamline.app.data.magnet

import com.streamline.app.domain.model.MagnetSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class LimeTorrentsParserTest {
    @Test
    fun `parses a LimeTorrents result row`() {
        val hash = "ABCDEF0123456789ABCDEF0123456789ABCDEF01"
        val html = """
            <table class="table2">
              <tr bgcolor="#F4F4F4">
                <td class="tdleft"><div class="tt-name">
                  <a href="http://itorrents.net/torrent/$hash.torrent?title=x" rel="nofollow"></a>
                  <a href="/The-Matrix-1999-1080p-torrent-123.html">The Matrix 1999 1080p</a>
                </div></td>
                <td class="tdnormal">1 Year+ - in Movies</td>
                <td class="tdnormal">2.5 GB</td>
                <td class="tdseed">12</td>
                <td class="tdleech">3</td>
                <td class="tdright"><div class="hb5"></div></td>
              </tr>
            </table>
        """.trimIndent()

        val page = LimeTorrentsParser.parse(html, 1)
        val item = page.items.single()

        assertEquals(MagnetSource.LIME, item.source)
        assertEquals("2.5 GB", item.sizeLabel)
        assertEquals(12, item.seeds)
        assertEquals(3, item.peers)
        assertEquals(1999, item.year)
        assertEquals("1080p", item.quality)
        assertNotNull(item.magnetUrl)
        assertEquals("lime:" + hash.lowercase(), item.id)
        assertEquals(1, page.totalResults)
    }
}

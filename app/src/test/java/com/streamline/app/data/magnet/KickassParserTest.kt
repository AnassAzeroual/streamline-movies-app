package com.streamline.app.data.magnet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KickassParserTest {

    private val listing = """
        <table>
          <tr class="odd">
            <td>
              <div class="torrentname">
                <a href="/avengers-endgame-2019-imax-1080p-webrip-x265-atmos-lkf-t6746108.html" class="cellMainLink">
                  <strong>Avengers</strong> Endgame 2019 IMAX 1080p WEBRip x265 Atmos-LKF
                </a>
              </div>
            </td>
            <td class="nobr center"> 3.5 GB </td>
            <td class="center"> lkf001 </td>
            <td class="green center"> 38 </td>
            <td class="red lasttd center"> 10 </td>
          </tr>
        </table>
    """.trimIndent()

    private val detail = """
        <html><body>
          <a href="magnet:?xt=urn:btih:EACBF5983B0C34F1A956F38DCE8ADBB0DF70F015&amp;dn=Avengers.Endgame">Magnet</a>
          <a href="/torrent/avengers-endgame-6746108.torrent">Download</a>
          <img src="/static/movie/Untitled-Avengers-Movie-6746108.jpg">
        </body></html>
    """.trimIndent()

    @Test
    fun `parses kickass listing rows`() {
        val rows = KickassParser.parseListing(listing)

        assertEquals(1, rows.size)
        assertEquals(
            "/avengers-endgame-2019-imax-1080p-webrip-x265-atmos-lkf-t6746108.html",
            rows.single().path,
        )
        assertEquals("Avengers Endgame 2019 IMAX 1080p WEBRip x265 Atmos-LKF", rows.single().title)
        assertEquals("3.5 GB", rows.single().size)
        assertEquals(38, rows.single().seeds)
        assertEquals(10, rows.single().peers)
    }

    @Test
    fun `parses kickass detail magnet and poster`() {
        val parsed = KickassParser.parseDetail(detail, "https://kickasstorrents.to")

        assertTrue(parsed.magnetUrl.orEmpty().startsWith("magnet:?xt=urn:btih:EACBF5983B0C34F1A956F38DCE8ADBB0DF70F015"))
        assertTrue(parsed.magnetUrl.orEmpty().contains("&dn=Avengers.Endgame"))
        assertEquals(
            "https://kickasstorrents.to/static/movie/Untitled-Avengers-Movie-6746108.jpg",
            parsed.posterUrl,
        )
        assertEquals(
            "https://kickasstorrents.to/torrent/avengers-endgame-6746108.torrent",
            parsed.torrentUrl,
        )
    }
}

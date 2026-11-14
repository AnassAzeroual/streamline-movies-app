package com.streamline.app.data.magnet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class X1337HtmlParserTest {

    private val listing = """
        <html><body>
        <table class="table-list table table-responsive table-striped">
          <thead><tr><th>Name</th><th>Seeds</th><th>Leeches</th><th>Size</th></tr></thead>
          <tbody>
            <tr>
              <td class="coll-1 name">
                <a href="/torrent/123/Example-Movie-2024-1080p/" class="icon"><i class="flaticon"></i></a>
                <a href="/torrent/123/Example-Movie-2024-1080p/">Example Movie 2024 1080p WEB-DL</a>
              </td>
              <td class="coll-2 seeds">120</td>
              <td class="coll-3 leeches">12</td>
              <td class="coll-4 size">1.4 GB<span class="seeds">120</span></td>
              <td class="coll-5">uploader</td>
            </tr>
            <tr>
              <td class="coll-1 name">
                <a href="/torrent/456/Another-Film-1999-720p/">Another Film 1999 720p</a>
              </td>
              <td class="coll-2 seeds">8</td>
              <td class="coll-3 leeches">2</td>
              <td class="coll-4 size">700 MB</td>
              <td class="coll-5">uploader</td>
            </tr>
          </tbody>
        </table>
        </body></html>
    """.trimIndent()

    private val detail = """
        <html><body>
          <div class="torrent-image">
            <img src="//cdn.example/poster.jpg" alt="Poster">
          </div>
          <div class="dropdown-menu">
            <ul>
              <li><a href="magnet:?xt=urn:btih:abcdabcdabcdabcdabcdabcdabcdabcdabcdabcd&amp;dn=Example">Magnet</a></li>
              <li><a href="/torrent/123/Example.torrent">Download torrent</a></li>
            </ul>
          </div>
          <div class="infohash-box">
            <p>Infohash :</p>
            <span>abcdabcdabcdabcdabcdabcdabcdabcdabcdabcd</span>
          </div>
        </body></html>
    """.trimIndent()

    @Test
    fun `parses listing rows with the right title seeds and size`() {
        val rows = X1337HtmlParser.parseListing(listing)

        assertEquals(2, rows.size)
        assertEquals("/torrent/123/Example-Movie-2024-1080p/", rows[0].path)
        assertEquals("Example Movie 2024 1080p WEB-DL", rows[0].title)
        assertEquals(120, rows[0].seeds)
        assertEquals(12, rows[0].leeches)
        assertEquals("1.4 GB", rows[0].size)
        assertEquals("/torrent/456/Another-Film-1999-720p/", rows[1].path)
    }

    @Test
    fun `extracts the magnet and poster from a torrent page`() {
        val parsed = X1337HtmlParser.parseDetail(detail, "https://1337x.to")

        assertEquals(
            "magnet:?xt=urn:btih:abcdabcdabcdabcdabcdabcdabcdabcdabcdabcd&dn=Example",
            parsed.magnetUrl,
        )
        assertEquals("abcdabcdabcdabcdabcdabcdabcdabcdabcdabcd", parsed.infoHash)
        assertEquals("https://cdn.example/poster.jpg", parsed.posterUrl)
        assertEquals("https://1337x.to/torrent/123/Example.torrent", parsed.torrentUrl)
    }

    @Test
    fun `extracts the last year and the best quality token`() {
        assertEquals(2017, X1337HtmlParser.extractYear("Blade Runner 2049 2017 1080p"))
        assertEquals(2019, X1337HtmlParser.extractYear("1917 2019"))
        assertEquals("2160p", X1337HtmlParser.extractQuality("Movie 2024 2160p 1080p"))
        assertEquals("1080p", X1337HtmlParser.extractQuality("Movie 2024 1080p"))
        assertNull(X1337HtmlParser.extractQuality("Movie 2024"))
    }

    @Test
    fun `recognizes a cloudflare challenge page`() {
        assertTrue(X1337HtmlParser.isChallengePage("<title>Just a moment...</title>"))
        assertTrue(!X1337HtmlParser.isChallengePage("<table class=\"table-list\"></table>"))
    }

    @Test
    fun `decodes named and numeric entities`() {
        assertEquals("A & B ' C", X1337HtmlParser.decodeEntities("A &amp; B &#39; C"))
        assertEquals("A & B", X1337HtmlParser.decodeEntities("A &#x26; B"))
    }

    @Test
    fun `returns no rows when there is no result table`() {
        assertTrue(X1337HtmlParser.parseListing("<html><body>No results</body></html>").isEmpty())
    }
}

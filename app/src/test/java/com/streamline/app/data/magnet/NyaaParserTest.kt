package com.streamline.app.data.magnet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NyaaParserTest {

    private val xml = """
        <rss xmlns:nyaa="https://nyaa.si/xmlns/nyaa" version="2.0">
          <channel>
            <item>
              <title>Future.Avengers.S02.SUBFRENCH.720p.WEB.x264-Nina</title>
              <link>https://nyaa.si/download/2004814.torrent</link>
              <guid isPermaLink="true">https://nyaa.si/view/2004814</guid>
              <nyaa:seeders>12</nyaa:seeders>
              <nyaa:leechers>3</nyaa:leechers>
              <nyaa:infoHash>9b31970ae7604d47ee01657bb932f4fad2be9f4e</nyaa:infoHash>
              <nyaa:category>Anime - Non-English-translated</nyaa:category>
              <nyaa:size>9.5 GiB</nyaa:size>
            </item>
          </channel>
        </rss>
    """.trimIndent()

    @Test
    fun `maps an nyaa rss item`() {
        val page = NyaaParser.parse(xml, page = 1)

        assertEquals(1, page.items.size)
        val item = page.items.single()
        assertEquals("nyaa:9B31970AE7604D47EE01657BB932F4FAD2BE9F4E", item.id)
        assertEquals("Future Avengers S02 SUBFRENCH 720p WEB x264-Nina", item.title)
        assertEquals(12, item.seeds)
        assertEquals(3, item.peers)
        assertEquals("9.5 GiB", item.sizeLabel)
        assertEquals("Anime", item.genreLabel)
        assertEquals("720p", item.quality)
        assertTrue(item.magnetUrl.startsWith("magnet:?xt=urn:btih:9B31970AE7604D47EE01657BB932F4FAD2BE9F4E"))
        assertEquals("https://nyaa.si/download/2004814.torrent", item.torrentUrl)
    }
}

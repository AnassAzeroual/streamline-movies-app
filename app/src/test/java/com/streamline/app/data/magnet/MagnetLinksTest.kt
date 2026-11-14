package com.streamline.app.data.magnet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MagnetLinksTest {

    @Test
    fun `builds a paste-ready magnet with an encoded display name`() {
        val hash = "0123456789abcdef0123456789abcdef01234567"
        val magnet = MagnetLinks.fromInfoHash(hash, "Movie & More (2024)")

        assertTrue(magnet.startsWith("magnet:?xt=urn:btih:0123456789ABCDEF0123456789ABCDEF01234567"))
        assertTrue(magnet.contains("&dn=Movie%20%26%20More%20%282024%29"))
        assertTrue(magnet.contains("&tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337%2Fannounce"))
    }

    @Test
    fun `accepts an urn prefix and trims whitespace`() {
        val magnet = MagnetLinks.fromInfoHash(
            "  urn:btih:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa  ",
            "Example",
        )
        assertTrue(magnet.startsWith("magnet:?xt=urn:btih:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects a malformed info hash`() {
        MagnetLinks.fromInfoHash("not-a-hash", "Example")
    }

    @Test
    fun `empty display names still produce a valid magnet`() {
        val magnet = MagnetLinks.fromInfoHash("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", "  ")
        assertEquals(true, magnet.contains("&dn=Streamline%20magnet"))
    }
}

package com.streamline.app.core.playback

import com.streamline.app.domain.model.PlaybackRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackKeysTest {
    private val magnet = "magnet:?xt=urn:btih:ABCDEF0123456789ABCDEF0123456789ABCDEF01&dn=Show"

    @Test
    fun legacyKeyIsInfoHash() {
        assertEquals(
            "abcdef0123456789abcdef0123456789abcdef01",
            PlaybackKeys.forFile(magnet, null),
        )
    }

    @Test
    fun eachVideoFileHasItsOwnResumeKey() {
        val first = PlaybackKeys.forFile(magnet, 3)
        val second = PlaybackKeys.forFile(magnet, 4)
        assertEquals("abcdef0123456789abcdef0123456789abcdef01:3", first)
        assertNotEquals(first, second)
    }

    @Test
    fun invalidMagnetHasNoKey() {
        assertNull(PlaybackKeys.forFile("not-a-magnet", 1))
    }

    @Test
    fun historyRequestKeepsSelectedFile() {
        val request = PlaybackRequest(
            id = "show",
            title = "Show S01E02",
            magnetUrl = magnet,
            videoFileIndex = 7,
        )
        assertEquals(7, request.videoFileIndex)
        assertEquals(7, request.copy().videoFileIndex)
    }
}

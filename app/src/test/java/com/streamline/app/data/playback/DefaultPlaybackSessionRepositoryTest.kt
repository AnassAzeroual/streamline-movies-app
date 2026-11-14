package com.streamline.app.data.playback

import com.streamline.app.domain.model.PlaybackRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DefaultPlaybackSessionRepositoryTest {

    private val request = PlaybackRequest(
        id = "yts:1",
        title = "Example",
        magnetUrl = "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567",
        posterUrl = "https://images.example/poster.jpg",
        sourceLabel = "YTS",
    )

    @Test
    fun `offers a request and consumes it exactly once`() {
        val repository = DefaultPlaybackSessionRepository()
        repository.offer(request)

        assertEquals(request, repository.consume())
        assertNull(repository.consume())
    }

    @Test
    fun `a newer offer replaces the pending request`() {
        val repository = DefaultPlaybackSessionRepository()
        repository.offer(request)
        val replacement = request.copy(id = "1337x:2", title = "Another")

        repository.offer(replacement)

        assertEquals(replacement, repository.consume())
    }
}

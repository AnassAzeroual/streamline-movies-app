package com.streamline.app.data.playback

import com.streamline.app.domain.model.PlaybackRequest
import com.streamline.app.domain.repository.PlaybackSessionRepository

class DefaultPlaybackSessionRepository : PlaybackSessionRepository {
    private var pending: PlaybackRequest? = null

    @Synchronized
    override fun offer(request: PlaybackRequest) {
        pending = request
    }

    @Synchronized
    override fun consume(): PlaybackRequest? {
        val request = pending
        pending = null
        return request
    }
}

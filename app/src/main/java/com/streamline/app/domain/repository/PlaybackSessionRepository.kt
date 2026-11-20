package com.streamline.app.domain.repository

import com.streamline.app.domain.model.PlaybackRequest





interface PlaybackSessionRepository {
    fun offer(request: PlaybackRequest)
    fun consume(): PlaybackRequest?
}

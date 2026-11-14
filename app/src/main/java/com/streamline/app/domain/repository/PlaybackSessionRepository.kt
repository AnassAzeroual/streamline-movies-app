package com.streamline.app.domain.repository

import com.streamline.app.domain.model.PlaybackRequest

/**
 * Holds the playback handoff between the magnet archive and the player. Keeping the request out
 * of navigation arguments avoids encoding long magnet URIs and their trackers into a route.
 */
interface PlaybackSessionRepository {
    fun offer(request: PlaybackRequest)
    fun consume(): PlaybackRequest?
}

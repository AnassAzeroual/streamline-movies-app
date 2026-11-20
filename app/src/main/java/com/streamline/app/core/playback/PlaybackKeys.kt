package com.streamline.app.core.playback

object PlaybackKeys {
    private val INFO_HASH = Regex("(?i)btih:([a-z0-9]{32,40})")

    fun hashFor(magnetUrl: String): String? =
        INFO_HASH.find(magnetUrl)?.groupValues?.getOrNull(1)?.lowercase()

    fun forFile(magnetUrl: String, fileIndex: Int?): String? {
        val hash = hashFor(magnetUrl) ?: return null
        return if (fileIndex == null) hash else hash + ":" + fileIndex
    }
}

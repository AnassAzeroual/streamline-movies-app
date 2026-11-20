package com.streamline.app.core.playback

import android.content.Context
import com.google.gson.Gson
import com.streamline.app.data.magnet.TorrentText
import com.streamline.app.domain.model.PlaybackRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlaybackHistoryEntry(
    val id: String,
    val title: String,
    val magnetUrl: String,
    val torrentUrl: String? = null,
    val posterUrl: String? = null,
    val sourceLabel: String? = null,
    val quality: String? = null,
    val videoFileIndex: Int? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val updatedAt: Long = 0L,
) {
    val progress: Float
        get() = if (durationMs > 0L) {
            (positionMs.toDouble() / durationMs).toFloat().coerceIn(0f, 1f)
        } else {
            0f
        }

    fun request(): PlaybackRequest = PlaybackRequest(
        id = id,
        title = title,
        magnetUrl = magnetUrl,
        torrentUrl = torrentUrl,
        posterUrl = posterUrl,
        sourceLabel = sourceLabel,
        quality = quality,
        videoFileIndex = videoFileIndex,
    )
}

class PlaybackHistoryStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<PlaybackHistoryEntry>> = _items.asStateFlow()

    @Synchronized
    fun record(request: PlaybackRequest, positionMs: Long, durationMs: Long) {
        if (positionMs < MIN_POSITION_MS || durationMs <= 0L) return
        if (TorrentText.isAv1(request.title)) return
        val entry = PlaybackHistoryEntry(
            id = request.id,
            title = request.title,
            magnetUrl = request.magnetUrl,
            torrentUrl = request.torrentUrl,
            posterUrl = request.posterUrl,
            sourceLabel = request.sourceLabel,
            quality = request.quality,
            videoFileIndex = request.videoFileIndex,
            positionMs = positionMs,
            durationMs = durationMs,
            updatedAt = System.currentTimeMillis(),
        )
        val next = (listOf(entry) + _items.value.filterNot { it.id == entry.id })
            .sortedByDescending { it.updatedAt }
            .take(MAX_ITEMS)
        _items.value = next
        save(next)
    }

    @Synchronized
    fun remove(id: String) {
        val next = _items.value.filterNot { it.id == id }
        _items.value = next
        save(next)
    }

    
    @Synchronized
    fun clearAll() {
        _items.value = emptyList()
        preferences.edit().clear().apply()
    }

    private fun load(): List<PlaybackHistoryEntry> {
        val raw = preferences.getString(KEY_ITEMS, null) ?: return emptyList()
        return runCatching {
            gson.fromJson(raw, Array<PlaybackHistoryEntry>::class.java)
                ?.filterNotNull()
                ?.filter { it.id.isNotBlank() && it.magnetUrl.isNotBlank() }
                ?.filterNot { TorrentText.isAv1(it.title) }
                ?.sortedByDescending { it.updatedAt }
                ?.take(MAX_ITEMS)
                ?.toList()
                .orEmpty()
        }.getOrDefault(emptyList())
    }

    private fun save(items: List<PlaybackHistoryEntry>) {
        preferences.edit().putString(KEY_ITEMS, gson.toJson(items)).apply()
    }

    private companion object {
        const val PREFERENCES = "streamline_history"
        const val KEY_ITEMS = "items"
        const val MAX_ITEMS = 20
        const val MIN_POSITION_MS = 10_000L
    }
}

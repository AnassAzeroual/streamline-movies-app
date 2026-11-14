package com.streamline.app.core.playback

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Remembers where the viewer stopped so a stream can resume after the app restarts.
 *
 * Entries are keyed by the torrent info hash: it is stable across magnet rewrites, trackers being
 * appended, and the ephemeral per-session save directory that playback uses.
 */
class PlaybackPositionStore(context: Context) {

    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val mapType = object : TypeToken<Map<String, Entry>>() {}.type

    data class Entry(
        val positionMs: Long = 0L,
        val durationMs: Long = 0L,
        val updatedAt: Long = 0L,
    ) {
        val progressFraction: Float
            get() = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    }

    /** Returns null when there is nothing worth resuming (missing, unwatched, or finished). */
    @Synchronized
    fun get(key: String?): Entry? {
        if (key.isNullOrBlank()) return null
        val entry = read()[key] ?: return null
        if (entry.positionMs < MIN_RESUME_MS) return null
        if (entry.durationMs > 0L && entry.positionMs >= entry.durationMs * COMPLETED_FRACTION) {
            return null
        }
        return entry
    }

    @Synchronized
    fun save(key: String?, entry: Entry) {
        if (key.isNullOrBlank()) return
        if (entry.positionMs < MIN_RESUME_MS) return
        write(read() + (key to entry.copy(updatedAt = System.currentTimeMillis())))
    }

    @Synchronized
    fun clear(key: String?) {
        if (key.isNullOrBlank()) return
        val entries = read()
        if (entries.containsKey(key)) write(entries - key)
    }

    /** Info hash of a magnet URI, or null when the URI carries none. */
    fun keyFor(magnetUrl: String): String? =
        INFO_HASH.find(magnetUrl)?.groupValues?.getOrNull(1)?.lowercase()

    private fun read(): Map<String, Entry> {
        val raw = preferences.getString(KEY_ENTRIES, null) ?: return emptyMap()
        return runCatching {
            gson.fromJson<Map<String, Entry>>(raw, mapType)?.toMap().orEmpty()
        }.getOrDefault(emptyMap())
    }

    private fun write(entries: Map<String, Entry>) {
        val pruned = if (entries.size > MAX_ENTRIES) {
            entries.entries.sortedByDescending { it.value.updatedAt }.take(MAX_ENTRIES)
                .associate { it.key to it.value }
        } else {
            entries
        }
        preferences.edit().putString(KEY_ENTRIES, gson.toJson(pruned)).apply()
    }

    private companion object {
        const val PREFERENCES = "streamline_positions"
        const val KEY_ENTRIES = "entries"
        const val MAX_ENTRIES = 100
        const val MIN_RESUME_MS = 30_000L
        const val COMPLETED_FRACTION = 0.95f
        val INFO_HASH = Regex("(?i)btih:([a-z0-9]{32,40})")
    }
}

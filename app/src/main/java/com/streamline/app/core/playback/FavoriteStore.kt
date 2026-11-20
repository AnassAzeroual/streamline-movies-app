package com.streamline.app.core.playback

import android.content.Context
import com.google.gson.Gson
import com.streamline.app.data.magnet.TorrentText
import com.streamline.app.domain.model.MagnetTitle
import com.streamline.app.domain.model.PlaybackRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class FavoriteEntry(
    val item: MagnetTitle,
    val addedAt: Long = System.currentTimeMillis(),
) {
    fun request(): PlaybackRequest {
        val release = item.playableReleases().firstOrNull()
        return item.playbackRequest(release)
    }
}

class FavoriteStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<FavoriteEntry>> = _items.asStateFlow()

    @Synchronized
    fun toggle(item: MagnetTitle): Boolean {
        if (TorrentText.isAv1(item.title)) return false
        val existing = _items.value.firstOrNull { it.item.id == item.id }
        val next = if (existing != null) {
            _items.value.filterNot { it.item.id == item.id }
        } else {
            listOf(FavoriteEntry(item)) + _items.value
        }.take(MAX_ITEMS)
        _items.value = next
        save(next)
        return existing == null
    }

    @Synchronized
    fun remove(id: String) {
        val next = _items.value.filterNot { it.item.id == id }
        _items.value = next
        save(next)
    }

    fun favoriteIds(): Set<String> = _items.value.mapTo(HashSet()) { it.item.id }

    private fun load(): List<FavoriteEntry> {
        val raw = preferences.getString(KEY_ITEMS, null) ?: return emptyList()
        return runCatching {
            gson.fromJson(raw, Array<FavoriteEntry>::class.java)
                ?.filterNotNull()
                ?.filter { it.item.id.isNotBlank() && it.item.magnetUrl.isNotBlank() }
                ?.filterNot { TorrentText.isAv1(it.item.title) }
                ?.sortedByDescending { it.addedAt }
                ?.take(MAX_ITEMS)
                ?.toList()
                .orEmpty()
        }.getOrDefault(emptyList())
    }

    private fun save(items: List<FavoriteEntry>) {
        preferences.edit().putString(KEY_ITEMS, gson.toJson(items)).apply()
    }

    private companion object {
        const val PREFERENCES = "streamline_favorites"
        const val KEY_ITEMS = "items"
        const val MAX_ITEMS = 100
    }
}

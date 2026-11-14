package com.streamline.app.core.download

import android.content.Context
import com.google.gson.Gson

/** Small JSON store for the download queue. Kept separate from the torrent session. */
internal class DownloadStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val gson = Gson()

    @Synchronized
    fun load(): List<DownloadItem> {
        val raw = preferences.getString(KEY_ITEMS, null) ?: return emptyList()
        return runCatching {
            gson.fromJson(raw, Array<DownloadItem>::class.java)
                ?.filterNotNull()
                ?.mapNotNull { item ->
                    runCatching {
                        val id = item.id
                        if (id.isBlank()) return@runCatching null
                        if (item.magnetUrl.isBlank() &&
                            item.savedPath.isNullOrBlank() &&
                            item.publicUri.isNullOrBlank()
                        ) {
                            return@runCatching null
                        }
                        val status = runCatching { item.status }.getOrNull()
                            ?: DownloadStatus.PAUSED
                        item.copy(
                            status = status,
                            downloadRate = 0L,
                            peers = 0,
                        )
                    }.getOrNull()
                }
                ?.toList()
                .orEmpty()
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun save(items: List<DownloadItem>) {
        preferences.edit()
            .putString(KEY_ITEMS, gson.toJson(items))
            .apply()
    }

    private companion object {
        const val PREFERENCES = "streamline_downloads"
        const val KEY_ITEMS = "items"
    }
}

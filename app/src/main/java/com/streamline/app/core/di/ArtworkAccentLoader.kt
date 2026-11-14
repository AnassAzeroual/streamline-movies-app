package com.streamline.app.core.di

import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

/** Downloads a small poster crop and derives a readable accent colour from its palette. */
class ArtworkAccentLoader(private val client: OkHttpClient) {
    private val cache = ConcurrentHashMap<String, Int>()

    suspend fun loadAccent(imageUrl: String?): Int? {
        if (imageUrl.isNullOrBlank()) return null
        cache[imageUrl]?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url(imageUrl).build()
                val bytes = client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    response.body?.bytes()
                } ?: return@withContext null
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?: return@withContext null
                val palette = Palette.from(bitmap).clearFilters().generate()
                val swatch = palette.vibrantSwatch
                    ?: palette.lightVibrantSwatch
                    ?: palette.mutedSwatch
                    ?: palette.dominantSwatch
                    ?: return@withContext null
                val hsv = FloatArray(3)
                Color.colorToHSV(swatch.rgb, hsv)
                hsv[1] = max(hsv[1], 0.42f)
                hsv[2] = hsv[2].coerceIn(0.62f, 0.88f)
                val readable = Color.HSVToColor(hsv) or (0xFF shl 24)
                cache[imageUrl] = readable
                readable
            }.getOrNull()
        }
    }
}

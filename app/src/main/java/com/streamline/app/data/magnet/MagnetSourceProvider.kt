package com.streamline.app.data.magnet

import com.streamline.app.domain.model.MagnetPage

/** A single third-party index behind the archive screen. */
interface MagnetSourceProvider {
    suspend fun browse(query: String, page: Int): MagnetPage
}

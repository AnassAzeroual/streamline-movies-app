package com.streamline.app.data.magnet

import com.streamline.app.domain.model.MagnetPage


interface MagnetSourceProvider {
    suspend fun browse(query: String, page: Int): MagnetPage
}

package com.streamline.app.domain.repository

import com.streamline.app.domain.model.MagnetPage
import com.streamline.app.domain.model.MagnetSource

interface MagnetCatalogRepository {
    suspend fun browse(
        source: MagnetSource,
        query: String,
        page: Int,
    ): MagnetPage
}

/** A source-level failure whose [message] is safe to show directly in the archive UI. */
class MagnetCatalogException(
    override val message: String,
    cause: Throwable? = null,
) : java.io.IOException(message, cause)

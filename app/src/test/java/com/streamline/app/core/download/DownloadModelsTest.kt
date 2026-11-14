package com.streamline.app.core.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadModelsTest {

    private fun item(
        status: DownloadStatus,
        downloaded: Long = 0L,
        total: Long = 0L,
    ): DownloadItem = DownloadItem(
        id = "hash",
        title = "Example",
        magnetUrl = "magnet:?xt=urn:btih:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
        status = status,
        downloadedBytes = downloaded,
        totalBytes = total,
    )

    @Test
    fun `progress and percent are derived from byte counters`() {
        val downloading = item(DownloadStatus.DOWNLOADING, downloaded = 250L, total = 1_000L)
        assertEquals(0.25f, downloading.progress, 0.001f)
        assertEquals(25, downloading.percent)
        assertTrue(downloading.canPause)
        assertFalse(downloading.canResume)

        val empty = item(DownloadStatus.FETCHING)
        assertEquals(0f, empty.progress, 0.001f)
        assertEquals(0, empty.percent)
    }

    @Test
    fun `paused and failed items can resume`() {
        assertTrue(item(DownloadStatus.PAUSED, 50, 100).canResume)
        assertTrue(item(DownloadStatus.FAILED).canResume)
        assertFalse(item(DownloadStatus.FAILED).canPause)
        assertTrue(item(DownloadStatus.COMPLETED, 100, 100).isComplete)
        assertFalse(item(DownloadStatus.COMPLETED, 100, 100).canPause)
    }
}

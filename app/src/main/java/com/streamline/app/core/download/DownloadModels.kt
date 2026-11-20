package com.streamline.app.core.download

enum class DownloadStatus(val label: String, val isActive: Boolean) {
    QUEUED("Queued", isActive = true),
    FETCHING("Finding peers", isActive = true),
    DOWNLOADING("Downloading", isActive = true),
    PAUSED("Paused", isActive = false),
    COMPLETED("Ready", isActive = false),
    FAILED("Failed", isActive = false),
}

data class DownloadFileOption(
    val index: Int,
    val path: String,
    val size: Long,
    val isVideo: Boolean,
    val isSubtitle: Boolean,
)

data class DownloadItem(
    val id: String,
    val title: String,
    val magnetUrl: String,
    val posterUrl: String? = null,
    val sourceLabel: String? = null,
    val quality: String? = null,
    val selectedFileIndexes: List<Int> = emptyList(),
    val addedAt: Long = System.currentTimeMillis(),
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val downloadRate: Long = 0L,
    val peers: Int = 0,
    val error: String? = null,
    val savedPath: String? = null,
    val publicUri: String? = null,
) {
    val progress: Float
        get() = if (totalBytes > 0L) {
            (downloadedBytes.toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f)
        } else {
            0f
        }

    val percent: Int
        get() = (progress * 100f).toInt().coerceIn(0, 100)

    val isComplete: Boolean
        get() = status == DownloadStatus.COMPLETED

    val isPaused: Boolean
        get() = status == DownloadStatus.PAUSED

    val canPause: Boolean
        get() = status == DownloadStatus.QUEUED ||
            status == DownloadStatus.FETCHING ||
            status == DownloadStatus.DOWNLOADING

    val canResume: Boolean
        get() = status == DownloadStatus.PAUSED || status == DownloadStatus.FAILED
}

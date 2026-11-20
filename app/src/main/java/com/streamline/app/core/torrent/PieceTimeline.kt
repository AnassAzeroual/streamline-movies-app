package com.streamline.app.core.torrent

data class PieceTimeline(
    val fileSize: Long,
    val durationMs: Long,
    val pieceLength: Int,
    val fileTorrentOffset: Long,
    val firstPiece: Int,
    val lastPiece: Int,
) {
    val pieceDurationMs: Double
        get() = if (fileSize > 0L && pieceLength > 0) {
            durationMs.toDouble() * pieceLength / fileSize
        } else {
            0.0
        }

    fun pieceForTime(positionMs: Long): Int {
        if (durationMs <= 0L || pieceLength <= 0 || fileSize <= 0L) return firstPiece
        val clamped = positionMs.coerceIn(0L, durationMs)
        val fileByte = fileSize * clamped / durationMs
        return ((fileTorrentOffset + fileByte) / pieceLength).toInt()
            .coerceIn(firstPiece, lastPiece)
    }

    fun timeForPiece(piece: Int): Long {
        if (durationMs <= 0L || pieceLength <= 0 || fileSize <= 0L) return 0L
        val fileByte = (piece.toLong() * pieceLength - fileTorrentOffset)
            .coerceAtLeast(0L)
        return (fileByte * durationMs / fileSize).coerceIn(0L, durationMs)
    }

    fun pieceForOffset(offset: Long): Int {
        if (pieceLength <= 0) return firstPiece
        val clamped = offset.coerceIn(0L, fileSize)
        return ((fileTorrentOffset + clamped) / pieceLength).toInt()
            .coerceIn(firstPiece, lastPiece)
    }
}

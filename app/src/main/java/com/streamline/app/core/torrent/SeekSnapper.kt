package com.streamline.app.core.torrent

import com.streamline.app.core.logging.AppLog
import kotlin.math.abs

data class SeekSnapResult(
    val requestedMs: Long,
    val playbackMs: Long,
    val requestedPiece: Int,
    val playbackPiece: Int,
    val reason: String,
) {
    val snapped: Boolean get() = playbackMs != requestedMs
}








object SeekSnapper {
    private const val MIN_RUN_PIECES = 3
    private const val DEFAULT_WINDOW_PIECES = 2
    private const val MAX_WINDOW_PIECES = 4
    fun snap(
        playback: TorrentPlayback,
        requestedMs: Long,
        durationMs: Long,
        windowPieces: Int = DEFAULT_WINDOW_PIECES,
    ): SeekSnapResult {
        val first = playback.firstPiece
        val last = playback.lastPiece
        if (durationMs <= 0L || playback.pieceLength <= 0 || last < first || requestedMs <= 0L) {
            return SeekSnapResult(requestedMs, requestedMs, -1, -1, "not_ready")
        }
        val clamped = requestedMs.coerceIn(0L, durationMs)
        val timeline = playback.video.timeline(durationMs)
        val target = timeline.pieceForTime(clamped)
        val targetRun = cachedRunLength(playback, target, last)
        if (targetRun >= MIN_RUN_PIECES) {
            return SeekSnapResult(clamped, clamped, target, target, "already_contiguous")
        }

        val radius = windowPieces.coerceIn(1, MAX_WINDOW_PIECES)
        val candidates = (target - radius..target + radius)
            .filter { it in first..last }
            .mapNotNull { piece ->
                val runLength = cachedRunLength(playback, piece, last)
                if (runLength < MIN_RUN_PIECES) return@mapNotNull null
                val startMs = timeline.timeForPiece(piece)
                Candidate(piece, startMs, runLength)
            }

        
        
        
        val backward = candidates.filter { it.piece <= target }
        val best = (backward.ifEmpty { candidates })
            .sortedWith(
                compareByDescending<Candidate> { it.runLength }
                    .thenBy { abs(it.piece - target) },
            )
            .firstOrNull()
            ?: return SeekSnapResult(clamped, clamped, target, target, "no_cached_nearby")

        val reason = when {
            best.piece < target -> "rewind_to_cached"
            best.piece > target -> "advance_to_cached"
            else -> "snap_to_piece_start"
        }
        val result = SeekSnapResult(
            requestedMs = clamped,
            playbackMs = best.startMs,
            requestedPiece = target,
            playbackPiece = best.piece,
            reason = reason,
        )
        AppLog.event(
            "SEEK",
            "snap",
            fields = mapOf(
                "fromMs" to clamped,
                "toMs" to best.startMs,
                "fromPiece" to target,
                "toPiece" to best.piece,
                "reason" to reason,
                "runLength" to best.runLength,
                "windowPieces" to radius,
            ),
        )
        return result
    }

    private fun cachedRunLength(
        playback: TorrentPlayback,
        startPiece: Int,
        lastPiece: Int,
    ): Int {
        var count = 0
        var piece = startPiece
        while (piece <= lastPiece && count < MIN_RUN_PIECES) {
            if (!runCatching { playback.handle.havePiece(piece) }.getOrDefault(false)) break
            count++
            piece++
        }
        return count
    }

    private data class Candidate(
        val piece: Int,
        val startMs: Long,
        val runLength: Int,
    )
}

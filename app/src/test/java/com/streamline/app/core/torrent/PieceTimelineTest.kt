package com.streamline.app.core.torrent

import org.junit.Assert.assertEquals
import org.junit.Test

class PieceTimelineTest {
    private val timeline = PieceTimeline(
        fileSize = 2_000_000L,
        durationMs = 100_000L,
        pieceLength = 100_000,
        fileTorrentOffset = 250_000L,
        firstPiece = 2,
        lastPiece = 21,
    )

    @Test
    fun timeMapsIntoOffsetPieceRange() {
        assertEquals(2, timeline.pieceForTime(0L))
        assertEquals(12, timeline.pieceForTime(50_000L))
        assertEquals(21, timeline.pieceForTime(100_000L))
    }

    @Test
    fun pieceStartMapsBackToTime() {
        assertEquals(2, timeline.pieceForTime(0L))
        assertEquals(0L, timeline.timeForPiece(2))
        assertEquals(2_500L, timeline.timeForPiece(3))
    }

    @Test
    fun offsetUsesPieceBoundaries() {
        assertEquals(2, timeline.pieceForOffset(0L))
        assertEquals(3, timeline.pieceForOffset(50_001L))
        assertEquals(21, timeline.pieceForOffset(2_000_000L))
    }

    @Test
    fun invalidTimelineFallsBackSafely() {
        val empty = timeline.copy(durationMs = 0L)
        assertEquals(2, empty.pieceForTime(50_000L))
        assertEquals(0L, empty.timeForPiece(3))
    }
}

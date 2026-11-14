package com.streamline.app.core.torrent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class TorrentPieceWaiterTest {
    @Test
    fun `signal makes an awaited piece ready`() {
        val waiter = TorrentPieceWaiter()
        waiter.signal(7)
        assertTrue(waiter.await(7, 1_000L))
    }

    @Test
    fun `cancel releases a blocked waiter`() {
        val waiter = TorrentPieceWaiter()
        val entered = CountDownLatch(1)
        val result = arrayOf(true)
        val thread = Thread {
            entered.countDown()
            result[0] = waiter.await(99, 30_000L)
        }.apply { start() }
        assertTrue(entered.await(1, TimeUnit.SECONDS))
        waiter.cancel()
        thread.join(2_000L)
        assertFalse(thread.isAlive)
        assertFalse(result[0])
    }
}

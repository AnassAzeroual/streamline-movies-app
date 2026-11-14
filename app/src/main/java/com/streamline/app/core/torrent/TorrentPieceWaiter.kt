package com.streamline.app.core.torrent

/** Small event bridge from libtorrent piece alerts to the blocking Media3 data source. */
class TorrentPieceWaiter {
    private val lock = Object()
    private val completed = HashSet<Int>()
    @Volatile private var cancelled = false

    fun cancel() {
        synchronized(lock) {
            cancelled = true
            lock.notifyAll()
        }
    }

    fun isCancelled(): Boolean = cancelled

    fun signal(piece: Int) {
        synchronized(lock) {
            completed += piece
            lock.notifyAll()
        }
    }

    fun await(piece: Int, timeoutMillis: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis.coerceAtLeast(1L)
        synchronized(lock) {
            while (piece !in completed) {
                if (cancelled) return false
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0L) return false
                runCatching { lock.wait(remaining) }
            }
            return !cancelled
        }
    }
}

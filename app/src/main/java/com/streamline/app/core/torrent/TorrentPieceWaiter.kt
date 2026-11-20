package com.streamline.app.core.torrent


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

    
    fun wake() {
        synchronized(lock) {
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
                try {
                    lock.wait(remaining)
                } catch (interrupted: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return false
                }
            }
            return !cancelled
        }
    }
}

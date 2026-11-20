package com.streamline.app.core.tv

import android.view.KeyEvent

object TvRemotePlayback {
    class Token internal constructor(internal val handler: (KeyEvent) -> Boolean)

    @Volatile
    private var current: Token? = null

    fun install(handler: (KeyEvent) -> Boolean): Token {
        val token = Token(handler)
        current = token
        return token
    }

    fun clear(token: Token) {
        if (current === token) current = null
    }

    fun dispatch(event: KeyEvent): Boolean = current?.handler?.invoke(event) == true
}

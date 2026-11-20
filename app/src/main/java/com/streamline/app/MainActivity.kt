package com.streamline.app

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.streamline.app.core.designsystem.StreamlineTheme
import com.streamline.app.core.logging.AppLog
import com.streamline.app.core.tv.TvRemotePlayback
import com.streamline.app.domain.model.PlaybackRequest
import com.streamline.app.navigation.StreamlineNavHost
import java.net.URLDecoder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        AppLog.event("LIFECYCLE", "onCreate")
        handleMagnetIntent(intent)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        AppLog.event("SCREEN", "open", "MainActivity")
        setContent {
            val app = application as StreamlineApplication
            StreamlineTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val startedAt = SystemClock.elapsedRealtime()
                                val up = waitForUpOrCancellation()
                                val endedAt = SystemClock.elapsedRealtime()
                                val end = up?.position ?: down.position
                                val dx = end.x - down.position.x
                                val dy = end.y - down.position.y
                                AppLog.event(
                                    category = "UI",
                                    event = if (kotlin.math.abs(dx) + kotlin.math.abs(dy) > 24f) {
                                        "swipe"
                                    } else {
                                        "tap"
                                    },
                                    fields = mapOf(
                                        "x" to down.position.x.toInt(),
                                        "y" to down.position.y.toInt(),
                                        "dx" to dx.toInt(),
                                        "dy" to dy.toInt(),
                                        "durationMs" to (endedAt - startedAt),
                                    ),
                                )
                            }
                        },
                ) {
                    StreamlineNavHost(container = app.container)
                }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (BuildConfig.DEBUG && event.action == KeyEvent.ACTION_DOWN) {
            AppLog.event(
                "TV",
                "dispatch_key",
                fields = mapOf("keyCode" to event.keyCode, "repeat" to event.repeatCount),
            )
        }
        val handledByPlayer = TvRemotePlayback.dispatch(event)
        if (handledByPlayer) {
            AppLog.event(
                "TV",
                "remote_key",
                fields = mapOf(
                    "keyCode" to event.keyCode,
                    "action" to event.action,
                    "repeat" to event.repeatCount,
                ),
            )
        }
        if (handledByPlayer) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleMagnetIntent(intent)
    }

    private fun handleMagnetIntent(intent: Intent?) {
        val uri: Uri = intent?.data ?: return
        if (!uri.scheme.equals("magnet", ignoreCase = true)) return
        val magnet = uri.toString()
        val hash = Regex("(?i)btih:([a-z0-9]{32,40})")
            .find(magnet)
            ?.groupValues
            ?.getOrNull(1)
        val title = magnetQueryParameter(magnet, "dn")
            ?.takeIf { it.isNotBlank() }
            ?: "Magnet stream"
        val request = PlaybackRequest(
            id = hash ?: magnet,
            title = title,
            magnetUrl = magnet,
        )
        (application as StreamlineApplication).container.pendingMagnet.value = request
        AppLog.event(
            "PLAYBACK",
            "magnet_intent",
            fields = mapOf("title" to title, "hash" to hash),
        )
        intent.data = null
    }

    private fun magnetQueryParameter(magnet: String, key: String): String? {
        val query = magnet.substringAfter('?', "")
        if (query.isBlank()) return null
        return query.split('&')
            .firstOrNull { it.substringBefore('=') == key }
            ?.substringAfter('=', "")
            ?.takeIf { it.isNotBlank() }
            ?.let { value ->
                runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
            }
    }

    override fun onStart() {
        super.onStart()
        AppLog.event("LIFECYCLE", "onStart")
    }

    override fun onResume() {
        super.onResume()
        AppLog.event("LIFECYCLE", "onResume")
    }

    override fun onPause() {
        AppLog.event("LIFECYCLE", "onPause")
        super.onPause()
    }

    override fun onStop() {
        AppLog.event("LIFECYCLE", "onStop")
        super.onStop()
    }

    override fun onDestroy() {
        AppLog.event("LIFECYCLE", "onDestroy")
        super.onDestroy()
    }
}

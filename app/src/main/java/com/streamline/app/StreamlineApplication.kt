package com.streamline.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.streamline.app.core.di.AppContainer
import com.streamline.app.core.logging.AppLog

class StreamlineApplication : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        AppLog.init(this)
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            AppLog.exception(
                category = "CRASH",
                event = "uncaught_exception",
                throwable = throwable,
                fields = mapOf("thread" to thread.name),
            )
            AppLog.flush()
            previousHandler?.uncaughtException(thread, throwable)
        }
        container = AppContainer(this)
        AppLog.event("APP", "application_created")
    }

    override fun onTerminate() {
        AppLog.event("APP", "application_terminated")
        AppLog.flush()
        container.shutdown()
        super.onTerminate()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory())
            }
            .crossfade(true)
            .build()
}

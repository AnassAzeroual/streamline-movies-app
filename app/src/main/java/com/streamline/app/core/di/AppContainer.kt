package com.streamline.app.core.di

import android.content.Context
import com.streamline.app.core.download.TorrentDownloadManager
import com.streamline.app.core.playback.FavoriteStore
import com.streamline.app.core.playback.PlaybackHistoryStore
import com.streamline.app.core.playback.PlaybackPositionStore
import com.streamline.app.core.logging.AppLog
import com.streamline.app.core.settings.ProxyType
import com.streamline.app.core.settings.SettingsRepository
import com.streamline.app.core.torrent.TorrentStreamEngine
import com.streamline.app.data.DefaultSettingsRepository
import com.streamline.app.data.magnet.BtdigMagnetSource
import com.streamline.app.data.magnet.DefaultMagnetCatalogRepository
import com.streamline.app.data.magnet.KickassMagnetSource
import com.streamline.app.data.magnet.LimeTorrentsMagnetSource
import com.streamline.app.data.magnet.KnabenMagnetSource
import com.streamline.app.data.magnet.NyaaMagnetSource
import com.streamline.app.data.magnet.PirateBayMagnetSource
import com.streamline.app.data.magnet.TorrentsCsvMagnetSource
import com.streamline.app.data.magnet.X1337MagnetSource
import com.streamline.app.data.magnet.YtsMagnetSource
import com.streamline.app.data.playback.DefaultPlaybackSessionRepository
import com.streamline.app.domain.model.MagnetSource
import com.streamline.app.domain.model.PlaybackRequest
import kotlinx.coroutines.flow.MutableStateFlow
import com.streamline.app.domain.repository.MagnetCatalogRepository
import com.streamline.app.domain.repository.PlaybackSessionRepository
import okhttp3.Credentials
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.URI
import java.util.concurrent.TimeUnit





class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val settingsRepository: SettingsRepository = DefaultSettingsRepository(appContext)

    
    val playbackSessionRepository: PlaybackSessionRepository = DefaultPlaybackSessionRepository()

    private val providerProxySelector = object : ProxySelector() {
        override fun select(uri: URI?): List<Proxy> {
            val proxy = settingsRepository.settings.value.proxy
            if (!proxy.isConfigured) return listOf(Proxy.NO_PROXY)
            val type = if (proxy.type == ProxyType.SOCKS5) {
                Proxy.Type.SOCKS
            } else {
                Proxy.Type.HTTP
            }
            return listOf(Proxy(type, InetSocketAddress(proxy.host.trim(), proxy.port)))
        }

        override fun connectFailed(uri: URI?, sa: java.net.SocketAddress?, ioe: java.io.IOException?) {
            AppLog.warn(
                "PROXY",
                "connect_failed",
                fields = mapOf("uri" to uri?.host, "error" to ioe?.message),
            )
        }
    }

    private val archiveClient = OkHttpClient.Builder()
        .proxySelector(providerProxySelector)
        .proxyAuthenticator { _, response ->
            val proxy = settingsRepository.settings.value.proxy
            if (
                !proxy.isConfigured ||
                proxy.username.isBlank() ||
                response.request.header("Proxy-Authorization") != null
            ) {
                null
            } else {
                response.request.newBuilder()
                    .header(
                        "Proxy-Authorization",
                        Credentials.basic(proxy.username, proxy.password),
                    )
                    .build()
            }
        }
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(18, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 14; Streamline/1.0) " +
                            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36",
                    )
                    .build(),
            )
        }
        .build()

    
    val magnetCatalogRepository: MagnetCatalogRepository = DefaultMagnetCatalogRepository(
        providers = mapOf(
            MagnetSource.YTS to YtsMagnetSource(archiveClient),
            MagnetSource.X1337 to X1337MagnetSource(archiveClient),
            MagnetSource.TPB to PirateBayMagnetSource(archiveClient),
            MagnetSource.TORRENTS_CSV to TorrentsCsvMagnetSource(archiveClient),
            MagnetSource.KNABEN to KnabenMagnetSource(archiveClient),
            MagnetSource.NYAA to NyaaMagnetSource(archiveClient),
            MagnetSource.BTDIGG to BtdigMagnetSource(archiveClient),
            MagnetSource.KICKASS to KickassMagnetSource(archiveClient),
            MagnetSource.LIME to LimeTorrentsMagnetSource(archiveClient),
        ),
    )

    val artworkAccentLoader = ArtworkAccentLoader(
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build(),
    )

    
    val downloadManager = TorrentDownloadManager(appContext)

    



    val torrentEngine = TorrentStreamEngine(appContext).apply {
        onStreamingStateChanged = { active -> downloadManager.setStreamingThrottle(active) }
        warmUp()
    }

    val playbackPositionStore = PlaybackPositionStore(appContext)
    val playbackHistoryStore = PlaybackHistoryStore(appContext)
    val favoriteStore = FavoriteStore(appContext)
    val pendingMagnet = MutableStateFlow<PlaybackRequest?>(null)

    fun shutdown() {
        torrentEngine.stop()
        downloadManager.shutdown()
    }
}

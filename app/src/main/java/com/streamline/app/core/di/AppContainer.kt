package com.streamline.app.core.di

import android.content.Context
import com.streamline.app.core.download.TorrentDownloadManager
import com.streamline.app.core.playback.PlaybackPositionStore
import com.streamline.app.core.settings.SettingsRepository
import com.streamline.app.core.torrent.TorrentStreamEngine
import com.streamline.app.data.DefaultSettingsRepository
import com.streamline.app.data.magnet.BtdigMagnetSource
import com.streamline.app.data.magnet.DefaultMagnetCatalogRepository
import com.streamline.app.data.magnet.KickassMagnetSource
import com.streamline.app.data.magnet.KnabenMagnetSource
import com.streamline.app.data.magnet.NyaaMagnetSource
import com.streamline.app.data.magnet.PirateBayMagnetSource
import com.streamline.app.data.magnet.TorrentsCsvMagnetSource
import com.streamline.app.data.magnet.X1337MagnetSource
import com.streamline.app.data.magnet.YtsMagnetSource
import com.streamline.app.data.playback.DefaultPlaybackSessionRepository
import com.streamline.app.domain.model.MagnetSource
import com.streamline.app.domain.repository.MagnetCatalogRepository
import com.streamline.app.domain.repository.PlaybackSessionRepository
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Manual dependency container. Keeping it explicit makes the graph easy to audit and keeps the
 * build free of annotation processors.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val settingsRepository: SettingsRepository = DefaultSettingsRepository(appContext)

    /** Transient archive → player handoff; never persisted as a movie source. */
    val playbackSessionRepository: PlaybackSessionRepository = DefaultPlaybackSessionRepository()

    private val archiveClient = OkHttpClient.Builder()
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

    /** Metadata + magnet links only. Playback is a separate, explicit player handoff. */
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
        ),
    )

    val artworkAccentLoader = ArtworkAccentLoader(
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build(),
    )

    /** Complete-file torrent downloads; separate session from playback streaming. */
    val downloadManager = TorrentDownloadManager(appContext)

    /**
     * Playback yields nothing to the download queue: while a stream is active the download session
     * is rate-limited so piece fetching for the player wins the shared uplink/downlink.
     */
    val torrentEngine = TorrentStreamEngine(appContext).apply {
        onStreamingStateChanged = { active -> downloadManager.setStreamingThrottle(active) }
        warmUp()
    }

    val playbackPositionStore = PlaybackPositionStore(appContext)

    fun shutdown() {
        torrentEngine.stop()
        downloadManager.shutdown()
    }
}

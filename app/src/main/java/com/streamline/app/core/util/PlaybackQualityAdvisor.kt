package com.streamline.app.core.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.streamline.app.domain.model.MagnetRelease
import com.streamline.app.domain.model.VideoQuality

/** Reads the physical screen and the active network, then applies [QualityRules]. */
object PlaybackQualityAdvisor {
    fun screenQuality(context: Context): VideoQuality {
        val metrics = context.resources.displayMetrics
        return QualityRules.forScreenShortSide(minOf(metrics.widthPixels, metrics.heightPixels))
    }

    fun networkQuality(context: Context): VideoQuality? {
        val capabilities = networkCapabilities(context) ?: return null
        if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return null
        return QualityRules.forBandwidthKbps(capabilities.linkDownstreamBandwidthKbps)
    }

    fun transportLabel(context: Context): String {
        val capabilities = networkCapabilities(context) ?: return "Network"
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile data"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Network"
        }
    }

    fun recommend(context: Context, releases: List<MagnetRelease>): MagnetRelease? =
        QualityRules.choose(
            releases = releases,
            screen = screenQuality(context),
            network = networkQuality(context),
        )

    private fun networkCapabilities(context: Context): NetworkCapabilities? {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val network = manager.activeNetwork ?: return null
        return manager.getNetworkCapabilities(network)
    }
}

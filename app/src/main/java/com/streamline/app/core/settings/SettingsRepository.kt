package com.streamline.app.core.settings

import kotlinx.coroutines.flow.StateFlow

enum class ProxyType {
    HTTP,
    SOCKS5,
}

data class ProxySettings(
    val enabled: Boolean = false,
    val type: ProxyType = ProxyType.HTTP,
    val host: String = "",
    val port: Int = 8080,
    val username: String = "",
    val password: String = "",
) {
    val isConfigured: Boolean
        get() = enabled && host.isNotBlank() && port in 1..65535
}

data class AppSettings(
    val hardwareDecoding: Boolean = true,
    val showStreamStats: Boolean = false,
    val hideAdultContent: Boolean = true,
    val proxy: ProxySettings = ProxySettings(),
)

interface SettingsRepository {
    val settings: StateFlow<AppSettings>

    fun setHardwareDecodingEnabled(enabled: Boolean)

    fun setStreamStatsEnabled(enabled: Boolean)

    fun setHideAdultContent(hidden: Boolean)

    fun setProxySettings(proxy: ProxySettings)
}

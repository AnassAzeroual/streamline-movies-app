package com.streamline.app.data

import android.content.Context
import com.streamline.app.core.logging.AppLog
import com.streamline.app.core.settings.AppSettings
import com.streamline.app.core.settings.ProxySettings
import com.streamline.app.core.settings.ProxyType
import com.streamline.app.core.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class DefaultSettingsRepository(context: Context) : SettingsRepository {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val proxySecretStore = ProxySecretStore()
    private val _settings = MutableStateFlow(
        AppSettings(
            hardwareDecoding = preferences.getBoolean(KEY_HARDWARE_DECODING, true),
            showStreamStats = preferences.getBoolean(KEY_SHOW_STREAM_STATS, false),
            hideAdultContent = preferences.getBoolean(KEY_HIDE_ADULT, true),
            proxy = ProxySettings(
                enabled = preferences.getBoolean(KEY_PROXY_ENABLED, false),
                type = runCatching {
                    ProxyType.valueOf(
                        preferences.getString(KEY_PROXY_TYPE, ProxyType.HTTP.name)
                            ?: ProxyType.HTTP.name,
                    )
                }.getOrDefault(ProxyType.HTTP),
                host = preferences.getString(KEY_PROXY_HOST, "").orEmpty(),
                port = preferences.getInt(KEY_PROXY_PORT, 8080),
                username = preferences.getString(KEY_PROXY_USER, "").orEmpty(),
                password = proxySecretStore.decrypt(
                    preferences.getString(KEY_PROXY_PASSWORD, null),
                ),
            ),
        ),
    )
    override val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    override fun setHardwareDecodingEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_HARDWARE_DECODING, enabled).apply()
        _settings.update { it.copy(hardwareDecoding = enabled) }
    }

    override fun setStreamStatsEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_SHOW_STREAM_STATS, enabled).apply()
        _settings.update { it.copy(showStreamStats = enabled) }
    }

    override fun setHideAdultContent(hidden: Boolean) {
        preferences.edit().putBoolean(KEY_HIDE_ADULT, hidden).apply()
        _settings.update { it.copy(hideAdultContent = hidden) }
    }

    override fun setProxySettings(proxy: ProxySettings) {
        val editor = preferences.edit()
            .putBoolean(KEY_PROXY_ENABLED, proxy.enabled)
            .putString(KEY_PROXY_TYPE, proxy.type.name)
            .putString(KEY_PROXY_HOST, proxy.host.trim())
            .putInt(KEY_PROXY_PORT, proxy.port)
            .putString(KEY_PROXY_USER, proxy.username.trim())
        val encrypted = proxySecretStore.encrypt(proxy.password)
        if (encrypted == null) {
            editor.remove(KEY_PROXY_PASSWORD)
        } else {
            editor.putString(KEY_PROXY_PASSWORD, encrypted)
        }
        editor.apply()
        _settings.update { it.copy(proxy = proxy) }
        AppLog.event(
            "PROXY",
            "configured",
            fields = mapOf(
                "enabled" to proxy.enabled,
                "type" to proxy.type.name,
                "host" to proxy.host,
                "port" to proxy.port,
                "authenticated" to proxy.username.isNotBlank(),
            ),
        )
    }

    private companion object {
        const val PREFS_NAME = "streamline_settings"
        const val KEY_HARDWARE_DECODING = "hardware_decoding"
        const val KEY_SHOW_STREAM_STATS = "show_stream_stats"
        const val KEY_HIDE_ADULT = "hide_adult_content"
        const val KEY_PROXY_ENABLED = "proxy_enabled"
        const val KEY_PROXY_TYPE = "proxy_type"
        const val KEY_PROXY_HOST = "proxy_host"
        const val KEY_PROXY_PORT = "proxy_port"
        const val KEY_PROXY_USER = "proxy_user"
        const val KEY_PROXY_PASSWORD = "proxy_password_encrypted"
    }
}

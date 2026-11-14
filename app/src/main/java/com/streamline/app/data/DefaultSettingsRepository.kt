package com.streamline.app.data

import android.content.Context
import com.streamline.app.core.settings.AppSettings
import com.streamline.app.core.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class DefaultSettingsRepository(context: Context) : SettingsRepository {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(
        AppSettings(
            hardwareDecoding = preferences.getBoolean(KEY_HARDWARE_DECODING, true),
            showStreamStats = preferences.getBoolean(KEY_SHOW_STREAM_STATS, false),
            hideAdultContent = preferences.getBoolean(KEY_HIDE_ADULT, true),
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

    private companion object {
        const val PREFS_NAME = "streamline_settings"
        const val KEY_HARDWARE_DECODING = "hardware_decoding"
        const val KEY_SHOW_STREAM_STATS = "show_stream_stats"
        const val KEY_HIDE_ADULT = "hide_adult_content"
    }
}

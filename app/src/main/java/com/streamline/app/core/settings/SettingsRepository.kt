package com.streamline.app.core.settings

import kotlinx.coroutines.flow.StateFlow

data class AppSettings(
    val hardwareDecoding: Boolean = true,
    val showStreamStats: Boolean = false,
    val hideAdultContent: Boolean = true,
)

interface SettingsRepository {
    val settings: StateFlow<AppSettings>

    fun setHardwareDecodingEnabled(enabled: Boolean)

    fun setStreamStatsEnabled(enabled: Boolean)

    fun setHideAdultContent(hidden: Boolean)
}

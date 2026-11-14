package com.streamline.app.core.designsystem

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun isTelevision(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            (context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) ==
            Configuration.UI_MODE_TYPE_TELEVISION
    }
}

fun tvEdgePadding(isTv: Boolean): Dp = if (isTv) 48.dp else 18.dp

fun tvBarPadding(isTv: Boolean): Dp = if (isTv) 48.dp else 20.dp

fun tvTopPadding(isTv: Boolean): Dp = if (isTv) 28.dp else 9.dp

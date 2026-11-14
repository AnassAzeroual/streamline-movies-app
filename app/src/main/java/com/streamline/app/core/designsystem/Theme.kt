package com.streamline.app.core.designsystem

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val LocalArtworkAccent = compositionLocalOf { StreamlineColors.Gold }

private val StreamlineColorScheme = darkColorScheme(
    primary = StreamlineColors.Gold,
    onPrimary = StreamlineColors.Ink,
    primaryContainer = StreamlineColors.GoldDeep,
    onPrimaryContainer = StreamlineColors.Ink,
    secondary = StreamlineColors.Ice,
    onSecondary = StreamlineColors.Ink,
    tertiary = StreamlineColors.Coral,
    onTertiary = StreamlineColors.Ink,
    background = StreamlineColors.Ink,
    onBackground = StreamlineColors.TextPrimary,
    surface = StreamlineColors.Surface,
    onSurface = StreamlineColors.TextPrimary,
    surfaceVariant = StreamlineColors.SurfaceRaised,
    onSurfaceVariant = StreamlineColors.TextSecondary,
    surfaceContainer = StreamlineColors.Surface,
    surfaceContainerHigh = StreamlineColors.SurfaceRaised,
    outline = StreamlineColors.Hairline,
    outlineVariant = StreamlineColors.HairlineSoft,
    error = StreamlineColors.Danger,
    onError = StreamlineColors.Ink,
    scrim = Color.Black,
)

@Composable
fun StreamlineTheme(
    accent: Color = StreamlineColors.Gold,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = StreamlineColorScheme,
        typography = StreamlineTypography,
    ) {
        CompositionLocalProvider(LocalArtworkAccent provides accent) {
            content()
        }
    }
}

/** Full-screen OLED canvas with a subtle warm bloom behind the top of the content. */
@Composable
fun CinematicBackground(
    modifier: Modifier = Modifier,
    accent: Color = LocalArtworkAccent.current,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to StreamlineColors.InkSoft,
                    0.35f to StreamlineColors.Ink,
                    1f to Color(0xFF050506),
                ),
            ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.055f), Color.Transparent),
                        radius = 1100f,
                    ),
                ),
        )
        content()
    }
}

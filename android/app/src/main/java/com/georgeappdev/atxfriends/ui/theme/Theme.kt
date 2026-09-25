package com.georgeappdev.atxfriends.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LocalAtxColors = staticCompositionLocalOf { LightAtxColors }

private fun AtxColors.toColorScheme(dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = appPrimary,
        onPrimary = Color.White,
        secondary = appNavy,
        onSecondary = Color.White,
        background = appBackground,
        onBackground = primaryText,
        surface = appBackground,
        onSurface = primaryText,
        surfaceVariant = cardBackground,
        onSurfaceVariant = secondaryText,
        surfaceContainerLowest = cardBackground,
        surfaceContainerLow = cardBackground,
        surfaceContainer = cardBackground,
        surfaceContainerHigh = cardBackground,
        surfaceContainerHighest = cardBackground,
        outline = border,
        outlineVariant = border,
        error = danger,
        onError = Color.White,
    )
}

/**
 * Follows the system Light/Dark setting — never forced. Dynamic (wallpaper) color is
 * intentionally not used so the brand palette matches iOS.
 */
@Composable
fun AtxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkAtxColors else LightAtxColors
    CompositionLocalProvider(LocalAtxColors provides colors) {
        MaterialTheme(
            colorScheme = colors.toColorScheme(darkTheme),
            typography = AtxTypography,
            shapes = AtxShapes,
            content = content,
        )
    }
}

/** Access to the full iOS token set, e.g. `AtxTheme.colors.textMuted`. */
object AtxTheme {
    val colors: AtxColors
        @Composable @ReadOnlyComposable
        get() = LocalAtxColors.current
}

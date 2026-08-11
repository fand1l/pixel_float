package dev.fand1l.pixelfloat.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Theme for the island itself. Dynamic colour is derived from the wallpaper via the
 * window context. Later this moves behind ColorSchemeProvider, which also has to react to
 * wallpaper and dark-mode changes — the service is never recreated for those, so a scheme
 * computed once at start would stay stale forever.
 */
@Composable
fun IslandTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colorScheme = if (isSystemInDarkTheme()) {
        dynamicDarkColorScheme(context)
    } else {
        dynamicLightColorScheme(context)
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

/** Theme for the configuration app. */
@Composable
fun PixelFloatTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colorScheme = if (isSystemInDarkTheme()) {
        dynamicDarkColorScheme(context)
    } else {
        dynamicLightColorScheme(context)
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

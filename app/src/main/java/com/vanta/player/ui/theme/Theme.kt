package com.vanta.player.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Section 2: Premium dark interface: background #0F0F0F, charcoal surfaces, off-white text,
// muted secondary text, default red accent #EF454F.
data class VantaColors(
    val background: Color = Color(0xFF0F0F0F),
    val surfaceCharcoal: Color = Color(0xFF181818),
    val surfaceElevated: Color = Color(0xFF222222),
    val textPrimary: Color = Color(0xFFF3F3F4),
    val textSecondary: Color = Color(0xFF9A9A9E),
    val accent: Color = Color(0xFFEF454F)
)

val LocalVantaColors = staticCompositionLocalOf { VantaColors() }

@Composable
fun VantaTheme(
    accentColor: Color = Color(0xFFEF454F),
    content: @Composable () -> Unit
) {
    val colorScheme = darkColorScheme(
        primary = accentColor,
        secondary = accentColor,
        background = Color(0xFF0F0F0F),
        surface = Color(0xFF181818),
        onPrimary = Color(0xFF0F0F0F),
        onSecondary = Color.White,
        onBackground = Color(0xFFF3F3F4),
        onSurface = Color(0xFFF3F3F4),
        surfaceVariant = Color(0xFF222222),
        onSurfaceVariant = Color(0xFF9A9A9E)
    )

    val vantaColors = VantaColors(
        background = Color(0xFF0F0F0F),
        surfaceCharcoal = Color(0xFF181818),
        surfaceElevated = Color(0xFF222222),
        textPrimary = Color(0xFFF3F3F4),
        textSecondary = Color(0xFF9A9A9E),
        accent = accentColor
    )

    CompositionLocalProvider(LocalVantaColors provides vantaColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}

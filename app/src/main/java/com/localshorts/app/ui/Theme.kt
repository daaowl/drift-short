package com.localshorts.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.localshorts.app.R

object Hc {
    val Bg = Color(0xFF15171A)
    val Surface = Color(0xFF22262B)
    val Surface2 = Color(0xFF2E3338)
    val Ink = Color(0xFFE8E6E1)
    val Mute = Color(0xFF9AA0A4)
}

val LocalAccent = staticCompositionLocalOf { Color(0xFFE5484D) }

fun onAccent(accent: Color): Color = if (accent.luminance() > 0.45f) Hc.Bg else Color.White

@Composable
fun HollowTheme(accent: Color, content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = accent,
        onPrimary = onAccent(accent),
        background = Hc.Bg,
        onBackground = Hc.Ink,
        surface = Hc.Surface,
        onSurface = Hc.Ink,
        surfaceVariant = Hc.Surface2,
        onSurfaceVariant = Hc.Mute,
    )
    CompositionLocalProvider(LocalAccent provides accent) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/** In-app logo for each launcher icon choice. */
fun logoRes(iconId: String): Int = when (iconId) {
    "stack" -> R.drawable.logo_stack
    "orbit" -> R.drawable.logo_orbit
    "mono" -> R.drawable.logo_mono
    "prism" -> R.drawable.logo_prism
    else -> R.drawable.logo_hollow
}

/** Text shadow for labels drawn straight over video. */
val VideoTextShadow = androidx.compose.ui.graphics.Shadow(
    color = Color(0x99000000),
    offset = androidx.compose.ui.geometry.Offset(0f, 1.5f),
    blurRadius = 6f,
)

package com.localshorts.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import com.localshorts.app.data.Backdrop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.sin

/** Colours for the full-screen mesh: a base wash plus five large soft blobs. */
data class MeshPalette(val base: Color, val blobs: List<Color>)

val RavePalette = MeshPalette(
    base = Color(0xFF281C50),
    blobs = listOf(
        Color(0xFFC24A88), // dusty rose, top
        Color(0xFF8A2EC8), // violet, upper right
        Color(0xFF3A62CC), // cobalt, middle left
        Color(0xFF6E2CA6), // plum, right
        Color(0xFF94582E), // smoky amber, bottom left
        Color(0xFF4436B4), // indigo, bottom right
    ),
)

val SunsetPalette = MeshPalette(
    base = Color(0xFF3A1430),
    blobs = listOf(Color(0xFFD8604A), Color(0xFFB82E6A), Color(0xFF7A2C8A), Color(0xFFE08A4A), Color(0xFFC8486A), Color(0xFF5A2A86)),
)

val OceanPalette = MeshPalette(
    base = Color(0xFF0A1C38),
    blobs = listOf(Color(0xFF1E86B4), Color(0xFF2C4EC2), Color(0xFF15948C), Color(0xFF3A3CA4), Color(0xFF0E6488), Color(0xFF4A6CD0)),
)

val AuroraPalette = MeshPalette(
    base = Color(0xFF0A1C22),
    blobs = listOf(Color(0xFF26A87C), Color(0xFF6A4CC8), Color(0xFF1C8AA2), Color(0xFF3C9C6C), Color(0xFF8A44B4), Color(0xFF2A6CC4)),
)

val EmberPalette = MeshPalette(
    base = Color(0xFF1E0C08),
    blobs = listOf(Color(0xFFC4452C), Color(0xFFD27A2A), Color(0xFF8E2622), Color(0xFFB0542A), Color(0xFF6E2216), Color(0xFFB8863A)),
)

fun paletteFor(b: Backdrop): MeshPalette? = when (b) {
    Backdrop.RAVE -> RavePalette
    Backdrop.SUNSET -> SunsetPalette
    Backdrop.OCEAN -> OceanPalette
    Backdrop.AURORA -> AuroraPalette
    Backdrop.EMBER -> EmberPalette
    Backdrop.NONE -> null
}

/** The chosen backdrop filling its parent, optionally dimmed (for busy screens like Settings). */
@Composable
fun HollowBackdrop(backdrop: Backdrop, modifier: Modifier = Modifier, dim: Float = 0f, animate: Boolean = true) {
    val palette = paletteFor(backdrop)
    Box(modifier.background(Hc.Bg)) {
        if (palette != null) {
            MeshGradient(Modifier.matchParentSize(), palette, animate)
            if (dim > 0f) Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = dim)))
        }
    }
}

/**
 * A slow-moving, full-bleed mesh gradient: big overlapping colour fields that melt into each other,
 * like the backgrounds on Rave's welcome screen. Pure drawing, no bitmaps; redraws only.
 */
@Composable
fun MeshGradient(modifier: Modifier = Modifier, palette: MeshPalette = RavePalette, animate: Boolean = true) {
    val drift = rememberInfiniteTransition(label = "mesh")
    val t by drift.animateFloat(
        0f,
        (Math.PI * 2).toFloat(),
        infiniteRepeatable(tween(if (animate) 26000 else 1, easing = LinearEasing)),
        label = "mesh-t",
    )
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRect(palette.base)
        // (x, y, radius) as fractions of the screen, each wobbling on its own slow orbit
        val spots = listOf(
            Triple(0.30f + 0.05f * cos(t), 0.04f + 0.03f * sin(t), 0.55f),
            Triple(1.00f + 0.04f * sin(t * 0.8f), 0.22f + 0.05f * cos(t * 0.7f), 0.55f),
            Triple(0.05f + 0.05f * sin(t * 0.9f), 0.50f + 0.05f * cos(t), 0.50f),
            Triple(0.95f + 0.04f * cos(t * 0.7f), 0.62f + 0.04f * sin(t * 0.9f), 0.45f),
            Triple(0.00f + 0.05f * cos(t * 0.6f), 0.98f + 0.03f * sin(t * 1.1f), 0.60f),
            Triple(1.00f + 0.04f * cos(t * 1.2f), 1.00f + 0.04f * sin(t * 0.5f), 0.55f),
        )
        spots.forEachIndexed { i, (fx, fy, fr) ->
            val c = palette.blobs[i % palette.blobs.size]
            val center = Offset(w * fx, h * fy)
            val r = maxOf(w, h) * fr
            drawCircle(
                Brush.radialGradient(
                    0f to c,
                    0.5f to c.copy(alpha = 0.75f),
                    1f to c.copy(alpha = 0f),
                    center = center,
                    radius = r,
                ),
                radius = r,
                center = center,
            )
        }
    }
}

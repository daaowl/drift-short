package com.localshorts.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
    base = Color(0xFF5B3DB0),
    blobs = listOf(
        Color(0xFFF0559E), // hot rose, top left
        Color(0xFF9B2CF0), // electric violet, right
        Color(0xFF3D74F2), // bright cobalt, middle left
        Color(0xFFE08A3C), // warm amber, bottom left
        Color(0xFF5546F0), // vivid indigo, bottom right
    ),
)

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
            Triple(0.18f + 0.06f * cos(t), 0.12f + 0.04f * sin(t), 0.85f),
            Triple(0.95f + 0.05f * sin(t * 0.8f), 0.30f + 0.06f * cos(t * 0.7f), 0.70f),
            Triple(0.10f + 0.05f * sin(t * 0.9f), 0.55f + 0.05f * cos(t), 0.65f),
            Triple(0.05f + 0.06f * cos(t * 0.6f), 0.95f + 0.03f * sin(t * 1.1f), 0.70f),
            Triple(0.90f + 0.04f * cos(t * 1.2f), 0.85f + 0.05f * sin(t * 0.5f), 0.75f),
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
        // a soft central haze ties the colours together
        drawRect(
            Brush.radialGradient(
                listOf(Color(0x40FFFFFF), Color.Transparent),
                center = Offset(w * 0.45f, h * 0.38f),
                radius = maxOf(w, h) * 0.45f,
            ),
        )
    }
}

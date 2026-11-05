package com.localshorts.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Small stroke icons that are not in the core Material icon set, drawn on a 24-unit grid. */
enum class Glyph { FOLDER, SORT, GRID, SUN, VOLUME, DOTS, IMAGE, LOCK, INFO, SPEED, REPEAT, NEXT, STOP }

@Composable
fun GlyphIcon(glyph: Glyph, tint: Color, size: Dp = 22.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val u = this.size.minDimension / 24f
        val stroke = Stroke(width = 2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun p(x: Float, y: Float) = Offset(x * u, y * u)
        when (glyph) {
            Glyph.FOLDER -> {
                val path = Path().apply {
                    moveTo(3.5f * u, 6f * u); lineTo(9.5f * u, 6f * u); lineTo(11.5f * u, 8.5f * u)
                    lineTo(20.5f * u, 8.5f * u); lineTo(20.5f * u, 18.5f * u); lineTo(3.5f * u, 18.5f * u); close()
                }
                drawPath(path, tint, style = stroke)
            }
            Glyph.SORT -> {
                drawLine(tint, p(4f, 7f), p(20f, 7f), 2f * u, StrokeCap.Round)
                drawLine(tint, p(4f, 12f), p(15f, 12f), 2f * u, StrokeCap.Round)
                drawLine(tint, p(4f, 17f), p(10f, 17f), 2f * u, StrokeCap.Round)
            }
            Glyph.GRID -> {
                for (r in 0..1) for (c in 0..1) {
                    drawRoundRect(tint, p(4f + c * 9f, 4f + r * 9f), Size(7f * u, 7f * u), CornerRadius(1.5f * u), style = stroke)
                }
            }
            Glyph.SUN -> {
                drawCircle(tint, 4f * u, p(12f, 12f), style = stroke)
                for (i in 0 until 8) {
                    val a = Math.PI * i / 4
                    val c = kotlin.math.cos(a).toFloat()
                    val s = kotlin.math.sin(a).toFloat()
                    drawLine(tint, p(12f + c * 7f, 12f + s * 7f), p(12f + c * 9f, 12f + s * 9f), 2f * u, StrokeCap.Round)
                }
            }
            Glyph.VOLUME -> {
                val path = Path().apply {
                    moveTo(4f * u, 9.5f * u); lineTo(8f * u, 9.5f * u); lineTo(13f * u, 5f * u)
                    lineTo(13f * u, 19f * u); lineTo(8f * u, 14.5f * u); lineTo(4f * u, 14.5f * u); close()
                }
                drawPath(path, tint, style = stroke)
                drawArc(tint, -50f, 100f, false, p(13f, 8f), Size(6f * u, 8f * u), style = stroke)
            }
            Glyph.DOTS -> {
                drawCircle(tint, 1.8f * u, p(12f, 5f))
                drawCircle(tint, 1.8f * u, p(12f, 12f))
                drawCircle(tint, 1.8f * u, p(12f, 19f))
            }
            Glyph.IMAGE -> {
                drawRoundRect(tint, p(4f, 5f), Size(16f * u, 14f * u), CornerRadius(2f * u), style = stroke)
                val path = Path().apply {
                    moveTo(4.5f * u, 17f * u); lineTo(9.5f * u, 11.5f * u); lineTo(13f * u, 15f * u)
                    lineTo(15.5f * u, 12.5f * u); lineTo(19.5f * u, 16.5f * u)
                }
                drawPath(path, tint, style = stroke)
                drawCircle(tint, 1.5f * u, p(15.5f, 8.8f))
            }
            Glyph.LOCK -> {
                drawRoundRect(tint, p(5f, 11f), Size(14f * u, 9f * u), CornerRadius(2f * u), style = stroke)
                drawArc(tint, 180f, 180f, false, p(8f, 4f), Size(8f * u, 10f * u), style = stroke)
                drawLine(tint, p(8f, 9f), p(8f, 11f), 2f * u, StrokeCap.Round)
                drawLine(tint, p(16f, 9f), p(16f, 11f), 2f * u, StrokeCap.Round)
            }
            Glyph.INFO -> {
                drawCircle(tint, 9f * u, p(12f, 12f), style = stroke)
                drawLine(tint, p(12f, 11f), p(12f, 16.5f), 2.2f * u, StrokeCap.Round)
                drawCircle(tint, 1.3f * u, p(12f, 7.8f))
            }
            Glyph.SPEED -> {
                drawArc(tint, 150f, 240f, false, p(3.5f, 4.5f), Size(17f * u, 17f * u), style = stroke)
                drawLine(tint, p(12f, 13f), p(16.2f, 9f), 2f * u, StrokeCap.Round)
                drawCircle(tint, 1.4f * u, p(12f, 13f))
            }
            Glyph.REPEAT -> {
                val path = Path().apply {
                    moveTo(5f * u, 11f * u); lineTo(5f * u, 9.5f * u); quadraticBezierTo(5f * u, 7f * u, 7.5f * u, 7f * u); lineTo(18f * u, 7f * u)
                    moveTo(19f * u, 13f * u); lineTo(19f * u, 14.5f * u); quadraticBezierTo(19f * u, 17f * u, 16.5f * u, 17f * u); lineTo(6f * u, 17f * u)
                }
                drawPath(path, tint, style = stroke)
                drawPath(Path().apply { moveTo(15.5f * u, 4.5f * u); lineTo(18.5f * u, 7f * u); lineTo(15.5f * u, 9.5f * u) }, tint, style = stroke)
                drawPath(Path().apply { moveTo(8.5f * u, 14.5f * u); lineTo(5.5f * u, 17f * u); lineTo(8.5f * u, 19.5f * u) }, tint, style = stroke)
            }
            Glyph.NEXT -> {
                drawPath(Path().apply { moveTo(6f * u, 6f * u); lineTo(15f * u, 12f * u); lineTo(6f * u, 18f * u); close() }, tint)
                drawLine(tint, p(18f, 6f), p(18f, 18f), 2.4f * u, StrokeCap.Round)
            }
            Glyph.STOP -> {
                drawRoundRect(tint, p(6.5f, 6.5f), Size(11f * u, 11f * u), CornerRadius(2f * u))
            }
        }
    }
}

package com.localshorts.app.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localshorts.app.ui.VideoTextShadow
import com.localshorts.app.ui.formatTime
import kotlin.math.abs
import kotlin.math.roundToInt

/** Live state of a sideways seek swipe. */
data class SeekPreview(val targetMs: Long, val deltaMs: Long, val durationMs: Long)

/** A double-tap skip being shown: side -1 back / +1 forward, running total in seconds. */
data class SkipFlash(val side: Int, val totalS: Int, val id: Int)

/** A heart popping where the screen was double-tapped. */
data class HeartPop(val at: Offset, val id: Int)

/** Big, centred readout while swiping to seek: target time, how far you moved, and a progress line. */
@Composable
fun SeekBubble(p: SeekPreview, accent: Color, modifier: Modifier = Modifier) {
    val sign = if (p.deltaMs >= 0) "+" else "−"
    Column(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xB3101114))
            .padding(horizontal = 22.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "$sign${formatTime(abs(p.deltaMs))}",
            style = TextStyle(color = accent, fontSize = 14.sp, fontWeight = FontWeight.Bold),
        )
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(formatTime(p.targetMs), style = TextStyle(color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold))
            Text(
                "  /  " + formatTime(p.durationMs),
                style = TextStyle(color = Color.White.copy(alpha = 0.6f), fontSize = 15.sp, fontWeight = FontWeight.Medium),
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        val f = if (p.durationMs > 0) (p.targetMs.toFloat() / p.durationMs).coerceIn(0f, 1f) else 0f
        Box(Modifier.width(180.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x33FFFFFF))) {
            Box(Modifier.fillMaxWidth(f).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(accent))
        }
    }
}

/** YouTube-style skip flash: a soft arc washing in from the tapped side with chevrons and the total. */
@Composable
fun SkipFlashOverlay(flash: SkipFlash, onDone: () -> Unit) {
    val alpha = remember { Animatable(0f) }
    val chevron = remember { Animatable(0f) }
    LaunchedEffect(flash.id) {
        alpha.snapTo(1f)
        chevron.snapTo(0f)
        chevron.animateTo(1f, tween(450))
        kotlinx.coroutines.delay(250)
        alpha.animateTo(0f, tween(250))
        onDone()
    }
    BoxWithConstraints(Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha.value }) {
        val forward = flash.side > 0
        Canvas(Modifier.fillMaxSize()) {
            // Half-ellipse hugging the edge, fading toward the middle
            val w = size.width * 0.42f
            val left = if (forward) size.width - w else 0f
            val path = Path().apply {
                if (forward) {
                    moveTo(size.width, 0f)
                    lineTo(size.width - w * 0.55f, 0f)
                    cubicTo(size.width - w * 1.05f, size.height * 0.3f, size.width - w * 1.05f, size.height * 0.7f, size.width - w * 0.55f, size.height)
                    lineTo(size.width, size.height)
                } else {
                    moveTo(0f, 0f)
                    lineTo(w * 0.55f, 0f)
                    cubicTo(w * 1.05f, size.height * 0.3f, w * 1.05f, size.height * 0.7f, w * 0.55f, size.height)
                    lineTo(0f, size.height)
                }
                close()
            }
            drawPath(
                path,
                Brush.horizontalGradient(
                    if (forward) listOf(Color.Transparent, Color(0x40FFFFFF)) else listOf(Color(0x40FFFFFF), Color.Transparent),
                    startX = left,
                    endX = left + w,
                ),
            )
        }
        Column(
            Modifier
                .align(if (forward) Alignment.CenterEnd else Alignment.CenterStart)
                .padding(horizontal = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy((-4).dp)) {
                repeat(3) { i ->
                    // Chevrons light up one after another in the skip direction
                    val k = if (forward) i else 2 - i
                    val lit = ((chevron.value * 3f) - k).coerceIn(0f, 1f)
                    Chevron(forward, Color.White.copy(alpha = 0.35f + 0.65f * lit))
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "${flash.totalS} seconds",
                style = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, shadow = VideoTextShadow),
            )
        }
    }
}

@Composable
private fun Chevron(forward: Boolean, color: Color) {
    Canvas(Modifier.size(width = 16.dp, height = 18.dp)) {
        val p = Path().apply {
            if (forward) {
                moveTo(size.width * 0.15f, 0f); lineTo(size.width, size.height / 2); lineTo(size.width * 0.15f, size.height)
            } else {
                moveTo(size.width * 0.85f, 0f); lineTo(0f, size.height / 2); lineTo(size.width * 0.85f, size.height)
            }
            close()
        }
        drawPath(p, color)
    }
}

/** Heart that springs up at the tap point, drifts up a touch and fades. */
@Composable
fun HeartPopOverlay(pop: HeartPop, accent: Color, onDone: () -> Unit) {
    val t = remember { Animatable(0f) }
    LaunchedEffect(pop.id) {
        t.snapTo(0f)
        t.animateTo(1f, tween(800, easing = FastOutSlowInEasing))
        onDone()
    }
    val density = LocalDensity.current
    val sizePx = with(density) { 96.dp.toPx() }
    val v = t.value
    // 0..0.25 grow with overshoot, hold, then rise and fade
    val scale = when {
        v < 0.18f -> 0.4f + (v / 0.18f) * 0.85f
        v < 0.3f -> 1.25f - ((v - 0.18f) / 0.12f) * 0.25f
        else -> 1f + (v - 0.3f) * 0.15f
    }
    val alpha = if (v < 0.6f) 1f else (1f - (v - 0.6f) / 0.4f).coerceIn(0f, 1f)
    val lift = if (v < 0.4f) 0f else (v - 0.4f) * sizePx * 0.6f
    Icon(
        Icons.Default.Favorite,
        contentDescription = null,
        tint = accent,
        modifier = Modifier
            .offset { IntOffset((pop.at.x - sizePx / 2).roundToInt(), (pop.at.y - sizePx / 2 - lift).roundToInt()) }
            .size(96.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
                rotationZ = -8f
            },
    )
}

package com.localshorts.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localshorts.app.data.Gestures
import kotlin.math.abs

/** Which layer of gestures the phone preview shows and lets you edit. */
enum class GestureLayer(val label: String) { TAP("Tap"), DOUBLE("Double tap"), SWIPE("Swipe") }

/**
 * A little phone showing where each gesture works. The zone edges have handles: drag them to resize the
 * zones. [onChange] fires while dragging (for a live preview), [onCommit] when the finger lifts.
 */
@Composable
fun GestureMap(
    layer: GestureLayer,
    g: Gestures,
    brightnessOn: Boolean,
    volumeOn: Boolean,
    accent: Color,
    onChange: (Gestures) -> Unit,
    onCommit: (Gestures) -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 176.dp,
) {
    val height = width * (19.5f / 9f)
    val density = LocalDensity.current
    val latest by rememberUpdatedState(g)
    val change by rememberUpdatedState(onChange)
    val commit by rememberUpdatedState(onCommit)
    val layerNow by rememberUpdatedState(layer)
    // 0 none, 1 top band edge, 2 bottom band edge, 3 left side edge, 4 right side edge, 5 seek area edge
    var dragging by remember { mutableIntStateOf(0) }
    val zoneFill = accent.copy(alpha = 0.34f)
    val calm = Color.White.copy(alpha = 0.07f)

    Box(
        modifier
            .size(width, height)
            .clip(RoundedCornerShape(30.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF2A2140), Color(0xFF14121C), Color(0xFF1C1A2A))))
            .border(3.dp, Color(0xFF2E3036), RoundedCornerShape(30.dp))
            .pointerInput(Unit) {
                val grab = with(density) { 26.dp.toPx() }
                detectDragGestures(
                    onDragStart = { o ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val gg = latest
                        dragging = when (layerNow) {
                            GestureLayer.TAP -> if (!gg.pauseMiddleOnly) {
                                0
                            } else {
                                val top = h * (1 - gg.pauseBand) / 2
                                when {
                                    abs(o.y - top) < grab -> 1
                                    abs(o.y - (h - top)) < grab -> 2
                                    else -> 0
                                }
                            }
                            GestureLayer.DOUBLE -> if (!gg.doubleTapSkip) {
                                0
                            } else {
                                when {
                                    abs(o.x - w * gg.sideZone) < grab -> 3
                                    abs(o.x - w * (1 - gg.sideZone)) < grab -> 4
                                    else -> 0
                                }
                            }
                            GestureLayer.SWIPE -> if (gg.swipeSeek && abs(o.y - h * (1 - gg.seekZone)) < grab) 5 else 0
                        }
                    },
                    onDragEnd = {
                        if (dragging != 0) commit(latest)
                        dragging = 0
                    },
                    onDragCancel = { dragging = 0 },
                ) { ch, _ ->
                    if (dragging == 0) return@detectDragGestures
                    ch.consume()
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    val p = ch.position
                    val gg = latest
                    val next = when (dragging) {
                        1 -> gg.copy(pauseBand = (1f - 2f * p.y / h).coerceIn(0.2f, 0.9f))
                        2 -> gg.copy(pauseBand = (2f * p.y / h - 1f).coerceIn(0.2f, 0.9f))
                        3 -> gg.copy(sideZone = (p.x / w).coerceIn(0.15f, 0.45f))
                        4 -> gg.copy(sideZone = (1f - p.x / w).coerceIn(0.15f, 0.45f))
                        5 -> gg.copy(seekZone = (1f - p.y / h).coerceIn(0.2f, 0.8f))
                        else -> gg
                    }
                    change(next)
                }
            },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wDp = maxWidth
            val hDp = maxHeight
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))
                fun hLine(y: Float, active: Boolean) {
                    drawLine(Color.White.copy(alpha = if (active) 0.95f else 0.6f), Offset(0f, y), Offset(w, y), 1.5.dp.toPx(), pathEffect = dash)
                    val hw = 34.dp.toPx(); val hh = (if (active) 8 else 6).dp.toPx()
                    drawRoundRect(Color.White, Offset(w / 2 - hw / 2, y - hh / 2), Size(hw, hh), CornerRadius(hh / 2))
                }
                fun vLine(x: Float, active: Boolean) {
                    drawLine(Color.White.copy(alpha = if (active) 0.95f else 0.6f), Offset(x, 0f), Offset(x, h), 1.5.dp.toPx(), pathEffect = dash)
                    val hh = 34.dp.toPx(); val hw = (if (active) 8 else 6).dp.toPx()
                    drawRoundRect(Color.White, Offset(x - hw / 2, h / 2 - hh / 2), Size(hw, hh), CornerRadius(hw / 2))
                }
                when (layer) {
                    GestureLayer.TAP -> {
                        if (g.pauseMiddleOnly) {
                            val top = h * (1 - g.pauseBand) / 2
                            drawRect(calm, Offset.Zero, Size(w, top))
                            drawRect(calm, Offset(0f, h - top), Size(w, top))
                            drawRect(zoneFill, Offset(0f, top), Size(w, h - 2 * top))
                            hLine(top, dragging == 1)
                            hLine(h - top, dragging == 2)
                        } else {
                            drawRect(zoneFill)
                        }
                    }
                    GestureLayer.DOUBLE -> {
                        if (g.doubleTapSkip) {
                            val sw = w * g.sideZone
                            drawRect(zoneFill, Offset.Zero, Size(sw, h))
                            drawRect(zoneFill, Offset(w - sw, 0f), Size(sw, h))
                            drawRect(if (g.doubleTapFavourite) Color(0x30FF5A7A) else calm, Offset(sw, 0f), Size(w - 2 * sw, h))
                            vLine(sw, dragging == 3)
                            vLine(w - sw, dragging == 4)
                        } else {
                            drawRect(if (g.doubleTapFavourite) Color(0x30FF5A7A) else calm)
                        }
                    }
                    GestureLayer.SWIPE -> {
                        val edge = 0.25f * w
                        if (brightnessOn) drawRect(calm, Offset.Zero, Size(edge, h))
                        if (volumeOn) drawRect(calm, Offset(w - edge, 0f), Size(edge, h))
                        if (g.swipeSeek) {
                            val top = h * (1 - g.seekZone)
                            drawRect(zoneFill, Offset(0f, top), Size(w, h - top))
                            hLine(top, dragging == 5)
                        }
                    }
                }
            }

            // Zone labels, placed by the same fractions as the drawing
            @Composable
            fun Label(text: String, x: Dp, y: Dp, boxW: Dp, sub: String? = null) {
                Column(
                    Modifier.offset(x, y).width(boxW),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(text, style = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, shadow = VideoTextShadow))
                    if (sub != null) Text(sub, style = TextStyle(color = Color.White.copy(alpha = 0.7f), fontSize = 10.5.sp, textAlign = TextAlign.Center, shadow = VideoTextShadow))
                }
            }
            when (layer) {
                GestureLayer.TAP -> if (g.pauseMiddleOnly) {
                    val top = hDp * ((1 - g.pauseBand) / 2)
                    Label("Pause / play", 0.dp, hDp / 2 - 16.dp, wDp, "${(g.pauseBand * 100).toInt()}% of the screen")
                    if (top > 34.dp) Label("Show controls", 0.dp, top / 2 - 9.dp, wDp)
                    if (top > 34.dp) Label("Show controls", 0.dp, hDp - top / 2 - 9.dp, wDp)
                } else {
                    Label("Pause / play", 0.dp, hDp / 2 - 16.dp, wDp, "anywhere")
                }
                GestureLayer.DOUBLE -> {
                    val sw = wDp * g.sideZone
                    if (g.doubleTapSkip) {
                        Label("−${g.skipBackS}s", 0.dp, hDp / 2 - 40.dp, sw)
                        Label("+${g.skipForwardS}s", wDp - sw, hDp / 2 - 40.dp, sw)
                    }
                    val midX = if (g.doubleTapSkip) sw else 0.dp
                    val midW = if (g.doubleTapSkip) wDp - sw * 2 else wDp
                    if (g.doubleTapFavourite) Label("♥", midX, hDp / 2 - 52.dp, midW, "Favourite")
                }
                GestureLayer.SWIPE -> {
                    val edge = wDp * 0.25f
                    if (brightnessOn) Label("☀", 0.dp, 40.dp, edge, "↕")
                    if (volumeOn) Label("🔊", wDp - edge, 40.dp, edge, "↕")
                    if (g.swipeVideos) Label("↕", 0.dp, hDp * 0.22f, wDp, "Next video") else Label("—", 0.dp, hDp * 0.22f, wDp, "Video change off")
                    if (g.swipeSeek) {
                        val top = hDp * (1 - g.seekZone)
                        Label("⟷  Seek", 0.dp, top + (hDp - top) / 2 - 14.dp, wDp, if (g.seekRangeS == 0) "whole video" else "${g.seekRangeS}s per swipe")
                    }
                }
            }
        }
    }
}

/** A tidy row of pill choices. */
@Composable
fun ChoiceChips(options: List<Pair<Int, String>>, selected: Int, accent: Color, onPick: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (v, label) ->
            val on = v == selected
            val bg by animateColorAsState(if (on) accent else Hc.Surface2, tween(180), label = "chip")
            Box(
                Modifier
                    .height(34.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(bg)
                    .clickable { onPick(v) }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (on) onAccent(accent) else Hc.Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

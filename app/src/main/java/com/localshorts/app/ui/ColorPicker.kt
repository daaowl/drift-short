package com.localshorts.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Hue / saturation / brightness sliders. The swatch updates live; [onCommit] fires when a drag
 * ends, so settings are written once per adjustment rather than on every frame.
 */
@Composable
fun HsvPicker(color: Color, onCommit: (Color) -> Unit) {
    val start = remember(color) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color.toArgb(), hsv)
        hsv
    }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var sat by remember { mutableFloatStateOf(start[1]) }
    var value by remember { mutableFloatStateOf(start[2]) }
    LaunchedEffect(color) {
        hue = start[0]
        sat = start[1]
        value = start[2]
    }
    val live = Color.hsv(hue, sat.coerceIn(0f, 1f), value.coerceIn(0f, 1f))
    val commit = { onCommit(Color.hsv(hue, sat.coerceIn(0f, 1f), value.coerceIn(0f, 1f))) }

    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(live))
            Spacer(Modifier.width(12.dp))
            Text("Custom colour", color = Hc.Ink, fontSize = 15.sp)
        }
        Spacer(Modifier.height(14.dp))
        Label("Hue")
        GradientSlider(
            fraction = hue / 360f,
            brush = Brush.horizontalGradient(List(7) { i -> Color.hsv(i * 60f % 360f, 1f, 1f) }.let { it.dropLast(1) + Color.hsv(0f, 1f, 1f) }),
            thumb = Color.hsv(hue, 1f, 1f),
            onChange = { hue = (it * 360f).coerceIn(0f, 359.9f) },
            onEnd = commit,
        )
        Label("Saturation")
        GradientSlider(
            fraction = sat,
            brush = Brush.horizontalGradient(listOf(Color.hsv(hue, 0f, value), Color.hsv(hue, 1f, value))),
            thumb = live,
            onChange = { sat = it },
            onEnd = commit,
        )
        Label("Brightness")
        GradientSlider(
            fraction = value,
            brush = Brush.horizontalGradient(listOf(Color.Black, Color.hsv(hue, sat, 1f))),
            thumb = live,
            onChange = { value = it.coerceAtLeast(0.15f) },
            onEnd = commit,
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(text, color = Hc.Mute, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
}

@Composable
private fun GradientSlider(fraction: Float, brush: Brush, thumb: Color, onChange: (Float) -> Unit, onEnd: () -> Unit) {
    val change by rememberUpdatedState(onChange)
    val end by rememberUpdatedState(onEnd)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(32.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { change((it.x / size.width).coerceIn(0f, 1f)) },
                    onDragEnd = { end() },
                    onDragCancel = { end() },
                ) { c, _ ->
                    c.consume()
                    change((c.position.x / size.width).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectTapGestures {
                    change((it.x / size.width).coerceIn(0f, 1f))
                    end()
                }
            },
    ) {
        val trackH = 10.dp.toPx()
        val r = 12.dp.toPx()
        val y = size.height / 2 - trackH / 2
        drawRoundRect(brush, Offset(r, y), Size(size.width - 2 * r, trackH), CornerRadius(trackH / 2))
        val x = r + (size.width - 2 * r) * fraction.coerceIn(0f, 1f)
        drawCircle(Color.White, r, Offset(x, size.height / 2))
        drawCircle(thumb, r - 3.dp.toPx(), Offset(x, size.height / 2))
    }
}

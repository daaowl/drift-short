package com.localshorts.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * A small menu that pops out of the button that opened it, like the context menus in iOS and
 * premium Android players: translucent charcoal card, large corner radius, a hairline border and
 * soft shadow, and a quick spring scale from the button's corner. It never jumps to the bottom of
 * the screen; if there's no room on the requested side it flips to the other side.
 */
class Popover(
    /** The opening button's bounds in the same coordinate space as the layer that draws it. */
    val anchor: Rect,
    val above: Boolean = false,
    val content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
)

private val PopoverShape = RoundedCornerShape(22.dp)

@Composable
fun PopoverLayer(popover: Popover?, onGone: () -> Unit) {
    if (popover == null) return
    val visible = remember(popover) { MutableTransitionState(false).apply { targetState = true } }
    val dismiss = { visible.targetState = false }
    LaunchedEffect(visible.isIdle, visible.currentState) {
        if (visible.isIdle && !visible.currentState) onGone()
    }

    Box(
        Modifier
            .fillMaxSize()
            // Any touch outside the card closes it and is not passed to what's underneath
            .pointerInput(popover) {
                awaitEachGesture {
                    awaitFirstDown(pass = PointerEventPass.Main)
                    dismiss()
                }
            },
    ) {
        Layout(
            content = {
                AnimatedVisibility(
                    visibleState = visible,
                    enter = fadeIn(tween(110)) + scaleIn(
                        animationSpec = spring(dampingRatio = 0.78f, stiffness = 700f),
                        initialScale = 0.82f,
                        transformOrigin = TransformOrigin(1f, if (popover.above) 1f else 0f),
                    ),
                    exit = fadeOut(tween(90)) + scaleOut(
                        animationSpec = tween(110),
                        targetScale = 0.9f,
                        transformOrigin = TransformOrigin(1f, if (popover.above) 1f else 0f),
                    ),
                ) {
                    Column(
                        Modifier
                            .widthIn(min = 196.dp, max = 280.dp)
                            .shadow(18.dp, PopoverShape, ambientColor = Color.Black, spotColor = Color.Black)
                            .clip(PopoverShape)
                            .background(Color(0xE61C1F23))
                            .border(0.5.dp, Color(0x1FFFFFFF), PopoverShape)
                            // swallow taps inside the card
                            .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false).consume() } }
                            .padding(vertical = 6.dp),
                    ) {
                        popover.content(this, dismiss)
                    }
                }
            },
        ) { measurables, constraints ->
            val placeable = measurables.firstOrNull()?.measure(constraints.copy(minWidth = 0, minHeight = 0))
            layout(constraints.maxWidth, constraints.maxHeight) {
                if (placeable != null) {
                    val margin = 10.dp.toPx()
                    val gap = 8.dp.toPx()
                    val a = popover.anchor
                    val x = (a.right - placeable.width)
                        .coerceIn(margin, (constraints.maxWidth - placeable.width - margin).coerceAtLeast(margin))
                    val below = a.bottom + gap
                    val aboveY = a.top - gap - placeable.height
                    val y = when {
                        popover.above && aboveY >= margin -> aboveY
                        !popover.above && below + placeable.height <= constraints.maxHeight - margin -> below
                        popover.above -> below
                        else -> aboveY
                    }.coerceIn(margin, (constraints.maxHeight - placeable.height - margin).coerceAtLeast(margin))
                    placeable.place(x.roundToInt(), y.roundToInt())
                }
            }
        }
    }
}

/** A tappable row inside a popover. */
@Composable
fun PopoverItem(
    text: String,
    onClick: () -> Unit,
    leading: (@Composable () -> Unit)? = null,
    checked: Boolean = false,
    accent: Color = LocalAccent.current,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) { leading() }
            Spacer(Modifier.width(12.dp))
        }
        Text(
            text,
            color = if (checked) accent else Hc.Ink,
            fontSize = 14.sp,
            fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (checked) {
            Spacer(Modifier.width(10.dp))
            Icon(Icons.Default.Check, contentDescription = "Selected", tint = accent, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun PopoverLabel(text: String) {
    Text(
        text,
        color = Hc.Mute,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 4.dp),
    )
}

@Composable
fun PopoverDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .height(0.5.dp)
            .background(Color(0x1FFFFFFF)),
    )
}

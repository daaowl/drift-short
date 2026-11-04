package com.vanta.player.ui.components

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import com.vanta.player.model.ShortTransitionSpeed
import com.vanta.player.player.SlotRole
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Implements Section 6:
 * - Wrap around at collection ends; handle a one-video collection without an invalid adjacent page.
 * - Current and adjacent pages follow the finger continuously.
 * - Baseline settling duration is 230–370ms depending on remaining travel; Slow multiplies by 1.35, Fast by 0.72.
 * - Respects disabled system animations.
 */
@Composable
fun VerticalShortsPager(
    currentIndex: Int,
    totalCount: Int,
    transitionSpeed: ShortTransitionSpeed,
    autoAdvanceEvents: SharedFlow<Int>,
    onContinuousDragFraction: (Float) -> Unit,
    onSwipeSettled: (indexDelta: Int) -> Unit,
    modifier: Modifier = Modifier,
    slotContent: @Composable (role: SlotRole) -> Unit
) {
    val context = LocalContext.current
    val animationsEnabled = remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) > 0f
        }.getOrDefault(true)
    }

    val scope = rememberCoroutineScope()
    val offsetY = remember { Animatable(0f) }
    val viewportHeightHolder = remember { floatArrayOf(1920f) }
    val canSwipeAdjacent = totalCount > 1

    fun computeSettleDurationMs(remainingFraction: Float): Int {
        if (!animationsEnabled) return 0
        val baseDuration = 230f + (140f * remainingFraction.coerceIn(0f, 1f))
        return (baseDuration * transitionSpeed.multiplier).roundToInt().coerceIn(80, 650)
    }

    LaunchedEffect(autoAdvanceEvents, currentIndex, totalCount, transitionSpeed) {
        autoAdvanceEvents.collect { delta ->
            if (delta > 0 && canSwipeAdjacent) {
                val heightPx = viewportHeightHolder[0].coerceAtLeast(1f)
                val duration = computeSettleDurationMs(1f)
                if (duration == 0) {
                    offsetY.snapTo(0f)
                    onSwipeSettled(1)
                } else {
                    offsetY.animateTo(
                        targetValue = -heightPx,
                        animationSpec = tween(
                            durationMillis = duration,
                            easing = FastOutSlowInEasing
                        )
                    ) {
                        val fraction = (-value / heightPx).coerceIn(-1f, 1f)
                        onContinuousDragFraction(fraction)
                    }
                    offsetY.snapTo(0f)
                    onSwipeSettled(1)
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                if (size.height > 0) {
                    viewportHeightHolder[0] = size.height.toFloat()
                }
            }
            .pointerInput(currentIndex, totalCount, transitionSpeed) {
                val touchSlop = viewConfiguration.touchSlop

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val velocityTracker = VelocityTracker()
                    velocityTracker.addPosition(down.uptimeMillis, down.position)

                    var isDraggingPager = false
                    var accumulatedDy = 0f
                    var accumulatedDx = 0f
                    var pointerId = down.id

                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.firstOrNull { it.id == pointerId }
                            ?: event.changes.firstOrNull()
                            ?: break

                        if (!change.pressed) {
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            break
                        }

                        pointerId = change.id
                        if (change.isConsumed && !isDraggingPager) {
                            break
                        }

                        val delta = change.positionChange()
                        accumulatedDx += delta.x
                        accumulatedDy += delta.y

                        if (!isDraggingPager) {
                            if (abs(accumulatedDy) > touchSlop && abs(accumulatedDy) > abs(accumulatedDx) * 1.15f) {
                                isDraggingPager = true
                                change.consume()
                            } else if (abs(accumulatedDx) > touchSlop * 1.5f) {
                                break
                            }
                        } else {
                            change.consume()
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            val heightPx = viewportHeightHolder[0].coerceAtLeast(1f)
                            val rawTarget = offsetY.value + delta.y

                            // Single-video collection does not expose invalid adjacent pages
                            val boundedTarget = if (!canSwipeAdjacent) {
                                (rawTarget * 0.12f).coerceIn(-heightPx * 0.15f, heightPx * 0.15f)
                            } else {
                                rawTarget.coerceIn(-heightPx, heightPx)
                            }

                            scope.launch {
                                offsetY.snapTo(boundedTarget)
                            }
                            val dragFraction = if (canSwipeAdjacent) {
                                (-boundedTarget / heightPx).coerceIn(-1f, 1f)
                            } else {
                                0f
                            }
                            onContinuousDragFraction(dragFraction)
                        }
                    }

                    if (isDraggingPager) {
                        val heightPx = viewportHeightHolder[0].coerceAtLeast(1f)
                        val currentOffset = offsetY.value
                        val velocityY = velocityTracker.calculateVelocity().y

                        val fractionMoved = abs(currentOffset) / heightPx
                        val isFlingUp = velocityY < -1050f && currentOffset < -heightPx * 0.06f
                        val isFlingDown = velocityY > 1050f && currentOffset > heightPx * 0.06f

                        val indexDelta = if (!canSwipeAdjacent) {
                            0
                        } else when {
                            currentOffset < 0f && (fractionMoved >= 0.40f || isFlingUp) -> 1
                            currentOffset > 0f && (fractionMoved >= 0.40f || isFlingDown) -> -1
                            else -> 0
                        }

                        val targetOffsetPx = when (indexDelta) {
                            1 -> -heightPx
                            -1 -> heightPx
                            else -> 0f
                        }

                        val remainingTravelFraction = abs(targetOffsetPx - currentOffset) / heightPx
                        val settleDuration = computeSettleDurationMs(remainingTravelFraction)

                        scope.launch {
                            if (settleDuration == 0) {
                                offsetY.snapTo(0f)
                                onSwipeSettled(indexDelta)
                            } else {
                                offsetY.animateTo(
                                    targetValue = targetOffsetPx,
                                    animationSpec = tween(
                                        durationMillis = settleDuration,
                                        easing = FastOutSlowInEasing
                                    )
                                ) {
                                    val liveFraction = if (canSwipeAdjacent) {
                                        (-value / heightPx).coerceIn(-1f, 1f)
                                    } else 0f
                                    onContinuousDragFraction(liveFraction)
                                }
                                offsetY.snapTo(0f)
                                onSwipeSettled(indexDelta)
                            }
                        }
                    }
                }
            }
    ) {
        val heightPx = viewportHeightHolder[0].coerceAtLeast(1f)
        val currentOffsetPx = offsetY.value

        if (canSwipeAdjacent && currentOffsetPx > 0.5f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset {
                        IntOffset(
                            x = 0,
                            y = (currentOffsetPx - heightPx).roundToInt()
                        )
                    }
            ) {
                slotContent(SlotRole.PREV)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset {
                    IntOffset(
                        x = 0,
                        y = currentOffsetPx.roundToInt()
                    )
                }
        ) {
            slotContent(SlotRole.CURRENT)
        }

        if (canSwipeAdjacent && currentOffsetPx < -0.5f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset {
                        IntOffset(
                            x = 0,
                            y = (currentOffsetPx + heightPx).roundToInt()
                        )
                    }
            ) {
                slotContent(SlotRole.NEXT)
            }
        }
    }
}

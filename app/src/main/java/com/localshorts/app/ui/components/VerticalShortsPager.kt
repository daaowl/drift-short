package com.localshorts.app.ui.components

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
import com.localshorts.app.model.SwipeTransitionSpeed
import com.localshorts.app.player.PLAYER_POOL_SIZE
import com.localshorts.app.player.SlotRole
import com.localshorts.app.player.slotIndexFor
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Continuous-tracking vertical Shorts pager.
 *
 * Three slot containers (one per pooled player) are composed permanently and keyed by slot index,
 * so a TextureView is never recreated or re-attached when the user moves between videos: video
 * index i always lives in slot (i mod 3). Only the translation of each container changes.
 *
 * - Finger tracking is 1:1 and independent of [transitionSpeed].
 * - [transitionSpeed] only shapes the settle animation after release.
 * - [onContinuousDragFraction] receives the incoming-video share on every pointer move.
 */
@Composable
fun VerticalShortsPager(
    currentIndex: Int,
    totalCount: Int,
    transitionSpeed: SwipeTransitionSpeed,
    autoAdvanceEvents: SharedFlow<Int>,
    leftEdgeReserved: Boolean,
    rightEdgeReserved: Boolean,
    onContinuousDragFraction: (Float) -> Unit,
    onSwipeSettled: (indexDelta: Int) -> Unit,
    modifier: Modifier = Modifier,
    slotContent: @Composable (slotIndex: Int, videoIndex: Int) -> Unit
) {
    val scope = rememberCoroutineScope()
    var displayIndex by remember { mutableIntStateOf(currentIndex) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var viewportH by remember { mutableFloatStateOf(1920f) }
    val settleJob = remember { arrayOfNulls<Job>(1) }

    val latestTotal by rememberUpdatedState(totalCount)
    val latestSpeed by rememberUpdatedState(transitionSpeed)
    val latestOnFraction by rememberUpdatedState(onContinuousDragFraction)
    val latestOnSettled by rememberUpdatedState(onSwipeSettled)

    LaunchedEffect(currentIndex) {
        if (settleJob[0]?.isActive != true && displayIndex != currentIndex) {
            displayIndex = currentIndex
            dragY = 0f
        }
    }

    fun startSettle(delta: Int, velocity: Float, spec: AnimationSpec<Float>) {
        settleJob[0]?.cancel()
        settleJob[0] = scope.launch {
            val h = viewportH.coerceAtLeast(1f)
            val target = when (delta) {
                1 -> -h
                -1 -> h
                else -> 0f
            }
            animate(
                initialValue = dragY,
                targetValue = target,
                initialVelocity = velocity,
                animationSpec = spec
            ) { value, _ ->
                val c = value.coerceIn(-h, h)
                dragY = c
                latestOnFraction((-c / h).coerceIn(-1f, 1f))
            }
            // Index and offset change in ONE snapshot so no frame ever shows the new index with
            // the old offset (or vice versa).
            Snapshot.withMutableSnapshot {
                displayIndex += delta
                dragY = 0f
            }
            latestOnSettled(delta)
        }
    }

    fun applyDrag(dy: Float) {
        val h = viewportH.coerceAtLeast(1f)
        val hasPrev = displayIndex > 0
        val hasNext = displayIndex < latestTotal - 1
        val resisted = if ((dragY >= 0f && dy > 0f && !hasPrev) || (dragY <= 0f && dy < 0f && !hasNext)) {
            dy * 0.3f
        } else {
            dy
        }
        var next = (dragY + resisted).coerceIn(-h, h)
        if (!hasPrev && next > h * 0.15f) next = h * 0.15f
        if (!hasNext && next < -h * 0.15f) next = -h * 0.15f
        dragY = next
        latestOnFraction((-next / h).coerceIn(-1f, 1f))
    }

    LaunchedEffect(autoAdvanceEvents) {
        autoAdvanceEvents.collect { delta ->
            if (delta > 0 && displayIndex < latestTotal - 1 && settleJob[0]?.isActive != true) {
                startSettle(
                    delta = 1,
                    velocity = 0f,
                    spec = tween<Float>(
                        durationMillis = latestSpeed.settleDurationMs,
                        easing = FastOutSlowInEasing
                    )
                )
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                if (size.height > 0) viewportH = size.height.toFloat()
            }
            .pointerInput(leftEdgeReserved, rightEdgeReserved) {
                val touchSlop = viewConfiguration.touchSlop

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    val h = size.height.toFloat().coerceAtLeast(1f)

                    // Edge gutters belong to the brightness / volume gestures when enabled.
                    val inSafeBand = down.position.y in (h * 0.15f)..(h * 0.82f)
                    val reserved = inSafeBand && (
                        (leftEdgeReserved && down.position.x <= w * 0.15f) ||
                            (rightEdgeReserved && down.position.x >= w * 0.85f)
                        )
                    if (reserved) return@awaitEachGesture

                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)

                    var dragging = false
                    var accDx = 0f
                    var accDy = 0f

                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        tracker.addPosition(change.uptimeMillis, change.position)
                        if (!change.pressed) break
                        if (change.isConsumed && !dragging) break

                        val delta = change.positionChange()
                        accDx += delta.x
                        accDy += delta.y

                        if (!dragging) {
                            if (abs(accDy) > touchSlop && abs(accDy) > abs(accDx) * 1.15f) {
                                dragging = true
                                settleJob[0]?.cancel()
                                change.consume()
                                applyDrag(accDy)
                            } else if (abs(accDx) > touchSlop * 1.5f) {
                                break
                            }
                        } else {
                            change.consume()
                            applyDrag(delta.y)
                        }
                    }

                    if (dragging) {
                        val hh = viewportH.coerceAtLeast(1f)
                        val cur = dragY
                        val velocityY = tracker.calculateVelocity().y
                        val hasPrev = displayIndex > 0
                        val hasNext = displayIndex < latestTotal - 1
                        val moved = abs(cur) / hh
                        val flingUp = velocityY < -1100f && cur < -hh * 0.08f
                        val flingDown = velocityY > 1100f && cur > hh * 0.08f

                        val delta = when {
                            cur < 0f && hasNext && (moved >= 0.42f || flingUp) -> 1
                            cur > 0f && hasPrev && (moved >= 0.42f || flingDown) -> -1
                            else -> 0
                        }
                        startSettle(
                            delta = delta,
                            velocity = velocityY,
                            spec = spring<Float>(
                                dampingRatio = latestSpeed.dampingRatio,
                                stiffness = latestSpeed.stiffness,
                                visibilityThreshold = 0.5f
                            )
                        )
                    }
                }
            }
    ) {
        for (slot in 0 until PLAYER_POOL_SIZE) {
            key(slot) {
                val videoIndex = (displayIndex - 1..displayIndex + 1).first { slotIndexFor(it) == slot }
                val role = when {
                    videoIndex < displayIndex -> SlotRole.PREV
                    videoIndex == displayIndex -> SlotRole.CURRENT
                    else -> SlotRole.NEXT
                }
                val inRange = videoIndex in 0 until latestTotal

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(if (role == SlotRole.CURRENT) 1f else 0f)
                        .graphicsLayer {
                            val h = viewportH
                            // Idle neighbours stay stacked behind the current video (inside the
                            // window, so their TextureView keeps a live surface and renders its
                            // first frame ahead of time) and only slide in once a drag starts.
                            translationY = when (role) {
                                SlotRole.CURRENT -> dragY
                                SlotRole.NEXT -> if (dragY < 0f) h + dragY else 0f
                                SlotRole.PREV -> if (dragY > 0f) dragY - h else 0f
                            }
                        }
                ) {
                    if (inRange) {
                        slotContent(slot, videoIndex)
                    }
                }
            }
        }
    }
}

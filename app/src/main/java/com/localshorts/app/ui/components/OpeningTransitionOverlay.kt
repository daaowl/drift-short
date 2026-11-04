package com.localshorts.app.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.localshorts.app.data.ThumbnailCacheManager
import com.localshorts.app.model.OpeningTransitionState
import com.localshorts.app.model.TransitionPhase
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Geometry-driven thumbnail <-> fullscreen transition.
 *
 * - Animates the real bounds and corner radius from the tapped grid cell to the viewport.
 * - Stays opaque over the player until its TextureView has delivered onRenderedFirstFrame
 *   (transitionState.isFirstFrameReady). A 1.4 s guard only exists so a broken file with no video
 *   track can never leave the cover stuck.
 * - At full size the picture is placed with [placedVideoSize], exactly like the live TextureView,
 *   so removing the cover causes no jump.
 */
@Composable
fun OpeningTransitionOverlay(
    transitionState: OpeningTransitionState,
    thumbnailCache: ThumbnailCacheManager,
    onOpeningGeometryFinished: () -> Unit,
    onCoverDismissed: () -> Unit,
    onClosingFinished: () -> Unit
) {
    val phase = transitionState.phase
    val targetVideo = transitionState.targetVideo
    if (phase == TransitionPhase.IDLE || targetVideo == null) return

    val density = LocalDensity.current

    val latestOpeningFinished by rememberUpdatedState(onOpeningGeometryFinished)
    val latestDismissed by rememberUpdatedState(onCoverDismissed)
    val latestClosingFinished by rememberUpdatedState(onClosingFinished)

    var bitmap by remember(targetVideo.thumbnailKey, transitionState.frameBitmap) {
        mutableStateOf<Bitmap?>(
            transitionState.frameBitmap ?: thumbnailCache.getMemoryCached(targetVideo)
        )
    }

    LaunchedEffect(targetVideo.thumbnailKey, transitionState.frameBitmap) {
        if (bitmap == null) {
            bitmap = thumbnailCache.getOrDecodeThumbnail(targetVideo)
        }
    }

    val progress = remember(targetVideo.stableId) {
        Animatable(if (phase == TransitionPhase.CLOSING) 1f else 0f)
    }

    LaunchedEffect(phase, targetVideo.stableId) {
        when (phase) {
            TransitionPhase.OPENING -> {
                progress.snapTo(0f)
                progress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 330, easing = FastOutSlowInEasing)
                )
                latestOpeningFinished()
            }
            TransitionPhase.CLOSING -> {
                progress.snapTo(1f)
                progress.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
                )
                latestClosingFinished()
            }
            else -> Unit
        }
    }

    // Uncover rule: geometry finished (phase == IN_PLAYER) AND first frame rendered.
    LaunchedEffect(phase, transitionState.isFirstFrameReady, targetVideo.stableId) {
        if (phase == TransitionPhase.IN_PLAYER) {
            if (transitionState.isFirstFrameReady) {
                latestDismissed()
            } else {
                delay(1_400L)
                latestDismissed()
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            // Swallow touches while the cover is up so the player underneath is not triggered.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                    }
                }
            }
    ) {
        val viewportW = constraints.maxWidth.toFloat()
        val viewportH = constraints.maxHeight.toFloat()
        val fullScreenRect = Rect(0f, 0f, viewportW, viewportH)

        val fallbackRect = Rect(
            left = viewportW * 0.35f,
            top = viewportH * 0.35f,
            right = viewportW * 0.65f,
            bottom = viewportH * 0.65f
        )
        val origin = transitionState.startBounds
        val originRect = if (origin != null &&
            origin.bottom > 0f && origin.top < viewportH &&
            origin.right > 0f && origin.left < viewportW
        ) {
            origin
        } else {
            fallbackRect
        }

        val t = (if (phase == TransitionPhase.IN_PLAYER) 1f else progress.value).coerceIn(0f, 1f)
        val left = lerp(originRect.left, fullScreenRect.left, t)
        val top = lerp(originRect.top, fullScreenRect.top, t)
        val width = lerp(originRect.width, fullScreenRect.width, t)
        val height = lerp(originRect.height, fullScreenRect.height, t)
        val cornerRadius = lerp(14f, 0f, t).dp

        // Scrim fades in with the expansion and out with the collapse.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = (t * 0.96f).coerceIn(0f, 1f)))
        )

        Box(
            modifier = Modifier
                .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                .size(
                    width = with(density) { width.toDp() },
                    height = with(density) { height.toDp() }
                )
                .clip(RoundedCornerShape(cornerRadius))
                .background(Color(0xFF0A0A0E)),
            contentAlignment = Alignment.Center
        ) {
            val bmp = bitmap
            if (bmp != null && !bmp.isRecycled) {
                val aspect = targetVideo.aspectRatio
                if (isCoverFit(aspect)) {
                    // Same crop as the grid cell and as the live video: no mode switch needed.
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Grid cell shows a centre crop; fullscreen shows the whole letterboxed frame.
                    // Cross-blend the two so there is no pop at either end.
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(t)
                    )
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(1f - t)
                    )
                }
            }
        }
    }
}

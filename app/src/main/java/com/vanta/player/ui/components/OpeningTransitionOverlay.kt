package com.vanta.player.ui.components

import android.graphics.Bitmap
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.vanta.player.data.ThumbnailCacheManager
import com.vanta.player.model.OpeningTransitionState
import com.vanta.player.model.TransitionPhase
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Section 6:
 * - Smooth expansion from the tapped grid tile into the full-screen player; baseline opening duration ~280ms.
 * - Retain visual continuity while decoding. Remove the opening artwork only after animation completion
 *   AND an actual frame presentation at full-screen size (`isFirstFrameReady`), not merely player preparation.
 * - Respect disabled system animations.
 */
@Composable
fun OpeningTransitionOverlay(
    transitionState: OpeningTransitionState,
    thumbnailCache: ThumbnailCacheManager,
    onOpeningAnimationFinished: () -> Unit,
    onClosingAnimationFinished: () -> Unit
) {
    val phase = transitionState.phase
    val targetVideo = transitionState.targetVideo
    if (phase == TransitionPhase.IDLE || targetVideo == null) return

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

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    val fullScreenRect = remember(screenWidthPx, screenHeightPx) {
        Rect(0f, 0f, screenWidthPx, screenHeightPx)
    }

    val fallbackRect = remember(screenWidthPx, screenHeightPx) {
        val w = screenWidthPx * 0.45f
        val h = screenHeightPx * 0.45f
        Rect(
            left = (screenWidthPx - w) / 2f,
            top = (screenHeightPx - h) / 2f,
            right = (screenWidthPx + w) / 2f,
            bottom = (screenHeightPx + h) / 2f
        )
    }

    val originRect = transitionState.startBounds ?: fallbackRect

    var bitmap by remember(targetVideo.thumbnailCacheKey) {
        mutableStateOf<Bitmap?>(thumbnailCache.getMemoryCached(targetVideo))
    }

    LaunchedEffect(targetVideo.thumbnailCacheKey) {
        if (bitmap == null) {
            bitmap = thumbnailCache.getOrDecodeThumbnail(targetVideo)
        }
    }

    val progress = remember(targetVideo.stableId) {
        Animatable(if (phase == TransitionPhase.CLOSING) 1f else 0f)
    }

    var safetyValveTriggered by remember(targetVideo.stableId) { mutableStateOf(false) }

    LaunchedEffect(phase, targetVideo.stableId) {
        when (phase) {
            TransitionPhase.OPENING -> {
                safetyValveTriggered = false
                if (!animationsEnabled) {
                    progress.snapTo(1f)
                    onOpeningAnimationFinished()
                } else {
                    progress.snapTo(0f)
                    progress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(
                            durationMillis = 280,
                            easing = FastOutSlowInEasing
                        )
                    )
                    onOpeningAnimationFinished()
                }
            }
            TransitionPhase.CLOSING -> {
                if (!animationsEnabled) {
                    progress.snapTo(0f)
                    onClosingAnimationFinished()
                } else {
                    progress.snapTo(1f)
                    progress.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(
                            durationMillis = 240,
                            easing = FastOutSlowInEasing
                        )
                    )
                    onClosingAnimationFinished()
                }
            }
            else -> Unit
        }
    }

    LaunchedEffect(phase, targetVideo.stableId) {
        if (phase == TransitionPhase.OPENING || phase == TransitionPhase.IN_PLAYER) {
            delay(1_400L)
            safetyValveTriggered = true
        }
    }

    val isGeometryAtFullscreen = progress.value >= 0.995f
    val canHideCoverInPlayer = phase == TransitionPhase.IN_PLAYER &&
        isGeometryAtFullscreen &&
        (transitionState.isFirstFrameReady || safetyValveTriggered)

    if (canHideCoverInPlayer) {
        return
    }

    val t = progress.value.coerceIn(0f, 1f)
    val currentLeft = lerp(originRect.left, fullScreenRect.left, t)
    val currentTop = lerp(originRect.top, fullScreenRect.top, t)
    val currentWidth = lerp(originRect.width, fullScreenRect.width, t)
    val currentHeight = lerp(originRect.height, fullScreenRect.height, t)
    val cornerRadiusDp = lerp(6f, 0f, t).dp

    val widthDp = with(density) { currentWidth.toDp() }
    val heightDp = with(density) { currentHeight.toDp() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = (t * 0.96f).coerceIn(0f, 1f)))
    ) {
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = currentLeft.roundToInt(),
                        y = currentTop.roundToInt()
                    )
                }
                .size(width = widthDp, height = heightDp)
                .clip(RoundedCornerShape(cornerRadiusDp))
                .background(Color(0xFF0F0F0F)),
            contentAlignment = Alignment.Center
        ) {
            val bmp = bitmap
            if (bmp != null && !bmp.isRecycled) {
                val contentScale = if (t < 0.7f) ContentScale.Crop else ContentScale.Fit
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = targetVideo.fileName,
                    contentScale = contentScale,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

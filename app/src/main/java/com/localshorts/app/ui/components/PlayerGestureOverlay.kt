package com.localshorts.app.ui.components

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localshorts.app.model.MiddleGestureAction
import com.localshorts.app.ui.theme.LocalHollowColors
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class ActiveHudType {
    NONE,
    BRIGHTNESS,
    VOLUME
}

/**
 * Wraps the player content so tap and edge gestures live on the PARENT of the pager. Pointer events
 * reach the pager (child) first; once it claims a vertical drag the tap detector here is cancelled.
 *
 * - Single tap: play / pause.
 * - Double tap (unless disabled): favourite or info toggle.
 * - Outer 15% left gutter, vertical drag: window brightness (only if enabled).
 * - Outer 15% right gutter, vertical drag: media volume (only if enabled).
 * The pager ignores gestures that start in an enabled gutter, so the two never fight.
 */
@Composable
fun PlayerGestureOverlay(
    brightnessGestureEnabled: Boolean,
    volumeGestureEnabled: Boolean,
    middleGestureAction: MiddleGestureAction,
    onSingleTapPlayPause: () -> Unit,
    onDoubleTapFavourite: () -> Unit,
    onDoubleTapToggleInfo: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val hollowColors = LocalHollowColors.current

    val latestTap by rememberUpdatedState(onSingleTapPlayPause)
    val latestDoubleFav by rememberUpdatedState(onDoubleTapFavourite)
    val latestDoubleInfo by rememberUpdatedState(onDoubleTapToggleInfo)

    var activeHud by remember { mutableStateOf(ActiveHudType.NONE) }
    var hudValueFraction by remember { mutableFloatStateOf(0.5f) }
    var hudTriggerTick by remember { mutableLongStateOf(0L) }
    var showHeartBurst by remember { mutableStateOf(false) }

    LaunchedEffect(hudTriggerTick) {
        if (hudTriggerTick > 0L) {
            delay(950L)
            activeHud = ActiveHudType.NONE
        }
    }

    LaunchedEffect(showHeartBurst) {
        if (showHeartBurst) {
            delay(650L)
            showHeartBurst = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(middleGestureAction) {
                val onDouble: ((Offset) -> Unit)? =
                    if (middleGestureAction == MiddleGestureAction.DISABLED) {
                        null
                    } else {
                        { _: Offset ->
                            when (middleGestureAction) {
                                MiddleGestureAction.DOUBLE_TAP_FAVOURITE -> {
                                    showHeartBurst = true
                                    latestDoubleFav()
                                }
                                MiddleGestureAction.SHOW_VIDEO_INFO -> latestDoubleInfo()
                                MiddleGestureAction.DISABLED -> Unit
                            }
                        }
                    }
                detectTapGestures(
                    onTap = { latestTap() },
                    onDoubleTap = onDouble
                )
            }
            .pointerInput(brightnessGestureEnabled, volumeGestureEnabled) {
                if (!brightnessGestureEnabled && !volumeGestureEnabled) return@pointerInput
                val touchSlop = viewConfiguration.touchSlop

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val widthPx = size.width.toFloat().coerceAtLeast(1f)
                    val heightPx = size.height.toFloat().coerceAtLeast(1f)
                    val startX = down.position.x
                    val startY = down.position.y

                    val inVerticalSafeZone = startY in (heightPx * 0.15f)..(heightPx * 0.82f)
                    val isLeftEdge = inVerticalSafeZone && startX <= widthPx * 0.15f && brightnessGestureEnabled
                    val isRightEdge = inVerticalSafeZone && startX >= widthPx * 0.85f && volumeGestureEnabled
                    if (!isLeftEdge && !isRightEdge) return@awaitEachGesture

                    var locked = false
                    var totalDy = 0f
                    var totalDx = 0f

                    var currentBrightness = activity?.window?.attributes?.screenBrightness
                        ?.takeIf { it >= 0f } ?: 0.55f

                    val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                    var currentVolFloat = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break

                        val delta = change.positionChange()
                        totalDx += delta.x
                        totalDy += delta.y

                        if (!locked) {
                            if (abs(totalDy) > touchSlop && abs(totalDy) > abs(totalDx) * 1.3f) {
                                locked = true
                                change.consume()
                            } else if (abs(totalDx) > touchSlop) {
                                break
                            }
                        } else {
                            change.consume()
                            val deltaFraction = -delta.y / (heightPx * 0.55f)
                            if (isLeftEdge) {
                                currentBrightness = (currentBrightness + deltaFraction).coerceIn(0.02f, 1.0f)
                                activity?.window?.let { win ->
                                    val params = win.attributes
                                    params.screenBrightness = currentBrightness
                                    win.attributes = params
                                }
                                activeHud = ActiveHudType.BRIGHTNESS
                                hudValueFraction = currentBrightness
                                hudTriggerTick = System.currentTimeMillis()
                            } else {
                                currentVolFloat = (currentVolFloat + deltaFraction * maxVol)
                                    .coerceIn(0f, maxVol.toFloat())
                                audioManager.setStreamVolume(
                                    AudioManager.STREAM_MUSIC,
                                    currentVolFloat.roundToInt().coerceIn(0, maxVol),
                                    0
                                )
                                activeHud = ActiveHudType.VOLUME
                                hudValueFraction = currentVolFloat / maxVol.toFloat()
                                hudTriggerTick = System.currentTimeMillis()
                            }
                        }
                    }
                }
            }
    ) {
        content()

        AnimatedVisibility(
            visible = activeHud != ActiveHudType.NONE,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 68.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xCC12131A))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Icon(
                    imageVector = if (activeHud == ActiveHudType.BRIGHTNESS) {
                        Icons.Rounded.BrightnessHigh
                    } else {
                        Icons.AutoMirrored.Rounded.VolumeUp
                    },
                    contentDescription = null,
                    tint = hollowColors.accentPrimary,
                    modifier = Modifier.size(18.dp)
                )

                Box(
                    modifier = Modifier
                        .width(110.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.2f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width((110f * hudValueFraction.coerceIn(0f, 1f)).dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(hollowColors.accentPrimary)
                    )
                }

                Text(
                    text = "${(hudValueFraction * 100).roundToInt()}%",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        AnimatedVisibility(
            visible = showHeartBurst,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(38.dp))
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Favorite,
                    contentDescription = null,
                    tint = hollowColors.accentPrimary,
                    modifier = Modifier.size(42.dp)
                )
            }
        }
    }
}

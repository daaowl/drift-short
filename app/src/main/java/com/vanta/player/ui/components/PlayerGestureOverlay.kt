package com.vanta.player.ui.components

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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vanta.player.ui.theme.LocalVantaColors
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class ActiveHudType {
    NONE,
    BRIGHTNESS,
    VOLUME
}

/**
 * Implements Section 7:
 * - Left quarter (0..25%): vertical brightness adjustment (when enabled).
 * - Right quarter (75..100%): vertical media-volume adjustment (when enabled).
 * - Middle half (25..75%): change video.
 * - Disabling a side gesture makes that region available for changing videos.
 * - Brightness applies only to the app window.
 */
@Composable
fun PlayerGestureOverlay(
    brightnessGestureEnabled: Boolean,
    volumeGestureEnabled: Boolean,
    onSingleTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val vantaColors = LocalVantaColors.current

    var activeHud by remember { mutableStateOf(ActiveHudType.NONE) }
    var hudValueFraction by remember { mutableFloatStateOf(0.5f) }
    var hudTriggerTick by remember { mutableStateOf(0L) }

    LaunchedEffect(hudTriggerTick) {
        if (hudTriggerTick > 0L) {
            delay(900L)
            activeHud = ActiveHudType.NONE
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onSingleTap() }
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

                    val inVerticalSafeZone = startY in (heightPx * 0.12f)..(heightPx * 0.85f)
                    // Left quarter = 0..25%, Right quarter = 75..100%
                    val isLeftQuarter = inVerticalSafeZone && startX <= widthPx * 0.25f && brightnessGestureEnabled
                    val isRightQuarter = inVerticalSafeZone && startX >= widthPx * 0.75f && volumeGestureEnabled

                    if (!isLeftQuarter && !isRightQuarter) {
                        // Falls through to VerticalShortsPager so disabled side gestures change videos
                        return@awaitEachGesture
                    }

                    var lockedEdgeGesture = false
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

                        if (!lockedEdgeGesture) {
                            if (abs(totalDy) > touchSlop && abs(totalDy) > abs(totalDx) * 1.25f) {
                                lockedEdgeGesture = true
                                change.consume()
                            } else if (abs(totalDx) > touchSlop) {
                                break
                            }
                        } else {
                            change.consume()
                            val deltaFraction = -delta.y / (heightPx * 0.55f)
                            if (isLeftQuarter) {
                                currentBrightness = (currentBrightness + deltaFraction).coerceIn(0.01f, 1.0f)
                                activity?.window?.let { win ->
                                    val params = win.attributes
                                    params.screenBrightness = currentBrightness
                                    win.attributes = params
                                }
                                activeHud = ActiveHudType.BRIGHTNESS
                                hudValueFraction = currentBrightness
                                hudTriggerTick = System.currentTimeMillis()
                            } else if (isRightQuarter) {
                                currentVolFloat = (currentVolFloat + deltaFraction * maxVol)
                                    .coerceIn(0f, maxVol.toFloat())
                                val targetIndex = currentVolFloat.roundToInt().coerceIn(0, maxVol)
                                audioManager.setStreamVolume(
                                    AudioManager.STREAM_MUSIC,
                                    targetIndex,
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
        AnimatedVisibility(
            visible = activeHud != ActiveHudType.NONE,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 64.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xD9161616))
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Icon(
                    imageVector = if (activeHud == ActiveHudType.BRIGHTNESS) {
                        Icons.Rounded.BrightnessHigh
                    } else {
                        Icons.AutoMirrored.Rounded.VolumeUp
                    },
                    contentDescription = null,
                    tint = vantaColors.accent,
                    modifier = Modifier.size(18.dp)
                )

                Box(
                    modifier = Modifier
                        .width(96.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.22f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width((96f * hudValueFraction.coerceIn(0f, 1f)).dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(vantaColors.accent)
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
    }
}

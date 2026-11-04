package com.vanta.player.ui.screens

import android.view.TextureView
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.vanta.player.model.AppSettings
import com.vanta.player.model.SUPPORTED_PLAYBACK_SPEEDS
import com.vanta.player.model.VideoItem
import com.vanta.player.model.formatTimestampMs
import com.vanta.player.player.PlaybackCoordinator
import com.vanta.player.player.PlaybackUiState
import com.vanta.player.player.SlotRole
import com.vanta.player.ui.components.PlayerGestureOverlay
import com.vanta.player.ui.components.VerticalShortsPager
import com.vanta.player.ui.theme.LocalVantaColors
import kotlinx.coroutines.delay
import kotlin.math.roundToLong

/**
 * Implements Section 5 & Section 6:
 * - Immersive full-screen player, correct aspect ratio, no stretching; fit the whole video with black space where needed.
 * - Tap the screen to pause/resume. Compact central play indicator when paused.
 * - Small top-left Back button.
 * - Small top-right three-dot menu available ONLY when paused (Playback speed, Add/remove favourite, Change thumbnail, Back to library).
 * - Transparent compact bottom controls: saved date from file metadata, title without recognised video extension,
 *   thin seek bar with a small thumb, and elapsed/total time.
 * - Controls fade away after 1.25 seconds (1250ms) of inactivity; prevented from hiding while dragging seek bar.
 * - Adjacent player pages do NOT request thumbnail artwork; they show actual decoded video frames from the bounded pool.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    playbackState: PlaybackUiState,
    settings: AppSettings,
    playbackCoordinator: PlaybackCoordinator,
    onClosePlayer: () -> Unit,
    onToggleFavourite: (String) -> Unit,
    onSelectSpeed: (Float) -> Unit,
    onOpenCustomThumbnailEditor: (VideoItem) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(enabled = playbackState.isPlayerOpen) {
        onClosePlayer()
    }

    val currentVideo = playbackState.currentVideo ?: return
    val vantaColors = LocalVantaColors.current

    var controlsVisible by remember { mutableStateOf(true) }
    var lastInteractionTimestamp by remember { mutableStateOf(System.currentTimeMillis()) }
    var isThreeDotMenuOpen by remember { mutableStateOf(false) }
    var isSpeedSubmenuOpen by remember { mutableStateOf(false) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableFloatStateOf(0f) }

    // Section 5: Controls fade away after 1.25 seconds (1250ms) of inactivity
    LaunchedEffect(
        lastInteractionTimestamp,
        playbackState.isUserPaused,
        isThreeDotMenuOpen,
        isSpeedSubmenuOpen,
        isScrubbing,
        playbackState.currentIndex
    ) {
        controlsVisible = true
        if (!playbackState.isUserPaused && !isThreeDotMenuOpen && !isSpeedSubmenuOpen && !isScrubbing) {
            delay(1_250L)
            controlsVisible = false
        }
    }

    val textShadow = remember {
        Shadow(
            color = Color.Black.copy(alpha = 0.85f),
            offset = Offset(0f, 1.5f),
            blurRadius = 4f
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. Vertical Shorts Pager (wraps around collection ends; handles 1-video gracefully)
        VerticalShortsPager(
            currentIndex = playbackState.currentIndex,
            totalCount = playbackState.playlist.size,
            transitionSpeed = settings.shortTransitionSpeed,
            autoAdvanceEvents = playbackCoordinator.autoAdvanceEvent,
            onContinuousDragFraction = { fraction ->
                playbackCoordinator.onContinuousDrag(fraction)
            },
            onSwipeSettled = { delta ->
                isThreeDotMenuOpen = false
                isSpeedSubmenuOpen = false
                lastInteractionTimestamp = System.currentTimeMillis()
                playbackCoordinator.onSwipeSettled(delta)
            }
        ) { role ->
            val slotVideo = when (role) {
                SlotRole.PREV -> playbackState.prevVideo
                SlotRole.CURRENT -> playbackState.currentVideo
                SlotRole.NEXT -> playbackState.nextVideo
            }
            val slotIndex = when (role) {
                SlotRole.PREV -> playbackState.prevSlotIndex
                SlotRole.CURRENT -> playbackState.currentSlotIndex
                SlotRole.NEXT -> playbackState.nextSlotIndex
            }

            if (slotVideo != null) {
                DecodedVideoPageViewport(
                    video = slotVideo,
                    slotIndex = slotIndex,
                    playbackState = playbackState,
                    playbackCoordinator = playbackCoordinator
                )
            }
        }

        // 2. Gesture Layer (Left 25% Brightness, Right 25% Volume, Middle 50% Video Swipe + Tap Pause/Resume)
        PlayerGestureOverlay(
            brightnessGestureEnabled = settings.gestureBrightnessEnabled,
            volumeGestureEnabled = settings.gestureVolumeEnabled,
            onSingleTap = {
                lastInteractionTimestamp = System.currentTimeMillis()
                controlsVisible = true
                playbackCoordinator.togglePlayPause()
            }
        )

        // 3. Compact Central Play Indicator when paused
        AnimatedVisibility(
            visible = playbackState.isUserPaused || playbackState.isStoppedAtFinalFrame,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.52f))
                    .clickable {
                        lastInteractionTimestamp = System.currentTimeMillis()
                        playbackCoordinator.togglePlayPause()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "Play video",
                    tint = Color.White,
                    modifier = Modifier.size(34.dp)
                )
            }
        }

        // 4. Top Controls: Small top-left Back button & top-right three-dot menu (available ONLY when paused)
        AnimatedVisibility(
            visible = controlsVisible || playbackState.isUserPaused || playbackState.isStoppedAtFinalFrame,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                IconButton(
                    onClick = onClosePlayer,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.38f))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Section 5: Small top-right three-dot menu available ONLY when paused
                if (playbackState.isUserPaused || playbackState.isStoppedAtFinalFrame) {
                    Box {
                        IconButton(
                            onClick = {
                                lastInteractionTimestamp = System.currentTimeMillis()
                                isThreeDotMenuOpen = true
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.38f))
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "More options",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = isThreeDotMenuOpen,
                            onDismissRequest = {
                                isThreeDotMenuOpen = false
                                isSpeedSubmenuOpen = false
                            },
                            modifier = Modifier.background(vantaColors.surfaceElevated)
                        ) {
                            if (!isSpeedSubmenuOpen) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "Playback speed (${playbackState.playbackSpeed}×)",
                                            color = vantaColors.textPrimary,
                                            fontSize = 13.sp
                                        )
                                    },
                                    onClick = {
                                        isSpeedSubmenuOpen = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = if (currentVideo.isFavourite) "Remove favourite" else "Add favourite",
                                            color = vantaColors.textPrimary,
                                            fontSize = 13.sp
                                        )
                                    },
                                    onClick = {
                                        isThreeDotMenuOpen = false
                                        onToggleFavourite(currentVideo.stableId)
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "Change thumbnail",
                                            color = vantaColors.textPrimary,
                                            fontSize = 13.sp
                                        )
                                    },
                                    onClick = {
                                        isThreeDotMenuOpen = false
                                        onOpenCustomThumbnailEditor(currentVideo)
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "Back to library",
                                            color = vantaColors.textPrimary,
                                            fontSize = 13.sp
                                        )
                                    },
                                    onClick = {
                                        isThreeDotMenuOpen = false
                                        onClosePlayer()
                                    }
                                )
                            } else {
                                SUPPORTED_PLAYBACK_SPEEDS.forEach { spd ->
                                    val isSelected = playbackState.playbackSpeed == spd
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "${spd}×",
                                                color = if (isSelected) vantaColors.accent else vantaColors.textPrimary,
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                            )
                                        },
                                        trailingIcon = {
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Check,
                                                    contentDescription = null,
                                                    tint = vantaColors.accent,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        },
                                        onClick = {
                                            onSelectSpeed(spd)
                                            isSpeedSubmenuOpen = false
                                            isThreeDotMenuOpen = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Transparent compact bottom controls: saved date, title without extension, thin seek bar, elapsed/total time
        AnimatedVisibility(
            visible = controlsVisible || playbackState.isUserPaused || playbackState.isStoppedAtFinalFrame || isScrubbing,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            val durationMs = playbackState.currentDurationMs.coerceAtLeast(1L)
            val displayedPosMs = if (isScrubbing) {
                scrubPositionMs.roundToLong().coerceIn(0L, durationMs)
            } else {
                playbackState.currentPositionMs.coerceIn(0L, durationMs)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                // Saved date from file metadata
                Text(
                    text = currentVideo.formattedSavedDate,
                    style = TextStyle(
                        color = Color.White.copy(alpha = 0.76f),
                        fontSize = 11.sp,
                        shadow = textShadow
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))

                // Title without recognised video extension, single-line ellipsised, subtle text shadow
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = currentVideo.titleWithoutExtension,
                        style = TextStyle(
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            shadow = textShadow
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    )

                    Text(
                        text = "${formatTimestampMs(displayedPosMs)} / ${formatTimestampMs(durationMs)}",
                        style = TextStyle(
                            color = Color.White.copy(alpha = 0.88f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            shadow = textShadow
                        )
                    )
                }

                // Thin seek bar with a small thumb using the selected accent
                Slider(
                    value = displayedPosMs.toFloat(),
                    onValueChange = { newPos ->
                        isScrubbing = true
                        scrubPositionMs = newPos
                        lastInteractionTimestamp = System.currentTimeMillis()
                    },
                    onValueChangeFinished = {
                        playbackCoordinator.seekTo(scrubPositionMs.roundToLong())
                        isScrubbing = false
                        lastInteractionTimestamp = System.currentTimeMillis()
                    },
                    valueRange = 0f..durationMs.toFloat(),
                    thumb = {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(vantaColors.accent)
                        )
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = vantaColors.accent,
                        activeTrackColor = vantaColors.accent,
                        inactiveTrackColor = vantaColors.accent.copy(alpha = 0.28f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                )
            }
        }
    }
}

/**
 * Section 5 & Section 6:
 * - Fit the whole video with black space where needed (`Modifier.aspectRatio(aspectRatio)` inside centered Box).
 * - Preserve v1.19's fix: adjacent player pages do NOT request thumbnail artwork; they show actual decoded
 *   video frames from the pre-warmed ExoPlayer pool.
 */
@Composable
private fun DecodedVideoPageViewport(
    video: VideoItem,
    slotIndex: Int,
    playbackState: PlaybackUiState,
    playbackCoordinator: PlaybackCoordinator
) {
    val managedSlot = remember(slotIndex) { playbackCoordinator.getManagedSlot(slotIndex) }
    val slotSnapshot = playbackState.slotSnapshots[slotIndex]

    val aspectRatio = if (slotSnapshot != null && slotSnapshot.videoWidth > 0 && slotSnapshot.videoHeight > 0) {
        (slotSnapshot.videoWidth.toFloat() / slotSnapshot.videoHeight.toFloat()).coerceIn(0.25f, 3.5f)
    } else {
        video.aspectRatio
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                TextureView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    isOpaque = false
                    managedSlot.attachTextureView(this)
                }
            },
            update = { textureView ->
                managedSlot.attachTextureView(textureView)
            },
            modifier = Modifier
                .fillMaxSize()
                .aspectRatio(aspectRatio, matchHeightConstraintsFirst = aspectRatio < 0.6f)
        )

        DisposableEffect(managedSlot) {
            onDispose { }
        }

        if (slotSnapshot?.hasError == true) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.78f))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    text = slotSnapshot.errorMessage ?: "Unplayable video format",
                    color = Color.White,
                    fontSize = 13.sp
                )
            }
        }
    }
}

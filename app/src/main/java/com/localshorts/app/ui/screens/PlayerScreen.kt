package com.localshorts.app.ui.screens

import android.graphics.Bitmap
import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localshorts.app.data.ThumbnailCacheManager
import com.localshorts.app.model.AppSettings
import com.localshorts.app.model.EndBehavior
import com.localshorts.app.model.IconStylePreset
import com.localshorts.app.model.SUPPORTED_PLAYBACK_SPEEDS
import com.localshorts.app.model.VideoItem
import com.localshorts.app.player.PlaybackCoordinator
import com.localshorts.app.player.PlaybackUiState
import com.localshorts.app.ui.components.PlayerGestureOverlay
import com.localshorts.app.ui.components.VerticalShortsPager
import com.localshorts.app.ui.components.placedVideoSize
import com.localshorts.app.ui.theme.LocalHollowColors
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun PlayerScreen(
    playbackState: PlaybackUiState,
    settings: AppSettings,
    playbackCoordinator: PlaybackCoordinator,
    thumbnailCache: ThumbnailCacheManager,
    onClosePlayer: () -> Unit,
    onToggleFavourite: (String) -> Unit,
    onSelectSpeed: (Float) -> Unit,
    onSelectEndBehavior: (EndBehavior) -> Unit,
    onToggleVideoInfo: () -> Unit,
    onOpenSettings: () -> Unit,
    onSetThumbnail: (String) -> Unit,
    onResetThumbnail: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(enabled = playbackState.isPlayerOpen) {
        onClosePlayer()
    }

    val currentVideo = playbackState.currentVideo ?: return
    val hollowColors = LocalHollowColors.current

    var controlsVisible by remember { mutableStateOf(true) }
    var lastInteractionTimestamp by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var isSpeedMenuExpanded by remember { mutableStateOf(false) }
    var isScrubbing by remember { mutableStateOf(false) }
    var thumbnailToast by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(thumbnailToast) {
        if (thumbnailToast != null) {
            delay(1_600L)
            thumbnailToast = null
        }
    }

    // Auto-hide the seek bar and chrome after 2.8 s of idle playback.
    LaunchedEffect(
        lastInteractionTimestamp,
        playbackState.isUserPaused,
        isSpeedMenuExpanded,
        isScrubbing,
        playbackState.currentIndex
    ) {
        controlsVisible = true
        if (!playbackState.isUserPaused && !isSpeedMenuExpanded && !isScrubbing) {
            delay(2_800L)
            controlsVisible = false
        }
    }

    PlayerGestureOverlay(
        brightnessGestureEnabled = settings.gestureBrightnessEnabled,
        volumeGestureEnabled = settings.gestureVolumeEnabled,
        middleGestureAction = settings.middleGestureAction,
        onSingleTapPlayPause = {
            lastInteractionTimestamp = System.currentTimeMillis()
            controlsVisible = true
            if (isSpeedMenuExpanded) {
                isSpeedMenuExpanded = false
            } else {
                playbackCoordinator.togglePlayPause()
            }
        },
        onDoubleTapFavourite = {
            lastInteractionTimestamp = System.currentTimeMillis()
            if (!currentVideo.isFavourite) {
                onToggleFavourite(currentVideo.stableId)
            }
        },
        onDoubleTapToggleInfo = {
            lastInteractionTimestamp = System.currentTimeMillis()
            onToggleVideoInfo()
        },
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. Vertical Shorts pager: three permanent pooled slots, 1:1 finger tracking.
        VerticalShortsPager(
            currentIndex = playbackState.currentIndex,
            totalCount = playbackState.playlist.size,
            transitionSpeed = settings.swipeTransitionSpeed,
            autoAdvanceEvents = playbackCoordinator.autoAdvanceEvent,
            leftEdgeReserved = settings.gestureBrightnessEnabled,
            rightEdgeReserved = settings.gestureVolumeEnabled,
            onContinuousDragFraction = { fraction ->
                playbackCoordinator.onContinuousDrag(fraction)
            },
            onSwipeSettled = { delta ->
                isSpeedMenuExpanded = false
                lastInteractionTimestamp = System.currentTimeMillis()
                playbackCoordinator.onSwipeSettled(delta)
            }
        ) { slotIndex, videoIndex ->
            val slotVideo = playbackState.playlist.getOrNull(videoIndex)
            if (slotVideo != null) {
                VideoSlotViewport(
                    video = slotVideo,
                    slotIndex = slotIndex,
                    playbackState = playbackState,
                    playbackCoordinator = playbackCoordinator,
                    thumbnailCache = thumbnailCache
                )
            }
        }

        AnimatedVisibility(
            visible = thumbnailToast != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 64.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xCC12131A))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = thumbnailToast ?: "",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // 2. Centre resume indicator when paused by the user.
        AnimatedVisibility(
            visible = playbackState.isUserPaused,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
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
                    contentDescription = "Resume",
                    tint = Color.White,
                    modifier = Modifier.size(38.dp)
                )
            }
        }

        // 3. Top bar: back, info toggle, settings.
        AnimatedVisibility(
            visible = controlsVisible || playbackState.isUserPaused,
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
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.62f), Color.Transparent)
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                PlayerActionIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back to gallery",
                    iconStyle = settings.iconStylePreset,
                    onClick = onClosePlayer
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PlayerActionIconButton(
                        icon = Icons.Rounded.Info,
                        contentDescription = "Toggle video info",
                        iconStyle = settings.iconStylePreset,
                        tint = if (settings.showVideoInfoOverlay) hollowColors.accentPrimary else Color.White,
                        onClick = {
                            lastInteractionTimestamp = System.currentTimeMillis()
                            onToggleVideoInfo()
                        }
                    )
                    PlayerActionIconButton(
                        icon = Icons.Rounded.Settings,
                        contentDescription = "Settings",
                        iconStyle = settings.iconStylePreset,
                        onClick = onOpenSettings
                    )
                }
            }
        }

        // 4. Right column: favourite, speed, end behaviour.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 14.dp, bottom = 58.dp)
        ) {
            PlayerActionIconButton(
                icon = if (currentVideo.isFavourite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = "Favourite",
                iconStyle = settings.iconStylePreset,
                tint = if (currentVideo.isFavourite) hollowColors.accentPrimary else Color.White,
                onClick = {
                    lastInteractionTimestamp = System.currentTimeMillis()
                    onToggleFavourite(currentVideo.stableId)
                }
            )

            // Tap: use the frame on screen as the gallery thumbnail. Long-press: back to automatic.
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .border(
                        width = 1.dp,
                        color = if (currentVideo.hasCustomThumbnail) {
                            hollowColors.accentPrimary.copy(alpha = 0.7f)
                        } else {
                            Color.White.copy(alpha = 0.14f)
                        },
                        shape = CircleShape
                    )
                    .pointerInput(currentVideo.stableId) {
                        detectTapGestures(
                            onTap = {
                                lastInteractionTimestamp = System.currentTimeMillis()
                                onSetThumbnail(currentVideo.stableId)
                                thumbnailToast = "Thumbnail set"
                            },
                            onLongPress = {
                                lastInteractionTimestamp = System.currentTimeMillis()
                                onResetThumbnail(currentVideo.stableId)
                                thumbnailToast = "Thumbnail reset to default"
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Image,
                    contentDescription = "Set current frame as thumbnail",
                    tint = if (currentVideo.hasCustomThumbnail) hollowColors.accentPrimary else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .border(
                        width = 1.dp,
                        color = if (playbackState.playbackSpeed != 1.0f) {
                            hollowColors.accentPrimary.copy(alpha = 0.7f)
                        } else {
                            Color.White.copy(alpha = 0.15f)
                        },
                        shape = RoundedCornerShape(18.dp)
                    )
                    .clickable {
                        lastInteractionTimestamp = System.currentTimeMillis()
                        isSpeedMenuExpanded = !isSpeedMenuExpanded
                    }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Speed,
                        contentDescription = "Playback speed",
                        tint = if (playbackState.playbackSpeed != 1.0f) hollowColors.accentPrimary else Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = formatSpeed(playbackState.playbackSpeed),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(18.dp)
                    )
                    .clickable {
                        lastInteractionTimestamp = System.currentTimeMillis()
                        val nextBehavior = when (playbackState.endBehavior) {
                            EndBehavior.LOOP -> EndBehavior.PLAY_NEXT
                            EndBehavior.PLAY_NEXT -> EndBehavior.STOP
                            EndBehavior.STOP -> EndBehavior.LOOP
                        }
                        onSelectEndBehavior(nextBehavior)
                    }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Repeat,
                        contentDescription = "End behavior",
                        tint = hollowColors.accentPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = playbackState.endBehavior.label,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // 5. Speed strip (0.25x .. 2.25x).
        AnimatedVisibility(
            visible = isSpeedMenuExpanded,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 66.dp, start = 14.dp, end = 78.dp)
        ) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xE614151E))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                items(SUPPORTED_PLAYBACK_SPEEDS) { speed ->
                    val isSelected = playbackState.playbackSpeed == speed
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isSelected) hollowColors.accentPrimary else Color.White.copy(alpha = 0.08f)
                            )
                            .clickable {
                                lastInteractionTimestamp = System.currentTimeMillis()
                                onSelectSpeed(speed)
                                isSpeedMenuExpanded = false
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = formatSpeed(speed),
                            color = if (isSelected) Color(0xFF0A0A0E) else Color.White,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        // 6. Bottom: optional info card + auto-hiding seek bar.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            AnimatedVisibility(visible = settings.showVideoInfoOverlay) {
                Column(
                    modifier = Modifier
                        .padding(end = 76.dp, bottom = 6.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.52f))
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = currentVideo.fileName,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${currentVideo.width}×${currentVideo.height} · ${currentVideo.formattedDuration} · Watched ${currentVideo.watchCount}×",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 11.sp
                    )
                }
            }

            AnimatedVisibility(
                visible = controlsVisible || playbackState.isUserPaused || isScrubbing,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                SeekBar(
                    playbackCoordinator = playbackCoordinator,
                    onScrubbingChanged = { scrubbing ->
                        isScrubbing = scrubbing
                        lastInteractionTimestamp = System.currentTimeMillis()
                    }
                )
            }
        }
    }
}

/** Own composable so the 10 Hz progress updates only recompose the slider. */
@Composable
private fun SeekBar(
    playbackCoordinator: PlaybackCoordinator,
    onScrubbingChanged: (Boolean) -> Unit
) {
    val hollowColors = LocalHollowColors.current
    val progress by playbackCoordinator.progress.collectAsStateWithLifecycle()
    var scrubbing by remember { mutableStateOf(false) }
    var scrubPos by remember { mutableFloatStateOf(0f) }

    val durationMs = progress.durationMs.coerceAtLeast(1L).toFloat()
    val displayed = if (scrubbing) scrubPos else progress.positionMs.toFloat().coerceIn(0f, durationMs)

    Slider(
        value = displayed,
        onValueChange = { newPos ->
            if (!scrubbing) {
                scrubbing = true
                onScrubbingChanged(true)
            }
            scrubPos = newPos
        },
        onValueChangeFinished = {
            playbackCoordinator.seekTo(scrubPos.toLong())
            scrubbing = false
            onScrubbingChanged(false)
        },
        valueRange = 0f..durationMs,
        colors = SliderDefaults.colors(
            thumbColor = hollowColors.accentPrimary,
            activeTrackColor = hollowColors.accentPrimary,
            inactiveTrackColor = Color.White.copy(alpha = 0.24f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp)
    )
}

/**
 * One pooled slot. The TextureView is created once per slot composition and keeps its surface for
 * the whole session; the retained thumbnail sits on top until Media3 reports onRenderedFirstFrame.
 * Placement comes from [placedVideoSize], shared with the opening-transition overlay.
 */
@Composable
private fun VideoSlotViewport(
    video: VideoItem,
    slotIndex: Int,
    playbackState: PlaybackUiState,
    playbackCoordinator: PlaybackCoordinator,
    thumbnailCache: ThumbnailCacheManager
) {
    val managedSlot = remember(slotIndex) { playbackCoordinator.getManagedSlot(slotIndex) }
    val slotSnapshot = playbackState.slotSnapshots[slotIndex]
    val density = LocalDensity.current

    val isFirstFrameRenderedForVideo = slotSnapshot?.boundVideoId == video.stableId &&
        slotSnapshot.isFirstFrameRendered

    var cachedBitmap by remember(video.thumbnailKey) {
        mutableStateOf<Bitmap?>(thumbnailCache.getMemoryCached(video))
    }

    LaunchedEffect(video.thumbnailKey) {
        if (cachedBitmap == null) {
            cachedBitmap = thumbnailCache.getOrDecodeThumbnail(video)
        }
    }

    val textureHolder = remember(slotIndex) { arrayOfNulls<TextureView>(1) }
    DisposableEffect(managedSlot) {
        onDispose {
            textureHolder[0]?.let { managedSlot.detachTextureView(it) }
            textureHolder[0] = null
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clipToBounds(),
        contentAlignment = Alignment.Center
    ) {
        val placed = placedVideoSize(
            aspect = video.aspectRatio,
            viewportW = constraints.maxWidth.toFloat(),
            viewportH = constraints.maxHeight.toFloat()
        )
        val widthDp = with(density) { placed.width.toDp() }
        val heightDp = with(density) { placed.height.toDp() }

        Box(modifier = Modifier.requiredSize(widthDp, heightDp)) {
            AndroidView(
                factory = { ctx ->
                    TextureView(ctx).apply {
                        textureHolder[0] = this
                        managedSlot.attachTextureView(this)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (!isFirstFrameRenderedForVideo) {
                val bmp = cachedBitmap
                if (bmp != null && !bmp.isRecycled) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = video.fileName,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        if (slotSnapshot?.hasError == true && slotSnapshot.boundVideoId == video.stableId) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    text = slotSnapshot.errorMessage ?: "Codec unsupported for this file",
                    color = Color.White,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun PlayerActionIconButton(
    icon: ImageVector,
    contentDescription: String,
    iconStyle: IconStylePreset,
    tint: Color = Color.White,
    onClick: () -> Unit
) {
    val buttonModifier = when (iconStyle) {
        IconStylePreset.MINIMAL -> Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.35f))
        IconStylePreset.SOFT_GLASS -> Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape)
        IconStylePreset.OUTLINED -> Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.45f))
            .border(1.2.dp, Color.White.copy(alpha = 0.32f), RoundedCornerShape(14.dp))
    }

    Box(
        modifier = buttonModifier.clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(21.dp)
        )
    }
}

private fun formatSpeed(speed: Float): String {
    return if (speed % 1.0f == 0f) {
        String.format(Locale.US, "%.0fx", speed)
    } else if ((speed * 10f) % 1.0f == 0f) {
        String.format(Locale.US, "%.1fx", speed)
    } else {
        String.format(Locale.US, "%.2fx", speed)
    }
}

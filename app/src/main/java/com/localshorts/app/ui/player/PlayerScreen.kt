package com.localshorts.app.ui.player

import android.app.Activity
import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import com.localshorts.app.MainViewModel
import com.localshorts.app.data.SPEEDS
import com.localshorts.app.data.ThumbnailLoader
import com.localshorts.app.data.VideoItem
import com.localshorts.app.data.speedLabel
import com.localshorts.app.player.PlaybackCoordinator
import com.localshorts.app.ui.Hc
import com.localshorts.app.ui.LocalAccent
import com.localshorts.app.ui.formatTime
import com.localshorts.app.ui.onAccent
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerScreen(
    vm: MainViewModel,
    videos: List<VideoItem>,
    startIndex: Int,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by vm.settings.collectAsState()
    val favs by vm.favourites.collectAsState()
    val coordinator = remember {
        PlaybackCoordinator(context, videos, scope) { id -> vm.addWatch(id) }.also { it.speed = vm.settings.value.speed }
    }
    val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, (videos.size - 1).coerceAtLeast(0))) { videos.size }

    DisposableEffect(Unit) { onDispose { coordinator.release() } }
    LaunchedEffect(settings.end) { coordinator.endBehavior = settings.end }
    LaunchedEffect(pager) {
        // currentPage switches as soon as a page covers more than half the screen, finger still down
        snapshotFlow { pager.currentPage }.collect { coordinator.setActive(it) }
    }
    coordinator.onEndedNext = {
        scope.launch {
            val next = pager.currentPage + 1
            if (next < videos.size) pager.animateScrollToPage(next) else coordinator.playing.value = false
        }
    }

    // Pause when the app goes to the background, resume when it comes back
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_STOP -> coordinator.onBackground()
                Lifecycle.Event.ON_START -> coordinator.onForeground()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    // Full screen while the player is open
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        val ctl = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        ctl?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        ctl?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { ctl?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    var showSpeed by remember { mutableStateOf(false) }
    BackHandler {
        if (showSpeed) showSpeed = false else onClose()
    }

    val playing by coordinator.playing.collectAsState()
    val failed by coordinator.failed.collectAsState()
    val paused = !playing
    val accent = LocalAccent.current

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        VerticalPager(
            state = pager,
            beyondBoundsPageCount = 1,
            key = { videos[it].id },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures(onTap = { coordinator.toggle() }) },
        ) { page ->
            val inWindow = abs(page - pager.currentPage) <= 1
            VideoPage(
                video = videos[page],
                player = if (inWindow) coordinator.playerFor(page) else null,
                loader = vm.thumbs,
                failed = videos[page].id in failed,
            )
        }

        // Center play mark while paused
        AnimatedVisibility(
            visible = paused && !showSpeed,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(72.dp),
            )
        }

        // Top controls only while paused: back, favourite, speed
        AnimatedVisibility(
            visible = paused,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            val current = videos.getOrNull(pager.currentPage)
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 8.dp, end = 8.dp, top = 28.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close", tint = Color.White)
                }
                Spacer(Modifier.weight(1f))
                if (current != null) {
                    val fav = current.id in favs
                    RoundButton(onClick = { vm.toggleFavourite(current.id) }) {
                        Icon(
                            if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (fav) "Remove favourite" else "Add favourite",
                            tint = if (fav) accent else Color.White,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    speedLabel(coordinator.speed).replace("Normal", "1x"),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0x66000000))
                        .clickable { showSpeed = true }
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                )
            }
        }

        // Seek bar: a thin line while playing, a full scrubber while paused
        SeekBar(
            coordinator = coordinator,
            expanded = paused,
            accent = accent,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        if (showSpeed) {
            SpeedPanel(
                current = coordinator.speed,
                accent = accent,
                onPick = {
                    coordinator.speed = it
                    showSpeed = false
                },
                onDismiss = { showSpeed = false },
            )
        }
    }
}

@Composable
private fun RoundButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color(0x66000000)),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(onClick = onClick) { content() }
    }
}

/**
 * One page of the feed. The start frame (0:00) sits on top of the video surface until the player
 * reports its first rendered frame, so the page never shows a black surface.
 */
@Composable
private fun VideoPage(video: VideoItem, player: ExoPlayer?, loader: ThumbnailLoader, failed: Boolean) {
    val context = LocalContext.current
    val start by produceState(initialValue = loader.cachedStart(video), video.id) {
        if (value == null) value = loader.startFrame(video)
    }
    var rendered by remember(video.id) { mutableStateOf(false) }
    var videoSize by remember(video.id) { mutableStateOf<VideoSize?>(null) }
    val textureView = remember { TextureView(context) }

    if (player != null) {
        DisposableEffect(player, video.id) {
            rendered = false
            val listener = object : Player.Listener {
                override fun onRenderedFirstFrame() {
                    if (player.currentMediaItem?.mediaId == video.id) rendered = true
                }

                override fun onVideoSizeChanged(size: VideoSize) {
                    if (player.currentMediaItem?.mediaId == video.id && size.width > 0 && size.height > 0) videoSize = size
                }
            }
            player.addListener(listener)
            player.setVideoTextureView(textureView)
            onDispose {
                player.removeListener(listener)
                player.clearVideoTextureView(textureView)
                rendered = false
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        val boxRatio = if (maxHeight.value > 0f) maxWidth.value / maxHeight.value else 0.5f
        val ratio: Float? = videoSize?.let { it.width * it.pixelWidthHeightRatio / it.height }
            ?: start?.let { it.width.toFloat() / it.height }
        val fit = if (ratio != null && ratio > 0f) {
            Modifier.aspectRatio(ratio, matchHeightConstraintsFirst = ratio < boxRatio)
        } else {
            Modifier.fillMaxSize()
        }
        if (player != null) {
            AndroidView(factory = { textureView }, modifier = fit)
        }
        val s = start
        if (s != null && !rendered) {
            Image(
                bitmap = s.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().background(Color.Black),
            )
        }
        if (failed) {
            Text(
                "This file can’t be played on this phone",
                color = Hc.Ink,
                fontSize = 14.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xAA000000))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun SeekBar(
    coordinator: PlaybackCoordinator,
    expanded: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val progress by coordinator.progress.collectAsState()
    val duration by coordinator.durationMs.collectAsState()
    var drag by remember { mutableStateOf<Float?>(null) }
    val c by rememberUpdatedState(coordinator)
    val shown = drag ?: progress

    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (expanded || drag != null) {
            Text(
                formatTime((shown * duration).toLong()) + " / " + formatTime(duration),
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x66000000))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
            Spacer(Modifier.height(6.dp))
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(if (expanded) 40.dp else 18.dp)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { o ->
                            val f = (o.x / size.width).coerceIn(0f, 1f)
                            drag = f
                            c.seek(f, exact = false)
                        },
                        onDragEnd = {
                            drag?.let { c.seek(it, exact = true) }
                            drag = null
                        },
                        onDragCancel = { drag = null },
                    ) { change, _ ->
                        change.consume()
                        val f = (change.position.x / size.width).coerceIn(0f, 1f)
                        drag = f
                        c.seek(f, exact = false)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { o -> c.seek((o.x / size.width).coerceIn(0f, 1f), exact = true) }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val h = when {
                    drag != null -> 4.dp.toPx()
                    expanded -> 3.dp.toPx()
                    else -> 2.dp.toPx()
                }
                val y = size.height - h - (if (expanded) 14.dp.toPx() else 0f)
                drawRect(Color.White.copy(alpha = 0.25f), Offset(0f, y), Size(size.width, h))
                drawRect(accent, Offset(0f, y), Size(size.width * shown, h))
                if (expanded || drag != null) {
                    drawCircle(accent, radius = if (drag != null) 8.dp.toPx() else 6.dp.toPx(), center = Offset(size.width * shown, y + h / 2))
                }
            }
        }
    }
}

@Composable
private fun SpeedPanel(current: Float, accent: Color, onPick: (Float) -> Unit, onDismiss: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x55000000))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Hc.Surface)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(16.dp),
        ) {
            Text("Playback speed", color = Hc.Mute, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(12.dp))
            SPEEDS.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { s ->
                        val on = s == current
                        Text(
                            speedLabel(s),
                            color = if (on) onAccent(accent) else Hc.Ink,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (on) accent else Hc.Surface2)
                                .clickable { onPick(s) }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

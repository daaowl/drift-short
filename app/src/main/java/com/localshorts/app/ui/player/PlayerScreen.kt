package com.localshorts.app.ui.player

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.provider.Settings as SystemSettings
import android.view.TextureView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.localshorts.app.data.EndBehavior
import com.localshorts.app.data.InfoStyle
import com.localshorts.app.data.SPEEDS
import com.localshorts.app.data.ThumbnailLoader
import com.localshorts.app.data.TransitionSpeed
import com.localshorts.app.data.VideoItem
import com.localshorts.app.data.speedLabel
import com.localshorts.app.player.PlaybackCoordinator
import com.localshorts.app.ui.Glyph
import com.localshorts.app.ui.GlyphIcon
import com.localshorts.app.ui.Hc
import com.localshorts.app.ui.LocalAccent
import com.localshorts.app.ui.VideoTextShadow
import com.localshorts.app.ui.formatTime
import com.localshorts.app.ui.viewsLabel
import com.localshorts.app.ui.onAccent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import kotlin.math.abs

/** Settling animation after a swipe is released. Never affects how the page follows the finger. */
fun settleSpec(t: TransitionSpeed): AnimationSpec<Float> = when (t) {
    TransitionSpeed.SLOW -> spring(dampingRatio = 1f, stiffness = 110f)
    TransitionSpeed.NORMAL -> spring(dampingRatio = 1f, stiffness = 380f)
    TransitionSpeed.FAST -> spring(dampingRatio = 1f, stiffness = 1400f)
}

private const val HIDE_AFTER_MS = 1250L
private const val SWITCH_AT = 0.53f // hysteresis around the 50% line

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun PlayerScreen(
    vm: MainViewModel,
    videos: List<VideoItem>,
    startIndex: Int,
    entered: Boolean,
    closing: Boolean,
    onFirstFrame: (String) -> Unit,
    onClose: (currentId: String) -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val settings by vm.settings.collectAsState()
    val favs by vm.favourites.collectAsState()
    val accent = LocalAccent.current
    val start = startIndex.coerceIn(0, (videos.size - 1).coerceAtLeast(0))
    val coordinator = remember {
        PlaybackCoordinator(context, videos, scope) { id -> vm.addWatch(id) }.also { it.speed = vm.settings.value.speed }
    }
    val pager = rememberPagerState(initialPage = start) { videos.size }
    var dominant by remember { mutableIntStateOf(start) }

    DisposableEffect(Unit) { onDispose { coordinator.release() } }
    LaunchedEffect(settings.end) { coordinator.endBehavior = settings.end }
    LaunchedEffect(closing) { if (closing) coordinator.hold() }

    // Dominance: whichever page covers more than half the screen plays with audio, finger down or not.
    LaunchedEffect(pager) {
        coordinator.setActive(start)
        snapshotFlow { Triple(pager.currentPage, pager.currentPageOffsetFraction, pager.isScrollInProgress) }
            .collect { (page, fraction, scrolling) ->
                val pos = page + fraction
                var d = dominant
                if (!scrolling) {
                    d = page
                } else {
                    while (pos - d > SWITCH_AT && d < videos.size - 1) d++
                    while (d - pos > SWITCH_AT && d > 0) d--
                }
                if (d != dominant) dominant = d
                coordinator.setActive(d)
                if (!scrolling) coordinator.onSettled(d)
            }
    }
    coordinator.onEndedNext = {
        scope.launch {
            val next = dominant + 1
            if (next < videos.size) pager.animateScrollToPage(next, animationSpec = settleSpec(settings.transition))
            else coordinator.pause()
        }
    }

    // Background / foreground
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

    // Full screen, screen kept awake, window brightness restored on exit
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        val ctl = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        ctl?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        ctl?.hide(WindowInsetsCompat.Type.systemBars())
        view.keepScreenOn = true
        val originalBrightness = window?.attributes?.screenBrightness ?: -1f
        onDispose {
            ctl?.show(WindowInsetsCompat.Type.systemBars())
            view.keepScreenOn = false
            window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = originalBrightness } }
        }
    }

    val playing by coordinator.playing.collectAsState()
    val watch by vm.watchCounts.collectAsState()
    val videoSize by coordinator.videoSize.collectAsState()
    val duration by coordinator.durationMs.collectAsState()
    val failed by coordinator.failed.collectAsState()
    val paused = !playing

    var menuOpen by remember { mutableStateOf(false) }
    var speedOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var editReturnMs by remember { mutableLongStateOf(0L) }
    var scrubbing by remember { mutableStateOf(false) }
    var poke by remember { mutableIntStateOf(0) }
    var controlsShown by remember { mutableStateOf(true) }
    val keepVisible = paused || menuOpen || speedOpen || scrubbing
    LaunchedEffect(keepVisible, poke, entered, dominant) {
        controlsShown = true
        if (!keepVisible) {
            delay(HIDE_AFTER_MS)
            controlsShown = false
        }
    }
    val showControls = entered && !closing && controlsShown && !editing

    // Brightness / volume HUD
    var hud by remember { mutableStateOf<Pair<Glyph, Float>?>(null) }
    LaunchedEffect(hud) {
        if (hud != null) {
            delay(700)
            hud = null
        }
    }

    val current = videos.getOrNull(dominant)
    val close = { current?.let { onClose(it.id) } ?: Unit }

    BackHandler {
        when {
            editing -> {
                coordinator.seekToMs(editReturnMs)
                editing = false
            }
            speedOpen -> speedOpen = false
            menuOpen -> menuOpen = false
            else -> close()
        }
    }

    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val brightnessOn by rememberUpdatedState(settings.brightnessGesture && !editing)
    val volumeOn by rememberUpdatedState(settings.volumeGesture && !editing)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Side-zone gestures. Runs before the pager sees the touch, and only claims a drag once it
            // is clearly vertical, so taps still pause and a disabled side falls through to swiping.
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    val zone = when {
                        down.position.x < w * 0.25f && brightnessOn -> 1
                        down.position.x > w * 0.75f && volumeOn -> 2
                        else -> 0
                    }
                    if (zone == 0) return@awaitEachGesture
                    val slop = viewConfiguration.touchSlop
                    var claimed = false
                    var startY = 0f
                    var startValue = 0f
                    while (true) {
                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) break
                        val dx = ch.position.x - down.position.x
                        val dy = ch.position.y - down.position.y
                        if (!claimed) {
                            if (abs(dy) > slop && abs(dy) > abs(dx) * 1.2f) {
                                claimed = true
                                startY = ch.position.y
                                startValue = if (zone == 1) {
                                    currentBrightness(context)
                                } else {
                                    audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() /
                                        audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                }
                                ch.consume()
                            } else if (abs(dx) > slop) {
                                break
                            }
                        } else {
                            ch.consume()
                            val v = (startValue + (startY - ch.position.y) / (h * 0.55f)).coerceIn(0f, 1f)
                            if (zone == 1) {
                                (context as? Activity)?.window?.let { win ->
                                    win.attributes = win.attributes.apply { screenBrightness = v.coerceAtLeast(0.01f) }
                                }
                                hud = Glyph.SUN to v
                            } else {
                                val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                audio.setStreamVolume(AudioManager.STREAM_MUSIC, (v * max).toInt().coerceIn(0, max), 0)
                                hud = Glyph.VOLUME to v
                            }
                        }
                    }
                }
            },
    ) {
        VerticalPager(
            state = pager,
            beyondBoundsPageCount = 1,
            key = { videos[it].id },
            userScrollEnabled = entered && !closing && !editing,
            flingBehavior = PagerDefaults.flingBehavior(state = pager, snapAnimationSpec = settleSpec(settings.transition)),
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(editing) {
                    detectTapGestures(onTap = {
                        if (editing) return@detectTapGestures
                        if (menuOpen || speedOpen) {
                            menuOpen = false
                            speedOpen = false
                        } else {
                            coordinator.toggle()
                        }
                        poke++
                    })
                },
        ) { page ->
            val inWindow = abs(page - dominant) <= 1
            VideoPage(
                video = videos[page],
                player = if (inWindow) coordinator.playerFor(page) else null,
                loader = vm.thumbs,
                failed = videos[page].id in failed,
                onRendered = onFirstFrame,
            )
        }

        // Top bar: back on the left; "i" and three dots on the right. All auto-hide together.
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 12.dp,
                        end = 12.dp,
                        top = with(density) { WindowInsets.statusBarsIgnoringVisibility.getTop(this).toDp() } + 8.dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SmallRoundButton(onClick = close, label = "Back") {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.weight(1f))
                SmallRoundButton(
                    onClick = {
                        vm.setPlayerInfo(!settings.playerInfo)
                        poke++
                    },
                    label = if (settings.playerInfo) "Hide details" else "Show details",
                ) {
                    GlyphIcon(Glyph.INFO, if (settings.playerInfo) accent else Color.White, size = 18.dp)
                }
                if (current != null) {
                    Box {
                        SmallRoundButton(onClick = { menuOpen = true; poke++ }, label = "More") {
                            GlyphIcon(Glyph.DOTS, Color.White, size = 18.dp)
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                            modifier = Modifier.background(Hc.Surface),
                        ) {
                            DropdownMenuItem(
                                text = { Text("Choose thumbnail frame…", color = Hc.Ink) },
                                leadingIcon = { GlyphIcon(Glyph.IMAGE, Hc.Ink, size = 20.dp) },
                                onClick = {
                                    menuOpen = false
                                    editReturnMs = coordinator.positionNow()
                                    coordinator.pause()
                                    val o = vm.thumbStore.override(current.id)
                                    if (o >= 0) coordinator.seekToMs(o)
                                    editing = true
                                },
                            )
                            if (vm.thumbStore.hasOverride(current.id)) {
                                DropdownMenuItem(
                                    text = { Text("Reset thumbnail to default", color = Hc.Ink) },
                                    leadingIcon = { GlyphIcon(Glyph.REPEAT, Hc.Ink, size = 20.dp) },
                                    onClick = {
                                        menuOpen = false
                                        vm.resetThumbOverride(current.id)
                                        Toast.makeText(context, "Thumbnail reset to default", Toast.LENGTH_SHORT).show()
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottom: details island / action stack (stay up, slide down when the seek bar hides),
        // then the auto-hiding seek block. Everything is transparent over the video.
        if (current != null && !editing) {
            val navBottom = with(density) { WindowInsets.navigationBarsIgnoringVisibility.getBottom(this).toDp() }
            val info = settings.playerInfo && entered && !closing
            val island = settings.infoStyle == InfoStyle.ISLAND
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(bottom = navBottom + 6.dp),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Box(Modifier.weight(1f)) {
                        androidx.compose.animation.AnimatedVisibility(visible = info && island, enter = fadeIn(), exit = fadeOut()) {
                            InfoIsland(
                                video = current,
                                views = watch[current.id] ?: 0,
                                size = videoSize,
                                durationMs = duration,
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    androidx.compose.animation.AnimatedVisibility(visible = info, enter = fadeIn(), exit = fadeOut()) {
                        ActionStack(
                            favourite = current.id in favs,
                            speed = coordinator.speed,
                            end = settings.end,
                            accent = accent,
                            onFavourite = { vm.toggleFavourite(current.id) },
                            onThumbnail = {
                                val ms = coordinator.positionNow()
                                vm.setThumbOverride(current.id, ms)
                                Toast.makeText(context, "Thumbnail set at ${formatTime(ms)}", Toast.LENGTH_SHORT).show()
                            },
                            onSpeed = { speedOpen = true; poke++ },
                            onEnd = {
                                val all = EndBehavior.values()
                                vm.setEnd(all[(settings.end.ordinal + 1) % all.size])
                            },
                        )
                    }
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = showControls,
                    enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                    exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
                ) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
                        if (info && !island) {
                            Text(
                                listOf(PlayerFormat.date(current.modified), viewsLabel(watch[current.id] ?: 0))
                                    .filter { it.isNotEmpty() }.joinToString("  ·  "),
                                style = TextStyle(color = Color.White.copy(alpha = 0.78f), fontSize = 11.sp, shadow = VideoTextShadow),
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                current.name,
                                style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, shadow = VideoTextShadow),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(2.dp))
                        }
                        SeekBar(
                            coordinator = coordinator,
                            accent = accent,
                            onScrubbing = { scrubbing = it; poke++ },
                        )
                        TimeRow(coordinator)
                    }
                }
            }
        }

        // Brightness / volume pill: icon, slider, percentage
        hud?.let { (glyph, value) ->
            Row(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = with(density) { WindowInsets.statusBarsIgnoringVisibility.getTop(this).toDp() } + 8.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xB31A1D21))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlyphIcon(glyph, accent, size = 18.dp)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.width(120.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x33FFFFFF))) {
                    Box(Modifier.fillMaxWidth(value.coerceIn(0f, 1f)).height(4.dp).clip(RoundedCornerShape(2.dp)).background(accent))
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    "${(value * 100).toInt()}%",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(36.dp),
                )
            }
        }

        if (speedOpen) {
            SpeedPanel(
                current = coordinator.speed,
                accent = accent,
                onPick = {
                    coordinator.speed = it
                    vm.setSpeed(it)
                    speedOpen = false
                },
                onDismiss = { speedOpen = false },
            )
        }

        if (editing && current != null) {
            ThumbnailEditor(
                coordinator = coordinator,
                accent = accent,
                hasOverride = vm.thumbStore.hasOverride(current.id),
                onSave = {
                    vm.setThumbOverride(current.id, coordinator.positionNow())
                    Toast.makeText(context, "Thumbnail updated", Toast.LENGTH_SHORT).show()
                    editing = false
                },
                onReset = {
                    vm.resetThumbOverride(current.id)
                    Toast.makeText(context, "Thumbnail reset to default", Toast.LENGTH_SHORT).show()
                    editing = false
                },
                onCancel = {
                    coordinator.seekToMs(editReturnMs)
                    editing = false
                },
            )
        }

        // Swallow input while the open/close animation runs
        if (!entered || closing) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(pass = PointerEventPass.Initial).consume()
                            while (true) {
                                val ev = awaitPointerEvent(PointerEventPass.Initial)
                                ev.changes.forEach { it.consume() }
                                if (ev.changes.none { it.pressed }) break
                            }
                        }
                    },
            )
        }
    }
}

private fun currentBrightness(context: Context): Float {
    val w = (context as? Activity)?.window
    val b = w?.attributes?.screenBrightness ?: -1f
    if (b >= 0f) return b
    return try {
        SystemSettings.System.getInt(context.contentResolver, SystemSettings.System.SCREEN_BRIGHTNESS) / 255f
    } catch (_: Exception) {
        0.5f
    }.coerceIn(0f, 1f)
}

object PlayerFormat {
    fun date(ms: Long): String = if (ms <= 0) "" else DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ms))
    fun speed(s: Float): String = speedLabel(s).replace("Normal", "1x")
}

@Composable
private fun SmallRoundButton(onClick: () -> Unit, label: String, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color(0x59000000))
            .clickable(onClickLabel = label, role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/**
 * One page of the feed. Until the player draws its first real frame on this page, the 0:00 start
 * frame is shown on top, so a page never shows an empty surface.
 */
@Composable
private fun VideoPage(
    video: VideoItem,
    player: ExoPlayer?,
    loader: ThumbnailLoader,
    failed: Boolean,
    onRendered: (String) -> Unit,
) {
    val context = LocalContext.current
    val start by produceState(initialValue = loader.cachedStart(video), video.id) {
        if (value == null) value = loader.startFrame(video)
    }
    var rendered by remember(video.id) { mutableStateOf(false) }
    var videoSize by remember(video.id) { mutableStateOf<VideoSize?>(null) }
    val textureView = remember { TextureView(context) }
    val onRenderedNow by rememberUpdatedState(onRendered)
    LaunchedEffect(failed) { if (failed) onRenderedNow(video.id) }

    if (player != null) {
        DisposableEffect(player, video.id) {
            val mine = { player.currentMediaItem?.mediaId == video.id }
            val listener = object : Player.Listener {
                override fun onRenderedFirstFrame() {
                    if (mine()) {
                        rendered = true
                        onRenderedNow(video.id)
                    }
                }

                override fun onVideoSizeChanged(size: VideoSize) {
                    if (mine() && size.width > 0 && size.height > 0) videoSize = size
                }
            }
            player.addListener(listener)
            player.setVideoTextureView(textureView)
            if (mine() && player.videoSize.width > 0) videoSize = player.videoSize
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
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (failed) {
            Text(
                "This video can’t be played on this phone",
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
private fun TimeRow(coordinator: PlaybackCoordinator) {
    val pos by coordinator.positionMs.collectAsState()
    val dur by coordinator.durationMs.collectAsState()
    Text(
        formatTime(pos) + " / " + formatTime(dur),
        style = TextStyle(color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp, shadow = VideoTextShadow),
    )
}

/** Thin seek bar in the accent colour. The touch target is taller than the line. */
@Composable
private fun SeekBar(
    coordinator: PlaybackCoordinator,
    accent: Color,
    onScrubbing: (Boolean) -> Unit,
    thick: Boolean = false,
) {
    val progress by coordinator.progress.collectAsState()
    var drag by remember { mutableStateOf<Float?>(null) }
    val c by rememberUpdatedState(coordinator)
    val scrubbing by rememberUpdatedState(onScrubbing)
    val shown = drag ?: progress
    Box(
        Modifier
            .fillMaxWidth()
            .height(24.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o ->
                        val f = (o.x / size.width).coerceIn(0f, 1f)
                        drag = f
                        scrubbing(true)
                        c.seek(f, exact = false)
                    },
                    onDragEnd = {
                        drag?.let { c.seek(it, exact = true) }
                        drag = null
                        scrubbing(false)
                    },
                    onDragCancel = {
                        drag = null
                        scrubbing(false)
                    },
                ) { change, _ ->
                    change.consume()
                    val f = (change.position.x / size.width).coerceIn(0f, 1f)
                    drag = f
                    c.seek(f, exact = false)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { o ->
                    c.seek((o.x / size.width).coerceIn(0f, 1f), exact = true)
                    scrubbing(false)
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val h = when {
                drag != null -> 3.dp.toPx()
                thick -> 2.5.dp.toPx()
                else -> 1.5.dp.toPx()
            }
            val y = size.height / 2 - h / 2
            drawRect(Color.White.copy(alpha = 0.28f), Offset(0f, y), Size(size.width, h))
            drawRect(accent, Offset(0f, y), Size(size.width * shown, h))
            drawCircle(Color.Black.copy(alpha = 0.25f), radius = (if (drag != null) 6.dp else 4.5.dp).toPx() + 1.dp.toPx(), center = Offset(size.width * shown, y + h / 2 + 1.dp.toPx()))
            drawCircle(accent, radius = (if (drag != null) 6.dp else 4.5.dp).toPx(), center = Offset(size.width * shown, y + h / 2))
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
                            textAlign = TextAlign.Center,
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

/** Scrub through the paused video and pick the frame to use as this video's thumbnail. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThumbnailEditor(
    coordinator: PlaybackCoordinator,
    accent: Color,
    hasOverride: Boolean,
    onSave: () -> Unit,
    onReset: () -> Unit,
    onCancel: () -> Unit,
) {
    val density = LocalDensity.current
    val navBottom = with(density) { WindowInsets.navigationBarsIgnoringVisibility.getBottom(this).toDp() }
    val pos by coordinator.positionMs.collectAsState()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = navBottom + 12.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xD922262B))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Choose thumbnail", color = Hc.Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(formatTime(pos), color = Hc.Mute, fontSize = 13.sp)
            }
            Text(
                "Drag to the frame you want on the grid.",
                color = Hc.Mute,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(6.dp))
            SeekBar(coordinator = coordinator, accent = accent, onScrubbing = {}, thick = true)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EditorButton("Cancel", false, accent, Modifier.weight(1f), onCancel)
                if (hasOverride) EditorButton("Reset", false, accent, Modifier.weight(1f), onReset)
                EditorButton("Use frame", true, accent, Modifier.weight(1.3f), onSave)
            }
        }
    }
}

@Composable
private fun EditorButton(text: String, primary: Boolean, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    Text(
        text,
        color = if (primary) onAccent(accent) else Hc.Ink,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(if (primary) accent else Hc.Surface2)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    )
}

/** Rounded translucent card with the title and details. Lives above the seek bar. */
@Composable
private fun InfoIsland(video: VideoItem, views: Int, size: VideoSize, durationMs: Long) {
    val details = listOfNotNull(
        PlayerFormat.date(video.modified).ifEmpty { null },
        if (size.width > 0 && size.height > 0) "${size.width}\u00D7${size.height}" else null,
        if (durationMs > 0) formatTime(durationMs) else null,
        viewsLabel(views),
    ).joinToString(" \u00B7 ")
    Column(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x8C15171A))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            video.name,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(3.dp))
        Text(details, color = Color.White.copy(alpha = 0.72f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Favourite, thumbnail, speed and end-behaviour controls stacked in the bottom-right corner. */
@Composable
private fun ActionStack(
    favourite: Boolean,
    speed: Float,
    end: EndBehavior,
    accent: Color,
    onFavourite: () -> Unit,
    onThumbnail: () -> Unit,
    onSpeed: () -> Unit,
    onEnd: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ActionCircle(if (favourite) "Remove favourite" else "Add favourite", onFavourite) {
            Icon(
                if (favourite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = null,
                tint = if (favourite) accent else Color.White,
                modifier = Modifier.size(19.dp),
            )
        }
        ActionCircle("Use this frame as thumbnail", onThumbnail) {
            GlyphIcon(Glyph.IMAGE, Color.White, size = 19.dp)
        }
        ActionChip(Glyph.SPEED, PlayerFormat.speed(speed), accent, "Playback speed", onSpeed)
        ActionChip(
            when (end) {
                EndBehavior.LOOP -> Glyph.REPEAT
                EndBehavior.NEXT -> Glyph.NEXT
                EndBehavior.STOP -> Glyph.STOP
            },
            when (end) {
                EndBehavior.LOOP -> "Loop"
                EndBehavior.NEXT -> "Next"
                EndBehavior.STOP -> "Stop"
            },
            accent,
            "When the video ends",
            onEnd,
        )
    }
}

@Composable
private fun ActionCircle(label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color(0x66101214))
            .clickable(onClickLabel = label, role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun ActionChip(glyph: Glyph, text: String, accent: Color, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(Color(0x66101214))
            .clickable(onClickLabel = label, role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .semantics { contentDescription = "$label: $text" }
            .padding(start = 9.dp, end = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(glyph, accent, size = 15.dp)
        Spacer(Modifier.width(5.dp))
        Text(text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

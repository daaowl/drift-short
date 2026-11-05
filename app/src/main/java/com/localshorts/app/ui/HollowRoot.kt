package com.localshorts.app.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.localshorts.app.MainActivity
import com.localshorts.app.MainViewModel
import com.localshorts.app.data.OpenStyle
import com.localshorts.app.data.VideoItem
import com.localshorts.app.ui.player.FrameGrab
import com.localshorts.app.ui.player.PlayerScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private class PlayerSession(val list: List<VideoItem>, val start: Int, val from: Rect?)

private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val Standard = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
private val Decelerate = CubicBezierEasing(0f, 0f, 0.2f, 1f)
private val IosSpringish = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)
private val Overshoot = CubicBezierEasing(0.34f, 1.22f, 0.64f, 1f)

private fun clamp01(x: Float) = x.coerceIn(0f, 1f)

/**
 * Timing and look of each opening style.
 *
 * Grow styles expand the video out of its grid tile. The image that grows is the video's own
 * 0:00 frame (the tile's custom thumbnail blends into it early in the motion), so when real playback
 * appears it is the same picture and nothing jumps or holds. Closing shrinks the frame that was
 * actually on screen, blending into the tile's thumbnail only at the very end.
 *
 * YouTube style is different: rebuilt from a screen recording of the YouTube app. Opening slides the
 * gallery down while it fades, and the full-screen player fades in over it. Closing fades the
 * player out quickly and slides the gallery up from below.
 */
private class Motion(
    val openMs: Int,
    val closeMs: Int,
    val openEasing: Easing,
    val closeEasing: Easing,
    val growFromTile: Boolean,
    /** Point in the open after which the real video may replace the growing frame. */
    val revealAt: Float,
    /** Grid tile shows a moving preview of 0:00, so there is no thumbnail to blend from. */
    val fromPreview: Boolean = false,
    val tileRadiusDp: Float = 0f,
    val bulgeRadiusDp: Float = 0f,
    val dim: Float = 1f,
    val galleryScale: Float = 1f,
)

private fun motionFor(style: OpenStyle) = when (style) {
    OpenStyle.GOAT -> Motion(380, 320, Emphasized, Emphasized, growFromTile = true, revealAt = 0.1f, fromPreview = true)
    OpenStyle.HOLLOW -> Motion(420, 340, Emphasized, Emphasized, growFromTile = true, revealAt = 0.35f, bulgeRadiusDp = 20f)
    OpenStyle.YOUTUBE -> Motion(460, 420, LinearEasing, LinearEasing, growFromTile = false, revealAt = 0f, dim = 0f)
    OpenStyle.INSTAGRAM -> Motion(400, 340, IosSpringish, IosSpringish, growFromTile = true, revealAt = 0.35f, tileRadiusDp = 4f, dim = 0.7f, galleryScale = 0.94f)
    OpenStyle.TIKTOK -> Motion(340, 260, Overshoot, Standard, growFromTile = true, revealAt = 0.35f, tileRadiusDp = 2f)
}

/** Shared between the player host and the gallery, so the gallery can move with the player. */
private class HostFx {
    var progress: Animatable<Float, AnimationVector1D>? by mutableStateOf(null)
    var motion: Motion? by mutableStateOf(null)
    var closing by mutableStateOf(false)
    var covering by mutableStateOf(false)
}

/**
 * The gallery always stays composed underneath the player and settings, so its scroll position,
 * filter and loaded thumbnails survive opening a video or settings.
 */
@Composable
fun HollowRoot(vm: MainViewModel, activity: MainActivity) {
    val settings by vm.settings.collectAsState()
    var session by remember { mutableStateOf<PlayerSession?>(null) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val gridState = rememberSaveable(saver = LazyGridState.Saver) { LazyGridState() }
    val bounds = remember { HashMap<String, Rect>() }
    val locked by activity.locked
    val fx = remember { HostFx() }

    // Keep the activity's synchronous lock flag in step with the saved setting
    LaunchedEffect(settings.loaded, settings.lock) {
        if (settings.loaded) activity.setLockEnabled(settings.lock)
    }

    HollowTheme(accent = Color(settings.accent)) {
        Box(Modifier.fillMaxSize().background(if (session != null) Color.Black else Hc.Bg)) {
            GalleryScreen(
                vm = vm,
                activity = activity,
                gridState = gridState,
                bounds = bounds,
                onOpen = { list, index ->
                    if (session == null) {
                        val v = list[index]
                        val r = bounds[v.id]
                        val visible = gridState.layoutInfo.visibleItemsInfo.any { it.key == v.id }
                        session = PlayerSession(list, index, if (visible) r else null)
                    }
                },
                onSettings = { showSettings = true },
                modifier = Modifier.graphicsLayer {
                    if (fx.covering) {
                        // Stop drawing the gallery once the player fully covers it
                        alpha = 0f
                        return@graphicsLayer
                    }
                    val m = fx.motion ?: return@graphicsLayer
                    val t = fx.progress?.value ?: return@graphicsLayer
                    if (!m.growFromTile) {
                        // YouTube: slides down and fades on open; rises from below on close
                        if (!fx.closing) {
                            translationY = size.height * 0.38f * Decelerate.transform(clamp01(t / 0.75f))
                            alpha = 1f - clamp01(t / 0.7f)
                        } else {
                            val c = 1f - t
                            val rise = Decelerate.transform(clamp01((c - 0.15f) / 0.85f))
                            translationY = size.height * 0.45f * (1f - rise)
                            alpha = clamp01((c - 0.15f) / 0.3f)
                        }
                    } else if (m.galleryScale != 1f) {
                        val s = lerp(1f, m.galleryScale, clamp01(t))
                        scaleX = s
                        scaleY = s
                    }
                },
            )
            session?.let { s ->
                PlayerHost(
                    vm = vm,
                    session = s,
                    gridState = gridState,
                    bounds = bounds,
                    fx = fx,
                    onDone = {
                        fx.covering = false
                        fx.progress = null
                        fx.motion = null
                        fx.closing = false
                        session = null
                    },
                )
            }
            if (showSettings) {
                BackHandler { showSettings = false }
                SettingsScreen(vm = vm, activity = activity, onBack = { showSettings = false })
            }
            if (locked) {
                LockScreen(iconId = settings.icon, onUnlock = { activity.authenticate() })
            }
        }
    }
}

@Composable
private fun PlayerHost(
    vm: MainViewModel,
    session: PlayerSession,
    gridState: LazyGridState,
    bounds: Map<String, Rect>,
    fx: HostFx,
    onDone: () -> Unit,
) {
    val motion = remember { motionFor(vm.settings.value.openStyle) }
    val progress = remember { Animatable(0f) }
    val stackAlpha = remember { Animatable(if (motion.growFromTile) 1f else 0f) }
    var entered by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    var startRendered by remember { mutableStateOf(false) }
    var coverVideo by remember { mutableStateOf(session.list[session.start]) }
    var target by remember { mutableStateOf(session.from) }
    var grabbed by remember { mutableStateOf<Bitmap?>(null) }
    val frameGrab = remember { FrameGrab() }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val thumbVersion by vm.thumbVersion.collectAsState()
    val tileCover = rememberCover(coverVideo, vm.thumbs, thumbVersion)
    val startFrame by produceState(initialValue = vm.thumbs.cachedStart(coverVideo), coverVideo.id) {
        if (value == null) value = vm.thumbs.startFrame(coverVideo)
    }

    LaunchedEffect(Unit) {
        fx.motion = motion
        fx.progress = progress
        launch {
            progress.animateTo(1f, tween(motion.openMs, easing = motion.openEasing))
            entered = true
            fx.covering = true
        }
        if (motion.growFromTile) {
            // Reveal real playback as soon as it's drawing and the thumbnail has blended away.
            // The growing image is the same 0:00 frame, so this swap is invisible.
            snapshotFlow { startRendered && progress.value >= motion.revealAt - 0.001f }.first { it }
            stackAlpha.animateTo(0f, tween(90))
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val full = Rect(0f, 0f, w, h)
        fun from(): Rect = target ?: Rect(w * 0.12f, h * 0.18f, w * 0.88f, h * 0.82f)

        val close: (String) -> Unit = close@{ currentId ->
            if (closing) return@close
            closing = true
            scope.launch {
                grabbed = frameGrab.grab?.invoke()
                fx.closing = true
                fx.covering = false
                coverVideo = session.list.firstOrNull { it.id == currentId } ?: coverVideo
                if (motion.growFromTile) {
                    // Bring the current video's tile on screen so the player can shrink back into it
                    val idx = vm.gallery.value.indexOfFirst { it.id == currentId }
                    var visible = gridState.layoutInfo.visibleItemsInfo.any { it.key == currentId }
                    if (!visible && idx >= 0) {
                        gridState.scrollToItem(idx)
                        withFrameNanos { }
                        withFrameNanos { }
                        visible = gridState.layoutInfo.visibleItemsInfo.any { it.key == currentId }
                    }
                    target = if (visible) bounds[currentId] else null
                    // The frame on screen goes on top first: identical picture, so no visible change
                    stackAlpha.snapTo(1f)
                }
                progress.animateTo(0f, tween(motion.closeMs, easing = motion.closeEasing))
                onDone()
            }
        }

        // Dim the gallery behind the growing player
        if (motion.dim > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = clamp01(progress.value) * motion.dim }
                    .background(Color.Black),
            )
        }

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val t = progress.value
                    if (!motion.growFromTile) {
                        // YouTube: full-screen player fades in slowly over the sliding gallery;
                        // on close it fades out quickly before the gallery rises
                        alpha = if (!closing) {
                            Standard.transform(clamp01((t - 0.12f) / 0.75f))
                        } else {
                            1f - clamp01((1f - t) / 0.28f)
                        }
                        return@graphicsLayer
                    }
                    val r = from()
                    val s0 = (r.width / w).coerceAtLeast(0.01f)
                    val h0 = r.height / s0 // visible content height at t = 0, in content space
                    val s = lerp(s0, 1f, t)
                    val visibleH = lerp(h0, h, t)
                    val cx = lerp(r.center.x, full.center.x, t)
                    val cy = lerp(r.center.y, full.center.y, t)
                    scaleX = s
                    scaleY = s
                    translationX = cx - full.center.x
                    translationY = cy - full.center.y
                    val px = with(density) { 1.dp.toPx() }
                    val tt = clamp01(t)
                    val radius = (motion.tileRadiusDp * (1f - tt) + motion.bulgeRadiusDp * 4f * tt * (1f - tt)) * px / s
                    clip = true
                    shape = CenterBandShape(visibleH, radius)
                },
        ) {
            PlayerScreen(
                vm = vm,
                videos = session.list,
                startIndex = session.start,
                entered = entered,
                closing = closing,
                onFirstFrame = { id -> if (id == session.list[session.start].id) startRendered = true },
                onClose = close,
                frameGrab = frameGrab,
            )
            if (motion.growFromTile) {
                // The growing picture: the frame on screen (closing) or the 0:00 frame (opening),
                // with the tile's thumbnail blended over it only near the tile.
                val base = if (closing) grabbed ?: startFrame else startFrame
                val tileImage = if (motion.fromPreview) startFrame else tileCover
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = stackAlpha.value },
                ) {
                    if (base != null) {
                        Image(
                            bitmap = base.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().background(Color.Black),
                        )
                    }
                    if (tileImage != null) {
                        Image(
                            bitmap = tileImage.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .layout { measurable, constraints ->
                                    val r = from()
                                    val s0 = (r.width / w).coerceAtLeast(0.01f)
                                    val bandH = lerp(r.height / s0, h, clamp01(progress.value)).roundToInt().coerceAtLeast(1)
                                    val p = measurable.measure(
                                        constraints.copy(minWidth = constraints.maxWidth, minHeight = bandH, maxHeight = bandH),
                                    )
                                    layout(constraints.maxWidth, constraints.maxHeight) {
                                        p.place(0, (constraints.maxHeight - bandH) / 2)
                                    }
                                }
                                // Fully the tile's picture at the tile, gone by a third of the way
                                .graphicsLayer { alpha = clamp01(1f - progress.value / 0.35f) },
                        )
                    }
                }
            }
        }
    }
}

/** Clips to a full-width horizontal band of [bandHeight], centred vertically. */
private class CenterBandShape(private val bandHeight: Float, private val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val band = bandHeight.coerceAtMost(size.height * 1.2f)
        val top = (size.height - band) / 2f
        return Outline.Rounded(
            RoundRect(0f, top, size.width, top + band, CornerRadius(radius.coerceAtLeast(0f))),
        )
    }
}

package com.localshorts.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
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
import com.localshorts.app.data.VideoItem
import com.localshorts.app.ui.player.PlayerScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private class PlayerSession(val list: List<VideoItem>, val start: Int, val from: Rect?)

private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private const val OPEN_MS = 420
private const val CLOSE_MS = 340

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

    // Keep the activity's synchronous lock flag in step with the saved setting
    LaunchedEffect(settings.loaded, settings.lock) {
        if (settings.loaded) activity.setLockEnabled(settings.lock)
    }

    HollowTheme(accent = Color(settings.accent)) {
        Box(Modifier.fillMaxSize().background(Hc.Bg)) {
            val playerCovering = remember { mutableStateOf(false) }
            GalleryScreen(
                vm = vm,
                activity = activity,
                gridState = gridState,
                bounds = bounds,
                onOpen = { list, index ->
                    val v = list[index]
                    val r = bounds[v.id]
                    val visible = gridState.layoutInfo.visibleItemsInfo.any { it.key == v.id }
                    session = PlayerSession(list, index, if (visible) r else null)
                },
                onSettings = { showSettings = true },
                // Stop drawing the gallery once the player fully covers it
                modifier = Modifier.graphicsLayer { alpha = if (playerCovering.value) 0f else 1f },
            )
            session?.let { s ->
                PlayerHost(
                    vm = vm,
                    session = s,
                    gridState = gridState,
                    bounds = bounds,
                    onCovering = { playerCovering.value = it },
                    onDone = {
                        playerCovering.value = false
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

/**
 * Opens the player by growing it out of the tapped tile, and shrinks it back into the tile on close.
 *
 * The tile's cover stays on top while the video loads. It only fades once the player reports a real
 * rendered frame AND the motion is far enough along that the eye can't track the swap. The cover may
 * be a later moment than 0:00, so it is never assumed to match the first frame.
 */
@Composable
private fun PlayerHost(
    vm: MainViewModel,
    session: PlayerSession,
    gridState: LazyGridState,
    bounds: Map<String, Rect>,
    onCovering: (Boolean) -> Unit,
    onDone: () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    val coverAlpha = remember { Animatable(1f) }
    var entered by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    var startRendered by remember { mutableStateOf(false) }
    var coverVideo by remember { mutableStateOf(session.list[session.start]) }
    var target by remember { mutableStateOf(session.from) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val thumbVersion by vm.thumbVersion.collectAsState()
    val cover = rememberCover(coverVideo, vm.thumbs, thumbVersion)

    LaunchedEffect(Unit) {
        launch {
            progress.animateTo(1f, tween(OPEN_MS, easing = Emphasized))
            entered = true
            onCovering(true)
        }
        snapshotFlow { startRendered && progress.value >= 0.45f }.first { it }
        coverAlpha.animateTo(0f, tween(170))
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
                onCovering(false)
                coverVideo = session.list.firstOrNull { it.id == currentId } ?: coverVideo
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
                coverAlpha.animateTo(1f, tween(120))
                progress.animateTo(0f, tween(CLOSE_MS, easing = Emphasized))
                onDone()
            }
        }

        // Dim the gallery behind the growing player
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = progress.value }
                .background(Color.Black),
        )

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val t = progress.value
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
                    val radius = with(density) { 20.dp.toPx() } * 4f * t * (1f - t) / s
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
            )
            // The tile's own cover, laid out exactly like the tile (cropped to the visible band)
            if (cover != null) {
                Image(
                    bitmap = cover.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .layout { measurable, constraints ->
                            val t = progress.value
                            val r = from()
                            val s0 = (r.width / w).coerceAtLeast(0.01f)
                            val bandH = lerp(r.height / s0, h, t).roundToInt().coerceAtLeast(1)
                            val p = measurable.measure(
                                constraints.copy(minWidth = constraints.maxWidth, minHeight = bandH, maxHeight = bandH),
                            )
                            layout(constraints.maxWidth, constraints.maxHeight) {
                                p.place(0, (constraints.maxHeight - bandH) / 2)
                            }
                        }
                        .graphicsLayer { alpha = coverAlpha.value },
                )
            }
        }
    }
}

/** Clips to a full-width horizontal band of [bandHeight], centred vertically. */
private class CenterBandShape(private val bandHeight: Float, private val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val top = (size.height - bandHeight) / 2f
        return Outline.Rounded(
            RoundRect(0f, top, size.width, top + bandHeight, CornerRadius(radius.coerceAtLeast(0f))),
        )
    }
}

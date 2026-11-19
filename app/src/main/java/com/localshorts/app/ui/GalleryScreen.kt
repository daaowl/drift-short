package com.localshorts.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localshorts.app.MainActivity
import com.localshorts.app.MainViewModel
import com.localshorts.app.data.Folder
import com.localshorts.app.data.OpenStyle
import com.localshorts.app.data.SortOrder
import com.localshorts.app.data.ThumbnailLoader
import com.localshorts.app.data.VideoItem
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/** Up to this many folders show as pills in the island; more switch to a "current folder" button. */
private const val ISLAND_PILL_LIMIT = 4

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen(
    vm: MainViewModel,
    activity: MainActivity,
    gridState: LazyGridState,
    bounds: MutableMap<String, Rect>,
    onOpen: (list: List<VideoItem>, index: Int) -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings by vm.settings.collectAsState()
    val videos by vm.gallery.collectAsState()
    val library by vm.library.collectAsState()
    val watch by vm.watchCounts.collectAsState()
    val favs by vm.favourites.collectAsState()
    val query by vm.query.collectAsState()
    val scanning by vm.scanning.collectAsState()
    val unavailable by vm.unavailable.collectAsState()
    val thumbVersion by vm.thumbVersion.collectAsState()
    val accent = LocalAccent.current
    val focus = LocalFocusManager.current
    val density = LocalDensity.current
    val haze = remember { HazeState() }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.addFolder(uri)
    }
    val addFolder = {
        activity.suppressNextLock = true
        pick.launch(null)
    }

    var popover by remember { mutableStateOf<Popover?>(null) }
    var sortBounds by remember { mutableStateOf(Rect.Zero) }
    var searching by remember { mutableStateOf(query.isNotEmpty()) }
    var panelOpen by remember { mutableStateOf(false) }
    BackHandler(enabled = panelOpen) { panelOpen = false }
    BackHandler(enabled = searching && !panelOpen) {
        vm.query.value = ""
        searching = false
        focus.clearFocus()
    }

    // Header slides away while scrolling down; the folder island tucks away too
    var headerPx by remember { mutableIntStateOf(0) }
    var headerOffset by remember { mutableFloatStateOf(0f) }
    var islandShown by remember { mutableStateOf(true) }
    val collapse = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                headerOffset = (headerOffset + available.y).coerceIn(-headerPx.toFloat(), 0f)
                if (available.y < -6f) islandShown = false
                if (available.y > 6f) islandShown = true
                return Offset.Zero
            }
        }
    }
    val atTop by remember { derivedStateOf { gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset < 8 } }
    LaunchedEffect(atTop) {
        if (atTop) {
            headerOffset = 0f
            islandShown = true
        }
    }

    // Tiles that appear in the first moments of a list fade up in a short stagger
    val listKey = "${settings.activeFolder}|${settings.favouritesOnly}|${settings.sort}|${query.isNotBlank()}"
    val appearAt = remember(listKey) { System.currentTimeMillis() }

    val statusTop = WindowInsets.statusBarsIgnoringVisibility.getTop(density)
    val navBottom = WindowInsets.navigationBarsIgnoringVisibility.getBottom(density)
    val cols = settings.columns
    val gutter = when (cols) { 1 -> 10.dp; 2 -> 6.dp; 3 -> 4.dp; else -> 3.dp }
    val radius = when (cols) { 1 -> 22.dp; 2 -> 16.dp; 3 -> 12.dp; else -> 9.dp }

    CompositionLocalProvider(LocalHaze provides haze) {
        Box(
            modifier
                .fillMaxSize()
                .background(Hc.Bg)
                .nestedScroll(collapse),
        ) {
            // Everything that scrolls is the glass's backdrop
            Box(Modifier.fillMaxSize().haze(state = haze)) {
                AmbientGlow(accent, gridState)
                if (videos.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(top = with(density) { (statusTop + headerPx).toDp() })) {
                        EmptyState(
                            noFolders = settings.loaded && settings.folders.isEmpty(),
                            scanning = scanning,
                            searching = query.isNotBlank(),
                            favourites = settings.favouritesOnly,
                            lost = settings.folders.isNotEmpty() && settings.folders.all { it.uri in unavailable },
                            accent = accent,
                            onAdd = addFolder,
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(cols),
                        state = gridState,
                        contentPadding = with(density) {
                            PaddingValues(
                                start = gutter,
                                end = gutter,
                                top = (statusTop + headerPx).toDp() + 4.dp,
                                bottom = navBottom.toDp() + 110.dp,
                            )
                        },
                        horizontalArrangement = Arrangement.spacedBy(gutter),
                        verticalArrangement = Arrangement.spacedBy(gutter),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        itemsIndexed(videos, key = { _, v -> v.id }) { index, v ->
                            Thumb(
                                v = v,
                                loader = vm.thumbs,
                                thumbVersion = thumbVersion,
                                preview = settings.openStyle == OpenStyle.GOAT,
                                warmStart = settings.openStyle != OpenStyle.YOUTUBE,
                                columns = cols,
                                ratio = if (cols == 1 && settings.tileShape.ratio < 0.7f) 4f / 5f else settings.tileShape.ratio,
                                radius = radius,
                                favourite = v.id in favs,
                                views = watch[v.id] ?: 0,
                                enterDelay = if (System.currentTimeMillis() - appearAt < 700) (index.coerceAtMost(14) * 28) else -1,
                                modifier = Modifier
                                    .animateItemPlacement(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow))
                                    .onGloballyPositioned { bounds[v.id] = it.boundsInRoot() },
                                onClick = {
                                    focus.clearFocus()
                                    onOpen(videos, index)
                                },
                            )
                        }
                    }
                }
            }

            // ---------- header ----------
            Column(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer { translationY = headerOffset }
                    .onSizeChanged { headerPx = it.height - statusTop },
            ) {
                val glassAlpha by animateFloatAsState(if (atTop) 0f else 1f, tween(260), label = "header-glass")
                Box(Modifier.fillMaxWidth()) {
                    // Glass fades in once content scrolls under the header
                    Box(
                        Modifier
                            .matchParentSize()
                            .graphicsLayer { alpha = glassAlpha }
                            .glass(haze, RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp), tint = Color(0xB30A0A0C)),
                    )
                    Column(Modifier.padding(top = with(density) { statusTop.toDp() })) {
                        AnimatedContent(
                            targetState = searching,
                            transitionSpec = {
                                (fadeIn(tween(200)) + slideInVertically(tween(260)) { -it / 3 })
                                    .togetherWith(fadeOut(tween(140)))
                            },
                            label = "header-mode",
                        ) { isSearching ->
                            if (isSearching) {
                                SearchBar(
                                    query = query,
                                    accent = accent,
                                    onChange = { vm.query.value = it },
                                    onClose = {
                                        vm.query.value = ""
                                        searching = false
                                        focus.clearFocus()
                                    },
                                    onDone = { focus.clearFocus() },
                                )
                            } else {
                                Row(
                                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 10.dp, bottom = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Wordmark(accent, Modifier.weight(1f))
                                    GlassCircle(onClick = { searching = true }, label = "Search") {
                                        Icon(Icons.Default.Search, contentDescription = null, tint = Hc.Ink, modifier = Modifier.size(20.dp))
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    GlassCircle(onClick = onSettings, label = "Settings") {
                                        Icon(Icons.Default.Settings, contentDescription = null, tint = Hc.Ink, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 6.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val selected = when {
                                settings.favouritesOnly -> 2
                                settings.sort == SortOrder.NEWEST -> 0
                                settings.sort == SortOrder.OLDEST -> 1
                                else -> -1
                            }
                            FilterControl(
                                labels = listOf("Latest", "Oldest", "Favourites"),
                                selected = selected,
                                accent = accent,
                                modifier = Modifier.weight(1f, fill = false),
                            ) { i ->
                                when (i) {
                                    0 -> vm.setView(false, SortOrder.NEWEST)
                                    1 -> vm.setView(false, SortOrder.OLDEST)
                                    else -> vm.setView(true, settings.sort)
                                }
                            }
                            Spacer(Modifier.weight(0.01f))
                            Spacer(Modifier.width(10.dp))
                            Box(Modifier.onGloballyPositioned { sortBounds = it.boundsInRoot() }) {
                                val custom = settings.sort == SortOrder.NAME || settings.sort == SortOrder.MOST_WATCHED
                                GlassCircle(
                                    onClick = {
                                        popover = Popover(sortBounds) { dismiss ->
                                            PopoverLabel("Sort by")
                                            SortOrder.values().forEach { o ->
                                                PopoverItem(o.label, checked = o == settings.sort, onClick = {
                                                    vm.setView(settings.favouritesOnly, o)
                                                    dismiss()
                                                })
                                            }
                                        }
                                    },
                                    label = "Sort: ${settings.sort.label}",
                                    size = 40.dp,
                                ) {
                                    GlyphIcon(Glyph.SORT, if (custom) accent else Hc.Ink, size = 19.dp)
                                }
                            }
                        }
                    }
                }
            }

            // Status bar gets a quiet glass strip once content scrolls, so it never collides with the clock
            Box(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBarsIgnoringVisibility)
                    .graphicsLayer { alpha = if (atTop) 0f else 1f }
                    .glass(haze, RoundedCornerShape(0.dp), tint = Color(0xCC09090B)),
            )

            // ---------- folder island ----------
            AnimatedVisibility(
                visible = islandShown && !panelOpen && settings.loaded,
                enter = fadeIn(tween(180)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 500f)) { it * 2 },
                exit = fadeOut(tween(140)) + slideOutVertically(tween(200)) { it * 2 },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = with(density) { navBottom.toDp() } + 18.dp),
            ) {
                FolderIsland(
                    folders = settings.folders,
                    active = settings.activeFolder,
                    unavailable = unavailable,
                    library = library,
                    accent = accent,
                    onSelect = { vm.setActiveFolder(it) },
                    onOpenPanel = { panelOpen = true },
                    onAdd = addFolder,
                )
            }

            if (panelOpen) {
                FolderPanel(
                    folders = settings.folders,
                    active = settings.activeFolder,
                    unavailable = unavailable,
                    library = library,
                    loader = vm.thumbs,
                    thumbVersion = thumbVersion,
                    accent = accent,
                    navBottom = with(density) { navBottom.toDp() },
                    onSelect = {
                        vm.setActiveFolder(it)
                        panelOpen = false
                    },
                    onAdd = addFolder,
                    onRefresh = { vm.refresh() },
                    onClose = { panelOpen = false },
                )
            }

            PopoverLayer(popover) { popover = null }
        }
    }
}

// ============================================================== header pieces

@Composable
private fun Wordmark(accent: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text(
            "Hollow",
            style = TextStyle(
                fontFamily = Serif,
                fontStyle = FontStyle.Italic,
                fontSize = 36.sp,
                color = Hc.Ink,
                letterSpacing = (-0.5).sp,
            ),
        )
        Box(
            Modifier
                .padding(start = 3.dp, bottom = 9.dp)
                .size(7.dp)
                .clip(CircleShape)
                .background(accent),
        )
    }
}

@Composable
fun GlassCircle(onClick: () -> Unit, label: String, size: Dp = 42.dp, content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(size)
            .pressScale(interaction, 0.9f)
            .clip(CircleShape)
            .glass(LocalHaze.current, CircleShape, tint = Color(0x80202227))
            .clickable(interactionSource = interaction, indication = null, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun SearchBar(query: String, accent: Color, onChange: (String) -> Unit, onClose: () -> Unit, onDone: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { focus.requestFocus() }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 6.dp)
            .height(46.dp)
            .clip(RoundedCornerShape(23.dp))
            .glass(LocalHaze.current, RoundedCornerShape(23.dp), tint = Color(0x99202227))
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Search, contentDescription = null, tint = Hc.Mute, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text("Search your videos", color = Hc.Faint, fontSize = 15.sp)
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = Hc.Ink, fontSize = 15.sp, fontFamily = Sans, fontWeight = FontWeight.Medium),
                cursorBrush = SolidColor(accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onDone() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .clickable(onClickLabel = "Close search", onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Close, contentDescription = null, tint = Hc.Mute, modifier = Modifier.size(19.dp))
        }
    }
}

/** Glass track with a softly sliding accent pill under the selected label. */
@Composable
private fun FilterControl(
    labels: List<String>,
    selected: Int,
    accent: Color,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
) {
    val density = LocalDensity.current
    val xs = remember { mutableStateMapOf<Int, Float>() }
    val ws = remember { mutableStateMapOf<Int, Float>() }
    val targetW = ws[selected] ?: 0f
    val x by animateFloatAsState(xs[selected] ?: 0f, spring(dampingRatio = 0.78f, stiffness = 520f), label = "pill-x")
    val w by animateFloatAsState(targetW, spring(dampingRatio = 0.78f, stiffness = 520f), label = "pill-w")
    val pillAlpha by animateFloatAsState(if (selected >= 0 && targetW > 0f) 1f else 0f, tween(180), label = "pill-a")
    Box(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .glass(LocalHaze.current, RoundedCornerShape(20.dp), tint = Color(0x80202227))
            .padding(4.dp),
    ) {
        Box(
            Modifier
                .graphicsLayer {
                    translationX = x
                    alpha = pillAlpha
                }
                .width(with(density) { w.toDp() })
                .fillMaxHeight()
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.92f), accent))),
        )
        Row(Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
            labels.forEachIndexed { i, label ->
                val on = i == selected
                val color by animateColorAsState(if (on) onAccent(accent) else Hc.Mute, tween(220), label = "seg-color")
                Text(
                    label,
                    color = color,
                    fontSize = 13.5.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier
                        .onGloballyPositioned {
                            xs[i] = it.positionInParent().x
                            ws[i] = it.size.width.toFloat()
                        }
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClickLabel = label) { onSelect(i) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/** Slow, soft colour light behind the top of the feed. Fades as you scroll away from the top. */
@Composable
private fun AmbientGlow(accent: Color, gridState: LazyGridState) {
    val drift = rememberInfiniteTransition(label = "glow")
    val a by drift.animateFloat(0f, (Math.PI * 2).toFloat(), infiniteRepeatable(tween(18000, easing = LinearEasing)), label = "glow-a")
    val second = remember(accent) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(accent.toArgb(), hsv)
        Color.hsv((hsv[0] + 38f) % 360f, (hsv[1] * 0.9f).coerceIn(0f, 1f), hsv[2].coerceAtLeast(0.6f))
    }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(420.dp)
            .graphicsLayer {
                val away = gridState.firstVisibleItemIndex > 0
                alpha = if (away) 0f else 1f - (gridState.firstVisibleItemScrollOffset / 600f).coerceIn(0f, 1f)
            },
    ) {
        val c1 = Offset(size.width * (0.22f + 0.06f * cos(a)), size.height * (0.12f + 0.05f * sin(a)))
        val c2 = Offset(size.width * (0.86f + 0.05f * sin(a * 0.7f)), size.height * (0.05f + 0.06f * cos(a * 0.8f)))
        drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.30f), Color.Transparent), c1, size.width * 0.62f), size.width * 0.62f, c1)
        drawCircle(Brush.radialGradient(listOf(second.copy(alpha = 0.20f), Color.Transparent), c2, size.width * 0.55f), size.width * 0.55f, c2)
        // melt the light into the page further down
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Hc.Bg), startY = size.height * 0.45f, endY = size.height))
    }
}

// ============================================================== grid tile

@Composable
private fun Thumb(
    v: VideoItem,
    loader: ThumbnailLoader,
    thumbVersion: Int,
    preview: Boolean,
    warmStart: Boolean,
    columns: Int,
    ratio: Float,
    radius: Dp,
    favourite: Boolean,
    views: Int,
    enterDelay: Int,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val bmp = rememberCover(v, loader, thumbVersion)
    // Have the 0:00 frame ready before a tap, so the opening grows a real frame straight away
    if (warmStart) {
        LaunchedEffect(v.id, bmp != null) { if (bmp != null) loader.startFrame(v) }
    }
    val strip = if (preview) {
        produceState(initialValue = loader.cachedPreview(v), v.id) {
            if (value == null) value = loader.preview(v)
        }.value
    } else {
        null
    }
    val small = columns >= 3
    val shape = RoundedCornerShape(radius)
    val interaction = remember { MutableInteractionSource() }

    // Fade-up entrance for the first tiles of a list
    val enter = remember { Animatable(if (enterDelay >= 0) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (enterDelay >= 0) {
            delay(enterDelay.toLong())
            enter.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = 260f))
        }
    }
    // Images fade in when they arrive, but never flash if they were already cached
    val hadImageAtStart = remember(v.id) { bmp != null || strip != null }
    val imageAlpha by animateFloatAsState(
        if (bmp != null || strip != null) 1f else 0f,
        if (hadImageAtStart) tween(0) else tween(260),
        label = "img",
    )

    Box(
        modifier
            .graphicsLayer {
                alpha = enter.value
                translationY = (1f - enter.value) * 28.dp.toPx()
            }
            .pressScale(interaction, 0.955f)
            .fillMaxWidth()
            .aspectRatio(ratio)
            .clip(shape)
            .background(Hc.Surface)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        if (bmp == null && strip == null) Shimmer(Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = imageAlpha }) {
            if (strip != null) {
                PreviewFlipbook(strip, Modifier.fillMaxSize())
            } else if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = v.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        // Soft bottom vignette behind the view count
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0x99000000)),
                            startY = size.height * 0.68f,
                            endY = size.height,
                        ),
                    )
                },
        )
        if (favourite) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(if (small) 5.dp else 7.dp)
                    .size(if (small) 20.dp else 24.dp)
                    .clip(CircleShape)
                    .background(Color(0x66000000)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Favorite, contentDescription = "Favourite", tint = Color.White, modifier = Modifier.size(if (small) 11.dp else 13.dp))
            }
        }
        Row(
            Modifier.align(Alignment.BottomStart).padding(start = if (small) 6.dp else 9.dp, bottom = if (small) 5.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlyphIcon(Glyph.NEXT, Color.White.copy(alpha = 0.9f), size = if (small) 9.dp else 10.dp)
            Spacer(Modifier.width(3.dp))
            Text(
                compactViews(views),
                style = TextStyle(
                    color = Color.White,
                    fontSize = if (small) 10.sp else 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Sans,
                    shadow = VideoTextShadow,
                ),
                maxLines = 1,
            )
        }
    }
}

private fun compactViews(n: Int): String = viewsLabel(n).removeSuffix(" views").removeSuffix(" view")

@Composable
private fun Shimmer(modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(-1f, 2f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "shimmer-x")
    Box(
        modifier.drawBehind {
            val w = size.width
            drawRect(
                Brush.linearGradient(
                    listOf(Color.Transparent, Color(0x14FFFFFF), Color.Transparent),
                    start = Offset(w * x - w * 0.6f, 0f),
                    end = Offset(w * x, size.height),
                ),
            )
        },
    )
}

// ============================================================== folder island + panel

@Composable
private fun FolderIsland(
    folders: List<Folder>,
    active: String,
    unavailable: Set<String>,
    library: List<VideoItem>,
    accent: Color,
    onSelect: (String) -> Unit,
    onOpenPanel: () -> Unit,
    onAdd: () -> Unit,
) {
    val haze = LocalHaze.current
    val shape = RoundedCornerShape(28.dp)
    Box(
        Modifier
            .shadow(24.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .glass(haze, shape, tint = Color(0xA6141518))
            .widthIn(max = 360.dp)
            .height(56.dp)
            .padding(6.dp),
    ) {
        when {
            folders.isEmpty() -> {
                Row(
                    Modifier
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(22.dp))
                        .background(accent)
                        .clickable(onClick = onAdd)
                        .padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = onAccent(accent), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add a folder", color = onAccent(accent), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
            folders.size <= ISLAND_PILL_LIMIT -> {
                // Pills: All + each folder, with the same sliding highlight as the filters
                val names = listOf("" to "All") + folders.map { it.uri to it.name }
                val selected = names.indexOfFirst { it.first == active }.coerceAtLeast(0)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IslandPills(
                        labels = names.map { (uri, n) -> if (uri in unavailable) "$n (!)" else n },
                        selected = selected,
                        accent = accent,
                        modifier = Modifier.weight(1f, fill = false),
                    ) { onSelect(names[it].first) }
                    Spacer(Modifier.width(4.dp))
                    IslandButton(onOpenPanel, "All folders") { GlyphIcon(Glyph.GRID, Hc.Ink, size = 18.dp) }
                }
            }
            else -> {
                // Too many for pills: one button showing the current folder, opening the panel
                val name = folders.firstOrNull { it.uri == active }?.name ?: "All videos"
                val count = if (active.isEmpty()) library.size else library.count { it.folder == active }
                Row(
                    Modifier
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(22.dp))
                        .clickable(onClickLabel = "Choose folder", onClick = onOpenPanel)
                        .padding(start = 8.dp, end = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(accent.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) { GlyphIcon(Glyph.FOLDER, accent, size = 17.dp) }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.widthIn(max = 200.dp)) {
                        Text(name, color = Hc.Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("$count videos · ${folders.size} folders", color = Hc.Mute, fontSize = 11.sp, maxLines = 1)
                    }
                    Spacer(Modifier.width(10.dp))
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = null, tint = Hc.Mute, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun IslandButton(onClick: () -> Unit, label: String, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color(0x14FFFFFF))
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun IslandPills(
    labels: List<String>,
    selected: Int,
    accent: Color,
    modifier: Modifier,
    onSelect: (Int) -> Unit,
) {
    val density = LocalDensity.current
    val xs = remember { mutableStateMapOf<Int, Float>() }
    val ws = remember { mutableStateMapOf<Int, Float>() }
    val x by animateFloatAsState(xs[selected] ?: 0f, spring(dampingRatio = 0.75f, stiffness = 480f), label = "isl-x")
    val w by animateFloatAsState(ws[selected] ?: 0f, spring(dampingRatio = 0.75f, stiffness = 480f), label = "isl-w")
    Box(modifier.fillMaxHeight().horizontalScroll(rememberScrollState())) {
        Box(
            Modifier
                .graphicsLayer { translationX = x }
                .width(with(density) { w.toDp() })
                .fillMaxHeight()
                .clip(RoundedCornerShape(22.dp))
                .background(accent),
        )
        Row(Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
            labels.forEachIndexed { i, label ->
                val on = i == selected
                val color by animateColorAsState(if (on) onAccent(accent) else Hc.Ink.copy(alpha = 0.78f), tween(200), label = "isl-c")
                Text(
                    label,
                    color = color,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .onGloballyPositioned {
                            xs[i] = it.positionInParent().x
                            ws[i] = it.size.width.toFloat()
                        }
                        .widthIn(max = 140.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .clickable(onClickLabel = label) { onSelect(i) }
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                )
            }
        }
    }
}

/** Glass sheet of folder cards, each with a small mosaic of its videos. Scales to any number of folders. */
@Composable
private fun FolderPanel(
    folders: List<Folder>,
    active: String,
    unavailable: Set<String>,
    library: List<VideoItem>,
    loader: ThumbnailLoader,
    thumbVersion: Int,
    accent: Color,
    navBottom: Dp,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit,
    onRefresh: () -> Unit,
    onClose: () -> Unit,
) {
    val haze = LocalHaze.current
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = 420f)) }
    var filter by remember { mutableStateOf("") }
    val byFolder = remember(library) { library.groupBy { it.folder } }
    val shown = folders.filter { filter.isBlank() || it.name.contains(filter.trim(), ignoreCase = true) }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = appear.value.coerceIn(0f, 1f) }
                .background(Color(0x8C000000))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        )
        val shape = RoundedCornerShape(30.dp)
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 10.dp, end = 10.dp, bottom = navBottom + 10.dp)
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .graphicsLayer {
                    val p = appear.value
                    alpha = p.coerceIn(0f, 1f)
                    translationY = (1f - p) * 160.dp.toPx()
                    scaleX = 0.94f + 0.06f * p
                    scaleY = 0.94f + 0.06f * p
                    transformOrigin = TransformOrigin(0.5f, 1f)
                }
                .shadow(30.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(shape)
                .glass(haze, shape, tint = Color(0xD9101113))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(top = 10.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).width(36.dp).height(4.dp).clip(CircleShape).background(Color(0x33FFFFFF)))
            Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 14.dp, top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Folders", style = TextStyle(fontFamily = Serif, fontStyle = FontStyle.Italic, fontSize = 30.sp, color = Hc.Ink), modifier = Modifier.weight(1f))
                IslandButton(onRefresh, "Refresh") { Icon(Icons.Default.Refresh, contentDescription = null, tint = Hc.Ink, modifier = Modifier.size(19.dp)) }
                Spacer(Modifier.width(8.dp))
                Row(
                    Modifier
                        .height(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(accent)
                        .clickable(onClick = onAdd)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = onAccent(accent), modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add", color = onAccent(accent), fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (folders.size > 6) {
                Row(
                    Modifier
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .fillMaxWidth()
                        .height(42.dp)
                        .clip(RoundedCornerShape(21.dp))
                        .background(Color(0x14FFFFFF))
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = Hc.Mute, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) {
                        if (filter.isEmpty()) Text("Find a folder", color = Hc.Faint, fontSize = 14.sp)
                        BasicTextField(
                            value = filter,
                            onValueChange = { filter = it },
                            singleLine = true,
                            textStyle = TextStyle(color = Hc.Ink, fontSize = 14.sp, fontFamily = Sans, fontWeight = FontWeight.Medium),
                            cursorBrush = SolidColor(accent),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                state = rememberLazyGridState(),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (filter.isBlank()) {
                    item(key = "all") {
                        FolderCard("All videos", library.size, library, null, active.isEmpty(), accent, loader, thumbVersion) { onSelect("") }
                    }
                }
                items(shown.size, key = { shown[it].uri }) { i ->
                    val f = shown[i]
                    val vids = byFolder[f.uri].orEmpty()
                    FolderCard(
                        f.name,
                        vids.size,
                        vids,
                        if (f.uri in unavailable) "No access" else null,
                        active == f.uri,
                        accent,
                        loader,
                        thumbVersion,
                    ) { onSelect(f.uri) }
                }
            }
        }
    }
}

@Composable
private fun FolderCard(
    name: String,
    count: Int,
    videos: List<VideoItem>,
    warning: String?,
    selected: Boolean,
    accent: Color,
    loader: ThumbnailLoader,
    thumbVersion: Int,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val picks = remember(videos) { videos.sortedByDescending { it.modified }.take(3) }
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .pressScale(interaction, 0.96f)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(shape)
                .background(Hc.Surface2)
                .then(if (selected) Modifier.border(2.dp, accent, shape) else Modifier),
        ) {
            // Mosaic: one tall cover on the left, two stacked on the right
            Row(Modifier.fillMaxSize().padding(if (selected) 4.dp else 0.dp).clip(RoundedCornerShape(if (selected) 16.dp else 20.dp))) {
                MosaicCell(picks.getOrNull(0), loader, thumbVersion, Modifier.weight(1.25f).fillMaxHeight())
                Spacer(Modifier.width(2.dp))
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    MosaicCell(picks.getOrNull(1), loader, thumbVersion, Modifier.weight(1f).fillMaxWidth())
                    Spacer(Modifier.height(2.dp))
                    MosaicCell(picks.getOrNull(2), loader, thumbVersion, Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(name, color = if (selected) accent else Hc.Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
        Text(
            warning ?: "$count video${if (count == 1) "" else "s"}",
            color = if (warning != null) accent else Hc.Mute,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
}

@Composable
private fun MosaicCell(v: VideoItem?, loader: ThumbnailLoader, thumbVersion: Int, modifier: Modifier) {
    Box(modifier.background(Hc.Raised)) {
        if (v != null) {
            val bmp = rememberCover(v, loader, thumbVersion)
            if (bmp != null) {
                Image(bmp.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

// ============================================================== empty state

@Composable
private fun EmptyState(
    noFolders: Boolean,
    scanning: Boolean,
    searching: Boolean,
    favourites: Boolean,
    lost: Boolean,
    accent: Color,
    onAdd: () -> Unit,
) {
    val (title, body) = when {
        noFolders -> "Your hollow is empty" to "Pick a folder of videos to fill it. Hidden folders with a .nomedia file work too."
        scanning -> "Gathering your videos" to "This only takes a moment."
        lost -> "Lost access to this folder" to "Open the folder list and add it again to restore it."
        searching -> "Nothing by that name" to "Try a different word, or clear the search."
        favourites -> "No favourites yet" to "Tap the heart while a video plays to keep it here."
        else -> "Nothing in this folder" to "Videos you add to it will show up here."
    }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 36.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EmptyArt(accent, scanning)
        Spacer(Modifier.height(28.dp))
        Text(title, style = TextStyle(fontFamily = Serif, fontStyle = FontStyle.Italic, fontSize = 30.sp, color = Hc.Ink, textAlign = TextAlign.Center))
        Spacer(Modifier.height(10.dp))
        Text(body, color = Hc.Mute, fontSize = 14.5.sp, lineHeight = 21.sp, textAlign = TextAlign.Center)
        if (noFolders) {
            Spacer(Modifier.height(28.dp))
            val interaction = remember { MutableInteractionSource() }
            Row(
                Modifier
                    .pressScale(interaction, 0.95f)
                    .shadow(18.dp, RoundedCornerShape(26.dp), ambientColor = accent, spotColor = accent)
                    .clip(RoundedCornerShape(26.dp))
                    .background(accent)
                    .clickable(interactionSource = interaction, indication = null, onClick = onAdd)
                    .padding(horizontal = 26.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = onAccent(accent), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Choose a folder", color = onAccent(accent), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Three softly floating portrait cards with a glow, drawn in code. */
@Composable
private fun EmptyArt(accent: Color, busy: Boolean) {
    val t = rememberInfiniteTransition(label = "empty")
    val float by t.animateFloat(0f, (Math.PI * 2).toFloat(), infiniteRepeatable(tween(if (busy) 1600 else 5200, easing = LinearEasing)), label = "float")
    Canvas(Modifier.size(width = 200.dp, height = 170.dp)) {
        val cw = size.width * 0.34f
        val ch = cw * 1.5f
        val cx = size.width / 2
        val cy = size.height / 2
        drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.35f), Color.Transparent), Offset(cx, cy), size.width * 0.55f), size.width * 0.55f, Offset(cx, cy))
        val cards = listOf(Triple(-0.62f, -12f, 0.55f), Triple(0.62f, 12f, 0.55f), Triple(0f, 0f, 1f))
        cards.forEachIndexed { i, (dx, angle, a) ->
            val bob = sin(float + i * 1.9f) * 6.dp.toPx()
            val center = Offset(cx + dx * cw, cy + bob)
            rotate(angle, center) {
                val tl = Offset(center.x - cw / 2, center.y - ch / 2)
                val r = CornerRadius(14.dp.toPx())
                drawRoundRect(Color(0xFF1C1E22), tl, Size(cw, ch), r)
                drawRoundRect(
                    Brush.verticalGradient(listOf(accent.copy(alpha = 0.55f * a), accent.copy(alpha = 0.12f * a)), startY = tl.y, endY = tl.y + ch),
                    tl, Size(cw, ch), r,
                )
                drawRoundRect(Color(0x1FFFFFFF), tl, Size(cw, ch), r, style = Stroke(1.dp.toPx()))
                if (i == 2) {
                    val s = cw * 0.16f
                    val p = Path().apply {
                        moveTo(center.x - s * 0.7f, center.y - s)
                        lineTo(center.x + s * 0.95f, center.y)
                        lineTo(center.x - s * 0.7f, center.y + s)
                        close()
                    }
                    drawPath(p, Color.White.copy(alpha = 0.92f))
                }
            }
        }
    }
}

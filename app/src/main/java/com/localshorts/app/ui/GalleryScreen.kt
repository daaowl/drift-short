package com.localshorts.app.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localshorts.app.MainActivity
import com.localshorts.app.MainViewModel
import com.localshorts.app.data.SortOrder
import com.localshorts.app.data.ThumbnailLoader
import com.localshorts.app.data.VideoItem

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
    val watch by vm.watchCounts.collectAsState()
    val favs by vm.favourites.collectAsState()
    val query by vm.query.collectAsState()
    val scanning by vm.scanning.collectAsState()
    val unavailable by vm.unavailable.collectAsState()
    val thumbVersion by vm.thumbVersion.collectAsState()
    val accent = LocalAccent.current
    val focus = LocalFocusManager.current
    val density = LocalDensity.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.addFolder(uri)
    }
    val addFolder = {
        activity.suppressNextLock = true
        pick.launch(null)
    }

    // Header slides away while scrolling down the feed and comes back on the way up
    var headerPx by remember { mutableIntStateOf(0) }
    var headerOffset by remember { mutableFloatStateOf(0f) }
    val collapse = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                headerOffset = (headerOffset + available.y).coerceIn(-headerPx.toFloat(), 0f)
                return Offset.Zero
            }
        }
    }
    // Always show the header at the very top of the feed
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0 }
            .collect { atTop -> if (atTop) headerOffset = 0f }
    }

    val statusTop = WindowInsets.statusBarsIgnoringVisibility.getTop(density)
    val navBottom = WindowInsets.navigationBarsIgnoringVisibility.getBottom(density)

    Box(
        modifier
            .fillMaxSize()
            .background(Hc.Bg)
            .nestedScroll(collapse),
    ) {
        if (videos.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(top = with(density) { (statusTop + headerPx).toDp() })) {
                EmptyState(
                    noFolders = settings.loaded && settings.folders.isEmpty(),
                    scanning = scanning,
                    searching = query.isNotBlank(),
                    favourites = settings.favouritesOnly,
                    lost = settings.folders.isNotEmpty() && settings.folders.all { it.uri in unavailable },
                    onAdd = addFolder,
                )
            }
        } else {
            val cols = settings.columns
            LazyVerticalGrid(
                columns = GridCells.Fixed(cols),
                state = gridState,
                contentPadding = with(density) {
                    PaddingValues(top = (statusTop + headerPx).toDp(), bottom = navBottom.toDp() + 8.dp)
                },
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(videos, key = { _, v -> v.id }) { index, v ->
                    Thumb(
                        v = v,
                        loader = vm.thumbs,
                        thumbVersion = thumbVersion,
                        columns = cols,
                        favourite = v.id in favs,
                        views = watch[v.id] ?: 0,
                        modifier = Modifier.onGloballyPositioned { bounds[v.id] = it.boundsInRoot() },
                        onClick = {
                            focus.clearFocus()
                            onOpen(videos, index)
                        },
                    )
                }
            }
        }

        // Header (slides under the status bar scrim)
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = with(density) { statusTop.toDp() })
                .graphicsLayer { translationY = headerOffset }
                .background(Hc.Bg)
                .onSizeChanged { headerPx = it.height },
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 2.dp, top = 6.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(logoRes(settings.icon)),
                    contentDescription = "Hollow",
                    modifier = Modifier.size(26.dp),
                )
                Spacer(Modifier.width(10.dp))
                SearchField(
                    query = query,
                    accent = accent,
                    onChange = { vm.query.value = it },
                    onDone = { focus.clearFocus() },
                    modifier = Modifier.weight(1f),
                )
                FolderMenu(
                    folders = settings.folders,
                    active = settings.activeFolder,
                    unavailable = unavailable,
                    accent = accent,
                    onSelect = { vm.setActiveFolder(it) },
                    onAdd = addFolder,
                    onRefresh = { vm.refresh() },
                )
                IconButton(onClick = onSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Hc.Ink)
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val fav = settings.favouritesOnly
                TabPill("Latest", !fav && settings.sort == SortOrder.NEWEST, accent) { vm.setView(false, SortOrder.NEWEST) }
                TabPill("Oldest", !fav && settings.sort == SortOrder.OLDEST, accent) { vm.setView(false, SortOrder.OLDEST) }
                TabPill("Favourites", fav, accent) { vm.setView(true, settings.sort) }
                Spacer(Modifier.weight(1f))
                SortMenu(settings.sort, accent) { vm.setView(settings.favouritesOnly, it) }
            }
        }

        // Status bar scrim so content never shows behind the clock
        Box(
            Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBarsIgnoringVisibility)
                .background(Hc.Bg),
        )
    }
}

@Composable
private fun SearchField(query: String, accent: Color, onChange: (String) -> Unit, onDone: () -> Unit, modifier: Modifier) {
    Row(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Hc.Surface)
            .padding(start = 12.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Search, contentDescription = null, tint = Hc.Mute, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text("Search", color = Hc.Mute, fontSize = 15.sp)
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = Hc.Ink, fontSize = 15.sp),
                cursorBrush = SolidColor(accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onDone() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            IconButton(onClick = { onChange(""); onDone() }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Clear search", tint = Hc.Mute, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun FolderMenu(
    folders: List<com.localshorts.app.data.Folder>,
    active: String,
    unavailable: Set<String>,
    accent: Color,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit,
    onRefresh: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            GlyphIcon(Glyph.FOLDER, if (active.isNotEmpty()) accent else Hc.Ink)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.background(Hc.Surface)) {
            MenuRow("All folders", checked = active.isEmpty(), accent = accent) { onSelect(""); open = false }
            folders.forEach { f ->
                val lost = f.uri in unavailable
                MenuRow(
                    if (lost) "${f.name} (no access)" else f.name,
                    checked = active == f.uri,
                    accent = accent,
                ) { onSelect(f.uri); open = false }
            }
            HorizontalDivider(color = Color(0x22FFFFFF))
            DropdownMenuItem(
                text = { Text("Add folder", color = Hc.Ink) },
                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, tint = Hc.Ink) },
                onClick = { open = false; onAdd() },
            )
            DropdownMenuItem(
                text = { Text("Refresh", color = Hc.Ink) },
                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null, tint = Hc.Ink) },
                onClick = { open = false; onRefresh() },
            )
        }
    }
}

@Composable
private fun SortMenu(sort: SortOrder, accent: Color, onPick: (SortOrder) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val custom = sort == SortOrder.NAME || sort == SortOrder.MOST_WATCHED
    Box {
        Row(
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .clickable { open = true }
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlyphIcon(Glyph.SORT, if (custom) accent else Hc.Mute, size = 18.dp)
            Spacer(Modifier.width(4.dp))
            Text(sort.label, color = if (custom) accent else Hc.Mute, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.background(Hc.Surface)) {
            SortOrder.values().forEach { o ->
                MenuRow(o.label, checked = o == sort, accent = accent) { onPick(o); open = false }
            }
        }
    }
}

@Composable
private fun MenuRow(label: String, checked: Boolean, accent: Color, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, color = if (checked) accent else Hc.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingIcon = { if (checked) Icon(Icons.Default.Check, contentDescription = "Selected", tint = accent) },
        onClick = onClick,
    )
}

@Composable
private fun TabPill(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) onAccent(accent) else Hc.Ink,
        fontSize = 14.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) accent else Hc.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

@Composable
private fun EmptyState(
    noFolders: Boolean,
    scanning: Boolean,
    searching: Boolean,
    favourites: Boolean,
    lost: Boolean,
    onAdd: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val text = when {
            noFolders -> "Add a folder to start.\nHidden folders with a .nomedia file work too."
            scanning -> "Loading videos"
            lost -> "Hollow lost access to this folder.\nRemove it in Settings and add it again."
            searching -> "No videos match your search"
            favourites -> "No favourites yet.\nPause a video and use ⋮ to add one."
            else -> "No videos in this folder"
        }
        Text(text, color = Hc.Mute, fontSize = 15.sp, lineHeight = 22.sp, textAlign = TextAlign.Center)
        if (noFolders) {
            Spacer(Modifier.height(20.dp))
            Row(
                Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(LocalAccent.current)
                    .clickable(onClick = onAdd)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val fg = onAccent(LocalAccent.current)
                Icon(Icons.Default.Add, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add folder", color = fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun rememberCover(v: VideoItem, loader: ThumbnailLoader, thumbVersion: Int): Bitmap? {
    val key = loader.coverKey(v)
    val state = produceState(initialValue = loader.cachedCover(v), v.id, key, thumbVersion) {
        val cached = loader.cachedCover(v)
        if (cached != null) value = cached else loader.cover(v)?.let { value = it }
    }
    return state.value
}

@Composable
private fun Thumb(
    v: VideoItem,
    loader: ThumbnailLoader,
    thumbVersion: Int,
    columns: Int,
    favourite: Boolean,
    views: Int,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val bmp = rememberCover(v, loader, thumbVersion)
    val small = columns >= 3
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(if (columns == 1) 4f / 5f else 9f / 16f)
            .background(Hc.Surface)
            .clickable(onClick = onClick),
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = v.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (favourite) {
            Icon(
                Icons.Default.Favorite,
                contentDescription = "Favourite",
                tint = Color.White,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(if (small) 12.dp else 14.dp),
            )
        }
        Text(
            viewsLabel(views),
            style = TextStyle(
                color = Color.White,
                fontSize = if (small) 9.sp else 10.sp,
                fontWeight = FontWeight.Medium,
                shadow = VideoTextShadow,
            ),
            maxLines = 1,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 6.dp, bottom = 5.dp),
        )
    }
}

fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

fun viewsLabel(n: Int): String = when {
    n == 1 -> "1 view"
    n < 1000 -> "$n views"
    n < 1_000_000 -> trim1(n / 1000.0) + "K views"
    else -> trim1(n / 1_000_000.0) + "M views"
}

private fun trim1(d: Double): String {
    val s = String.format(java.util.Locale.US, "%.1f", d)
    return if (s.endsWith(".0")) s.dropLast(2) else s
}

package com.localshorts.app.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localshorts.app.MainViewModel
import com.localshorts.app.R
import com.localshorts.app.data.GalleryTab
import com.localshorts.app.data.ThumbnailLoader
import com.localshorts.app.data.VideoItem

@Composable
fun GalleryScreen(
    vm: MainViewModel,
    gridState: LazyGridState,
    onOpen: (List<VideoItem>, Int) -> Unit,
    onSettings: () -> Unit,
) {
    val settings by vm.settings.collectAsState()
    val videos by vm.gallery.collectAsState()
    val favs by vm.favourites.collectAsState()
    val watch by vm.watchCounts.collectAsState()
    val query by vm.query.collectAsState()
    val scanning by vm.scanning.collectAsState()
    val unavailable by vm.unavailable.collectAsState()
    val accent = LocalAccent.current
    val focus = LocalFocusManager.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.addFolder(uri)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Hc.Bg)
            .statusBarsPadding(),
    ) {
        // Header: logo, search, settings
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.logo_mark),
                contentDescription = "Hollow",
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(12.dp))
            Row(
                Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(21.dp))
                    .background(Hc.Surface)
                    .padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = Hc.Mute, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Search videos", color = Hc.Mute, fontSize = 15.sp)
                    BasicTextField(
                        value = query,
                        onValueChange = { vm.query.value = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Hc.Ink, fontSize = 15.sp),
                        cursorBrush = SolidColor(accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (query.isNotEmpty()) {
                    IconButton(onClick = { vm.query.value = ""; focus.clearFocus() }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search", tint = Hc.Mute, modifier = Modifier.size(18.dp))
                    }
                }
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Hc.Ink)
            }
        }

        // Tabs sit right under the header; grid columns toggle on the right
        Row(
            Modifier.fillMaxWidth().padding(start = 6.dp, end = 12.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(GalleryTab.LATEST to "Latest", GalleryTab.OLDEST to "Oldest", GalleryTab.FAVOURITES to "Favourites")
                .forEach { (tab, label) -> TabLabel(label, settings.tab == tab, accent) { vm.setTab(tab) } }
            Spacer(Modifier.weight(1f))
            ColumnsButton(settings.columns) { vm.setColumns(settings.columns % 4 + 1) }
        }

        // Folder chips, only when there is something to switch between
        if (settings.folders.size > 1 || unavailable.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Chip("All folders", settings.activeFolder.isEmpty(), accent) { vm.setActiveFolder("") }
                settings.folders.forEach { f ->
                    val lost = f.uri in unavailable
                    Chip(if (lost) f.name + " (no access)" else f.name, settings.activeFolder == f.uri, accent) {
                        vm.setActiveFolder(f.uri)
                    }
                }
            }
        } else {
            Spacer(Modifier.height(6.dp))
        }

        Box(Modifier.fillMaxSize()) {
            if (videos.isEmpty()) {
                EmptyState(
                    noFolders = settings.loaded && settings.folders.isEmpty(),
                    scanning = scanning,
                    searching = query.isNotBlank(),
                    favourites = settings.tab == GalleryTab.FAVOURITES,
                    lost = settings.folders.isNotEmpty() && settings.folders.all { it.uri in unavailable },
                    onAdd = { pick.launch(null) },
                )
            } else {
                val cols = settings.columns
                LazyVerticalGrid(
                    columns = GridCells.Fixed(cols),
                    state = gridState,
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                ) {
                    itemsIndexed(videos, key = { _, v -> v.id }) { index, v ->
                        Thumb(
                            v = v,
                            loader = vm.thumbs,
                            columns = cols,
                            favourite = v.id in favs,
                            views = watch[v.id] ?: 0,
                            onClick = {
                                focus.clearFocus()
                                onOpen(videos, index)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TabLabel(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    val lineWidth by animateDpAsState(if (selected) 18.dp else 0.dp, label = "tab")
    Column(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            label,
            color = if (selected) Hc.Ink else Hc.Mute,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
        Spacer(Modifier.height(5.dp))
        Box(
            Modifier
                .width(lineWidth)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent),
        )
    }
}

@Composable
private fun ColumnsButton(columns: Int, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Hc.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(16.dp)) {
            val g = size.width * 0.14f
            val cell = (size.width - g) / 2
            for (r in 0..1) for (c in 0..1) {
                drawRoundRect(
                    color = Hc.Ink,
                    topLeft = Offset(c * (cell + g), r * (cell + g)),
                    size = Size(cell, cell),
                    cornerRadius = CornerRadius(cell * 0.25f),
                    style = Stroke(width = size.width * 0.1f),
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        Text("$columns", color = Hc.Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) onAccent(accent) else Hc.Ink,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) accent else Hc.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
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
            favourites -> "No favourites yet.\nTap the heart in the player to add one."
            else -> "No videos in this folder"
        }
        Text(text, color = Hc.Mute, fontSize = 15.sp, lineHeight = 22.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
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
fun rememberCover(v: VideoItem, loader: ThumbnailLoader): Bitmap? {
    val state = produceState(initialValue = loader.cachedCover(v), v.id) {
        if (value == null) value = loader.cover(v)
    }
    return state.value
}

@Composable
private fun Thumb(
    v: VideoItem,
    loader: ThumbnailLoader,
    columns: Int,
    favourite: Boolean,
    views: Int,
    onClick: () -> Unit,
) {
    val bmp = rememberCover(v, loader)
    val small = columns >= 3
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(if (columns == 1) 4f / 5f else 9f / 16f)
            .clip(RoundedCornerShape(if (small) 10.dp else 14.dp))
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
        val dur = loader.duration(v)
        if (dur > 0) {
            Text(
                formatTime(dur),
                color = Color.White,
                fontSize = if (small) 10.sp else 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x99000000))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            )
        }
        if (favourite) {
            Icon(
                Icons.Default.Favorite,
                contentDescription = "Favourite",
                tint = Color.White,
                modifier = Modifier.align(Alignment.TopStart).padding(7.dp).size(if (small) 14.dp else 16.dp),
            )
        }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC0E1013))))
                .padding(start = 8.dp, end = 8.dp, top = 22.dp, bottom = 8.dp),
        ) {
            if (columns <= 2) {
                Text(
                    v.name,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 17.sp,
                )
                Spacer(Modifier.height(2.dp))
            }
            Text(viewsLabel(views), color = Color(0xFFD2D2D2), fontSize = if (small) 10.sp else 11.sp, maxLines = 1)
        }
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

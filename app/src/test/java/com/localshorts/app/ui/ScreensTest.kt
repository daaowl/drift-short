package com.localshorts.app.ui

import androidx.compose.foundation.layout.fillMaxHeight
import com.localshorts.app.data.Backdrop
import com.localshorts.app.data.LookStyle

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.localshorts.app.data.Folder
import com.localshorts.app.data.VideoItem
import org.junit.Rule
import org.junit.Test

/** Renders the redesigned screens off-device so the look can be checked on every build. */
class ScreensTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6,
        theme = "android:Theme.Material.NoActionBar",
        maxPercentDifference = 100.0,
    )

    private val accent = Color(0xFFE5484D)

    @Test
    fun home_two_columns() = paparazzi.snapshot { Frame { HomeMock(columns = 2, folders = 3, selected = 1) } }

    @Test
    fun home_three_columns_classic() = paparazzi.snapshot { Frame { HomeMock(columns = 3, ratio = 3f / 4f, folders = 2, selected = 0) } }

    @Test
    fun home_many_folders_square() = paparazzi.snapshot { Frame { HomeMock(columns = 2, ratio = 1f, folders = 9, selected = 2) } }

    @Test fun style_rounded() = paparazzi.snapshot { Frame(LookStyle.ROUNDED) { HomeMock(columns = 2, folders = 3, selected = 1) } }
    @Test fun style_clean() = paparazzi.snapshot { Frame(LookStyle.CLEAN) { HomeMock(columns = 2, folders = 3, selected = 1, backdrop = Backdrop.OCEAN) } }
    @Test fun style_bold() = paparazzi.snapshot { Frame(LookStyle.BOLD) { HomeMock(columns = 2, folders = 3, selected = 1, backdrop = Backdrop.SUNSET) } }
    @Test fun style_system() = paparazzi.snapshot { Frame(LookStyle.SYSTEM) { HomeMock(columns = 2, folders = 3, selected = 1, backdrop = Backdrop.AURORA) } }

    @Test fun auto_shapes() = paparazzi.snapshot { Frame { HomeMock(columns = 2, folders = 3, selected = 1, auto = true) } }
    @Test fun auto_shapes_three() = paparazzi.snapshot { Frame { HomeMock(columns = 3, folders = 3, selected = 0, auto = true) } }

    @Test fun backdrops() = paparazzi.snapshot {
        Frame {
            Row(Modifier.fillMaxSize()) {
                Backdrop.values().forEach { b -> HollowBackdrop(b, Modifier.weight(1f).fillMaxHeight(), animate = false) }
            }
        }
    }

    @Test
    fun empty() = paparazzi.snapshot {
        Frame {
            Box(Modifier.fillMaxSize().background(Hc.Bg)) {
                HollowBackdrop(Backdrop.RAVE, Modifier.fillMaxSize(), animate = false)
                EmptyState(noFolders = true, scanning = false, searching = false, favourites = false, lost = false, accent = accent, onAdd = {})
            }
        }
    }

    @Composable
    private fun Frame(look: LookStyle = LookStyle.CLASSIC, content: @Composable () -> Unit) = HollowTheme(accent, look) { content() }

    @Composable
    private fun HomeMock(columns: Int, folders: Int, selected: Int, ratio: Float = 9f / 16f, backdrop: Backdrop = Backdrop.RAVE, auto: Boolean = false) {
        val gutter = 0.dp
        val shapes = listOf(0.5625f, 0.5625f, 1.78f, 0.5625f, 0.75f, 0.5625f, 1f, 1.78f, 0.5625f, 0.5625f, 0.8f, 0.5625f)
        val layout = if (auto) justifyRows(shapes, columns) else null
        val w = 411.4.dp
        val radius = 0.dp
        val covers = remember { List(12) { fakeFrame(it) } }
        val names = listOf("Phonk edits", "Night drives", "Drift", "JDM", "Saved", "Cosplay x cars", "Slowed", "Reverb", "Archive")
        val fs = List(folders) { Folder("uri$it", names[it % names.size]) }
        val lib = List(30) { VideoItem("v$it", android.net.Uri.EMPTY, "Video $it", it.toLong(), 1L, "uri${it % folders.coerceAtLeast(1)}") }
        Box(Modifier.fillMaxSize().background(Hc.Bg)) {
            HollowBackdrop(backdrop, Modifier.fillMaxSize(), animate = false)
            LazyVerticalGrid(
                columns = GridCells.Fixed(if (auto) SPAN_UNITS else columns),
                contentPadding = PaddingValues(top = 150.dp, bottom = 110.dp),
                horizontalArrangement = Arrangement.spacedBy(gutter),
                verticalArrangement = Arrangement.spacedBy(gutter),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(12, span = { i -> androidx.compose.foundation.lazy.grid.GridItemSpan(layout?.spans?.get(i) ?: 1) }) { i ->
                    TileVisual(
                        height = if (layout != null) w / layout.rowSums[i] else null,
                        cover = covers[i],
                        strip = null,
                        name = "Video $i",
                        columns = columns,
                        ratio = ratio,
                        radius = radius,
                        favourite = i % 5 == 1,
                        views = listOf(0, 3, 12, 1, 48, 7, 230, 2, 5, 19, 1200, 4)[i],
                    )
                }
            }
            Column(Modifier.fillMaxWidth().padding(top = 28.dp)) {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Wordmark(accent, Modifier.weight(1f))
                    GlassCircle(onClick = {}, label = "Search") { Icon(Icons.Default.Search, null, tint = Hc.Ink, modifier = Modifier.size(20.dp)) }
                    Spacer(Modifier.width(10.dp))
                    GlassCircle(onClick = {}, label = "Settings") { Icon(Icons.Default.Settings, null, tint = Hc.Ink, modifier = Modifier.size(20.dp)) }
                }
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 6.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterControl(listOf("Latest", "Oldest", "Most watched", "Favourites"), selected = 2, accent = accent, modifier = Modifier.weight(1f, fill = false)) {}
                    Spacer(Modifier.weight(0.01f))
                    Spacer(Modifier.width(10.dp))
                    GlassCircle(onClick = {}, label = "Sort", size = 40.dp) { GlyphIcon(Glyph.SORT, Hc.Ink, size = 19.dp) }
                }
            }
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 30.dp)) {
                FolderIsland(
                    folders = fs,
                    active = if (selected == 0) "" else fs.getOrNull(selected - 1)?.uri ?: "",
                    unavailable = emptySet(),
                    library = lib,
                    accent = accent,
                    onSelect = {},
                    onOpenPanel = {},
                    onAdd = {},
                )
            }
        }
    }

    /** A stand-in "video frame": moody gradient sky, a glowing light and a dark horizon. */
    private fun fakeFrame(seed: Int): Bitmap {
        val w = 270
        val h = 480
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val hues = floatArrayOf(350f, 20f, 210f, 280f, 160f, 35f, 300f, 190f, 5f, 240f, 120f, 330f)
        val hue = hues[seed % hues.size]
        val top = android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.55f, 0.55f))
        val bottom = android.graphics.Color.HSVToColor(floatArrayOf((hue + 30f) % 360f, 0.7f, 0.12f))
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), top, bottom, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        val lx = w * (0.25f + (seed * 37 % 50) / 100f)
        val ly = h * (0.28f + (seed * 13 % 20) / 100f)
        p.shader = RadialGradient(lx, ly, w * 0.55f, android.graphics.Color.argb(200, 255, 230, 200), android.graphics.Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawCircle(lx, ly, w * 0.55f, p)
        p.shader = null
        p.color = android.graphics.Color.argb(230, 8, 8, 12)
        c.drawRect(0f, h * 0.72f, w.toFloat(), h.toFloat(), p)
        p.color = android.graphics.Color.argb(255, 20, 20, 26)
        c.drawRoundRect(w * 0.2f, h * 0.6f, w * 0.8f, h * 0.76f, 30f, 30f, p)
        return b
    }
}

package com.localshorts.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.localshorts.app.R
import com.localshorts.app.data.Folder
import com.localshorts.app.data.VideoItem
import org.junit.Rule
import org.junit.Test

/** Side-by-side look options for the home screen: font, wordmark and filter tabs over the mesh. */
class VariantsTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6, theme = "android:Theme.Material.NoActionBar", maxPercentDifference = 100.0)

    private val nunito = FontFamily(Font(R.font.nunito_600, FontWeight.SemiBold), Font(R.font.nunito_700, FontWeight.Bold), Font(R.font.nunito_800, FontWeight.ExtraBold))
    private val outfit = FontFamily(Font(R.font.outfit_500, FontWeight.Medium), Font(R.font.outfit_600, FontWeight.SemiBold), Font(R.font.outfit_700, FontWeight.Bold), Font(R.font.outfit_800, FontWeight.ExtraBold))
    private val fredoka = FontFamily(Font(R.font.fredoka_500, FontWeight.Medium), Font(R.font.fredoka_600, FontWeight.SemiBold), Font(R.font.fredoka_700, FontWeight.Bold))

    private enum class Tabs { CHIPS, UNDERLINE, SEGMENT }

    @Test fun option_a_nunito_chips() = paparazzi.snapshot { Mock(nunito, FontWeight.ExtraBold, Tabs.CHIPS) }
    @Test fun option_b_outfit_underline() = paparazzi.snapshot { Mock(outfit, FontWeight.ExtraBold, Tabs.UNDERLINE) }
    @Test fun option_c_fredoka_segment() = paparazzi.snapshot { Mock(fredoka, FontWeight.Bold, Tabs.SEGMENT) }
    @Test fun option_d_system_chips() = paparazzi.snapshot { Mock(FontFamily.Default, FontWeight.Black, Tabs.CHIPS) }

    @Composable
    private fun Mock(font: FontFamily, wordWeight: FontWeight, tabs: Tabs) = HollowTheme(Color(0xFFE5484D)) {
        val covers = remember { List(12) { fakeFrame(it) } }
        val fs = listOf(Folder("a", "Phonk edits"), Folder("b", "Night drives"), Folder("c", "Drift"))
        val lib = List(20) { VideoItem("v$it", android.net.Uri.EMPTY, "Video $it", it.toLong(), 1L, listOf("a", "b", "c")[it % 3]) }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            MeshGradient(Modifier.fillMaxSize(), animate = false)
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(top = 168.dp, bottom = 110.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(12) { i ->
                    TileVisual(covers[i], null, "Video $i", 2, 9f / 16f, 0.dp, i % 5 == 1, listOf(0, 3, 12, 1, 48, 7, 230, 2, 5, 19, 1200, 4)[i])
                }
            }
            Column(Modifier.fillMaxWidth().padding(top = 30.dp)) {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 14.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("hollow", style = TextStyle(fontFamily = font, fontWeight = wordWeight, fontSize = 38.sp, color = Color.White, letterSpacing = (-1).sp), modifier = Modifier.weight(1f))
                    Circle { Icon(Icons.Default.Search, null, tint = Color.White, modifier = Modifier.size(21.dp)) }
                    Spacer(Modifier.width(10.dp))
                    Circle { Icon(Icons.Default.Settings, null, tint = Color.White, modifier = Modifier.size(21.dp)) }
                }
                Spacer(Modifier.height(14.dp))
                val labels = listOf("Latest", "Oldest", "Most watched", "Favourites")
                Row(Modifier.fillMaxWidth().padding(end = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).horizontalScroll(androidx.compose.foundation.rememberScrollState())) {
                when (tabs) {
                    Tabs.CHIPS -> Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        labels.forEachIndexed { i, l ->
                            val on = i == 0
                            Text(
                                l,
                                style = TextStyle(fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (on) Color(0xFF14121A) else Color.White),
                                modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(if (on) Color.White else Color(0x33FFFFFF)).padding(horizontal = 14.dp, vertical = 8.dp),
                            )
                        }
                    }
                    Tabs.UNDERLINE -> Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                        labels.forEachIndexed { i, l ->
                            val on = i == 0
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(l, style = TextStyle(fontFamily = font, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, fontSize = 15.sp, color = if (on) Color.White else Color(0xB3FFFFFF)))
                                Spacer(Modifier.height(6.dp))
                                Box(Modifier.width(if (on) 22.dp else 0.dp).height(3.dp).clip(CircleShape).background(Color.White))
                            }
                        }
                    }
                    Tabs.SEGMENT -> Row(
                        Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(22.dp)).background(Color(0x40000000)).padding(4.dp),
                    ) {
                        labels.forEachIndexed { i, l ->
                            val on = i == 0
                            Text(
                                l,
                                style = TextStyle(fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = if (on) Color(0xFF14121A) else Color.White),
                                modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(if (on) Color.White else Color.Transparent).padding(horizontal = 13.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
                }
                Spacer(Modifier.width(8.dp))
                Circle { GlyphIcon(Glyph.SORT, Color.White, size = 20.dp) }
                }
            }
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 30.dp)) {
                ClearIsland(font)
            }
        }
    }

    /** See-through folder switcher: light frosted tint and a hairline edge, white selected pill. */
    @Composable
    private fun ClearIsland(font: FontFamily) {
        val shape = RoundedCornerShape(28.dp)
        Row(
            Modifier
                .clip(shape)
                .background(Color(0x2EFFFFFF))
                .border(0.8.dp, Color(0x40FFFFFF), shape)
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf("All", "Phonk edits", "Night drives").forEachIndexed { i, l ->
                val on = i == 1
                Text(
                    l,
                    style = TextStyle(fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (on) Color(0xFF14121A) else Color.White),
                    modifier = Modifier.clip(RoundedCornerShape(22.dp)).background(if (on) Color.White else Color.Transparent).padding(horizontal = 15.dp, vertical = 11.dp),
                )
            }
            Spacer(Modifier.width(4.dp))
            Box(Modifier.size(42.dp).clip(CircleShape).background(Color(0x26FFFFFF)), contentAlignment = Alignment.Center) {
                GlyphIcon(Glyph.GRID, Color.White, size = 18.dp)
            }
        }
    }

    @Test fun gradient_full() = paparazzi.snapshot {
        HollowTheme(Color(0xFFE5484D)) {
            Box(Modifier.fillMaxSize()) {
                MeshGradient(Modifier.fillMaxSize(), animate = false)
                Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 30.dp)) { ClearIsland(fredoka) }
            }
        }
    }

    @Composable
    private fun Circle(content: @Composable () -> Unit) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(Color(0x33FFFFFF)), contentAlignment = Alignment.Center) { content() }
    }

    private fun fakeFrame(seed: Int): Bitmap {
        val w = 270
        val h = 480
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val hues = floatArrayOf(350f, 20f, 210f, 280f, 160f, 35f, 300f, 190f, 5f, 240f, 120f, 330f)
        val hue = hues[seed % hues.size]
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.5f, 0.6f)), android.graphics.Color.HSVToColor(floatArrayOf((hue + 30f) % 360f, 0.7f, 0.12f)), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        val lx = w * (0.25f + (seed * 37 % 50) / 100f)
        p.shader = RadialGradient(lx, h * 0.35f, w * 0.55f, android.graphics.Color.argb(200, 255, 230, 200), android.graphics.Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawCircle(lx, h * 0.35f, w * 0.55f, p)
        return b
    }
}

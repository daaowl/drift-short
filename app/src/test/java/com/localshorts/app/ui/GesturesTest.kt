package com.localshorts.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.localshorts.app.data.Gestures
import com.localshorts.app.ui.player.HeartPop
import com.localshorts.app.ui.player.SeekBubble
import com.localshorts.app.ui.player.SeekPreview
import com.localshorts.app.ui.player.SkipFlash
import com.localshorts.app.ui.player.SkipFlashOverlay
import org.junit.Rule
import org.junit.Test

class GesturesTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6, theme = "android:Theme.Material.NoActionBar", maxPercentDifference = 100.0)

    private val accent = Color(0xFFE5484D)

    @Test fun gesture_maps() = paparazzi.snapshot {
        HollowTheme(accent) {
            Row(
                Modifier.fillMaxSize().background(Hc.Bg).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GestureLayer.values().forEach { l ->
                    GestureMap(l, Gestures(), brightnessOn = true, volumeOn = true, accent = accent, onChange = {}, onCommit = {}, width = 124.dp)
                }
            }
        }
    }

    @Test fun player_feedback() = paparazzi.snapshot {
        HollowTheme(accent) {
            Box(Modifier.fillMaxSize().background(Color(0xFF30283C))) {
                SkipFlashOverlay(SkipFlash(1, 15, 1)) {}
                SeekBubble(SeekPreview(83_000, 12_000, 225_000), accent, Modifier.align(Alignment.Center))
            }
        }
    }
}

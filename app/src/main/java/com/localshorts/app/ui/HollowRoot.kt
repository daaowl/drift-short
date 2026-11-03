package com.localshorts.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.localshorts.app.MainViewModel
import com.localshorts.app.data.VideoItem
import com.localshorts.app.ui.player.PlayerScreen

private class OpenPlayer(val list: List<VideoItem>, val start: Int)

/**
 * The gallery always stays composed underneath the player and settings, so its scroll position,
 * tab and loaded thumbnails survive opening a video or settings.
 */
@Composable
fun HollowRoot(vm: MainViewModel) {
    val settings by vm.settings.collectAsState()
    var open by remember { mutableStateOf<OpenPlayer?>(null) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val gridState = rememberSaveable(saver = LazyGridState.Saver) { LazyGridState() }

    HollowTheme(accent = Color(settings.accent)) {
        Box(Modifier.fillMaxSize()) {
            GalleryScreen(
                vm = vm,
                gridState = gridState,
                onOpen = { list, index -> open = OpenPlayer(list, index) },
                onSettings = { showSettings = true },
            )
            open?.let { o ->
                PlayerScreen(
                    vm = vm,
                    videos = o.list,
                    startIndex = o.start,
                    onClose = { open = null },
                )
            }
            if (showSettings) {
                BackHandler { showSettings = false }
                SettingsScreen(vm = vm, onBack = { showSettings = false })
            }
        }
    }
}

package com.localshorts.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.localshorts.app.data.EndBehavior
import com.localshorts.app.data.Folder
import com.localshorts.app.data.InfoStyle
import com.localshorts.app.data.Settings
import com.localshorts.app.data.SortOrder
import com.localshorts.app.data.ThumbMode
import com.localshorts.app.data.TransitionSpeed
import com.localshorts.app.data.VideoItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val hollow = app as HollowApp
    val thumbs = hollow.thumbs
    val thumbStore = hollow.thumbStore

    val settings: StateFlow<Settings> =
        hollow.settings.flow.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    val favourites: StateFlow<Set<String>> =
        hollow.userData.favourites.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val watchCounts: StateFlow<Map<String, Int>> =
        hollow.userData.watchCounts.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    val scanning: StateFlow<Boolean> = hollow.library.scanning
    val unavailable: StateFlow<Set<String>> = hollow.library.unavailable
    val thumbVersion: StateFlow<Int> = hollow.thumbStore.version

    val query = MutableStateFlow("")

    /** The gallery list for the current folder, filter, sort and search. Never rescans. */
    val gallery: StateFlow<List<VideoItem>> = combine(
        hollow.library.videos, settings, favourites, watchCounts, query,
    ) { videos, s, favs, watch, q ->
        val needle = q.trim().lowercase(Locale.ROOT)
        var list = when {
            needle.isNotEmpty() -> videos.filter { it.name.lowercase(Locale.ROOT).contains(needle) }
            s.activeFolder.isEmpty() -> videos
            else -> videos.filter { it.folder == s.activeFolder }
        }
        if (s.favouritesOnly) list = list.filter { it.id in favs }
        when (s.sort) {
            SortOrder.NEWEST -> list.sortedByDescending { it.modified }
            SortOrder.OLDEST -> list.sortedBy { it.modified }
            SortOrder.NAME -> list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            SortOrder.MOST_WATCHED -> list.sortedWith(
                compareByDescending<VideoItem> { watch[it.id] ?: 0 }.thenByDescending { it.modified },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            settings.filter { it.loaded }.map { it.folders }.distinctUntilChanged().collect {
                hollow.library.refresh(it)
            }
        }
        // Keep the real launcher icon in line with the saved choice (covers restores and reinstalls)
        viewModelScope.launch {
            settings.filter { it.loaded }.map { it.icon }.distinctUntilChanged().collect {
                runCatching { AppIcons.apply(getApplication(), it) }
            }
        }
    }

    fun refresh() = viewModelScope.launch { hollow.library.refresh(settings.value.folders) }

    fun addFolder(uri: Uri) = viewModelScope.launch {
        hollow.library.takePermission(uri)
        hollow.settings.addFolder(Folder(uri.toString(), hollow.library.folderName(uri)))
    }

    fun removeFolder(uri: String) = viewModelScope.launch {
        hollow.library.releasePermission(uri)
        hollow.settings.removeFolder(uri)
    }

    fun setActiveFolder(uri: String) = viewModelScope.launch { hollow.settings.setActiveFolder(uri) }
    fun setView(favouritesOnly: Boolean, sort: SortOrder) =
        viewModelScope.launch { hollow.settings.setView(favouritesOnly, sort) }
    fun setColumns(n: Int) = viewModelScope.launch { hollow.settings.setColumns(n) }
    fun setEnd(e: EndBehavior) = viewModelScope.launch { hollow.settings.setEnd(e) }
    fun setSpeed(s: Float) = viewModelScope.launch { hollow.settings.setSpeed(s) }
    fun setTransition(t: TransitionSpeed) = viewModelScope.launch { hollow.settings.setTransition(t) }
    fun setAccent(c: Long) = viewModelScope.launch { hollow.settings.setAccent(c) }
    fun setBrightnessGesture(on: Boolean) = viewModelScope.launch { hollow.settings.setBrightnessGesture(on) }
    fun setVolumeGesture(on: Boolean) = viewModelScope.launch { hollow.settings.setVolumeGesture(on) }
    fun setIcon(id: String) = viewModelScope.launch { hollow.settings.setIcon(id) }
    fun setLock(on: Boolean) = viewModelScope.launch { hollow.settings.setLock(on) }
    fun setInfoStyle(st: InfoStyle) = viewModelScope.launch { hollow.settings.setInfoStyle(st) }
    fun setPlayerInfo(on: Boolean) = viewModelScope.launch { hollow.settings.setPlayerInfo(on) }
    fun toggleFavourite(id: String) = viewModelScope.launch { hollow.userData.toggleFavourite(id) }
    fun addWatch(id: String) = viewModelScope.launch { hollow.userData.addWatch(id) }

    fun setThumbOverride(id: String, ms: Long) = thumbStore.setOverride(id, ms)
    fun resetThumbOverride(id: String) = thumbStore.clearOverride(id)
    fun applyGlobalThumb(mode: ThumbMode, seconds: Int, keep: Boolean) {
        thumbStore.applyGlobal(mode, seconds, keep)
        thumbs.clearCovers()
    }
}

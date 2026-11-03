package com.localshorts.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.localshorts.app.data.EndBehavior
import com.localshorts.app.data.Folder
import com.localshorts.app.data.GalleryTab
import com.localshorts.app.data.Settings
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

    val settings: StateFlow<Settings> =
        hollow.settings.flow.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    val favourites: StateFlow<Set<String>> =
        hollow.userData.favourites.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val watchCounts: StateFlow<Map<String, Int>> =
        hollow.userData.watchCounts.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    val scanning: StateFlow<Boolean> = hollow.library.scanning
    val unavailable: StateFlow<Set<String>> = hollow.library.unavailable

    val query = MutableStateFlow("")

    /** The gallery list for the current tab, folder and search. Tabs re-sort; they never rescan. */
    val gallery: StateFlow<List<VideoItem>> = combine(
        hollow.library.videos, settings, favourites, query,
    ) { videos, s, favs, q ->
        val needle = q.trim().lowercase(Locale.ROOT)
        val base = when {
            needle.isNotEmpty() -> videos.filter { it.name.lowercase(Locale.ROOT).contains(needle) }
            s.activeFolder.isEmpty() -> videos
            else -> videos.filter { it.folder == s.activeFolder }
        }
        when (s.tab) {
            GalleryTab.LATEST -> base.sortedByDescending { it.modified }
            GalleryTab.OLDEST -> base.sortedBy { it.modified }
            GalleryTab.FAVOURITES -> base.filter { it.id in favs }.sortedByDescending { it.modified }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            settings.filter { it.loaded }.map { it.folders }.distinctUntilChanged().collect {
                hollow.library.refresh(it)
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
    fun setTab(t: GalleryTab) = viewModelScope.launch { hollow.settings.setTab(t) }
    fun setColumns(n: Int) = viewModelScope.launch { hollow.settings.setColumns(n) }
    fun setEnd(e: EndBehavior) = viewModelScope.launch { hollow.settings.setEnd(e) }
    fun setSpeed(s: Float) = viewModelScope.launch { hollow.settings.setSpeed(s) }
    fun setAccent(c: Long) = viewModelScope.launch { hollow.settings.setAccent(c) }
    fun toggleFavourite(id: String) = viewModelScope.launch { hollow.userData.toggleFavourite(id) }
    fun addWatch(id: String) = viewModelScope.launch { hollow.userData.addWatch(id) }
}

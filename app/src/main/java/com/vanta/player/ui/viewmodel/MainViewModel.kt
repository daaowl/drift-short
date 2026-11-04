package com.vanta.player.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vanta.player.VantaApplication
import com.vanta.player.data.VideoUserStats
import com.vanta.player.model.AppSettings
import com.vanta.player.model.CollectionTab
import com.vanta.player.model.EndBehavior
import com.vanta.player.model.FolderEntry
import com.vanta.player.model.LauncherIconOption
import com.vanta.player.model.OpeningTransitionState
import com.vanta.player.model.ShortTransitionSpeed
import com.vanta.player.model.SortOption
import com.vanta.player.model.TransitionPhase
import com.vanta.player.model.VideoItem
import com.vanta.player.player.PlaybackCoordinator
import com.vanta.player.player.PlaybackUiState
import com.vanta.player.util.LauncherIconManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToLong

data class BrowsingScrollPosition(
    val firstVisibleIndex: Int = 0,
    val firstVisibleOffset: Int = 0,
    val anchorVideoId: String? = null,
    val isHeaderCollapsed: Boolean = false
)

data class VantaUiState(
    val settings: AppSettings = AppSettings(),
    val isScanning: Boolean = false,
    val folderErrorMessage: String? = null,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val displayedVideos: List<VideoItem> = emptyList(),
    val totalFolderVideoCount: Int = 0,
    val favouritesCount: Int = 0,
    val isSettingsOpen: Boolean = false,
    val isFolderSwitcherOpen: Boolean = false,
    val customThumbnailEditorVideo: VideoItem? = null,
    val transitionState: OpeningTransitionState = OpeningTransitionState(),
    val playbackState: PlaybackUiState = PlaybackUiState(),
    val isAppLocked: Boolean = false,
    val toastMessage: String? = null
) {
    val activeBrowsingKey: String
        get() = "${settings.activeFolderUri.orEmpty()}_${settings.selectedTab.name}_${settings.sortOption.name}_${searchQuery.trim().lowercase()}"
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as VantaApplication
    private val preferencesStore = app.preferencesStore
    private val scanner = app.libraryScanner
    val thumbnailCache = app.thumbnailCacheManager

    val playbackCoordinator = PlaybackCoordinator(
        context = app.applicationContext,
        scope = viewModelScope,
        onWatchCountQualified = { stableVideoId ->
            viewModelScope.launch {
                preferencesStore.incrementWatchCount(stableVideoId)
            }
        }
    )

    private val rawScannedVideos = MutableStateFlow<List<VideoItem>>(emptyList())
    private val isScanningFlow = MutableStateFlow(false)
    private val folderErrorFlow = MutableStateFlow<String?>(null)
    private val searchQueryFlow = MutableStateFlow("")
    private val isSearchActiveFlow = MutableStateFlow(false)
    private val isSettingsOpenFlow = MutableStateFlow(false)
    private val isFolderSwitcherOpenFlow = MutableStateFlow(false)
    private val customThumbEditorVideoFlow = MutableStateFlow<VideoItem?>(null)
    private val isAppLockedFlow = MutableStateFlow(false)
    private val toastMessageFlow = MutableStateFlow<String?>(null)

    private val _transitionState = MutableStateFlow(OpeningTransitionState())
    val transitionState: StateFlow<OpeningTransitionState> = _transitionState.asStateFlow()

    private val thumbnailBoundsRegistry = ConcurrentHashMap<String, Rect>()

    // Section 2: Track browsing positions separately by folder/filter/search/sort during the session
    private val sessionScrollPositions = ConcurrentHashMap<String, BrowsingScrollPosition>()

    private var activeScanJob: Job? = null

    private data class CoreInputs(
        val settings: AppSettings,
        val userStats: VideoUserStats,
        val rawVideos: List<VideoItem>,
        val searchQuery: String,
        val isSearchActive: Boolean,
        val isScanning: Boolean,
        val folderError: String?
    )

    private val coreInputsFlow = combine(
        preferencesStore.settingsFlow,
        preferencesStore.userStatsFlow,
        rawScannedVideos,
        combine(searchQueryFlow, isSearchActiveFlow) { q, active -> q to active },
        combine(isScanningFlow, folderErrorFlow) { s, err -> s to err }
    ) { settings, stats, rawVideos, (query, searchActive), (scanning, err) ->
        CoreInputs(
            settings = settings,
            userStats = stats,
            rawVideos = rawVideos,
            searchQuery = query,
            isSearchActive = searchActive,
            isScanning = scanning,
            folderError = err
        )
    }

    private data class OverlayInputs(
        val isSettingsOpen: Boolean,
        val isFolderSwitcherOpen: Boolean,
        val customEditorVideo: VideoItem?,
        val isAppLocked: Boolean,
        val toastMessage: String?
    )

    private val overlayInputsFlow = combine(
        isSettingsOpenFlow,
        isFolderSwitcherOpenFlow,
        customThumbEditorVideoFlow,
        isAppLockedFlow,
        toastMessageFlow
    ) { settingsOpen, folderOpen, editorVid, locked, toast ->
        OverlayInputs(settingsOpen, folderOpen, editorVid, locked, toast)
    }

    val uiState: StateFlow<VantaUiState> = combine(
        coreInputsFlow,
        overlayInputsFlow,
        _transitionState,
        playbackCoordinator.state
    ) { core, overlays, transition, playback ->
        val defaultThumbMs = (core.settings.defaultThumbnailSeconds * 1000f).roundToLong()

        val enrichedVideos = core.rawVideos.map { video ->
            val customMs = core.userStats.customThumbnailTimestampsMs[video.stableId]
            val rawEffectiveMs = customMs ?: defaultThumbMs
            val clampedEffectiveMs = thumbnailCache.clampTimestampToDuration(
                rawEffectiveMs,
                video.durationMs
            )
            video.copy(
                isFavourite = core.userStats.favouriteIds.contains(video.stableId),
                watchCount = core.userStats.watchCounts[video.stableId] ?: 0,
                customThumbnailMs = customMs,
                effectiveThumbnailMs = clampedEffectiveMs
            )
        }

        val byId = enrichedVideos.associateBy { it.stableId }
        playbackCoordinator.updatePlaylistMetadata(byId)

        val tabFiltered = when (core.settings.selectedTab) {
            CollectionTab.FAVOURITES -> enrichedVideos.filter { it.isFavourite }
            CollectionTab.LATEST, CollectionTab.OLDEST -> enrichedVideos
        }

        val queryTrimmed = core.searchQuery.trim()
        val searched = if (queryTrimmed.isEmpty()) {
            tabFiltered
        } else {
            tabFiltered.filter { it.fileName.contains(queryTrimmed, ignoreCase = true) }
        }

        // Section 2: Dates use file last-modified metadata. Put unknown dates (<= 0) last in date sorts.
        // Most viewed sorts descending by local watch count, breaking ties by newest first, then deterministic stableId.
        val sorted = when (core.settings.sortOption) {
            SortOption.NEWEST_FIRST -> searched.sortedWith(
                compareBy<VideoItem> { if (it.lastModifiedMs <= 0L) 1 else 0 }
                    .thenByDescending { it.lastModifiedMs }
                    .thenBy { it.fileName.lowercase() }
                    .thenBy { it.stableId }
            )
            SortOption.OLDEST_FIRST -> searched.sortedWith(
                compareBy<VideoItem> { if (it.lastModifiedMs <= 0L) 1 else 0 }
                    .thenBy { it.lastModifiedMs }
                    .thenBy { it.fileName.lowercase() }
                    .thenBy { it.stableId }
            )
            SortOption.NAME_ASC -> searched.sortedWith(
                compareBy<VideoItem> { it.fileName.lowercase() }
                    .thenByDescending { it.lastModifiedMs }
                    .thenBy { it.stableId }
            )
            SortOption.NAME_DESC -> searched.sortedWith(
                compareByDescending<VideoItem> { it.fileName.lowercase() }
                    .thenByDescending { it.lastModifiedMs }
                    .thenBy { it.stableId }
            )
            SortOption.MOST_VIEWED -> searched.sortedWith(
                compareByDescending<VideoItem> { it.watchCount }
                    .thenBy { if (it.lastModifiedMs <= 0L) 1 else 0 }
                    .thenByDescending { it.lastModifiedMs }
                    .thenBy { it.fileName.lowercase() }
                    .thenBy { it.stableId }
            )
        }

        val verifiedFolders = core.settings.savedFolders.map { folder ->
            folder.copy(isAvailable = scanner.isFolderPermissionValid(folder.uri))
        }

        val currentTargetId = playback.currentVideo?.stableId ?: transition.targetVideo?.stableId
        val currentSlotSnap = playback.slotSnapshots[playback.currentSlotIndex]
        val isFirstFrameReady = currentSlotSnap?.boundVideoId == currentTargetId &&
            currentSlotSnap?.isFirstFrameRendered == true

        val updatedEditorVideo = overlays.customEditorVideo?.let { byId[it.stableId] ?: it }

        VantaUiState(
            settings = core.settings.copy(savedFolders = verifiedFolders),
            isScanning = core.isScanning,
            folderErrorMessage = core.folderError,
            searchQuery = core.searchQuery,
            isSearchActive = core.isSearchActive,
            displayedVideos = sorted,
            totalFolderVideoCount = enrichedVideos.size,
            favouritesCount = enrichedVideos.count { it.isFavourite },
            isSettingsOpen = overlays.isSettingsOpen,
            isFolderSwitcherOpen = overlays.isFolderSwitcherOpen,
            customThumbnailEditorVideo = updatedEditorVideo,
            transitionState = transition.copy(isFirstFrameReady = isFirstFrameReady),
            playbackState = playback,
            isAppLocked = overlays.isAppLocked,
            toastMessage = overlays.toastMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = VantaUiState()
    )

    init {
        viewModelScope.launch {
            preferencesStore.settingsFlow
                .map { it.activeFolderUri }
                .distinctUntilChanged()
                .collect { activeUriStr ->
                    if (activeUriStr.isNullOrBlank()) {
                        rawScannedVideos.value = emptyList()
                        folderErrorFlow.value = null
                    } else {
                        triggerFolderScan(Uri.parse(activeUriStr))
                    }
                }
        }
    }

    fun getBrowsingScrollPosition(key: String): BrowsingScrollPosition {
        return sessionScrollPositions[key] ?: BrowsingScrollPosition()
    }

    fun saveBrowsingScrollPosition(
        key: String,
        index: Int,
        offset: Int,
        anchorVideoId: String?,
        isHeaderCollapsed: Boolean
    ) {
        sessionScrollPositions[key] = BrowsingScrollPosition(
            firstVisibleIndex = index.coerceAtLeast(0),
            firstVisibleOffset = offset.coerceAtLeast(0),
            anchorVideoId = anchorVideoId,
            isHeaderCollapsed = isHeaderCollapsed
        )
    }

    fun onFolderPicked(treeUri: Uri) {
        viewModelScope.launch {
            runCatching {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                app.contentResolver.takePersistableUriPermission(treeUri, flags)
            }
            val displayName = scanner.resolveFolderDisplayName(treeUri)
            val displayPath = scanner.resolveFolderDisplayPath(treeUri)
            prepareForFolderSwitch()
            preferencesStore.addOrSelectFolder(treeUri.toString(), displayName, displayPath)
        }
    }

    fun selectSavedFolder(folder: FolderEntry) {
        viewModelScope.launch {
            prepareForFolderSwitch()
            if (!scanner.isFolderPermissionValid(folder.uri)) {
                folderErrorFlow.value = "Permission for \"${folder.displayName}\" was revoked or folder is missing."
                preferencesStore.setActiveFolder(folder.uriString)
                rawScannedVideos.value = emptyList()
            } else {
                folderErrorFlow.value = null
                preferencesStore.setActiveFolder(folder.uriString)
            }
        }
    }

    // Section 1: Switching folders stops playback, clears search, returns to the All (Latest) collection, and scans
    private fun prepareForFolderSwitch() {
        if (playbackCoordinator.state.value.isPlayerOpen) {
            playbackCoordinator.closePlayer()
            _transitionState.value = OpeningTransitionState(phase = TransitionPhase.IDLE)
        }
        searchQueryFlow.value = ""
        isSearchActiveFlow.value = false
        isFolderSwitcherOpenFlow.value = false
    }

    fun removeSavedFolder(folder: FolderEntry) {
        viewModelScope.launch {
            if (uiState.value.settings.activeFolderUri == folder.uriString) {
                prepareForFolderSwitch()
            }
            preferencesStore.removeFolder(folder.uriString)
        }
    }

    fun refreshActiveFolder() {
        val activeUriStr = uiState.value.settings.activeFolderUri ?: return
        triggerFolderScan(Uri.parse(activeUriStr))
    }

    private fun triggerFolderScan(treeUri: Uri) {
        activeScanJob?.cancel()
        activeScanJob = viewModelScope.launch(Dispatchers.IO) {
            isScanningFlow.value = true
            folderErrorFlow.value = null
            val result = scanner.scanFolder(treeUri)
            // Section 1: Background scan results must not interrupt an open player
            rawScannedVideos.value = result.videos
            folderErrorFlow.value = result.errorMessage
            isScanningFlow.value = false
        }
    }

    fun registerThumbnailBounds(stableVideoId: String, boundsInWindow: Rect) {
        if (!boundsInWindow.isEmpty && boundsInWindow.width > 0f && boundsInWindow.height > 0f) {
            thumbnailBoundsRegistry[stableVideoId] = boundsInWindow
        }
    }

    fun onThumbnailTapped(video: VideoItem, tappedBounds: Rect?) {
        val currentList = uiState.value.displayedVideos
        val index = currentList.indexOfFirst { it.stableId == video.stableId }.coerceAtLeast(0)
        val startRect = tappedBounds ?: thumbnailBoundsRegistry[video.stableId]

        val currentSettings = uiState.value.settings
        playbackCoordinator.openPlaylistAt(
            playlist = currentList,
            startIndex = index,
            defaultSpeed = currentSettings.defaultPlaybackSpeed,
            endBehavior = currentSettings.endBehavior
        )

        _transitionState.value = OpeningTransitionState(
            phase = TransitionPhase.OPENING,
            targetVideo = video,
            startBounds = startRect,
            isFirstFrameReady = false
        )
    }

    fun onOpeningGeometryAnimationFinished() {
        _transitionState.update { state ->
            if (state.phase == TransitionPhase.OPENING) {
                state.copy(phase = TransitionPhase.IN_PLAYER)
            } else {
                state
            }
        }
    }

    fun requestClosePlayer() {
        val playback = playbackCoordinator.state.value
        if (!playback.isPlayerOpen) return

        val activeVideo = playback.currentVideo
        val targetRect = activeVideo?.let { thumbnailBoundsRegistry[it.stableId] }

        playbackCoordinator.pauseForClosingOrBackground()

        if (activeVideo != null && targetRect != null) {
            _transitionState.value = OpeningTransitionState(
                phase = TransitionPhase.CLOSING,
                targetVideo = activeVideo,
                startBounds = targetRect,
                isFirstFrameReady = true
            )
        } else {
            finishClosePlayer()
        }
    }

    fun finishClosePlayer() {
        playbackCoordinator.closePlayer()
        _transitionState.value = OpeningTransitionState(phase = TransitionPhase.IDLE)
    }

    fun openSearch() {
        isSearchActiveFlow.value = true
    }

    fun closeSearch() {
        isSearchActiveFlow.value = false
        searchQueryFlow.value = ""
    }

    fun setSearchQuery(query: String) {
        searchQueryFlow.value = query
    }

    // Section 2: Latest selects newest order; Oldest selects oldest order; Favourites filters saved favourites
    fun onTabSelected(tab: CollectionTab) {
        val currentSort = uiState.value.settings.sortOption
        val targetSort = when (tab) {
            CollectionTab.LATEST -> SortOption.NEWEST_FIRST
            CollectionTab.OLDEST -> SortOption.OLDEST_FIRST
            CollectionTab.FAVOURITES -> currentSort
        }
        viewModelScope.launch {
            preferencesStore.setSelectedTabAndSort(tab, targetSort)
        }
    }

    fun onSortOptionSelected(sort: SortOption) {
        val currentTab = uiState.value.settings.selectedTab
        val updatedTab = when {
            currentTab == CollectionTab.FAVOURITES -> CollectionTab.FAVOURITES
            sort == SortOption.OLDEST_FIRST -> CollectionTab.OLDEST
            else -> CollectionTab.LATEST
        }
        viewModelScope.launch {
            preferencesStore.setSelectedTabAndSort(updatedTab, sort)
        }
    }

    fun setGridColumns(columns: Int) {
        viewModelScope.launch {
            preferencesStore.setGridColumns(columns)
        }
    }

    fun toggleFavourite(videoId: String) {
        viewModelScope.launch {
            preferencesStore.toggleFavourite(videoId)
        }
    }

    fun openCustomThumbnailEditor(video: VideoItem) {
        if (playbackCoordinator.state.value.isPlayerOpen) {
            playbackCoordinator.pauseExplicitly()
        }
        customThumbEditorVideoFlow.value = video
    }

    fun closeCustomThumbnailEditor() {
        customThumbEditorVideoFlow.value = null
    }

    fun saveCustomThumbnailTimestamp(video: VideoItem, timestampMs: Long?) {
        viewModelScope.launch {
            preferencesStore.setCustomThumbnailForVideo(video.stableId, timestampMs)
            customThumbEditorVideoFlow.value = null
            showToast(if (timestampMs != null) "Custom thumbnail saved" else "Restored default thumbnail")
        }
    }

    fun setKeepCustomFrames(keep: Boolean) {
        viewModelScope.launch {
            preferencesStore.setKeepCustomFrames(keep)
        }
    }

    fun applyGlobalDefaultThumbnailSeconds(seconds: Float, clearCustomOverrides: Boolean) {
        viewModelScope.launch {
            preferencesStore.applyGlobalDefaultThumbnailSeconds(seconds, clearCustomOverrides)
            showToast("Default thumbnail frame updated to ${seconds}s")
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        val ok = playbackCoordinator.setPlaybackSpeed(speed)
        if (!ok) {
            showToast("Speed ${speed}× is not supported by this video codec")
        }
        viewModelScope.launch {
            preferencesStore.setDefaultPlaybackSpeed(speed)
        }
    }

    fun setEndBehavior(behavior: EndBehavior) {
        playbackCoordinator.setEndBehavior(behavior)
        viewModelScope.launch {
            preferencesStore.setEndBehavior(behavior)
        }
    }

    fun setShortTransitionSpeed(speed: ShortTransitionSpeed) {
        viewModelScope.launch {
            preferencesStore.setShortTransitionSpeed(speed)
        }
    }

    fun setAccentColorArgb(argb: Int) {
        viewModelScope.launch {
            preferencesStore.setAccentColorArgb(argb)
        }
    }

    fun setLauncherIcon(option: LauncherIconOption) {
        viewModelScope.launch {
            preferencesStore.setLauncherIcon(option)
            LauncherIconManager.applyLauncherIcon(app.applicationContext, option)
            showToast("Launcher icon changed to ${option.title} (launcher may take a moment to refresh)")
        }
    }

    fun setGestureBrightnessEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesStore.setGestureBrightness(enabled)
        }
    }

    fun setGestureVolumeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesStore.setGestureVolume(enabled)
        }
    }

    fun setRequirePhoneUnlock(enabled: Boolean) {
        viewModelScope.launch {
            preferencesStore.setRequirePhoneUnlock(enabled)
        }
    }

    fun setAppLocked(locked: Boolean) {
        isAppLockedFlow.value = locked
        if (locked) {
            playbackCoordinator.pauseForClosingOrBackground()
        }
    }

    fun openFolderSwitcher() {
        isFolderSwitcherOpenFlow.value = true
    }

    fun closeFolderSwitcher() {
        isFolderSwitcherOpenFlow.value = false
    }

    fun openSettings() {
        if (playbackCoordinator.state.value.isPlayerOpen) {
            playbackCoordinator.pauseForClosingOrBackground()
        }
        isSettingsOpenFlow.value = true
    }

    fun closeSettings() {
        isSettingsOpenFlow.value = false
        if (playbackCoordinator.state.value.isPlayerOpen) {
            playbackCoordinator.enforceDominantPlayback()
        }
    }

    fun showToast(message: String) {
        toastMessageFlow.value = message
    }

    fun clearToast() {
        toastMessageFlow.value = null
    }

    override fun onCleared() {
        super.onCleared()
        playbackCoordinator.release()
    }
}

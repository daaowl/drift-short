package com.localshorts.app.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.localshorts.app.HollowApplication
import com.localshorts.app.data.VideoUserStats
import com.localshorts.app.model.AccentPreset
import com.localshorts.app.model.AppSettings
import com.localshorts.app.model.EndBehavior
import com.localshorts.app.model.FolderEntry
import com.localshorts.app.model.GradientPreset
import com.localshorts.app.model.IconStylePreset
import com.localshorts.app.model.MiddleGestureAction
import com.localshorts.app.model.OpeningTransitionState
import com.localshorts.app.model.SortTab
import com.localshorts.app.model.SwipeTransitionSpeed
import com.localshorts.app.model.TransitionPhase
import com.localshorts.app.model.VideoItem
import com.localshorts.app.player.PlaybackCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/** Library/gallery state only. Playback ticks never touch this flow, so the grid never recomposes for them. */
data class HollowUiState(
    val settings: AppSettings = AppSettings(),
    val isScanning: Boolean = false,
    val folderErrorMessage: String? = null,
    val searchQuery: String = "",
    val displayedVideos: List<VideoItem> = emptyList(),
    val totalFolderVideoCount: Int = 0,
    val favouritesCount: Int = 0,
    val customThumbnailCount: Int = 0
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as HollowApplication
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

    private val _isSettingsOpen = MutableStateFlow(false)
    val isSettingsOpen: StateFlow<Boolean> = _isSettingsOpen.asStateFlow()

    private val _transitionState = MutableStateFlow(OpeningTransitionState())

    // Live screen bounds (root window coordinates) of the thumbnails currently composed in the grid.
    private val thumbnailBoundsRegistry = ConcurrentHashMap<String, Rect>()

    private var activeScanJob: Job? = null

    private data class LibraryInputs(
        val settings: AppSettings,
        val userStats: VideoUserStats,
        val rawVideos: List<VideoItem>,
        val searchQuery: String,
        val isScanning: Boolean,
        val folderError: String?
    )

    private val libraryInputsFlow = combine(
        preferencesStore.settingsFlow,
        preferencesStore.userStatsFlow,
        rawScannedVideos,
        searchQueryFlow,
        combine(isScanningFlow, folderErrorFlow) { scanning, err -> scanning to err }
    ) { settings, stats, rawVideos, query, scanState ->
        LibraryInputs(
            settings = settings,
            userStats = stats,
            rawVideos = rawVideos,
            searchQuery = query,
            isScanning = scanState.first,
            folderError = scanState.second
        )
    }

    val uiState: StateFlow<HollowUiState> = libraryInputsFlow
        .map { inputs ->
            val enrichedVideos = inputs.rawVideos.map { video ->
                video.copy(
                    isFavourite = inputs.userStats.favouriteIds.contains(video.stableId),
                    watchCount = inputs.userStats.watchCounts[video.stableId] ?: 0,
                    thumbnailTimeMs = effectiveThumbnailTime(
                        override = inputs.userStats.thumbnailTimes[video.stableId],
                        fixedSeconds = inputs.settings.thumbnailFixedSeconds,
                        durationMs = video.durationMs
                    ),
                    hasCustomThumbnail = inputs.userStats.thumbnailTimes.containsKey(video.stableId)
                )
            }

            // Keep an open player's favourite / watch metadata fresh without touching any player.
            playbackCoordinator.updatePlaylistMetadata(enrichedVideos.associateBy { it.stableId })

            val queryTrimmed = inputs.searchQuery.trim()
            val searched = if (queryTrimmed.isEmpty()) {
                enrichedVideos
            } else {
                enrichedVideos.filter { it.fileName.contains(queryTrimmed, ignoreCase = true) }
            }

            val sorted = when (inputs.settings.selectedTab) {
                SortTab.LATEST -> searched.sortedWith(
                    compareByDescending<VideoItem> { it.lastModifiedMs }
                        .thenBy { it.fileName.lowercase() }
                )
                SortTab.OLDEST -> searched.sortedWith(
                    compareBy<VideoItem> { it.lastModifiedMs }
                        .thenBy { it.fileName.lowercase() }
                )
                SortTab.FAVOURITES -> searched
                    .filter { it.isFavourite }
                    .sortedWith(
                        compareByDescending<VideoItem> { it.lastModifiedMs }
                            .thenBy { it.fileName.lowercase() }
                    )
            }

            val verifiedFolders = inputs.settings.savedFolders.map { folder ->
                folder.copy(isAvailable = scanner.isFolderPermissionValid(folder.uri))
            }

            HollowUiState(
                settings = inputs.settings.copy(savedFolders = verifiedFolders),
                isScanning = inputs.isScanning,
                folderErrorMessage = inputs.folderError,
                searchQuery = inputs.searchQuery,
                displayedVideos = sorted,
                totalFolderVideoCount = enrichedVideos.size,
                favouritesCount = enrichedVideos.count { it.isFavourite },
                customThumbnailCount = enrichedVideos.count { it.hasCustomThumbnail }
            )
        }
        // Sorting, filtering and the SAF permission checks must never run on the main thread.
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HollowUiState()
        )

    /**
     * Transition overlay state with the "first frame rendered for the target video" flag folded in.
     * Light-weight: no library work here.
     */
    val transitionUiState: StateFlow<OpeningTransitionState> = combine(
        _transitionState,
        playbackCoordinator.state
    ) { transition, playback ->
        val targetId = transition.targetVideo?.stableId
        val snapshot = playback.slotSnapshots[playback.currentSlotIndex]
        val ready = targetId != null &&
            snapshot != null &&
            snapshot.boundVideoId == targetId &&
            snapshot.isFirstFrameRendered
        transition.copy(isFirstFrameReady = ready)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = OpeningTransitionState()
    )

    val playbackState = playbackCoordinator.state

    init {
        // Rescan whenever the active folder changes.
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

    fun onFolderPicked(treeUri: Uri) {
        viewModelScope.launch {
            runCatching {
                app.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val displayName = scanner.resolveFolderDisplayName(treeUri)
            preferencesStore.addOrSelectFolder(treeUri.toString(), displayName)
        }
    }

    fun selectSavedFolder(folder: FolderEntry) {
        viewModelScope.launch {
            if (!scanner.isFolderPermissionValid(folder.uri)) {
                folderErrorFlow.value =
                    "Access to \"${folder.displayName}\" is no longer available. Please re-add the folder."
                preferencesStore.setActiveFolder(folder.uriString)
                rawScannedVideos.value = emptyList()
            } else {
                folderErrorFlow.value = null
                preferencesStore.setActiveFolder(folder.uriString)
                // Same folder tapped again: the active-folder flow will not re-emit, so rescan.
                if (uiState.value.settings.activeFolderUri == folder.uriString) {
                    triggerFolderScan(folder.uri)
                }
            }
        }
    }

    fun removeSavedFolder(folder: FolderEntry) {
        viewModelScope.launch {
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
            rawScannedVideos.value = result.videos
            folderErrorFlow.value = result.errorMessage
            isScanningFlow.value = false

            // Pre-warm thumbnails for the first screenful, off the main thread.
            result.videos.take(18).forEach { video ->
                launch { thumbnailCache.getOrDecodeThumbnail(video) }
            }
        }
    }

    fun registerThumbnailBounds(stableVideoId: String, boundsInWindow: Rect) {
        if (!boundsInWindow.isEmpty && boundsInWindow.width > 0f && boundsInWindow.height > 0f) {
            thumbnailBoundsRegistry[stableVideoId] = boundsInWindow
        }
    }

    fun unregisterThumbnailBounds(stableVideoId: String) {
        thumbnailBoundsRegistry.remove(stableVideoId)
    }

    fun onThumbnailTapped(video: VideoItem, tappedBounds: Rect?) {
        if (_transitionState.value.phase != TransitionPhase.IDLE) return
        if (playbackCoordinator.state.value.isPlayerOpen) return

        val currentList = uiState.value.displayedVideos
        val index = currentList.indexOfFirst { it.stableId == video.stableId }
        if (index < 0) return
        val startRect = tappedBounds ?: thumbnailBoundsRegistry[video.stableId]

        viewModelScope.launch {
            preferencesStore.setLastSelectedVideoId(video.stableId)
        }

        // Player preparation starts immediately and independently of the opening animation.
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
            startBounds = startRect
        )
    }

    fun onOpeningGeometryFinished() {
        _transitionState.update { state ->
            if (state.phase == TransitionPhase.OPENING) state.copy(phase = TransitionPhase.IN_PLAYER) else state
        }
    }

    /** First frame is on screen (or the guard expired): the cover goes away for good. */
    fun onCoverDismissed() {
        _transitionState.update { state ->
            if (state.phase == TransitionPhase.IN_PLAYER) OpeningTransitionState() else state
        }
    }

    fun requestClosePlayer() {
        val playback = playbackCoordinator.state.value
        if (!playback.isPlayerOpen) return
        if (_transitionState.value.phase == TransitionPhase.CLOSING) return

        val activeVideo = playback.currentVideo
        val targetRect = activeVideo?.let { thumbnailBoundsRegistry[it.stableId] }
        val frame: Bitmap? = playbackCoordinator.captureCurrentFrame()

        // Pause audio immediately; the last rendered frame stays visible during the collapse.
        playbackCoordinator.pauseForClosingOrBackground()

        if (activeVideo != null) {
            _transitionState.value = OpeningTransitionState(
                phase = TransitionPhase.CLOSING,
                targetVideo = activeVideo,
                startBounds = targetRect,
                frameBitmap = frame
            )
        } else {
            finishClosePlayer()
        }
    }

    fun finishClosePlayer() {
        playbackCoordinator.closePlayer()
        _transitionState.value = OpeningTransitionState()
    }

    fun setSearchQuery(query: String) {
        searchQueryFlow.value = query
    }

    fun setSelectedTab(tab: SortTab) {
        viewModelScope.launch { preferencesStore.setSelectedTab(tab) }
    }

    fun setGridColumns(columns: Int) {
        viewModelScope.launch { preferencesStore.setGridColumns(columns) }
    }

    fun saveGalleryScrollState(firstVisibleIndex: Int, firstVisibleOffset: Int) {
        viewModelScope.launch { preferencesStore.saveScrollPosition(firstVisibleIndex, firstVisibleOffset) }
    }

    fun toggleFavourite(videoId: String) {
        viewModelScope.launch { preferencesStore.toggleFavourite(videoId) }
    }

    /** Uses the frame currently on screen as this video's gallery thumbnail. */
    fun setThumbnailFromCurrentFrame(videoId: String) {
        val positionMs = playbackCoordinator.progress.value.positionMs
        viewModelScope.launch { preferencesStore.setThumbnailTime(videoId, positionMs) }
    }

    fun setThumbnailFixedSeconds(seconds: Int?) {
        viewModelScope.launch { preferencesStore.setThumbnailFixedSeconds(seconds) }
    }

    fun clearAllThumbnailOverrides() {
        viewModelScope.launch { preferencesStore.clearAllThumbnailOverrides() }
    }

    private fun effectiveThumbnailTime(override: Long?, fixedSeconds: Int?, durationMs: Long): Long? {
        if (override != null) return override
        if (fixedSeconds == null) return null
        val fixedMs = fixedSeconds * 1000L
        // Clips shorter than the global time use their midpoint instead of a blank end frame.
        return if (durationMs in 1L..fixedMs) durationMs / 2 else fixedMs
    }

    fun resetThumbnail(videoId: String) {
        viewModelScope.launch { preferencesStore.setThumbnailTime(videoId, null) }
    }

    fun setPlaybackSpeed(speed: Float) {
        playbackCoordinator.setPlaybackSpeed(speed)
        viewModelScope.launch { preferencesStore.setDefaultPlaybackSpeed(speed) }
    }

    fun setEndBehavior(behavior: EndBehavior) {
        playbackCoordinator.setEndBehavior(behavior)
        viewModelScope.launch { preferencesStore.setEndBehavior(behavior) }
    }

    fun setSwipeTransitionSpeed(speed: SwipeTransitionSpeed) {
        viewModelScope.launch { preferencesStore.setSwipeTransitionSpeed(speed) }
    }

    fun setAccentPreset(preset: AccentPreset) {
        viewModelScope.launch { preferencesStore.setAccentPreset(preset) }
    }

    fun setGradientPreset(preset: GradientPreset) {
        viewModelScope.launch { preferencesStore.setGradientPreset(preset) }
    }

    fun setIconStylePreset(preset: IconStylePreset) {
        viewModelScope.launch { preferencesStore.setIconStylePreset(preset) }
    }

    fun setGestureBrightnessEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesStore.setGestureBrightness(enabled) }
    }

    fun setGestureVolumeEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesStore.setGestureVolume(enabled) }
    }

    fun setMiddleGestureAction(action: MiddleGestureAction) {
        viewModelScope.launch { preferencesStore.setMiddleGestureAction(action) }
    }

    fun toggleVideoInfoOverlay() {
        val next = !uiState.value.settings.showVideoInfoOverlay
        viewModelScope.launch { preferencesStore.setShowVideoInfoOverlay(next) }
    }

    fun openSettings() {
        if (playbackCoordinator.state.value.isPlayerOpen) {
            playbackCoordinator.pauseForClosingOrBackground()
        }
        _isSettingsOpen.value = true
    }

    fun closeSettings() {
        _isSettingsOpen.value = false
        if (playbackCoordinator.state.value.isPlayerOpen) {
            playbackCoordinator.enforceDominantPlayback()
        }
    }

    override fun onCleared() {
        super.onCleared()
        playbackCoordinator.release()
    }
}

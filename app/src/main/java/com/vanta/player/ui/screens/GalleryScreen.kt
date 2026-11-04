package com.vanta.player.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.vanta.player.data.ThumbnailCacheManager
import com.vanta.player.model.CollectionTab
import com.vanta.player.model.FolderEntry
import com.vanta.player.model.SortOption
import com.vanta.player.model.VideoItem
import com.vanta.player.ui.theme.LocalVantaColors
import com.vanta.player.ui.viewmodel.BrowsingScrollPosition
import com.vanta.player.ui.viewmodel.VantaUiState
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun GalleryScreen(
    uiState: VantaUiState,
    thumbnailCache: ThumbnailCacheManager,
    savedScrollPosition: BrowsingScrollPosition,
    onSaveScrollPosition: (index: Int, offset: Int, anchorVideoId: String?, headerCollapsed: Boolean) -> Unit,
    onAddFolderClick: () -> Unit,
    onSelectFolder: (FolderEntry) -> Unit,
    onOpenFolderSwitcher: () -> Unit,
    onCloseFolderSwitcher: () -> Unit,
    onRefreshFolder: () -> Unit,
    onSelectTab: (CollectionTab) -> Unit,
    onSelectSortOption: (SortOption) -> Unit,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onVideoTapped: (VideoItem, Rect?) -> Unit,
    onToggleFavourite: (String) -> Unit,
    onOpenCustomThumbnailEditor: (VideoItem) -> Unit,
    onRegisterThumbnailBounds: (String, Rect) -> Unit,
    modifier: Modifier = Modifier
) {
    val vantaColors = LocalVantaColors.current
    val settings = uiState.settings

    // Section 2: Device Back closes search when applicable
    BackHandler(enabled = uiState.isSearchActive) {
        onCloseSearch()
    }

    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = savedScrollPosition.firstVisibleIndex,
        initialFirstVisibleItemScrollOffset = savedScrollPosition.firstVisibleOffset
    )

    var isHeaderCollapsed by remember(uiState.activeBrowsingKey) {
        mutableStateOf(savedScrollPosition.isHeaderCollapsed)
    }
    var previousIndex by remember(uiState.activeBrowsingKey) {
        mutableIntStateOf(savedScrollPosition.firstVisibleIndex)
    }
    var previousOffset by remember(uiState.activeBrowsingKey) {
        mutableIntStateOf(savedScrollPosition.firstVisibleOffset)
    }
    var lastColumns by remember { mutableIntStateOf(settings.gridColumns) }

    // Restore browsing position when switching tabs/sorts/folders
    LaunchedEffect(uiState.activeBrowsingKey) {
        gridState.scrollToItem(
            savedScrollPosition.firstVisibleIndex,
            savedScrollPosition.firstVisibleOffset
        )
    }

    // Section 2: When column count changes, preserve the visible video as the scroll anchor
    LaunchedEffect(settings.gridColumns) {
        if (lastColumns != settings.gridColumns) {
            lastColumns = settings.gridColumns
            val anchorId = savedScrollPosition.anchorVideoId
            if (anchorId != null) {
                val anchorIdx = uiState.displayedVideos.indexOfFirst { it.stableId == anchorId }
                if (anchorIdx >= 0) {
                    gridState.scrollToItem(anchorIdx, 0)
                }
            }
        }
    }

    // Track scroll direction to collapse/expand entire header and persist session position
    LaunchedEffect(gridState, uiState.activeBrowsingKey, uiState.isSearchActive) {
        snapshotFlow {
            gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset
        }
            .distinctUntilChanged()
            .collect { (index, offset) ->
                if (uiState.isSearchActive || (index == 0 && offset < 20)) {
                    isHeaderCollapsed = false
                } else {
                    val scrollingDown = index > previousIndex ||
                        (index == previousIndex && offset > previousOffset + 28)
                    val scrollingUp = index < previousIndex ||
                        (index == previousIndex && offset < previousOffset - 28)
                    if (scrollingDown && index >= 1) {
                        isHeaderCollapsed = true
                    } else if (scrollingUp) {
                        isHeaderCollapsed = false
                    }
                }
                previousIndex = index
                previousOffset = offset
                val anchorId = uiState.displayedVideos.getOrNull(index)?.stableId
                onSaveScrollPosition(index, offset, anchorId, isHeaderCollapsed)
            }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(vantaColors.background)
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Section 2: Entire header, including branding and tabs, collapses when scrolling upward
            // through the feed and returns when scrolling back or reaching the top. Keep open while searching.
            AnimatedVisibility(visible = !isHeaderCollapsed || uiState.isSearchActive) {
                VantaCompactHeaderAndTabs(
                    uiState = uiState,
                    onOpenSearch = onOpenSearch,
                    onCloseSearch = onCloseSearch,
                    onSearchQueryChange = onSearchQueryChange,
                    onOpenFolderSwitcher = onOpenFolderSwitcher,
                    onRefreshFolder = onRefreshFolder,
                    onOpenSettings = onOpenSettings,
                    onSelectTab = onSelectTab,
                    onSelectSortOption = onSelectSortOption
                )
            }

            // Main Grid / Empty States
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when {
                    settings.savedFolders.isEmpty() -> {
                        FirstUseOnboardingView(onAddFolderClick = onAddFolderClick)
                    }
                    uiState.folderErrorMessage != null -> {
                        PermissionFailureView(
                            message = uiState.folderErrorMessage,
                            onAddFolderClick = onAddFolderClick,
                            onRefreshClick = onRefreshFolder
                        )
                    }
                    uiState.isScanning && uiState.displayedVideos.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = vantaColors.accent,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                    uiState.displayedVideos.isEmpty() -> {
                        EmptyCollectionMessageView(
                            selectedTab = settings.selectedTab,
                            searchQuery = uiState.searchQuery,
                            onRefresh = onRefreshFolder
                        )
                    }
                    else -> {
                        // Section 3: Seamless portrait grid inspired by YouTube Shorts' arrangement,
                        // with 9:16 tiles, centre-cropped images, and narrow ~2dp gutters.
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(settings.gridColumns.coerceIn(1, 4)),
                            state = gridState,
                            contentPadding = PaddingValues(2.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(
                                items = uiState.displayedVideos,
                                key = { it.stableId },
                                contentType = { "short_tile" }
                            ) { video ->
                                ShortGridTile(
                                    video = video,
                                    thumbnailCache = thumbnailCache,
                                    onBoundsChanged = { rect ->
                                        onRegisterThumbnailBounds(video.stableId, rect)
                                    },
                                    onClick = { rect ->
                                        onVideoTapped(video, rect)
                                    },
                                    onToggleFavourite = {
                                        onToggleFavourite(video.stableId)
                                    },
                                    onChangeThumbnail = {
                                        onOpenCustomThumbnailEditor(video)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Folder Switcher Dialog accessible from the main header
        if (uiState.isFolderSwitcherOpen) {
            FolderSwitcherDialog(
                folders = settings.savedFolders,
                activeFolderUri = settings.activeFolderUri,
                onSelectFolder = onSelectFolder,
                onAddAnotherFolder = {
                    onCloseFolderSwitcher()
                    onAddFolderClick()
                },
                onDismiss = onCloseFolderSwitcher
            )
        }
    }
}

@Composable
private fun VantaCompactHeaderAndTabs(
    uiState: VantaUiState,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onOpenFolderSwitcher: () -> Unit,
    onRefreshFolder: () -> Unit,
    onOpenSettings: () -> Unit,
    onSelectTab: (CollectionTab) -> Unit,
    onSelectSortOption: (SortOption) -> Unit
) {
    val vantaColors = LocalVantaColors.current
    val settings = uiState.settings
    var isSortMenuExpanded by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(vantaColors.background)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        if (uiState.isSearchActive) {
            // Integrated Search Header with Back/Close and Clear-text actions
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(vantaColors.surfaceCharcoal)
                    .padding(horizontal = 6.dp)
            ) {
                IconButton(
                    onClick = {
                        keyboardController?.hide()
                        onCloseSearch()
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Close search",
                        tint = vantaColors.textPrimary,
                        modifier = Modifier.size(19.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (uiState.searchQuery.isEmpty()) {
                        Text(
                            text = "Search videos…",
                            color = vantaColors.textSecondary,
                            fontSize = 14.sp
                        )
                    }
                    BasicTextField(
                        value = uiState.searchQuery,
                        onValueChange = onSearchQueryChange,
                        singleLine = true,
                        textStyle = TextStyle(
                            color = vantaColors.textPrimary,
                            fontSize = 14.sp
                        ),
                        cursorBrush = SolidColor(vantaColors.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                keyboardController?.hide()
                                focusManager.clearFocus()
                            }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (uiState.searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { onSearchQueryChange("") },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Clear search text",
                            tint = vantaColors.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        } else {
            // Compact integrated header: Original Vanta mark/name, Search, Folder, Sort, Settings
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
            ) {
                // Original Vanta Mark + Name
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(vantaColors.accent.copy(alpha = 0.18f))
                            .border(1.dp, vantaColors.accent, RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = vantaColors.accent,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Vanta",
                        color = vantaColors.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = onOpenSearch,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Search videos",
                            tint = vantaColors.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = onOpenFolderSwitcher,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FolderOpen,
                            contentDescription = "Switch folder",
                            tint = vantaColors.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { isSortMenuExpanded = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Sort,
                                contentDescription = "Sort videos",
                                tint = vantaColors.textPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = isSortMenuExpanded,
                            onDismissRequest = { isSortMenuExpanded = false },
                            modifier = Modifier.background(vantaColors.surfaceElevated)
                        ) {
                            SortOption.entries.forEach { option ->
                                val selected = settings.sortOption == option
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = option.label,
                                            color = if (selected) vantaColors.accent else vantaColors.textPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                    },
                                    trailingIcon = {
                                        if (selected) {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = null,
                                                tint = vantaColors.accent,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    },
                                    onClick = {
                                        isSortMenuExpanded = false
                                        onSelectSortOption(option)
                                    }
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Settings",
                            tint = vantaColors.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Section 2: Latest, Oldest, and Favourites tabs with balanced spacing.
        // Preserve the baseline's subtle accent-tinted selected-tab fill and border,
        // comfortable horizontal padding, and compact height.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp)
        ) {
            CollectionTab.entries.forEach { tab ->
                val isSelected = when (tab) {
                    CollectionTab.FAVOURITES -> settings.selectedTab == CollectionTab.FAVOURITES
                    CollectionTab.LATEST -> settings.selectedTab != CollectionTab.FAVOURITES &&
                        settings.sortOption != SortOption.OLDEST_FIRST
                    CollectionTab.OLDEST -> settings.selectedTab != CollectionTab.FAVOURITES &&
                        settings.sortOption == SortOption.OLDEST_FIRST
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isSelected) vantaColors.accent.copy(alpha = 0.16f)
                            else vantaColors.surfaceCharcoal
                        )
                        .border(
                            width = 1.dp,
                            color = if (isSelected) vantaColors.accent.copy(alpha = 0.75f)
                            else Color.White.copy(alpha = 0.07f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { onSelectTab(tab) }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tab.label,
                        color = if (isSelected) vantaColors.accent else vantaColors.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * Section 3:
 * - 9:16 tiles, centre-cropped images
 * - No timestamp or duration printed on grid thumbnails
 * - Small local watch-count label near the bottom, with a subtle readability gradient
 * - Tap a thumbnail to play; long-press for Add/remove favourite and Change thumbnail
 * - Prevents late results from populating the wrong recycled tile via `remember(video.thumbnailCacheKey)`
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShortGridTile(
    video: VideoItem,
    thumbnailCache: ThumbnailCacheManager,
    onBoundsChanged: (Rect) -> Unit,
    onClick: (Rect?) -> Unit,
    onToggleFavourite: () -> Unit,
    onChangeThumbnail: () -> Unit
) {
    val vantaColors = LocalVantaColors.current
    var latestBounds by remember { mutableStateOf<Rect?>(null) }
    var showContextMenu by remember { mutableStateOf(false) }

    val cacheKey = video.thumbnailCacheKey
    var bitmap by remember(cacheKey) {
        mutableStateOf<Bitmap?>(thumbnailCache.getMemoryCached(video))
    }

    LaunchedEffect(cacheKey) {
        if (bitmap == null) {
            val loaded = thumbnailCache.getOrDecodeThumbnail(video)
            // Only populate if this tile is still bound to the exact same cacheKey
            if (cacheKey == video.thumbnailCacheKey) {
                bitmap = loaded
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(9f / 16f)
            .background(vantaColors.surfaceCharcoal)
            .onGloballyPositioned { coordinates ->
                if (coordinates.isAttached) {
                    val rect = coordinates.boundsInWindow()
                    latestBounds = rect
                    onBoundsChanged(rect)
                }
            }
            .combinedClickable(
                onClick = { onClick(latestBounds) },
                onLongClick = { showContextMenu = true }
            )
    ) {
        val bmp = bitmap
        if (bmp != null && !bmp.isRecycled) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = video.titleWithoutExtension,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Subtle readability gradient at bottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.68f))
                    )
                )
        )

        // Small favourite indicator in top-right if favourited
        if (video.isFavourite) {
            Icon(
                imageVector = Icons.Rounded.Favorite,
                contentDescription = "Favourite",
                tint = vantaColors.accent,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(14.dp)
            )
        }

        // Small local watch-count label near the bottom (no duration printed on grid thumbnails)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 7.dp, vertical = 5.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = video.formattedWatchCount,
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }

        // Long-press Menu: Add/remove favourite and Change thumbnail
        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false },
            modifier = Modifier.background(vantaColors.surfaceElevated)
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = if (video.isFavourite) "Remove favourite" else "Add favourite",
                        color = vantaColors.textPrimary,
                        fontSize = 13.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Favorite,
                        contentDescription = null,
                        tint = if (video.isFavourite) vantaColors.accent else vantaColors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                },
                onClick = {
                    showContextMenu = false
                    onToggleFavourite()
                }
            )
            DropdownMenuItem(
                text = {
                    Text(
                        text = "Change thumbnail",
                        color = vantaColors.textPrimary,
                        fontSize = 13.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Image,
                        contentDescription = null,
                        tint = vantaColors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                },
                onClick = {
                    showContextMenu = false
                    onChangeThumbnail()
                }
            )
        }
    }
}

@Composable
private fun FolderSwitcherDialog(
    folders: List<FolderEntry>,
    activeFolderUri: String?,
    onSelectFolder: (FolderEntry) -> Unit,
    onAddAnotherFolder: () -> Unit,
    onDismiss: () -> Unit
) {
    val vantaColors = LocalVantaColors.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = vantaColors.surfaceCharcoal,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Folders",
                    color = vantaColors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(10.dp))

                if (folders.isEmpty()) {
                    Text(
                        text = "No folders granted yet.",
                        color = vantaColors.textSecondary,
                        fontSize = 13.sp
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        folders.forEach { folder ->
                            val isActive = folder.uriString == activeFolderUri
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isActive) vantaColors.accent.copy(alpha = 0.14f)
                                        else vantaColors.surfaceElevated
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isActive) vantaColors.accent else Color.Transparent,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable { onSelectFolder(folder) }
                                    .padding(horizontal = 12.dp, vertical = 9.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = folder.displayName,
                                        color = vantaColors.textPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = folder.displayPath,
                                        color = vantaColors.textSecondary,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (isActive) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = "Active folder",
                                        tint = vantaColors.accent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onAddAnotherFolder,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = vantaColors.accent,
                        contentColor = Color(0xFF0F0F0F)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Add another folder",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun FirstUseOnboardingView(onAddFolderClick: () -> Unit) {
    val vantaColors = LocalVantaColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(vantaColors.surfaceCharcoal),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.VideoLibrary,
                contentDescription = null,
                tint = vantaColors.accent,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Choose a video folder",
            color = vantaColors.textPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Vanta plays local videos directly in place—including hidden .nomedia folders and nested subfolders.",
            color = vantaColors.textSecondary,
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onAddFolderClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = vantaColors.accent,
                contentColor = Color(0xFF0F0F0F)
            ),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = null,
                modifier = Modifier.size(17.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Select folder", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PermissionFailureView(
    message: String,
    onAddFolderClick: () -> Unit,
    onRefreshClick: () -> Unit
) {
    val vantaColors = LocalVantaColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
    ) {
        Text(
            text = "Folder access unavailable",
            color = vantaColors.textPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = message,
            color = vantaColors.textSecondary,
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onRefreshClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = vantaColors.surfaceElevated,
                    contentColor = vantaColors.textPrimary
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Retry")
            }
            Button(
                onClick = onAddFolderClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = vantaColors.accent,
                    contentColor = Color(0xFF0F0F0F)
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Grant folder permission", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun EmptyCollectionMessageView(
    selectedTab: CollectionTab,
    searchQuery: String,
    onRefresh: () -> Unit
) {
    val vantaColors = LocalVantaColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
    ) {
        val message = when {
            searchQuery.isNotBlank() -> "No search matches for \"${searchQuery.trim()}\""
            selectedTab == CollectionTab.FAVOURITES -> "No saved favourites yet"
            else -> "This folder has no supported videos"
        }
        Text(
            text = message,
            color = vantaColors.textSecondary,
            fontSize = 14.sp
        )
        if (searchQuery.isBlank() && selectedTab != CollectionTab.FAVOURITES) {
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onRefresh,
                colors = ButtonDefaults.buttonColors(
                    containerColor = vantaColors.surfaceElevated,
                    contentColor = vantaColors.textPrimary
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Refresh folder", fontSize = 13.sp)
            }
        }
    }
}

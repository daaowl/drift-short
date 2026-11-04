package com.localshorts.app.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localshorts.app.data.ThumbnailCacheManager
import com.localshorts.app.model.FolderEntry
import com.localshorts.app.model.SortTab
import com.localshorts.app.model.VideoItem
import com.localshorts.app.ui.theme.LocalHollowColors
import com.localshorts.app.ui.viewmodel.HollowUiState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(FlowPreview::class)
@Composable
fun GalleryScreen(
   uiState: HollowUiState,
   thumbnailCache: ThumbnailCacheManager,
   onAddFolderClick: () -> Unit,
   onSelectFolder: (FolderEntry) -> Unit,
   onRefreshFolder: () -> Unit,
   onSelectTab: (SortTab) -> Unit,
   onSearchQueryChange: (String) -> Unit,
   onCycleGridColumns: () -> Unit,
   onOpenSettings: () -> Unit,
   onVideoTapped: (VideoItem, Rect?) -> Unit,
   onToggleFavourite: (String) -> Unit,
   onRegisterThumbnailBounds: (String, Rect) -> Unit,
   onUnregisterThumbnailBounds: (String) -> Unit,
   onSaveScrollPosition: (Int, Int) -> Unit,
   modifier: Modifier = Modifier
) {
   val hollowColors = LocalHollowColors.current
   val settings = uiState.settings
   var isSearchExpanded by remember { mutableStateOf(false) }

   val gridState = rememberLazyGridState(
       initialFirstVisibleItemIndex = settings.lastGalleryScrollIndex,
       initialFirstVisibleItemScrollOffset = settings.lastGalleryScrollOffset
   )

   // Persist scroll position unobtrusively when scrolling settles
   LaunchedEffect(gridState) {
       snapshotFlow {
           gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset
       }
           .distinctUntilChanged()
           .debounce(400L)
           .collect { (index, offset) ->
               onSaveScrollPosition(index, offset)
           }
   }

   Box(
       modifier = modifier
           .fillMaxSize()
           .background(
               Brush.verticalGradient(
                   colors = listOf(hollowColors.gradientTop, hollowColors.gradientBottom)
               )
           )
           .statusBarsPadding()
   ) {
       Column(modifier = Modifier.fillMaxSize()) {
           // Compact Top Bar + Elevated Tab Row (positioned high with zero excess empty space above)
           TopHeaderAndTabs(
               uiState = uiState,
               isSearchExpanded = isSearchExpanded,
               onToggleSearch = {
                   isSearchExpanded = !isSearchExpanded
                   if (!isSearchExpanded && uiState.searchQuery.isNotEmpty()) {
                       onSearchQueryChange("")
                   }
               },
               onSearchQueryChange = onSearchQueryChange,
               onSelectTab = onSelectTab,
               onCycleGridColumns = onCycleGridColumns,
               onRefreshFolder = onRefreshFolder,
               onOpenSettings = onOpenSettings,
               onAddFolderClick = onAddFolderClick,
               onSelectFolder = onSelectFolder
           )

           // Main Content Area
           Box(
               modifier = Modifier
                   .fillMaxWidth()
                   .weight(1f)
           ) {
               when {
                   settings.savedFolders.isEmpty() -> {
                       EmptyLibraryOnboarding(
                           onAddFolderClick = onAddFolderClick
                       )
                   }
                   uiState.folderErrorMessage != null -> {
                       FolderErrorView(
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
                           Column(horizontalAlignment = Alignment.CenterHorizontally) {
                               CircularProgressIndicator(
                                   color = hollowColors.accentPrimary,
                                   strokeWidth = 2.5.dp,
                                   modifier = Modifier.size(32.dp)
                               )
                               Spacer(modifier = Modifier.height(12.dp))
                               Text(
                                   text = "Scanning folder…",
                                   color = hollowColors.textSecondary,
                                   fontSize = 13.sp
                               )
                           }
                       }
                   }
                   uiState.displayedVideos.isEmpty() -> {
                       EmptyTabOrSearchState(
                           selectedTab = settings.selectedTab,
                           searchQuery = uiState.searchQuery,
                           onRefresh = onRefreshFolder
                       )
                   }
                   else -> {
                       LazyVerticalGrid(
                           columns = GridCells.Fixed(settings.gridColumns.coerceIn(1, 4)),
                           state = gridState,
                           contentPadding = PaddingValues(
                               start = 10.dp,
                               end = 10.dp,
                               top = 6.dp,
                               bottom = 28.dp
                           ),
                           horizontalArrangement = Arrangement.spacedBy(8.dp),
                           verticalArrangement = Arrangement.spacedBy(8.dp),
                           modifier = Modifier.fillMaxSize()
                       ) {
                           items(
                               items = uiState.displayedVideos,
                               key = { it.stableId },
                               contentType = { "video_thumb" }
                           ) { video ->
                               VideoThumbnailCard(
                                   video = video,
                                   gridColumns = settings.gridColumns,
                                   thumbnailCache = thumbnailCache,
                                   onBoundsChanged = { rect ->
                                       onRegisterThumbnailBounds(video.stableId, rect)
                                   },
                                   onBoundsRemoved = {
                                       onUnregisterThumbnailBounds(video.stableId)
                                   },
                                   onClick = { rect ->
                                       onVideoTapped(video, rect)
                                   },
                                   onFavouriteToggle = {
                                       onToggleFavourite(video.stableId)
                                   }
                               )
                           }
                       }
                   }
               }
           }
       }
   }
}

@Composable
private fun TopHeaderAndTabs(
   uiState: HollowUiState,
   isSearchExpanded: Boolean,
   onToggleSearch: () -> Unit,
   onSearchQueryChange: (String) -> Unit,
   onSelectTab: (SortTab) -> Unit,
   onCycleGridColumns: () -> Unit,
   onRefreshFolder: () -> Unit,
   onOpenSettings: () -> Unit,
   onAddFolderClick: () -> Unit,
   onSelectFolder: (FolderEntry) -> Unit
) {
   val hollowColors = LocalHollowColors.current
   val settings = uiState.settings

   Column(
       modifier = Modifier
           .fillMaxWidth()
           .padding(horizontal = 12.dp, vertical = 4.dp)
   ) {
       // Row 1: App Title + Tabs inline or right below with tight spacing + utility actions
       Row(
           verticalAlignment = Alignment.CenterVertically,
           horizontalArrangement = Arrangement.SpaceBetween,
           modifier = Modifier.fillMaxWidth()
       ) {
           //Segmented Tab Pills positioned high in the header
           Row(
               verticalAlignment = Alignment.CenterVertically,
               horizontalArrangement = Arrangement.spacedBy(6.dp),
               modifier = Modifier
                   .clip(RoundedCornerShape(20.dp))
                   .background(hollowColors.surfaceCard)
                   .padding(3.dp)
           ) {
               SortTab.entries.forEach { tab ->
                   val selected = settings.selectedTab == tab
                   val labelText = if (tab == SortTab.FAVOURITES && uiState.favouritesCount > 0) {
                       "${tab.label} · ${uiState.favouritesCount}"
                   } else {
                       tab.label
                   }
                   Box(
                       modifier = Modifier
                           .clip(RoundedCornerShape(17.dp))
                           .background(
                               if (selected) hollowColors.accentPrimary else Color.Transparent
                           )
                           .clickable { onSelectTab(tab) }
                           .padding(horizontal = 13.dp, vertical = 6.dp),
                       contentAlignment = Alignment.Center
                   ) {
                       Text(
                           text = labelText,
                           color = if (selected) Color(0xFF09090D) else hollowColors.textSecondary,
                           fontSize = 13.sp,
                           fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                       )
                   }
               }
           }

           Row(
               verticalAlignment = Alignment.CenterVertically,
               horizontalArrangement = Arrangement.spacedBy(2.dp)
           ) {
               IconButton(
                   onClick = onToggleSearch,
                   modifier = Modifier.size(36.dp)
               ) {
                   Icon(
                       imageVector = if (isSearchExpanded) Icons.Rounded.Close else Icons.Rounded.Search,
                       contentDescription = "Search",
                       tint = if (uiState.searchQuery.isNotEmpty()) hollowColors.accentPrimary else hollowColors.textPrimary,
                       modifier = Modifier.size(20.dp)
                   )
               }

               IconButton(
                   onClick = onCycleGridColumns,
                   modifier = Modifier.size(36.dp)
               ) {
                   Row(verticalAlignment = Alignment.CenterVertically) {
                       Icon(
                           imageVector = Icons.Rounded.GridView,
                           contentDescription = "Grid Columns",
                           tint = hollowColors.textPrimary,
                           modifier = Modifier.size(18.dp)
                       )
                       Text(
                           text = "${settings.gridColumns}",
                           color = hollowColors.accentPrimary,
                           fontSize = 11.sp,
                           fontWeight = FontWeight.Bold
                       )
                   }
               }

               IconButton(
                   onClick = onOpenSettings,
                   modifier = Modifier.size(36.dp)
               ) {
                   Icon(
                       imageVector = Icons.Rounded.Settings,
                       contentDescription = "Settings",
                       tint = hollowColors.textPrimary,
                       modifier = Modifier.size(20.dp)
                   )
               }
           }
       }

       // Optional Folder Switcher Chip Strip (when folders exist)
       if (settings.savedFolders.isNotEmpty()) {
           Spacer(modifier = Modifier.height(6.dp))
           LazyRow(
               horizontalArrangement = Arrangement.spacedBy(6.dp),
               verticalAlignment = Alignment.CenterVertically,
               modifier = Modifier.fillMaxWidth()
           ) {
               items(
                   items = settings.savedFolders,
                   key = { it.uriString }
               ) { folder ->
                   val isActive = folder.uriString == settings.activeFolderUri
                   Row(
                       verticalAlignment = Alignment.CenterVertically,
                       modifier = Modifier
                           .clip(RoundedCornerShape(14.dp))
                           .background(
                               if (isActive) hollowColors.surfaceElevated else hollowColors.surfaceCard
                           )
                           .border(
                               width = 1.dp,
                               color = if (isActive) {
                                   hollowColors.accentPrimary.copy(alpha = 0.65f)
                               } else {
                                   Color.White.copy(alpha = 0.06f)
                               },
                               shape = RoundedCornerShape(14.dp)
                           )
                           .clickable { onSelectFolder(folder) }
                           .padding(horizontal = 10.dp, vertical = 5.dp)
                   ) {
                       Icon(
                           imageVector = Icons.Rounded.FolderOpen,
                           contentDescription = null,
                           tint = if (isActive) hollowColors.accentPrimary else hollowColors.textSecondary,
                           modifier = Modifier.size(14.dp)
                       )
                       Spacer(modifier = Modifier.width(5.dp))
                       Text(
                           text = folder.displayName,
                           color = if (isActive) hollowColors.textPrimary else hollowColors.textSecondary,
                           fontSize = 12.sp,
                           maxLines = 1,
                           overflow = TextOverflow.Ellipsis
                       )
                   }
               }

               item {
                   Row(
                       verticalAlignment = Alignment.CenterVertically,
                       modifier = Modifier
                           .clip(RoundedCornerShape(14.dp))
                           .background(hollowColors.surfaceCard)
                           .clickable { onAddFolderClick() }
                           .padding(horizontal = 9.dp, vertical = 5.dp)
                   ) {
                       Icon(
                           imageVector = Icons.Rounded.Add,
                           contentDescription = "Add Folder",
                           tint = hollowColors.accentPrimary,
                           modifier = Modifier.size(14.dp)
                       )
                       Spacer(modifier = Modifier.width(3.dp))
                       Text(
                           text = "Add",
                           color = hollowColors.accentPrimary,
                           fontSize = 12.sp,
                           fontWeight = FontWeight.Medium
                       )
                   }
               }

               item {
                   IconButton(
                       onClick = onRefreshFolder,
                       modifier = Modifier.size(26.dp)
                   ) {
                       Icon(
                           imageVector = Icons.Rounded.Refresh,
                           contentDescription = "Refresh Library",
                           tint = hollowColors.textSecondary,
                           modifier = Modifier.size(15.dp)
                       )
                   }
               }
           }
       }

       // Expandable Search Bar
       AnimatedVisibility(visible = isSearchExpanded || uiState.searchQuery.isNotEmpty()) {
           Box(
               modifier = Modifier
                   .fillMaxWidth()
                   .padding(top = 6.dp, bottom = 2.dp)
                   .clip(RoundedCornerShape(14.dp))
                   .background(hollowColors.surfaceElevated)
                   .padding(horizontal = 12.dp, vertical = 9.dp)
           ) {
               Row(verticalAlignment = Alignment.CenterVertically) {
                   Icon(
                       imageVector = Icons.Rounded.Search,
                       contentDescription = null,
                       tint = hollowColors.textSecondary,
                       modifier = Modifier.size(16.dp)
                   )
                   Spacer(modifier = Modifier.width(8.dp))
                   Box(modifier = Modifier.weight(1f)) {
                       if (uiState.searchQuery.isEmpty()) {
                           Text(
                               text = "Search by filename…",
                               color = hollowColors.textSecondary.copy(alpha = 0.7f),
                               fontSize = 13.sp
                           )
                       }
                       BasicTextField(
                           value = uiState.searchQuery,
                           onValueChange = onSearchQueryChange,
                           singleLine = true,
                           textStyle = TextStyle(
                               color = hollowColors.textPrimary,
                               fontSize = 13.sp
                           ),
                           cursorBrush = SolidColor(hollowColors.accentPrimary),
                           modifier = Modifier.fillMaxWidth()
                       )
                   }
                   if (uiState.searchQuery.isNotEmpty()) {
                       Icon(
                           imageVector = Icons.Rounded.Close,
                           contentDescription = "Clear search",
                           tint = hollowColors.textSecondary,
                           modifier = Modifier
                               .size(16.dp)
                               .clickable { onSearchQueryChange("") }
                       )
                   }
               }
           }
       }
   }
}

@Composable
private fun VideoThumbnailCard(
   video: VideoItem,
   gridColumns: Int,
   thumbnailCache: ThumbnailCacheManager,
   onBoundsChanged: (Rect) -> Unit,
   onBoundsRemoved: () -> Unit,
   onClick: (Rect?) -> Unit,
   onFavouriteToggle: () -> Unit
) {
   val hollowColors = LocalHollowColors.current
   var latestBounds by remember { mutableStateOf<Rect?>(null) }

   // Synchronous memory lookup prevents any placeholder flash on scroll or tab switch
   var bitmap by remember(video.stableId) {
       mutableStateOf<Bitmap?>(thumbnailCache.getMemoryCached(video.stableId))
   }

   LaunchedEffect(video.stableId) {
       if (bitmap == null) {
           bitmap = thumbnailCache.getOrDecodeThumbnail(video)
       }
   }

   DisposableEffect(video.stableId) {
       onDispose { onBoundsRemoved() }
   }

   Box(
       modifier = Modifier
           .fillMaxWidth()
           .aspectRatio(9f / 16f)
           .clip(RoundedCornerShape(14.dp))
           .background(hollowColors.surfaceCard)
           .onGloballyPositioned { coordinates ->
               if (coordinates.isAttached) {
                   val rect = coordinates.boundsInWindow()
                   latestBounds = rect
                   onBoundsChanged(rect)
               }
           }
           .clickable { onClick(latestBounds) }
   ) {
       val bmp = bitmap
       if (bmp != null && !bmp.isRecycled) {
           Image(
               bitmap = bmp.asImageBitmap(),
               contentDescription = video.fileName,
               contentScale = ContentScale.Crop,
               modifier = Modifier.fillMaxSize()
           )
       }

       // Subtle bottom gradient for duration and title legibility
       Box(
           modifier = Modifier
               .fillMaxWidth()
               .height(54.dp)
               .align(Alignment.BottomCenter)
               .background(
                   Brush.verticalGradient(
                       colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))
                   )
               )
       )

       // Top-right Favourite Indicator / Quick Toggle
       if (video.isFavourite) {
           Box(
               modifier = Modifier
                   .align(Alignment.TopEnd)
                   .padding(6.dp)
                   .size(22.dp)
                   .clip(CircleShape)
                   .background(Color.Black.copy(alpha = 0.52f))
                   .clickable { onFavouriteToggle() },
               contentAlignment = Alignment.Center
           ) {
               Icon(
                   imageVector = Icons.Rounded.Favorite,
                   contentDescription = "Favourite",
                   tint = hollowColors.accentPrimary,
                   modifier = Modifier.size(13.dp)
               )
           }
       }

       // Bottom metadata row: filename (when 1 or 2 columns) + duration badge
       Row(
           verticalAlignment = Alignment.CenterVertically,
           horizontalArrangement = Arrangement.SpaceBetween,
           modifier = Modifier
               .fillMaxWidth()
               .align(Alignment.BottomCenter)
               .padding(horizontal = 7.dp, vertical = 6.dp)
       ) {
           if (gridColumns <= 2) {
               Text(
                   text = video.fileName.substringBeforeLast('.'),
                   color = Color.White.copy(alpha = 0.9f),
                   fontSize = 11.sp,
                   maxLines = 1,
                   overflow = TextOverflow.Ellipsis,
                   modifier = Modifier
                       .weight(1f)
                       .padding(end = 6.dp)
               )
           } else {
               Spacer(modifier = Modifier.weight(1f))
           }

           Box(
               modifier = Modifier
                   .clip(RoundedCornerShape(6.dp))
                   .background(Color.Black.copy(alpha = 0.65f))
                   .padding(horizontal = 5.dp, vertical = 2.dp)
           ) {
               Text(
                   text = video.formattedDuration,
                   color = Color.White,
                   fontSize = 10.sp,
                   fontWeight = FontWeight.Medium
               )
           }
       }
   }
}

@Composable
private fun EmptyLibraryOnboarding(onAddFolderClick: () -> Unit) {
   val hollowColors = LocalHollowColors.current
   Column(
       horizontalAlignment = Alignment.CenterHorizontally,
       verticalArrangement = Arrangement.Center,
       modifier = Modifier
           .fillMaxSize()
           .padding(32.dp)
   ) {
       Box(
           modifier = Modifier
               .size(68.dp)
               .clip(RoundedCornerShape(22.dp))
               .background(hollowColors.surfaceElevated),
           contentAlignment = Alignment.Center
       ) {
           Icon(
               imageVector = Icons.Rounded.VideoLibrary,
               contentDescription = null,
               tint = hollowColors.accentPrimary,
               modifier = Modifier.size(32.dp)
           )
       }
       Spacer(modifier = Modifier.height(18.dp))
       Text(
           text = "Private Local Shorts",
           color = hollowColors.textPrimary,
           fontSize = 19.sp,
           fontWeight = FontWeight.SemiBold
       )
       Spacer(modifier = Modifier.height(8.dp))
       Text(
           text = "Select any folder on your device—including hidden .nomedia folders. Hollow never modifies or uploads your files.",
           color = hollowColors.textSecondary,
           fontSize = 13.sp,
           lineHeight = 19.sp
       )
       Spacer(modifier = Modifier.height(22.dp))
       Button(
           onClick = onAddFolderClick,
           colors = ButtonDefaults.buttonColors(
               containerColor = hollowColors.accentPrimary,
               contentColor = Color(0xFF0A0A0E)
           ),
           shape = RoundedCornerShape(14.dp)
       ) {
           Icon(
               imageVector = Icons.Rounded.Add,
               contentDescription = null,
               modifier = Modifier.size(18.dp)
           )
           Spacer(modifier = Modifier.width(6.dp))
           Text(
               text = "Add Video Folder",
               fontWeight = FontWeight.SemiBold
           )
       }
   }
}

@Composable
private fun FolderErrorView(
   message: String,
   onAddFolderClick: () -> Unit,
   onRefreshClick: () -> Unit
) {
   val hollowColors = LocalHollowColors.current
   Column(
       horizontalAlignment = Alignment.CenterHorizontally,
       verticalArrangement = Arrangement.Center,
       modifier = Modifier
           .fillMaxSize()
           .padding(32.dp)
   ) {
       Text(
           text = "Folder Unavailable",
           color = hollowColors.textPrimary,
           fontSize = 17.sp,
           fontWeight = FontWeight.SemiBold
       )
       Spacer(modifier = Modifier.height(8.dp))
       Text(
           text = message,
           color = hollowColors.textSecondary,
           fontSize = 13.sp
       )
       Spacer(modifier = Modifier.height(18.dp))
       Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
           Button(
               onClick = onRefreshClick,
               colors = ButtonDefaults.buttonColors(
                   containerColor = hollowColors.surfaceElevated,
                   contentColor = hollowColors.textPrimary
               ),
               shape = RoundedCornerShape(12.dp)
           ) {
               Text("Retry")
           }
           Button(
               onClick = onAddFolderClick,
               colors = ButtonDefaults.buttonColors(
                   containerColor = hollowColors.accentPrimary,
                   contentColor = Color(0xFF0A0A0E)
               ),
               shape = RoundedCornerShape(12.dp)
           ) {
               Text("Re-select Folder", fontWeight = FontWeight.SemiBold)
           }
       }
   }
}

@Composable
private fun EmptyTabOrSearchState(
   selectedTab: SortTab,
   searchQuery: String,
   onRefresh: () -> Unit
) {
   val hollowColors = LocalHollowColors.current
   Column(
       horizontalAlignment = Alignment.CenterHorizontally,
       verticalArrangement = Arrangement.Center,
       modifier = Modifier
           .fillMaxSize()
           .padding(32.dp)
   ) {
       val text = when {
           searchQuery.isNotBlank() -> "No videos matching \"$searchQuery\""
           selectedTab == SortTab.FAVOURITES -> "No favourite videos yet"
           else -> "No supported videos (MP4, MKV, MOV, WEBM) found in this folder"
       }
       Text(
           text = text,
           color = hollowColors.textSecondary,
           fontSize = 14.sp
       )
       if (searchQuery.isBlank() && selectedTab != SortTab.FAVOURITES) {
           Spacer(modifier = Modifier.height(14.dp))
           Button(
               onClick = onRefresh,
               colors = ButtonDefaults.buttonColors(
                   containerColor = hollowColors.surfaceElevated,
                   contentColor = hollowColors.textPrimary
               ),
               shape = RoundedCornerShape(12.dp)
           ) {
               Text("Rescan Folder", fontSize = 13.sp)
           }
       }
   }
}

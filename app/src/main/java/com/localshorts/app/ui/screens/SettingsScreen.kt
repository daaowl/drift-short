package com.localshorts.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localshorts.app.model.AccentPreset
import com.localshorts.app.model.AppSettings
import com.localshorts.app.model.EndBehavior
import com.localshorts.app.model.FolderEntry
import com.localshorts.app.model.GradientPreset
import com.localshorts.app.model.IconStylePreset
import com.localshorts.app.model.MiddleGestureAction
import com.localshorts.app.model.SUPPORTED_PLAYBACK_SPEEDS
import com.localshorts.app.model.SwipeTransitionSpeed
import com.localshorts.app.ui.theme.LocalHollowColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
   settings: AppSettings,
   onBack: () -> Unit,
   onSelectAccent: (AccentPreset) -> Unit,
   onSelectGradient: (GradientPreset) -> Unit,
   onSelectIconStyle: (IconStylePreset) -> Unit,
   onSelectGridColumns: (Int) -> Unit,
   onSelectDefaultSpeed: (Float) -> Unit,
   onSelectEndBehavior: (EndBehavior) -> Unit,
   onSelectSwipeTransitionSpeed: (SwipeTransitionSpeed) -> Unit,
   onToggleBrightnessGesture: (Boolean) -> Unit,
   onToggleVolumeGesture: (Boolean) -> Unit,
   onSelectMiddleGesture: (MiddleGestureAction) -> Unit,
   onAddFolder: () -> Unit,
   onSelectActiveFolder: (FolderEntry) -> Unit,
   onRemoveFolder: (FolderEntry) -> Unit,
   onRefreshLibrary: () -> Unit,
   customThumbnailCount: Int,
   onSelectThumbnailFixedSeconds: (Int?) -> Unit,
   onClearThumbnailOverrides: () -> Unit,
   modifier: Modifier = Modifier
) {
   BackHandler(onBack = onBack)

   val hollowColors = LocalHollowColors.current
   val scrollState = rememberScrollState()

   Column(
       modifier = modifier
           .fillMaxSize()
           .background(
               Brush.verticalGradient(
                   colors = listOf(hollowColors.gradientTop, hollowColors.gradientBottom)
               )
           )
           .statusBarsPadding()
           .navigationBarsPadding()
   ) {
       // Header
       Row(
           verticalAlignment = Alignment.CenterVertically,
           modifier = Modifier
               .fillMaxWidth()
               .padding(horizontal = 10.dp, vertical = 8.dp)
       ) {
           IconButton(onClick = onBack) {
               Icon(
                   imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                   contentDescription = "Back",
                   tint = hollowColors.textPrimary
               )
           }
           Text(
               text = "Settings",
               color = hollowColors.textPrimary,
               fontSize = 19.sp,
               fontWeight = FontWeight.SemiBold
           )
       }

       Column(
           verticalArrangement = Arrangement.spacedBy(16.dp),
           modifier = Modifier
               .fillMaxSize()
               .verticalScroll(scrollState)
               .padding(horizontal = 16.dp, vertical = 8.dp)
       ) {
           // SECTION 1: LIBRARY FOLDERS
           SettingsSectionCard(title = "Library Folders (.nomedia supported)") {
               Row(
                   horizontalArrangement = Arrangement.spacedBy(8.dp),
                   modifier = Modifier.fillMaxWidth()
               ) {
                   Button(
                       onClick = onAddFolder,
                       colors = ButtonDefaults.buttonColors(
                           containerColor = hollowColors.accentPrimary,
                           contentColor = Color(0xFF0A0A0E)
                       ),
                       shape = RoundedCornerShape(12.dp),
                       modifier = Modifier.weight(1f)
                   ) {
                       Icon(
                           imageVector = Icons.Rounded.Add,
                           contentDescription = null,
                           modifier = Modifier.size(16.dp)
                       )
                       Spacer(modifier = Modifier.width(6.dp))
                       Text("Add Folder", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                   }

                   Button(
                       onClick = onRefreshLibrary,
                       colors = ButtonDefaults.buttonColors(
                           containerColor = hollowColors.surfaceElevated,
                           contentColor = hollowColors.textPrimary
                       ),
                       shape = RoundedCornerShape(12.dp)
                   ) {
                       Icon(
                           imageVector = Icons.Rounded.Refresh,
                           contentDescription = "Refresh",
                           modifier = Modifier.size(16.dp)
                       )
                       Spacer(modifier = Modifier.width(6.dp))
                       Text("Refresh", fontSize = 13.sp)
                   }
               }

               if (settings.savedFolders.isEmpty()) {
                   Spacer(modifier = Modifier.height(8.dp))
                   Text(
                       text = "No folders added yet.",
                       color = hollowColors.textSecondary,
                       fontSize = 13.sp
                   )
               } else {
                   Spacer(modifier = Modifier.height(10.dp))
                   Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                       settings.savedFolders.forEach { folder ->
                           val isActive = folder.uriString == settings.activeFolderUri
                           Row(
                               verticalAlignment = Alignment.CenterVertically,
                               horizontalArrangement = Arrangement.SpaceBetween,
                               modifier = Modifier
                                   .fillMaxWidth()
                                   .clip(RoundedCornerShape(12.dp))
                                   .background(hollowColors.surfaceElevated)
                                   .border(
                                       width = 1.dp,
                                       color = if (isActive) hollowColors.accentPrimary else Color.Transparent,
                                       shape = RoundedCornerShape(12.dp)
                                   )
                                   .clickable { onSelectActiveFolder(folder) }
                                   .padding(horizontal = 12.dp, vertical = 10.dp)
                           ) {
                               Row(
                                   verticalAlignment = Alignment.CenterVertically,
                                   modifier = Modifier.weight(1f)
                               ) {
                                   Icon(
                                       imageVector = Icons.Rounded.Folder,
                                       contentDescription = null,
                                       tint = if (isActive) hollowColors.accentPrimary else hollowColors.textSecondary,
                                       modifier = Modifier.size(18.dp)
                                   )
                                   Spacer(modifier = Modifier.width(10.dp))
                                   Column {
                                       Text(
                                           text = folder.displayName,
                                           color = hollowColors.textPrimary,
                                           fontSize = 14.sp,
                                           fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                           maxLines = 1,
                                           overflow = TextOverflow.Ellipsis
                                       )
                                       Text(
                                           text = if (!folder.isAvailable) {
                                               "Permission revoked or folder unavailable"
                                           } else if (isActive) {
                                               "Active folder"
                                           } else {
                                               "Tap to switch"
                                           },
                                           color = if (!folder.isAvailable) Color(0xFFFF6B6B) else hollowColors.textSecondary,
                                           fontSize = 11.sp
                                       )
                                   }
                               }

                               IconButton(
                                   onClick = { onRemoveFolder(folder) },
                                   modifier = Modifier.size(32.dp)
                               ) {
                                   Icon(
                                       imageVector = Icons.Rounded.DeleteOutline,
                                       contentDescription = "Remove folder from app",
                                       tint = hollowColors.textSecondary,
                                       modifier = Modifier.size(18.dp)
                                   )
                               }
                           }
                       }
                   }
               }
           }

           // SECTION: THUMBNAILS
           SettingsSectionCard(title = "Thumbnails") {
               val fixed = settings.thumbnailFixedSeconds
               Text(
                   text = "Default thumbnail frame",
                   color = hollowColors.textPrimary,
                   fontSize = 13.sp,
                   fontWeight = FontWeight.Medium
               )
               Text(
                   text = "Applies to every video without its own thumbnail.",
                   color = hollowColors.textSecondary,
                   fontSize = 11.sp
               )
               Spacer(modifier = Modifier.height(8.dp))
               Row(
                   horizontalArrangement = Arrangement.spacedBy(8.dp),
                   modifier = Modifier.fillMaxWidth()
               ) {
                   SelectablePill(
                       label = "Automatic",
                       selected = fixed == null,
                       onClick = { onSelectThumbnailFixedSeconds(null) },
                       modifier = Modifier.weight(1f)
                   )
                   SelectablePill(
                       label = "Fixed time",
                       selected = fixed != null,
                       onClick = { if (fixed == null) onSelectThumbnailFixedSeconds(3) },
                       modifier = Modifier.weight(1f)
                   )
               }

               if (fixed != null) {
                   Spacer(modifier = Modifier.height(12.dp))
                   Row(
                       verticalAlignment = Alignment.CenterVertically,
                       modifier = Modifier.fillMaxWidth()
                   ) {
                       Text(
                           text = "Time into each video",
                           color = hollowColors.textPrimary,
                           fontSize = 13.sp,
                           modifier = Modifier.weight(1f)
                       )
                       StepperButton(label = "−", enabled = fixed > 0) {
                           onSelectThumbnailFixedSeconds((fixed - 1).coerceAtLeast(0))
                       }
                       Text(
                           text = "$fixed s",
                           color = hollowColors.textPrimary,
                           fontSize = 14.sp,
                           fontWeight = FontWeight.SemiBold,
                           modifier = Modifier.padding(horizontal = 12.dp)
                       )
                       StepperButton(label = "+", enabled = fixed < 600) {
                           onSelectThumbnailFixedSeconds((fixed + 1).coerceAtMost(600))
                       }
                   }
                   Spacer(modifier = Modifier.height(8.dp))
                   Row(
                       horizontalArrangement = Arrangement.spacedBy(6.dp),
                       modifier = Modifier.fillMaxWidth()
                   ) {
                       listOf(1, 3, 5, 10, 30).forEach { s ->
                           SelectablePill(
                               label = "${s}s",
                               selected = fixed == s,
                               onClick = { onSelectThumbnailFixedSeconds(s) },
                               modifier = Modifier.weight(1f)
                           )
                       }
                   }
               }

               Spacer(modifier = Modifier.height(12.dp))
               Text(
                   text = if (customThumbnailCount > 0) {
                       "$customThumbnailCount video${if (customThumbnailCount == 1) " has" else "s have"} an individual thumbnail, which overrides the default."
                   } else {
                       "To give one video its own thumbnail, tap the image button in the player. Long-press it to go back to the default."
                   },
                   color = hollowColors.textSecondary,
                   fontSize = 11.sp
               )
               if (customThumbnailCount > 0) {
                   Spacer(modifier = Modifier.height(8.dp))
                   SelectablePill(
                       label = "Reset all individual thumbnails",
                       selected = false,
                       onClick = onClearThumbnailOverrides,
                       modifier = Modifier.fillMaxWidth()
                   )
               }
           }

           // SECTION 2: PLAYBACK & SWIPE PHYSICS
           SettingsSectionCard(title = "Playback & Navigation") {
               Text(
                   text = "Video Swipe Transition Speed",
                   color = hollowColors.textPrimary,
                   fontSize = 13.sp,
                   fontWeight = FontWeight.Medium
               )
               Text(
                   text = "Controls settling speed after release while preserving 1:1 finger tracking.",
                   color = hollowColors.textSecondary,
                   fontSize = 11.sp
               )
               Spacer(modifier = Modifier.height(8.dp))
               Row(
                   horizontalArrangement = Arrangement.spacedBy(8.dp),
                   modifier = Modifier.fillMaxWidth()
               ) {
                   SwipeTransitionSpeed.entries.forEach { speed ->
                       val selected = settings.swipeTransitionSpeed == speed
                       SelectablePill(
                           label = speed.label,
                           selected = selected,
                           onClick = { onSelectSwipeTransitionSpeed(speed) },
                           modifier = Modifier.weight(1f)
                       )
                   }
               }

               Spacer(modifier = Modifier.height(14.dp))
               Text(
                   text = "End-of-Video Behavior",
                   color = hollowColors.textPrimary,
                   fontSize = 13.sp,
                   fontWeight = FontWeight.Medium
               )
               Spacer(modifier = Modifier.height(8.dp))
               Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                   EndBehavior.entries.forEach { behavior ->
                       val selected = settings.endBehavior == behavior
                       Row(
                           verticalAlignment = Alignment.CenterVertically,
                           horizontalArrangement = Arrangement.SpaceBetween,
                           modifier = Modifier
                               .fillMaxWidth()
                               .clip(RoundedCornerShape(10.dp))
                               .background(
                                   if (selected) hollowColors.accentPrimary.copy(alpha = 0.16f)
                                   else hollowColors.surfaceElevated
                               )
                               .border(
                                   width = 1.dp,
                                   color = if (selected) hollowColors.accentPrimary else Color.Transparent,
                                   shape = RoundedCornerShape(10.dp)
                               )
                               .clickable { onSelectEndBehavior(behavior) }
                               .padding(horizontal = 12.dp, vertical = 9.dp)
                       ) {
                           Column {
                               Text(
                                   text = behavior.label,
                                   color = hollowColors.textPrimary,
                                   fontSize = 13.sp,
                                   fontWeight = FontWeight.SemiBold
                               )
                               Text(
                                   text = behavior.description,
                                   color = hollowColors.textSecondary,
                                   fontSize = 11.sp
                               )
                           }
                       }
                   }
               }

               Spacer(modifier = Modifier.height(14.dp))
               Text(
                   text = "Default Playback Speed",
                   color = hollowColors.textPrimary,
                   fontSize = 13.sp,
                   fontWeight = FontWeight.Medium
               )
               Spacer(modifier = Modifier.height(8.dp))
               FlowRow(
                   horizontalArrangement = Arrangement.spacedBy(6.dp),
                   verticalArrangement = Arrangement.spacedBy(6.dp)
               ) {
                   SUPPORTED_PLAYBACK_SPEEDS.forEach { spd ->
                       SelectablePill(
                           label = "${spd}x",
                           selected = settings.defaultPlaybackSpeed == spd,
                           onClick = { onSelectDefaultSpeed(spd) }
                       )
                   }
               }
           }

           // SECTION 3: GESTURES
           SettingsSectionCard(title = "Gestures") {
               SettingToggleRow(
                   title = "Left-Side Brightness Swipe",
                   subtitle = "Vertical drag along left edge adjusts window brightness",
                   checked = settings.gestureBrightnessEnabled,
                   onCheckedChange = onToggleBrightnessGesture
               )
               Spacer(modifier = Modifier.height(10.dp))
               SettingToggleRow(
                   title = "Right-Side Volume Swipe",
                   subtitle = "Vertical drag along right edge adjusts media volume",
                   checked = settings.gestureVolumeEnabled,
                   onCheckedChange = onToggleVolumeGesture
               )
               Spacer(modifier = Modifier.height(12.dp))
               Text(
                   text = "Middle Zone Double-Tap Action",
                   color = hollowColors.textPrimary,
                   fontSize = 13.sp,
                   fontWeight = FontWeight.Medium
               )
               Spacer(modifier = Modifier.height(8.dp))
               Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                   MiddleGestureAction.entries.forEach { action ->
                       SelectablePill(
                           label = action.label,
                           selected = settings.middleGestureAction == action,
                           onClick = { onSelectMiddleGesture(action) },
                           modifier = Modifier.fillMaxWidth()
                       )
                   }
               }
           }

           // SECTION 4: APPEARANCE
           SettingsSectionCard(title = "Appearance") {
               Text(
                   text = "Gallery Grid Columns",
                   color = hollowColors.textPrimary,
                   fontSize = 13.sp,
                   fontWeight = FontWeight.Medium
               )
               Spacer(modifier = Modifier.height(8.dp))
               Row(
                   horizontalArrangement = Arrangement.spacedBy(8.dp),
                   modifier = Modifier.fillMaxWidth()
               ) {
                   listOf(1, 2, 3, 4).forEach { cols ->
                       SelectablePill(
                           label = "$cols Col",
                           selected = settings.gridColumns == cols,
                           onClick = { onSelectGridColumns(cols) },
                           modifier = Modifier.weight(1f)
                       )
                   }
               }

               Spacer(modifier = Modifier.height(14.dp))
               Text(
                   text = "Accent Colour Preset",
                   color = hollowColors.textPrimary,
                   fontSize = 13.sp,
                   fontWeight = FontWeight.Medium
               )
               Spacer(modifier = Modifier.height(8.dp))
               FlowRow(
                   horizontalArrangement = Arrangement.spacedBy(8.dp),
                   verticalArrangement = Arrangement.spacedBy(8.dp)
               ) {
                   AccentPreset.entries.forEach { preset ->
                       val selected = settings.accentPreset == preset
                       Row(
                           verticalAlignment = Alignment.CenterVertically,
                           modifier = Modifier
                               .clip(RoundedCornerShape(18.dp))
                               .background(hollowColors.surfaceElevated)
                               .border(
                                   width = if (selected) 1.5.dp else 1.dp,
                                   color = if (selected) preset.primaryColor else Color.White.copy(alpha = 0.08f),
                                   shape = RoundedCornerShape(18.dp)
                               )
                               .clickable { onSelectAccent(preset) }
                               .padding(horizontal = 10.dp, vertical = 6.dp)
                       ) {
                           Box(
                               modifier = Modifier
                                   .size(12.dp)
                                   .clip(CircleShape)
                                   .background(preset.primaryColor)
                           )
                           Spacer(modifier = Modifier.width(6.dp))
                           Text(
                               text = preset.label,
                               color = hollowColors.textPrimary,
                               fontSize = 12.sp
                           )
                       }
                   }
               }

               Spacer(modifier = Modifier.height(14.dp))
               Text(
                   text = "Gradient Theme",
                   color = hollowColors.textPrimary,
                   fontSize = 13.sp,
                   fontWeight = FontWeight.Medium
               )
               Spacer(modifier = Modifier.height(8.dp))
               FlowRow(
                   horizontalArrangement = Arrangement.spacedBy(8.dp),
                   verticalArrangement = Arrangement.spacedBy(8.dp)
               ) {
                   GradientPreset.entries.forEach { preset ->
                       SelectablePill(
                           label = preset.label,
                           selected = settings.gradientPreset == preset,
                           onClick = { onSelectGradient(preset) }
                       )
                   }
               }

               Spacer(modifier = Modifier.height(14.dp))
               Text(
                   text = "Player Overlay Icon Style",
                   color = hollowColors.textPrimary,
                   fontSize = 13.sp,
                   fontWeight = FontWeight.Medium
               )
               Spacer(modifier = Modifier.height(8.dp))
               Row(
                   horizontalArrangement = Arrangement.spacedBy(8.dp),
                   modifier = Modifier.fillMaxWidth()
               ) {
                   IconStylePreset.entries.forEach { style ->
                       SelectablePill(
                           label = style.label,
                           selected = settings.iconStylePreset == style,
                           onClick = { onSelectIconStyle(style) },
                           modifier = Modifier.weight(1f)
                       )
                   }
               }
           }

           Spacer(modifier = Modifier.height(24.dp))
       }
   }
}

@Composable
private fun SettingsSectionCard(
   title: String,
   content: @Composable () -> Unit
) {
   val hollowColors = LocalHollowColors.current
   Column(
       modifier = Modifier
           .fillMaxWidth()
           .clip(RoundedCornerShape(16.dp))
           .background(hollowColors.surfaceCard)
           .padding(14.dp)
   ) {
       Text(
           text = title,
           color = hollowColors.accentPrimary,
           fontSize = 12.sp,
           fontWeight = FontWeight.Bold
       )
       Spacer(modifier = Modifier.height(10.dp))
       content()
   }
}

@Composable
private fun StepperButton(
   label: String,
   enabled: Boolean,
   onClick: () -> Unit
) {
   val hollowColors = LocalHollowColors.current
   Box(
       modifier = Modifier
           .size(34.dp)
           .clip(CircleShape)
           .background(hollowColors.surfaceElevated)
           .clickable(enabled = enabled) { onClick() },
       contentAlignment = Alignment.Center
   ) {
       Text(
           text = label,
           color = if (enabled) hollowColors.textPrimary else hollowColors.textSecondary,
           fontSize = 17.sp,
           fontWeight = FontWeight.SemiBold
       )
   }
}

@Composable
private fun SelectablePill(
   label: String,
   selected: Boolean,
   onClick: () -> Unit,
   modifier: Modifier = Modifier
) {
   val hollowColors = LocalHollowColors.current
   Box(
       modifier = modifier
           .clip(RoundedCornerShape(10.dp))
           .background(
               if (selected) hollowColors.accentPrimary else hollowColors.surfaceElevated
           )
           .clickable { onClick() }
           .padding(horizontal = 12.dp, vertical = 8.dp),
       contentAlignment = Alignment.Center
   ) {
       Text(
           text = label,
           color = if (selected) Color(0xFF0A0A0E) else hollowColors.textPrimary,
           fontSize = 12.sp,
           fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
       )
   }
}

@Composable
private fun SettingToggleRow(
   title: String,
   subtitle: String,
   checked: Boolean,
   onCheckedChange: (Boolean) -> Unit
) {
   val hollowColors = LocalHollowColors.current
   Row(
       verticalAlignment = Alignment.CenterVertically,
       horizontalArrangement = Arrangement.SpaceBetween,
       modifier = Modifier.fillMaxWidth()
   ) {
       Column(modifier = Modifier.weight(1f)) {
           Text(
               text = title,
               color = hollowColors.textPrimary,
               fontSize = 13.sp,
               fontWeight = FontWeight.Medium
           )
           Text(
               text = subtitle,
               color = hollowColors.textSecondary,
               fontSize = 11.sp
           )
       }
       Switch(
           checked = checked,
           onCheckedChange = onCheckedChange,
           colors = SwitchDefaults.colors(
               checkedThumbColor = Color(0xFF0A0A0E),
               checkedTrackColor = hollowColors.accentPrimary
           )
       )
   }
}

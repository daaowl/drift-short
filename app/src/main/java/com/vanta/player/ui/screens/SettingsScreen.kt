package com.vanta.player.ui.screens

import android.app.KeyguardManager
import android.content.Context
import android.graphics.Color as AndroidColor
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.vanta.player.model.AccentPreset
import com.vanta.player.model.AppSettings
import com.vanta.player.model.EndBehavior
import com.vanta.player.model.FolderEntry
import com.vanta.player.model.LauncherIconOption
import com.vanta.player.model.SUPPORTED_PLAYBACK_SPEEDS
import com.vanta.player.model.ShortTransitionSpeed
import com.vanta.player.ui.theme.LocalVantaColors
import java.util.Locale

/**
 * Implements Section 10 & Section 11:
 * Clearly separated settings groups:
 * 1. Appearance (Accent presets + Custom HSV Picker with rainbow hue/saturation/brightness sliders + 6 Launcher Icons + Grid Columns)
 * 2. Thumbnails (Global Default frame timestamp in seconds including decimals, Apply to all videos, Keep custom frames with confirmation dialog)
 * 3. Playback (Playback speed, Short transition speed, End-of-video behavior)
 * 4. Player gestures (Brightness switch, Volume switch, Gesture-zone explanation)
 * 5. Library (Folder management, Watch-count help explanation)
 * 6. Privacy (Optional Require phone unlock using device credentials)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onSelectAccentColor: (Int) -> Unit,
    onSelectLauncherIcon: (LauncherIconOption) -> Unit,
    onSelectGridColumns: (Int) -> Unit,
    onSetKeepCustomFrames: (Boolean) -> Unit,
    onApplyGlobalDefaultThumbnailSeconds: (Float, Boolean) -> Unit,
    onSelectDefaultSpeed: (Float) -> Unit,
    onSelectShortTransitionSpeed: (ShortTransitionSpeed) -> Unit,
    onSelectEndBehavior: (EndBehavior) -> Unit,
    onToggleBrightnessGesture: (Boolean) -> Unit,
    onToggleVolumeGesture: (Boolean) -> Unit,
    onToggleRequirePhoneUnlock: (Boolean) -> Unit,
    onAddFolder: () -> Unit,
    onSelectActiveFolder: (FolderEntry) -> Unit,
    onRemoveFolder: (FolderEntry) -> Unit,
    onRefreshLibrary: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val vantaColors = LocalVantaColors.current
    val scrollState = rememberScrollState()

    var showCustomAccentPicker by remember { mutableStateOf(false) }
    var defaultFrameInput by remember(settings.defaultThumbnailSeconds) {
        mutableStateOf(
            if (settings.defaultThumbnailSeconds % 1f == 0f) {
                String.format(Locale.US, "%.0f", settings.defaultThumbnailSeconds)
            } else {
                settings.defaultThumbnailSeconds.toString()
            }
        )
    }
    var pendingClearOverridesSeconds by remember { mutableStateOf<Float?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(vantaColors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = vantaColors.textPrimary
                )
            }
            Text(
                text = "Settings",
                color = vantaColors.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            // GROUP 1: APPEARANCE
            SettingsSectionCard(title = "Appearance") {
                Text(
                    text = "Accent colour (${settings.accentHexString})",
                    color = vantaColors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AccentPreset.entries.forEach { preset ->
                        val selected = settings.accentColorArgb == preset.colorHex
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(vantaColors.surfaceElevated)
                                .border(
                                    width = if (selected) 1.5.dp else 1.dp,
                                    color = if (selected) preset.color else Color.White.copy(alpha = 0.08f),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable { onSelectAccentColor(preset.colorHex) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(preset.color)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = preset.label,
                                color = vantaColors.textPrimary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    // Custom HSV Colour Picker trigger
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(vantaColors.surfaceElevated)
                            .border(
                                width = 1.dp,
                                color = vantaColors.accent.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable { showCustomAccentPicker = true }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Palette,
                            contentDescription = null,
                            tint = vantaColors.accent,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Custom…",
                            color = vantaColors.textPrimary,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Grid columns",
                    color = vantaColors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(1, 2, 3, 4).forEach { cols ->
                        SelectableOptionPill(
                            label = "$cols",
                            selected = settings.gridColumns == cols,
                            onClick = { onSelectGridColumns(cols) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "App launcher icon",
                    color = vantaColors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LauncherIconOption.entries.chunked(2).forEach { rowPair ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            rowPair.forEach { option ->
                                val selected = settings.launcherIcon == option
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(vantaColors.surfaceElevated)
                                        .border(
                                            width = if (selected) 1.5.dp else 1.dp,
                                            color = if (selected) vantaColors.accent else Color.White.copy(alpha = 0.07f),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable { onSelectLauncherIcon(option) }
                                        .padding(10.dp)
                                ) {
                                    LauncherIconPreviewBadge(option = option)
                                    Spacer(modifier = Modifier.width(9.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = option.title,
                                            color = vantaColors.textPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = option.subtitle,
                                            color = vantaColors.textSecondary,
                                            fontSize = 10.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (selected) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = "Selected icon",
                                            tint = vantaColors.accent,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // GROUP 2: THUMBNAILS
            SettingsSectionCard(title = "Thumbnails") {
                Text(
                    text = "Default frame timestamp (seconds)",
                    color = vantaColors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Accepts decimals (e.g. 1.0 or 2.5). Extraction is clamped to each video's length.",
                    color = vantaColors.textSecondary,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = defaultFrameInput,
                        onValueChange = { defaultFrameInput = it },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )

                    Button(
                        onClick = {
                            val parsed = defaultFrameInput.trim().toFloatOrNull()
                            if (parsed == null || parsed < 0f || parsed.isNaN()) {
                                onShowMessage("Enter a valid non-negative timestamp in seconds")
                            } else if (settings.keepCustomFramesOnGlobalApply) {
                                onApplyGlobalDefaultThumbnailSeconds(parsed, false)
                            } else {
                                pendingClearOverridesSeconds = parsed
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = vantaColors.accent,
                            contentColor = Color(0xFF0F0F0F)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "Apply to all videos",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                SettingToggleRow(
                    title = "Keep custom frames",
                    subtitle = "When enabled, applying a global default preserves per-video custom thumbnails",
                    checked = settings.keepCustomFramesOnGlobalApply,
                    onCheckedChange = onSetKeepCustomFrames
                )
            }

            // GROUP 3: PLAYBACK
            SettingsSectionCard(title = "Playback") {
                Text(
                    text = "Playback speed",
                    color = vantaColors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SUPPORTED_PLAYBACK_SPEEDS.forEach { spd ->
                        SelectableOptionPill(
                            label = "${spd}×",
                            selected = settings.defaultPlaybackSpeed == spd,
                            onClick = { onSelectDefaultSpeed(spd) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Short transition",
                    color = vantaColors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ShortTransitionSpeed.entries.forEach { speed ->
                        SelectableOptionPill(
                            label = speed.label,
                            selected = settings.shortTransitionSpeed == speed,
                            onClick = { onSelectShortTransitionSpeed(speed) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "End of video",
                    color = vantaColors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    EndBehavior.entries.forEach { behavior ->
                        SelectableOptionPill(
                            label = behavior.label,
                            selected = settings.endBehavior == behavior,
                            onClick = { onSelectEndBehavior(behavior) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // GROUP 4: PLAYER GESTURES
            SettingsSectionCard(title = "Player gestures") {
                SettingToggleRow(
                    title = "Brightness gesture",
                    subtitle = "Vertical swipe in the left quarter adjusts app-window brightness",
                    checked = settings.gestureBrightnessEnabled,
                    onCheckedChange = onToggleBrightnessGesture
                )
                Spacer(modifier = Modifier.height(10.dp))
                SettingToggleRow(
                    title = "Volume gesture",
                    subtitle = "Vertical swipe in the right quarter adjusts media volume",
                    checked = settings.gestureVolumeEnabled,
                    onCheckedChange = onToggleVolumeGesture
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Gesture zones: Left 25% controls window brightness, Right 25% controls media volume, and Middle 50% swipes between shorts. Disabling a side gesture makes that region available for swiping between shorts.",
                    color = vantaColors.textSecondary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }

            // GROUP 5: LIBRARY
            SettingsSectionCard(title = "Library") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = onAddFolder,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = vantaColors.accent,
                            contentColor = Color(0xFF0F0F0F)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add another folder", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }

                    Button(
                        onClick = onRefreshLibrary,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = vantaColors.surfaceElevated,
                            contentColor = vantaColors.textPrimary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Refresh",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Refresh", fontSize = 12.sp)
                    }
                }

                if (settings.savedFolders.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        settings.savedFolders.forEach { folder ->
                            val isActive = folder.uriString == settings.activeFolderUri
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(vantaColors.surfaceElevated)
                                    .border(
                                        width = 1.dp,
                                        color = if (isActive) vantaColors.accent else Color.Transparent,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable { onSelectActiveFolder(folder) }
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Folder,
                                        contentDescription = null,
                                        tint = if (isActive) vantaColors.accent else vantaColors.textSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = folder.displayName,
                                            color = vantaColors.textPrimary,
                                            fontSize = 13.sp,
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
                                }

                                IconButton(
                                    onClick = { onRemoveFolder(folder) },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = "Remove folder",
                                        tint = vantaColors.textSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Local watch counts: A view is counted once per viewing session after 3 seconds of active playback (or 90% of a shorter video's duration), adjusted for playback speed. Paused time, seeking jumps, and automatic loops do not add extra views.",
                    color = vantaColors.textSecondary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }

            // GROUP 6: PRIVACY
            SettingsSectionCard(title = "Privacy") {
                SettingToggleRow(
                    title = "Require phone unlock",
                    subtitle = "Confirm device PIN, pattern, password, or biometric when opening Vanta",
                    checked = settings.requirePhoneUnlock,
                    onCheckedChange = { wantEnabled ->
                        if (wantEnabled) {
                            val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                            if (km?.isDeviceSecure == true) {
                                onToggleRequirePhoneUnlock(true)
                            } else {
                                onShowMessage("Set up a secure screen lock in Android Settings before enabling")
                            }
                        } else {
                            onToggleRequirePhoneUnlock(false)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Confirmation dialog when applying global default with Keep custom frames disabled
    val secondsToConfirm = pendingClearOverridesSeconds
    if (secondsToConfirm != null) {
        AlertDialog(
            onDismissRequest = { pendingClearOverridesSeconds = null },
            containerColor = vantaColors.surfaceCharcoal,
            title = {
                Text(
                    text = "Replace custom thumbnails?",
                    color = vantaColors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Text(
                    text = "Keep custom frames is disabled. Applying ${secondsToConfirm}s to all videos will clear your individual custom thumbnail overrides.",
                    color = vantaColors.textSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingClearOverridesSeconds = null
                        onApplyGlobalDefaultThumbnailSeconds(secondsToConfirm, true)
                    }
                ) {
                    Text("Apply & clear overrides", color = vantaColors.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingClearOverridesSeconds = null }) {
                    Text("Cancel", color = vantaColors.textPrimary)
                }
            }
        )
    }

    // Custom HSV Accent Picker Dialog (Hue rainbow slider + Saturation + Brightness + Hex readout)
    if (showCustomAccentPicker) {
        CustomAccentColorPickerDialog(
            initialColorArgb = settings.accentColorArgb,
            onSave = { argb ->
                showCustomAccentPicker = false
                onSelectAccentColor(argb)
            },
            onCancel = { showCustomAccentPicker = false }
        )
    }
}

@Composable
private fun CustomAccentColorPickerDialog(
    initialColorArgb: Int,
    onSave: (Int) -> Unit,
    onCancel: () -> Unit
) {
    val vantaColors = LocalVantaColors.current
    val initialHsv = remember(initialColorArgb) {
        FloatArray(3).also { AndroidColor.colorToHSV(initialColorArgb, it) }
    }

    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var value by remember { mutableFloatStateOf(initialHsv[2]) }

    val currentArgb = remember(hue, saturation, value) {
        AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value))
    }
    val hexReadout = remember(currentArgb) {
        String.format(Locale.US, "#%06X", 0xFFFFFF and currentArgb)
    }

    Dialog(onDismissRequest = onCancel) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = vantaColors.surfaceCharcoal,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Custom accent colour",
                    color = vantaColors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Live colour preview and hexadecimal readout
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(currentArgb))
                            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                    )
                    Column {
                        Text(
                            text = hexReadout,
                            color = vantaColors.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Live colour preview",
                            color = vantaColors.textSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Rainbow Hue Bar + Slider
                Text(text = "Hue", color = vantaColors.textSecondary, fontSize = 12.sp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Red,
                                    Color.Yellow,
                                    Color.Green,
                                    Color.Cyan,
                                    Color.Blue,
                                    Color.Magenta,
                                    Color.Red
                                )
                            )
                        )
                )
                Slider(
                    value = hue,
                    onValueChange = { hue = it },
                    valueRange = 0f..360f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(currentArgb),
                        activeTrackColor = Color(currentArgb)
                    )
                )

                // Saturation Slider
                Text(text = "Saturation", color = vantaColors.textSecondary, fontSize = 12.sp)
                Slider(
                    value = saturation,
                    onValueChange = { saturation = it },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(currentArgb),
                        activeTrackColor = Color(currentArgb)
                    )
                )

                // Brightness / Value Slider
                Text(text = "Brightness", color = vantaColors.textSecondary, fontSize = 12.sp)
                Slider(
                    value = value,
                    onValueChange = { value = it },
                    valueRange = 0.15f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(currentArgb),
                        activeTrackColor = Color(currentArgb)
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = onCancel) {
                        Text("Cancel", color = vantaColors.textSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onSave(currentArgb) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(currentArgb),
                            contentColor = Color(0xFF0F0F0F)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Save", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun LauncherIconPreviewBadge(option: LauncherIconOption) {
    val bgColor = Color(option.bgHex)
    val fgColor = Color(option.fgHex)
    Canvas(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(bgColor)
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(9.dp))
    ) {
        val w = size.width
        val h = size.height
        when (option) {
            LauncherIconOption.VANTA -> {
                val path = Path().apply {
                    moveTo(w * 0.34f, h * 0.26f)
                    lineTo(w * 0.74f, h * 0.50f)
                    lineTo(w * 0.34f, h * 0.74f)
                    close()
                }
                drawPath(path, fgColor)
            }
            LauncherIconOption.FRAMES -> {
                drawRect(
                    color = fgColor,
                    topLeft = Offset(w * 0.25f, h * 0.25f),
                    size = androidx.compose.ui.geometry.Size(w * 0.5f, h * 0.5f),
                    style = Stroke(width = 3f)
                )
            }
            LauncherIconOption.PULSE -> {
                drawCircle(color = fgColor.copy(alpha = 0.35f), radius = w * 0.34f)
                drawCircle(color = fgColor, radius = w * 0.18f)
            }
            LauncherIconOption.ORBIT -> {
                drawCircle(color = fgColor, radius = w * 0.30f, style = Stroke(width = 2.8f))
                drawCircle(color = fgColor, radius = w * 0.10f)
            }
            LauncherIconOption.RIBBON -> {
                val path = Path().apply {
                    moveTo(w * 0.25f, h * 0.72f)
                    lineTo(w * 0.50f, h * 0.24f)
                    lineTo(w * 0.75f, h * 0.72f)
                }
                drawPath(path, fgColor, style = Stroke(width = 3.5f))
            }
            LauncherIconOption.MONO -> {
                drawCircle(color = fgColor, radius = w * 0.24f)
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    val vantaColors = LocalVantaColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(vantaColors.surfaceCharcoal)
            .padding(14.dp)
    ) {
        Text(
            text = title,
            color = vantaColors.accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun SelectableOptionPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val vantaColors = LocalVantaColors.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) vantaColors.accent.copy(alpha = 0.18f) else vantaColors.surfaceElevated
            )
            .border(
                width = 1.dp,
                color = if (selected) vantaColors.accent else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) vantaColors.accent else vantaColors.textPrimary,
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
    val vantaColors = LocalVantaColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = title,
                color = vantaColors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                color = vantaColors.textSecondary,
                fontSize = 11.sp
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF0F0F0F),
                checkedTrackColor = vantaColors.accent
            )
        )
    }
}

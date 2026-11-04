package com.localshorts.app.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localshorts.app.MainActivity
import com.localshorts.app.MainViewModel
import com.localshorts.app.data.ACCENTS
import com.localshorts.app.data.APP_ICONS
import com.localshorts.app.data.EndBehavior
import com.localshorts.app.data.SPEEDS
import com.localshorts.app.data.ThumbMode
import com.localshorts.app.data.TransitionSpeed
import com.localshorts.app.data.speedLabel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: MainViewModel, activity: MainActivity, onBack: () -> Unit) {
    val s by vm.settings.collectAsState()
    val unavailable by vm.unavailable.collectAsState()
    val thumbVersion by vm.thumbVersion.collectAsState()
    val accent = LocalAccent.current
    val context = LocalContext.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.addFolder(uri)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Hc.Bg)
            // swallow touches so the gallery underneath never receives them
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility),
    ) {
        Row(Modifier.padding(start = 4.dp, top = 4.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Hc.Ink) }
            Text("Settings", color = Hc.Ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        }
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
                .padding(start = 16.dp, end = 16.dp, bottom = 40.dp),
        ) {
            // ---------------- Appearance ----------------
            Section("Appearance") {
                Label("Accent colour")
                Row(Modifier.padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ACCENTS.forEach { (name, value) ->
                        val c = Color(value)
                        val on = s.accent == value
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .then(if (on) Modifier.border(2.dp, Hc.Ink, CircleShape) else Modifier)
                                .clickable { vm.setAccent(value) }
                                .padding(4.dp)
                                .clip(CircleShape)
                                .background(c),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (on) Icon(Icons.Default.Check, contentDescription = "$name, selected", tint = onAccent(c), modifier = Modifier.size(16.dp))
                        }
                    }
                }
                HsvPicker(color = accent, onCommit = { vm.setAccent(it.toArgb().toLong() and 0xFFFFFFFFL) })
                Spacer(Modifier.height(14.dp))
                Divider()
                Label("App icon", top = 14.dp)
                Row(Modifier.padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    APP_ICONS.forEach { icon ->
                        val on = s.icon == icon.id
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    if (!on) {
                                        vm.setIcon(icon.id)
                                        Toast.makeText(context, "Home screen icon changed to ${icon.label}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Hc.Bg)
                                    .then(if (on) Modifier.border(2.dp, accent, RoundedCornerShape(14.dp)) else Modifier),
                                contentAlignment = Alignment.Center,
                            ) {
                                Image(painterResource(logoRes(icon.id)), contentDescription = icon.label, modifier = Modifier.size(32.dp))
                            }
                            Text(icon.label, color = if (on) accent else Hc.Mute, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
                Hint("Changes the real launcher icon. Some launchers take a few seconds to update, and home screen shortcuts may need re-adding.")
                Divider()
                Label("Grid columns", top = 14.dp)
                Segmented(listOf("1", "2", "3", "4"), s.columns - 1, accent) { vm.setColumns(it + 1) }
                Spacer(Modifier.height(14.dp))
            }

            // ---------------- Thumbnails ----------------
            Section("Thumbnails") {
                val store = vm.thumbStore
                var mode by remember(thumbVersion) { mutableStateOf(store.mode) }
                var seconds by remember(thumbVersion) { mutableIntStateOf(store.fixedSeconds) }
                var keep by remember(thumbVersion) { mutableStateOf(store.keepOverrides) }
                Label("Default thumbnail frame")
                Segmented(listOf("Skip intro (auto)", "Fixed time"), if (mode == ThumbMode.FIXED) 1 else 0, accent) {
                    mode = if (it == 1) ThumbMode.FIXED else ThumbMode.AUTO
                }
                if (mode == ThumbMode.FIXED) {
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Time into each video", color = Hc.Ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        Stepper(value = seconds, suffix = " s", min = 0, max = 600) { seconds = it }
                    }
                } else {
                    Hint("Uses the frame 2 seconds after the first scene cut, so intros are skipped.", top = 10.dp)
                }
                ToggleRow("Keep individual thumbnails", keep, accent) { keep = it }
                PillButton("Apply to all videos", null, primary = true, accent = accent, modifier = Modifier.fillMaxWidth()) {
                    vm.applyGlobalThumb(mode, seconds, keep)
                    Toast.makeText(context, "Thumbnails updated", Toast.LENGTH_SHORT).show()
                }
                val custom = store.overrideCount()
                Hint(
                    (if (custom > 0) "$custom video${if (custom == 1) " has" else "s have"} a custom thumbnail. " else "") +
                        "To pick one video's thumbnail, pause it, tap ⋮, then Change thumbnail.",
                    top = 10.dp,
                )
            }

            // ---------------- Playback ----------------
            Section("Playback") {
                Label("When a video ends")
                Segmented(listOf("Loop", "Play next", "Stop"), EndBehavior.values().indexOf(s.end), accent) {
                    vm.setEnd(EndBehavior.values()[it])
                }
                Spacer(Modifier.height(14.dp))
                Divider()
                Label("Short transition speed", top = 14.dp)
                Segmented(TransitionSpeed.values().map { it.label }, TransitionSpeed.values().indexOf(s.transition), accent) {
                    vm.setTransition(TransitionSpeed.values()[it])
                }
                Hint("How the next video settles into place after you let go. Swiping always follows your finger.", top = 8.dp)
                Divider()
                Label("Playback speed", top = 14.dp)
                SPEEDS.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { v ->
                            val on = v == s.speed
                            Text(
                                speedLabel(v),
                                color = if (on) onAccent(accent) else Hc.Ink,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (on) accent else Hc.Surface2)
                                    .clickable { vm.setSpeed(v) }
                                    .padding(vertical = 10.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }

            // ---------------- Gestures ----------------
            Section("Gestures") {
                ToggleRow("Brightness (swipe on the left side)", s.brightnessGesture, accent) { vm.setBrightnessGesture(it) }
                Divider()
                ToggleRow("Volume (swipe on the right side)", s.volumeGesture, accent) { vm.setVolumeGesture(it) }
                Hint("Swiping in the middle always changes videos. A side that is turned off changes videos too.", top = 4.dp)
            }

            // ---------------- Library ----------------
            Section("Library") {
                if (s.folders.isEmpty()) {
                    Text("No folders yet.", color = Hc.Mute, fontSize = 15.sp, modifier = Modifier.padding(bottom = 6.dp))
                }
                FolderRow("All folders", null, active = s.activeFolder.isEmpty(), accent = accent, onSelect = { vm.setActiveFolder("") }, onRemove = null)
                s.folders.forEach { f ->
                    Divider()
                    FolderRow(
                        name = f.name,
                        note = if (f.uri in unavailable) "No access. Remove it and add it again." else null,
                        active = s.activeFolder == f.uri,
                        accent = accent,
                        onSelect = { vm.setActiveFolder(f.uri) },
                        onRemove = { vm.removeFolder(f.uri) },
                    )
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton("Add folder", Icons.Default.Add, primary = true, accent = accent, modifier = Modifier.weight(1f)) {
                        activity.suppressNextLock = true
                        pick.launch(null)
                    }
                    PillButton("Refresh", Icons.Default.Refresh, primary = false, accent = accent, modifier = Modifier.weight(1f)) { vm.refresh() }
                }
                Hint("Removing a folder only removes it from Hollow. Your videos stay where they are.")
            }

            // ---------------- Privacy ----------------
            Section("Privacy") {
                ToggleRow("Lock Hollow with your phone's screen lock", s.lock, accent) { want ->
                    if (want) {
                        if (!activity.canUseDeviceAuth()) {
                            Toast.makeText(context, "Set up a screen lock on your phone first", Toast.LENGTH_LONG).show()
                        } else {
                            activity.authenticate { ok -> if (ok) vm.setLock(true) }
                        }
                    } else {
                        activity.authenticate { ok -> if (ok) vm.setLock(false) }
                    }
                }
                Hint("Asks for your fingerprint, face or PIN when you open Hollow. Screenshots and screen recording stay allowed.", top = 4.dp)
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Text(title, color = Hc.Mute, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 4.dp, top = 22.dp, bottom = 8.dp))
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Hc.Surface)
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(14.dp))
        content()
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun Label(text: String, top: Dp = 0.dp) {
    Text(text, color = Hc.Ink, fontSize = 15.sp, modifier = Modifier.padding(top = top, bottom = 10.dp))
}

@Composable
private fun Hint(text: String, top: Dp = 0.dp) {
    Text(text, color = Hc.Mute, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = top, bottom = 12.dp))
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x14FFFFFF)))
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, accent: Color, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Hc.Ink, fontSize = 15.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = onAccent(accent),
                checkedTrackColor = accent,
                uncheckedThumbColor = Hc.Mute,
                uncheckedTrackColor = Hc.Surface2,
                uncheckedBorderColor = Hc.Surface2,
            ),
        )
    }
}

@Composable
private fun Stepper(value: Int, suffix: String, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepButton("−") { onChange((value - 1).coerceAtLeast(min)) }
        Text("$value$suffix", color = Hc.Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, modifier = Modifier.width(56.dp))
        StepButton("+") { onChange((value + 1).coerceAtMost(max)) }
    }
}

@Composable
private fun StepButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Hc.Surface2)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Hc.Ink, fontSize = 18.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Segmented(labels: List<String>, selected: Int, accent: Color, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Hc.Surface2)
            .padding(3.dp),
    ) {
        labels.forEachIndexed { i, l ->
            val on = i == selected
            Text(
                l,
                color = if (on) onAccent(accent) else Hc.Ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(17.dp))
                    .background(if (on) accent else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun FolderRow(
    name: String,
    note: String?,
    active: Boolean,
    accent: Color,
    onSelect: () -> Unit,
    onRemove: (() -> Unit)?,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(20.dp)
                .clip(CircleShape)
                .border(2.dp, if (active) accent else Hc.Mute, CircleShape)
                .padding(5.dp)
                .clip(CircleShape)
                .background(if (active) accent else Color.Transparent),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = Hc.Ink, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (note != null) Text(note, color = accent, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
        }
        if (onRemove != null) {
            Text(
                "Remove",
                color = Hc.Mute,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onRemove)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun PillButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    primary: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val fg = if (primary) onAccent(accent) else Hc.Ink
    Row(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(if (primary) accent else Hc.Surface2)
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

package com.localshorts.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localshorts.app.MainViewModel
import com.localshorts.app.data.ACCENTS
import com.localshorts.app.data.EndBehavior
import com.localshorts.app.data.SPEEDS
import com.localshorts.app.data.speedLabel

@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsState()
    val unavailable by vm.unavailable.collectAsState()
    val accent = LocalAccent.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.addFolder(uri)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Hc.Bg)
            // swallow touches so the gallery underneath never receives them
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .statusBarsPadding(),
    ) {
        Row(Modifier.padding(start = 4.dp, top = 4.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Hc.Ink) }
            Text("Settings", color = Hc.Ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        }
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 40.dp),
        ) {
            Section("Appearance") {
                Label("Accent colour")
                Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ACCENTS.forEach { (name, value) ->
                        val c = Color(value)
                        val on = s.accent == value
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .then(if (on) Modifier.border(2.dp, Hc.Ink, CircleShape) else Modifier)
                                .clickable { vm.setAccent(value) }
                                .padding(5.dp)
                                .clip(CircleShape)
                                .background(c),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (on) Icon(Icons.Default.Check, contentDescription = "$name, selected", tint = onAccent(c), modifier = Modifier.size(18.dp))
                        }
                    }
                }
                Divider()
                Label("Gallery columns", top = 14.dp)
                Segmented(listOf("1", "2", "3", "4"), s.columns - 1, accent) { vm.setColumns(it + 1) }
                Spacer(Modifier.height(14.dp))
            }

            Section("Playback") {
                Label("When a video ends")
                Segmented(listOf("Loop", "Play next", "Stop"), EndBehavior.values().indexOf(s.end), accent) {
                    vm.setEnd(EndBehavior.values()[it])
                }
                Spacer(Modifier.height(14.dp))
                Divider()
                Label("Default speed", top = 14.dp)
                SPEEDS.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                Spacer(Modifier.height(14.dp))
            }

            Section("Library") {
                if (s.folders.isEmpty()) {
                    Text("No folders yet.", color = Hc.Mute, fontSize = 15.sp, modifier = Modifier.padding(vertical = 14.dp))
                }
                FolderRow("All folders", null, active = s.activeFolder.isEmpty(), accent = accent, onSelect = { vm.setActiveFolder("") }, onRemove = null)
                s.folders.forEach { f ->
                    Divider()
                    FolderRow(
                        name = f.name,
                        note = if (f.uri in unavailable) "No access. Remove and add it again." else null,
                        active = s.activeFolder == f.uri,
                        accent = accent,
                        onSelect = { vm.setActiveFolder(f.uri) },
                        onRemove = { vm.removeFolder(f.uri) },
                    )
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton("Add folder", Icons.Default.Add, primary = true, accent = accent, modifier = Modifier.weight(1f)) { pick.launch(null) }
                    PillButton("Refresh", Icons.Default.Refresh, primary = false, accent = accent, modifier = Modifier.weight(1f)) { vm.refresh() }
                }
                Text(
                    "Removing a folder only removes it from Hollow. Your videos stay where they are.",
                    color = Hc.Mute,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(bottom = 14.dp),
                )
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
    }
}

@Composable
private fun Label(text: String, top: androidx.compose.ui.unit.Dp = 0.dp) {
    Text(text, color = Hc.Ink, fontSize = 15.sp, modifier = Modifier.padding(top = top, bottom = 10.dp))
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x14FFFFFF)))
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

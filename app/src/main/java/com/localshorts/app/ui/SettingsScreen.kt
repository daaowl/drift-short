package com.localshorts.app.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
import com.localshorts.app.data.InfoStyle
import com.localshorts.app.data.OpenStyle
import com.localshorts.app.data.SPEEDS
import com.localshorts.app.data.Settings
import com.localshorts.app.data.ThumbMode
import com.localshorts.app.data.TransitionSpeed
import com.localshorts.app.data.speedLabel
import kotlin.math.roundToInt

private enum class Page(val title: String, val glyph: Glyph) {
    APPEARANCE("Appearance", Glyph.PALETTE),
    PLAYBACK("Playback", Glyph.PLAY),
    THUMBNAILS("Thumbnails", Glyph.IMAGE),
    GESTURES("Gestures", Glyph.HAND),
    LIBRARY("Library", Glyph.FOLDER),
    PRIVACY("Privacy", Glyph.LOCK),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: MainViewModel, activity: MainActivity, onBack: () -> Unit) {
    val s by vm.settings.collectAsState()
    var page by rememberSaveable { mutableStateOf<Page?>(null) }
    BackHandler(enabled = page != null) { page = null }

    Column(
        Modifier
            .fillMaxSize()
            .background(Hc.Bg)
            // swallow touches so the gallery underneath never receives them
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp, end = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (page != null) page = null else onBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Hc.Ink)
            }
            Text(page?.title ?: "Settings", color = Hc.Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                val forward = targetState != null
                (slideInHorizontally(tween(220)) { if (forward) it / 4 else -it / 4 } + fadeIn(tween(220)))
                    .togetherWith(slideOutHorizontally(tween(180)) { if (forward) -it / 4 else it / 4 } + fadeOut(tween(160)))
            },
            label = "settings-page",
        ) { current ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            ) {
                when (current) {
                    null -> Home(s) { page = it }
                    Page.APPEARANCE -> Appearance(vm, s)
                    Page.PLAYBACK -> Playback(vm, s)
                    Page.THUMBNAILS -> Thumbnails(vm)
                    Page.GESTURES -> Gestures(vm, s)
                    Page.LIBRARY -> Library(vm, activity, s)
                    Page.PRIVACY -> Privacy(vm, activity, s)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- home

@Composable
private fun Home(s: Settings, open: (Page) -> Unit) {
    fun summary(p: Page) = when (p) {
        Page.APPEARANCE -> "${ACCENTS.firstOrNull { it.second == s.accent }?.first ?: "Custom"} accent · ${APP_ICONS.firstOrNull { it.id == s.icon }?.label ?: "Hollow"} icon · ${s.columns} columns"
        Page.PLAYBACK -> "${s.openStyle.label} opening · ${endLabel(s.end)} · ${s.transition.label} swipe"
        Page.THUMBNAILS -> "Default frame and custom thumbnails"
        Page.GESTURES -> listOfNotNull(if (s.brightnessGesture) "Brightness" else null, if (s.volumeGesture) "Volume" else null)
            .ifEmpty { listOf("Off") }.joinToString(" · ")
        Page.LIBRARY -> if (s.folders.isEmpty()) "No folders yet" else "${s.folders.size} folder${if (s.folders.size == 1) "" else "s"}"
        Page.PRIVACY -> if (s.lock) "Lock on" else "Lock off"
    }
    Group {
        listOf(Page.APPEARANCE, Page.PLAYBACK, Page.THUMBNAILS, Page.GESTURES).forEachIndexed { i, p ->
            if (i > 0) Divider(inset = 56.dp)
            NavRow(p.glyph, p.title, summary(p)) { open(p) }
        }
    }
    Spacer(Modifier.height(14.dp))
    Group {
        listOf(Page.LIBRARY, Page.PRIVACY).forEachIndexed { i, p ->
            if (i > 0) Divider(inset = 56.dp)
            NavRow(p.glyph, p.title, summary(p)) { open(p) }
        }
    }
}

private fun endLabel(e: EndBehavior) = when (e) {
    EndBehavior.LOOP -> "Loop"
    EndBehavior.NEXT -> "Play next"
    EndBehavior.STOP -> "Stop"
}

// ---------------------------------------------------------------- pages

@Composable
private fun Appearance(vm: MainViewModel, s: Settings) {
    val accent = LocalAccent.current
    val context = LocalContext.current
    var custom by remember { mutableStateOf(false) }

    Header("Accent colour")
    Group(padded = true) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            ACCENTS.forEach { (name, value) ->
                val c = Color(value)
                val on = s.accent == value
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .then(if (on) Modifier.border(2.dp, Hc.Ink, CircleShape) else Modifier)
                        .clickable { vm.setAccent(value) }
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(c),
                    contentAlignment = Alignment.Center,
                ) {
                    if (on) Icon(Icons.Default.Check, contentDescription = "$name, selected", tint = onAccent(c), modifier = Modifier.size(15.dp))
                }
            }
        }
        Divider()
        Row(
            Modifier.fillMaxWidth().clickable { custom = !custom }.padding(vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Custom colour", color = Hc.Ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text(if (custom) "Done" else "Edit", color = accent, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        AnimatedVisibility(custom, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(bottom = 12.dp)) {
                HsvPicker(color = accent, onCommit = { vm.setAccent(it.toArgb().toLong() and 0xFFFFFFFFL) })
            }
        }
    }

    Header("App icon")
    Group(padded = true) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            APP_ICONS.forEach { icon ->
                val on = s.icon == icon.id
                Column(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            if (!on) {
                                vm.setIcon(icon.id)
                                Toast.makeText(context, "Home screen icon changed to ${icon.label}", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(
                        painterResource(logoRes(icon.id)),
                        contentDescription = icon.label,
                        modifier = Modifier
                            .size(46.dp)
                            .then(if (on) Modifier.border(2.dp, accent, RoundedCornerShape(12.dp)) else Modifier)
                            .padding(if (on) 3.dp else 0.dp)
                            .clip(RoundedCornerShape(10.dp)),
                    )
                    Text(icon.label, color = if (on) accent else Hc.Mute, fontSize = 11.sp, modifier = Modifier.padding(top = 5.dp))
                }
            }
        }
    }
    Footnote("The home screen icon may take a few seconds to change.")

    Header("Gallery")
    Group(padded = true) {
        LabeledControl("Columns") {
            Segmented(listOf("1", "2", "3", "4"), s.columns - 1, accent) { vm.setColumns(it + 1) }
        }
    }
}

@Composable
private fun Playback(vm: MainViewModel, s: Settings) {
    val accent = LocalAccent.current

    Header("Opening animation")
    Group {
        OpenStyle.values().forEachIndexed { i, st ->
            if (i > 0) Divider(inset = 16.dp)
            ChoiceRow(st.label, st.summary, st == s.openStyle, accent) { vm.setOpenStyle(st) }
        }
    }
    Footnote("Closing a video plays the same animation in reverse.")

    Header("Player")
    Group(padded = true) {
        LabeledControl("When a video ends") {
            Segmented(listOf("Loop", "Play next", "Stop"), EndBehavior.values().indexOf(s.end), accent) {
                vm.setEnd(EndBehavior.values()[it])
            }
        }
        Divider()
        LabeledControl("Swipe settle speed") {
            Segmented(TransitionSpeed.values().map { it.label }, TransitionSpeed.values().indexOf(s.transition), accent) {
                vm.setTransition(TransitionSpeed.values()[it])
            }
        }
        Divider()
        LabeledControl("Default speed") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SPEEDS.chunked(5).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { v ->
                            val on = v == s.speed
                            Text(
                                speedLabel(v).replace("Normal", "1x"),
                                color = if (on) onAccent(accent) else Hc.Ink,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (on) accent else Hc.Surface2)
                                    .clickable { vm.setSpeed(v) }
                                    .padding(vertical = 9.dp),
                            )
                        }
                        repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }

    Header("Video details")
    Group(padded = true) {
        LabeledControl("Style") {
            Segmented(InfoStyle.values().map { it.label }, InfoStyle.values().indexOf(s.infoStyle), accent) {
                vm.setInfoStyle(InfoStyle.values()[it])
            }
        }
        Divider()
        SwitchRow("Show details and buttons", null, s.playerInfo, accent) { vm.setPlayerInfo(it) }
    }
    Footnote("Island keeps the title and details in a small card on screen. Classic shows them above the seek bar. The i button in the player toggles them.")
}

@Composable
private fun Thumbnails(vm: MainViewModel) {
    val accent = LocalAccent.current
    val context = LocalContext.current
    val version by vm.thumbVersion.collectAsState()
    val store = vm.thumbStore
    var mode by remember(version) { mutableStateOf(store.mode) }
    var seconds by remember(version) { mutableIntStateOf(store.fixedSeconds) }
    var keep by remember(version) { mutableStateOf(store.keepOverrides) }

    Header("Default frame")
    Group(padded = true) {
        LabeledControl("Use") {
            Segmented(listOf("Skip intro", "Fixed time"), if (mode == ThumbMode.FIXED) 1 else 0, accent) {
                mode = if (it == 1) ThumbMode.FIXED else ThumbMode.AUTO
            }
        }
        if (mode == ThumbMode.FIXED) {
            Divider()
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Seconds into each video", color = Hc.Ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
                SecondsField(seconds, accent) { seconds = it }
            }
            Slider(
                value = seconds.coerceAtMost(120).toFloat(),
                onValueChange = { seconds = it.roundToInt() },
                valueRange = 0f..120f,
                colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent, inactiveTrackColor = Hc.Surface2),
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        Divider()
        SwitchRow("Keep custom thumbnails", "Videos you picked a frame for stay as they are", keep, accent) { keep = it }
    }
    Footnote(
        if (mode == ThumbMode.FIXED) "Slide up to 2 minutes, or type any second up to 600."
        else "Uses the frame 2 seconds after the first scene cut, so intros are skipped.",
    )
    Spacer(Modifier.height(4.dp))
    PrimaryButton("Apply to all videos", accent) {
        vm.applyGlobalThumb(mode, seconds, keep)
        Toast.makeText(context, "Thumbnails updated", Toast.LENGTH_SHORT).show()
    }
    val custom = store.overrideCount()
    Footnote(
        (if (custom > 0) "$custom video${if (custom == 1) " has" else "s have"} a custom thumbnail. " else "") +
            "To set one, tap the image button in the player. For an exact frame, use ⋮ → Choose thumbnail frame.",
    )
}

@Composable
private fun Gestures(vm: MainViewModel, s: Settings) {
    val accent = LocalAccent.current
    Group {
        SwitchRow("Brightness", "Swipe up or down on the left side", s.brightnessGesture, accent, padded = true) { vm.setBrightnessGesture(it) }
        Divider(inset = 16.dp)
        SwitchRow("Volume", "Swipe up or down on the right side", s.volumeGesture, accent, padded = true) { vm.setVolumeGesture(it) }
    }
    Footnote("Swiping in the middle always changes videos. A side that's turned off changes videos too.")
}

@Composable
private fun Library(vm: MainViewModel, activity: MainActivity, s: Settings) {
    val accent = LocalAccent.current
    val unavailable by vm.unavailable.collectAsState()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.addFolder(uri)
    }
    Header("Show videos from")
    Group {
        FolderRow("All folders", null, active = s.activeFolder.isEmpty(), accent = accent, onSelect = { vm.setActiveFolder("") }, onRemove = null)
        s.folders.forEach { f ->
            Divider(inset = 50.dp)
            FolderRow(
                name = f.name,
                note = if (f.uri in unavailable) "No access. Remove it and add it again." else null,
                active = s.activeFolder == f.uri,
                accent = accent,
                onSelect = { vm.setActiveFolder(f.uri) },
                onRemove = { vm.removeFolder(f.uri) },
            )
        }
    }
    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PrimaryButton("Add folder", accent, Modifier.weight(1f)) {
            activity.suppressNextLock = true
            pick.launch(null)
        }
        SecondaryButton("Refresh", Modifier.weight(1f)) { vm.refresh() }
    }
    Footnote("Removing a folder only removes it from Hollow. Your videos stay where they are.")
}

@Composable
private fun Privacy(vm: MainViewModel, activity: MainActivity, s: Settings) {
    val accent = LocalAccent.current
    val context = LocalContext.current
    Group {
        SwitchRow("App lock", "Fingerprint, face or phone PIN", s.lock, accent, padded = true) { want ->
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
    }
    Footnote(
        "Hollow asks when it starts fresh: after you swipe it away, it's closed by the system, or the phone restarts. " +
            "Switching to another app and back doesn't ask again. Screenshots and screen recording stay allowed.",
    )
}

// ---------------------------------------------------------------- building blocks

@Composable
private fun Header(text: String) {
    Text(
        text.uppercase(),
        color = Hc.Mute,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.6.sp,
        modifier = Modifier.padding(start = 6.dp, top = 22.dp, bottom = 8.dp),
    )
}

@Composable
private fun Footnote(text: String) {
    Text(
        text,
        color = Hc.Mute,
        fontSize = 12.5.sp,
        lineHeight = 17.sp,
        modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 8.dp),
    )
}

@Composable
private fun Group(padded: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Hc.Surface)
            .then(if (padded) Modifier.padding(horizontal = 16.dp, vertical = 6.dp) else Modifier),
        content = content,
    )
}

@Composable
private fun Divider(inset: Dp = 0.dp) {
    Box(Modifier.fillMaxWidth().padding(start = inset).height(0.5.dp).background(Color(0x1FFFFFFF)))
}

@Composable
private fun NavRow(glyph: Glyph, title: String, summary: String, onClick: () -> Unit) {
    val accent = LocalAccent.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) { GlyphIcon(glyph, accent, size = 18.dp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Hc.Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(summary, color = Hc.Mute, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Hc.Mute, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun LabeledControl(label: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(label, color = Hc.Ink, fontSize = 15.sp, modifier = Modifier.padding(bottom = 10.dp))
        content()
    }
}

@Composable
private fun ChoiceRow(title: String, summary: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (selected) accent else Hc.Ink, fontSize = 15.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            Text(summary, color = Hc.Mute, fontSize = 12.5.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .size(20.dp)
                .clip(CircleShape)
                .border(2.dp, if (selected) accent else Hc.Mute, CircleShape)
                .padding(5.dp)
                .clip(CircleShape)
                .background(if (selected) accent else Color.Transparent),
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    summary: String?,
    checked: Boolean,
    accent: Color,
    padded: Boolean = false,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = if (padded) 16.dp else 0.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = Hc.Ink, fontSize = 15.sp)
            if (summary != null) Text(summary, color = Hc.Mute, fontSize = 12.5.sp, modifier = Modifier.padding(top = 2.dp))
        }
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
private fun Segmented(labels: List<String>, selected: Int, accent: Color, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Hc.Surface2)
            .padding(3.dp),
    ) {
        labels.forEachIndexed { i, l ->
            val on = i == selected
            Text(
                l,
                color = if (on) onAccent(accent) else Hc.Ink,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (on) accent else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 8.dp),
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
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(Glyph.FOLDER, if (active) accent else Hc.Mute, size = 20.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = if (active) accent else Hc.Ink, fontSize = 15.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (note != null) Text(note, color = accent, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
        }
        if (onRemove != null) {
            Text(
                "Remove",
                color = Hc.Mute,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onRemove)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        } else if (active) {
            Icon(Icons.Default.Check, contentDescription = "Selected", tint = accent, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun PrimaryButton(text: String, accent: Color, modifier: Modifier = Modifier.fillMaxWidth(), onClick: () -> Unit) {
    Text(
        text,
        color = onAccent(accent),
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(accent)
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
    )
}

@Composable
private fun SecondaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        text,
        color = Hc.Ink,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Hc.Surface)
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
    )
}

/** Typed seconds for the fixed thumbnail time. Accepts 0 to 600. */
@Composable
private fun SecondsField(seconds: Int, accent: Color, onChange: (Int) -> Unit) {
    var text by remember(seconds) { mutableStateOf(seconds.toString()) }
    Row(
        Modifier
            .width(80.dp)
            .height(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Hc.Surface2)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = text,
            onValueChange = { t ->
                val digits = t.filter { it.isDigit() }.take(3)
                text = digits
                digits.toIntOrNull()?.let { onChange(it.coerceIn(0, 600)) }
            },
            singleLine = true,
            textStyle = TextStyle(color = Hc.Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.End),
            cursorBrush = SolidColor(accent),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
        Text(" s", color = Hc.Mute, fontSize = 15.sp)
    }
}

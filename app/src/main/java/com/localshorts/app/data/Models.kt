package com.localshorts.app.data

import android.net.Uri

data class VideoItem(
    /** Stable id: hash of the document id and file size, so favourites survive reordering. */
    val id: String,
    val uri: Uri,
    val name: String,
    val modified: Long,
    val size: Long,
    val folder: String,
)

data class Folder(val uri: String, val name: String)

enum class EndBehavior { LOOP, NEXT, STOP }

enum class SortOrder(val label: String) {
    NEWEST("Newest"),
    OLDEST("Oldest"),
    NAME("Name"),
    MOST_WATCHED("Most watched"),
}

enum class TransitionSpeed(val label: String) { SLOW("Slow"), NORMAL("Normal"), FAST("Fast") }

/** How a video opens from the grid (and closes back into it). */
enum class OpenStyle(val label: String, val summary: String) {
    HOLLOW("Hollow", "Grows out of the tile and fades into the video mid-motion"),
    YOUTUBE("YouTube style", "Quick solid expand; the cover holds until the video starts"),
    INSTAGRAM("Instagram style", "Zooms in from the tile while fading in"),
    TIKTOK("TikTok style", "Slides up from the bottom of the screen"),
}

/** How the title and details sit on the player. */
enum class InfoStyle(val label: String) { CLASSIC("Classic"), ISLAND("Island") }

data class Settings(
    val columns: Int = 2,
    val end: EndBehavior = EndBehavior.LOOP,
    val speed: Float = 1f,
    val transition: TransitionSpeed = TransitionSpeed.NORMAL,
    val accent: Long = ACCENTS.first().second,
    val favouritesOnly: Boolean = false,
    val sort: SortOrder = SortOrder.NEWEST,
    val folders: List<Folder> = emptyList(),
    /** "" means all folders. */
    val activeFolder: String = "",
    val brightnessGesture: Boolean = true,
    val volumeGesture: Boolean = true,
    val icon: String = APP_ICONS.first().id,
    val lock: Boolean = false,
    val infoStyle: InfoStyle = InfoStyle.ISLAND,
    /** The player's "i" toggle: shows the action buttons and video details. */
    val playerInfo: Boolean = true,
    val openStyle: OpenStyle = OpenStyle.HOLLOW,
    val loaded: Boolean = false,
)

val ACCENTS: List<Pair<String, Long>> = listOf(
    "Red" to 0xFFE5484DL,
    "Ember" to 0xFFF2683CL,
    "Gold" to 0xFFE8B04BL,
    "Sage" to 0xFF7FB89AL,
    "Sky" to 0xFF5AA9E6L,
    "Violet" to 0xFF9B7BF0L,
)

/** Launcher icon choices. Each one is an activity-alias in the manifest. */
data class AppIcon(val id: String, val label: String, val alias: String, val drawableName: String)

val APP_ICONS: List<AppIcon> = listOf(
    AppIcon("hollow", "Hollow", "com.localshorts.app.IconHollow", "logo_hollow"),
    AppIcon("stack", "Stack", "com.localshorts.app.IconStack", "logo_stack"),
    AppIcon("orbit", "Orbit", "com.localshorts.app.IconOrbit", "logo_orbit"),
    AppIcon("mono", "Mono", "com.localshorts.app.IconMono", "logo_mono"),
    AppIcon("prism", "Prism", "com.localshorts.app.IconPrism", "logo_prism"),
)

val SPEEDS: List<Float> = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.25f)

fun speedLabel(s: Float): String {
    if (s == 1f) return "Normal"
    val t = String.format(java.util.Locale.US, "%.2f", s)
    return t.trimEnd('0').trimEnd('.') + "x"
}

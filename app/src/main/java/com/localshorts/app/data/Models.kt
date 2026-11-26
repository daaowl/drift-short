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
    HOLLOW("Hollow", "Grows out of the tile and flows into the video mid-motion"),
    YOUTUBE("YouTube", "Gallery slides down and fades while the video fades in, rebuilt from your recording"),
    INSTAGRAM("Instagram (iOS)", "Zooms out of the tile while the gallery recedes behind it"),
    TIKTOK("TikTok", "A quick, springy expand from the tile"),
}

/** Overall look: fonts, wordmark and filter tabs. CLASSIC is the original 1.0 header. */
enum class LookStyle(val label: String, val summary: String) {
    CLASSIC("Classic", "Serif wordmark, glass tabs with a sliding pill"),
    ROUNDED("Rounded", "Nunito, separate pill tabs"),
    CLEAN("Clean", "Outfit, text tabs with an underline"),
    BOLD("Bold", "Fredoka, white pill in a dark track"),
    SYSTEM("System", "Your phone's font, separate pill tabs"),
}

/** Full-screen gradient behind the app. */
enum class Backdrop(val label: String) {
    RAVE("Rave"),
    SUNSET("Sunset"),
    OCEAN("Ocean"),
    AURORA("Aurora"),
    EMBER("Ember"),
    NONE("None"),
}

/** Where and how the player reacts to taps and swipes. */
data class Gestures(
    /** Single tap pauses only inside the middle band; elsewhere it just shows the controls. */
    val pauseMiddleOnly: Boolean = true,
    /** Height of the middle pause band. */
    val pauseBand: Float = 0.5f,
    val doubleTapSkip: Boolean = true,
    val doubleTapFavourite: Boolean = true,
    /** Width of each side's double-tap skip zone. */
    val sideZone: Float = 0.3f,
    val skipBackS: Int = 5,
    val skipForwardS: Int = 5,
    /** Swipe up or down in the player to move to the next / previous video. */
    val swipeVideos: Boolean = true,
    val swipeSeek: Boolean = true,
    /** Height of the bottom area where a sideways swipe seeks. */
    val seekZone: Float = 0.5f,
    /** Seconds covered by a full-width swipe; 0 = the whole video. */
    val seekRangeS: Int = 60,
)

val SKIP_CHOICES = listOf(5, 10, 15, 30)
val SEEK_RANGES = listOf(30 to "30 s", 60 to "1 min", 120 to "2 min", 0 to "Whole video")

/** Shape of the grid tiles. */
enum class TileShape(val label: String, val ratio: Float) {
    /** Each tile takes its own video's shape; rows are packed so nothing leaves a gap. */
    AUTO("Auto", 0f),
    TALL("Tall", 9f / 16f),
    CLASSIC("Classic", 3f / 4f),
    SQUARE("Square", 1f),
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
    val tileShape: TileShape = TileShape.AUTO,
    /** Goat's idea: grid tiles play a short moving preview, and openings grow from its first frame. */
    val previews: Boolean = false,
    val look: LookStyle = LookStyle.CLASSIC,
    val backdrop: Backdrop = Backdrop.RAVE,
    /** Thin gaps between grid tiles (the 1.0 / 1.1 look). Off = tiles touch. */
    val tileGaps: Boolean = false,
    /** After a new sort/folder/filter: true jumps to the top; false glides along with the video on screen. */
    val jumpToTop: Boolean = false,
    /** Player gestures, all editable on the Gestures page. Zone sizes are fractions of the screen. */
    val gestures: Gestures = Gestures(),
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
    // Ids and alias names stay fixed across versions so an icon chosen earlier never disappears
    AppIcon("hollow", "Rave", "com.localshorts.app.IconHollow", "logo_hollow"),
    AppIcon("stack", "Sunset", "com.localshorts.app.IconStack", "logo_stack"),
    AppIcon("orbit", "Ocean", "com.localshorts.app.IconOrbit", "logo_orbit"),
    AppIcon("mono", "Noir", "com.localshorts.app.IconMono", "logo_mono"),
    AppIcon("prism", "Aurora", "com.localshorts.app.IconPrism", "logo_prism"),
    AppIcon("play", "Play", "com.localshorts.app.IconPlay", "logo_play"),
    AppIcon("noirplay", "Noir Play", "com.localshorts.app.IconNoirPlay", "logo_noirplay"),
)

val SPEEDS: List<Float> = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.25f)

fun speedLabel(s: Float): String {
    if (s == 1f) return "Normal"
    val t = String.format(java.util.Locale.US, "%.2f", s)
    return t.trimEnd('0').trimEnd('.') + "x"
}

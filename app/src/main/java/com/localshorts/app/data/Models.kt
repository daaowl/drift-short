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

enum class GalleryTab { LATEST, OLDEST, FAVOURITES }

data class Settings(
    val columns: Int = 2,
    val end: EndBehavior = EndBehavior.LOOP,
    val speed: Float = 1f,
    val accent: Long = ACCENTS.first().second,
    val tab: GalleryTab = GalleryTab.LATEST,
    val folders: List<Folder> = emptyList(),
    /** "" means all folders. */
    val activeFolder: String = "",
    val loaded: Boolean = false,
)

val ACCENTS: List<Pair<String, Long>> = listOf(
    "Red" to 0xFFE5484DL,
    "Sage" to 0xFF9DB4A5L,
    "Dusty blue" to 0xFF8EA4B8L,
    "Sand" to 0xFFC2B49AL,
    "Mauve" to 0xFFB39BA8L,
)

val SPEEDS: List<Float> = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.25f)

fun speedLabel(s: Float): String {
    if (s == 1f) return "Normal"
    val t = String.format(java.util.Locale.US, "%.2f", s)
    return t.trimEnd('0').trimEnd('.') + "x"
}

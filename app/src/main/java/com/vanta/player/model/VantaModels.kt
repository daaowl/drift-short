package com.vanta.player.model

import android.net.Uri
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class CollectionTab(val label: String) {
    LATEST("Latest"),
    OLDEST("Oldest"),
    FAVOURITES("Favourites")
}

enum class SortOption(val label: String) {
    NEWEST_FIRST("Newest first"),
    OLDEST_FIRST("Oldest first"),
    NAME_ASC("Name A–Z"),
    NAME_DESC("Name Z–A"),
    MOST_VIEWED("Most viewed")
}

enum class EndBehavior(val label: String) {
    LOOP("Loop this video"),
    PLAY_NEXT("Play next video"),
    STOP("Stop at the end")
}

enum class ShortTransitionSpeed(
    val label: String,
    val multiplier: Float
) {
    SLOW("Slow · cinematic", 1.35f),
    NORMAL("Normal · balanced", 1.0f),
    FAST("Fast · snappy", 0.72f)
}

enum class AccentPreset(val label: String, val colorHex: Int) {
    RED("Red", 0xFFEF454F.toInt()),
    GREEN("Green", 0xFF77BA8A.toInt()),
    BLUE("Blue", 0xFF69A5EE.toInt()),
    GOLD("Gold", 0xFFD6AE6C.toInt()),
    PURPLE("Purple", 0xFFBE87CB.toInt()),
    SILVER("Silver", 0xFFBCBCBC.toInt());

    val color: Color get() = Color(colorHex)
}

enum class LauncherIconOption(
    val id: String,
    val title: String,
    val subtitle: String,
    val aliasClassName: String,
    val bgHex: Int,
    val fgHex: Int
) {
    VANTA("vanta", "Vanta", "pearl & ember", "com.vanta.player.IconVantaAlias", 0xFF171314.toInt(), 0xFFEF454F.toInt()),
    FRAMES("frames", "Frames", "slate & ice", "com.vanta.player.IconFramesAlias", 0xFF121820.toInt(), 0xFF7AC6FF.toInt()),
    PULSE("pulse", "Pulse", "ink & violet", "com.vanta.player.IconPulseAlias", 0xFF14111E.toInt(), 0xFFBE87CB.toInt()),
    ORBIT("orbit", "Orbit", "charcoal & champagne", "com.vanta.player.IconOrbitAlias", 0xFF181614.toInt(), 0xFFD6AE6C.toInt()),
    RIBBON("ribbon", "Ribbon", "forest & sage", "com.vanta.player.IconRibbonAlias", 0xFF111A15.toInt(), 0xFF77BA8A.toInt()),
    MONO("mono", "Mono", "graphite & silver", "com.vanta.player.IconMonoAlias", 0xFF161616.toInt(), 0xFFD8D8D8.toInt())
}

data class FolderEntry(
    val uriString: String,
    val displayName: String,
    val displayPath: String,
    val addedTimestamp: Long,
    val isAvailable: Boolean = true
) {
    val uri: Uri get() = Uri.parse(uriString)
}

data class VideoItem(
    val stableId: String,
    val uriString: String,
    val fileName: String,
    val folderUriString: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val lastModifiedMs: Long,
    val isFavourite: Boolean = false,
    val watchCount: Int = 0,
    val customThumbnailMs: Long? = null,
    val effectiveThumbnailMs: Long = 1000L
) {
    val uri: Uri get() = Uri.parse(uriString)

    val titleWithoutExtension: String
        get() = fileName.substringBeforeLast('.', fileName)

    val aspectRatio: Float
        get() = if (width > 0 && height > 0) {
            (width.toFloat() / height.toFloat()).coerceIn(0.35f, 2.8f)
        } else {
            9f / 16f
        }

    val isPortrait: Boolean
        get() = height >= width

    val formattedDuration: String
        get() = formatTimestampMs(durationMs)

    val formattedSavedDate: String
        get() {
            if (lastModifiedMs <= 0L) return "Unknown date"
            return SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(lastModifiedMs))
        }

    val formattedWatchCount: String
        get() = formatCompactViews(watchCount)

    /**
     * Cache key includes video identity, modification time, and chosen frame timestamp
     * as required by Section 3 & Section 4.
     */
    val thumbnailCacheKey: String
        get() = "${stableId}_${lastModifiedMs}_${effectiveThumbnailMs}"
}

fun formatTimestampMs(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

fun formatCompactViews(count: Int): String {
    return when {
        count == 1 -> "1 view"
        count < 1_000 -> "$count views"
        count < 1_000_000 -> {
            val k = count / 1000.0
            val formatted = if (k >= 10.0 || count % 1000 == 0) {
                String.format(Locale.US, "%.0fK", k)
            } else {
                String.format(Locale.US, "%.1fK", k)
            }
            "$formatted views"
        }
        else -> {
            val m = count / 1_000_000.0
            val formatted = if (m >= 10.0 || count % 1_000_000 == 0) {
                String.format(Locale.US, "%.0fM", m)
            } else {
                String.format(Locale.US, "%.1fM", m)
            }
            "$formatted views"
        }
    }
}

data class AppSettings(
    val gridColumns: Int = 2,
    val selectedTab: CollectionTab = CollectionTab.LATEST,
    val sortOption: SortOption = SortOption.NEWEST_FIRST,
    val defaultPlaybackSpeed: Float = 1.0f,
    val endBehavior: EndBehavior = EndBehavior.LOOP,
    val shortTransitionSpeed: ShortTransitionSpeed = ShortTransitionSpeed.NORMAL,
    val accentColorArgb: Int = 0xFFEF454F.toInt(),
    val launcherIcon: LauncherIconOption = LauncherIconOption.VANTA,
    val defaultThumbnailSeconds: Float = 1.0f,
    val keepCustomFramesOnGlobalApply: Boolean = true,
    val gestureBrightnessEnabled: Boolean = true,
    val gestureVolumeEnabled: Boolean = true,
    val requirePhoneUnlock: Boolean = false,
    val activeFolderUri: String? = null,
    val savedFolders: List<FolderEntry> = emptyList()
) {
    val accentColor: Color get() = Color(accentColorArgb)

    val accentHexString: String
        get() = String.format(Locale.US, "#%06X", 0xFFFFFF and accentColorArgb)
}

enum class TransitionPhase {
    IDLE,
    OPENING,
    IN_PLAYER,
    CLOSING
}

data class OpeningTransitionState(
    val phase: TransitionPhase = TransitionPhase.IDLE,
    val targetVideo: VideoItem? = null,
    val startBounds: Rect? = null,
    val isFirstFrameReady: Boolean = false
)

val SUPPORTED_PLAYBACK_SPEEDS = listOf(
    0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.25f
)

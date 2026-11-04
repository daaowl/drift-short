package com.localshorts.app.model

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color

enum class SortTab(val label: String) {
   LATEST("Latest"),
   OLDEST("Oldest"),
   FAVOURITES("Favourites")
}

enum class EndBehavior(val label: String, val description: String) {
   LOOP("Loop", "Restart the same video continuously"),
   PLAY_NEXT("Play Next", "Advance automatically to the next video"),
   STOP("Stop", "Pause on the final frame without clearing")
}

enum class SwipeTransitionSpeed(
   val label: String,
   val settleDurationMs: Int,
   val dampingRatio: Float,
   val stiffness: Float
) {
   SLOW("Slow", settleDurationMs = 460, dampingRatio = 0.88f, stiffness = 240f),
   NORMAL("Normal", settleDurationMs = 270, dampingRatio = 0.86f, stiffness = 520f),
   FAST("Fast", settleDurationMs = 165, dampingRatio = 0.92f, stiffness = 980f)
}

enum class AccentPreset(
   val label: String,
   val primaryColor: Color,
   val secondaryColor: Color
) {
   VIOLET("Iris Violet", Color(0xFF9D7BFF), Color(0xFF6B4EFF)),
   EMBER("Warm Ember", Color(0xFFFF7A59), Color(0xFFD94E34)),
   MINT("Glacier Mint", Color(0xFF4EE0B8), Color(0xFF1FA683)),
   ICE("Frost Cyan", Color(0xFF5CC8FF), Color(0xFF2889CC)),
   MONO("Pure Monochrome", Color(0xFFEAEAEA), Color(0xFF9E9E9E))
}

enum class GradientPreset(
   val label: String,
   val topColor: Color,
   val bottomColor: Color
) {
   OBSIDIAN("Obsidian Void", Color(0xFF0A0A0E), Color(0xFF050507)),
   MIDNIGHT("Midnight Indigo", Color(0xFF10111C), Color(0xFF06070B)),
   CARBON("Carbon Slate", Color(0xFF131516), Color(0xFF08090A)),
   VELVET("Deep Velvet", Color(0xFF161019), Color(0xFF070509))
}

enum class IconStylePreset(val label: String) {
   MINIMAL("Minimal Pill"),
   SOFT_GLASS("Soft Glass"),
   OUTLINED("Crisp Outlined")
}

enum class MiddleGestureAction(val label: String) {
   DOUBLE_TAP_FAVOURITE("Double-tap to Favourite"),
   SHOW_VIDEO_INFO("Double-tap for Video Info"),
   DISABLED("Disabled (Play/Pause only)")
}

data class FolderEntry(
   val uriString: String,
   val displayName: String,
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
   val watchCount: Int = 0
) {
   val uri: Uri get() = Uri.parse(uriString)

   val aspectRatio: Float
       get() = if (width > 0 && height > 0) {
           (width.toFloat() / height.toFloat()).coerceIn(0.35f, 2.8f)
       } else {
           9f / 16f
       }

   val isPortrait: Boolean
       get() = height >= width

   val formattedDuration: String
       get() {
           val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
           val hours = totalSeconds / 3600L
           val minutes = (totalSeconds % 3600L) / 60L
           val seconds = totalSeconds % 60L
           return if (hours > 0) {
               String.format("%d:%02d:%02d", hours, minutes, seconds)
           } else {
               String.format("%d:%02d", minutes, seconds)
           }
       }
}

data class AppSettings(
   val gridColumns: Int = 3,
   val selectedTab: SortTab = SortTab.LATEST,
   val defaultPlaybackSpeed: Float = 1.0f,
   val endBehavior: EndBehavior = EndBehavior.LOOP,
   val swipeTransitionSpeed: SwipeTransitionSpeed = SwipeTransitionSpeed.NORMAL,
   val accentPreset: AccentPreset = AccentPreset.VIOLET,
   val gradientPreset: GradientPreset = GradientPreset.OBSIDIAN,
   val iconStylePreset: IconStylePreset = IconStylePreset.SOFT_GLASS,
   val gestureBrightnessEnabled: Boolean = true,
   val gestureVolumeEnabled: Boolean = true,
   val middleGestureAction: MiddleGestureAction = MiddleGestureAction.DOUBLE_TAP_FAVOURITE,
   val showVideoInfoOverlay: Boolean = false,
   val activeFolderUri: String? = null,
   val savedFolders: List<FolderEntry> = emptyList(),
   val lastSelectedVideoId: String? = null,
   val lastGalleryScrollIndex: Int = 0,
   val lastGalleryScrollOffset: Int = 0
)

data class ThumbnailBounds(
   val videoId: String,
   val rectInWindow: Rect
)

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
   val isFirstFrameReady: Boolean = false,
   val frameBitmap: Bitmap? = null
)

val SUPPORTED_PLAYBACK_SPEEDS = listOf(
   0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.25f
)

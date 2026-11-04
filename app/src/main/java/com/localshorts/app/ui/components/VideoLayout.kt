package com.localshorts.app.ui.components

/** Portrait videos up to this aspect ratio (w/h) fill the viewport by cropping; others are letterboxed. */
const val COVER_ASPECT_THRESHOLD = 0.62f

fun isCoverFit(aspect: Float): Boolean = aspect <= COVER_ASPECT_THRESHOLD

data class PlacedSize(val width: Float, val height: Float)

/**
 * Single source of truth for how a video is placed inside a viewport. Used by the live
 * TextureView, the retained thumbnail cover and the opening transition overlay so that the
 * three always line up pixel for pixel (no jump when the cover is removed).
 */
fun placedVideoSize(aspect: Float, viewportW: Float, viewportH: Float): PlacedSize {
    val safeAspect = aspect.coerceIn(0.2f, 5f)
    val viewportAspect = viewportW / viewportH.coerceAtLeast(1f)
    return if (isCoverFit(safeAspect)) {
        if (safeAspect > viewportAspect) PlacedSize(viewportH * safeAspect, viewportH)
        else PlacedSize(viewportW, viewportW / safeAspect)
    } else {
        if (safeAspect > viewportAspect) PlacedSize(viewportW, viewportW / safeAspect)
        else PlacedSize(viewportH * safeAspect, viewportH)
    }
}

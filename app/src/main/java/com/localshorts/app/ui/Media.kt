package com.localshorts.app.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import com.localshorts.app.data.ThumbnailLoader
import com.localshorts.app.data.VideoItem

@Composable
fun rememberCover(v: VideoItem, loader: ThumbnailLoader, thumbVersion: Int): Bitmap? {
    val key = loader.coverKey(v)
    val state = produceState(initialValue = loader.cachedCover(v), v.id, key, thumbVersion) {
        val cached = loader.cachedCover(v)
        if (cached != null) value = cached else loader.cover(v)?.let { value = it }
    }
    return state.value
}

fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

fun viewsLabel(n: Int): String = when {
    n == 1 -> "1 view"
    n < 1000 -> "$n views"
    n < 1_000_000 -> trim1(n / 1000.0) + "K views"
    else -> trim1(n / 1_000_000.0) + "M views"
}

private fun trim1(d: Double): String {
    val s = String.format(java.util.Locale.US, "%.1f", d)
    return if (s.endsWith(".0")) s.dropLast(2) else s
}

/**
 * Plays a preview strip (frames side by side) as a soft looping flipbook, cropped like the tile.
 * Each frame blends into the next, and the animation only redraws; it never recomposes.
 */
@Composable
fun PreviewFlipbook(strip: Bitmap, modifier: Modifier = Modifier) {
    val image = remember(strip) { strip.asImageBitmap() }
    val frameH = strip.height
    // The loader always writes exactly PREVIEW_FRAMES frames side by side
    val count = com.localshorts.app.data.PREVIEW_FRAMES
    val frameW = (strip.width / count).coerceAtLeast(1)
    val clock = rememberInfiniteTransition(label = "preview")
    val pos by clock.animateFloat(
        initialValue = 0f,
        targetValue = count.toFloat(),
        animationSpec = infiniteRepeatable(tween(durationMillis = (count * com.localshorts.app.data.PREVIEW_STEP_MS).toInt(), easing = LinearEasing)),
        label = "preview-frame",
    )
    androidx.compose.foundation.Canvas(modifier) {
        val k = pos.toInt().coerceIn(0, count - 1)
        val next = (k + 1) % count
        val blend = (pos - k).coerceIn(0f, 1f)
        // centre-crop each frame to the tile's shape
        val tileRatio = size.width / size.height
        val frameRatio = frameW.toFloat() / frameH
        val srcW: Int
        val srcH: Int
        if (frameRatio > tileRatio) {
            srcH = frameH
            srcW = (frameH * tileRatio).toInt().coerceIn(1, frameW)
        } else {
            srcW = frameW
            srcH = (frameW / tileRatio).toInt().coerceIn(1, frameH)
        }
        val dx = (frameW - srcW) / 2
        val dy = (frameH - srcH) / 2
        val dst = androidx.compose.ui.unit.IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1))
        drawImage(image, srcOffset = androidx.compose.ui.unit.IntOffset(k * frameW + dx, dy), srcSize = androidx.compose.ui.unit.IntSize(srcW, srcH), dstSize = dst)
        if (blend > 0.6f) {
            drawImage(
                image,
                srcOffset = androidx.compose.ui.unit.IntOffset(next * frameW + dx, dy),
                srcSize = androidx.compose.ui.unit.IntSize(srcW, srcH),
                dstSize = dst,
                alpha = ((blend - 0.6f) / 0.4f).coerceIn(0f, 1f),
            )
        }
    }
}

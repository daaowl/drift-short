package com.localshorts.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import java.io.File
import java.io.FileOutputStream

/**
 * Thumbnails and durations, decoded off the main thread, cached in memory and on disk.
 *
 * Two kinds of image per video:
 *  - cover: the gallery thumbnail, taken 2 s after the first hard scene cut so the intro is skipped
 *  - start frame: the frame at 0:00, shown in the player until the video's first frame renders
 */
class ThumbnailLoader(private val context: Context) {
    private val mem = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 6).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(2))
    private val inflight = HashMap<String, Deferred<Bitmap?>>()
    private val meta = context.getSharedPreferences("video_meta", Context.MODE_PRIVATE)
    private val dir = File(context.cacheDir, "thumbs").apply { mkdirs() }

    fun cachedCover(v: VideoItem): Bitmap? = mem.get("c_" + v.id)
    fun cachedStart(v: VideoItem): Bitmap? = mem.get("s_" + v.id)

    /** Duration in ms, or -1 if not known yet. Filled in when the cover is first made. */
    fun duration(v: VideoItem): Long = meta.getLong("d_" + v.id, -1L)

    suspend fun cover(v: VideoItem): Bitmap? = load("c_" + v.id) { makeCover(v) }
    suspend fun startFrame(v: VideoItem): Bitmap? = load("s_" + v.id) { makeStart(v) }

    private suspend fun load(key: String, make: () -> Bitmap?): Bitmap? {
        mem.get(key)?.let { return it }
        val job = synchronized(inflight) {
            inflight.getOrPut(key) {
                scope.async {
                    val b = try {
                        readDisk(key) ?: make()?.also { writeDisk(key, it) }
                    } catch (t: Throwable) {
                        null
                    }
                    if (b != null) mem.put(key, b)
                    synchronized(inflight) { inflight.remove(key) }
                    b
                }
            }
        }
        return job.await()
    }

    private fun readDisk(key: String): Bitmap? {
        val f = File(dir, "$key.jpg")
        if (!f.exists()) return null
        return BitmapFactory.decodeFile(f.path)
    }

    private fun writeDisk(key: String, b: Bitmap) {
        try {
            FileOutputStream(File(dir, "$key.jpg")).use { b.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        } catch (_: Exception) {
        }
    }

    private fun open(v: VideoItem) = MediaMetadataRetriever().apply { setDataSource(context, v.uri) }

    private fun makeStart(v: VideoItem): Bitmap? {
        val r = open(v)
        try {
            storeDuration(v, r)
            return frame(r, 0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 960)
        } finally {
            r.release()
        }
    }

    private fun makeCover(v: VideoItem): Bitmap? {
        val r = open(v)
        try {
            val dur = storeDuration(v, r)
            val cut = detectCut(r, dur)
            val at = if (cut >= 0) cut + 2000 else dur * 3 / 10
            val ms = at.coerceIn(0L, (dur - 200).coerceAtLeast(0L))
            return frame(r, ms, MediaMetadataRetriever.OPTION_CLOSEST, 720)
                ?: frame(r, ms, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 720)
        } finally {
            r.release()
        }
    }

    private fun storeDuration(v: VideoItem, r: MediaMetadataRetriever): Long {
        val d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        meta.edit().putLong("d_" + v.id, d).apply()
        return d
    }

    private fun frame(r: MediaMetadataRetriever, ms: Long, option: Int, maxSide: Int): Bitmap? {
        if (Build.VERSION.SDK_INT >= 27) {
            r.getScaledFrameAtTime(ms * 1000, option, maxSide, maxSide)?.let { return it }
        }
        val f = r.getFrameAtTime(ms * 1000, option) ?: return null
        val scale = minOf(1f, maxSide / maxOf(f.width, f.height).toFloat())
        if (scale >= 1f) return f
        val s = Bitmap.createScaledBitmap(f, (f.width * scale).toInt().coerceAtLeast(1), (f.height * scale).toInt().coerceAtLeast(1), true)
        f.recycle()
        return s
    }

    /** First hard scene cut in the first 40 s, in ms, or -1. */
    private fun detectCut(r: MediaMetadataRetriever, dur: Long): Long {
        var prev: IntArray? = null
        val end = minOf((dur - 500).coerceAtLeast(0L), 40_000L)
        var t = 0L
        while (t <= end) {
            val b = frame(r, t, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 96)
            if (b != null) {
                val cur = luma(b)
                b.recycle()
                val p = prev
                if (p != null) {
                    var sum = 0L
                    for (i in cur.indices) sum += kotlin.math.abs(cur[i] - p[i])
                    if (sum / cur.size > 28) return t
                }
                prev = cur
            }
            t += 1000
        }
        return -1
    }

    private fun luma(b: Bitmap): IntArray {
        val s = Bitmap.createScaledBitmap(b, 24, 32, true)
        val px = IntArray(24 * 32)
        s.getPixels(px, 0, 24, 0, 0, 24, 32)
        if (s !== b) s.recycle()
        return IntArray(px.size) { i ->
            val c = px[i]
            (299 * ((c shr 16) and 255) + 587 * ((c shr 8) and 255) + 114 * (c and 255)) / 1000
        }
    }
}

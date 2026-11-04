package com.localshorts.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.FileOutputStream

/**
 * Covers and start frames, decoded on a 2-thread background pool, cached in memory and on disk.
 *
 * - cover: the grid thumbnail, taken at the video's thumbnail timestamp (override, fixed, or auto)
 * - start frame: the frame at 0:00, shown in the player until real video frames render
 *
 * Requests nobody is waiting for any more (tile scrolled away) are cancelled before they decode.
 */
class ThumbnailLoader(private val context: Context, private val store: ThumbStore) {
    private val mem = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 5).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(2))

    private class Request(val job: Deferred<Bitmap?>) {
        var waiters = 0
    }

    private val inflight = HashMap<String, Request>()
    private val dir = File(context.cacheDir, "thumbs2").apply { mkdirs() }

    fun coverKey(v: VideoItem) = "c_${v.id}_${store.signature(v.id)}"
    fun cachedCover(v: VideoItem): Bitmap? = mem.get(coverKey(v))
    fun cachedStart(v: VideoItem): Bitmap? = mem.get("s_" + v.id)
    fun duration(v: VideoItem): Long = store.duration(v.id)

    suspend fun cover(v: VideoItem): Bitmap? {
        val key = coverKey(v)
        return load(key) { makeCover(v) }
    }

    suspend fun startFrame(v: VideoItem): Bitmap? = load("s_" + v.id) { makeStart(v) }

    /** Drops cached covers after the global rule changes. Start frames stay. */
    fun clearCovers() {
        mem.snapshot().keys.filter { it.startsWith("c_") }.forEach { mem.remove(it) }
        dir.listFiles()?.filter { it.name.startsWith("c_") }?.forEach { it.delete() }
    }

    private suspend fun load(key: String, make: suspend () -> Bitmap?): Bitmap? {
        mem.get(key)?.let { return it }
        val req = synchronized(inflight) {
            inflight.getOrPut(key) {
                lateinit var r: Request
                r = Request(scope.async(start = CoroutineStart.LAZY) {
                    val b = try {
                        readDisk(key) ?: make()?.also { writeDisk(key, it) }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (t: Throwable) {
                        null
                    }
                    if (b != null) mem.put(key, b)
                    synchronized(inflight) { if (inflight[key] === r) inflight.remove(key) }
                    b
                })
                r.job.start()
                r
            }.also { it.waiters++ }
        }
        try {
            return req.job.await()
        } finally {
            synchronized(inflight) {
                req.waiters--
                if (req.waiters <= 0 && !req.job.isCompleted) {
                    req.job.cancel()
                    if (inflight[key] === req) inflight.remove(key)
                }
            }
        }
    }

    private fun readDisk(key: String): Bitmap? {
        val f = File(dir, "$key.jpg")
        return if (f.exists()) BitmapFactory.decodeFile(f.path) else null
    }

    private fun writeDisk(key: String, b: Bitmap) {
        try {
            FileOutputStream(File(dir, "$key.jpg")).use { b.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        } catch (_: Exception) {
        }
    }

    private fun open(v: VideoItem) = MediaMetadataRetriever().apply { setDataSource(context, v.uri) }

    private fun duration(v: VideoItem, r: MediaMetadataRetriever): Long {
        val known = store.duration(v.id)
        if (known >= 0) return known
        val d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        store.setDuration(v.id, d)
        return d
    }

    private suspend fun makeStart(v: VideoItem): Bitmap? {
        val r = open(v)
        try {
            duration(v, r)
            currentCoroutineContext().ensureActive()
            return frame(r, 0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 1080)
        } finally {
            r.release()
        }
    }

    private suspend fun makeCover(v: VideoItem): Bitmap? {
        val r = open(v)
        try {
            val dur = duration(v, r)
            val o = store.override(v.id)
            val at = when {
                o >= 0 -> o
                store.mode == ThumbMode.FIXED -> store.fixedSeconds * 1000L
                else -> {
                    var cut = store.cut(v.id)
                    if (cut == -2L) {
                        cut = detectCut(r, dur)
                        store.setCut(v.id, cut)
                    }
                    if (cut >= 0) cut + 2000 else dur * 3 / 10
                }
            }
            val ms = at.coerceIn(0L, (dur - 200).coerceAtLeast(0L))
            currentCoroutineContext().ensureActive()
            return frame(r, ms, MediaMetadataRetriever.OPTION_CLOSEST, 720)
                ?: frame(r, ms, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 720)
        } finally {
            r.release()
        }
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
    private suspend fun detectCut(r: MediaMetadataRetriever, dur: Long): Long {
        var prev: IntArray? = null
        val end = minOf((dur - 500).coerceAtLeast(0L), 40_000L)
        var t = 0L
        while (t <= end) {
            currentCoroutineContext().ensureActive()
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

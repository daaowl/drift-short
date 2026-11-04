package com.vanta.player.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.LruCache
import com.vanta.player.model.VideoItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Implements Section 3 & Section 4 thumbnail requirements:
 * - Bounded 48 MiB in-memory LruCache
 * - Persistent disk cache bounded at 128 MiB and trimmed down toward 100 MiB
 * - Cache identity includes video identity, modification time, and chosen frame timestamp (`video.thumbnailCacheKey`)
 * - Request deduplication per cache key
 * - Direct custom frame preview extraction for the Custom Thumbnail Editor
 */
class ThumbnailCacheManager(private val context: Context) {

    companion object {
        private const val MEMORY_CACHE_KB = 48 * 1024 // 48 MiB
        private const val DISK_HIGH_WATERMARK_BYTES = 128L * 1024L * 1024L // 128 MiB
        private const val DISK_TARGET_BYTES = 100L * 1024L * 1024L // 100 MiB
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val decodeDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(3)

    private val diskCacheDir: File by lazy {
        File(context.cacheDir, "vanta_v119_thumbs").apply { mkdirs() }
    }

    private val memoryCache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(MEMORY_CACHE_KB) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return (value.allocationByteCount / 1024).coerceAtLeast(1)
        }
    }

    private val keyLocks = ConcurrentHashMap<String, Mutex>()
    private val writeCounter = AtomicInteger(0)

    fun getMemoryCached(video: VideoItem): Bitmap? {
        return memoryCache.get(video.thumbnailCacheKey)
    }

    suspend fun getOrDecodeThumbnail(
        video: VideoItem,
        targetWidthPx: Int = 540,
        targetHeightPx: Int = 960
    ): Bitmap? {
        val cacheKey = video.thumbnailCacheKey
        memoryCache.get(cacheKey)?.let { return it }

        val mutex = keyLocks.getOrPut(cacheKey) { Mutex() }
        return mutex.withLock {
            memoryCache.get(cacheKey)?.let { return@withLock it }

            withContext(decodeDispatcher) {
                val diskFile = getDiskFile(cacheKey)
                if (diskFile.exists() && diskFile.length() > 0L) {
                    val decoded = runCatching {
                        diskFile.setLastModified(System.currentTimeMillis())
                        BitmapFactory.decodeFile(diskFile.absolutePath)
                    }.getOrNull()
                    if (decoded != null) {
                        memoryCache.put(cacheKey, decoded)
                        return@withContext decoded
                    }
                }

                val clampedFrameMs = clampTimestampToDuration(
                    requestedMs = video.effectiveThumbnailMs,
                    durationMs = video.durationMs
                )
                val extracted = extractFrameAtMs(
                    video = video,
                    timestampMs = clampedFrameMs,
                    targetWidthPx = targetWidthPx,
                    targetHeightPx = targetHeightPx
                )
                if (extracted != null) {
                    memoryCache.put(cacheKey, extracted)
                    runCatching {
                        FileOutputStream(diskFile).use { out ->
                            extracted.compress(Bitmap.CompressFormat.JPEG, 86, out)
                        }
                        if (writeCounter.incrementAndGet() % 20 == 0) {
                            trimDiskCacheIfNeeded()
                        }
                    }
                }
                extracted
            }
        }
    }

    /**
     * Used by the Custom Thumbnail Editor slider release to render an arbitrary preview frame
     * off the UI thread without polluting the main cache until Save is pressed.
     */
    suspend fun extractPreviewFrame(
        video: VideoItem,
        requestedTimestampMs: Long,
        targetWidthPx: Int = 720,
        targetHeightPx: Int = 1280
    ): Bitmap? = withContext(decodeDispatcher) {
        val clampedMs = clampTimestampToDuration(requestedTimestampMs, video.durationMs)
        extractFrameAtMs(video, clampedMs, targetWidthPx, targetHeightPx)
    }

    fun clampTimestampToDuration(requestedMs: Long, durationMs: Long): Long {
        if (durationMs <= 0L) return requestedMs.coerceAtLeast(0L)
        val safeUpper = (durationMs - 80L).coerceAtLeast(0L)
        return requestedMs.coerceIn(0L, safeUpper)
    }

    private fun extractFrameAtMs(
        video: VideoItem,
        timestampMs: Long,
        targetWidthPx: Int,
        targetHeightPx: Int
    ): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, video.uri)
            val timeUs = timestampMs.coerceAtLeast(0L) * 1000L
            retriever.getScaledFrameAtTime(
                timeUs,
                MediaMetadataRetriever.OPTION_CLOSEST,
                targetWidthPx.coerceAtLeast(240),
                targetHeightPx.coerceAtLeast(360)
            ) ?: retriever.getScaledFrameAtTime(
                timeUs,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                targetWidthPx.coerceAtLeast(240),
                targetHeightPx.coerceAtLeast(360)
            ) ?: retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun trimDiskCacheIfNeeded() {
        runCatching {
            val files = diskCacheDir.listFiles() ?: return
            var totalBytes = files.sumOf { it.length() }
            if (totalBytes <= DISK_HIGH_WATERMARK_BYTES) return

            val sortedOldestFirst = files.sortedBy { it.lastModified() }
            for (f in sortedOldestFirst) {
                val len = f.length()
                if (f.delete()) {
                    totalBytes -= len
                }
                if (totalBytes <= DISK_TARGET_BYTES) break
            }
        }
    }

    private fun getDiskFile(cacheKey: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(cacheKey.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(diskCacheDir, "thumb_$digest.jpg")
    }
}

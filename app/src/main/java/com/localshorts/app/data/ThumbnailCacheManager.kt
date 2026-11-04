package com.localshorts.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.LruCache
import com.localshorts.app.model.VideoItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
* Provides zero-flicker synchronous memory-cache lookups and bounded off-main-thread
* disk + MediaMetadataRetriever frame extraction for both gallery cells and the
* thumbnail-to-fullscreen geometry transition overlay.
*/
class ThumbnailCacheManager(private val context: Context) {

   @OptIn(ExperimentalCoroutinesApi::class)
   private val decodeDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(3)

   private val diskCacheDir: File by lazy {
       File(context.cacheDir, "hollow_thumb_cache").apply { mkdirs() }
   }

   private val memoryCache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(
       (Runtime.getRuntime().maxMemory() / 1024 / 6).toInt().coerceAtLeast(16 * 1024)
   ) {
       override fun sizeOf(key: String, value: Bitmap): Int {
           return value.allocationByteCount / 1024
       }
   }

   private val keyLocks = ConcurrentHashMap<String, Mutex>()

   fun getMemoryCached(video: VideoItem): Bitmap? {
       return memoryCache.get(video.thumbnailKey)
   }

   suspend fun getOrDecodeThumbnail(
       video: VideoItem,
       maxDimensionPx: Int = 720
   ): Bitmap? {
       memoryCache.get(video.thumbnailKey)?.let { return it }

       val mutex = keyLocks.getOrPut(video.thumbnailKey) { Mutex() }
       return mutex.withLock {
           memoryCache.get(video.thumbnailKey)?.let { return@withLock it }

           withContext(decodeDispatcher) {
               val diskFile = getDiskFile(video.thumbnailKey)
               if (diskFile.exists() && diskFile.length() > 0L) {
                   val decoded = runCatching {
                       BitmapFactory.decodeFile(diskFile.absolutePath)
                   }.getOrNull()
                   if (decoded != null) {
                       memoryCache.put(video.thumbnailKey, decoded)
                       return@withContext decoded
                   }
               }

               val extracted = extractVideoFrame(video, maxDimensionPx)
               if (extracted != null) {
                   memoryCache.put(video.thumbnailKey, extracted)
                   runCatching {
                       FileOutputStream(diskFile).use { out ->
                           extracted.compress(Bitmap.CompressFormat.JPEG, 86, out)
                       }
                   }
               }
               extracted
           }
       }
   }

   private fun extractVideoFrame(
       video: VideoItem,
       maxDimensionPx: Int
   ): Bitmap? {
       val retriever = MediaMetadataRetriever()
       return try {
           retriever.setDataSource(context, video.uri)
           val customTimeMs = video.thumbnailTimeMs
           val frameTimeUs = customTimeMs?.let { it * 1000L }
               ?: if (video.durationMs > 1200L) 200_000L else 0L
           // A user-picked frame must be the exact frame, not the nearest keyframe.
           val seekOption = if (customTimeMs != null) {
               MediaMetadataRetriever.OPTION_CLOSEST
           } else {
               MediaMetadataRetriever.OPTION_CLOSEST_SYNC
           }
           // Keep the real aspect ratio: the player cover and transition overlay rely on it.
           val srcW = video.width.coerceAtLeast(1)
           val srcH = video.height.coerceAtLeast(1)
           val scale = minOf(1f, maxDimensionPx.toFloat() / maxOf(srcW, srcH).toFloat())
           val targetWidthPx = (srcW * scale).toInt().coerceAtLeast(2)
           val targetHeightPx = (srcH * scale).toInt().coerceAtLeast(2)
           val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
               retriever.getScaledFrameAtTime(
                   frameTimeUs,
                   seekOption,
                   targetWidthPx,
                   targetHeightPx
               ) ?: retriever.getFrameAtTime(frameTimeUs, seekOption)
           } else {
               retriever.getFrameAtTime(frameTimeUs, seekOption)
           }
           bitmap
       } catch (_: Exception) {
           null
       } finally {
           runCatching { retriever.release() }
       }
   }

   private fun getDiskFile(stableId: String): File {
       val digest = MessageDigest.getInstance("SHA-256")
           .digest(stableId.toByteArray())
           .joinToString("") { "%02x".format(it) }
       return File(diskCacheDir, "thumb_$digest.jpg")
   }
}

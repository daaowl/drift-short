package com.localshorts.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.localshorts.app.data.PreferencesStore
import com.localshorts.app.data.SafLibraryScanner
import com.localshorts.app.data.ThumbnailCacheManager
import java.io.File

class HollowApplication : Application(), ImageLoaderFactory {

   lateinit var preferencesStore: PreferencesStore
       private set

   lateinit var libraryScanner: SafLibraryScanner
       private set

   lateinit var thumbnailCacheManager: ThumbnailCacheManager
       private set

   override fun onCreate() {
       super.onCreate()
       preferencesStore = PreferencesStore(applicationContext)
       thumbnailCacheManager = ThumbnailCacheManager(applicationContext)
       libraryScanner = SafLibraryScanner(applicationContext)
   }

   override fun newImageLoader(): ImageLoader {
       return ImageLoader.Builder(this)
           .components {
               add(VideoFrameDecoder.Factory())
           }
           .memoryCache {
               MemoryCache.Builder(this)
                   .maxSizePercent(0.25)
                   .build()
           }
           .diskCache {
               DiskCache.Builder()
                   .directory(File(cacheDir, "coil_video_thumbs"))
                   .maxSizeBytes(128L * 1024L * 1024L) // 128 MB
                   .build()
           }
           .crossfade(false)
           .build()
   }
}

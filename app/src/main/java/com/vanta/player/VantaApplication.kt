package com.vanta.player

import android.app.Application
import com.vanta.player.data.PreferencesStore
import com.vanta.player.data.SafLibraryScanner
import com.vanta.player.data.ThumbnailCacheManager

class VantaApplication : Application() {

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
}

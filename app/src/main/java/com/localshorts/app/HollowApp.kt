package com.localshorts.app

import android.app.Application
import com.localshorts.app.data.LibraryRepository
import com.localshorts.app.data.SettingsRepository
import com.localshorts.app.data.ThumbStore
import com.localshorts.app.data.ThumbnailLoader
import com.localshorts.app.data.UserDataRepository

class HollowApp : Application() {
    lateinit var settings: SettingsRepository
        private set
    lateinit var userData: UserDataRepository
        private set
    lateinit var library: LibraryRepository
        private set
    lateinit var thumbStore: ThumbStore
        private set
    lateinit var thumbs: ThumbnailLoader
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsRepository(this)
        userData = UserDataRepository(this)
        library = LibraryRepository(this)
        thumbStore = ThumbStore(this)
        thumbs = ThumbnailLoader(this, thumbStore)
    }
}

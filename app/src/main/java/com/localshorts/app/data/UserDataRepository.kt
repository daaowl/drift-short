package com.localshorts.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.userStore by preferencesDataStore(name = "userdata")

/** Favourites and watch counts, keyed by stable video id. Never touches the video files. */
class UserDataRepository(private val context: Context) {
    private val kFavs = stringSetPreferencesKey("favourites")
    private val watchPrefix = "w_"

    val favourites: Flow<Set<String>> = context.userStore.data.map { it[kFavs] ?: emptySet() }

    val watchCounts: Flow<Map<String, Int>> = context.userStore.data.map { p ->
        p.asMap().mapNotNull { (k, v) ->
            if (k.name.startsWith(watchPrefix) && v is Int) k.name.removePrefix(watchPrefix) to v else null
        }.toMap()
    }

    suspend fun toggleFavourite(id: String) = context.userStore.edit { p ->
        val s = (p[kFavs] ?: emptySet()).toMutableSet()
        if (!s.add(id)) s.remove(id)
        p[kFavs] = s
    }

    suspend fun addWatch(id: String) = context.userStore.edit { p ->
        val k = intPreferencesKey(watchPrefix + id)
        p[k] = (p[k] ?: 0) + 1
    }
}

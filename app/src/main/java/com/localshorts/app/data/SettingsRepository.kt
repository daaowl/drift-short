package com.localshorts.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private val kCols = intPreferencesKey("columns")
    private val kEnd = stringPreferencesKey("end")
    private val kSpeed = floatPreferencesKey("speed")
    private val kTransition = stringPreferencesKey("transition")
    private val kAccent = longPreferencesKey("accent")
    private val kFavOnly = booleanPreferencesKey("favourites_only")
    private val kSort = stringPreferencesKey("sort")
    private val kFolders = stringPreferencesKey("folders")
    private val kActive = stringPreferencesKey("active_folder")
    private val kBright = booleanPreferencesKey("gesture_brightness")
    private val kVol = booleanPreferencesKey("gesture_volume")
    private val kIcon = stringPreferencesKey("icon")
    private val kLock = booleanPreferencesKey("lock")

    val flow: Flow<Settings> = context.settingsStore.data.map { p -> read(p) }

    private inline fun <reified T : Enum<T>> enumOf(s: String?, default: T): T =
        s?.let { v -> enumValues<T>().firstOrNull { it.name == v } } ?: default

    private fun read(p: Preferences) = Settings(
        columns = (p[kCols] ?: 2).coerceIn(1, 4),
        end = enumOf(p[kEnd], EndBehavior.LOOP),
        speed = (p[kSpeed] ?: 1f).coerceIn(0.25f, 2.25f),
        transition = enumOf(p[kTransition], TransitionSpeed.NORMAL),
        accent = p[kAccent] ?: ACCENTS.first().second,
        favouritesOnly = p[kFavOnly] ?: false,
        sort = enumOf(p[kSort], SortOrder.NEWEST),
        folders = decodeFolders(p[kFolders]),
        activeFolder = p[kActive] ?: "",
        brightnessGesture = p[kBright] ?: true,
        volumeGesture = p[kVol] ?: true,
        icon = p[kIcon] ?: APP_ICONS.first().id,
        lock = p[kLock] ?: false,
        loaded = true,
    )

    suspend fun setColumns(n: Int) = context.settingsStore.edit { it[kCols] = n.coerceIn(1, 4) }
    suspend fun setEnd(e: EndBehavior) = context.settingsStore.edit { it[kEnd] = e.name }
    suspend fun setSpeed(s: Float) = context.settingsStore.edit { it[kSpeed] = s }
    suspend fun setTransition(t: TransitionSpeed) = context.settingsStore.edit { it[kTransition] = t.name }
    suspend fun setAccent(c: Long) = context.settingsStore.edit { it[kAccent] = c }
    suspend fun setView(favouritesOnly: Boolean, sort: SortOrder) = context.settingsStore.edit {
        it[kFavOnly] = favouritesOnly
        it[kSort] = sort.name
    }
    suspend fun setActiveFolder(uri: String) = context.settingsStore.edit { it[kActive] = uri }
    suspend fun setBrightnessGesture(on: Boolean) = context.settingsStore.edit { it[kBright] = on }
    suspend fun setVolumeGesture(on: Boolean) = context.settingsStore.edit { it[kVol] = on }
    suspend fun setIcon(id: String) = context.settingsStore.edit { it[kIcon] = id }
    suspend fun setLock(on: Boolean) = context.settingsStore.edit { it[kLock] = on }

    suspend fun addFolder(f: Folder) = context.settingsStore.edit { p ->
        val list = decodeFolders(p[kFolders]).filterNot { it.uri == f.uri } + f
        p[kFolders] = encodeFolders(list)
        p[kActive] = f.uri
    }

    suspend fun removeFolder(uri: String) = context.settingsStore.edit { p ->
        p[kFolders] = encodeFolders(decodeFolders(p[kFolders]).filterNot { it.uri == uri })
        if (p[kActive] == uri) p[kActive] = ""
    }

    private fun encodeFolders(list: List<Folder>) = list.joinToString("\n") { it.uri + "\t" + it.name }

    private fun decodeFolders(s: String?): List<Folder> =
        s.orEmpty().split("\n").mapNotNull { line ->
            val parts = line.split("\t")
            if (parts.size == 2 && parts[0].isNotBlank()) Folder(parts[0], parts[1]) else null
        }
}

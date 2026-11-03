package com.localshorts.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
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
    private val kAccent = longPreferencesKey("accent")
    private val kTab = stringPreferencesKey("tab")
    private val kFolders = stringPreferencesKey("folders")
    private val kActive = stringPreferencesKey("active_folder")

    val flow: Flow<Settings> = context.settingsStore.data.map { p -> read(p) }

    private fun read(p: Preferences) = Settings(
        columns = (p[kCols] ?: 2).coerceIn(1, 4),
        end = p[kEnd]?.let { runCatching { EndBehavior.valueOf(it) }.getOrNull() } ?: EndBehavior.LOOP,
        speed = p[kSpeed] ?: 1f,
        accent = p[kAccent] ?: ACCENTS.first().second,
        tab = p[kTab]?.let { runCatching { GalleryTab.valueOf(it) }.getOrNull() } ?: GalleryTab.LATEST,
        folders = decodeFolders(p[kFolders]),
        activeFolder = p[kActive] ?: "",
        loaded = true,
    )

    suspend fun setColumns(n: Int) = context.settingsStore.edit { it[kCols] = n.coerceIn(1, 4) }
    suspend fun setEnd(e: EndBehavior) = context.settingsStore.edit { it[kEnd] = e.name }
    suspend fun setSpeed(s: Float) = context.settingsStore.edit { it[kSpeed] = s }
    suspend fun setAccent(c: Long) = context.settingsStore.edit { it[kAccent] = c }
    suspend fun setTab(t: GalleryTab) = context.settingsStore.edit { it[kTab] = t.name }
    suspend fun setActiveFolder(uri: String) = context.settingsStore.edit { it[kActive] = uri }

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

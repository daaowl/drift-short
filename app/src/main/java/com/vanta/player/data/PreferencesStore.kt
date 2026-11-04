package com.vanta.player.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vanta.player.model.AppSettings
import com.vanta.player.model.CollectionTab
import com.vanta.player.model.EndBehavior
import com.vanta.player.model.FolderEntry
import com.vanta.player.model.LauncherIconOption
import com.vanta.player.model.ShortTransitionSpeed
import com.vanta.player.model.SortOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "vanta_v119_prefs")

data class VideoUserStats(
    val favouriteIds: Set<String> = emptySet(),
    val watchCounts: Map<String, Int> = emptyMap(),
    val customThumbnailTimestampsMs: Map<String, Long> = emptyMap()
)

class PreferencesStore(private val context: Context) {

    private object Keys {
        val GRID_COLUMNS = intPreferencesKey("grid_columns")
        val SELECTED_TAB = stringPreferencesKey("selected_tab")
        val SORT_OPTION = stringPreferencesKey("sort_option")
        val DEFAULT_SPEED = floatPreferencesKey("default_playback_speed")
        val END_BEHAVIOR = stringPreferencesKey("end_behavior")
        val TRANSITION_SPEED = stringPreferencesKey("short_transition_speed")
        val ACCENT_COLOR_ARGB = intPreferencesKey("accent_color_argb")
        val LAUNCHER_ICON = stringPreferencesKey("launcher_icon")
        val DEFAULT_THUMB_SEC = floatPreferencesKey("default_thumb_seconds")
        val KEEP_CUSTOM_FRAMES = booleanPreferencesKey("keep_custom_frames")
        val GESTURE_BRIGHTNESS = booleanPreferencesKey("gesture_brightness")
        val GESTURE_VOLUME = booleanPreferencesKey("gesture_volume")
        val REQUIRE_PHONE_UNLOCK = booleanPreferencesKey("require_phone_unlock")
        val ACTIVE_FOLDER_URI = stringPreferencesKey("active_folder_uri")
        val SAVED_FOLDERS_JSON = stringPreferencesKey("saved_folders_json")
        val FAVOURITE_IDS = stringSetPreferencesKey("favourite_video_ids")
        val WATCH_COUNTS_JSON = stringPreferencesKey("watch_counts_json")
        val CUSTOM_THUMBS_JSON = stringPreferencesKey("custom_thumbs_json")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { prefs ->
            AppSettings(
                gridColumns = (prefs[Keys.GRID_COLUMNS] ?: 2).coerceIn(1, 4),
                selectedTab = runCatching {
                    CollectionTab.valueOf(prefs[Keys.SELECTED_TAB] ?: CollectionTab.LATEST.name)
                }.getOrDefault(CollectionTab.LATEST),
                sortOption = runCatching {
                    SortOption.valueOf(prefs[Keys.SORT_OPTION] ?: SortOption.NEWEST_FIRST.name)
                }.getOrDefault(SortOption.NEWEST_FIRST),
                defaultPlaybackSpeed = (prefs[Keys.DEFAULT_SPEED] ?: 1.0f).coerceIn(0.25f, 2.25f),
                endBehavior = runCatching {
                    EndBehavior.valueOf(prefs[Keys.END_BEHAVIOR] ?: EndBehavior.LOOP.name)
                }.getOrDefault(EndBehavior.LOOP),
                shortTransitionSpeed = runCatching {
                    ShortTransitionSpeed.valueOf(
                        prefs[Keys.TRANSITION_SPEED] ?: ShortTransitionSpeed.NORMAL.name
                    )
                }.getOrDefault(ShortTransitionSpeed.NORMAL),
                accentColorArgb = prefs[Keys.ACCENT_COLOR_ARGB] ?: 0xFFEF454F.toInt(),
                launcherIcon = runCatching {
                    LauncherIconOption.valueOf(
                        prefs[Keys.LAUNCHER_ICON] ?: LauncherIconOption.VANTA.name
                    )
                }.getOrDefault(LauncherIconOption.VANTA),
                defaultThumbnailSeconds = (prefs[Keys.DEFAULT_THUMB_SEC] ?: 1.0f).coerceAtLeast(0f),
                keepCustomFramesOnGlobalApply = prefs[Keys.KEEP_CUSTOM_FRAMES] ?: true,
                gestureBrightnessEnabled = prefs[Keys.GESTURE_BRIGHTNESS] ?: true,
                gestureVolumeEnabled = prefs[Keys.GESTURE_VOLUME] ?: true,
                requirePhoneUnlock = prefs[Keys.REQUIRE_PHONE_UNLOCK] ?: false,
                activeFolderUri = prefs[Keys.ACTIVE_FOLDER_URI],
                savedFolders = parseFoldersJson(prefs[Keys.SAVED_FOLDERS_JSON])
            )
        }

    val userStatsFlow: Flow<VideoUserStats> = context.dataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { prefs ->
            VideoUserStats(
                favouriteIds = prefs[Keys.FAVOURITE_IDS] ?: emptySet(),
                watchCounts = parseIntMapJson(prefs[Keys.WATCH_COUNTS_JSON]),
                customThumbnailTimestampsMs = parseLongMapJson(prefs[Keys.CUSTOM_THUMBS_JSON])
            )
        }

    suspend fun setGridColumns(columns: Int) {
        context.dataStore.edit { it[Keys.GRID_COLUMNS] = columns.coerceIn(1, 4) }
    }

    suspend fun setSelectedTabAndSort(tab: CollectionTab, sort: SortOption) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SELECTED_TAB] = tab.name
            prefs[Keys.SORT_OPTION] = sort.name
        }
    }

    suspend fun setSortOption(sort: SortOption) {
        context.dataStore.edit { it[Keys.SORT_OPTION] = sort.name }
    }

    suspend fun setDefaultPlaybackSpeed(speed: Float) {
        context.dataStore.edit { it[Keys.DEFAULT_SPEED] = speed.coerceIn(0.25f, 2.25f) }
    }

    suspend fun setEndBehavior(behavior: EndBehavior) {
        context.dataStore.edit { it[Keys.END_BEHAVIOR] = behavior.name }
    }

    suspend fun setShortTransitionSpeed(speed: ShortTransitionSpeed) {
        context.dataStore.edit { it[Keys.TRANSITION_SPEED] = speed.name }
    }

    suspend fun setAccentColorArgb(argb: Int) {
        context.dataStore.edit { it[Keys.ACCENT_COLOR_ARGB] = argb }
    }

    suspend fun setLauncherIcon(option: LauncherIconOption) {
        context.dataStore.edit { it[Keys.LAUNCHER_ICON] = option.name }
    }

    suspend fun setKeepCustomFrames(keep: Boolean) {
        context.dataStore.edit { it[Keys.KEEP_CUSTOM_FRAMES] = keep }
    }

    suspend fun applyGlobalDefaultThumbnailSeconds(seconds: Float, clearCustomOverrides: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DEFAULT_THUMB_SEC] = seconds.coerceAtLeast(0f)
            if (clearCustomOverrides) {
                prefs.remove(Keys.CUSTOM_THUMBS_JSON)
            }
        }
    }

    suspend fun setCustomThumbnailForVideo(stableVideoId: String, timestampMs: Long?) {
        context.dataStore.edit { prefs ->
            val current = parseLongMapJson(prefs[Keys.CUSTOM_THUMBS_JSON]).toMutableMap()
            if (timestampMs == null) {
                current.remove(stableVideoId)
            } else {
                current[stableVideoId] = timestampMs.coerceAtLeast(0L)
            }
            prefs[Keys.CUSTOM_THUMBS_JSON] = encodeLongMapJson(current)
        }
    }

    suspend fun setGestureBrightness(enabled: Boolean) {
        context.dataStore.edit { it[Keys.GESTURE_BRIGHTNESS] = enabled }
    }

    suspend fun setGestureVolume(enabled: Boolean) {
        context.dataStore.edit { it[Keys.GESTURE_VOLUME] = enabled }
    }

    suspend fun setRequirePhoneUnlock(enabled: Boolean) {
        context.dataStore.edit { it[Keys.REQUIRE_PHONE_UNLOCK] = enabled }
    }

    suspend fun addOrSelectFolder(uriString: String, displayName: String, displayPath: String) {
        context.dataStore.edit { prefs ->
            val current = parseFoldersJson(prefs[Keys.SAVED_FOLDERS_JSON]).toMutableList()
            val existingIndex = current.indexOfFirst { it.uriString == uriString }
            val entry = FolderEntry(
                uriString = uriString,
                displayName = displayName,
                displayPath = displayPath,
                addedTimestamp = System.currentTimeMillis(),
                isAvailable = true
            )
            if (existingIndex >= 0) {
                current[existingIndex] = entry
            } else {
                current.add(0, entry)
            }
            prefs[Keys.SAVED_FOLDERS_JSON] = encodeFoldersJson(current)
            prefs[Keys.ACTIVE_FOLDER_URI] = uriString
            prefs[Keys.SELECTED_TAB] = CollectionTab.LATEST.name
        }
    }

    suspend fun setActiveFolder(uriString: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ACTIVE_FOLDER_URI] = uriString
            prefs[Keys.SELECTED_TAB] = CollectionTab.LATEST.name
        }
    }

    suspend fun removeFolder(uriString: String) {
        context.dataStore.edit { prefs ->
            val updated = parseFoldersJson(prefs[Keys.SAVED_FOLDERS_JSON])
                .filterNot { it.uriString == uriString }
            prefs[Keys.SAVED_FOLDERS_JSON] = encodeFoldersJson(updated)
            if (prefs[Keys.ACTIVE_FOLDER_URI] == uriString) {
                val nextActive = updated.firstOrNull()?.uriString
                if (nextActive != null) {
                    prefs[Keys.ACTIVE_FOLDER_URI] = nextActive
                } else {
                    prefs.remove(Keys.ACTIVE_FOLDER_URI)
                }
            }
        }
    }

    suspend fun toggleFavourite(stableVideoId: String): Boolean {
        var isNowFav = false
        context.dataStore.edit { prefs ->
            val current = (prefs[Keys.FAVOURITE_IDS] ?: emptySet()).toMutableSet()
            if (current.contains(stableVideoId)) {
                current.remove(stableVideoId)
                isNowFav = false
            } else {
                current.add(stableVideoId)
                isNowFav = true
            }
            prefs[Keys.FAVOURITE_IDS] = current
        }
        return isNowFav
    }

    suspend fun incrementWatchCount(stableVideoId: String) {
        context.dataStore.edit { prefs ->
            val map = parseIntMapJson(prefs[Keys.WATCH_COUNTS_JSON]).toMutableMap()
            val nextCount = (map[stableVideoId] ?: 0) + 1
            map[stableVideoId] = nextCount
            prefs[Keys.WATCH_COUNTS_JSON] = encodeIntMapJson(map)
        }
    }

    private fun parseFoldersJson(json: String?): List<FolderEntry> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val uri = obj.getString("uri")
                    val name = obj.optString("name", "Folder")
                    add(
                        FolderEntry(
                            uriString = uri,
                            displayName = name,
                            displayPath = obj.optString("path", name),
                            addedTimestamp = obj.optLong("added", 0L),
                            isAvailable = true
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun encodeFoldersJson(folders: List<FolderEntry>): String {
        val arr = JSONArray()
        folders.forEach { folder ->
            val obj = JSONObject()
            obj.put("uri", folder.uriString)
            obj.put("name", folder.displayName)
            obj.put("path", folder.displayPath)
            obj.put("added", folder.addedTimestamp)
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun parseIntMapJson(json: String?): Map<String, Int> {
        if (json.isNullOrBlank()) return emptyMap()
        return runCatching {
            val obj = JSONObject(json)
            val result = mutableMapOf<String, Int>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                result[key] = obj.optInt(key, 0)
            }
            result
        }.getOrDefault(emptyMap())
    }

    private fun encodeIntMapJson(map: Map<String, Int>): String {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        return obj.toString()
    }

    private fun parseLongMapJson(json: String?): Map<String, Long> {
        if (json.isNullOrBlank()) return emptyMap()
        return runCatching {
            val obj = JSONObject(json)
            val result = mutableMapOf<String, Long>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                result[key] = obj.optLong(key, 0L)
            }
            result
        }.getOrDefault(emptyMap())
    }

    private fun encodeLongMapJson(map: Map<String, Long>): String {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        return obj.toString()
    }
}

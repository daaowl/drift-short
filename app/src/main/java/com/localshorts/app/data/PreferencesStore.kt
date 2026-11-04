package com.localshorts.app.data

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
import com.localshorts.app.model.AccentPreset
import com.localshorts.app.model.AppSettings
import com.localshorts.app.model.EndBehavior
import com.localshorts.app.model.FolderEntry
import com.localshorts.app.model.GradientPreset
import com.localshorts.app.model.IconStylePreset
import com.localshorts.app.model.MiddleGestureAction
import com.localshorts.app.model.SortTab
import com.localshorts.app.model.SwipeTransitionSpeed
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "hollow_prefs")

data class VideoUserStats(
   val favouriteIds: Set<String> = emptySet(),
   val watchCounts: Map<String, Int> = emptyMap()
)

class PreferencesStore(private val context: Context) {

   private object Keys {
       val GRID_COLUMNS = intPreferencesKey("grid_columns")
       val SELECTED_TAB = stringPreferencesKey("selected_tab")
       val DEFAULT_SPEED = floatPreferencesKey("default_playback_speed")
       val END_BEHAVIOR = stringPreferencesKey("end_behavior")
       val SWIPE_SPEED = stringPreferencesKey("swipe_transition_speed")
       val ACCENT_PRESET = stringPreferencesKey("accent_preset")
       val GRADIENT_PRESET = stringPreferencesKey("gradient_preset")
       val ICON_STYLE_PRESET = stringPreferencesKey("icon_style_preset")
       val GESTURE_BRIGHTNESS = booleanPreferencesKey("gesture_brightness")
       val GESTURE_VOLUME = booleanPreferencesKey("gesture_volume")
       val MIDDLE_GESTURE = stringPreferencesKey("middle_gesture_action")
       val SHOW_VIDEO_INFO = booleanPreferencesKey("show_video_info")
       val ACTIVE_FOLDER_URI = stringPreferencesKey("active_folder_uri")
       val SAVED_FOLDERS_JSON = stringPreferencesKey("saved_folders_json")
       val LAST_SELECTED_VIDEO_ID = stringPreferencesKey("last_selected_video_id")
       val LAST_SCROLL_INDEX = intPreferencesKey("last_scroll_index")
       val LAST_SCROLL_OFFSET = intPreferencesKey("last_scroll_offset")
       val FAVOURITE_IDS = stringSetPreferencesKey("favourite_video_ids")
       val WATCH_COUNTS_JSON = stringPreferencesKey("watch_counts_json")
   }

   val settingsFlow: Flow<AppSettings> = context.dataStore.data
       .catch { e ->
           if (e is IOException) emit(emptyPreferences()) else throw e
       }
       .map { prefs ->
           AppSettings(
               gridColumns = (prefs[Keys.GRID_COLUMNS] ?: 3).coerceIn(1, 4),
               selectedTab = runCatching {
                   SortTab.valueOf(prefs[Keys.SELECTED_TAB] ?: SortTab.LATEST.name)
               }.getOrDefault(SortTab.LATEST),
               defaultPlaybackSpeed = (prefs[Keys.DEFAULT_SPEED] ?: 1.0f).coerceIn(0.25f, 2.25f),
               endBehavior = runCatching {
                   EndBehavior.valueOf(prefs[Keys.END_BEHAVIOR] ?: EndBehavior.LOOP.name)
               }.getOrDefault(EndBehavior.LOOP),
               swipeTransitionSpeed = runCatching {
                   SwipeTransitionSpeed.valueOf(
                       prefs[Keys.SWIPE_SPEED] ?: SwipeTransitionSpeed.NORMAL.name
                   )
               }.getOrDefault(SwipeTransitionSpeed.NORMAL),
               accentPreset = runCatching {
                   AccentPreset.valueOf(prefs[Keys.ACCENT_PRESET] ?: AccentPreset.VIOLET.name)
               }.getOrDefault(AccentPreset.VIOLET),
               gradientPreset = runCatching {
                   GradientPreset.valueOf(
                       prefs[Keys.GRADIENT_PRESET] ?: GradientPreset.OBSIDIAN.name
                   )
               }.getOrDefault(GradientPreset.OBSIDIAN),
               iconStylePreset = runCatching {
                   IconStylePreset.valueOf(
                       prefs[Keys.ICON_STYLE_PRESET] ?: IconStylePreset.SOFT_GLASS.name
                   )
               }.getOrDefault(IconStylePreset.SOFT_GLASS),
               gestureBrightnessEnabled = prefs[Keys.GESTURE_BRIGHTNESS] ?: true,
               gestureVolumeEnabled = prefs[Keys.GESTURE_VOLUME] ?: true,
               middleGestureAction = runCatching {
                   MiddleGestureAction.valueOf(
                       prefs[Keys.MIDDLE_GESTURE] ?: MiddleGestureAction.DOUBLE_TAP_FAVOURITE.name
                   )
               }.getOrDefault(MiddleGestureAction.DOUBLE_TAP_FAVOURITE),
               showVideoInfoOverlay = prefs[Keys.SHOW_VIDEO_INFO] ?: false,
               activeFolderUri = prefs[Keys.ACTIVE_FOLDER_URI],
               savedFolders = parseFoldersJson(prefs[Keys.SAVED_FOLDERS_JSON]),
               lastSelectedVideoId = prefs[Keys.LAST_SELECTED_VIDEO_ID],
               lastGalleryScrollIndex = prefs[Keys.LAST_SCROLL_INDEX] ?: 0,
               lastGalleryScrollOffset = prefs[Keys.LAST_SCROLL_OFFSET] ?: 0
           )
       }

   val userStatsFlow: Flow<VideoUserStats> = context.dataStore.data
       .catch { e ->
           if (e is IOException) emit(emptyPreferences()) else throw e
       }
       .map { prefs ->
           VideoUserStats(
               favouriteIds = prefs[Keys.FAVOURITE_IDS] ?: emptySet(),
               watchCounts = parseWatchCountsJson(prefs[Keys.WATCH_COUNTS_JSON])
           )
       }

   suspend fun setGridColumns(columns: Int) {
       context.dataStore.edit { it[Keys.GRID_COLUMNS] = columns.coerceIn(1, 4) }
   }

   suspend fun setSelectedTab(tab: SortTab) {
       context.dataStore.edit { it[Keys.SELECTED_TAB] = tab.name }
   }

   suspend fun setDefaultPlaybackSpeed(speed: Float) {
       context.dataStore.edit { it[Keys.DEFAULT_SPEED] = speed.coerceIn(0.25f, 2.25f) }
   }

   suspend fun setEndBehavior(behavior: EndBehavior) {
       context.dataStore.edit { it[Keys.END_BEHAVIOR] = behavior.name }
   }

   suspend fun setSwipeTransitionSpeed(speed: SwipeTransitionSpeed) {
       context.dataStore.edit { it[Keys.SWIPE_SPEED] = speed.name }
   }

   suspend fun setAccentPreset(preset: AccentPreset) {
       context.dataStore.edit { it[Keys.ACCENT_PRESET] = preset.name }
   }

   suspend fun setGradientPreset(preset: GradientPreset) {
       context.dataStore.edit { it[Keys.GRADIENT_PRESET] = preset.name }
   }

   suspend fun setIconStylePreset(preset: IconStylePreset) {
       context.dataStore.edit { it[Keys.ICON_STYLE_PRESET] = preset.name }
   }

   suspend fun setGestureBrightness(enabled: Boolean) {
       context.dataStore.edit { it[Keys.GESTURE_BRIGHTNESS] = enabled }
   }

   suspend fun setGestureVolume(enabled: Boolean) {
       context.dataStore.edit { it[Keys.GESTURE_VOLUME] = enabled }
   }

   suspend fun setMiddleGestureAction(action: MiddleGestureAction) {
       context.dataStore.edit { it[Keys.MIDDLE_GESTURE] = action.name }
   }

   suspend fun setShowVideoInfoOverlay(show: Boolean) {
       context.dataStore.edit { it[Keys.SHOW_VIDEO_INFO] = show }
   }

   suspend fun saveScrollPosition(index: Int, offset: Int) {
       context.dataStore.edit { prefs ->
           prefs[Keys.LAST_SCROLL_INDEX] = index.coerceAtLeast(0)
           prefs[Keys.LAST_SCROLL_OFFSET] = offset.coerceAtLeast(0)
       }
   }

   suspend fun setLastSelectedVideoId(videoId: String?) {
       context.dataStore.edit { prefs ->
           if (videoId == null) {
               prefs.remove(Keys.LAST_SELECTED_VIDEO_ID)
           } else {
               prefs[Keys.LAST_SELECTED_VIDEO_ID] = videoId
           }
       }
   }

   suspend fun addOrSelectFolder(uriString: String, displayName: String) {
       context.dataStore.edit { prefs ->
           val current = parseFoldersJson(prefs[Keys.SAVED_FOLDERS_JSON]).toMutableList()
           val existingIndex = current.indexOfFirst { it.uriString == uriString }
           val entry = FolderEntry(
               uriString = uriString,
               displayName = displayName,
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
           prefs[Keys.LAST_SCROLL_INDEX] = 0
           prefs[Keys.LAST_SCROLL_OFFSET] = 0
       }
   }

   suspend fun setActiveFolder(uriString: String) {
       context.dataStore.edit { prefs ->
           prefs[Keys.ACTIVE_FOLDER_URI] = uriString
           prefs[Keys.LAST_SCROLL_INDEX] = 0
           prefs[Keys.LAST_SCROLL_OFFSET] = 0
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
           val map = parseWatchCountsJson(prefs[Keys.WATCH_COUNTS_JSON]).toMutableMap()
           val nextCount = (map[stableVideoId] ?: 0) + 1
           map[stableVideoId] = nextCount
           prefs[Keys.WATCH_COUNTS_JSON] = encodeWatchCountsJson(map)
       }
   }

   private fun parseFoldersJson(json: String?): List<FolderEntry> {
       if (json.isNullOrBlank()) return emptyList()
       return runCatching {
           val arr = JSONArray(json)
           buildList {
               for (i in 0 until arr.length()) {
                   val obj = arr.getJSONObject(i)
                   add(
                       FolderEntry(
                           uriString = obj.getString("uri"),
                           displayName = obj.optString("name", "Folder"),
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
           obj.put("added", folder.addedTimestamp)
           arr.put(obj)
       }
       return arr.toString()
   }

   private fun parseWatchCountsJson(json: String?): Map<String, Int> {
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

   private fun encodeWatchCountsJson(counts: Map<String, Int>): String {
       val obj = JSONObject()
       counts.forEach { (k, v) -> obj.put(k, v) }
       return obj.toString()
   }
}

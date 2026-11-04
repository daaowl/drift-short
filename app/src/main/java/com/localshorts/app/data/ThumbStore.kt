package com.localshorts.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThumbMode { AUTO, FIXED }

/**
 * Thumbnail timestamp choices. Kept in SharedPreferences so the background decoder can read them
 * synchronously. [version] changes whenever anything that affects a thumbnail changes.
 */
class ThumbStore(context: Context) {
    private val prefs = context.getSharedPreferences("thumbs", Context.MODE_PRIVATE)
    private val _version = MutableStateFlow(prefs.getInt("version", 0))
    val version: StateFlow<Int> = _version.asStateFlow()

    /** The applied global rule. AUTO = 2 s after the first hard scene cut, so intros are skipped. */
    val mode: ThumbMode get() = if (prefs.getString("mode", "AUTO") == "FIXED") ThumbMode.FIXED else ThumbMode.AUTO
    val fixedSeconds: Int get() = prefs.getInt("fixed_seconds", 3)
    val keepOverrides: Boolean get() = prefs.getBoolean("keep_overrides", true)

    fun override(id: String): Long = prefs.getLong("o_$id", -1L)
    fun hasOverride(id: String) = override(id) >= 0
    fun overrideCount(): Int = prefs.all.keys.count { it.startsWith("o_") }

    fun cut(id: String): Long = prefs.getLong("cut_$id", -2L)
    fun setCut(id: String, ms: Long) = prefs.edit().putLong("cut_$id", ms).apply()
    fun duration(id: String): Long = prefs.getLong("d_$id", -1L)
    fun setDuration(id: String, ms: Long) = prefs.edit().putLong("d_$id", ms).apply()

    /** A short tag describing which frame this video's cover should show. Part of the cache key. */
    fun signature(id: String): String {
        val o = override(id)
        return when {
            o >= 0 -> "o$o"
            mode == ThumbMode.FIXED -> "f$fixedSeconds"
            else -> "a"
        }
    }

    fun setOverride(id: String, ms: Long) {
        prefs.edit().putLong("o_$id", ms.coerceAtLeast(0)).apply()
        bump()
    }

    fun clearOverride(id: String) {
        prefs.edit().remove("o_$id").apply()
        bump()
    }

    /** Applies a new global rule to every video. Individual overrides survive when [keep] is true. */
    fun applyGlobal(mode: ThumbMode, seconds: Int, keep: Boolean) {
        val e = prefs.edit()
            .putString("mode", mode.name)
            .putInt("fixed_seconds", seconds.coerceIn(0, 600))
            .putBoolean("keep_overrides", keep)
        if (!keep) prefs.all.keys.filter { it.startsWith("o_") }.forEach { e.remove(it) }
        e.apply()
        bump()
    }

    private fun bump() {
        val v = _version.value + 1
        prefs.edit().putInt("version", v).apply()
        _version.value = v
    }
}

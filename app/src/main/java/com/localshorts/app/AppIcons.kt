package com.localshorts.app

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.localshorts.app.data.APP_ICONS

/** Switches the real launcher icon by enabling one activity-alias and disabling the rest. */
object AppIcons {
    fun apply(context: Context, id: String) {
        val pm = context.packageManager
        val target = APP_ICONS.firstOrNull { it.id == id } ?: APP_ICONS.first()
        // Enable the new one first so there is never a moment with no launcher entry
        pm.setComponentEnabledSetting(
            ComponentName(context.packageName, target.alias),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
        APP_ICONS.filter { it.id != target.id }.forEach {
            pm.setComponentEnabledSetting(
                ComponentName(context.packageName, it.alias),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
    }
}

package com.vanta.player.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.vanta.player.model.LauncherIconOption

/**
 * Implements Section 10:
 * Changes the actual Android launcher icon by enabling the chosen `<activity-alias>`
 * and disabling the other five so there are never duplicate enabled launcher entries.
 */
object LauncherIconManager {

    fun applyLauncherIcon(context: Context, selected: LauncherIconOption) {
        val pm = context.packageManager
        LauncherIconOption.entries.forEach { option ->
            val component = ComponentName(context.packageName, option.aliasClassName)
            val targetState = if (option == selected) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            runCatching {
                if (pm.getComponentEnabledSetting(component) != targetState) {
                    pm.setComponentEnabledSetting(
                        component,
                        targetState,
                        PackageManager.DONT_KILL_APP
                    )
                }
            }
        }
    }
}

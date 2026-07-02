/*
 *  Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.reviewreminders

import android.Manifest
import android.app.ActivityManager
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.core.content.getSystemService
import com.ichi2.utils.Permissions
import com.ichi2.utils.Permissions.arePermissionsDefinedInAnkiDroidManifest

/**
 * The battery optimization state applied to the app.
 *
 * @see ReminderTroubleshootingRepository.getBatteryOptimizationState
 */
enum class BatteryOptimizationState {
    /** App is exempt from battery optimization */
    Unrestricted,

    /** Default, may delay background work */
    Optimized,

    /** Background usage is disabled (API 28+) */
    Restricted,
}

class ReminderTroubleshootingRepository(
    private val context: Context,
) {
    fun isNotificationPermissionGranted(): Boolean = Permissions.canPostNotifications(context)

    fun isDoNotDisturbOff(): Boolean? {
        val notificationManager = context.getSystemService<NotificationManager>() ?: return null
        return notificationManager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL
    }

    /**
     * Returns the battery optimization state applied to the app.
     *
     * @see BatteryOptimizationState
     */
    fun getBatteryOptimizationState(): BatteryOptimizationState? {
        val powerManager = context.getSystemService<PowerManager>() ?: return null
        if (powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
            return BatteryOptimizationState.Unrestricted
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val activityManager = context.getSystemService<ActivityManager>() ?: return null
            if (activityManager.isBackgroundRestricted) {
                return BatteryOptimizationState.Restricted
            }
        }
        return BatteryOptimizationState.Optimized
    }

    fun isPowerSavingModeOff(): Boolean? {
        val powerManager = context.getSystemService<PowerManager>() ?: return null
        return !powerManager.isPowerSaveMode
    }

    fun isExactAlarmPermissionGranted(): Boolean? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        if (!context.arePermissionsDefinedInAnkiDroidManifest(Manifest.permission.SCHEDULE_EXACT_ALARM)) return null
        val alarmManager = context.getSystemService<AlarmManager>() ?: return null
        return alarmManager.canScheduleExactAlarms()
    }
}

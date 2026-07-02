/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.contextmenu

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.CheckResult
import timber.log.Timber
import java.lang.Exception

abstract class SystemContextMenu(
    private val context: Context,
) {
    /** We use an activity alias as the name so we can disable the context menu without disabling the activity  */
    protected abstract val activityName: String

    fun setSystemMenuEnabled(enabled: Boolean) {
        try {
            val enabledState =
                if (enabled) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                }
            packageManager.setComponentEnabledSetting(componentName, enabledState, PackageManager.DONT_KILL_APP)
        } catch (e: Exception) {
            Timber.w(e, "Failed to set Context Menu state")
        }
    }

    fun ensureConsistentStateWithPreferenceStatus(preferenceStatus: Boolean) {
        @SuppressLint("CheckResult")
        val actualStatus = systemMenuStatus
        if (actualStatus == null || actualStatus != preferenceStatus) {
            Timber.d("Modifying Context Menu Status: Preference was %b", preferenceStatus)
            setSystemMenuEnabled(preferenceStatus)
        }
    }

    @get:CheckResult
    private val systemMenuStatus: Boolean?
        get() =
            try {
                packageManager.getComponentEnabledSetting(componentName) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } catch (e: Exception) {
                Timber.w(e, "Failed to read context menu status setting")
                null
            }
    private val packageManager: PackageManager
        get() = context.packageManager

    // this can throw if context.getPackageName() throws
    @get:CheckResult
    private val componentName: ComponentName
        get() = ComponentName(context, activityName)
}

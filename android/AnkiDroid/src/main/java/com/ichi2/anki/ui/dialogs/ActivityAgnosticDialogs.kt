/*
 *  Copyright (c) 2023 Ashish Yadav <mailtoashish693@gmail.com>
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

package com.ichi2.anki.ui.dialogs

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceManager.getDefaultSharedPreferences
// **********************************************************************************************

/**
 * This allows showing a dialog in the currently started activity if one exists,
 * or, if the app is in background, scheduling it to be shown the next time any activity is started.
 */
class ActivityAgnosticDialogs private constructor(
    private val application: Application,
) {
    private val preferences = getDefaultSharedPreferences(application)

    private val startedActivityStack = mutableListOf<Activity>()

    private val currentlyStartedFragmentActivity get() =
        startedActivityStack
            .filterIsInstance<FragmentActivity>()
            .lastOrNull()

    private fun registerCallbacks() {
        application.registerActivityLifecycleCallbacks(
            object : DefaultActivityLifecycleCallbacks {
                override fun onActivityStarted(activity: Activity) {
                    startedActivityStack.add(activity)
                }

                override fun onActivityStopped(activity: Activity) {
                    startedActivityStack.remove(activity)
                }
            },
        )
    }

    companion object {
        fun register(application: Application) =
            ActivityAgnosticDialogs(application)
                .apply { registerCallbacks() }
    }
}

interface DefaultActivityLifecycleCallbacks : Application.ActivityLifecycleCallbacks {
    override fun onActivityCreated(
        activity: Activity,
        savedInstanceState: Bundle?,
    ) {}

    override fun onActivityStarted(activity: Activity) {}

    override fun onActivityResumed(activity: Activity) {}

    override fun onActivityPaused(activity: Activity) {}

    override fun onActivityStopped(activity: Activity) {}

    override fun onActivitySaveInstanceState(
        activity: Activity,
        outState: Bundle,
    ) {}

    override fun onActivityDestroyed(activity: Activity) {}
}

/*
 * Copyright (c) 2021 Shridhar Goel <shridhar.goel@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki

import android.app.Activity
import android.content.res.Configuration
import android.view.View
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.hamcrest.Matcher

object TestUtils {
    /**
     * Get instance of current activity
     */
    val activityInstance: Activity?
        get() {
            val activity = arrayOfNulls<Activity>(1)
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val resumedActivities: Collection<*> =
                    ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(
                        Stage.RESUMED,
                    )
                if (resumedActivities.iterator().hasNext()) {
                    val currentActivity = resumedActivities.iterator().next() as Activity
                    activity[0] = currentActivity
                }
            }
            return activity[0]
        }

    /**
     * Returns true if device is a tablet - tablet layout is in 'xlarge' values overlay,
     * so test for that screen layout in our resources configuration
     */
    val isTablet: Boolean
        get() =
            (
                activityInstance!!.resources.configuration.screenLayout and
                    Configuration.SCREENLAYOUT_SIZE_MASK
            ) ==
                Configuration.SCREENLAYOUT_SIZE_XLARGE

    /**
     * Click on a view using its ID inside a RecyclerView item
     */
    fun clickChildViewWithId(id: Int): ViewAction =
        object : ViewAction {
            override fun getConstraints(): Matcher<View>? = null

            override fun getDescription(): String = "Click on a child view with specified id."

            override fun perform(
                uiController: UiController,
                view: View,
            ) {
                val v = view.findViewById<View>(id)
                v.performClick()
            }
        }

    /** @return if the instrumented tests were built on a CI machine
     */
    fun wasBuiltOnCI(): Boolean {
        // DO NOT COPY THIS INTO AN CODE WHICH IS RELEASED PUBLICLY

        // We use BuildConfig as we couldn't detect an envvar after `adb root && adb shell setprop`. See: #9293
        // TODO: See if we can fix this to use an envvar, and rename to isCi().
        return BuildConfig.CI
    }
}

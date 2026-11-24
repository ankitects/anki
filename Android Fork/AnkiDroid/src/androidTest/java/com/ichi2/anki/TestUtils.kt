// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Shridhar Goel <shridhar.goel@gmail.com>

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

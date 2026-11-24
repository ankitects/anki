// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.test.core.app.ApplicationProvider
import org.junit.runners.model.MultipleFailureException
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowActivity
import org.robolectric.util.ReflectionHelpers

/**
 * Methods for interacting with Robolectric
 */
object Robolectric {
    /**
     * Allows a test activity to be launched under Robolectric
     * This is unusually difficult due to AGP not merging test manifests
     */
    inline fun <reified TestActivity : Activity> registerTestActivity() {
        // https://github.com/robolectric/robolectric/pull/4736
        val context: Context = ApplicationProvider.getApplicationContext()
        val activityInfo =
            ActivityInfo().apply {
                name = TestActivity::class.java.name
                packageName = context.packageName
            }
        shadowOf(context.packageManager).addOrUpdateActivity(activityInfo)
    }

    /**
     * Activities still registered with Robolectric's ActivityThread (`mActivities`).
     *
     * A retained AppCompat delegate can recreate its activity on a later night-mode change.
     * Removing a registration alone does not dispose that delegate.
     */
    fun liveActivities(): List<Activity> {
        // ActivityThread is not on the library compile classpath; use RuntimeEnvironment + reflection.
        val activityThread = RuntimeEnvironment.getActivityThread() ?: return emptyList()

        @Suppress("UNCHECKED_CAST")
        val records =
            ReflectionHelpers.getField<Map<*, *>>(activityThread, "mActivities") ?: return emptyList()
        return records.values.mapNotNull { record ->
            record ?: return@mapNotNull null
            ReflectionHelpers.getField<Activity?>(record, "activity")
        }
    }

    /**
     * Closes a controller, reporting lifecycle failures after removing its registrations.
     *
     * [ActivityController.close] follows the lifecycle and tolerates already-closed controllers.
     * It does nothing if `onCreate` never completed, so that case also needs explicit cleanup.
     */
    fun closeActivity(controller: ActivityController<*>) {
        val activity = controller.get()
        val closeFailure = runCatching { controller.close() }.exceptionOrNull()
        if (closeFailure == null && liveActivities().none { it === activity }) return

        val cleanupFailure = runCatching { disposeActivityRegistrations(activity) }.exceptionOrNull()
        MultipleFailureException.assertEmpty(listOfNotNull(closeFailure, cleanupFailure))
    }

    private fun disposeActivityRegistrations(activity: Activity) {
        try {
            // onDestroy may fail before AppCompatActivity reaches its delegate. AppCompat keeps
            // its own registry, independent of ActivityThread.mActivities.
            (activity as? AppCompatActivity)?.delegate?.onDestroy()
        } finally {
            forceRemoveActivity(activity)
        }
    }

    /** Removes [activity] from ActivityThread.mActivities if still present. */
    fun forceRemoveActivity(activity: Activity) {
        val activities = activityThreadActivities() ?: return
        val tokens =
            activities.entries
                .mapNotNull { (token, record) ->
                    val recorded =
                        try {
                            ReflectionHelpers.getField<Activity?>(record, "activity")
                        } catch (_: Exception) {
                            null
                        }
                    if (recorded === activity) token else null
                }
        tokens.forEach { activities.remove(it) }
    }

    /** Clears every ActivityThread.mActivities entry (last-resort isolation). */
    fun clearAllLiveActivities() {
        activityThreadActivities()?.clear()
    }

    private fun activityThreadActivities(): MutableMap<Any, Any>? {
        val activityThread = RuntimeEnvironment.getActivityThread() ?: return null
        val activitiesField =
            try {
                ReflectionHelpers.getField<Any?>(activityThread, "mActivities")
            } catch (_: Exception) {
                null
            } ?: return null
        @Suppress("UNCHECKED_CAST")
        return activitiesField as? MutableMap<Any, Any>
    }

    /**
     * Fails if any Robolectric-managed activities are still alive.
     * Destroys remnants before throwing so later tests stay isolated.
     */
    fun assertNoLiveActivities(whenWhat: String) {
        val live = liveActivities()
        if (live.isEmpty()) return
        val names = live.map { it.javaClass.name }
        val leak =
            AssertionError(
                "Robolectric leftover activities after $whenWhat: $names. " +
                    "Close ActivityControllers in the test that created them " +
                    "(saveControllerForCleanup / ActivityController.use / close). " +
                    "Retained AppCompat delegates can recreate activities on later night-mode changes.",
            )
        val failures = mutableListOf<Throwable>(leak)
        for (activity in live) {
            try {
                val shadow = Shadow.extract<ShadowActivity>(activity)
                val controller = ReflectionHelpers.getField<ActivityController<*>?>(shadow, "controller")
                if (controller != null) {
                    closeActivity(controller)
                } else {
                    disposeActivityRegistrations(activity)
                }
            } catch (failure: Throwable) {
                failures.add(failure)
            }
        }
        // Last resort: if reflection identity matching failed, drop the whole map.
        clearAllLiveActivities()
        MultipleFailureException.assertEmpty(failures)
    }
}

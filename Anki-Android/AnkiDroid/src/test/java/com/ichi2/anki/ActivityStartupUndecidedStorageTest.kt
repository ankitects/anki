// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import com.ichi2.anki.storage.StorageDecision
import com.ichi2.testutils.ActivityList
import com.ichi2.testutils.ActivityList.ActivityLaunchParam
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

/**
 * All activities start crash-free when [CollectionHelper.storageDecision] returns
 * [StorageDecision.Undecided].
 *
 * Unlike [ExternalEntryPointsUndecidedStorageTest], this covers in-app navigation and pinned
 * shortcuts.
 *
 * Collection-requiring activities are expected to finish via [ensureStorageIsReady][com.ichi2.anki.startup.ensureStorageIsReady].
 *
 * If this fails for a new activity, add the following after `super.onCreate`:
 * ```
 * if (!ensureStorageIsReady()) {
 *     return
 * }
 * ```
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
class ActivityStartupUndecidedStorageTest : RobolectricTest() {
    @ParameterizedRobolectricTestRunner.Parameter
    @JvmField // required for Parameter
    var launcher: ActivityLaunchParam? = null

    // Only used for display, but needs to be defined
    @ParameterizedRobolectricTestRunner.Parameter(1)
    @JvmField // required for Parameter
    @Suppress("unused")
    var activityName: String? = null

    @Before
    fun setStorageUndecided() {
        CollectionHelper.storageDecisionTestOverride = StorageDecision.Undecided
    }

    @After
    fun resetStorageDecision() {
        CollectionHelper.storageDecisionTestOverride = null
    }

    @Test
    fun `startup is crash-free when storage is undecided`() {
        val controller = launcher!!.build(targetContext)
        // mirrors Android: an activity which finishes during onCreate (e.g. redirectToMainEntryPoint)
        // does not receive the remaining lifecycle callbacks; controller.setup() would force them
        controller.create()
        if (!controller.get().isFinishing) {
            controller
                .start()
                .postCreate(null)
                .resume()
                .visible()
        }
        advanceRobolectricLooper()
    }

    companion object {
        @Suppress("unused")
        @ParameterizedRobolectricTestRunner.Parameters(name = "{1}")
        @JvmStatic // required for initParameters
        fun initParameters(): Collection<Array<Any>> = ActivityList.allActivitiesAndIntents().map { arrayOf(it, it.simpleName) }
    }
}

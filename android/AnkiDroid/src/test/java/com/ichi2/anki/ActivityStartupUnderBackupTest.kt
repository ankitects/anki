/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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
package com.ichi2.anki

import android.app.Activity
import android.os.Looper.getMainLooper
import com.ichi2.anki.instantnoteeditor.InstantNoteEditorActivity
import com.ichi2.anki.preferences.PreferencesActivity
import com.ichi2.testutils.ActivityList
import com.ichi2.testutils.ActivityList.ActivityLaunchParam
import com.ichi2.testutils.skipTest
import com.ichi2.utils.ExceptionUtil.getFullStackTrace
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import java.util.stream.Collectors

@RunWith(ParameterizedRobolectricTestRunner::class)
class ActivityStartupUnderBackupTest : RobolectricTest() {
    @ParameterizedRobolectricTestRunner.Parameter
    @JvmField // required for Parameter
    var launcher: ActivityLaunchParam? = null

    // Only used for display, but needs to be defined
    @ParameterizedRobolectricTestRunner.Parameter(1)
    @JvmField // required for Parameter
    @Suppress("unused")
    var activityName: String? = null

    @Before
    fun before() {
        notYetHandled(IntentHandler::class.java.simpleName, "Not working (or implemented) - inherits from Activity")
        notYetHandled(IntentHandler2::class.java.simpleName, "Not working (or implemented) - inherits from Activity")
        notYetHandled(
            PreferencesActivity::class.java.simpleName,
            "Not working (or implemented) - inherits from AppCompatPreferenceActivity",
        )
        notYetHandled(
            SingleFragmentActivity::class.java.simpleName,
            "Implemented, but the test fails because the activity throws if a specific intent extra isn't set",
        )
        notYetHandled(InstantNoteEditorActivity::class.java.simpleName, "Single instance activity so should be used")
    }

    /**
     * Tests that each activity can handle [AnkiDroidApp.getInstance] returning null
     * This happens during a backup, for details, see [AnkiActivity.showedActivityFailedScreen]
     *
     * Note: If you ran this test and it failed, please check to make sure that any new onCreate methods
     * have the following code snippet at the start:
     * `
     * if (showedActivityFailedScreen(savedInstanceState)) {
     * return;
     * }
     ` *
     */
    @Test
    fun activityHandlesRestoreBackup() {
        AnkiDroidApp.simulateRestoreFromBackup()
        val controller: ActivityController<out Activity?> =
            try {
                launcher!!.build(targetContext).create()
            } catch (npe: Exception) {
                val stackTrace = getFullStackTrace(npe)
                Assert.fail(
                    """If you ran this test and it failed, please check to make sure that any new onCreate methods
have the following code snippet at the start:
if (showedActivityFailedScreen(savedInstanceState)) {
  return;
}
$stackTrace""",
                )
                throw npe
            }
        shadowOf(getMainLooper()).idle()

        // Note: Robolectric differs from actual Android (process is not killed).
        // But we get the main idea: onCreate() doesn't throw an exception and is handled.
        // and onDestroy() is also called in the real implementation on my phone.
        assertThat("If a backup was taking place, the activity should be finishing", controller.get()!!.isFinishing, equalTo(true))
        controller.destroy()
        assertThat(
            "If a backup was taking place, the activity should be destroyed successfully",
            controller.get()!!.isDestroyed,
            equalTo(true),
        )
    }

    private fun notYetHandled(
        activityName: String,
        reason: String,
    ) {
        if (launcher!!.simpleName == activityName) {
            skipTest("$activityName $reason")
        }
    }

    companion object {
        @ParameterizedRobolectricTestRunner.Parameters(name = "{1}")
        @JvmStatic // required for initParameters
        fun initParameters(): Collection<Array<Any>> =
            ActivityList
                .allActivitiesAndIntents()
                .stream()
                .map { x: ActivityLaunchParam ->
                    arrayOf(x, x.simpleName)
                }.collect(Collectors.toList())
    }
}

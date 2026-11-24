// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.app.Activity
import android.content.Intent
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
import org.robolectric.shadows.ShadowToast
import java.util.stream.Collectors
import kotlin.test.assertNull

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
        if (controller.get() is AbstractIntentHandler) {
            assertNull(shadowOf(controller.get()).nextStartedActivity, "The intent must not be dispatched during backup recovery")
            assertThat(
                ShadowToast.getTextOfLatestToast(),
                equalTo(targetContext.getString(CommonString.ankidroid_cannot_open_after_backup_try_again)),
            )
        }
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
                }.collect(Collectors.toList()) +
                listOf(Intent.ACTION_MAIN, "com.ichi2.anki.DO_SYNC").map { action ->
                    arrayOf(
                        ActivityLaunchParam.get(IntentHandler::class.java) { Intent(action) },
                        "IntentHandler $action",
                    )
                }
    }
}

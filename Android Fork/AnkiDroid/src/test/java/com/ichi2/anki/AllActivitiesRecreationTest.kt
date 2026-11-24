// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.os.Build
import android.os.Bundle
import androidx.core.content.edit
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterValuesProvider
import com.ichi2.anki.account.AccountActivity
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.multimedia.MultimediaActivity
import com.ichi2.anki.preferences.PreferencesActivity
import com.ichi2.anki.previewer.CardViewerActivity
import com.ichi2.anki.ui.windows.permissions.PermissionsActivity
import com.ichi2.anki.utils.ConfigAwareSingleFragmentActivity
import com.ichi2.testutils.ActivityList
import com.ichi2.testutils.ActivityList.ActivityLaunchParam
import com.ichi2.testutils.grantWritePermissions
import com.ichi2.testutils.skipTest
import com.ichi2.widget.cardanalysis.CardAnalysisWidgetConfig
import com.ichi2.widget.deckpicker.DeckPickerWidgetConfig
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestParameterInjector
import org.robolectric.android.controller.ActivityController

/**
 * Tests "Don't keep activities" for all activities in the manifest.
 *
 * @see ActivityList.allActivitiesAndIntents
 */
@RunWith(RobolectricTestParameterInjector::class)
class AllActivitiesRecreationTest : RobolectricTest() {
    @TestParameter(valuesProvider = ActivityLauncherProvider::class)
    lateinit var config: ActivityConfig

    private val launcher: ActivityLaunchParam get() = config.launcher

    @Before
    fun startAsAnExistingUser() {
        grantWritePermissions()
        targetContext.sharedPrefs().edit { putBoolean(IntroductionActivity.PREF_INTRODUCTION_SLIDES_SHOWN, true) }
    }

    @Before
    fun skipUnhandledActivities() {
        // Same exclusions as ActivityStartupUnderBackupTest - onCreate failure.
        notYetHandled(IntentHandler::class.java.simpleName, "Not working (or implemented) - inherits from Activity")
        notYetHandled(IntentHandler2::class.java.simpleName, "Not working (or implemented) - inherits from Activity")
        notYetHandled(
            SingleFragmentActivity::class.java.simpleName,
            "Implemented, but the test fails because the activity throws if a specific intent extra isn't set",
        )

        // TODO: split these into per-class recreation tests which pass 'fragmentName'.
        for (activity in fragmentHosts) {
            notYetHandled(activity.simpleName, "Needs 'fragmentName' intent extra")
        }
    }

    @Test
    fun `activity is recreated from its saved state`() {
        ensureCollectionLoadIsSynchronous()
        val controller = launcher.build(targetContext)
        saveControllerForCleanup(controller)

        controller.create()
        // mirrors Android: an activity which finishes during onCreate (e.g. redirectToMainEntryPoint)
        // does not receive the remaining lifecycle callbacks
        if (!controller.get().isFinishing) {
            controller
                .start()
                .postCreate(null)
                .resume()
                .visible()
        }
        advanceRobolectricLooper()

        // the system never recreates an activity which finished on the way up, so there is
        // nothing to test. Assert on it, or a screen could silently stop being covered
        if (controller.get().isFinishing) {
            assertThat(
                "${launcher.simpleName} finished during startup, so its recreation is untested." +
                    " If this is expected, add it to finishesDuringStartup",
                finishesDuringStartup.contains(launcher.activity),
                equalTo(true),
            )
            return
        }

        // controller.recreate() retains ViewModels for a configuration change. "Don't keep
        // activities" discards them, so restore a fresh controller from the saved state instead.
        val originalActivity = controller.get()
        val savedState = Bundle()
        controller.pause()
        controller.saveInstanceStateCompat(savedState)
        controller.destroy()
        assertThat(
            "Don't keep activities should destroy without a configuration change",
            originalActivity.isChangingConfigurations,
            equalTo(false),
        )

        val restoredController = launcher.build(targetContext)
        saveControllerForCleanup(restoredController)
        restoredController.setup(savedState)
        advanceRobolectricLooper()

        assertThat(
            "the recreated activity should not finish",
            restoredController.get().isFinishing,
            equalTo(false),
        )
    }

    /** Stops the activity and saves its state in the order used by the current Android version. */
    private fun ActivityController<*>.saveInstanceStateCompat(savedState: Bundle) {
        // Android P and later save instance state after onStop; earlier versions save before it.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            stop().saveInstanceState(savedState)
        } else {
            saveInstanceState(savedState).stop()
        }
    }

    private fun notYetHandled(
        activityName: String,
        reason: String,
    ) {
        if (launcher.simpleName == activityName) {
            skipTest("$activityName $reason")
        }
    }

    /** Wraps the launcher so JUnit formats the test name correctly */
    class ActivityConfig(
        val launcher: ActivityLaunchParam,
    ) {
        override fun toString(): String = launcher.simpleName
    }

    class ActivityLauncherProvider : TestParameterValuesProvider() {
        override fun provideValues(context: Context?): List<ActivityConfig> =
            ActivityList
                .allActivitiesAndIntents()
                .filterNot { handled.contains(it.activity) }
                .map { ActivityConfig(it) }
    }

    companion object {
        /**
         * Activities which finish on the way up given what this test provides: an empty
         * collection, no card to review, no notetype and no widget id.
         */
        private val finishesDuringStartup =
            listOf(
                CardAnalysisWidgetConfig::class.java,
                DeckPickerWidgetConfig::class.java,
                NoteTypeFieldEditor::class.java,
                PermissionsActivity::class.java,
                Reviewer::class.java,
            )

        /**
         * Activities with their own recreation test, so are excluded from the list.
         *
         * @see CardTemplateEditorTest.testEditTemplateContents
         */
        private val handled =
            setOf(
                CardTemplateEditor::class.java,
            )

        /** Activities hosting a fragment named by an intent extra which [ActivityList] does not set */
        private val fragmentHosts =
            listOf(
                AccountActivity::class.java,
                CardViewerActivity::class.java,
                ConfigAwareSingleFragmentActivity::class.java,
                MultimediaActivity::class.java,
                PreferencesActivity::class.java,
            )
    }
}

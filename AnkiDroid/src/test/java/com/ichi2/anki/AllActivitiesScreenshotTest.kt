// SPDX-FileCopyrightText: 2026 David Allison <davidallisongithub@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki

import android.app.Activity
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterValuesProvider
import com.ichi2.anki.account.AccountActivity
import com.ichi2.anki.instantnoteeditor.InstantNoteEditorActivity
import com.ichi2.anki.multimedia.MultimediaActivity
import com.ichi2.anki.preferences.PreferencesActivity
import com.ichi2.anki.previewer.CardViewerActivity
import com.ichi2.anki.utils.ConfigAwareSingleFragmentActivity
import com.ichi2.testutils.ActivityList
import com.ichi2.testutils.ActivityList.ActivityLaunchParam
import com.ichi2.testutils.BackupManagerTestUtilities
import com.ichi2.testutils.skipTest
import com.ichi2.utils.dp
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Captures a baseline screenshot for every activity declared in the manifest.
 *
 * @see ActivityList.allActivitiesAndIntents
 *
 * `./gradlew :AnkiDroid:verifyRoborazziPlayDebug -Pscreenshot --tests "com.ichi2.anki.AllActivitiesScreenshotTest"`
 *
 * TODO: Split each activity into its own per-class screenshot test
 */
class AllActivitiesScreenshotTest : ScreenshotTest() {
    @TestParameter(valuesProvider = ActivityLauncherProvider::class)
    lateinit var config: ActivityConfig

    @TestParameter
    var isEdgeToEdge: Boolean = false

    @Before
    override fun setUp() {
        // Same exclusions as ActivityStartupUnderBackupTest — onCreate fails standalone for these.
        notYetHandled(IntentHandler::class.java.simpleName, "Not working (or implemented) - inherits from Activity")
        notYetHandled(IntentHandler2::class.java.simpleName, "Not working (or implemented) - inherits from Activity")
        notYetHandled(
            SingleFragmentActivity::class.java.simpleName,
            "Implemented, but the test fails because the activity throws if a specific intent extra isn't set",
        )
        notYetHandled(InstantNoteEditorActivity::class.java.simpleName, "Single instance activity so should be used")

        // Fragment-host activities: need a 'fragmentName' intent extra to render anything.
        // TODO: split these into per-class screenshot tests that pass a real fragment.
        notYetHandled(ConfigAwareSingleFragmentActivity::class.java.simpleName, "Needs 'fragmentName' intent extra")
        notYetHandled(MultimediaActivity::class.java.simpleName, "Needs 'fragmentName' intent extra")
        notYetHandled(AccountActivity::class.java.simpleName, "Needs 'fragmentName' intent extra")

        // TODO: split into a per-class test that creates a real note type before launching.
        notYetHandled(CardTemplateEditor::class.java.simpleName, "Needs a real note type in the collection")

        super.setUp()
    }

    @After
    fun tearDownBackup() {
        BackupManagerTestUtilities.reset()
    }

    @Test
    fun screenshot() {
        val launcher = config.launcher
        val activity =
            startActivityNormallyOpenCollectionWithIntent(
                launcher.activity,
                launcher.buildIntent(targetContext),
            )

        if (isEdgeToEdge) {
            activity.simulateEdgeToEdge()
        }

        val displayName = launcher.simpleName + if (isEdgeToEdge) "_edgeToEdge" else ""
        captureScreen(displayName)
    }

    private fun notYetHandled(
        activityName: String,
        reason: String,
    ) {
        if (config.launcher.simpleName == activityName) {
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
        override fun provideValues(context: Context?): List<ActivityConfig> {
            val handled =
                setOf(
                    // DeckPickerScreenshotTest
                    DeckPicker::class.java,
                    // StudyScreenScreenshotTest, PreviewerScreenshotTest and TemplatePreviewerScreenshotTest
                    CardViewerActivity::class.java,
                    // PreferencesScreenshotTest
                    PreferencesActivity::class.java,
                )
            return ActivityList
                .allActivitiesAndIntents()
                .filterNot { handled.contains(it.activity) }
                .map { ActivityConfig(it) }
        }
    }
}

/**
 * Inject realistic system bars for edge to edge.
 *
 * Mirrors the helper from `DeckPickerScreenshotTest` on the `edge-2-edge` branch.
 *
 * WARN: Does not match reality. There are issues with element placement and scrolling lists. [FAB in Deck Picker]
 */
private fun Activity.simulateEdgeToEdge() {
    val insets =
        WindowInsetsCompat
            .Builder()
            .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, 24.dp.toPx(this), 0, 0))
            .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, 48.dp.toPx(this)))
            .build()
    ViewCompat.dispatchApplyWindowInsets(window.decorView, insets)

    val decor = window.decorView as ViewGroup
    val navBarOverlay =
        View(this).apply {
            setBackgroundColor(0x80000000.toInt())
        }
    decor.addView(
        navBarOverlay,
        FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 48.dp.toPx(this), Gravity.BOTTOM),
    )
}

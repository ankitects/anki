// SPDX-FileCopyrightText: 2026 David Allison <davidallisongithub@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki

import android.app.Activity
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.content.edit
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.ichi2.anki.account.AccountActivity
import com.ichi2.anki.instantnoteeditor.InstantNoteEditorActivity
import com.ichi2.anki.multimedia.MultimediaActivity
import com.ichi2.anki.preferences.PreferencesActivity
import com.ichi2.anki.preferences.sharedPrefs
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
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

/**
 * Captures a baseline screenshot for every activity declared in the manifest.
 *
 * @see ActivityList.allActivitiesAndIntents
 *
 * `./gradlew :AnkiDroid:verifyRoborazziPlayDebug -Pscreenshot --tests "com.ichi2.anki.AllActivitiesScreenshotTest"`
 *
 * TODO: Split each activity into its own per-class screenshot test
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
class AllActivitiesScreenshotTest : ScreenshotTest() {
    @ParameterizedRobolectricTestRunner.Parameter
    @JvmField
    var launcher: ActivityLaunchParam? = null

    // Used for display and the screenshot filename (e.g. "DeckPicker" or "DeckPicker_edgeToEdge")
    @ParameterizedRobolectricTestRunner.Parameter(1)
    @JvmField
    var displayName: String? = null

    @ParameterizedRobolectricTestRunner.Parameter(2)
    @JvmField
    var configure: (Activity.() -> Unit)? = null

    @Before
    override fun setUp() {
        // Same exclusions as ActivityStartupUnderBackupTest — onCreate fails standalone for these.
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

        // Fragment-host activities: need a 'fragmentName' intent extra to render anything.
        // TODO: split these into per-class screenshot tests that pass a real fragment.
        notYetHandled(ConfigAwareSingleFragmentActivity::class.java.simpleName, "Needs 'fragmentName' intent extra")
        notYetHandled(CardViewerActivity::class.java.simpleName, "Needs 'fragmentName' intent extra")
        notYetHandled(MultimediaActivity::class.java.simpleName, "Needs 'fragmentName' intent extra")
        notYetHandled(AccountActivity::class.java.simpleName, "Needs 'fragmentName' intent extra")

        // TODO: split into a per-class test that creates a real note type before launching.
        notYetHandled(CardTemplateEditor::class.java.simpleName, "Needs a real note type in the collection")

        super.setUp()

        // Setup for DeckPicker
        ensureCollectionLoadIsSynchronous()
        setIntroductionSlidesShown(true)
        BackupManagerTestUtilities.setupSpaceForBackup(targetContext)
        // suppress the periodic 'backup your collection' prompt so the screenshot is just the activity
        targetContext.sharedPrefs().edit { putBoolean("backupPromptDisabled", true) }
    }

    @After
    fun tearDownBackup() {
        BackupManagerTestUtilities.reset()
    }

    @Test
    fun screenshot() {
        val activity =
            startActivityNormallyOpenCollectionWithIntent(
                launcher!!.activity,
                launcher!!.buildIntent(targetContext),
            )
        configure!!(activity)
        captureScreen(displayName!!)
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
        private val regular: Activity.() -> Unit = {}
        private val edgeToEdge: Activity.() -> Unit = { simulateEdgeToEdge() }

        @ParameterizedRobolectricTestRunner.Parameters(name = "{1}")
        @JvmStatic
        fun initParameters(): Collection<Array<Any>> =
            ActivityList.allActivitiesAndIntents().flatMap { launcher ->
                listOf(
                    arrayOf<Any>(launcher, launcher.simpleName, regular),
                    arrayOf<Any>(launcher, "${launcher.simpleName}_edgeToEdge", edgeToEdge),
                )
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

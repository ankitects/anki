// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.export

import android.content.DialogInterface
import android.widget.Spinner
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragment
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.Visibility.GONE
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CommonString
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.browser.IdsFile
import com.ichi2.anki.deckAdapter
import com.ichi2.anki.dialogs.DeckSelectionDialog
import com.ichi2.anki.dialogs.utils.input
import com.ichi2.anki.libanki.Consts.DEFAULT_DECK_ID
import com.ichi2.anki.utils.ext.requireParcelable
import com.ichi2.anki.withDeckPicker
import com.ichi2.testutils.parcelSize
import com.ichi2.testutils.parcelledCopy
import com.ichi2.testutils.saveState
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.not
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowToast
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.ichi2.anki.ankicommon.R as AnkiCommonR
import com.ichi2.anki.common.android.R as CommonR

@RunWith(AndroidJUnit4::class)
class ExportDialogFragmentTest : RobolectricTest() {
    @Test
    fun `missing selection cancels export`() = assertUnavailableSelection { assertTrue(delete()) }

    @Test
    fun `truncated selection cancels export`() = assertUnavailableSelection { writeBytes(readBytes().dropLast(1).toByteArray()) }

    private fun assertUnavailableSelection(changeFile: IdsFile.() -> Unit) =
        runTest {
            ensureCollectionLoadIsSynchronous()
            for (type in ExportDialogFragment.ExportType.entries) {
                // APKG, notes as text, and cards as text all need the original selection.
                for (exportFormat in 1..3) {
                    val args = ExportDialogFragment.newInstance(targetContext.cacheDir, type, listOf(1L)).requireArguments()
                    launchFragment<ExportDialogFragment>(fragmentArgs = args, themeResId = CommonR.style.Theme_AnkiDroid).use { scenario ->
                        advanceRobolectricLooper()
                        scenario.onFragment { fragment ->
                            fragment.binding.exportTypeSelector.setSelection(exportFormat)
                            // The file can disappear even while the dialog is open.
                            args.requireParcelable<IdsFile>(ARG_IDS_FILE).changeFile()
                            val dialog = fragment.requireDialog() as AlertDialog
                            assertTrue(fragment.binding.deckSelector.isEnabled)
                            ShadowToast.reset()
                            dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
                            advanceRobolectricLooper()
                            assertFalse(dialog.isShowing)
                            assertEquals(targetContext.getString(CommonString.something_wrong), ShadowToast.getTextOfLatestToast())
                        }
                    }
                }
            }
        }

    @Test
    fun `collection export options are initialized correctly`() {
        onExportDialog {
            // Select export type as anki collection package.
            onView(withId(R.id.export_type_selector)).inRoot(isDialog()).perform(scrollTo(), click())
            onData(containsString("Anki collection package"))
                .inAdapterView(withId(R.id.export_type_selector))
                .perform(click())

            // Check that the UI elements are displayed and have the correct text and default values for checkboxes.
            onView(withId(R.id.collection_include_media))
                .inRoot(isDialog())
                .perform(scrollTo())
                .check(matches(isDisplayed()))
                .check(matches(withText(TR.exportingIncludeMedia())))
                .check(matches(isChecked()))

            onView(withId(R.id.collection_export_legacy))
                .inRoot(isDialog())
                .perform(scrollTo())
                .check(matches(isDisplayed()))
                .check(matches(withText(TR.exportingSupportOlderAnkiVersions())))
                .check(matches(not(isChecked())))
        }
    }

    @Test
    fun `apkg export options are initialized correctly`() {
        onExportDialog {
            // Select export type as anki deck package.
            onView(withId(R.id.export_type_selector)).inRoot(isDialog()).perform(scrollTo(), click())
            onData(containsString("Anki deck package"))
                .inAdapterView(withId(R.id.export_type_selector))
                .perform(click())

            // Check that the UI elements are displayed and have the correct text and default values for checkboxes.
            onView(withId(R.id.apkg_include_media))
                .inRoot(isDialog())
                .perform(scrollTo())
                .check(matches(isDisplayed()))
                .check(matches(withText(TR.exportingIncludeMedia())))
                .check(matches(isChecked()))

            onView(withId(R.id.apkg_include_deck_configs))
                .inRoot(isDialog())
                .perform(scrollTo())
                .check(matches(isDisplayed()))
                .check(matches(withText(TR.exportingIncludeDeckConfigs())))
                .check(matches(not(isChecked())))

            onView(withId(R.id.apkg_include_schedule))
                .inRoot(isDialog())
                .perform(scrollTo())
                .check(matches(isDisplayed()))
                .check(matches(withText(TR.exportingIncludeSchedulingInformation())))
                .check(matches(isChecked()))

            onView(withId(R.id.apkg_export_legacy))
                .inRoot(isDialog())
                .perform(scrollTo())
                .check(matches(isDisplayed()))
                .check(matches(withText(TR.exportingSupportOlderAnkiVersions())))
                .check(matches(not(isChecked())))
        }
    }

    @Test
    fun `Legacy export checkbox(default false) is shown only for collection and apkg`() {
        onExportDialog {
            // check legacy checkboxes status for collection export
            onView(withId(R.id.export_type_selector)).inRoot(isDialog()).perform(scrollTo(), click())
            onData(containsString("Anki collection package"))
                .inAdapterView(withId(R.id.export_type_selector))
                .perform(click())
            onView(withId(R.id.collection_export_legacy))
                .inRoot(isDialog())
                .perform(scrollTo())
                .check(matches(isDisplayed()))
            onView(withId(R.id.collection_export_legacy))
                .inRoot(isDialog())
                .check(matches(not(isChecked())))
            onView(withId(R.id.apkg_export_legacy))
                .inRoot(isDialog())
                .check(matches(withEffectiveVisibility(GONE)))

            // check legacy checkboxes status for apkg export
            onView(withId(R.id.export_type_selector)).inRoot(isDialog()).perform(scrollTo(), click())
            onData(containsString("Anki deck package"))
                .inAdapterView(withId(R.id.export_type_selector))
                .perform(click())
            onView(withId(R.id.apkg_export_legacy))
                .inRoot(isDialog())
                .perform(scrollTo())
                .check(matches(isDisplayed()))
            onView(withId(R.id.apkg_export_legacy))
                .inRoot(isDialog())
                .check(matches(not(isChecked())))
            onView(withId(R.id.collection_export_legacy))
                .inRoot(isDialog())
                .check(matches(withEffectiveVisibility(GONE)))

            // checkboxes are not shown for notes export
            onView(withId(R.id.export_type_selector)).inRoot(isDialog()).perform(scrollTo(), click())
            onData(containsString("Notes in plain text"))
                .inAdapterView(withId(R.id.export_type_selector))
                .perform(click())
            onView(withId(R.id.apkg_export_legacy))
                .inRoot(isDialog())
                .check(matches(withEffectiveVisibility(GONE)))
            onView(withId(R.id.collection_export_legacy))
                .inRoot(isDialog())
                .check(matches(withEffectiveVisibility(GONE)))

            // checkboxes are not shown for cards export
            onView(withId(R.id.export_type_selector)).inRoot(isDialog()).perform(scrollTo(), click())
            onData(containsString("Cards in plain text"))
                .inAdapterView(withId(R.id.export_type_selector))
                .perform(click())
            onView(withId(R.id.apkg_export_legacy))
                .inRoot(isDialog())
                .check(matches(withEffectiveVisibility(GONE)))
            onView(withId(R.id.collection_export_legacy))
                .inRoot(isDialog())
                .check(matches(withEffectiveVisibility(GONE)))
        }
    }

    @Test
    fun `deck selector opens the deck selection dialog`() {
        onDeckExportDialog { scenario ->
            scenario.onFragment { fragment ->
                fragment.binding.deckSelector.performClick()
                advanceRobolectricLooper()
                val deckSelectionDialog = fragment.childFragmentManager.findFragmentByTag(DeckSelectionDialog.TAG)
                assertNotNull(deckSelectionDialog)
                assertTrue(deckSelectionDialog.requireArguments().getBoolean(DeckSelectionDialog.ARG_ALLOW_ALL, false))
                assertTrue(deckSelectionDialog.requireArguments().getBoolean(DeckSelectionDialog.ARG_ALLOW_FILTERED, false))
            }
        }
    }

    @Test
    fun `deck selected in the deck selection dialog is kept on recreation`() {
        addDeck("Chosen")
        onDeckExportDialog { scenario ->
            onView(withId(R.id.deck_selector)).inRoot(isDialog()).perform(click())
            advanceRobolectricLooper()
            onView(withText("Chosen")).inRoot(isDialog()).perform(click())
            advanceRobolectricLooper()
            onView(withId(R.id.deck_selector)).inRoot(isDialog()).check(matches(withText("Chosen")))

            scenario.recreate()
            advanceRobolectricLooper()
            onView(withId(R.id.deck_selector)).inRoot(isDialog()).check(matches(withText("Chosen")))
        }
    }

    @Test
    fun `filtered deck can be selected as the export source`() {
        addDynamicDeck("Filtered export source")
        onDeckExportDialog {
            onView(withId(R.id.deck_selector)).inRoot(isDialog()).perform(click())
            advanceRobolectricLooper()
            onView(withText("Filtered export source")).inRoot(isDialog()).perform(click())
            advanceRobolectricLooper()
            onView(withId(R.id.deck_selector)).inRoot(isDialog()).check(matches(withText("Filtered export source")))
        }
    }

    @Test
    fun `deck selector exposes its control role and popup to accessibility services`() =
        onDeckExportDialog { scenario ->
            scenario.onFragment { fragment ->
                val node = fragment.binding.deckSelector.createAccessibilityNodeInfo()
                assertEquals(Spinner::class.java.name, node.className.toString(), "Deck selection must expose a control role")
                assertTrue(node.canOpenPopup(), "Deck selection must announce that it opens a popup")
            }
        }

    @Test
    fun `deck created through export is visible after cancelling export`() =
        onExportDialogOverDeckPicker { deckPicker, export ->
            assertTrue(export.binding.deckSelector.performClick())
            advanceRobolectricLooper()
            val picker = assertIs<DeckSelectionDialog>(export.childFragmentManager.findFragmentByTag(DeckSelectionDialog.TAG))
            val menu = picker.binding.toolbar.menu
            assertTrue(menu.performIdentifierAction(AnkiCommonR.id.action_add_deck, 0))
            advanceRobolectricLooper()

            val creation = assertIs<AlertDialog>(ShadowDialog.getLatestDialog())
            creation.input = "Created from export"
            creation.positiveButton.performClick()
            advanceRobolectricLooper()
            val createdId = assertNotNull(col.decks.idForName("Created from export"))

            (export.requireDialog() as AlertDialog).negativeButton.performClick()
            advanceRobolectricLooper()
            val adapter = deckPicker.deckAdapter
            advanceRobolectricLooperUntil(
                lazyMessage = { "Deck created through export was saved but never appeared in the deck list" },
            ) {
                adapter.currentList.any { it.did == createdId }
            }
        }

    @Test
    fun `cancelling export while the deck selector loads does not show an error`() =
        onExportDialogOverDeckPicker { _, export ->
            assertTrue(export.binding.deckSelector.isEnabled)

            // Hold collection access so export can be dismissed before the deck picker opens.
            holdingCollectionAccess { scheduler ->
                assertTrue(export.binding.deckSelector.performClick())
                assertNull(export.childFragmentManager.findFragmentByTag(DeckSelectionDialog.TAG))
                assertIs<AlertDialog>(export.requireDialog()).negativeButton.performClick()
                advanceRobolectricLooper()
                assertFalse(export.isAdded)

                // RobolectricTest fails if the delayed task attempts to show an error dialog.
                scheduler.advanceUntilIdle()
                advanceRobolectricLooper()
            }
        }

    @Test
    fun `double tap while the decks load opens one deck dialog`() =
        onExportDialogOverDeckPicker { _, export ->
            holdingCollectionAccess { scheduler ->
                assertTrue(export.binding.deckSelector.performClick())
                assertTrue(export.binding.deckSelector.performClick())
                scheduler.advanceUntilIdle()
                advanceRobolectricLooper()
            }
            export.childFragmentManager.executePendingTransactions()
            assertEquals(1, export.childFragmentManager.fragments.count { it is DeckSelectionDialog })
        }

    @Test
    fun `double tap with no collection backlog opens one deck dialog`() =
        onExportDialogOverDeckPicker { _, export ->
            // Unlike the delayed-load test, allow the loading job to finish before
            // the queued fragment transaction runs and the second click arrives.
            assertTrue(export.binding.deckSelector.performClick())
            assertTrue(export.binding.deckSelector.performClick())
            advanceRobolectricLooper()
            export.childFragmentManager.executePendingTransactions()
            assertEquals(1, export.childFragmentManager.fragments.count { it is DeckSelectionDialog })
        }

    @Test
    fun `opening the deck picker keeps saved state below the Binder limit`() {
        ensureCollectionLoadIsSynchronous()
        val parent = "Medical Curriculum::Clinical Medicine and Surgery::Examination Preparation::Detailed Subject Review"
        repeat(3500) { addDeck("$parent::Topic ${it.toString().padStart(4, '0')}") }

        Robolectric.buildActivity(FragmentActivity::class.java).use { controller ->
            controller.get().setTheme(CommonR.style.Theme_AnkiDroid)
            val activity = controller.setup().get()
            val export = ExportDialogFragment.newInstance(DEFAULT_DECK_ID)
            export.show(activity.supportFragmentManager, "export")
            advanceRobolectricLooper()
            assertTrue(export.binding.deckSelector.performClick())
            advanceRobolectricLooper()
            assertNotNull(export.childFragmentManager.findFragmentByTag(DeckSelectionDialog.TAG))

            // Fragment arguments are included in the activity state sent over Binder.
            // The process shares a 1 MiB transaction buffer, so this is an upper bound.
            val bytes = controller.saveState().parcelSize()
            assertTrue(bytes < 1024 * 1024, "Activity saved state is $bytes bytes")
        }
    }

    @Test
    fun `restoring export reloads the selected deck name`() {
        ensureCollectionLoadIsSynchronous()
        val deckId = addDeck("Original name")
        val savedState =
            Robolectric.buildActivity(FragmentActivity::class.java).use { controller ->
                controller.get().setTheme(CommonR.style.Theme_AnkiDroid)
                val activity = controller.setup().get()
                ExportDialogFragment.newInstance(deckId).show(activity.supportFragmentManager, "export")
                advanceRobolectricLooper()
                controller.saveState().parcelledCopy(FragmentActivity::class.java.classLoader)
            }

        // Simulate a collection change while the activity is stopped.
        col.decks.rename(deckId, "Renamed deck")

        Robolectric.buildActivity(FragmentActivity::class.java).use { controller ->
            controller.get().setTheme(CommonR.style.Theme_AnkiDroid)
            val activity = controller.setup(savedState).get()
            advanceRobolectricLooper()
            val export = assertIs<ExportDialogFragment>(activity.supportFragmentManager.findFragmentByTag("export"))
            val selector = export.binding.deckSelector
            assertEquals("Renamed deck", selector.text.toString())
        }
    }

    @Test
    fun `ids file is not removed on a configuration change`() {
        val fragment =
            ExportDialogFragment.newInstance(
                idsFileDirectory = targetContext.cacheDir,
                type = ExportDialogFragment.ExportType.Notes,
                ids = listOf(1L, 2L, 3L),
            )
        val idsFile = fragment.requireArguments().requireParcelable<IdsFile>(ARG_IDS_FILE)
        assertTrue(idsFile.exists())

        launchFragment<ExportDialogFragment>(
            fragmentArgs = fragment.arguments,
            themeResId = CommonR.style.Theme_AnkiDroid,
        ).use { scenario ->
            scenario.recreate()
            assertTrue(idsFile.exists(), "ids file must survive a configuration change")
        }
    }

    /** Launches [ExportDialogFragment] and executes [action] on the fragment. */
    private fun onExportDialog(action: ExportDialogFragment.() -> Unit) =
        launchFragment<ExportDialogFragment>(
            themeResId = CommonR.style.Theme_AnkiDroid,
        ).use { scenario -> scenario.onFragment { action(it) } }

    private fun onDeckExportDialog(action: (FragmentScenario<ExportDialogFragment>) -> Unit) {
        ensureCollectionLoadIsSynchronous()
        launchFragment<ExportDialogFragment>(
            fragmentArgs = ExportDialogFragment.newInstance(DEFAULT_DECK_ID).arguments,
            themeResId = CommonR.style.Theme_AnkiDroid,
        ).use { scenario ->
            advanceRobolectricLooper()
            action(scenario)
        }
    }

    private fun onExportDialogOverDeckPicker(action: (DeckPicker, ExportDialogFragment) -> Unit) =
        withDeckPicker(1, withCards = true) { deckPicker ->
            val export = ExportDialogFragment.newInstance(DEFAULT_DECK_ID)
            export.show(deckPicker.supportFragmentManager, "export")
            deckPicker.supportFragmentManager.executePendingTransactions()
            advanceRobolectricLooper()
            action(deckPicker, export)
        }

    private fun holdingCollectionAccess(block: (TestCoroutineScheduler) -> Unit) {
        val queue = StandardTestDispatcher()
        val previousQueue = CollectionManager.setTestDispatcher(queue, useReentrantLock = false)
        try {
            block(queue.scheduler)
        } finally {
            CollectionManager.setTestDispatcher(previousQueue)
        }
    }

    companion object {
        private const val ARG_IDS_FILE = "arg_ids_file"
    }
}

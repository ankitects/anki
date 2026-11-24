// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 Anoop <xenonnn4w@gmail.com>

package com.ichi2.anki.widget.cardanalysis

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.database.sqlite.SQLiteDatabaseCorruptException
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CommonString
import com.ichi2.anki.CrashReportData.HelpAction
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.dialogs.DatabaseErrorDialog
import com.ichi2.anki.dialogs.databaseErrorDialogs
import com.ichi2.anki.dialogs.utils.message
import com.ichi2.anki.dialogs.utils.performPositiveClick
import com.ichi2.anki.dialogs.utils.title
import com.ichi2.anki.setIntroductionSlidesShown
import com.ichi2.anki.utils.ext.DIALOG_FRAGMENT_TAG
import com.ichi2.anki.widgets.R
import com.ichi2.testutils.BackupManagerTestUtilities
import com.ichi2.widget.AppWidgetId
import com.ichi2.widget.AppWidgetId.Companion.updateWidget
import com.ichi2.widget.cardanalysis.CardAnalysisWidget
import com.ichi2.widget.cardanalysis.CardAnalysisWidgetConfig
import com.ichi2.widget.cardanalysis.CardAnalysisWidgetPreferences
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAppWidgetManager
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// TODO CardAnalysisWidgetPreferences should be refactored for injection/easier use in tests
@RunWith(AndroidJUnit4::class)
class CardAnalysisWidgetConfigTest : RobolectricTest() {
    private lateinit var preferences: CardAnalysisWidgetPreferences

    override fun setUp() {
        super.setUp()
        val mgr = AppWidgetManager.getInstance(targetContext)
        mgr.bindAppWidgetIdIfAllowed(
            testWidgetId.id,
            ComponentName(targetContext, CardAnalysisWidget::class.java),
        )
        preferences = CardAnalysisWidgetPreferences(targetContext)
    }

    override fun tearDown() {
        super.tearDown()
        ShadowAppWidgetManager.reset()
    }

    @Test
    fun `finishes at start when collection is empty`() =
        runTest {
            val activity = startTestActivity(withEmptyCollection = true)
            advanceUntilIdle()
            assertTrue(activity.isFinishing)
        }

    @Test
    fun `finishes at start if provided widget id is INVALID_APPWIDGET_ID`() =
        runTest {
            val activity =
                startTestActivity(widgetId = AppWidgetId(AppWidgetManager.INVALID_APPWIDGET_ID))
            advanceUntilIdle()
            assertTrue(activity.isFinishing)
        }

    @Test
    fun `configures correctly a new widget`() =
        runTest {
            val testDeckId = addDeck(TEST_DECK_NAME1)
            val activity = startTestActivity()
            advanceUntilIdle()
            // first setup of the widget, no deck selected so deck name view should be empty
            onView(withId(R.id.deck_name)).check(matches(withText("")))
            // select dialog already open, select deck 1
            onView(withText(CommonString.select_deck_title))
                .inRoot(isDialog())
                .check(matches(isDisplayed()))
            onView(withText(TEST_DECK_NAME1)).inRoot(isDialog()).perform(click())
            // check if UI shows the selected deck and if it was saved in preferences
            onView(withId(R.id.deck_name)).check(matches(withText(TEST_DECK_NAME1)))
            assertEquals(testDeckId, preferences.getSelectedDeckIdFromPreferences(testWidgetId))
            // the activity is closed on first deck selection
            assertTrue(activity.isFinishing)
        }

    @Test
    fun `ui is updated based on user deck selection`() =
        runTest {
            val testDeck1Id = addDeck(TEST_DECK_NAME1)
            preferences.saveSelectedDeck(testWidgetId, testDeck1Id)
            val testDeck2Id = addDeck(TEST_DECK_NAME2)
            startTestActivity()
            advanceUntilIdle()
            // deck 1 is already selected
            onView(withId(R.id.deck_name)).check(matches(withText(TEST_DECK_NAME1)))
            // open deck selection, select deck 2 and verify that is displayed and selected in ui
            onView(withId(R.id.change_btn)).perform(click())
            onView(withText(TEST_DECK_NAME2)).inRoot(isDialog()).perform(click())
            onView(withId(R.id.deck_name)).check(matches(withText(TEST_DECK_NAME2)))
            // set configuration as done
            onView(withId(R.id.done_btn)).perform(click())
            // check that the correct deck is saved in preferences
            assertEquals(testDeck2Id, preferences.getSelectedDeckIdFromPreferences(testWidgetId))
        }

    @Test
    fun `handles correctly user not selecting a deck`() =
        runTest {
            startTestActivity()
            advanceUntilIdle()
            // no selection yet
            onView(withId(R.id.deck_name)).check(matches(withText("")))
            // cancel deck selection
            onView(withText(CommonString.dialog_cancel)).inRoot(isDialog()).perform(click())
            // still no deck selected, so deck name should still be empty
            onView(withId(R.id.deck_name)).check(matches(withText("")))
            // set configuration as done
            onView(withId(R.id.done_btn)).perform(click())
            // no deck selected so there shouldn't be any deck saved in preferences
            assertNull(preferences.getSelectedDeckIdFromPreferences(testWidgetId))
        }

    @Test
    fun `handles widget removal while configuring`() =
        runTest {
            val testDeckId = addDeck(TEST_DECK_NAME1)
            preferences.saveSelectedDeck(testWidgetId, testDeckId)

            startTestActivity()
            advanceUntilIdle()
            // check that we have a deck previously selected
            onView(withId(R.id.deck_name)).check(matches(withText(TEST_DECK_NAME1)))

            val deleteWidgetIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_DELETED)
            deleteWidgetIntent.updateWidget(testWidgetId)
            targetContext.sendBroadcast(deleteWidgetIntent)
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            // check that the previous preferences data is deleted
            assertNull(preferences.getSelectedDeckIdFromPreferences(testWidgetId))
        }

    @Test
    fun `handles decks selection on configuration change`() =
        runTest {
            val testDeck1Id = addDeck(TEST_DECK_NAME1)
            val testDeck2Id = addDeck(TEST_DECK_NAME2)
            preferences.saveSelectedDeck(testWidgetId, testDeck1Id)
            val activity = startTestActivity()
            advanceUntilIdle()
            // verify that deck 1 is selected
            onView(withId(R.id.deck_name)).check(matches(withText(TEST_DECK_NAME1)))
            // open deck selection, select deck 2 and verify this new selection
            onView(withId(R.id.change_btn)).perform(click())
            onView(withText(TEST_DECK_NAME2)).inRoot(isDialog()).perform(click())
            onView(withId(R.id.deck_name)).check(matches(withText(TEST_DECK_NAME2)))
            // simulate a configuration change by recreating the activity
            activity.recreate()
            // check that the previously deck 2 is still displayed
            onView(withId(R.id.deck_name)).check(matches(withText(TEST_DECK_NAME2)))
            // click done
            onView(withId(R.id.done_btn)).perform(click())
            // check that the last selected deck is saved in preferences
            assertEquals(testDeck2Id, preferences.getSelectedDeckIdFromPreferences(testWidgetId))
        }

    @Test
    fun `a corrupt collection shows the load failure in the Deck Picker`() {
        val activity = startTestActivityWithDeckPicker()
        databaseErrorDialogs.showLoadFailed(activity, SQLiteDatabaseCorruptException())

        val loadFailed = activity.openedDeckPicker().awaitDialog<DatabaseErrorDialog>()
        assertEquals(getResourceString(CommonString.open_collection_failed_title), loadFailed.title)
    }

    @Test
    fun `Check Database from an error runs in the Deck Picker`() {
        val activity = startTestActivityWithDeckPicker()
        assertTrue(HelpAction.OpenCheckDatabase.canExecute(activity))
        runBlocking { HelpAction.OpenCheckDatabase.execute(activity) }

        activity.openedDeckPicker().awaitDialog<DatabaseErrorDialog>().performPositiveClick()
        assertEquals(TR.databaseCheckRebuilt(), (ShadowDialog.getLatestDialog() as AlertDialog).message)
    }

    private fun startTestActivityWithDeckPicker(): CardAnalysisWidgetConfig {
        ensureCollectionLoadIsSynchronous()
        setIntroductionSlidesShown(true)
        BackupManagerTestUtilities.setupSpaceForBackup(targetContext)
        targetContext.sharedPrefs().edit { putBoolean("backupPromptDisabled", true) }
        return startTestActivity()
    }

    private fun FragmentActivity.openedDeckPicker(): DeckPicker {
        val intent = assertNotNull(shadowOf(this).nextStartedActivity, "the Deck Picker was not opened")
        assertEquals(DeckPicker::class.java.name, intent.component?.className)
        return startActivityNormallyOpenCollectionWithIntent(DeckPicker::class.java, intent)
    }

    private inline fun <reified T : DialogFragment> FragmentActivity.awaitDialog(): AlertDialog {
        advanceRobolectricLooperUntil { supportFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG) is T }
        return (supportFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG) as T).requireDialog() as AlertDialog
    }

    private fun startTestActivity(
        widgetId: AppWidgetId = testWidgetId,
        withEmptyCollection: Boolean = false,
    ): CardAnalysisWidgetConfig {
        if (!withEmptyCollection) {
            ensureNonEmptyCollection()
        }
        val intent =
            Intent(targetContext, CardAnalysisWidgetConfig::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId.id)
            }
        return startActivityNormallyOpenCollectionWithIntent(
            CardAnalysisWidgetConfig::class.java,
            intent,
        )
    }

    companion object {
        private val testWidgetId = AppWidgetId(1)
        private const val TEST_DECK_NAME1 = "TestDeckName1"
        private const val TEST_DECK_NAME2 = "TestDeckName2"
    }
}

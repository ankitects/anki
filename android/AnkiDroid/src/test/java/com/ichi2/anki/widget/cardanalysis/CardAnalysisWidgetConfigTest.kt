/*
 *  Copyright (c) 2024 Anoop <xenonnn4w@gmail.com>
 *  Copyright (c) 2025 lukstbit <52494258+lukstbit@users.noreply.github.com>
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

package com.ichi2.anki.widget.cardanalysis

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.widget.AppWidgetId
import com.ichi2.widget.AppWidgetId.Companion.updateWidget
import com.ichi2.widget.cardanalysis.CardAnalysisWidget
import com.ichi2.widget.cardanalysis.CardAnalysisWidgetConfig
import com.ichi2.widget.cardanalysis.CardAnalysisWidgetPreferences
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowAppWidgetManager
import org.robolectric.shadows.ShadowLooper
import kotlin.test.assertEquals
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
            onView(withText(R.string.select_deck_title))
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
            onView(withText(R.string.dialog_cancel)).inRoot(isDialog()).perform(click())
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

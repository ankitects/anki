/*
 * Copyright (c) 2025 lukstbit <52494258+lukstbit@users.noreply.github.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.pages

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.dialogs.DeckSelectionDialog
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.runner.RunWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class StatisticsTest : RobolectricTest() {
    @Test
    fun `shows 'Default' deck when collection is empty`() =
        runTest {
            ActivityScenario.launch<SingleFragmentActivity>(
                StatisticsDestination().toIntent(
                    targetContext,
                ),
            )
            advanceUntilIdle()
            onView(withText("Default")).check(matches(isDisplayed()))
        }

    @Test
    fun `changing decks shows new deck name`() =
        runTest {
            val testDeckName1 = "TestDeckName1"
            val testDeckName2 = "TestDeckName2"
            val testDeck1 = addDeck(testDeckName1)
            withCol { decks.select(testDeck1) }
            addDeck(testDeckName2)
            ActivityScenario.launch<SingleFragmentActivity>(
                StatisticsDestination().toIntent(
                    targetContext,
                ),
            )
            advanceUntilIdle()
            onView(withId(R.id.deck_name)).check(matches(withText(testDeckName1)))
            onView(withId(R.id.deck_name)).perform(click())
            advanceUntilIdle()
            // select test deck 2
            onView(withText(testDeckName2)).inRoot(isDialog()).perform(click())
            // check the activity that it has the new deck name
            onView(withId(R.id.deck_name)).check(matches(withText(testDeckName2)))
        }

    @Test
    fun `uses expected constraints for decks list selection dialog`() =
        runTest {
            // the statistics screen doesn't allow the selection of 'All Decks' and filtered decks,
            // also 'Default' deck should be enabled no matter its status(empty/not empty)
            ActivityScenario
                .launch<SingleFragmentActivity>(StatisticsDestination().toIntent(targetContext))
                .onActivity { activity ->
                    val statisticsFragment =
                        activity.supportFragmentManager.findFragmentById(R.id.fragment_container)
                    assertNotNull(statisticsFragment)
                    onView(withId(R.id.deck_name)).perform(click())
                    advanceRobolectricLooper()
                    val deckSelectionDialog =
                        activity.supportFragmentManager.findFragmentByTag(DeckSelectionDialog.TAG)
                    assertNotNull(deckSelectionDialog)
                    assertFalse(deckSelectionDialog.requireArguments().getBoolean(DeckSelectionDialog.ARG_ALLOW_ALL, true))
                    assertFalse(deckSelectionDialog.requireArguments().getBoolean(DeckSelectionDialog.ARG_ALLOW_FILTERED, true))
                    assertTrue(deckSelectionDialog.requireArguments().getBoolean(DeckSelectionDialog.ARG_SKIP_EMPTY_DEFAULT, false))
                }
        }
}

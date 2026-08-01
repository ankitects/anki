/*
 *  Copyright (c) 2024 Anoop <xenonnn4w@gmail.com>
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

package com.ichi2.anki.widget.deckpicker

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.model.SelectableDeck
import com.ichi2.widget.AppWidgetId
import com.ichi2.widget.deckpicker.DeckPickerWidgetConfig
import com.ichi2.widget.deckpicker.DeckPickerWidgetPreferences
import kotlinx.coroutines.runBlocking
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

val testWidgetId = AppWidgetId(1)

@RunWith(AndroidJUnit4::class)
class DeckPickerWidgetConfigTest : RobolectricTest() {
    private lateinit var activity: DeckPickerWidgetConfig
    private val widgetPreferences = DeckPickerWidgetPreferences(targetContext)

    /**
     * Sets up the test environment before each test.
     *
     * Initializes the `DeckPickerWidgetConfig` activity and associated components like
     * `WidgetPreferences`. This setup is executed before each test method.
     */
    @Before
    override fun setUp() {
        super.setUp()
        ensureNonEmptyCollection()

        val intent =
            Intent(targetContext, DeckPickerWidgetConfig::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 1)
            }

        activity = startActivityNormallyOpenCollectionWithIntent(DeckPickerWidgetConfig::class.java, intent)

        // Ensure deckAdapter is initialized
        runBlocking { activity.initTask.join() }
    }

    /**
     * Tests the functionality of saving selected decks to preferences.
     *
     * This test adds a deck to the adapter and verifies if it gets correctly saved to the
     * `WidgetPreferences`.
     */
    @Test
    fun testSaveSelectedDecksToPreferences() {
        // Add decks to adapter
        val deck1 = SelectableDeck.Deck(1, "Deck 1")
        activity.deckAdapter.addDeck(deck1)

        // Save selected decks
        activity.saveSelectedDecksToPreferencesDeckPickerWidget()

        // Verify saved decks
        val selectedDeckIds = widgetPreferences.getSelectedDeckIdsFromPreferences(testWidgetId).toList()
        assertThat(selectedDeckIds.contains(deck1.deckId), equalTo(true))
    }

    /**
     * Tests the loading of saved preferences into the activity's view.
     *
     * This test saves decks to preferences, then loads them into the activity and checks if the
     * `RecyclerView` displays the correct number of items based on the saved preferences.
     */
    @Test
    fun testLoadSavedPreferences() =
        runTest {
            // Save decks to preferences
            val deckIds = listOf(1L)
            widgetPreferences.saveSelectedDecks(testWidgetId, deckIds.map { it.toString() })

            // Load preferences
            activity.updateViewWithSavedPreferences()

            // Get the RecyclerView and its adapter
            val recyclerView = activity.findViewById<RecyclerView>(R.id.recyclerViewSelectedDecks)
            val adapter = recyclerView.adapter

            // Verify the adapter has the correct item count
            assertThat(adapter?.itemCount, equalTo(deckIds.size))
        }

    /**
     * Tests the visibility of different views based on the selected decks.
     *
     * This test checks the visibility of the placeholder and configuration container views
     * before and after adding a deck.
     */
    @Test
    fun testUpdateViewVisibility() {
        val noDecksPlaceholder = activity.findViewById<View>(R.id.no_decks_placeholder)
        val widgetConfigContainer = activity.findViewById<View>(R.id.widgetConfigContainer)

        // Initially, no decks should be selected
        activity.updateViewVisibility()
        assertThat(noDecksPlaceholder.visibility, equalTo(View.VISIBLE))
        assertThat(widgetConfigContainer.visibility, equalTo(View.GONE))

        // Add a deck and update view visibility
        val deck = SelectableDeck.Deck(1, "Deck 1")
        activity.deckAdapter.addDeck(deck)
        activity.updateViewVisibility()

        assertThat(noDecksPlaceholder.visibility, equalTo(View.GONE))
        assertThat(widgetConfigContainer.visibility, equalTo(View.VISIBLE))
    }

    /**
     * Tests the selection of a deck.
     *
     * This test verifies that when a deck is selected, it gets added to the adapter and displayed
     * in the `RecyclerView`.
     */
    @Test
    fun testOnDeckSelected() {
        val deck = SelectableDeck.Deck(1, "Deck 1")
        activity.onDeckSelected(deck)

        // Verify deck is added to adapter
        val recyclerView = activity.findViewById<RecyclerView>(R.id.recyclerViewSelectedDecks)
        assertThat(recyclerView.adapter?.itemCount, equalTo(1))
    }
}

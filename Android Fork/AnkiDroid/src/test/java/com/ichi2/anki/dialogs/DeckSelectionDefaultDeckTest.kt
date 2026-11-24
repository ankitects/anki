// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.libanki.Consts.DEFAULT_DECK_ID
import com.ichi2.anki.model.SelectableDeck
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class DeckSelectionDefaultDeckTest : RobolectricTest() {
    @Test
    fun `renamed default with cards only in a subdeck can be selected`() {
        col.decks.rename(col.decks.getDefault(), "Words")
        addNoteToDeck(addDeck("Words::Vocabulary"))

        val activity = startRegularActivity<AnkiActivity>()
        var selectedDeck: SelectableDeck? = null
        activity.registerDeckSelectedHandler { selectedDeck = it }
        activity.startDeckSelection(skipEmptyDefault = true)
        advanceRobolectricLooper()

        onView(withText("Words")).inRoot(isDialog()).perform(click())
        assertEquals(SelectableDeck.Deck(DEFAULT_DECK_ID, "Words"), selectedDeck)
    }
}

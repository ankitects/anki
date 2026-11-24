// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.chip.Chip
import com.ichi2.anki.CardBrowser
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.browser.search.StandardSearchFragment
import com.ichi2.anki.browser.search.requireChildFragment
import com.ichi2.anki.dialogs.DeckSelectionDialog
import com.ichi2.anki.settings.Prefs
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.ichi2.anki.ankicommon.R as AnkiCommonR

@RunWith(AndroidJUnit4::class)
class CardBrowserDeckSelectionTest : RobolectricTest() {
    @Test
    fun `deck selected inside search is applied on submission`() {
        addBasicNote("Outside")
        val chosenDeck = addDeck("Chosen").withNote()

        withCardBrowserFragment(useSearchView = true) {
            val currentSearch = activityViewModel.searchRequestFlow.value
            val search = assertNotNull(searchView)
            search.show()
            advanceRobolectricLooper()
            assertTrue(searchViewModel.isScreenOpenFlow.value)
            val standard = requireChildFragment<StandardSearchFragment>(StandardSearchFragment.TAG)

            assertTrue(standard.binding.decksChip.performClick())
            standard.selectDeck("Chosen")

            assertEquals(
                chosenDeck,
                searchViewModel.filtersFlow.value.decks
                    .singleOrNull()
                    ?.id,
            )
            assertEquals(
                "Chosen",
                standard.binding.decksChip.text
                    .toString(),
            )
            assertEquals(currentSearch, activityViewModel.searchRequestFlow.value, "The search has not been submitted")

            search.editText.onEditorAction(IME_ACTION_SEARCH)
            awaitSearch()
            assertEquals(chosenDeck, activityViewModel.deckId)
            assertEquals(1, activityViewModel.rowCount)
        }
    }

    @Test
    fun `main deck chip still filters after closing search`() {
        addBasicNote("Outside")
        val chosenDeck = addDeck("Chosen").withNotes(count = 2)

        withCardBrowserFragment(useSearchView = true) {
            val search = assertNotNull(searchView)
            search.show()
            advanceRobolectricLooper()
            requireChildFragment<StandardSearchFragment>(StandardSearchFragment.TAG)
            search.hide()
            advanceRobolectricLooper()
            assertNull(childFragmentManager.findFragmentByTag(StandardSearchFragment.TAG))

            val chip = requireView().findViewById<Chip>(R.id.decks_chip)
            assertTrue(chip.performClick())
            selectDeck("Chosen")
            awaitSearch()

            assertEquals(chosenDeck, activityViewModel.deckId)
            assertEquals(chosenDeck, activityViewModel.lastDeckId)
            assertEquals("Chosen", chip.text.toString())
            assertEquals(2, activityViewModel.rowCount)

            assertTrue(chip.performClick())
            selectDeck("All decks")
            awaitSearch()

            assertTrue(activityViewModel.hasSelectedAllDecks())
            assertEquals("All decks", chip.text.toString())
            assertEquals(3, activityViewModel.rowCount)
        }
    }

    @Test
    fun `restored deck picker still updates search after browser recreation`() =
        runTest {
            addBasicNote("Outside")
            val chosenDeck = addDeck("Chosen").withNotes(count = 2)

            Prefs.devUsingCardBrowserSearchView = true
            try {
                ActivityScenario.launch(CardBrowser::class.java).use { scenario ->
                    lateinit var browser: CardBrowser
                    scenario.onActivity { browser = it }
                    val original = browser.cardBrowserFragment
                    assertNotNull(original.searchView).show()
                    advanceRobolectricLooper()
                    val standard = original.requireChildFragment<StandardSearchFragment>(StandardSearchFragment.TAG)
                    assertTrue(standard.binding.decksChip.performClick())
                    advanceRobolectricLooper()
                    val picker = standard.requireChildFragment<DeckSelectionDialog>(DeckSelectionDialog.TAG)
                    assertTrue(picker.requireDialog().isShowing)

                    scenario.recreate()
                    advanceRobolectricLooper()
                    scenario.onActivity { browser = it }

                    val restored = browser.cardBrowserFragment
                    assertNotSame(original, restored)
                    val restoredStandard = restored.requireChildFragment<StandardSearchFragment>(StandardSearchFragment.TAG)
                    val restoredPicker = restoredStandard.requireChildFragment<DeckSelectionDialog>(DeckSelectionDialog.TAG)
                    assertNotSame(picker, restoredPicker)
                    assertTrue(restoredPicker.requireDialog().isShowing)
                    assertTrue(restored.searchViewModel.isScreenOpenFlow.value)
                    val currentSearch = restored.activityViewModel.searchRequestFlow.value

                    restoredStandard.selectDeck("Chosen")

                    assertEquals(
                        chosenDeck,
                        restored.searchViewModel.filtersFlow.value.decks
                            .singleOrNull()
                            ?.id,
                    )
                    assertEquals(currentSearch, restored.activityViewModel.searchRequestFlow.value)

                    assertNotNull(restored.searchView).editText.onEditorAction(IME_ACTION_SEARCH)
                    restored.awaitSearch()
                    assertEquals(chosenDeck, restored.activityViewModel.deckId)
                    assertEquals(2, restored.activityViewModel.rowCount)
                }
            } finally {
                Prefs.devUsingCardBrowserSearchView = false
            }
        }

    private fun Fragment.selectDeck(name: String) {
        advanceRobolectricLooper()
        val picker = assertIs<DeckSelectionDialog>(childFragmentManager.findFragmentByTag(DeckSelectionDialog.TAG))
        val list = picker.binding.decks
        advanceRobolectricLooperUntil { list.adapter!!.itemCount > 0 }
        val row =
            (0 until list.adapter!!.itemCount)
                .mapNotNull { list.findViewHolderForAdapterPosition(it) }
                .single {
                    it.itemView
                        .findViewById<TextView>(AnkiCommonR.id.deck_text_view)
                        .text
                        .toString() == name
                }
        assertTrue(row.itemView.performClick())
        advanceRobolectricLooper()
    }

    private suspend fun CardBrowserFragment.awaitSearch() {
        activityViewModel.searchJob?.join()
        advanceRobolectricLooper()
    }
}

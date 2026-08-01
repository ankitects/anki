/*
 *  Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.deckpicker

import android.annotation.SuppressLint
import androidx.annotation.CheckResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.card_rendering.EmptyCardsReport
import anki.card_rendering.emptyCardsReport
import app.cash.turbine.test
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.libanki.Consts
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.Note
import com.ichi2.anki.libanki.emptyCids
import com.ichi2.testutils.ensureOpsExecuted
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber
import kotlin.test.assertEquals

/** Test of [DeckPickerViewModel] */
@RunWith(AndroidJUnit4::class)
class DeckPickerViewModelTest : RobolectricTest() {
    private val viewModel = DeckPickerViewModel()

    @Test
    fun `empty cards - flow`() =
        runTest {
            val cardsToEmpty = createEmptyCards()

            viewModel.emptyCardsNotification.test {
                // test a 'normal' deletion
                viewModel.deleteEmptyCards(cardsToEmpty).join()

                expectMostRecentItem().also {
                    assertThat("cards deleted", it.cardsDeleted, equalTo(EXPECTED_CARDS))
                }

                // ensure a duplicate output is displayed to the user
                val newCardsToEmpty = createEmptyCards()
                viewModel.deleteEmptyCards(newCardsToEmpty).join()

                expectMostRecentItem().also {
                    assertThat("cards deleted: duplicate output", it.cardsDeleted, equalTo(EXPECTED_CARDS))
                }

                // test an empty list: a no-op should inform the user, rather than do nothing
                viewModel.deleteEmptyCards(emptyCardsReport { }).join()

                expectMostRecentItem().also {
                    assertThat("'no cards deleted' is notified", it.cardsDeleted, equalTo(0))
                }
            }
        }

    @Test
    fun `empty cards - undoable`() =
        runTest {
            val cardsToEmpty = createEmptyCards()

            // ChangeManager assert
            ensureOpsExecuted(1) {
                viewModel.deleteEmptyCards(cardsToEmpty).join()
            }

            // backend assert
            assertThat("col undo status", col.undoStatus().undo, equalTo("Empty Cards"))
        }

    @Test
    fun `empty cards - keep notes`() =
        runTest {
            val emptyCardsReport = createEmptyCards()

            viewModel.emptyCardsNotification.test {
                viewModel.deleteEmptyCards(emptyCardsReport, preserveNotes = true).join()

                expectMostRecentItem().also {
                    assertThat("note is retained", it.cardsDeleted, equalTo(EXPECTED_CARDS - 1))
                }

                viewModel.deleteEmptyCards(emptyCardsReport, preserveNotes = false).join()

                expectMostRecentItem().also {
                    assertThat("note is deleted", it.cardsDeleted, equalTo(1))
                }
            }
        }

    @Test
    fun `empty filtered - functionality`() {
        runTest {
            val note = addBasicNote("To", "Filtered")
            val filteredDeckId = moveAllCardsToFilteredDeck(assertOn = note)

            viewModel.emptyFilteredDeck(filteredDeckId).join()

            assertThat("deck was reset", note.firstCard().did, equalTo(Consts.DEFAULT_DECK_ID))
        }
    }

    @Test
    fun `empty filtered - flows`() {
        runTest {
            viewModel.flowOfDeckCountsChanged.test {
                val filteredDeckId = moveAllCardsToFilteredDeck()
                expectNoEvents()
                viewModel.emptyFilteredDeck(filteredDeckId).join()
                awaitItem()
                expectNoEvents()
                viewModel.emptyFilteredDeck(filteredDeckId).join()
                awaitItem()
            }
        }
    }

    @Test
    fun `empty filtered - undoable`() {
        runTest {
            val filteredDeckId = moveAllCardsToFilteredDeck()

            // ChangeManager assert
            ensureOpsExecuted(1) {
                viewModel.emptyFilteredDeck(filteredDeckId).join()
            }

            // backend assert
            assertThat("col undo status", col.undoStatus().undo, equalTo("Empty"))
        }
    }

    /**
     * Creates a note with 3 cards, all empty
     *
     * This allows us to test the 'keep note' functionality only affects the first card
     */
    @CheckResult
    @SuppressLint("CheckResult")
    private suspend fun createEmptyCards(): EmptyCardsReport {
        addNoteUsingNoteTypeName("Cloze", "{{c1::Hello}} {{c3::There}} {{c2::World}}", "").apply {
            setField(0, "No cloze")
            col.updateNote(this)
        }
        return withCol { getEmptyCards() }.also { report ->
            assertThat("there are empty cards", report.emptyCids(), not(report.emptyCids().isEmpty()))
            Timber.d("created %d empty cards: [%s]", report.emptyCids().size, report.emptyCids())
        }
    }

    /** test helper to use [deleteEmptyCards] with the original test `preserveNotes` value */
    private fun DeckPickerViewModel.deleteEmptyCards(report: EmptyCardsReport) = deleteEmptyCards(report, preserveNotes = false)

    /**
     * Moves all cards to a deck named "Filtered"
     *
     * If there are no notes, one is created
     * @return The [DeckId] of the filtered deck
     */
    private fun moveAllCardsToFilteredDeck(assertOn: Note = addBasicNote("To", "Filtered")): DeckId =
        addDynamicDeck("Filtered", "").also { did ->
            assertThat("filter - did", assertOn.firstCard().did, equalTo(did))
            assertThat("filter - odid", assertOn.firstCard().oDid, equalTo(Consts.DEFAULT_DECK_ID))
        }

    companion object {
        private const val EXPECTED_CARDS: Int = 3
    }

    @Test
    fun `ensure collapsed decks are also deleted`() {
        runTest {
            val deckIdA = addDeck("A")
            val subDeckIdA1 = addDeck("A::A1")
            val subDeckIdA2 = addDeck("A::A2")
            // add other decks as well as control
            addDeck("B")
            addDeck("B:B1")
            viewModel.flowOfDisableShortcuts.test {
                viewModel.reloadDeckCounts().join()
                viewModel.disableDeckAndChildrenShortcuts(deckIdA)
                val actual = awaitItem()
                val expected =
                    listOf(
                        deckIdA.toString(),
                        subDeckIdA1.toString(),
                        subDeckIdA2.toString(),
                    )
                assertEquals(expected, actual)
            }
        }
    }

    @Test
    fun `request context menu - flow`() {
        runTest {
            val deckId = addDeck("Deck A")

            viewModel.flowOfShowContextMenu.test {
                viewModel.requestContextMenu(deckId).join()

                assertEquals(deckId, awaitItem())
            }
        }
    }

    @Test
    fun `request right click context menu - flow`() {
        runTest {
            val deckId = addDeck("Deck B")
            val x = 10f
            val y = 20f

            viewModel.flowOfShowRightClickContextMenu.test {
                viewModel.requestRightClickContextMenu(deckId, x, y).join()

                val item = awaitItem()
                assertEquals(deckId, item.deckId)
                assertEquals(x, item.x)
                assertEquals(y, item.y)
            }
        }
    }
}

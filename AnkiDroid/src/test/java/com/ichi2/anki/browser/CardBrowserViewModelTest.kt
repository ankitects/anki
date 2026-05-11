/*
 *  Copyright (c) 2023 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.browser

import androidx.core.content.edit
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.TurbineTestContext
import app.cash.turbine.test
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CardBrowser
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.Flag
import com.ichi2.anki.NoteEditorActivity
import com.ichi2.anki.NoteEditorFragment
import com.ichi2.anki.browser.CardBrowserColumn.ANSWER
import com.ichi2.anki.browser.CardBrowserColumn.CARD
import com.ichi2.anki.browser.CardBrowserColumn.CHANGED
import com.ichi2.anki.browser.CardBrowserColumn.CREATED
import com.ichi2.anki.browser.CardBrowserColumn.DECK
import com.ichi2.anki.browser.CardBrowserColumn.DUE
import com.ichi2.anki.browser.CardBrowserColumn.EASE
import com.ichi2.anki.browser.CardBrowserColumn.EDITED
import com.ichi2.anki.browser.CardBrowserColumn.FSRS_DIFFICULTY
import com.ichi2.anki.browser.CardBrowserColumn.FSRS_RETRIEVABILITY
import com.ichi2.anki.browser.CardBrowserColumn.FSRS_STABILITY
import com.ichi2.anki.browser.CardBrowserColumn.INTERVAL
import com.ichi2.anki.browser.CardBrowserColumn.LAPSES
import com.ichi2.anki.browser.CardBrowserColumn.NOTE_TYPE
import com.ichi2.anki.browser.CardBrowserColumn.ORIGINAL_POSITION
import com.ichi2.anki.browser.CardBrowserColumn.QUESTION
import com.ichi2.anki.browser.CardBrowserColumn.REVIEWS
import com.ichi2.anki.browser.CardBrowserColumn.SFLD
import com.ichi2.anki.browser.CardBrowserColumn.TAGS
import com.ichi2.anki.browser.CardBrowserLaunchOptions.DeepLink
import com.ichi2.anki.browser.CardBrowserLaunchOptions.ScrollToCard
import com.ichi2.anki.browser.CardBrowserLaunchOptions.SystemContextMenu
import com.ichi2.anki.browser.CardBrowserViewModel.ChangeMultiSelectMode
import com.ichi2.anki.browser.CardBrowserViewModel.ChangeMultiSelectMode.MultiSelectCause
import com.ichi2.anki.browser.CardBrowserViewModel.ChangeMultiSelectMode.SingleSelectCause
import com.ichi2.anki.browser.CardBrowserViewModel.ChangeNoteTypeResponse
import com.ichi2.anki.browser.CardBrowserViewModel.Companion.STATE_MULTISELECT_VALUES
import com.ichi2.anki.browser.CardBrowserViewModel.RowSelection
import com.ichi2.anki.browser.CardBrowserViewModel.ToggleSelectionState.SELECT_ALL
import com.ichi2.anki.browser.CardBrowserViewModel.ToggleSelectionState.SELECT_NONE
import com.ichi2.anki.browser.RepositionCardsRequest.NoRepositionableCardsError
import com.ichi2.anki.browser.RepositionCardsRequest.RepositionData
import com.ichi2.anki.browser.search.SavedSearch
import com.ichi2.anki.export.ExportDialogFragment
import com.ichi2.anki.flagCardForNote
import com.ichi2.anki.libanki.BrowserConfig
import com.ichi2.anki.libanki.Card
import com.ichi2.anki.libanki.CardId
import com.ichi2.anki.libanki.CardType
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.Note
import com.ichi2.anki.libanki.QueueType
import com.ichi2.anki.libanki.QueueType.ManuallyBuried
import com.ichi2.anki.libanki.QueueType.New
import com.ichi2.anki.libanki.testutils.AnkiTest
import com.ichi2.anki.model.CardStateFilter
import com.ichi2.anki.model.CardsOrNotes
import com.ichi2.anki.model.LegacySortType
import com.ichi2.anki.model.LegacySortType.NO_SORTING
import com.ichi2.anki.model.LegacySortType.SORT_FIELD
import com.ichi2.anki.model.SelectableDeck
import com.ichi2.anki.model.SortType
import com.ichi2.anki.servicelayer.NoteService
import com.ichi2.anki.setFlagFilterSync
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.utils.ext.ifNotZero
import com.ichi2.anki.utils.ext.ignoreAccentsInSearch
import com.ichi2.testutils.IntentAssert
import com.ichi2.testutils.JvmTest
import com.ichi2.testutils.createTransientDirectory
import com.ichi2.testutils.ensureNoOpsExecuted
import com.ichi2.testutils.ensureOpWithHandler
import com.ichi2.testutils.ensureOpsExecuted
import com.ichi2.testutils.ext.reopenWithLanguage
import com.ichi2.testutils.mockIt
import kotlinx.coroutines.flow.first
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.containsInAnyOrder
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.instanceOf
import org.hamcrest.Matchers.lessThan
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.nullValue
import org.junit.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertInstanceOf
import org.junit.runner.RunWith
import timber.log.Timber
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.io.path.pathString
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class CardBrowserViewModelTest : JvmTest() {
    override fun setUp() {
        super.setUp()
        Prefs.putString(com.ichi2.anki.R.string.pref_browser_no_sorting, null)
    }

    @Test
    fun `delete search history - Issue 14989`() =
        runViewModelTest {
            saveSearch("hello", "aa").also { result ->
                assertThat(result, equalTo(SaveSearchResult.SUCCESS))
            }
            savedSearches().also { searches ->
                assertThat("filters after saving", searches.size, equalTo(1))
                val search = searches.single()
                assertThat(
                    "filters after saving",
                    search,
                    equalTo(
                        SavedSearch(
                            "hello",
                            "aa",
                        ),
                    ),
                )
            }
            removeSavedSearch("hello")
            assertThat("filters should be empty after removing", savedSearches().size, equalTo(0))
        }

    @Test
    fun `saving search with same name fails`() =
        runViewModelTest {
            saveSearch("hello", "aa").also { result ->
                assertThat("saving a new search succeeds", result, equalTo(SaveSearchResult.SUCCESS))
            }
            saveSearch("hello", "bb").also { result ->
                assertThat("saving with same name fails", result, equalTo(SaveSearchResult.ALREADY_EXISTS))
            }
        }

    @Test
    fun `change deck in notes mode 15444`() =
        runViewModelTest {
            val newDeck = addDeck("World")
            selectDefaultDeck()

            repeat(5) {
                addBasicAndReversedNote()
            }
            setCardsOrNotes(CardsOrNotes.NOTES)
            waitForSearchResults()

            selectRowsWithPositions(0, 2)

            val allCardIds = queryAllSelectedCardIds()
            assertThat(allCardIds.size, equalTo(4))

            moveSelectedCardsToDeck(newDeck).join()

            for (cardId in allCardIds) {
                assertThat("Deck should be changed", col.getCard(cardId).did, equalTo(newDeck))
            }

            val hasSomeDecksUnchanged = cards.any { row -> col.getCard(row.requireCardId(cardsOrNotes)).did != newDeck }
            assertThat("some decks are unchanged", hasSomeDecksUnchanged)
        }

    /** Issue 18307: Reposition should work in notes mode and affect all cards */
    @Test
    fun `reposition in notes mode affects all cards 18307`() =
        runViewModelNotesTest(notes = 3) {
            // Select first 2 notes (should be 4 cards total)
            selectRowsWithPositions(0, 1)

            val allCardIds = queryAllSelectedCardIds()
            assertThat("Should have 4 cards (2 notes × 2 cards)", allCardIds.size, equalTo(4))

            // Reposition to position 100
            val count = repositionSelectedRows(position = 100, step = 1, shuffle = false, shift = false)
            assertThat("Should reposition 4 cards", count, equalTo(4))

            // Verify all 4 cards are repositioned (due >= 100)
            for (cardId in allCardIds) {
                val card = col.getCard(cardId)
                assertThat(
                    "Card $cardId should be repositioned to position >= 100",
                    card.due,
                    greaterThanOrEqualTo(100),
                )
            }

            // Verify the 3rd note's cards (2 cards) are NOT repositioned
            val unselectedRow = cards[2]
            val unselectedCardIds =
                BrowserRowCollection(cardsOrNotes, mutableListOf(unselectedRow))
                    .queryCardIds()
            for (cardId in unselectedCardIds) {
                val card = col.getCard(cardId)
                assertThat(
                    "Unselected card $cardId should not be repositioned",
                    card.due,
                    not(greaterThanOrEqualTo(100)),
                )
            }
        }

    /** Issue 18307: Reset progress should work in notes mode and affect all cards */
    @Test
    fun `reset progress in notes mode affects all cards 18307`() =
        runViewModelNotesTest(notes = 3) {
            // Give all cards some review history
            val allCards = col.findCards("deck:current")
            for (cardId in allCards) {
                moveToReviewQueue(col.getCard(cardId))
            }

            setCardsOrNotes(CardsOrNotes.NOTES)
            waitForSearchResults()

            selectRowsWithPositions(0, 1)

            val selectedCardIds = queryAllSelectedCardIds()
            assertThat("Should have 4 cards", selectedCardIds.size, equalTo(4))

            // Reset progress
            // TODO: This test directly calls forgetCards, but the actual logic includes
            // restorePosition and resetCounts parameters handled in ForgetCardsDialog (Activity-level)
            col.sched.forgetCards(ids = selectedCardIds)

            // Verify all 4 cards are reset to NEW
            for (cardId in selectedCardIds) {
                val card = col.getCard(cardId)
                assertThat("Card $cardId should be new", card.type, equalTo(CardType.New))
                assertThat("Card $cardId queue should be new", card.queue, equalTo(New))
            }

            // Verify unselected note's cards (2 cards) still have review history
            val unselectedRow = cards[2]
            val unselectedCardIds =
                BrowserRowCollection(cardsOrNotes, mutableListOf(unselectedRow))
                    .queryCardIds()
            for (cardId in unselectedCardIds) {
                val card = col.getCard(cardId)
                assertThat(
                    "Unselected card $cardId should still be review type",
                    card.type,
                    equalTo(CardType.Rev),
                )
                assertThat(
                    "Unselected card $cardId should still have reps",
                    card.reps,
                    equalTo(5),
                )
            }
        }

    /** 7420  */
    @Test
    fun addCardDeckIsNotSetIfAllDecksSelectedAfterLoad() =
        runViewModelTest {
            addDeck("NotDefault")

            assertThat("All decks should not be selected", !hasSelectedAllDecks())

            setSelectedDeck(SelectableDeck.AllDecks)

            assertThat("All decks should be selected", hasSelectedAllDecks())

            val addIntent = CardBrowser.createAddNoteLauncher(this).toIntent(mockIt())
            val bundle = addIntent.getBundleExtra(NoteEditorActivity.FRAGMENT_ARGS_EXTRA)
            IntentAssert.doesNotHaveExtra(bundle, NoteEditorFragment.EXTRA_DID)
        }

    @Test
    fun filterByFlagDisplaysProperly() =
        runViewModelTest {
            val cardWithRedFlag = addBasicNote("Card with red flag", "Reverse")
            flagCardForNote(cardWithRedFlag, Flag.RED)

            val cardWithGreenFlag = addBasicNote("Card with green flag", "Reverse")
            flagCardForNote(cardWithGreenFlag, Flag.GREEN)

            val anotherCardWithRedFlag = addBasicNote("Second card with red flag", "Reverse")
            flagCardForNote(anotherCardWithRedFlag, Flag.RED)

            setFlagFilterSync(Flag.RED)

            assertThat("Flagged cards should be returned", rowCount, equalTo(2))
        }

    @Test
    fun `toggle bury - single selection`() =
        runViewModelTest(notes = 1) {
            assertThat("bury with no cards selected does nothing", toggleBury(), nullValue())

            selectRowAtPosition(0)

            // bury & unbury
            toggleBury().also {
                assertNotNull(it)
                assertThat("toggle bury initially buries", it.wasBuried)
                assertThat("1 card is buried", it.count, equalTo(1))
            }
            toggleBury().also {
                assertNotNull(it)
                assertThat("toggle bury unburied on second press", !it.wasBuried)
                assertThat("1 card is unburied", it.count, equalTo(1))
            }
        }

    @Test
    fun `toggle bury - mixed selection`() =
        runViewModelTest(notes = 2) {
            selectRowAtPosition(0)
            toggleBury()
            selectRowAtPosition(1)

            assertThat(selectedRowCount(), equalTo(2))

            // 1 row is buried and 1 is unburied
            toggleBury().also {
                assertNotNull(it)
                assertThat("toggle bury with mixed selection buried", it.wasBuried)
                assertThat("2 cards are affected", it.count, equalTo(2))
            }

            assertThat(selectedRowCount(), equalTo(2))

            toggleBury().also {
                assertNotNull(it)
                assertThat("toggle bury with all buried performs 'unbury'", !it.wasBuried)
                assertThat("2 cards are affected", it.count, equalTo(2))
            }

            toggleBury().also {
                assertNotNull(it)
                assertThat("toggle bury with all unburied performs 'bury'", it.wasBuried)
                assertThat("2 cards are affected", it.count, equalTo(2))
            }

            assertThat(selectedRowCount(), equalTo(2))
        }

    @Test
    fun `toggle bury - queue changes`() =
        runViewModelTest(notes = 1) {
            selectRowAtPosition(0)

            suspend fun getQueue() = col.getCard(queryAllSelectedCardIds().single()).queue

            assertThat("initial queue = NEW", getQueue(), equalTo(New))

            toggleBury()

            assertThat("bury: queue -> MANUALLY_BURIED", getQueue(), equalTo(ManuallyBuried))

            toggleBury()

            assertThat("unbury: queue -> NEW", getQueue(), equalTo(New))
        }

    @Test
    fun `default init`() =
        runTest {
            viewModel().apply {
                assertThat(searchTerms, equalTo(""))
            }
        }

    @Test
    fun `Card Browser menu init`() =
        runTest {
            viewModel(intent = SystemContextMenu("Hello")).apply {
                assertThat(searchTerms, equalTo("Hello"))
            }
        }

    @Test
    fun `Deep Link init`() =
        runTest {
            viewModel(intent = DeepLink("Hello")).apply {
                assertThat(searchTerms, equalTo("Hello"))
            }
        }

    @Test
    fun `EXTRA_DECK_ID intent opens the specified deck`() =
        runTest {
            val deckId = addDeck("New")
            val savedState = SavedStateHandle(mapOf(CardBrowserViewModel.EXTRA_DECK_ID to deckId))
            viewModel(savedStateHandle = savedState).apply {
                assertThat("intent deck is selected", deckId, equalTo(this.deckId))
            }
        }

    @Test
    fun `EXTRA_DECK_ID intent persists deck to lastDeckIdRepository`() =
        runTest {
            val deckId = addDeck("New")
            val savedState = SavedStateHandle(mapOf(CardBrowserViewModel.EXTRA_DECK_ID to deckId))
            viewModel(savedStateHandle = savedState).apply {
                assertThat("deck persisted for next launch", lastDeckId, equalTo(deckId))
            }
        }

    @Test
    fun `no EXTRA_DECK_ID falls back to lastDeckIdRepository`() =
        runTest {
            val deckId = addDeck("Persisted")
            viewModel(lastDeckId = deckId).apply {
                assertThat("repository value is used", deckId, equalTo(this.deckId))
            }
        }

    @Test
    fun `EXTRA_DECK_ID for unknown deck falls back to lastDeckIdRepository`() =
        runTest {
            val persisted = addDeck("Persisted")
            val unknownDeckId: DeckId = 9_999_999_999L
            val savedState = SavedStateHandle(mapOf(CardBrowserViewModel.EXTRA_DECK_ID to unknownDeckId))
            viewModel(lastDeckId = persisted, savedStateHandle = savedState).apply {
                assertThat("unknown intent deck is ignored", persisted, equalTo(this.deckId))
            }
        }

    @Test
    fun `user deck change survives process death`() =
        runTest {
            val intentDeckId = addDeck("From intent")
            val userDeckId = addDeck("User selection")
            val savedState = SavedStateHandle(mapOf(CardBrowserViewModel.EXTRA_DECK_ID to intentDeckId))

            val persistentRepo =
                object : LastDeckIdRepository {
                    override var lastDeckId: DeckId? = null
                }

            // setup: initial launch + select new deck
            viewModel(
                savedStateHandle = savedState,
                lastDeckIdRepository = persistentRepo,
            ).apply {
                assertThat("intent honored on first launch", deckId, equalTo(intentDeckId))
                setSelectedDeck(SelectableDeck.Deck(deckId = userDeckId, name = "User selection"))
                assertThat("user choice persisted to repository", persistentRepo.lastDeckId, equalTo(userDeckId))
            }

            // intent does not override user selection
            viewModel(
                savedStateHandle = savedState,
                lastDeckIdRepository = persistentRepo,
            ).apply {
                assertThat("user choice survives recreation", deckId, equalTo(userDeckId))
            }
        }

    @Test
    fun `sort order from notes is selected - 16514`() {
        col.config.set("sortType", "noteCrt")
        col.config.set("noteSortType", "_field_Frequency")
        CardsOrNotes.NOTES.saveToCollection(col)

        runViewModelTest(notes = 1) {
            assertThat("1 row returned", rowCount, equalTo(1))
        }
    }

    fun `selected rows are refreshed`() =
        runViewModelTest(notes = 2) {
            flowOfSelectedRows.test {
                // initially, flowOfSelectedRows should not have emitted anything
                expectNoEvents()

                selectAll()
                assertThat("initial selection", awaitItem().size, equalTo(2))

                selectNone()
                assertThat("deselected all", awaitItem().size, equalTo(0))

                toggleRowSelectionAtPosition(0)
                assertThat("selected row", awaitItem().size, equalTo(1))

                toggleRowSelectionAtPosition(0)
                assertThat("deselected rows", awaitItem().size, equalTo(0))

                selectRowAtPosition(0)
                assertThat("select rows explicitly", awaitItem().size, equalTo(1))

                selectRowAtPosition(0)
                expectNoEvents()

                selectRowsBetweenPositions(0, 1)
                assertThat("select rows between positions", awaitItem().size, equalTo(2))

                selectRowsBetweenPositions(0, 1)
                expectNoEvents()
            }
        }

    @Test
    fun `selected card and note ids`() {
        val notes = List(2) { addBasicAndReversedNote() }

        val nids = notes.map { it.id }.toTypedArray()
        val cids = notes.flatMap { it.cids() }.toTypedArray()

        runViewModelTest {
            setCardsOrNotes(CardsOrNotes.CARDS).join()
            selectAll()
            assertThat("cards: rowCount", rowCount, equalTo(4))
            assertThat("cards: cids", queryAllSelectedCardIds(), containsInAnyOrder(*cids))
            assertThat("cards: nids", queryAllSelectedNoteIds(), containsInAnyOrder(*nids))

            selectNone()

            setCardsOrNotes(CardsOrNotes.NOTES).join()
            selectAll()
            assertThat("notes: rowCount", rowCount, equalTo(2))
            assertThat("notes: cids", queryAllSelectedCardIds(), containsInAnyOrder(*cids))
            assertThat("notes: nids", queryAllSelectedNoteIds(), containsInAnyOrder(*nids))
        }
    }

    @Test
    fun `executing select all twice does nothing`() =
        runViewModelTest(notes = 2) {
            assertThat(selectedRowCount(), equalTo(0))
            selectAll()
            assertThat(selectedRowCount(), equalTo(2))
            selectAll()
            assertThat(selectedRowCount(), equalTo(2))
        }

    @Test
    fun `cards - changing column index 1`() =
        runViewModelTest {
            flowOfActiveColumns.test {
                ignoreEventsDuringViewModelInit()

                assertThat("default column1 value", column1, equalTo(SFLD))

                setColumn(0, QUESTION)

                assertThat("flowOfColumn1", awaitItem().columns[0], equalTo(QUESTION))
                assertThat("column1", column1, equalTo(QUESTION))

                // expect no change if the value is selected again
                setColumn(0, QUESTION)
                expectNoEvents()
            }
        }

    @Test
    fun `cards - changing column index 2`() =
        runViewModelTest {
            flowOfActiveColumns.test {
                ignoreEventsDuringViewModelInit()

                assertThat("default column2Index value", column2, equalTo(CARD))

                setColumn(1, ANSWER)

                assertThat("flowOfColumnIndex2", awaitItem().columns[1], equalTo(ANSWER))
                assertThat("column2Index", column2, equalTo(ANSWER))

                // expect no change if the value is selected again
                setColumn(1, ANSWER)
                expectNoEvents()
            }
        }

    @Test
    fun `notes - changing column index 1`() =
        runViewModelNotesTest {
            flowOfActiveColumns.test {
                ignoreEventsDuringViewModelInit()

                assertThat("default column1 value", column1, equalTo(SFLD))

                setColumn(0, QUESTION)

                assertThat("flowOfColumn1", awaitItem().columns[0], equalTo(QUESTION))
                assertThat("column1", column1, equalTo(QUESTION))

                // expect no change if the value is selected again
                setColumn(0, QUESTION)
                expectNoEvents()
            }
        }

    @Test
    fun `notes - changing column index 2`() =
        runViewModelNotesTest {
            flowOfActiveColumns.test {
                ignoreEventsDuringViewModelInit()

                assertThat("default column2Index value", column2, equalTo(NOTE_TYPE))

                setColumn(1, ANSWER)

                assertThat("flowOfColumnIndex2", awaitItem().columns[1], equalTo(ANSWER))
                assertThat("column2Index", column2, equalTo(ANSWER))

                // expect no change if the value is selected again
                setColumn(1, ANSWER)
                expectNoEvents()
            }
        }

    @Test
    fun `mode mismatch - changing columns`() {
        // a user can use the options to update the inactive column set
        // this should cause an update to the backend, but no frontend events should be obtained
        runViewModelTest {
            flowOfActiveColumns.test {
                ignoreEventsDuringViewModelInit()
                assertThat("cardsOrNotes", cardsOrNotes, equalTo(CardsOrNotes.CARDS))

                // we're in cards so editing NOTES should not update visible columns
                setColumn(1, FSRS_STABILITY, CardsOrNotes.NOTES)
                expectNoEvents()

                BrowserColumnCollection.load(sharedPrefs(), CardsOrNotes.NOTES).apply {
                    assertThat("notes is changed", this.columns[1], equalTo(FSRS_STABILITY))
                }
                BrowserColumnCollection.load(sharedPrefs(), CardsOrNotes.CARDS).apply {
                    assertThat("cards is unchanged", this.columns[1], not(equalTo(FSRS_STABILITY)))
                }
            }
        }
    }

    @Test
    fun `change card order to NO_SORTING is a no-op if done twice`() =
        runViewModelTest {
            flowOfSearchState.test {
                ignoreEventsDuringViewModelInit()
                assertThat("initial order", order, equalTo(SORT_FIELD))
                assertThat("initial direction", !orderAsc)

                // changing the order performs a search & changes order
                changeCardOrder(NO_SORTING)
                expectMostRecentItem()
                assertThat("order changed", order, equalTo(NO_SORTING))
                assertThat("changed direction", !orderAsc)

                waitForSearchResults()

                // pressing 'no sorting' again is a no-op
                changeCardOrder(NO_SORTING)
                expectNoEvents()
                assertThat("order unchanged", order, equalTo(NO_SORTING))
                assertThat("unchanged direction", !orderAsc)
            }
        }

    @Test
    fun `change direction of results`() =
        runViewModelTest {
            flowOfSearchState.test {
                ignoreEventsDuringViewModelInit()
                assertThat("initial order", order, equalTo(SORT_FIELD))
                assertThat("initial direction", !orderAsc)

                // changing the order performs a search & changes order
                changeCardOrder(LegacySortType.EASE)
                expectMostRecentItem()
                assertThat("order changed", order, equalTo(LegacySortType.EASE))
                assertThat("changed direction is the default", !orderAsc)

                waitForSearchResults()

                // pressing 'ease' again changes direction
                changeCardOrder(LegacySortType.EASE)
                expectMostRecentItem()
                assertThat("order unchanged", order, equalTo(LegacySortType.EASE))
                assertThat("direction is changed", orderAsc)
            }
        }

    /*
     * Note: suspension behavior has been questioned from a performance perspective and is
     * subject to change
     *
     * Needing to know the 'suspended' status of all cards, makes this O(n).
     * Anki uses the O(1) approach of using the first selected card
     */

    @Test
    fun `suspend cards - cards - no selection`() =
        runViewModelTest(notes = 2) {
            ensureNoOpsExecuted {
                toggleSuspendCards()

                assertAllUnsuspended("no selection")
            }
        }

    @Test
    fun `suspend - cards - all suspended`() =
        runViewModelTest(notes = 2) {
            suspendAll()
            ensureOpsExecuted(1) {
                selectAll()
                toggleSuspendCards()

                assertAllUnsuspended("all suspended: unsuspend")
            }
        }

    @Test
    fun `suspend - cards - some suspended`() =
        runViewModelTest(notes = 2) {
            suspendCards(cards.first().requireCardId(cardsOrNotes))
            ensureOpsExecuted(1) {
                selectAll()
                toggleSuspendCards()

                assertAllSuspended("mixed selection: suspend all")
            }
        }

    @Test
    fun `suspend - cards - none suspended`() =
        runViewModelTest(notes = 2) {
            ensureOpsExecuted(1) {
                selectAll()
                toggleSuspendCards()

                assertAllSuspended("none suspended: suspend all")
            }
        }

    @Test
    fun `suspend - notes - no selection`() =
        runViewModelNotesTest(notes = 2) {
            ensureNoOpsExecuted {
                toggleSuspendCards()
                assertAllUnsuspended("none selected: do nothing")
            }
        }

    @Test
    fun `suspend - notes - all suspended`() =
        runViewModelNotesTest(notes = 2) {
            suspendAll()
            ensureOpsExecuted(1) {
                selectAll()
                toggleSuspendCards()
                assertAllUnsuspended("all suspended -> unsuspend")
            }
        }

    @Test
    fun `suspend - notes - some notes suspended`() =
        runViewModelNotesTest(notes = 2) {
            val nid = cards.first().cardOrNoteId
            suspendNote(col.getNote(nid))
            ensureOpsExecuted(1) {
                selectAll()
                toggleSuspendCards()
                assertAllSuspended("mixed selection -> suspend all")
            }
        }

    @Test
    fun `suspend - notes - some cards suspended`() =
        runViewModelNotesTest(notes = 2) {
            // this suspends o single cid from a nid
            suspendCards(cards.first().requireCardId(cardsOrNotes))
            ensureOpsExecuted(1) {
                selectAll()
                toggleSuspendCards()
                assertAllSuspended("mixed selection -> suspend all")
            }
        }

    fun `suspend cards - notes - none suspended`() =
        runViewModelNotesTest(notes = 2) {
            ensureOpsExecuted(1) {
                selectAll()
                toggleSuspendCards()
                assertAllSuspended("none suspended -> suspend all")
            }
        }

    @Test
    fun `export - no selection`() =
        runViewModelTest(notes = 2) {
            assertNull(querySelectionExportData(), "no export data if no selection")
        }

    @Test
    fun `export - one card`() =
        runViewModelTest(notes = 2) {
            selectRowsWithPositions(0)

            val (exportType, ids) = assertNotNull(querySelectionExportData())

            assertThat(exportType, equalTo(ExportDialogFragment.ExportType.Cards))
            assertThat(ids, hasSize(1))

            assertThat(ids.single(), equalTo(cards[0].cardOrNoteId))
        }

    @Test
    fun `export - one note`() =
        runViewModelNotesTest(notes = 2) {
            selectRowsWithPositions(0)

            val (exportType, ids) = assertNotNull(querySelectionExportData())

            assertThat(exportType, equalTo(ExportDialogFragment.ExportType.Notes))
            assertThat(ids, hasSize(1))

            assertThat(ids.single(), equalTo(cards[0].cardOrNoteId))
        }

    @Test
    fun `selection is maintained after toggle mark 14950`() =
        runViewModelTest(notes = 5) {
            selectRowsWithPositions(0, 1, 2)

            assertThat("3 rows are selected", selectedRows.size, equalTo(3))
            assertThat("selection is not marked", queryAllSelectedNotes().all { !it.isMarked() })

            toggleMark()

            assertThat("3 rows are still selected", selectedRows.size, equalTo(3))
            assertThat("selection is now marked", queryAllSelectedNotes().all { it.isMarked() })
        }

    @Test
    fun `changing note types changes columns`() =
        runViewModelTest {
            // BrowserColumnCollection contains BOTH notes and cards column configs
            BrowserColumnCollection.update(sharedPrefs(), CardsOrNotes.NOTES) {
                it[0] = QUESTION
                it[1] = FSRS_DIFFICULTY
                true
            }

            assertThat("column 2 before", column2, not(equalTo(FSRS_DIFFICULTY)))

            setCardsOrNotes(CardsOrNotes.NOTES)

            assertThat("column 2 after", column2, equalTo(FSRS_DIFFICULTY))
        }

    @Test
    fun `cards - delete one`() =
        runViewModelTest(notes = 2) {
            assertThat("initial card count", col.cardCount(), equalTo(2))
            selectRowsWithPositions(0)

            ensureOpsExecuted(1) {
                deleteSelectedNotes()
            }

            assertThat("1 card deleted", col.cardCount(), equalTo(1))
            assertThat("no selection after", selectedRowCount(), equalTo(0))
            assertThat("one row removed", rowCount, equalTo(1))
        }

    @Test
    fun `notes - delete one`() =
        runViewModelNotesTest(notes = 2) {
            assertThat("initial card count", col.cardCount(), equalTo(4))
            selectRowsWithPositions(0)

            ensureOpsExecuted(1) {
                deleteSelectedNotes()
            }

            assertThat("1 note deleted - 2 cards deleted", col.cardCount(), equalTo(2))
            assertThat("no selection after", selectedRowCount(), equalTo(0))
            assertThat("one row removed", rowCount, equalTo(1))
        }

    @Test
    fun `delete note - flowOfCardsUpdated emits`() =
        runViewModelTest(notes = 2) {
            selectRowsWithPositions(0)

            flowOfCardsUpdated.test {
                expectNoEvents()
                assertEquals(1, deleteSelectedNotes(), "1 note deleted")
                awaitItem()
            }
        }

    @Test
    fun `delete note - handler passed to undoableOp prevents double refresh`() =
        runViewModelTest(notes = 2) {
            selectRowsWithPositions(0)

            // flowOfCardsUpdated already performs a refresh
            ensureOpWithHandler(this) { deleteSelectedNotes() }
        }

    /** @see <a href="https://github.com/ankidroid/Anki-Android/issues/20556">#20556</a> */
    @Test
    fun `delete note - no crash when cardIdToBeScrolledTo is deleted in NOTES mode`() {
        val cardId = addBasicNote().firstCard().id
        runViewModelNotesTest(notes = 1, options = ScrollToCard(cardId)) {
            selectAll()
            deleteSelectedNotes()

            assertThat("card should be deleted", col.cardCount(), equalTo(0))

            assertDoesNotThrow { launchSearchForCards() }
        }
    }

    @Test
    fun `cardIdToBeScrolledTo is cleared after first scroll`() {
        val cardId = addBasicNote().firstCard().id
        runViewModelTest(notes = 1, options = ScrollToCard(cardId)) {
            assertThat(
                "cardIdToBeScrolledTo should be cleared after initial scroll",
                cardIdToBeScrolledTo,
                nullValue(),
            )
        }
    }

    @Test
    fun `valid scrollRequest if cardIdToBeScrolledTo is valid`() {
        val cardId = addBasicNote().firstCard().id
        runViewModelTest(options = ScrollToCard(cardId), initMode = InitMode.MANUAL) {
            flowOfScrollRequest.test {
                manualInit()
                val cardId = expectMostRecentItem().rowId.cardOrNoteId
                assertThat("A valid cardId is produced", cardId, equalTo(cardId))
            }
        }
    }

    @Test
    fun `no ScrollRequest if cardIdToBeScrolledTo is invalid`() {
        runViewModelTest(options = ScrollToCard(1234), initMode = InitMode.MANUAL) {
            flowOfScrollRequest.test {
                manualInit()
                expectNoEvents()
                assertThat("cardIdToBeScrolledTo is null if invalid", cardIdToBeScrolledTo, nullValue())
            }
        }
    }

    @Test
    fun `no crash if cardIdToBeScrolledTo is invalid - NOTES mode`() {
        runViewModelNotesTest(options = ScrollToCard(1234)) {
            assertThat("cardIdToBeScrolledTo is null if invalid", cardIdToBeScrolledTo, nullValue())
        }
    }

    @Test
    fun `notes - search for marked`() =
        runTest {
            addBasicAndReversedNote("hello", "world").also { note ->
                NoteService.toggleMark(note)
            }
            addBasicAndReversedNote("hello2", "world")

            runViewModelNotesTest {
                searchForMarkedNotes()
                waitForSearchResults()
                assertThat("A marked note is found", rowCount, equalTo(1))
            }
        }

    @Test
    fun `cards - search for marked`() =
        runTest {
            addBasicAndReversedNote("hello", "world").also { note ->
                NoteService.toggleMark(note)
            }
            addBasicAndReversedNote("hello2", "world")

            runViewModelTest {
                searchForMarkedNotes()
                waitForSearchResults()
                assertThat("both cards of a marked note are found", rowCount, equalTo(2))
            }
        }

    @Test
    fun `notes - search for suspended`() =
        runTest {
            addBasicAndReversedNote("hello", "world").also { note ->
                col.sched.suspendCards(listOf(note.cardIds(col).first()))
            }
            addBasicAndReversedNote("hello2", "world")

            runViewModelNotesTest {
                searchForSuspendedCards()
                waitForSearchResults()
                assertThat("A suspended card is found for the note", rowCount, equalTo(1))
            }
        }

    @Test
    fun `cards - search for suspended`() =
        runTest {
            addBasicAndReversedNote("hello", "world").also { note ->
                col.sched.suspendCards(listOf(note.cardIds(col).first()))
            }

            runViewModelTest {
                searchForSuspendedCards()
                waitForSearchResults()
                assertThat("one suspended cards of a note is found", rowCount, equalTo(1))
            }
        }

    @Test
    fun `notes - preview intent`() =
        runViewModelNotesTest(notes = 5) {
            assertThat("note count", col.noteCount(), equalTo(5))
            assertThat("card count", col.cardCount(), equalTo(10))
            val data = queryPreviewIntentData()
            assertThat(data.currentIndex, equalTo(0))

            data.idsFile.getIds().also { actualCardIds ->
                assertThat("previewing a note previews cards", actualCardIds, hasSize(5))

                val firstCardIds =
                    col
                        .findCards("")
                        .filter { col.getCard(it).ord == 0 }

                assertThat("first card ids", firstCardIds, hasSize(5))

                // TODO: this behaviour is unconfirmed in Anki Desktop
                assertThat(
                    "previewing first card in each note",
                    actualCardIds.toLongArray(),
                    equalTo(firstCardIds.toLongArray()),
                )
            }
        }

    @Test
    fun `cards - preview intent - no selection`() =
        runViewModelTest(notes = 2) {
            val data = queryPreviewIntentData()
            assertThat(data.currentIndex, equalTo(0))
            assertThat(data.idsFile.getIds(), hasSize(2))
        }

    @Test
    fun `cards - preview intent - selection`() =
        runViewModelTest(notes = 2) {
            selectRowsWithPositions(0).also {
                val data = queryPreviewIntentData()
                assertThat(data.currentIndex, equalTo(0))
                assertThat(data.idsFile.getIds(), hasSize(2))
            }

            selectNone()

            // ensure currentIndex changes
            selectRowsWithPositions(1).also {
                val data = queryPreviewIntentData()
                assertThat(data.currentIndex, equalTo(1))
                assertThat(data.idsFile.getIds(), hasSize(2))
            }
        }

    @Test
    fun `sound tags regression test`() {
        addBasicNote("[sound:david.mp3]")

        showMediaFilenamesPreference = false

        BrowserColumnCollection.update(AnkiDroidApp.sharedPreferencesProvider.sharedPrefs(), CardsOrNotes.CARDS) {
            it[0] = QUESTION
            true
        }

        runViewModelTest {
            waitForSearchResults()
            val (row, _) = this.transformBrowserRow(this.cards.single())
            val question = row.getCells(0)
            assertThat(question.text, equalTo(EXPECTED_SOUND))
        }
    }

    @Test
    fun `reposition - non new card`() {
        addBasicNote("New")
        addRevBasicNoteDueToday("Review", "Today")

        runViewModelTest {
            selectAll()
            assertThat("2 selected rows", selectedRows.size, equalTo(2))

            val repositionResult = prepareToRepositionCards()
            assertInstanceOf<RepositionData>(repositionResult, "mixed selection should still return reposition data").apply {
                val unsupported =
                    assertInstanceOf<UnsupportedCardCount.Count>(
                        unsupportedCardCount,
                        "unsupported card count should be exact",
                    )
                assertThat("unsupported card count", unsupported.value, equalTo(1))
            }

            val count = repositionSelectedRows(position = 50, step = 1, shuffle = false, shift = false)
            assertThat("only new cards should be repositioned", count, equalTo(1))
        }
    }

    @Test
    fun `reposition - new cards`() {
        repeat(2) { addBasicNote("New") }

        runViewModelTest {
            selectAll()

            val repositionResult = prepareToRepositionCards()
            assertInstanceOf<RepositionData>(repositionResult, "non-error").apply {
                assertThat("queueTop", queueTop, equalTo(1))
                assertThat("queueBottom", queueBottom, equalTo(2))

                assertThat(
                    "message",
                    this.toHumanReadableContent(),
                    equalTo(
                        """
                        Queue top: ⁨1⁩
                        Queue bottom: ⁨2⁩
                        """.trimIndent(),
                    ),
                )
            }
        }
    }

    @Test
    fun `reposition - suspended new card`() {
        addBasicNote("New").suspendAll()
        addBasicNote("New")

        runViewModelTest {
            selectAll()

            val cards = queryAllSelectedCardIds().map(col::getCard)
            assertTrue("at least one card is suspended") { cards.any { it.queue == QueueType.Suspended } }
            assertTrue("all suspended cards are still new type") {
                cards.filter { it.queue == QueueType.Suspended }.all { it.type == CardType.New }
            }

            val repositionResult = prepareToRepositionCards()

            // Should succeed because it's still a New card, even though suspended
            assertInstanceOf<RepositionData>(repositionResult, "suspended new card should be repositionable").apply {
                assertThat("queueTop", queueTop, equalTo(1))
                assertThat("queueBottom", queueBottom, equalTo(2))
            }
        }
    }

    @Test
    fun `reposition - all non new cards`() {
        addRevBasicNoteDueToday("Review1", "Today")
        addRevBasicNoteDueToday("Review2", "Today")

        runViewModelTest {
            selectAll()
            assertThat("2 selected rows", selectedRows.size, equalTo(2))

            val repositionResult = prepareToRepositionCards()
            assertInstanceOf<NoRepositionableCardsError>(repositionResult, "all non-new cards error")
        }
    }

    @Test
    fun `preview - no notes`() {
        // add a card: a preview should
        addBasicNote("Hello", "").firstCard().update {
            did = addDeck("Non-default Deck")
        }
        runViewModelTest {
            assertThat("no rows", rowCount, equalTo(0))
            this.previewColumnHeadings(CardsOrNotes.CARDS).apply {
                assertTrue("no sample values") { allColumns.all { it.sampleValue == null } }
                val (displayed, _) = this
                assertThat(
                    "displayed: cards",
                    displayed.map { it.columnType },
                    equalTo(listOf(SFLD, CARD, DUE, DECK)),
                )
            }
        }

        runViewModelNotesTest {
            assertThat("no rows", rowCount, equalTo(0))
            this.previewColumnHeadings(CardsOrNotes.NOTES).apply {
                assertTrue("no sample values") { allColumns.all { it.sampleValue == null } }
                val (displayed, _) = this
                assertThat(
                    "displayed: notes",
                    displayed.map { it.columnType },
                    equalTo(listOf(SFLD, NOTE_TYPE, CARD, TAGS)),
                )
            }
        }
    }

    @Test
    fun `preview - cards`() {
        runViewModelTest(notes = 1) {
            for (preview in previewColumnHeadings(CardsOrNotes.CARDS).allColumns) {
                val (expectedLabel, expectedValue) =
                    when (preview.columnType) {
                        SFLD -> Pair("Sort Field", "Front")
                        QUESTION -> Pair("Question", "Front")
                        ANSWER -> Pair("Answer", "Back")
                        DECK -> Pair("Deck", "Default")
                        TAGS -> Pair("Tags", "")
                        CARD -> Pair("Card Type", "Card 1")
                        DUE -> Pair("Due", "New #\u20681\u2069")
                        NOTE_TYPE -> Pair("Note Type", "Basic")
                        EASE -> Pair("Ease", "(new)")
                        INTERVAL -> Pair("Interval", "(new)")
                        LAPSES -> Pair("Lapses", "0")
                        REVIEWS -> Pair("Reviews", "0")
                        ORIGINAL_POSITION -> Pair("Position", "1")
                        CHANGED, CREATED, EDITED -> {
                            assertDate(preview.sampleValue)
                            continue
                        }
                        FSRS_DIFFICULTY -> Pair("Difficulty", "")
                        FSRS_RETRIEVABILITY -> Pair("Retrievability", "")
                        FSRS_STABILITY -> Pair("Stability", "")
                    }
                assertThat("${preview.columnType} value", preview.sampleValue, equalTo(expectedValue))
                assertThat("${preview.columnType} label", preview.label, equalTo(expectedLabel))
            }
        }
    }

    @Test
    fun `preview - notes`() {
        runViewModelNotesTest(notes = 1) {
            for (preview in previewColumnHeadings(CardsOrNotes.NOTES).allColumns) {
                val (expectedLabel, expectedValue) =
                    when (preview.columnType) {
                        SFLD -> Pair("Sort Field", "Front")
                        QUESTION -> Pair("Question", "Front")
                        ANSWER -> Pair("Answer", "Back")
                        DECK -> Pair("Deck", "Default")
                        TAGS -> Pair("Tags", "")
                        CARD -> Pair("Cards", "2")
                        DUE -> Pair("Due", "")
                        NOTE_TYPE -> Pair("Note Type", "Basic (and reversed card)")
                        EASE -> Pair("Avg. Ease", "(new)")
                        INTERVAL -> Pair("Avg. Interval", "")
                        LAPSES -> Pair("Lapses", "0")
                        REVIEWS -> Pair("Reviews", "0")
                        ORIGINAL_POSITION -> Pair("Position", "1")
                        CHANGED, CREATED, EDITED -> {
                            assertDate(preview.sampleValue)
                            continue
                        }
                        FSRS_DIFFICULTY -> Pair("Difficulty", "")
                        FSRS_RETRIEVABILITY -> Pair("Retrievability", "")
                        FSRS_STABILITY -> Pair("Stability", "")
                    }
                assertThat("${preview.columnType} value", preview.sampleValue, equalTo(expectedValue))
                assertThat("${preview.columnType} label", preview.label, equalTo(expectedLabel))
            }
        }
    }

    @Test
    fun `preview - round trip`() {
        runViewModelTest {
            previewColumnHeadings(cardsOrNotes).also { columns ->
                assertThat(
                    "initial columns",
                    columns.first.map { it.columnType },
                    contains(SFLD, CARD, DUE, DECK),
                )
            }

            @Suppress("UNUSED_VARIABLE", "unused")
            val unused = updateActiveColumns(listOf(CARD, DECK, SFLD, DUE, FSRS_STABILITY), cardsOrNotes)

            previewColumnHeadings(cardsOrNotes).also { columns ->
                assertThat(
                    "updated columns",
                    columns.first.map { it.columnType },
                    contains(CARD, DECK, SFLD, DUE, FSRS_STABILITY),
                )
            }
        }
    }

    @Test
    fun `preview does not write to SharedPreferences - issue 19885`() =
        runViewModelTest(notes = 1) {
            // A crash/close here caused all columns to be visible.
            sharedPrefs().edit { remove(BrowserConfig.ACTIVE_CARD_COLUMNS_KEY) }

            previewColumnHeadings(CardsOrNotes.CARDS)

            assertThat(
                "activeCols must not be written during preview",
                sharedPrefs().contains(BrowserConfig.ACTIVE_CARD_COLUMNS_KEY),
                equalTo(false),
            )
        }

    @Suppress("SpellCheckingInspection") // German
    @Test
    fun `columns headings - language change`() =
        runViewModelTest {
            fun firstHeading() = flowOfColumnHeadings.value.first().label

            assertThat("English", firstHeading(), equalTo("Sort Field"))

            col.reopenWithLanguage("de")
            onReinit()

            assertThat("German", firstHeading(), equalTo("Sortierfeld"))
        }

    @Test
    fun `deck name with quotes is properly escaped in search query`() =
        runViewModelTest {
            val deckWithQuotes = addDeck("Test\"Quotes\"In\"Deck")
            setSelectedDeck(deckWithQuotes)

            val searchString = requireNotNull(with(col) { searchRequestFlow.value.toSearchString() })
            assertThat(
                "Quotes in deck name should be escaped with backslashes",
                searchString.getOrThrow().value,
                equalTo("deck:Test\\\"Quotes\\\"In\\\"Deck"),
            )
        }

    @Test
    fun `toggle is 'deselect' if only row is selected`() =
        runViewModelTest(notes = 1) {
            this.toggleRowSelectionAtPosition(0)
            assertThat("toggle selection", flowOfToggleSelectionState.value, equalTo(SELECT_NONE))
            assertThat("multiselect after toggle", isInMultiSelectMode, equalTo(true))
        }

    @Test
    fun `toggle is 'select all' after deselection - multi note`() =
        runViewModelTest(notes = 2) {
            this.toggleRowSelectionAtPosition(0)
            assertThat("toggle selection", flowOfToggleSelectionState.value, equalTo(SELECT_NONE))
        }

    @Test
    fun `toggle all - multi note`() =
        runViewModelTest(notes = 2) {
            flowOfToggleSelectionState.test {
                assertThat(awaitItem(), equalTo(SELECT_NONE))

                // select all
                selectAll()?.join()
                expectNoEvents()
                assertThat("multiselect after toggle", isInMultiSelectMode, equalTo(true))

                // select none
                toggleSelectAllOrNone()?.join()
                assertThat("toggle selection after select none", awaitItem(), equalTo(SELECT_ALL))
                assertThat("multiselect after toggle 2", isInMultiSelectMode, equalTo(true))

                // select all manually
                toggleRowSelectionAtPosition(0).join()
                expectNoEvents() // remains 'select all'

                // select the last row - emits 'select none'
                toggleRowSelectionAtPosition(1).join()
                assertThat("toggle selection after select all manually", awaitItem(), equalTo(SELECT_NONE))

                // end select mode
                endMultiSelectMode(SingleSelectCause.NavigateBack)
                assertThat("multiselect after toggle 3", isInMultiSelectMode, equalTo(false))
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `toggleSelectAllOrNone - SELECT_ALL if partial selection`() =
        runViewModelTest(notes = 3) {
            flowOfToggleSelectionState.test {
                assertThat("toggle selection defaults to 'none' before selection", awaitItem(), equalTo(SELECT_NONE))
                toggleRowSelectionAtPosition(0).join()
                assertThat("toggle selection defaults to 'all' if 1/3 selected", awaitItem(), equalTo(SELECT_ALL))
                toggleRowSelectionAtPosition(2).join()
                expectNoEvents() // "toggle selection remains at 'all' if 2/3 selected"

                toggleSelectAllOrNone()?.join()
                assertThat(selectedRowCount(), equalTo(3))
                assertThat("toggle selection defaults to 'none' if 3/3 selected", awaitItem(), equalTo(SELECT_NONE))
            }
        }

    @Test
    fun `tap disables selection mode`() {
        // although a user can 'select 0' with SELECT_NONE and stay in selection mode,
        // it felt unintuitive to tap a row/checkbox and not have it disable the selection mode

        runViewModelTest(notes = 3) {
            this.toggleRowSelectionAtPosition(2)
            this.toggleRowSelectionAtPosition(1)
            this.toggleRowSelectionAtPosition(0)
            this.toggleRowSelectionAtPosition(0)
            this.toggleRowSelectionAtPosition(1)
            assertThat("selection -> in multiselect", isInMultiSelectMode, equalTo(true))
            this.toggleRowSelectionAtPosition(2)
            assertThat("tap last row -> disable multiselect", isInMultiSelectMode, equalTo(false))
        }
    }

    @Test
    fun `multiselect toggle state is restored`() {
        val handle = SavedStateHandle()
        runViewModelTest(savedStateHandle = handle, notes = 1) {
            assertThat(isInMultiSelectMode, equalTo(false))
            assertInstanceOf<SingleSelectCause.Other>(handle.multiselectMode, "initial multiselect state")
            selectAll()
            assertInstanceOf<MultiSelectCause.Other>(handle.multiselectMode, "multiselect after select all")
        }

        runViewModelTest(savedStateHandle = handle) {
            assertInstanceOf<MultiSelectCause.Other>(handle.multiselectMode, "multiselect state restoration")
            endMultiSelectMode(SingleSelectCause.NavigateBack)
            assertInstanceOf<SingleSelectCause.NavigateBack>(handle.multiselectMode, "initial multiselect state")
        }
    }

    @Test
    fun `multiselect checked state is restored`() {
        val handle = SavedStateHandle()
        var idOfSelectedRow: CardOrNoteId? = null
        runViewModelTest(savedStateHandle = handle, notes = 2, initMode = InitMode.NO_DELAY) {
            selectRowAtPosition(1)
            idOfSelectedRow = selectedRows.single()
            // HACK: easiest way to add it to the bundle. This is called on destruction
            handle[STATE_MULTISELECT_VALUES] = generateExpensiveSavedState()
        }

        runViewModelTest(savedStateHandle = handle, initMode = InitMode.NO_DELAY) {
            assertThat("row is still selected", selectedRows, hasSize(1))
            assertThat("same row is selected", selectedRows.single(), equalTo(idOfSelectedRow))
        }
    }

    @Test
    fun `change note type - no selection`() =
        runViewModelTest {
            flowOfChangeNoteType.test {
                requestChangeNoteType().join()
                assertThat("no selection", expectMostRecentItem(), instanceOf(ChangeNoteTypeResponse.NoSelection::class.java))
            }
        }

    @Test
    fun `change note type - mixed selection`() {
        addBasicNote()
        addClozeNote("{{c1::test}}")
        runViewModelTest {
            selectAll()
            flowOfChangeNoteType.test {
                requestChangeNoteType().join()
                assertThat(
                    "mixed note type selection",
                    expectMostRecentItem(),
                    instanceOf(ChangeNoteTypeResponse.MixedSelection::class.java),
                )
            }
        }
    }

    @Test
    fun `change note type - single valid selection`() =
        runViewModelTest(notes = 1) {
            val noteIds = this.cards.queryNoteIds()
            assertThat(noteIds, hasSize(1))

            flowOfChangeNoteType.test {
                selectAll()
                requestChangeNoteType().join()

                val item = expectMostRecentItem()
                assertThat("single valid selection", item, instanceOf(ChangeNoteTypeResponse.ChangeNoteType::class.java))
                val selection = item as ChangeNoteTypeResponse.ChangeNoteType
                assertThat(selection.noteIds, equalTo(noteIds))
            }
        }

    @Test
    fun `change note type - valid multiselect`() =
        runViewModelTest(notes = 2) {
            val noteIds = this.cards.queryNoteIds()
            assertThat(noteIds, hasSize(2))

            flowOfChangeNoteType.test {
                selectAll()
                requestChangeNoteType().join()

                val item = expectMostRecentItem()
                assertThat("multi valid selection", item, instanceOf(ChangeNoteTypeResponse.ChangeNoteType::class.java))
                val selection = item as ChangeNoteTypeResponse.ChangeNoteType
                assertThat(selection.noteIds, equalTo(noteIds))
            }
        }

    @Test
    fun `saving a blank query does nothing`() =
        runViewModelTest {
            flowOfSaveSearchNamePrompt.test {
                updateQueryText("AAA")
                updateQueryText("")

                saveCurrentSearch()

                expectNoEvents()
            }
        }

    @Test
    fun `saving a search opens the 'name' dialog`() =
        runViewModelTest {
            flowOfSaveSearchNamePrompt.test {
                updateQueryText("AAA")

                saveCurrentSearch()

                assertThat("save search is opened", expectMostRecentItem(), equalTo("AAA"))
            }
        }

    @Test
    fun `notes mode - a note maps to all its cards`() =
        runViewModelNotesTest(notes = 1) {
            // One Basic+Reversed note = 2 cards
            assertThat("one note", rowCount, equalTo(1))
            val row = cards.single()
            // When in NOTES mode, selecting a row should map to all its cards
            val cardIds = BrowserRowCollection(cardsOrNotes, mutableListOf(row)).queryCardIds()
            assertThat(
                "a single note expands to all its cards",
                cardIds,
                hasSize(2),
            )
            for (cardId in cardIds) {
                assertNotNull(col.getCard(cardId))
            }
        }

    @Test
    fun `accented tags are searchable if ignoring accents`() {
        addBasicNote().update { tags = mutableListOf("être") }
        addBasicNote("hello").update { tags = mutableListOf("être") }
        addBasicNote("hêllo").update { tags = mutableListOf("être") }

        col.config.ignoreAccentsInSearch = true

        runViewModelTest {
            filterByTags(listOf("être"), CardStateFilter.ALL_CARDS)

            assertThat(searchTerms, equalTo("(tag:être)"))
            assertThat("all tagged cards are returned", rowCount, equalTo(3))

            updateQueryText("tag:être hêllo")
            launchSearchForCards(tempSearchQuery!!)
            assertThat("input is unchanged", searchTerms, equalTo("tag:être hêllo"))
        }
    }

    /**
     * Regression test: parentheses are required until we fully use SearchRequest
     *
     * Otherwise, the 'AND' takes precedence over the 'OR':
     *
     * `tag:a OR tag:b a` is parsed as `tag:a OR (tag:b a)`
     */
    @Test
    fun `single tag filter is parenthesized to preserve precedence`() {
        addBasicNote().update { tags = mutableListOf("foo", "bar") }

        runViewModelTest {
            filterByTags(listOf("foo", "bar"), CardStateFilter.ALL_CARDS)

            assertThat(
                "single-tag filter wraps the tag clause so user-appended terms append safely",
                searchTerms,
                equalTo("(tag:foo OR tag:bar)"),
            )
        }
    }

    @Test
    fun `ignoring accents behavior`() {
        // when ignoring accents, either 'hello' or 'hêllo' match.
        addBasicNote("hello")
        addBasicNote("hêllo")

        col.config.ignoreAccentsInSearch = true

        runViewModelTest {
            updateQueryText("hêllo")
            launchSearchForCards(tempSearchQuery!!)

            assertThat("hello and hêllo are matched", rowCount, equalTo(2))
            assertThat("input is unchanged", searchTerms, equalTo("hêllo"))

            updateQueryText("hello")
            launchSearchForCards(tempSearchQuery!!)

            assertThat("hello and hêllo are matched", rowCount, equalTo(2))
            assertThat("input is unchanged", searchTerms, equalTo("hello"))
        }
    }

    @Test
    fun `updating sort type launches search`() =
        runViewModelTest {
            flowOfSearchState.test {
                expectNoEvents()

                setSortType(SortType.NoOrdering)

                expectMostRecentItem()
            }
        }

    @Test
    fun `updating sort type updates flows - no ordering`() =
        runViewModelTest {
            assertEquals(LegacySortType.SORT_FIELD, order)

            setSortType(SortType.NoOrdering)

            assertEquals(LegacySortType.NO_SORTING, order)
        }

    @Test
    fun `updating sort type updates flows - known column`() =
        runViewModelTest {
            assertEquals(LegacySortType.SORT_FIELD, order)

            setSortType(SortType.CollectionOrdering(BrowserColumnKey("cardDue"), true))

            assertEquals(LegacySortType.DUE_TIME, order)
        }

    @Test
    fun `updating sort type updates order`() =
        runViewModelTest {
            assertEquals(false, orderAsc)

            setSortType(SortType.CollectionOrdering(BrowserColumnKey("cardDue"), true))

            assertEquals(true, orderAsc)
        }

    @Test
    fun `sort type integration test`() {
        val firstId = addBasicNote("a").firstCard().id
        addBasicNote("b")
        val lastId = addBasicNote("c").firstCard().id

        runViewModelTest {
            assertEquals(firstId, this.cards[0].cardOrNoteId)

            setSortType(SortType.CollectionOrdering(BrowserColumnKey("noteFld"), reverse = true))

            assertEquals(lastId, this.cards[0].cardOrNoteId)

            setSortType(SortType.CollectionOrdering(BrowserColumnKey("noteFld"), reverse = false))

            assertEquals(firstId, this.cards[0].cardOrNoteId)
        }
    }

    private fun assertDate(str: String?) {
        // 2025-01-09 @ 18:06
        assertNotNull(str)
        assertTrue("date expected: $str") { str[4] == '-' && str[11] == '@' }
    }

    private var showMediaFilenamesPreference: Boolean
        // hardcoded @string/pref_display_filenames_in_browser_key
        get() = AnkiDroidApp.sharedPreferencesProvider.sharedPrefs().getBoolean("card_browser_show_media_filenames", false)
        set(value) =
            AnkiDroidApp.sharedPreferencesProvider.sharedPrefs().edit {
                putBoolean("card_browser_show_media_filenames", value)
            }

    private fun runViewModelNotesTest(
        notes: Int = 0,
        initMode: InitMode = InitMode.AUTOMATIC,
        options: CardBrowserLaunchOptions? = null,
        testBody: suspend CardBrowserViewModel.() -> Unit,
    ) = runTest {
        CardsOrNotes.NOTES.saveToCollection(col)
        repeat(notes) {
            // ensure 1 note = 2 cards
            addBasicAndReversedNote()
        }
        val viewModel =
            CardBrowserViewModel(
                lastDeckIdRepository = SharedPreferencesLastDeckIdRepository(),
                cacheDir = createTransientDirectory(),
                options = options,
                preferences = AnkiDroidApp.sharedPreferencesProvider,
                isFragmented = false,
                manualInit = initMode == InitMode.MANUAL || initMode == InitMode.AUTOMATIC,
                savedStateHandle = SavedStateHandle(),
            )
        // makes ignoreValuesFromViewModelLaunch work under test
        if (initMode == InitMode.AUTOMATIC) {
            viewModel.manualInit()
        }
        testBody(viewModel)
    }

    private fun runViewModelTest(
        notes: Int = 0,
        initMode: InitMode = InitMode.AUTOMATIC,
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
        options: CardBrowserLaunchOptions? = null,
        testBody: suspend CardBrowserViewModel.() -> Unit,
    ) = runTest {
        repeat(notes) {
            addBasicNote()
        }
        notes.ifNotZero { count -> Timber.d("added %d notes", count) }
        val viewModel =
            CardBrowserViewModel(
                lastDeckIdRepository = SharedPreferencesLastDeckIdRepository(),
                cacheDir = createTransientDirectory(),
                options = options,
                preferences = AnkiDroidApp.sharedPreferencesProvider,
                isFragmented = false,
                manualInit = initMode == InitMode.MANUAL || initMode == InitMode.AUTOMATIC,
                savedStateHandle = savedStateHandle,
            )
        // makes ignoreValuesFromViewModelLaunch work under test
        if (initMode == InitMode.AUTOMATIC) {
            viewModel.manualInit()
        }
        testBody(viewModel)
        Timber.d("end runViewModelTest")
    }

    companion object {
        const val EXPECTED_SOUND = "\uD83D\uDD09david.mp3\uD83D\uDD09"
        const val EXPECTED_TTS = "\uD83D\uDCACTest\uD83D\uDCAC"

        private suspend fun viewModel(
            lastDeckId: DeckId? = null,
            intent: CardBrowserLaunchOptions? = null,
            mode: CardsOrNotes = CardsOrNotes.CARDS,
            savedStateHandle: SavedStateHandle = SavedStateHandle(),
            lastDeckIdRepository: LastDeckIdRepository =
                object : LastDeckIdRepository {
                    override var lastDeckId: DeckId? = lastDeckId
                },
        ): CardBrowserViewModel {
            // default is CARDS, do nothing in this case
            if (mode == CardsOrNotes.NOTES) {
                CollectionManager.withCol { mode.saveToCollection(this@withCol) }
            }

            val cache = File(createTempDirectory().pathString)
            return CardBrowserViewModel(
                lastDeckIdRepository = lastDeckIdRepository,
                cacheDir = cache,
                options = intent,
                isFragmented = false,
                preferences = AnkiDroidApp.sharedPreferencesProvider,
                savedStateHandle = savedStateHandle,
            ).apply {
                invokeInitialSearch()
            }
        }
    }

    enum class InitMode {
        /** init { } runs as normal */
        NO_DELAY,

        /** init { } is delayed, via a call to [CardBrowserViewModel.manualInit] is called */
        AUTOMATIC,

        /** init is not run, and a manual invocation of [CardBrowserViewModel.manualInit] is necessary */
        MANUAL,
    }
}

@Suppress("SameParameterValue")
private fun CardBrowserViewModel.selectRowsWithPositions(vararg positions: Int) {
    for (pos in positions) {
        selectRowAtPosition(pos)
    }
}

/**
 * Helper for testing flows:
 *
 * A MutableStateFlow can either emit a value or not emit a value
 * depending on whether a consumer subscribes before or after the task launched
 * from init is completed
 */
private fun <T> TurbineTestContext<T>.ignoreEventsDuringViewModelInit() {
    try {
        expectMostRecentItem()
    } catch (_: AssertionError) {
        // explicitly ignored: no items
    }
}

private suspend fun CardBrowserViewModel.waitForSearchResults() {
    searchJob?.join()
}

private suspend fun CardBrowserViewModel.waitForInit() {
    this.flowOfInitCompleted.first { initCompleted -> initCompleted }
}

internal suspend fun CardBrowserViewModel.invokeInitialSearch() {
    Timber.d("waiting for init")
    waitForInit()
    Timber.d("init completed")
    // For legacy reasons, we need to know the number of cards to render when performing a search
    // This will be removed once we handle #11889
    // numberOfCardsToRenderFlow.emit(1)
    Timber.v("initial search completed")
}

private fun AnkiTest.assertAllSuspended(context: String) {
    val cards = col.findCards("").map { col.getCard(it) }
    assertThat("performance", cards.size, lessThan(10))

    for (card in cards) {
        assertThat(
            "$context: all cards are unsuspended",
            card.queue,
            equalTo(QueueType.Suspended),
        )
    }
}

private fun AnkiTest.assertAllUnsuspended(context: String) {
    val cards = col.findCards("").map { col.getCard(it) }
    assertThat("performance", cards.size, lessThan(10))

    for (card in cards) {
        assertThat(
            "$context: all cards unsuspended",
            card.queue,
            not(equalTo(QueueType.Suspended)),
        )
    }
}

private fun AnkiTest.suspendAll() {
    col.findCards("").also { cards ->
        col.sched.suspendCards(col.findCards(""))
        Timber.d("suspended %d cards", cards.size)
    }
}

/**
 * Suspends the cards associated with the provided [Card IDs][CardId].
 */
private fun AnkiTest.suspendCards(vararg cardIds: CardId) {
    col.sched.suspendCards(ids = cardIds.toList())
}

private fun AnkiTest.suspendNote(note: Note) {
    col.sched.suspendCards(note.cardIds(col))
}

suspend fun CardOrNoteId.requireCardId(cardsOrNotes: CardsOrNotes): CardId =
    toCardId(cardsOrNotes) ?: error("Expected card ID to be non-null for $this in $cardsOrNotes mode")

val CardBrowserViewModel.column1
    get() = this.activeColumns[0]

val CardBrowserViewModel.column2
    get() = this.activeColumns[1]

fun CardBrowserViewModel.setColumn(
    index: Int,
    column: CardBrowserColumn,
    cardsOrNotes: CardsOrNotes = this.cardsOrNotes,
): Boolean {
    val newColumns = activeColumns.toMutableList()
    newColumns[index] = column
    return updateActiveColumns(newColumns, cardsOrNotes)
}

val Pair<List<ColumnWithSample>, List<ColumnWithSample>>.allColumns
    get() = this.first + this.second

private fun CardBrowserViewModel.toggleRowSelectionAtPosition(position: Int) = toggleRowSelection(cards[position].toRowSelection())

fun CardBrowserViewModel.selectRowAtPosition(position: Int) {
    val rowId = this.getRowAtPosition(position)
    this.selectRowAtPosition(position, rowId.toRowSelection())
}

fun CardOrNoteId.toRowSelection() = RowSelection(rowId = this, topOffset = 0)

private val SavedStateHandle.multiselectMode
    get() = get<ChangeMultiSelectMode>("multiselect")

/**
 * Helper function to move a card to the review queue with review history.
 * Simulates a card that has been reviewed multiple times.
 */
private fun CardBrowserViewModelTest.moveToReviewQueue(card: Card) {
    card.update {
        queue = QueueType.Rev
        type = CardType.Rev
        reps = 5
        lapses = 1
        due = 50
    }
}

suspend fun CardBrowserViewModel.saveSearch(
    title: String,
    query: String,
) = saveSearch(SavedSearch(title, query))

suspend fun CardBrowserViewModel.setSelectedDeck(targetDid: DeckId) {
    val deck = SelectableDeck.fromCollection(includeFiltered = false).single { it.deckId == targetDid }
    setSelectedDeck(deck)
}

context(test: AnkiTest)
private suspend fun CardBrowserViewModel.queryAllSelectedNotes() = queryAllSelectedNoteIds().map { test.col.getNote(it) }

private suspend fun Note.isMarked(): Boolean = NoteService.isMarked(this)

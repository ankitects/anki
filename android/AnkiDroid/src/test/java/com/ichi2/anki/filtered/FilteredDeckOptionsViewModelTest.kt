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

package com.ichi2.anki.filtered

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.decks.Deck
import anki.decks.DeckKt.FilteredKt.searchTerm
import anki.decks.DeckKt.filtered
import anki.decks.filteredDeckForUpdate
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.Flag
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.flagCardForNote
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.Note
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import net.ankiweb.rsdroid.BackendException
import net.ankiweb.rsdroid.exceptions.BackendDeckIsFilteredException
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.jupiter.api.assertInstanceOf
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class FilteredDeckOptionsViewModelTest : RobolectricTest() {
    @Test
    fun `building a filtered deck with no cards fails unless allow empty is checked`() =
        runTest {
            addDeck(TEST_DECK_NAME, true)
            withViewModel {
                assertNull(current.throwable)
                assertEquals(current.filter1State.search, "deck:$TEST_DECK_NAME is:due")
                build()
                assertInstanceOf<FilteredDeckOptions>(state.value)
                assertInstanceOf<BackendDeckIsFilteredException>(current.throwable)
                // allow empty
                onAllowEmptyChange(true)
                assertNull(current.throwable)
                build()
                assertInstanceOf<DeckBuilt>(state.value)
            }
        }

    @Test
    fun `receiving DeckId of non filtered deck produces state with error`() =
        runTest {
            val deckId = addDeck(TEST_DECK_NAME)
            withViewModel(deckId) {
                assertInstanceOf<Initializing>(state.value)
                assertNotNull((state.value as Initializing).throwable)
            }
        }

    @Test
    fun `building produces a filtered deck which respects selected constraints`() =
        runTest {
            val initial = withCol { decks.byName(TEST_DECK_NAME) }
            assertNull(initial)
            val parentDeckId = addDeck("A", true)
            val n1 = addNoteToDeckA { flagCardForNote(this, Flag.RED) }
            val n2 = addNoteToDeckA { flagCardForNote(this, Flag.RED) }
            val n3 = addNoteToDeckA { flagCardForNote(this, Flag.GREEN) }
            val n4 = addNoteToDeckA { flagCardForNote(this, Flag.GREEN) }
            val n5 = addNoteToDeckA { flagCardForNote(this, Flag.BLUE) }
            withViewModel {
                onDeckNameChange(TEST_DECK_NAME)
                // first filter query "\deck:A\" flag:1", limit 1
                onSearchChange(FilterIndex.First, "\"deck:A\" flag:1")
                onLimitChange(FilterIndex.First, "1")
                onCardsOptionsChange(FilterIndex.First, 5) // Order added
                // second filter query "\deck:A\" flag:3", limit 1
                onSecondFilterStatusChange(true)
                onSearchChange(FilterIndex.Second, "\"deck:A\" flag:3")
                onLimitChange(FilterIndex.Second, "1")
                onCardsOptionsChange(FilterIndex.First, 5) // Order added
                build()
                assertInstanceOf<DeckBuilt>(state.value)
            }
            val deck = withCol { decks.byName(TEST_DECK_NAME) }
            assertNotNull(deck)
            assertTrue(deck.isFiltered)
            assertThat(deck.name, equalTo(TEST_DECK_NAME))
            val count = withCol { decks.cardCount(deck.id) }
            assertThat(count, equalTo(2))
            assertEquals(deck.id, n1.firstCard().did)
            assertEquals(deck.id, n3.firstCard().did)
            val originalCount = withCol { decks.cardCount(parentDeckId) }
            assertThat(originalCount, equalTo(3))
            assertEquals(parentDeckId, n2.firstCard().did)
            assertEquals(parentDeckId, n4.firstCard().did)
            assertEquals(parentDeckId, n5.firstCard().did)
        }

    @Test
    fun `rebuilding produces a filtered deck which respects updated constraints`() =
        runTest {
            val initial = withCol { decks.byName(TEST_DECK_NAME) }
            assertNull(initial)
            val parentDeckId = addDeck("A", true)
            val n1 = addNoteToDeckA { flagCardForNote(this, Flag.RED) }
            val n2 = addNoteToDeckA { flagCardForNote(this, Flag.GREEN) }
            val n3 = addNoteToDeckA { flagCardForNote(this, Flag.BLUE) }
            // building initial filtered deck
            withViewModel {
                onDeckNameChange(TEST_DECK_NAME)
                // only first filter with query "\deck:A\" flag:1", limit 1
                onSearchChange(FilterIndex.First, "\"deck:A\" flag:1")
                onLimitChange(FilterIndex.First, "1")
                build()
                assertInstanceOf<DeckBuilt>(state.value)
            }
            val deck = withCol { decks.byName(TEST_DECK_NAME) }
            assertNotNull(deck)
            assertTrue(deck.isFiltered)
            assertThat(deck.name, equalTo(TEST_DECK_NAME))
            val count = withCol { decks.cardCount(deck.id) }
            assertThat(count, equalTo(1))
            assertEquals(deck.id, n1.firstCard().did)
            // updating filtered deck
            withViewModel(deck.id) {
                // verify that the built filtered deck has the properties we expect
                assertThat(current.id, equalTo(deck.id))
                assertThat(current.name, equalTo(deck.name))
                assertThat(current.filter1State.search, equalTo("deck:A flag:1"))
                assertThat(current.filter1State.limit, equalTo("1"))
                // also allow green flagged card + increase limit
                onSearchChange(FilterIndex.First, "\"deck:a\" flag:1 or flag:3")
                onLimitChange(FilterIndex.First, "2")
                build()
                assertInstanceOf<DeckBuilt>(state.value)
            }
            val updatedDeck = withCol { decks.byName(TEST_DECK_NAME) }
            assertNotNull(updatedDeck)
            assertTrue(updatedDeck.isFiltered)
            assertThat(updatedDeck.name, equalTo(TEST_DECK_NAME))
            val updatedCount = withCol { decks.cardCount(updatedDeck.id) }
            assertThat(updatedCount, equalTo(2))
            assertEquals(updatedDeck.id, n1.firstCard().did)
            assertEquals(updatedDeck.id, n2.firstCard().did)
            // also verify parent deck
            assertThat(withCol { decks.cardCount(parentDeckId) }, equalTo(1))
            assertEquals(parentDeckId, n3.firstCard().did)
        }

    @Test
    fun `using an invalid search produces state with error`() =
        runTest {
            withViewModel {
                // invalid search, missing quote
                onSearchChange(FilterIndex.First, "\"deck:A is:new")
                assertThat(current.filter1State.search, equalTo("\"deck:A is:new"))
                build()
                // check that we didn't finish
                assertInstanceOf<FilteredDeckOptions>(state.value)
                assertNotNull(current.throwable)
                assertInstanceOf<BackendException.BackendSearchException>(current.throwable)
            }
        }

    @Test
    fun `state contains initial data passed in`() =
        runTest {
            withViewModel(search = "is:new", search2 = "is:due") {
                assertInstanceOf<FilteredDeckOptions>(state.value)
                assertThat(current.filter1State.search, equalTo("is:new"))
                assertThat(current.filter2State?.search, equalTo("is:due"))
            }
        }

    @Test
    fun `filter search in browser produces expected state`() =
        runTest {
            withViewModel {
                onSearchChange(FilterIndex.First, "deck:A flag:1")
                onSearchInBrowser(FilterIndex.First)
                val currentState = state.value
                assertInstanceOf<FilteredDeckOptions>(currentState)
                assertThat(currentState.browserQuery, equalTo("deck:A flag:1"))
                clearSearchInBrowser()
                assertNull((state.value as FilteredDeckOptions).browserQuery)
            }
        }

    @Test
    fun `invalid limit inputs produce expected state`() =
        runTest {
            withViewModel {
                onLimitChange(FilterIndex.First, "1")
                assertNull(current.filter1State.error)
                onLimitChange(FilterIndex.First, "")
                assertThat(current.filter1State.error, equalTo(SearchInputError.Empty))
                onLimitChange(FilterIndex.First, "abc")
                assertThat(current.filter1State.error, equalTo(SearchInputError.NotANumber))
                onSecondFilterStatusChange(true)
                onLimitChange(FilterIndex.Second, "1")
                assertNull(current.filter2State?.error)
                onLimitChange(FilterIndex.Second, "")
                assertThat(current.filter2State?.error, equalTo(SearchInputError.Empty))
                onLimitChange(FilterIndex.Second, "abc")
                assertThat(current.filter2State?.error, equalTo(SearchInputError.NotANumber))
            }
        }

    @Test
    fun `invalid limit prevents building or rebuilding`() =
        runTest {
            withViewModel {
                // first filter limit input has error
                assertTrue(current.isBuildingAllowed)
                onLimitChange(FilterIndex.First, "")
                assertThat(current.filter1State.error, equalTo(SearchInputError.Empty))
                assertFalse(current.isBuildingAllowed)
                // enabling the second filter still disables building
                onSecondFilterStatusChange(true)
                assertFalse(current.isBuildingAllowed)
                // error on a second filter limit error keeps the building disabled
                onLimitChange(FilterIndex.Second, "")
                assertThat(current.filter2State?.error, equalTo(SearchInputError.Empty))
                assertFalse(current.isBuildingAllowed)
                // fixing only first filter limit input still has the building disabled
                onLimitChange(FilterIndex.First, "1")
                assertNull(current.filter1State.error)
                assertFalse(current.isBuildingAllowed)
                // hiding the second filter(while having an error) enables building
                onSecondFilterStatusChange(false)
                assertTrue(current.isBuildingAllowed)
                // enabling the second filter and fixing the limit error allows building
                onSecondFilterStatusChange(true)
                assertFalse(current.isBuildingAllowed)
                onLimitChange(FilterIndex.Second, "1")
                assertTrue(current.isBuildingAllowed)
                // hiding the second filter still allows building(first filter is correct)
                onSecondFilterStatusChange(false)
                assertTrue(current.isBuildingAllowed)
            }
        }

    @Test
    fun `a deck without a second filter gets a new fresh filter state when second filter is enabled`() =
        runTest {
            addDeck("A", true)
            addNoteToDeckA { flagCardForNote(this, Flag.RED) }
            addNoteToDeckA { flagCardForNote(this, Flag.GREEN) }
            // setup only the first filter
            val data =
                filteredDeckForUpdate {
                    id = 0
                    name = "filtered"
                    config =
                        filtered {
                            searchTerms.add(
                                searchTerm {
                                    search = "flag:1"
                                    limit = 5
                                    order =
                                        Deck.Filtered.SearchTerm.Order
                                            .forNumber(1)
                                },
                            )
                        }
                }
            val changes = withCol { sched.addOrUpdateFilteredDeck(data) }
            withViewModel(changes.id) {
                assertNull(current.filter2State)
                onSecondFilterStatusChange(true)
                assertNotNull(current.filter2State)
            }
        }

    @Test
    fun `invalid name produces expected state`() {
        runTest {
            addDeck("A")
            withViewModel {
                assertNull(current.nameInputError)

                onDeckNameChange("")
                assertThat(current.nameInputError, equalTo(FilteredNameInputError.Empty))
                assertFalse(current.isBuildingAllowed)

                onDeckNameChange("A")
                assertThat(current.nameInputError, equalTo(FilteredNameInputError.AlreadyExists))
                assertFalse(current.isBuildingAllowed)

                onDeckNameChange("Filtered")
                assertTrue(current.isBuildingAllowed)
            }
        }
    }

    @Test
    fun `changing user input updates changed status`() =
        runTest {
            val testDid = createTestFilteredDeck()
            withViewModel(did = testDid) {
                assertThat(current.name, equalTo("Filtered"))
                assertThat(current.filter1State.search, equalTo("flag:1"))
                assertThat(current.filter1State.limit, equalTo("5"))
                assertTrue(current.isSecondFilterEnabled)
                assertThat(current.filter2State?.search, equalTo("flag:2"))
                assertThat(current.filter2State?.limit, equalTo("5"))
                assertFalse(hasUnsavedChanges.value)
                // check status for name changes
                onDeckNameChange("Not")
                assertTrue(hasUnsavedChanges.value)
                onDeckNameChange("Filtered")
                assertFalse(hasUnsavedChanges.value)
                // check status for filter 1 content changes
                onSearchChange(FilterIndex.First, "flags:7")
                assertTrue(hasUnsavedChanges.value)
                onSearchChange(FilterIndex.First, "flag:1")
                assertFalse(hasUnsavedChanges.value)
                onLimitChange(FilterIndex.First, "100")
                assertTrue(hasUnsavedChanges.value)
                onLimitChange(FilterIndex.First, "5")
                assertFalse(hasUnsavedChanges.value)
                onCardsOptionsChange(FilterIndex.First, 10)
                assertTrue(hasUnsavedChanges.value)
                onCardsOptionsChange(FilterIndex.First, 1)
                assertFalse(hasUnsavedChanges.value)
                // check status if the second filter availability is changed
                onSecondFilterStatusChange(false)
                assertTrue(hasUnsavedChanges.value)
                onSecondFilterStatusChange(true)
                assertFalse(hasUnsavedChanges.value)
                // check status for filter 2 content changes
                onSearchChange(FilterIndex.Second, "flags:7")
                assertTrue(hasUnsavedChanges.value)
                onSearchChange(FilterIndex.Second, "flag:2")
                assertFalse(hasUnsavedChanges.value)
                onLimitChange(FilterIndex.Second, "100")
                assertTrue(hasUnsavedChanges.value)
                onLimitChange(FilterIndex.Second, "5")
                assertFalse(hasUnsavedChanges.value)
                onCardsOptionsChange(FilterIndex.Second, 10)
                assertTrue(hasUnsavedChanges.value)
                onCardsOptionsChange(FilterIndex.Second, 1)
                assertFalse(hasUnsavedChanges.value)
            }
        }

    @Test
    fun `changed status is not updated for properties that are not observed`() =
        runTest {
            val testDid = createTestFilteredDeck()
            withViewModel(did = testDid) {
                assertFalse(current.allowEmpty)
                assertTrue(current.shouldReschedule)
                assertFalse(hasUnsavedChanges.value)
                onAllowEmptyChange(true)
                onRescheduleChange(false)
                assertFalse(hasUnsavedChanges.value)
            }
        }

    /** Returns the current state as a [FilteredDeckOptions] or throw otherwise */
    private val FilteredDeckOptionsViewModel.current: FilteredDeckOptions
        get() = state.value as FilteredDeckOptions

    private fun TestScope.withViewModel(
        did: DeckId = 0,
        search: String? = null,
        search2: String? = null,
        action: FilteredDeckOptionsViewModel.() -> Unit,
    ) {
        val handle =
            SavedStateHandle().apply {
                set(FilteredDeckOptionsFragment.ARG_DECK_ID, did)
                set(FilteredDeckOptionsFragment.ARG_SEARCH, search)
                set(FilteredDeckOptionsFragment.ARG_SEARCH_2, search2)
            }
        val viewModel = FilteredDeckOptionsViewModel(handle)
        advanceUntilIdle()
        advanceRobolectricLooper()
        viewModel.action()
    }

    private fun addNoteToDeckA(setup: Note.() -> Unit): Note =
        addBasicNote().update {
            moveToDeck("A", false)
            setup()
        }

    /** Note: created filtered deck has the second filter enabled by default */
    private suspend fun createTestFilteredDeck(): DeckId {
        addDeck("A", true)
        addNoteToDeckA { flagCardForNote(this, Flag.RED) }
        addNoteToDeckA { flagCardForNote(this, Flag.GREEN) }
        val data =
            filteredDeckForUpdate {
                id = 0
                name = "Filtered"
                config =
                    filtered {
                        reschedule = true
                        searchTerms.add(
                            searchTerm {
                                search = "flag:1"
                                limit = 5
                                order =
                                    Deck.Filtered.SearchTerm.Order
                                        .forNumber(1)
                            },
                        )
                        searchTerms.add(
                            searchTerm {
                                search = "flag:2"
                                limit = 5
                                order =
                                    Deck.Filtered.SearchTerm.Order
                                        .forNumber(1)
                            },
                        )
                    }
            }
        return withCol { sched.addOrUpdateFilteredDeck(data) }.id
    }

    companion object {
        private const val TEST_DECK_NAME = "TestFiltered"
    }
}

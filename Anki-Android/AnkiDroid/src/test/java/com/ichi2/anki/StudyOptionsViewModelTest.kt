// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.scheduler.CardAnswer.Rating
import app.cash.turbine.test
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.testutils.ensureOpsExecuted
import kotlinx.coroutines.joinAll
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.instanceOf
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class StudyOptionsViewModelTest : RobolectricTest() {
    private val viewModel = StudyOptionsViewModel()

    @Test
    fun `initial state is Loading`() {
        assertThat(viewModel.state, instanceOf(StudyOptionsState.Loading::class.java))
    }

    @Test
    fun `refreshData - empty deck shows Empty state`() =
        runTest {
            col
            viewModel.refreshData().join()
            assertIs<StudyOptionsState.Empty>(viewModel.state)
        }

    @Test
    fun `refreshData - deck with due cards shows StudyOptions state`() =
        runTest {
            addBasicNote("Front", "Back")

            viewModel.refreshData().join()

            val state = viewModel.state
            assertIs<StudyOptionsState.StudyOptions>(state)
            assertEquals(1, state.data.newCardsToday)
            assertEquals(1, state.data.numberOfCardsInDeck)
            assertEquals(1, state.data.totalNewCards)
            assertEquals(0, state.data.lrnCardsToday)
            assertEquals(0, state.data.revCardsToday)
        }

    @Test
    fun `refreshData - regular deck is not filtered`() =
        runTest {
            addBasicNote()

            viewModel.refreshData().join()

            assertFalse(viewModel.isFilteredDeck)
        }

    @Test
    fun `refreshData - congrats state when no cards due`() =
        runTest {
            addBasicNote()
            withCol {
                while (sched.card != null) {
                    val card = sched.card!!
                    sched.answerCard(card, Rating.EASY)
                }
            }

            viewModel.refreshData().join()

            assertIs<StudyOptionsState.Congrats>(viewModel.state)
        }

    @Test
    fun `refreshData - state flow emits updates`() =
        runTest {
            col
            viewModel.flowOfState.test {
                assertIs<StudyOptionsState.Loading>(awaitItem())

                viewModel.refreshData().join()
                assertIs<StudyOptionsState.Empty>(awaitItem())

                addBasicNote()
                viewModel.refreshData().join()
                assertIs<StudyOptionsState.StudyOptions>(awaitItem())
            }
        }

    @Test
    fun `refreshData - multiple cards counted correctly`() =
        runTest {
            repeat(5) { addBasicNote("Front $it", "Back $it") }

            viewModel.refreshData().join()

            val state = assertIs<StudyOptionsState.StudyOptions>(viewModel.state)
            assertEquals(5, state.data.newCardsToday)
            assertEquals(5, state.data.numberOfCardsInDeck)
        }

    @Test
    fun `refreshData - deck name is correct`() =
        runTest {
            addBasicNote()

            viewModel.refreshData().join()

            val state = assertIs<StudyOptionsState.StudyOptions>(viewModel.state)
            assertEquals("Default", state.deckName)
        }

    @Test
    fun `rebuildCram - updates state`() =
        runTest {
            addBasicNote()
            addDynamicDeck("Filtered", "")

            viewModel.rebuildCram()

            val state = viewModel.state
            assertThat(state, instanceOf(StudyOptionsState::class.java))
            assertTrue(viewModel.isFilteredDeck)
        }

    @Test
    fun `emptyCram - is undoable`() =
        runTest {
            addBasicNote()
            addDynamicDeck("Filtered", "")

            ensureOpsExecuted(1) {
                viewModel.emptyCram()
            }
        }

    @Test
    fun `rebuildCram - is undoable`() =
        runTest {
            addBasicNote()
            addDynamicDeck("Filtered", "")

            ensureOpsExecuted(1) {
                viewModel.rebuildCram()
            }
        }

    @Test
    fun `unbury - is undoable`() =
        runTest {
            addBasicNote()
            withCol {
                val card = sched.card!!
                sched.buryCards(listOf(card.id), true)
            }

            ensureOpsExecuted(1) {
                viewModel.unbury().join()
            }
        }

    @Test
    fun `haveBuried - false when no buried cards`() =
        runTest {
            addBasicNote()

            viewModel.refreshData().join()

            assertFalse(viewModel.haveBuried)
        }

    @Test
    fun `refreshData - buried cards are counted`() =
        runTest {
            addBasicNote("Front1", "Back1")
            addBasicNote("Front2", "Back2")
            withCol {
                val card = sched.card!!
                sched.buryCards(listOf(card.id), true)
            }

            viewModel.refreshData().join()

            val state = assertIs<StudyOptionsState.StudyOptions>(viewModel.state)
            assertTrue(state.data.buriedNew > 0, "expected buried new cards")
            assertEquals(1, state.data.newCardsToday)
        }

    @Test
    fun `refreshData - returns early without throwing when collection is closed`() =
        runTest {
            withNullCollection {
                viewModel.refreshData().join()
            }
        }

    @Test
    fun `refreshData - state stays Loading when collection is closed from the start`() =
        runTest {
            withNullCollection {
                assertIs<StudyOptionsState.Loading>(viewModel.state)

                viewModel.refreshData().join()

                assertIs<StudyOptionsState.Loading>(viewModel.state)
            }
        }

    @Test
    fun `refreshData - does not clobber a populated state when collection becomes closed`() =
        runTest {
            addBasicNote("Front", "Back")
            viewModel.refreshData().join()
            val populatedState = assertIs<StudyOptionsState.StudyOptions>(viewModel.state)
            val populatedDeckId = viewModel.selectedDeckId
            val populatedIsFiltered = viewModel.isFilteredDeck
            val populatedHaveBuried = viewModel.haveBuried

            withNullCollection {
                viewModel.refreshData().join()
                assertEquals(populatedState, viewModel.state)
                assertEquals(populatedDeckId, viewModel.selectedDeckId)
                assertEquals(populatedIsFiltered, viewModel.isFilteredDeck)
                assertEquals(populatedHaveBuried, viewModel.haveBuried)
            }
        }

    @Test
    fun `refreshData - flowOfState emits no extra value when collection is closed`() =
        runTest {
            withNullCollection {
                viewModel.flowOfState.test {
                    assertIs<StudyOptionsState.Loading>(awaitItem())
                    viewModel.refreshData().join()
                    expectNoEvents()
                }
            }
        }

    @Test
    fun `refreshData - safe under multiple rapid calls when collection is closed`() =
        runTest {
            withNullCollection {
                val jobs = (1..10).map { viewModel.refreshData() }
                jobs.joinAll()

                assertIs<StudyOptionsState.Loading>(viewModel.state)
            }
        }

    @Test
    fun `refreshData - resumes correctly after the collection becomes available again`() =
        runTest {
            withNullCollection {
                viewModel.refreshData().join()
                assertIs<StudyOptionsState.Loading>(viewModel.state)
            }

            addBasicNote("Front", "Back")
            viewModel.refreshData().join()

            assertIs<StudyOptionsState.StudyOptions>(viewModel.state)
        }

    @Test
    fun `refreshData - mutating operations stay safe when collection is closed`() =
        runTest {
            withNullCollection {
                viewModel.refreshData().join()
                viewModel.refreshData().join()
                viewModel.refreshData().join()
                assertIs<StudyOptionsState.Loading>(viewModel.state)
            }
        }
}

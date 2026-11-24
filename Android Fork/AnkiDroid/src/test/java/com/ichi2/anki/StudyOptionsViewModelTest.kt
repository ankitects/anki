// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki

import androidx.lifecycle.ViewModelStore
import anki.scheduler.CardAnswer.Rating
import app.cash.turbine.test
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.libanki.QueueType
import com.ichi2.anki.observability.ensureOpsExecuted
import com.ichi2.anki.progress.ViewModelProgress
import com.ichi2.testutils.JvmTest
import com.ichi2.testutils.withNullCollection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.instanceOf
import org.junit.After
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class StudyOptionsViewModelTest : JvmTest() {
    private val viewModels = ViewModelStore()

    @After
    override fun tearDown() {
        try {
            viewModels.clear()
        } finally {
            super.tearDown()
        }
    }

    private val viewModel = StudyOptionsViewModel().also { viewModels.put("study", it) }

    @Test
    fun `refreshData suspends without blocking the caller while sync holds the collection queue`() =
        runTest {
            col
            withBlockedCollectionQueue { releaseQueue ->
                val refresh = launch(start = CoroutineStart.UNDISPATCHED) { viewModel.refreshData() }
                assertFalse(refresh.isCompleted)
                assertIs<StudyOptionsState.Loading>(viewModel.state)

                releaseQueue()
                refresh.join()
                assertIs<StudyOptionsState.Empty>(viewModel.state)
            }
        }

    @Test
    fun `initial state is Loading`() {
        assertThat(viewModel.state, instanceOf(StudyOptionsState.Loading::class.java))
    }

    @Test
    fun `refreshData - empty deck shows Empty state`() =
        runTest {
            col
            viewModel.refreshData()
            assertIs<StudyOptionsState.Empty>(viewModel.state)
        }

    @Test
    fun `refreshData - deck with due cards shows StudyOptions state`() =
        runTest {
            addBasicNote("Front", "Back")

            viewModel.refreshData()

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

            viewModel.refreshData()

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

            viewModel.refreshData()

            assertIs<StudyOptionsState.Congrats>(viewModel.state)
        }

    @Test
    fun `refreshData - state flow emits updates`() =
        runTest {
            col
            viewModel.flowOfState.test {
                assertIs<StudyOptionsState.Loading>(awaitItem())

                viewModel.refreshData()
                assertIs<StudyOptionsState.Empty>(awaitItem())

                addBasicNote()
                viewModel.refreshData()
                assertIs<StudyOptionsState.StudyOptions>(awaitItem())
            }
        }

    @Test
    fun `refreshData - multiple cards counted correctly`() =
        runTest {
            repeat(5) { addBasicNote("Front $it", "Back $it") }

            viewModel.refreshData()

            val state = assertIs<StudyOptionsState.StudyOptions>(viewModel.state)
            assertEquals(5, state.data.newCardsToday)
            assertEquals(5, state.data.numberOfCardsInDeck)
        }

    @Test
    fun `refreshData - deck name is correct`() =
        runTest {
            addBasicNote()

            viewModel.refreshData()

            val state = assertIs<StudyOptionsState.StudyOptions>(viewModel.state)
            assertEquals("Default", state.deckName)
        }

    @Test
    fun `rebuildCram - updates state`() =
        runTest {
            addBasicNote()
            addDynamicDeck("Filtered", "")
            viewModel.refreshData()

            viewModel.rebuildCram(viewModel.selectedDeckId)

            val state = viewModel.state
            assertThat(state, instanceOf(StudyOptionsState::class.java))
            assertTrue(viewModel.isFilteredDeck)
        }

    @Test
    fun `emptyCram - is undoable`() =
        runTest {
            addBasicNote()
            addDynamicDeck("Filtered", "")
            viewModel.refreshData()

            ensureOpsExecuted(1) {
                viewModel.emptyCram(viewModel.selectedDeckId)
            }
        }

    @Test
    fun `rebuildCram - is undoable`() =
        runTest {
            addBasicNote()
            addDynamicDeck("Filtered", "")
            viewModel.refreshData()

            ensureOpsExecuted(1) {
                viewModel.rebuildCram(viewModel.selectedDeckId)
            }
        }

    @Test
    fun `rebuildCram - reports progress`() =
        runTest {
            addBasicNote()
            addDynamicDeck("Filtered", "")
            viewModel.refreshData()

            assertProgressAround(StudyOptionsProgress.REBUILDING_FILTERED_DECK) { viewModel.rebuildCram(viewModel.selectedDeckId) }
        }

    @Test
    fun `emptyCram - reports progress`() =
        runTest {
            addBasicNote()
            addDynamicDeck("Filtered", "")
            viewModel.refreshData()

            assertProgressAround(StudyOptionsProgress.EMPTYING_FILTERED_DECK) { viewModel.emptyCram(viewModel.selectedDeckId) }
        }

    @Test
    fun `unbury - is undoable`() =
        runTest {
            addBasicNote()
            withCol {
                val card = sched.card!!
                sched.buryCards(listOf(card.id), true)
            }
            viewModel.refreshData()

            ensureOpsExecuted(1) {
                viewModel.unbury(viewModel.selectedDeckId).join()
            }
        }

    @Test
    fun `emptyCram - empties the shown deck when another deck is selected`() =
        runTest {
            addNoteToDeck(addDeck("ShownHome"))
            addNoteToDeck(addDeck("OtherHome"))
            val shown = addDynamicDeck("Shown", search = "deck:ShownHome")
            viewModel.refreshData()
            val other = addDynamicDeck("Other", search = "deck:OtherHome")

            viewModel.emptyCram(viewModel.selectedDeckId)

            assertEquals(0, col.decks.cardCount(shown))
            assertEquals(1, col.decks.cardCount(other))
        }

    @Test
    fun `rebuildCram - rebuilds the shown deck when another deck is selected`() =
        runTest {
            addNoteToDeck(addDeck("ShownHome"))
            addNoteToDeck(addDeck("OtherHome"))
            val shown = addDynamicDeck("Shown", search = "deck:ShownHome")
            viewModel.refreshData()
            val other = addDynamicDeck("Other", search = "deck:OtherHome")
            col.sched.emptyFilteredDeck(shown)

            viewModel.rebuildCram(viewModel.selectedDeckId)

            assertEquals(1, col.decks.cardCount(shown))
            assertEquals(1, col.decks.cardCount(other))
        }

    @Test
    fun `unbury - unburies the shown deck when another deck is selected`() =
        runTest {
            val shown = addDeck("Shown", setAsSelected = true)
            val other = addDeck("Other")
            viewModel.refreshData()
            addNoteToDeck(shown, queueType = QueueType.ManuallyBuried)
            addNoteToDeck(other, queueType = QueueType.ManuallyBuried)
            col.decks.select(other)

            viewModel.unbury(viewModel.selectedDeckId).join()

            assertEquals(QueueType.New, col.getCard(col.findCards("deck:Shown").single()).queue)
            assertEquals(QueueType.ManuallyBuried, col.getCard(col.findCards("deck:Other").single()).queue)
        }

    @Test
    fun `haveBuried - false when no buried cards`() =
        runTest {
            addBasicNote()

            viewModel.refreshData()

            assertFalse(viewModel.haveBuried)
        }

    @Test
    fun `refreshData - buried siblings are counted`() =
        runTest {
            addBasicAndReversedNote("Front", "Back")
            withCol {
                val config = decks.configDictForDeckId(decks.current().id)
                config.new.bury = true
                decks.save(config)
            }

            viewModel.refreshData()

            val state = assertIs<StudyOptionsState.StudyOptions>(viewModel.state)
            assertEquals(1, state.data.buriedNew)
            assertEquals(1, state.data.newCardsToday)
        }

    @Test
    fun `refreshData - returns early without throwing when collection is closed`() =
        runTest {
            withNullCollection {
                viewModel.refreshData()
            }
        }

    @Test
    fun `refreshData - state stays Loading when collection is closed from the start`() =
        runTest {
            withNullCollection {
                assertIs<StudyOptionsState.Loading>(viewModel.state)

                viewModel.refreshData()

                assertIs<StudyOptionsState.Loading>(viewModel.state)
            }
        }

    @Test
    fun `refreshData - does not clobber a populated state when collection becomes closed`() =
        runTest {
            addBasicNote("Front", "Back")
            viewModel.refreshData()
            val populatedState = assertIs<StudyOptionsState.StudyOptions>(viewModel.state)
            val populatedDeckId = viewModel.selectedDeckId
            val populatedIsFiltered = viewModel.isFilteredDeck
            val populatedHaveBuried = viewModel.haveBuried

            withNullCollection {
                viewModel.refreshData()
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
                    viewModel.refreshData()
                    expectNoEvents()
                }
            }
        }

    @Test
    fun `refreshData - safe under multiple rapid calls when collection is closed`() =
        runTest {
            withNullCollection {
                val jobs = (1..10).map { launch { viewModel.refreshData() } }
                jobs.joinAll()

                assertIs<StudyOptionsState.Loading>(viewModel.state)
            }
        }

    @Test
    fun `refreshData - resumes correctly after the collection becomes available again`() =
        runTest {
            withNullCollection {
                viewModel.refreshData()
                assertIs<StudyOptionsState.Loading>(viewModel.state)
            }

            addBasicNote("Front", "Back")
            viewModel.refreshData()

            assertIs<StudyOptionsState.StudyOptions>(viewModel.state)
        }

    @Test
    fun `refreshData - mutating operations stay safe when collection is closed`() =
        runTest {
            withNullCollection {
                viewModel.refreshData()
                viewModel.refreshData()
                viewModel.refreshData()
                assertIs<StudyOptionsState.Loading>(viewModel.state)
            }
        }

    private suspend fun assertProgressAround(
        message: StudyOptionsProgress,
        op: suspend () -> Unit,
    ) {
        viewModel.progressManager.progress.test {
            assertIs<ViewModelProgress.Idle>(awaitItem())
            op()
            val active = assertIs<ViewModelProgress.Active<StudyOptionsProgress>>(awaitItem())
            assertEquals(message, active.message)
            assertIs<ViewModelProgress.Idle>(awaitItem())
            expectNoEvents()
        }
    }

    /** Hold the production queue as a sync would; restore the test dispatcher after all work finishes. */
    private suspend fun withBlockedCollectionQueue(block: suspend CoroutineScope.(releaseQueue: () -> Unit) -> Unit) {
        val previousQueue = CollectionManager.setTestDispatcher(Dispatchers.IO.limitedParallelism(1), useReentrantLock = false)
        val queueHeld = CountDownLatch(1)
        val releaseQueue = CountDownLatch(1)
        try {
            coroutineScope {
                launch(Dispatchers.IO) {
                    withCol {
                        queueHeld.countDown()
                        assertTrue(releaseQueue.await(5, TimeUnit.SECONDS), "caller blocked behind the collection queue")
                    }
                }
                try {
                    assertTrue(queueHeld.await(5, TimeUnit.SECONDS), "collection queue should be held")
                    block(releaseQueue::countDown)
                } finally {
                    releaseQueue.countDown()
                }
            }
        } finally {
            CollectionManager.setTestDispatcher(previousQueue)
        }
    }
}

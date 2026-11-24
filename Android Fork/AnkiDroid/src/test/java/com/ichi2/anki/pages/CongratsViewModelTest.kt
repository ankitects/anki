// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki.pages

import app.cash.turbine.test
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.common.destinations.StudyOptionsDestination
import com.ichi2.anki.observability.undoableOp
import com.ichi2.testutils.JvmTest
import org.junit.Test
import kotlin.test.assertEquals

// https://github.com/ankidroid/Anki-Android/issues/21393
class CongratsViewModelTest : JvmTest() {
    private val viewModel by lazy { CongratsViewModel() }

    @Test
    fun `collection changes request study options after a rebuild refills the deck`() =
        runTest {
            addBasicNote("Front", "Back")
            val deckId = addDynamicDeck("Filtered", "")
            withCol { sched.emptyFilteredDeck(deckId) }

            viewModel.studyOptionsDestination.test {
                undoableOp { sched.rebuildFilteredDeck(deckId) }
                assertEquals(StudyOptionsDestination, awaitItem())
            }
        }

    @Test
    fun `onStudyQueuesChanged - reloads the page when rebuild finds no cards`() =
        runTest {
            addBasicNote("Front", "Back")
            val deckId = addDynamicDeck("Filtered", "deck:NonExistentDeck")

            withCol { sched.rebuildFilteredDeck(deckId) }

            viewModel.congratsRefreshState.test {
                viewModel.onStudyQueuesChanged()
                assertEquals(CongratsRefreshState.ReloadPage, awaitItem())
            }
        }

    @Test
    fun `collection changes from this ViewModel are handled by their caller`() =
        runTest {
            addBasicNote()
            val deckId = addDynamicDeck("Filtered", "")
            withCol { sched.emptyFilteredDeck(deckId) }

            viewModel.studyOptionsDestination.test {
                undoableOp(handler = viewModel) { sched.rebuildFilteredDeck(deckId) }
                expectNoEvents()
            }
        }

    @Test
    fun `navigation waits for a collector and is cleared after opening`() =
        runTest {
            viewModel.onCustomStudyCompleted().join()

            viewModel.studyOptionsDestination.test {
                assertEquals(StudyOptionsDestination, awaitItem())
                viewModel.onStudyOptionsOpened()
            }
            viewModel.studyOptionsDestination.test { expectNoEvents() }
        }

    @Test
    fun `refresh does not repeat pending or completed navigation`() =
        runTest {
            addBasicNote()

            viewModel.studyOptionsDestination.test {
                viewModel.onCustomStudyCompleted().join()
                assertEquals(StudyOptionsDestination, awaitItem())

                viewModel.refreshIfNeeded()
                expectNoEvents()

                viewModel.onStudyOptionsOpened()
                viewModel.refreshIfNeeded()
                viewModel.onCustomStudyCompleted().join()
                expectNoEvents()
            }
        }

    @Test
    fun `unbury requests study options once`() =
        runTest {
            val card = addBasicNote().firstCard()
            withCol { sched.buryCards(listOf(card.id), manual = true) }

            viewModel.studyOptionsDestination.test {
                viewModel.onUnbury()
                assertEquals(StudyOptionsDestination, awaitItem())
                expectNoEvents()
            }
        }
}

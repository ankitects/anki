/*
 * Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.com>
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
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.ui.windows.reviewer.autoadvance

import com.ichi2.anki.libanki.Card
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class AutoAdvanceTest {
    private val testScope = TestScope(StandardTestDispatcher())

    @MockK
    lateinit var listener: AutoAdvance.ActionListener

    @MockK
    lateinit var card: Card

    private lateinit var autoAdvance: AutoAdvance

    private val defaultSettings get() =
        AutoAdvanceSettings(
            questionAction = QuestionAction.SHOW_ANSWER,
            answerAction = AnswerAction.ANSWER_GOOD,
            durationToShowQuestionFor = 5.seconds,
            durationToShowAnswerFor = 5.seconds,
            waitForAudio = false,
        )

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        every { card.currentDeckId() } returns 1L
        coEvery { listener.onAutoAdvanceAction(any()) } just Runs
        mockkObject(AutoAdvanceSettings)
        coEvery { AutoAdvanceSettings.createInstance(any()) } returns defaultSettings
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun createAutoAdvance(): AutoAdvance = AutoAdvance(testScope, listener, CompletableDeferred(card))

    @Test
    fun `onShowQuestion triggers action after delay when enabled`() =
        testScope.runTest {
            autoAdvance = createAutoAdvance()
            autoAdvance.isEnabled = true
            testScheduler.advanceUntilIdle()

            autoAdvance.onShowQuestion()

            // Advance time less than duration (4s) - should NOT fire yet
            advanceTimeBy(4.seconds)
            coVerify(exactly = 0) { listener.onAutoAdvanceAction(any()) }

            // Advance past duration (5s total) - SHOULD fire
            advanceTimeBy(2.seconds)
            coVerify(exactly = 1) { listener.onAutoAdvanceAction(QuestionAction.SHOW_ANSWER) }
        }

    @Test
    fun `onShowAnswer triggers action after delay when enabled`() =
        testScope.runTest {
            autoAdvance = createAutoAdvance()
            autoAdvance.isEnabled = true
            testScheduler.advanceUntilIdle()

            autoAdvance.onShowAnswer()

            // Advance time less than duration (4s) - should NOT fire yet
            advanceTimeBy(4.seconds)
            coVerify(exactly = 0) { listener.onAutoAdvanceAction(any()) }

            // Advance past duration (5s total) - SHOULD fire
            advanceTimeBy(2.seconds)
            coVerify(exactly = 1) { listener.onAutoAdvanceAction(AnswerAction.ANSWER_GOOD) }
        }

    @Test
    fun `actions do not trigger if AutoAdvance is disabled`() =
        testScope.runTest {
            autoAdvance = createAutoAdvance()
            autoAdvance.isEnabled = false
            testScheduler.advanceUntilIdle()

            autoAdvance.onShowQuestion()
            advanceTimeBy(6.seconds) // Past the 5s delay

            coVerify(exactly = 0) { listener.onAutoAdvanceAction(any()) }
        }

    @Test
    fun `setting isEnabled to false cancels pending jobs`() =
        testScope.runTest {
            autoAdvance = createAutoAdvance()
            autoAdvance.isEnabled = true
            testScheduler.advanceUntilIdle()

            autoAdvance.onShowQuestion()

            // Advance partially
            advanceTimeBy(2.seconds)

            // Disable mid-wait
            autoAdvance.isEnabled = false

            // Advance past the original trigger time
            advanceTimeBy(4.seconds)

            // Should not have fired
            coVerify(exactly = 0) { listener.onAutoAdvanceAction(any()) }
        }

    @Test
    fun `zero duration does not trigger action`() =
        testScope.runTest {
            coEvery { AutoAdvanceSettings.createInstance(any()) } returns
                defaultSettings.copy(durationToShowQuestionFor = 0.seconds)

            autoAdvance = createAutoAdvance()
            autoAdvance.isEnabled = true
            testScheduler.advanceUntilIdle()

            autoAdvance.onShowQuestion()
            advanceTimeBy(10.seconds)

            coVerify(exactly = 0) { listener.onAutoAdvanceAction(any()) }
        }

    @Test
    fun `onCardChange reloads settings`() =
        testScope.runTest {
            val initialSettings = defaultSettings
            val newSettings =
                initialSettings.copy(
                    questionAction = QuestionAction.SHOW_REMINDER,
                    durationToShowQuestionFor = 2.seconds,
                )

            coEvery { AutoAdvanceSettings.createInstance(1L) } returns initialSettings

            autoAdvance = createAutoAdvance()
            autoAdvance.isEnabled = true
            testScheduler.advanceUntilIdle()

            val newCard = mockk<Card>(relaxed = true)
            coEvery { AutoAdvanceSettings.createInstance(2L) } returns newSettings
            every { newCard.currentDeckId() } returns 2L
            autoAdvance.onCardChange(newCard)
            testScheduler.advanceUntilIdle()

            autoAdvance.onShowQuestion()
            advanceTimeBy(3.seconds)

            coVerify { listener.onAutoAdvanceAction(QuestionAction.SHOW_REMINDER) }
        }

    @Test
    fun `switching from Question to Answer cancels Question job`() =
        testScope.runTest {
            autoAdvance = createAutoAdvance()
            autoAdvance.isEnabled = true
            testScheduler.advanceUntilIdle()

            // Start question timer (5s)
            autoAdvance.onShowQuestion()
            advanceTimeBy(2.seconds)

            autoAdvance.onShowAnswer()

            // Advance time enough that Question timer WOULD have fired (total 6s)
            advanceTimeBy(4.seconds)

            // Verify QuestionAction was NEVER fired
            coVerify(exactly = 0) { listener.onAutoAdvanceAction(QuestionAction.SHOW_ANSWER) }

            // AnswerAction should be pending (needs 1 more second to reach 5s)
            coVerify(exactly = 0) { listener.onAutoAdvanceAction(any()) }
            advanceTimeBy(2.seconds)
            coVerify(exactly = 1) { listener.onAutoAdvanceAction(AnswerAction.ANSWER_GOOD) }
        }
}

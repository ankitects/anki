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
package com.ichi2.anki.ui.windows.reviewer

import android.os.SystemClock
import androidx.lifecycle.LifecycleOwner
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnswerTimerTest {
    private lateinit var answerTimer: AnswerTimer
    private lateinit var mockOwner: LifecycleOwner
    private var currentTime = 0L

    @Before
    fun setUp() {
        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } answers { currentTime }

        mockOwner = mockk(relaxed = true)
        answerTimer = AnswerTimer()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `initial state is Hidden`() {
        assertEquals(AnswerTimerState.Hidden, answerTimer.state.value)
    }

    @Test
    fun `configureForCard with shouldShow=false results in Hidden state`() {
        answerTimer.configureForCard(shouldShow = false, limitMs = 1000)
        assertEquals(AnswerTimerState.Hidden, answerTimer.state.value)
    }

    @Test
    fun `configureForCard with shouldShow=true starts Running state`() {
        currentTime = 1000L
        val limit = 5000

        answerTimer.configureForCard(shouldShow = true, limitMs = limit)

        val state = answerTimer.state.value
        assertTrue(state is AnswerTimerState.Running)
        with(state) {
            assertEquals(1000L, baseTime)
            assertEquals(limit, limitMs)
        }
    }

    @Test
    fun `stop transitions from Running to Stopped with correct elapsed time`() {
        currentTime = 1000L
        answerTimer.configureForCard(shouldShow = true, limitMs = 10000)

        // Advance time by 2.5 seconds
        currentTime = 3500L
        answerTimer.stop()

        val state = answerTimer.state.value
        assertTrue(state is AnswerTimerState.Stopped)
        with(state) {
            assertEquals(2500L, elapsedTimeMs)
            assertEquals(10000, limitMs)
        }
    }

    @Test
    fun `onPause transitions from Running to Paused with correct elapsed time`() {
        currentTime = 1000L
        answerTimer.configureForCard(shouldShow = true, limitMs = 10000)

        // Advance time by 3 seconds
        currentTime = 4000L
        answerTimer.onPause(mockOwner)

        val state = answerTimer.state.value
        assertTrue(state is AnswerTimerState.Paused)
        with(state) {
            assertEquals(3000L, elapsedTimeMs)
            assertEquals(10000, limitMs)
        }
    }

    @Test
    fun `onResume transitions from Paused to Running adjusting baseTime`() {
        // Start at T=1000
        currentTime = 1000L
        answerTimer.configureForCard(shouldShow = true, limitMs = 10000)

        // Pause at T=3000 (Elapsed = 2000)
        currentTime = 3000L
        answerTimer.onPause(mockOwner)

        // Time passes while paused (T=5000), then resume
        currentTime = 5000L
        answerTimer.onResume(mockOwner)

        val state = answerTimer.state.value
        assertTrue(state is AnswerTimerState.Running)

        // The new baseTime should be calculated such that: CurrentTime - BaseTime = PreviouslyElapsed
        // 5000 - BaseTime = 2000  =>  BaseTime = 3000
        assertEquals(3000L, (state).baseTime)
    }

    @Test
    fun `onPause clamps elapsed time to limit if exceeded`() {
        currentTime = 1000L
        val limit = 5000
        answerTimer.configureForCard(shouldShow = true, limitMs = limit)

        // Advance time by 6 seconds (1000 over limit)
        currentTime = 7000L
        answerTimer.onPause(mockOwner)

        val state = answerTimer.state.value
        assertTrue(state is AnswerTimerState.Paused)

        // Elapsed time should be clamped to the limit (5000), not the raw diff (6000)
        assertEquals(5000L, (state).elapsedTimeMs)
    }

    @Test
    fun `onResume stops timer if limit was reached while paused`() {
        currentTime = 1000L
        val limit = 5000
        answerTimer.configureForCard(shouldShow = true, limitMs = limit)

        // Advance beyond limit, then try to resume
        currentTime = 7000L
        answerTimer.onPause(mockOwner) // Clamps to 5000 (limit)
        answerTimer.onResume(mockOwner)

        val state = answerTimer.state.value
        assertTrue(state is AnswerTimerState.Stopped, "Should be stopped if limit is reached")
        assertEquals(5000L, (state).elapsedTimeMs)
    }

    @Test
    fun `hide forces state to Hidden`() {
        answerTimer.configureForCard(shouldShow = true, limitMs = 1000)
        assertTrue(answerTimer.state.value is AnswerTimerState.Running)

        answerTimer.hide()
        assertEquals(AnswerTimerState.Hidden, answerTimer.state.value)
    }

    @Test
    fun `stop works when Paused`() {
        currentTime = 0L
        answerTimer.configureForCard(true, 10000)

        // Pause at 2000, then stop
        currentTime = 2000L
        answerTimer.onPause(mockOwner)
        answerTimer.stop()

        val state = answerTimer.state.value
        assertTrue(state is AnswerTimerState.Stopped)
        assertEquals(2000L, (state).elapsedTimeMs)
    }

    @Test
    fun `stop clamps elapsed time to limit if exceeded`() {
        currentTime = 1000L
        val limit = 5000
        answerTimer.configureForCard(shouldShow = true, limitMs = limit)

        currentTime = 7000L
        answerTimer.stop()

        val state = answerTimer.state.value
        assertTrue(state is AnswerTimerState.Stopped)

        assertEquals(5000L, (state).elapsedTimeMs)
    }
}

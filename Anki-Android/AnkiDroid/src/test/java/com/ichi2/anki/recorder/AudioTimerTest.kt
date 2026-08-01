/*
 *  Copyright (c) 2025 Ashish Yadav <mailtoashish693@gmail.com>
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

package com.ichi2.anki.recorder

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.hamcrest.CoreMatchers.both
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.hamcrest.Matchers.lessThanOrEqualTo
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

class AudioTimerTest {
    private val fakeClock =
        object : TimeSource {
            var currentNanos = 0L

            override fun markNow(): TimeMark =
                object : TimeMark {
                    val startNanos = currentNanos

                    override fun elapsedNow(): Duration = (currentNanos - startNanos).nanoseconds

                    override fun plus(duration: Duration): TimeMark = this

                    override fun minus(duration: Duration): TimeMark = this

                    override fun hasPassedNow(): Boolean = elapsedNow() > Duration.ZERO

                    override fun hasNotPassedNow(): Boolean = !hasPassedNow()
                }

            fun advance(duration: Duration) {
                currentNanos += duration.inWholeNanoseconds
            }
        }

    private fun TestScope.advanceTime(duration: Duration) {
        fakeClock.advance(duration)
        advanceTimeBy(duration.inWholeMilliseconds)
    }

    private fun TestScope.createTimer(
        scope: CoroutineScope = backgroundScope,
        onTimerTick: (Duration) -> Unit = {},
        onAudioTick: () -> Unit = {},
        onNotificationTick: ((Duration) -> Unit)? = null,
    ): AudioTimer =
        AudioTimer(
            scope = scope,
            timeSource = fakeClock,
            onTimerTick = onTimerTick,
            onAudioTick = onAudioTick,
            onNotificationTick = onNotificationTick,
        )

    @Test
    fun `start triggers high frequency UI updates`() =
        runTest {
            val timerTicks = mutableListOf<Duration>()
            val waveTicks = mutableListOf<Unit>()

            val timer =
                createTimer(
                    onTimerTick = { timerTicks.add(it) },
                    onAudioTick = { waveTicks.add(Unit) },
                )

            timer.start()
            advanceTime(100.milliseconds)

            assertThat(
                "UI ticks should fire approx every 16ms",
                timerTicks.size,
                both(greaterThanOrEqualTo(5)).and(lessThanOrEqualTo(7)),
            )

            assertThat(
                "Wave ticks should fire approx every 50ms",
                waveTicks.size,
                both(greaterThanOrEqualTo(1)).and(lessThanOrEqualTo(3)),
            )

            assertEquals("Last tick should match elapsed time", 100.milliseconds, timerTicks.last())
        }

    @Test
    fun `notification tick fires exactly once per second`() =
        runTest {
            var notificationCount = 0

            val timer =
                createTimer(
                    onNotificationTick = { notificationCount++ },
                )

            timer.start()
            advanceTime(2500.milliseconds)

            assertEquals(
                "Should fire twice in 2.5 seconds (at 1s and 2s marks)",
                2,
                notificationCount,
            )
        }

    @Test
    fun `resume continues from paused duration`() =
        runTest {
            val timerTicks = mutableListOf<Duration>()
            val timer =
                createTimer(
                    onTimerTick = { timerTicks.add(it) },
                )

            timer.start()
            advanceTime(2.seconds)

            timer.pause()
            assertEquals("Should capture duration at pause", 2.seconds, timerTicks.last())

            advanceTime(5.seconds)

            timer.start()
            advanceTime(1.seconds)

            assertEquals(
                "Should resume from 2s + 1s new run time (ignoring the 5s pause)",
                3.seconds,
                timerTicks.last(),
            )
        }

    @Test
    fun `start with custom duration seeks correctly`() =
        runTest {
            val timerTicks = mutableListOf<Duration>()
            val timer =
                createTimer(
                    onTimerTick = { timerTicks.add(it) },
                )

            timer.start(50.seconds)
            advanceTime(16.milliseconds)

            assertEquals(
                "Should start counting from the provided base duration",
                50.seconds + 16.milliseconds,
                timerTicks.last(),
            )
        }

    @Test
    fun `stop resets everything`() =
        runTest {
            var lastDuration: Duration = (-1).seconds
            val timer =
                createTimer(
                    onTimerTick = { lastDuration = it },
                )

            timer.start()
            advanceTime(1.seconds)
            assertEquals(1.seconds, lastDuration)

            timer.stop()

            assertEquals("Stop should reset duration to ZERO", Duration.ZERO, lastDuration)
        }
}

/*
 *  Copyright (c) 2023 Ashish Yadav <mailtoashish693@gmail.com>
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * A timer utility for audio recording and playback
 *
 * It manages three independent update loops on the provided [kotlinx.coroutines.CoroutineScope]:
 * 1. **UI Timer (~16ms):** High-frequency updates for smooth text counters (e.g., 00:01.45).
 * 2. **Waveform (~50ms):** Medium-frequency updates for visualizers.
 * 3. **Notification (~1000ms):** Optional low-frequency updates for system notifications.
 *
 * @param scope A lifecycle-aware scope (e.g., `lifecycleScope`) which ensures timers are automatically cancelled when the UI is destroyed.
 * @param onTimerTick Lambda invoked every ~16ms with the precise [kotlin.time.Duration] elapsed.
 * @param onAudioTick Lambda invoked every ~50ms to trigger waveform visualization updates.
 * @param onNotificationTick Optional lambda invoked every 1 second. If null, this loop is not started.
 */
class AudioTimer(
    private val scope: CoroutineScope,
    private val onTimerTick: (Duration) -> Unit,
    private val onAudioTick: () -> Unit,
    private val onNotificationTick: ((Duration) -> Unit)? = null,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    private var timerJob: Job? = null
    private var accumulatedDuration: Duration = Duration.ZERO

    // The point in time when the current recording session started (null if not running)
    private var sessionStartTime: TimeMark? = null

    /**
     * Starts the timer from the current accumulated duration.
     * idempotent: calling this while running does nothing.
     */
    fun start() {
        synchronized(this) {
            if (timerJob?.isActive == true) return

            sessionStartTime = timeSource.markNow()

            // A parent job to manage all tick loops concurrently
            timerJob =
                scope.launch {
                    launchUiLoop()
                    launchWaveformLoop()
                    launchNotificationLoop()
                }
        }
    }

    private fun CoroutineScope.launchUiLoop() =
        launch {
            while (isActive) {
                onTimerTick(calculateDuration())
                delay(UI_TICK_DELAY)
            }
        }

    private fun CoroutineScope.launchWaveformLoop() =
        launch {
            while (isActive) {
                onAudioTick()
                delay(WAVEFORM_TICK_DELAY)
            }
        }

    private fun CoroutineScope.launchNotificationLoop() {
        onNotificationTick?.let { callback ->
            launch {
                while (isActive) {
                    delay(NOTIFICATION_TICK_DELAY)
                    callback(calculateDuration())
                }
            }
        }
    }

    /**
     * Resets the timer to a specific duration and starts it immediately.
     * Useful when resuming an existing recording.
     */
    fun start(fromDuration: Duration) =
        synchronized(this) {
            timerJob?.cancel()
            timerJob = null

            accumulatedDuration = fromDuration
            sessionStartTime = null

            start()
        }

    /**
     * Pauses the timer, saving the current accumulated duration.
     */
    fun pause() =
        synchronized(this) {
            accumulatedDuration = calculateDuration()
            timerJob?.cancel()
            timerJob = null
            sessionStartTime = null
        }

    /**
     * Stops the timer and resets the duration to zero.
     */
    fun stop() {
        synchronized(this) {
            timerJob?.cancel()
            timerJob = null
            accumulatedDuration = Duration.ZERO
            sessionStartTime = null
        }

        onTimerTick(Duration.ZERO)
    }

    private fun calculateDuration(): Duration {
        // Total = (Saved Time) + (Time since start button pressed)
        val currentSession = sessionStartTime?.elapsedNow() ?: Duration.ZERO
        return accumulatedDuration + currentSession
    }

    companion object {
        private val UI_TICK_DELAY = 16.milliseconds
        private val WAVEFORM_TICK_DELAY = 50.milliseconds
        private val NOTIFICATION_TICK_DELAY = 1.seconds
    }
}

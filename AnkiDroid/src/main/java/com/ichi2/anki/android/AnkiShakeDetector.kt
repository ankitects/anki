/*
 * Copyright (c) 2026 Jatin Kumar <jnkr2409@gmail.com>
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

package com.ichi2.anki.android
import android.content.Context
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.squareup.seismic.ShakeDetector
import timber.log.Timber
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/*
 * Wrapper for a [ShakeDetector] to provide a cooldown mechanism.
 * This prevents the "Undo" action or other gestures from triggering multiple times
 * in rapid succession during a single physical shake event.
 */
class AnkiShakeDetector(
    private val sensorManager: SensorManager,
    /*
     * Sensor Delay tells how often the app should check for phone movement.
     *
     * We use [SensorManager.SENSOR_DELAY_UI] because:
     * - Enough Speed : It is fast enough to catch a normal shake.
     * - Battery Friendly: It uses less power than "Game" or "Fastest" settings.
     */
    private val sensorDelay: Int = SensorManager.SENSOR_DELAY_UI,
    private val listener: ShakeDetector.Listener,
    /*
     * The minimum time between shake events to prevent accidental double-triggers.
     *
     * Through trial and error, 800ms was determined to be the optimal 'sweet spot':
     * - 500ms : A single physical shake often registered as two distinct events,
     *   causing the flag to toggle on and immediately off again.
     *
     * - 1000ms+ : Felt unresponsive when the user wanted to quickly flag/unflag
     *   multiple cards in a row.
     *
     * - 800ms : Consistently filters out the "rebound" of a single shake while
     *   remaining responsive for intentional back-to-back actions.
     */
    private val cooldown: Duration = 800.milliseconds,
) : ShakeDetector.Listener {
    private val shakeDetector = ShakeDetector(this)
    private var lastShakeTime = 0L

    fun start() {
        sensorManager.let {
            shakeDetector.start(it, sensorDelay)
        }
    }

    fun stop() {
        shakeDetector.stop()
    }

    override fun hearShake() {
        Timber.d("The time since the last shake was: ${SystemClock.elapsedRealtime() - lastShakeTime}")
        val currentTime = SystemClock.elapsedRealtime()
        if (currentTime - lastShakeTime < cooldown.inWholeMilliseconds) {
            return
        }
        try {
            listener.hearShake()
        } finally {
            lastShakeTime = SystemClock.elapsedRealtime()
        }
    }

    companion object {
        fun createInstance(
            context: Context,
            listener: ShakeDetector.Listener,
        ): AnkiShakeDetector? {
            val sensorManager = context.getSystemService<SensorManager>()
            return sensorManager?.let {
                AnkiShakeDetector(
                    sensorManager = sensorManager,
                    listener = listener,
                )
            }
        }
    }
}

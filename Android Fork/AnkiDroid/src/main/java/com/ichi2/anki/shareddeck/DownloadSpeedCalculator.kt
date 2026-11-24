// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.shareddeck

/**
 * Smooths the download speed so it doesn't jump around between polls.
 *
 * @param alpha how much the newest sample counts, from 0 to 1
 * @param stallTimeoutMillis how long the byte count can stay still before the speed starts to drop
 */
class DownloadSpeedCalculator(
    private val alpha: Double = 0.5,
    private val stallTimeoutMillis: Long = 5_000,
) {
    init {
        require(alpha in 0.0..1.0) { "Alpha must be between 0.0 and 1.0" }
    }

    private var lastSmoothedSpeed: Double = 0.0
    private var lastBytesDownloaded: Long = 0
    private var lastTimeChecked: Long? = null

    /** Returns the smoothed speed in bytes per second, given the total bytes downloaded so far. */
    fun update(
        downloadedBytes: Long,
        currentTimeMillis: Long,
    ): Double {
        val lastTime = lastTimeChecked
        if (lastTime != null && currentTimeMillis > lastTime) {
            val timeDiff = currentTimeMillis - lastTime
            val bytesDiff = downloadedBytes - lastBytesDownloaded

            if (bytesDiff == 0L) {
                if (timeDiff >= stallTimeoutMillis) lastSmoothedSpeed *= 1 - alpha
                return lastSmoothedSpeed
            }

            if (bytesDiff >= 0) {
                val instantSpeed = (bytesDiff * 1000.0) / timeDiff
                lastSmoothedSpeed =
                    if (lastSmoothedSpeed == 0.0 && instantSpeed > 0) {
                        // Initialize with the first non-zero speed to avoid a slow ramp up from 0.
                        instantSpeed
                    } else {
                        (lastSmoothedSpeed * (1 - alpha)) + (instantSpeed * alpha)
                    }
            }
        }
        lastBytesDownloaded = downloadedBytes
        lastTimeChecked = currentTimeMillis
        return lastSmoothedSpeed
    }

    /** Starts over for a new download. */
    fun reset() {
        lastSmoothedSpeed = 0.0
        lastBytesDownloaded = 0
        lastTimeChecked = null
    }
}

// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.time

import androidx.annotation.VisibleForTesting
import java.util.Stack

/** Singleton providing an instance of [Time].
 * Used for tests to mock the time provider
 * without forcing the direct dependency on a [Time] instance
 *
 * For later: move this into a DI container
 */
object TimeManager {
    @VisibleForTesting
    fun reset() {
        mockInstances.clear()
    }

    @VisibleForTesting
    fun resetWith(mockTime: Time) {
        reset()
        mockInstances.push(mockTime)
    }

    private var mockInstances: Stack<Time> = Stack()

    var time: Time = SystemTime()
        get() = if (mockInstances.any()) mockInstances.peek() else field
        private set
}

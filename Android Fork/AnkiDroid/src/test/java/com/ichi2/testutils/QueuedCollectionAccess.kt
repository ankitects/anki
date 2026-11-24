// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import com.ichi2.anki.CollectionManager
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle

/**
 * Queues collection access inside [block]: `withCol` waits until the test scheduler advances
 * (e.g. [advanceUntilIdle]), so an operation can be observed while it is in progress.
 */
suspend fun <T> TestScope.withQueuedCollectionAccess(block: suspend () -> T): T {
    val previousQueue = CollectionManager.setTestDispatcher(StandardTestDispatcher(testScheduler), useReentrantLock = false)
    try {
        return block()
    } finally {
        CollectionManager.setTestDispatcher(previousQueue)
    }
}

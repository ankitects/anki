// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import com.ichi2.anki.CollectionManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlin.test.assertSame

suspend fun <T> withCollectionAccessOn(
    dispatcher: CoroutineDispatcher,
    block: suspend () -> T,
): T {
    val previousQueue = CollectionManager.setTestDispatcher(dispatcher, useReentrantLock = false)
    try {
        return block()
    } finally {
        CollectionManager.setTestDispatcher(previousQueue)
    }
}

/** Use one scheduler for Main and collection work, restoring the production queue before teardown. */
internal fun withCollectionTestDispatcher(
    dispatcher: TestDispatcher,
    block: () -> Unit,
) {
    val previousDispatcher = CollectionManager.setTestDispatcher(dispatcher)
    val dispatcherAfterTest: CoroutineDispatcher
    try {
        block()
    } finally {
        dispatcherAfterTest = CollectionManager.setTestDispatcher(previousDispatcher)
    }
    assertSame(
        dispatcher,
        dispatcherAfterTest,
        "CollectionManager dispatcher was not restored. Save and restore it in a finally block.",
    )
}

/** Exercise callers while opening the collection fails, restoring the previous failure mode. */
internal inline fun withNullCollection(block: () -> Unit) {
    val previousFailure = CollectionManager.emulatedOpenFailure
    CollectionManager.closeCollectionBlocking()
    CollectionManager.emulatedOpenFailure = CollectionManager.CollectionOpenFailure.LOCKED
    try {
        block()
    } finally {
        CollectionManager.emulatedOpenFailure = previousFailure
    }
}

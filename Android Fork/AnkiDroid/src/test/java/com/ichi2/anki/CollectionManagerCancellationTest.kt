// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager.withCol
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.util.ReflectionHelpers
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

@RunWith(AndroidJUnit4::class)
class CollectionManagerCancellationTest : RobolectricTest() {
    @Test
    fun `withCol rejects an already cancelled coroutine`() =
        runTest {
            assertFalse(CollectionManager.isOpenUnsafe())
            launch {
                cancel()
                assertCollectionAccessCancelled()
            }.join()
            assertFalse(CollectionManager.isOpenUnsafe(), "cancelled work must not open the collection")
        }

    @Test
    fun `withCol skips work cancelled while waiting for the Robolectric lock`() =
        runTest {
            val cancelled = withCol { cancelQueuedCollectionAccess() }
            cancelled.join()
            // Cancellation must release the lock and leave subsequent collection access working.
            assertFalse(withCol { dbClosed })
        }

    private suspend fun assertCollectionAccessCancelled() {
        assertFailsWith<CancellationException> {
            withCol { error("cancelled work must not run") }
        }
    }

    /** The caller holds the collection lock until the worker has been cancelled. */
    private fun CoroutineScope.cancelQueuedCollectionAccess(): Job {
        val waitingThread = AtomicReference<Thread>()
        val worker =
            launch(Dispatchers.IO) {
                waitingThread.set(Thread.currentThread())
                assertCollectionAccessCancelled()
            }
        try {
            awaitCollectionLock(waitingThread)
        } finally {
            // Also cancel on assertion failure, before the caller releases the lock.
            worker.cancel()
        }
        return worker
    }

    private fun awaitCollectionLock(waitingThread: AtomicReference<Thread>) {
        // Inspect the actual lock so cancellation happens after the worker starts waiting.
        val mutex = ReflectionHelpers.getStaticField<ReentrantLock>(CollectionManager::class.java, "testMutex")
        val start = TimeSource.Monotonic.markNow()
        while (waitingThread.get()?.let(mutex::hasQueuedThread) != true) {
            assertTrue(start.elapsedNow() < 10.seconds, "worker did not wait for the collection lock")
            Thread.sleep(1)
        }
    }
}

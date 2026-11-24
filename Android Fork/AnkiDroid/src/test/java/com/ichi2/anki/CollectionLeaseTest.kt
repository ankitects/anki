// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import com.ichi2.anki.CollectionManager.collectionLease
import com.ichi2.anki.CollectionManager.tryWithCol
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.CollectionManager.withColExclusive
import com.ichi2.anki.CollectionManager.withLeaseForTest
import com.ichi2.anki.exception.CollectionLockedException
import com.ichi2.testutils.JvmTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import net.ankiweb.rsdroid.Backend
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.notNullValue
import org.hamcrest.Matchers.nullValue
import org.junit.Test
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Test of [CollectionLease], and of the callers which react to one being held */
class CollectionLeaseTest : JvmTest() {
    @Test
    fun `no lease is held by default`() =
        runTest {
            assertThat(collectionLease, nullValue())
        }

    @Test
    fun `a brief operation takes no lease`() =
        runTest {
            withCol {
                assertThat(collectionLease, nullValue())
            }
        }

    @Test
    fun `lease is held for the duration of the operation`() =
        runTest {
            withColExclusive(CollectionOperation.SYNC) {
                assertThat("held inside the block", collectionLease, notNullValue())
                assertThat(collectionLease!!.operation, equalTo(CollectionOperation.SYNC))
            }

            assertThat("released after the block", collectionLease, nullValue())
        }

    @Test
    fun `lease is released if the operation throws`() =
        runTest {
            assertFailsWith<IllegalStateException> {
                withColExclusive(CollectionOperation.FULL_DOWNLOAD) {
                    error("sync failed")
                }
            }

            assertThat(collectionLease, nullValue())
        }

    @Test
    fun `cancel is exposed to observers of the lease`() =
        runTest {
            var abortCalls = 0

            withColExclusive(CollectionOperation.SYNC, onCancel = { abortCalls++ }) {
                // an observer cancels the operation without needing to know what it is
                collectionLease!!.cancel()
                collectionLease!!.cancel()
            }

            assertEquals(1, abortCalls, "onCancel is invoked only once via the lease")
        }

    @Test
    fun `onCancel is handed the backend the operation is running against`() =
        runTest {
            // a handler which resolved its own backend would have to enter the collection queue,
            // which the operation being cancelled is holding
            var cancelledWith: Backend? = null

            withColExclusive(CollectionOperation.SYNC, onCancel = { cancelledWith = it }) {
                collectionLease!!.cancel()
            }

            assertSame(CollectionManager.getBackend(), cancelledWith, "onCancel receives the live backend")
        }

    @Test
    fun `tryWithCol runs the block when no lease is held`() =
        runTest {
            assertThat(tryWithCol { decks.count() }, equalTo(1))
        }

    @Test
    fun `tryWithCol returns null rather than queueing behind a lease`() =
        runTest {
            val result =
                withLeaseForTest(CollectionOperation.SYNC) {
                    tryWithCol { decks.count() }
                }

            assertThat("skipped rather than queued", result, nullValue())
        }

    @Test
    fun `tryWithCol skips for any long-running operation, not just sync`() =
        runTest {
            val result =
                withLeaseForTest(CollectionOperation.CHECK_DATABASE) {
                    tryWithCol { decks.count() }
                }

            assertThat(result, nullValue())
        }

    @Test
    fun `tryWithCol resumes once the lease is released`() =
        runTest {
            withLeaseForTest(CollectionOperation.SYNC) {
                assertThat(tryWithCol { decks.count() }, nullValue())
            }

            assertThat("no longer skipped", tryWithCol { decks.count() }, equalTo(1))
        }

    @Test
    fun `a lease cannot overtake brief work paused before queue dispatch`() =
        runTest {
            val queue = StandardTestDispatcher(testScheduler)
            val pausedBrief = CoroutineName("paused brief")
            var dispatchBrief: (() -> Unit)? = null
            val pausedQueue =
                object : CoroutineDispatcher() {
                    override fun dispatch(
                        context: CoroutineContext,
                        block: Runnable,
                    ) {
                        if (context[CoroutineName] === pausedBrief) {
                            // Pause after admission but before submitting to the serial queue.
                            // Only this coroutine may save dispatchBrief: application background
                            // work can dispatch concurrently and must not overwrite the saved task.
                            dispatchBrief = { queue.dispatch(context, block) }
                        } else {
                            queue.dispatch(context, block)
                        }
                    }
                }
            withCollectionQueue(pausedQueue) {
                val events = mutableListOf<String>()
                // Application startup can submit unrelated work (for example, the rollover alarm).
                // It must not steal the pause intended for the brief call below.
                launch(start = CoroutineStart.UNDISPATCHED) { withCol { } }
                val brief =
                    async(pausedBrief, start = CoroutineStart.UNDISPATCHED) {
                        tryWithCol { events.add("brief") }
                    }
                val otherBrief =
                    async(start = CoroutineStart.UNDISPATCHED) {
                        tryWithCol { events.add("other brief") }
                    }
                val sync =
                    launch(start = CoroutineStart.UNDISPATCHED) {
                        withColExclusive(CollectionOperation.SYNC) { events.add("sync") }
                    }
                try {
                    testScheduler.runCurrent()
                    assertEquals(true, otherBrief.await(), "overlapping brief work must not be skipped")
                    assertEquals(listOf("other brief"), events, "sync must wait for every admitted brief call")
                    assertThat(collectionLease, notNullValue())
                    assertThat(tryWithCol { error("new work must be skipped while sync waits") }, nullValue())
                } finally {
                    checkNotNull(dispatchBrief).invoke()
                }
                assertEquals(true, brief.await())
                sync.join()
                assertEquals(listOf("other brief", "brief", "sync"), events)
            }
        }

    @Test
    fun `a lease waits for all admitted brief calls without skipping overlapping calls`() =
        runTest {
            withCollectionQueue(StandardTestDispatcher(testScheduler)) {
                val events = mutableListOf<String>()
                val first =
                    async(start = CoroutineStart.UNDISPATCHED) {
                        tryWithCol { events.add("first") }
                    }
                val second =
                    async(start = CoroutineStart.UNDISPATCHED) {
                        tryWithCol { events.add("second") }
                    }
                val sync =
                    launch(start = CoroutineStart.UNDISPATCHED) {
                        // Observe admission independently of the collection queue's ordering.
                        withLeaseForTest(CollectionOperation.SYNC) { events.add("sync") }
                    }
                assertFalse(sync.isCompleted, "the lease must wait for both admitted calls")
                assertThat(collectionLease, notNullValue())
                assertThat(tryWithCol { error("new work must be skipped while sync waits") }, nullValue())
                assertEquals(true, first.await())
                assertEquals(true, second.await())
                sync.join()
                assertEquals(listOf("first", "second", "sync"), events)
                assertThat(collectionLease, nullValue())
            }
        }

    @Test
    fun `cancelling admitted brief work allows a waiting lease to proceed`() =
        runTest {
            withCollectionQueue(StandardTestDispatcher(testScheduler)) {
                val brief =
                    launch(start = CoroutineStart.UNDISPATCHED) {
                        tryWithCol { error("cancelled brief work must not run") }
                    }
                var syncRan = false
                val sync =
                    launch(start = CoroutineStart.UNDISPATCHED) {
                        withLeaseForTest(CollectionOperation.SYNC) { syncRan = true }
                    }
                assertFalse(syncRan)
                brief.cancelAndJoin()
                sync.join()
                assertTrue(syncRan)
                assertThat(collectionLease, nullValue())
                assertEquals(1, tryWithCol { decks.count() })
            }
        }

    @Test
    fun `failing admitted brief work allows a waiting lease to proceed`() =
        runTest {
            withCollectionQueue(StandardTestDispatcher(testScheduler)) {
                val brief =
                    launch(start = CoroutineStart.UNDISPATCHED) {
                        assertFailsWith<IllegalStateException> {
                            tryWithCol { error("brief work failed") }
                        }
                    }
                var syncRan = false
                val sync =
                    launch(start = CoroutineStart.UNDISPATCHED) {
                        withLeaseForTest(CollectionOperation.SYNC) { syncRan = true }
                    }
                assertFalse(syncRan)
                brief.join()
                sync.join()
                assertTrue(syncRan)
                assertThat(collectionLease, nullValue())
                assertEquals(1, tryWithCol { decks.count() })
            }
        }

    @Test
    fun `cancelling a lease waiting for brief work allows more brief work`() =
        runTest {
            withCollectionQueue(StandardTestDispatcher(testScheduler)) {
                val first = async(start = CoroutineStart.UNDISPATCHED) { tryWithCol { decks.count() } }
                var abortCalls = 0
                val sync =
                    launch(start = CoroutineStart.UNDISPATCHED) {
                        withColExclusive(CollectionOperation.SYNC, onCancel = { abortCalls++ }) {
                            error("cancelled sync must not run")
                        }
                    }
                collectionLease!!.cancel()
                sync.join()
                assertTrue(sync.isCancelled)
                assertEquals(0, abortCalls)
                assertThat(collectionLease, nullValue())
                val second = async(start = CoroutineStart.UNDISPATCHED) { tryWithCol { decks.count() } }
                val next =
                    launch(start = CoroutineStart.UNDISPATCHED) {
                        withLeaseForTest(CollectionOperation.FULL_DOWNLOAD) { }
                    }
                assertFalse(next.isCompleted, "the next lease must still wait for admitted work")
                assertEquals(1, first.await())
                assertEquals(1, second.await())
                next.join()
                assertThat(collectionLease, nullValue())
            }
        }

    @Test
    fun `cancelling an overlapping operation preserves the owning lease`() =
        runTest {
            withCollectionQueue(StandardTestDispatcher(testScheduler)) {
                val release = CompletableDeferred<Unit>()
                val first =
                    async(start = CoroutineStart.UNDISPATCHED) {
                        withLeaseForTest(CollectionOperation.SYNC) {
                            release.await()
                        }
                    }
                val lease = assertNotNull(collectionLease)
                val second =
                    launch(start = CoroutineStart.UNDISPATCHED) {
                        withColExclusive(CollectionOperation.FULL_DOWNLOAD) {
                            error("cancelled operation must not run")
                        }
                    }
                assertSame(lease, collectionLease, "a waiting operation must not replace the owner")
                second.cancelAndJoin()
                assertSame(lease, collectionLease, "cancelling a waiter must not clear the owner")
                assertThat(tryWithCol { error("sync still holds the lease") }, nullValue())
                release.complete(Unit)
                first.await()
                assertThat(collectionLease, nullValue())
            }
        }

    @Test
    fun `cancelling a queued lease skips its block without aborting the running operation`() =
        runTest {
            withCollectionQueue(StandardTestDispatcher(testScheduler)) {
                // Brief work suspends on queue dispatch; the next launch publishes its lease.
                // When the scheduler resumes brief work, it cancels the lease before it can run.
                val brief =
                    async(start = CoroutineStart.UNDISPATCHED) {
                        withCol {
                            assertNotNull(collectionLease, "lease must be published before brief work runs").cancel()
                        }
                    }
                val queued =
                    launch(start = CoroutineStart.UNDISPATCHED) {
                        withColExclusive(
                            CollectionOperation.SYNC,
                            onCancel = { error("a queued lease must not abort running work") },
                        ) {
                            error("a cancelled lease must not enter its block")
                        }
                    }

                brief.await()
                queued.join()
                assertTrue(queued.isCancelled, "Cancel must cancel the queued coroutine")
                assertThat(collectionLease, nullValue())
            }
        }

    @Test
    fun `a completed lease cannot cancel a subsequent operation`() =
        runTest {
            var abortCalls = 0
            val oldLease =
                withColExclusive(CollectionOperation.SYNC, onCancel = { abortCalls++ }) {
                    collectionLease!!
                }
            withColExclusive(CollectionOperation.FULL_DOWNLOAD) {
                oldLease.cancel()
                assertEquals(0, abortCalls, "a retained Cancel handler must be inert after completion")
            }
        }

    @Test
    fun `overlapping operations acquire distinct leases in order`() =
        runTest {
            withCollectionQueue(StandardTestDispatcher(testScheduler)) {
                val release = CompletableDeferred<Unit>()
                val first =
                    async(start = CoroutineStart.UNDISPATCHED) {
                        withLeaseForTest(CollectionOperation.SYNC) {
                            release.await()
                            assertNotNull(collectionLease)
                        }
                    }
                val lease = assertNotNull(collectionLease)
                val second =
                    async(start = CoroutineStart.UNDISPATCHED) {
                        withColExclusive(CollectionOperation.FULL_DOWNLOAD) { assertNotNull(collectionLease) }
                    }
                assertSame(lease, collectionLease, "sync retains ownership until completion")
                assertFalse(second.isCompleted, "the overlapping operation must wait for the owner")
                release.complete(Unit)
                assertEquals(CollectionOperation.SYNC, first.await().operation)
                assertEquals(CollectionOperation.FULL_DOWNLOAD, second.await().operation)
                assertThat(collectionLease, nullValue())
            }
        }

    @Test
    fun `failure to open the collection releases lease ownership`() =
        runTest {
            CollectionManager.emulatedOpenFailure = CollectionManager.CollectionOpenFailure.LOCKED
            try {
                assertFailsWith<CollectionLockedException> {
                    withColExclusive(CollectionOperation.SYNC) { error("collection could not open") }
                }
                assertThat(collectionLease, nullValue())
            } finally {
                CollectionManager.emulatedOpenFailure = null
            }
            withColExclusive(CollectionOperation.FULL_DOWNLOAD) {
                assertEquals(CollectionOperation.FULL_DOWNLOAD, collectionLease!!.operation)
            }
        }

    /** Exercise the withContext queue, and restore the collection queue before teardown. */
    private suspend fun TestScope.withCollectionQueue(
        dispatcher: CoroutineDispatcher,
        block: suspend CoroutineScope.() -> Unit,
    ) {
        val previousDispatcher = CollectionManager.setTestDispatcher(dispatcher, useReentrantLock = false)
        try {
            coroutineScope(block)
        } finally {
            CollectionManager.setTestDispatcher(previousDispatcher)
        }
    }
}

// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.observability

import anki.collection.opChanges
import com.ichi2.testutils.JvmTest
import com.ichi2.testutils.withCollectionAccessOn
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

class OpChangesNotifierTest : JvmTest() {
    @Test
    fun `operation returns its response after notifying subscribers with its handler`() =
        runTest {
            val changes = opChanges { tag = true }
            val handler = Any()
            ensureOpsExecuted(1) {
                ensureOpWithHandler(handler) {
                    assertSame(changes, undoableOp(handler) { changes })
                }
            }
        }

    @Test
    fun `cancelling a queued operation skips the mutation and notification`() =
        runTest {
            val observer = ChangeCounter()
            ChangeManager.subscribe(observer)
            var executed = false
            withCollectionAccessOn(StandardTestDispatcher(testScheduler)) {
                val operation =
                    launch(start = CoroutineStart.UNDISPATCHED) {
                        undoableOp {
                            executed = true
                            opChanges { tag = true }
                        }
                    }

                operation.cancelAndJoin()

                assertFalse(executed)
                assertEquals(0, observer.changeCount)
            }
        }

    @Test
    fun `cancelling during a mutation still notifies before the operation completes`() =
        runTest {
            val observer = ChangeCounter()
            ChangeManager.subscribe(observer)
            val operation =
                launch {
                    val caller = currentCoroutineContext().job
                    undoableOp {
                        caller.cancel()
                        opChanges { tag = true }
                    }
                    fail("Cancellation must still propagate to the caller")
                }

            operation.join()

            assertTrue(operation.isCancelled)
            assertEquals(1, observer.changeCount)
        }

    @Test
    fun `cancelling while notification is queued on Main still delivers it`() =
        runTest {
            val observer = ChangeCounter()
            ChangeManager.subscribe(observer)
            val worker = StandardTestDispatcher(testScheduler)
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            var executed = false
            try {
                withCollectionAccessOn(worker) {
                    val operation =
                        launch(worker, start = CoroutineStart.UNDISPATCHED) {
                            undoableOp {
                                executed = true
                                opChanges { tag = true }
                            }
                            fail("Cancellation must still propagate to the caller")
                        }
                    assertTrue(executed)
                    assertEquals(0, observer.changeCount)

                    operation.cancel()
                    runCurrent()
                    operation.join()

                    assertTrue(operation.isCancelled)
                    assertEquals(1, observer.changeCount)
                }
            } finally {
                Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            }
        }
}

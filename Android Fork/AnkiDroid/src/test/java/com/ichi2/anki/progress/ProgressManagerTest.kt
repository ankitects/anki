// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.progress

import com.ichi2.anki.ProgressContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class ProgressManagerTest {
    @Test
    fun `initial state is Idle`() {
        val manager = ProgressManager<String>()
        assertIs<ViewModelProgress.Idle>(manager.progress.value)
    }

    @Test
    fun `withProgress transitions to Active and back to Idle`() =
        runTest {
            val manager = ProgressManager<String>()

            manager.withProgress(message = "Loading...") {
                val state = manager.progress.value
                assertIs<ViewModelProgress.Active<String>>(state)
                assertEquals("Loading...", state.message)
            }

            assertIs<ViewModelProgress.Idle>(manager.progress.value)
        }

    @Test
    fun `withProgress returns block result`() =
        runTest {
            val manager = ProgressManager<String>()
            val result =
                manager.withProgress {
                    42
                }
            assertEquals(42, result)
        }

    @Test
    fun `withProgress returns to Idle even on exception`() =
        runTest {
            val manager = ProgressManager<String>()
            try {
                manager.withProgress {
                    throw RuntimeException("test error")
                }
            } catch (_: RuntimeException) {
                // expected
            }
            assertIs<ViewModelProgress.Idle>(manager.progress.value)
        }

    @Test
    fun `concurrent operations keep Active until all complete`() =
        runTest {
            val manager = ProgressManager<String>()
            val deferred1 = CompletableDeferred<Unit>()
            val deferred2 = CompletableDeferred<Unit>()

            val job1 =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    manager.withProgress(message = "Op 1") {
                        deferred1.await()
                    }
                }

            val job2 =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    manager.withProgress(message = "Op 2") {
                        deferred2.await()
                    }
                }

            assertIs<ViewModelProgress.Active<String>>(manager.progress.value)

            deferred1.complete(Unit)

            assertIs<ViewModelProgress.Active<String>>(manager.progress.value)

            deferred2.complete(Unit)

            assertIs<ViewModelProgress.Idle>(manager.progress.value)

            job1.join()
            job2.join()
        }

    @Test
    fun `updateProgress updates state mid-operation`() =
        runTest {
            val manager = ProgressManager<String>()

            manager.withProgress(message = "Starting") {
                val initialState = manager.progress.value
                assertIs<ViewModelProgress.Active<String>>(initialState)
                assertEquals("Starting", initialState.message)

                val testAmount = ProgressContext.Amount(current = 5, max = 10)
                updateProgress(message = "Step 2", amount = testAmount)

                val updatedState = manager.progress.value
                assertIs<ViewModelProgress.Active<String>>(updatedState)
                assertEquals("Step 2", updatedState.message)
                assertEquals(testAmount, updatedState.amount)
            }
        }

    @Test
    fun `updateProgress without a message keeps the current one`() =
        runTest {
            val manager = ProgressManager<String>()
            val message = "Processing"

            manager.withProgress(message = message) {
                val amount = ProgressContext.Amount(current = 1, max = 2)
                updateProgress(amount = amount)

                val state = manager.progress.value
                assertIs<ViewModelProgress.Active<String>>(state)
                assertEquals(message, state.message)
                assertEquals(amount, state.amount)
            }
        }

    @Test
    fun `onCancel makes dialog cancellable`() =
        runTest {
            val manager = ProgressManager<String>()

            manager.withProgress(onCancel = { }) {
                val state = manager.progress.value
                assertIs<ViewModelProgress.Active<String>>(state)
                assertEquals(true, state.cancellable)
            }
        }

    @Test
    fun `null onCancel makes dialog non-cancellable`() =
        runTest {
            val manager = ProgressManager<String>()

            manager.withProgress {
                val state = manager.progress.value
                assertIs<ViewModelProgress.Active<String>>(state)
                assertEquals(false, state.cancellable)
            }
        }

    @Test
    fun `requestCancel invokes onCancel callback`() =
        runTest {
            val manager = ProgressManager<String>()
            var cancelCalled = false

            val job =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    manager.withProgress(onCancel = { cancelCalled = true }) {
                        CompletableDeferred<Unit>().await()
                    }
                }

            manager.requestCancel()
            assertTrue(cancelCalled)
            job.cancel()
        }

    @Test
    fun `requestCancel invokes every active cancellable op`() =
        runTest {
            val manager = ProgressManager<String>()
            var firstCalled = false
            var secondCalled = false

            val job1 =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    manager.withProgress(onCancel = { firstCalled = true }) {
                        CompletableDeferred<Unit>().await()
                    }
                }
            val job2 =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    manager.withProgress(onCancel = { secondCalled = true }) {
                        CompletableDeferred<Unit>().await()
                    }
                }

            manager.requestCancel()
            assertTrue(firstCalled)
            assertTrue(secondCalled)

            job1.cancel()
            job2.cancel()
        }

    @Test
    fun `requestCancel only fires cancellable ops, non-cancellable keep running`() =
        runTest {
            val manager = ProgressManager<String>()
            var cancellableFired = false
            var nonCancellableFired = false

            val cancellableJob =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    manager.withProgress(onCancel = { cancellableFired = true }) {
                        CompletableDeferred<Unit>().await()
                    }
                }
            val nonCancellableJob =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    manager.withProgress(onCancel = null) {
                        nonCancellableFired = true
                        CompletableDeferred<Unit>().await()
                    }
                }

            manager.requestCancel()
            assertTrue(cancellableFired)
            assertTrue(nonCancellableFired)

            cancellableJob.cancel()
            nonCancellableJob.cancel()
        }

    @Test
    fun `state is cancellable while any cancellable op is active`() =
        runTest {
            val manager = ProgressManager<String>()
            val cancellableDone = CompletableDeferred<Unit>()
            val nonCancellableDone = CompletableDeferred<Unit>()

            val cancellableJob =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    manager.withProgress(onCancel = { }) {
                        cancellableDone.await()
                    }
                }
            val nonCancellableJob =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    manager.withProgress {
                        nonCancellableDone.await()
                    }
                }

            assertEquals(true, (manager.progress.value as ViewModelProgress.Active<String>).cancellable)

            cancellableDone.complete(Unit)
            assertEquals(false, (manager.progress.value as ViewModelProgress.Active<String>).cancellable)

            nonCancellableDone.complete(Unit)
            cancellableJob.join()
            nonCancellableJob.join()
        }

    @Test
    fun `updateProgress preserves derived cancellable flag`() =
        runTest {
            val manager = ProgressManager<String>()
            val cancellableDone = CompletableDeferred<Unit>()
            val nonCancellableDone = CompletableDeferred<Unit>()

            val cancellableJob =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    manager.withProgress(onCancel = { }) {
                        cancellableDone.await()
                    }
                }
            val nonCancellableJob =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    manager.withProgress {
                        updateProgress(message = "mid-update")
                        nonCancellableDone.await()
                    }
                }

            val state = manager.progress.value as ViewModelProgress.Active
            assertEquals("mid-update", state.message)
            assertEquals(true, state.cancellable)

            cancellableDone.complete(Unit)
            nonCancellableDone.complete(Unit)
            cancellableJob.join()
            nonCancellableJob.join()
        }

    @Test
    fun `newer op overrides previous ops message`() =
        runTest {
            val manager = ProgressManager<String>()
            val aDone = CompletableDeferred<Unit>()
            val bDone = CompletableDeferred<Unit>()

            val jobA =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    manager.withProgress(message = "A") { aDone.await() }
                }
            val jobB =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    manager.withProgress(message = "B") { bDone.await() }
                }

            val state = manager.progress.value as ViewModelProgress.Active
            assertEquals("B", state.message, "newest op wins")

            aDone.complete(Unit)
            bDone.complete(Unit)
            jobA.join()
            jobB.join()
        }

    @Test
    fun `when latest op ends remaining ops message is shown`() =
        runTest {
            val manager = ProgressManager<String>()
            val aDone = CompletableDeferred<Unit>()
            val bDone = CompletableDeferred<Unit>()

            val jobA =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    manager.withProgress(message = "A") { aDone.await() }
                }
            val jobB =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    manager.withProgress(message = "B") { bDone.await() }
                }

            assertEquals("B", (manager.progress.value as ViewModelProgress.Active<String>).message)

            bDone.complete(Unit)
            jobB.join()
            assertEquals("A", (manager.progress.value as ViewModelProgress.Active<String>).message)

            aDone.complete(Unit)
            jobA.join()
            assertIs<ViewModelProgress.Idle>(manager.progress.value)
        }

    @Test
    fun `updateProgress on a non-displayed op does not flicker the message`() =
        runTest {
            val manager = ProgressManager<String>()
            val aDone = CompletableDeferred<Unit>()
            val bDone = CompletableDeferred<Unit>()
            val aUpdated = CompletableDeferred<Unit>()

            val jobA =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    manager.withProgress(message = "A") {
                        aUpdated.await()
                        updateProgress(message = "A-updated")
                        aDone.await()
                    }
                }
            val jobB =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    manager.withProgress(message = "B") { bDone.await() }
                }

            // B is the displayed op (latest-started).
            assertEquals("B", (manager.progress.value as ViewModelProgress.Active<String>).message)

            // A updates while B is still displayed — displayed message must NOT flicker to A's.
            aUpdated.complete(Unit)
            assertEquals(
                "B",
                (manager.progress.value as ViewModelProgress.Active<String>).message,
                "non-displayed op's update must not steal the dialog",
            )

            // B ends — A becomes the displayed op and surfaces its held-update.
            bDone.complete(Unit)
            jobB.join()
            assertEquals(
                "A-updated",
                (manager.progress.value as ViewModelProgress.Active<String>).message,
                "A's held update should surface once it becomes the displayed op",
            )

            aDone.complete(Unit)
            jobA.join()
        }

    @Test
    fun `cancellability drops when last cancellable op ends`() =
        runTest {
            val manager = ProgressManager<String>()
            val cancellableDone = CompletableDeferred<Unit>()
            val nonCancellableDone = CompletableDeferred<Unit>()

            val cancellableJob =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    manager.withProgress(onCancel = { }) { cancellableDone.await() }
                }
            val nonCancellableJob =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    manager.withProgress { nonCancellableDone.await() }
                }

            assertEquals(true, (manager.progress.value as ViewModelProgress.Active<String>).cancellable)

            cancellableDone.complete(Unit)
            cancellableJob.join()
            assertEquals(false, (manager.progress.value as ViewModelProgress.Active<String>).cancellable)

            nonCancellableDone.complete(Unit)
            nonCancellableJob.join()
        }

    @Test
    fun `cancellability stays true when one of many cancellable ops ends`() =
        runTest {
            val manager = ProgressManager<String>()
            val done1 = CompletableDeferred<Unit>()
            val done2 = CompletableDeferred<Unit>()

            val job1 =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    manager.withProgress(onCancel = { }) { done1.await() }
                }
            val job2 =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    manager.withProgress(onCancel = { }) { done2.await() }
                }

            done1.complete(Unit)
            job1.join()

            assertEquals(true, (manager.progress.value as ViewModelProgress.Active<String>).cancellable)

            done2.complete(Unit)
            job2.join()
        }

    @Test
    fun `observer pattern - rapid Active updates do not restart the delay`() =
        runTest {
            val manager = ProgressManager<String>()
            val done = CompletableDeferred<Unit>()
            var dialogShownAt: Long? = null
            var pendingShow: kotlinx.coroutines.Job? = null

            val observer =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    manager.progress.collect { state ->
                        when (state) {
                            is ViewModelProgress.Idle -> {
                                pendingShow?.cancel()
                                pendingShow = null
                            }
                            is ViewModelProgress.Active -> {
                                if (pendingShow == null && dialogShownAt == null) {
                                    pendingShow =
                                        launch {
                                            kotlinx.coroutines.delay(600.milliseconds)
                                            dialogShownAt = testScheduler.currentTime
                                            pendingShow = null
                                        }
                                }
                            }
                        }
                    }
                }

            val opJob =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    manager.withProgress(message = "start") {
                        // Stream of updates every 100ms would defeat the delay
                        // under the old collectLatest pattern.
                        repeat(10) { i ->
                            kotlinx.coroutines.delay(100.milliseconds)
                            updateProgress(message = "step $i")
                        }
                        done.await()
                    }
                }

            // After 600ms of total time, the dialog should have shown exactly once.
            testScheduler.advanceTimeBy(700.milliseconds)
            val shownAt = dialogShownAt
            assertTrue(shownAt != null, "dialog must have been shown by now")
            assertTrue(
                shownAt <= 650,
                "dialog shown at ${shownAt}ms, expected ~600ms (anti-flash delay not restarted)",
            )

            done.complete(Unit)
            opJob.join()
            observer.cancel()
        }
}

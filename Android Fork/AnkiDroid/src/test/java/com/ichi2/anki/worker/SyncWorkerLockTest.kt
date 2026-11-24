// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.worker

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.ichi2.anki.RobolectricTest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * A [RobolectricTest] because running a worker creates a backend through `TR`, and its
 * teardown is what discards the backend. A leftover backend defeats tests that swap
 * the backend factory, such as `BackendEmulatingOpenConflictTest`.
 */
@RunWith(AndroidJUnit4::class)
class SyncWorkerLockTest : RobolectricTest() {
    private inline fun <reified W : CoroutineWorker> worker(): W = TestListenableWorkerBuilder<W>(targetContext).build()

    private suspend fun assertDroppedWhileSwitching(
        lock: Mutex,
        run: suspend () -> ListenableWorker.Result,
    ) = lock.withLock {
        assertIs<ListenableWorker.Result.Failure>(run(), "rescheduled, it would run later with the AnkiWeb key of the profile being left")
        assertTrue(lock.isLocked, "the worker must not release a lock the switch holds")
    }

    private suspend fun assertReleasesAfterRunning(
        lock: Mutex,
        run: suspend () -> ListenableWorker.Result,
    ) {
        runCatching { run() }
        assertFalse(lock.isLocked, "a finished sync must not leave switching blocked")
    }

    @Test
    fun `a sync is dropped while a profile switch holds its lock`() =
        runTest { assertDroppedWhileSwitching(syncLock) { worker<SyncWorker>().doWork() } }

    @Test
    fun `a media sync is dropped while a profile switch holds its lock`() =
        runTest { assertDroppedWhileSwitching(mediaSyncLock) { worker<SyncMediaWorker>().doWork() } }

    @Test
    fun `a sync releases its lock when it finishes`() = runTest { assertReleasesAfterRunning(syncLock) { worker<SyncWorker>().doWork() } }

    @Test
    fun `a media sync releases its lock when it finishes`() =
        runTest { assertReleasesAfterRunning(mediaSyncLock) { worker<SyncMediaWorker>().doWork() } }
}

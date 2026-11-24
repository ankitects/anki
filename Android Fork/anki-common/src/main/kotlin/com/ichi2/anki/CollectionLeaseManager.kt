// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import com.ichi2.anki.common.time.TimeManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.ankiweb.rsdroid.Backend
import timber.log.Timber

/**
 * Owns [collection lease][CollectionLease] acquisition, publication and cleanup.
 *
 * @see withLease
 */
internal class CollectionLeaseManager {
    // Serialize ownership before entering the collection queue, so a waiter cannot replace its owner.
    private val mutex = Mutex()

    /**
     * Used in both [tryWithoutLease] and [withLease], so a lease waits for all previously accepted calls.
     */
    private val acceptanceLock = Any()
    private var briefCallCount = 0
    private var briefCallsFinished: CompletableDeferred<Unit>? = null

    val flowOfLease: StateFlow<CollectionLease?>
        field = MutableStateFlow<CollectionLease?>(null)

    val lease: CollectionLease? get() = flowOfLease.value

    /**
     * Publishes one lease at a time, cancelling queued work through its own coroutine scope.
     *
     * If a lease is already active, suspend until the lease is released.
     *
     * No new brief calls can be accepted after the lease is published.
     *
     * All previously accepted calls must finish before invoking [block].
     */
    suspend fun <T> withLease(
        operation: CollectionOperation,
        onCancel: (Backend) -> Unit = {},
        block: suspend (CollectionLease) -> T,
    ): T =
        mutex.withLock {
            coroutineScope {
                val lease =
                    CollectionLease(
                        operation = operation,
                        startedAt = TimeManager.time.intTimeMS(),
                        cancelQueued = { cancel() },
                        cancelRunning = onCancel,
                    )
                // wait for brief jobs to finish (& acquire briefCallsFinished to await)
                val briefWork =
                    synchronized(acceptanceLock) {
                        flowOfLease.value = lease
                        briefCallsFinished
                    }
                Timber.d("collection lease acquired: %s", operation)
                try {
                    // wait for all brief jobs to complete
                    briefWork?.await()
                    block(lease)
                } finally {
                    lease.finish()
                    synchronized(acceptanceLock) {
                        flowOfLease.value = null
                    }
                    Timber.d("collection lease released: %s", lease)
                }
            }
        }

    /**
     * Skips disposable work when a [CollectionLease] is held.
     *
     * Otherwise, reserves access until [block] finishes.
     */
    suspend fun <T> tryWithoutLease(block: suspend () -> T): T? {
        // Overlapping brief calls are accepted together.
        // `withLease` waits for all of them to complete before entering the collection queue.
        synchronized(acceptanceLock) {
            lease?.let {
                Timber.d("tryWithCol: skipped, %s", it)
                return null
            }
            if (briefCallCount++ == 0) {
                briefCallsFinished = CompletableDeferred()
            }
        }
        try {
            return block()
        } finally {
            val finished =
                synchronized(acceptanceLock) {
                    if (--briefCallCount == 0) {
                        briefCallsFinished.also { briefCallsFinished = null }
                    } else {
                        null
                    }
                }
            // Resume the waiting lease outside the lock.
            finished?.complete(Unit)
        }
    }
}

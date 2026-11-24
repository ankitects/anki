// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.libanki.EpochMilliseconds
import kotlinx.coroutines.CancellationException
import net.ankiweb.rsdroid.Backend

/**
 * A collection operation which blocks the collection for a long period of time (Sync, Import, etc...).
 *
 * NOTE: This is defined by duration; all collection operations block.
 *
 * @see CollectionManager.withColExclusive
 */
// TODO: #17551 Add leases for unused constants & profile the app to find more
enum class CollectionOperation {
    SYNC,
    FULL_DOWNLOAD,
    FULL_UPLOAD,
    IMPORT_COLLECTION_PACKAGE,
    CHECK_DATABASE,
    CHECK_MEDIA,
    EMPTY_CARDS,
    ;

    /** Whether this operation syncs the collection, including full uploads and downloads. */
    val isSync: Boolean
        get() =
            when (this) {
                SYNC, FULL_DOWNLOAD, FULL_UPLOAD -> true
                IMPORT_COLLECTION_PACKAGE, CHECK_DATABASE, CHECK_MEDIA, EMPTY_CARDS -> false
            }
}

/**
 * An in-flight [CollectionOperation]: either running on the collection queue, or waiting to enter
 * it, acting as an exclusive lock.
 *
 * @param operation what is holding the queue
 * @param cancelQueued cancels the coroutine waiting to enter the collection queue
 * @param cancelRunning requests cancellation of the running operation without waiting for it to end
 */
class CollectionLease internal constructor(
    val operation: CollectionOperation,
    val startedAt: EpochMilliseconds,
    private val cancelQueued: () -> Unit,
    private val cancelRunning: (Backend) -> Unit,
) {
    private sealed class State {
        data object Queued : State()

        /** @param backend the collection was opened against, for [cancelRunning] */
        data class Running(
            val backend: Backend,
        ) : State()

        data object Cancelled : State()

        data object Finished : State()
    }

    private val lock = Any()
    private var state: State = State.Queued

    /**
     * Skip a queued operation, or request cancellation of a running one. A running operation may
     * not stop immediately, or at all. Repeated requests and requests after completion do nothing.
     */
    val cancel: () -> Unit = {
        synchronized(lock) {
            // Keep the callback under the lock: it must finish before another operation starts
            // using the backend, otherwise an old Cancel button could abort that operation.
            when (val current = state) {
                State.Queued -> {
                    state = State.Cancelled
                    cancelQueued()
                }
                is State.Running -> {
                    state = State.Cancelled
                    cancelRunning(current.backend)
                }
                State.Cancelled, State.Finished -> {}
            }
        }
    }

    /**
     * Called inside the collection queue, after opening the collection.
     *
     * @param backend handed to [cancelRunning] if the operation is cancelled while it runs
     */
    internal fun <T> run(
        backend: Backend,
        block: () -> T,
    ): T {
        synchronized(lock) {
            if (state != State.Queued) throw CancellationException("Collection lease cancelled before execution")
            state = State.Running(backend)
        }
        try {
            return block()
        } finally {
            // Disable cancellation before leaving the queue, not when the caller resumes later.
            finish()
        }
    }

    internal fun finish() {
        synchronized(lock) {
            state = State.Finished
        }
    }

    /** Milliseconds elapsed since the operation started. Useful when diagnosing a blocked caller. */
    fun elapsedMs(): Long = TimeManager.time.intTimeMS() - startedAt

    override fun toString(): String = "CollectionLease($operation, held for ${elapsedMs()}ms)"
}

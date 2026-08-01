// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.observability

import anki.collection.OpChanges
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Provides non-blocking delivery of [OpChanges] to the [ChangeManager] subscriber registry.
 *
 * Use this when `suspend` is not feasible (e.g. `ContentProvider`).
 *
 * [publish] queues a change onto [changeChannel] and returns immediately.
 *
 * @param deliver forwards a change to subscribers; invoked on [Dispatchers.Main].
 */
internal class OpChangesPublisher(
    private val deliver: (changes: OpChanges, handler: Any?) -> Unit,
) {
    private data class ChangeEvent(
        val changes: OpChanges,
        val handler: Any?,
    )

    /** CoroutineScope which handles the coroutine consuming [changeChannel]. */
    private var scope: CoroutineScope? = null
    private val changeChannel =
        Channel<ChangeEvent>(
            capacity = Channel.UNLIMITED,
        )

    /** Ensures that [changeChannel] is being processed. */
    @Synchronized
    private fun ensureCollector() {
        if (scope != null) return
        scope =
            CoroutineScope(Dispatchers.Main + SupervisorJob()).also { s ->
                s.launch {
                    for (event in changeChannel) {
                        deliver(event.changes, event.handler)
                    }
                }
            }
    }

    /**
     * Queues [changes] for non-blocking delivery to subscribers.
     *
     * May be called on any thread.
     *
     * @see ChangeManager.publish
     */
    fun publish(
        changes: OpChanges,
        handler: Any?,
    ) {
        ensureCollector()
        changeChannel.trySend(ChangeEvent(changes, handler))
    }

    /** Cancels the collector and discards any queued, undelivered changes. */
    @Synchronized
    fun reset() {
        scope?.cancel()
        scope = null
        changeChannel.drain()
    }
}

private fun <T> Channel<T>.drain() {
    while (this.tryReceive().isSuccess) { /* intentionally empty */ }
}

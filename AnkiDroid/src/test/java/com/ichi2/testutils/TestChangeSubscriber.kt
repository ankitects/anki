// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import anki.collection.OpChanges
import com.ichi2.anki.observability.ChangeManager
import timber.log.Timber
import kotlin.test.fail

/**
 * Asserts no calls to [ChangeManager.notifySubscribers] are made (typically via `undoableOp`)
 */
suspend fun ensureNoOpsExecuted(block: suspend () -> Unit) {
    val subscription = ChangeCounter()
    ChangeManager.subscribe(subscription)
    block()
    if (!subscription.hasChanges) {
        Timber.d("ensureNoOpsExecuted: success")
        return
    }

    fail("ChangeManager should not be called; ${ChangeManager.subscriberCount} subscribers")
}

/**
 * Asserts [count] calls are made to [ChangeManager.notifySubscribers]
 */
suspend fun ensureOpsExecuted(
    count: Int,
    block: suspend () -> Unit,
) {
    val subscription = ChangeCounter()

    Timber.v("Listening for ChangeManager ops")
    ChangeManager.subscribe(subscription)
    block()
    if (subscription.changeCount == count) {
        Timber.d("ensureOpsExecuted: success")
        return
    }

    fail("ChangeManager: expected $count calls, got ${subscription.changeCount}; ${ChangeManager.subscriberCount} subscribers")
}

/**
 * Asserts that [ChangeManager.notifySubscribers] is called with [handler] as the handler
 */
suspend fun ensureOpWithHandler(
    handler: Any,
    block: suspend () -> Unit,
) {
    val handlerAccessor = ExtractOpHandler()
    ChangeManager.subscribe(handlerAccessor)
    block()
    if (handlerAccessor.handler === handler) {
        Timber.d("ensureOpWithHandler: success")
        return
    }
    fail("ChangeManager: expected handler to be $handler, but was ${handlerAccessor.handler}")
}

// used to ensure a strong reference to the subscription is held
private class ChangeCounter : ChangeManager.Subscriber {
    private var changes = 0
    val changeCount get() = changes
    val hasChanges get() = changes > 0

    override fun opExecuted(
        changes: OpChanges,
        handler: Any?,
    ) {
        Timber.d("ChangeManager op detected")
        this.changes++
    }
}

private class ExtractOpHandler : ChangeManager.Subscriber {
    var handler: Any? = null
        private set

    override fun opExecuted(
        changes: OpChanges,
        handler: Any?,
    ) {
        this.handler = handler
    }
}

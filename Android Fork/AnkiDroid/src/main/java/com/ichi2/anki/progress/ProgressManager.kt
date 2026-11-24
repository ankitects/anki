// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.progress

import com.ichi2.anki.ProgressContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * Progress state shared by a ViewModel and its UI.
 *
 * [M] is the ViewModel's message type: a domain value (typically an enum) which the UI maps
 * to display text in [observeProgress]. This keeps Android resources out of the ViewModel.
 *
 * Concurrent [withProgress] calls are supported: the flow stays [Active][ViewModelProgress.Active]
 * until every call finishes. The displayed message/amount comes from whichever op was last to
 * start or update (one dialog, last write wins). The dialog is cancellable if any active op
 * passed an `onCancel`, and [requestCancel] fires all of those callbacks.
 */
class ProgressManager<M : Any> {
    val progress: StateFlow<ViewModelProgress<M>>
        field = MutableStateFlow<ViewModelProgress<M>>(ViewModelProgress.Idle)

    private val lock = Any()

    /** Keyed by op id, iteration order is start/update order the last entry wins. */
    private val activeOps = linkedMapOf<Long, Op<M>>()
    private val nextOpId = AtomicLong(0)

    /**
     * Mutable per-op state. Mirrors [ProgressContext]'s `var`-field style so updates
     * don't allocate. The instance is reused across updates and only the map entry
     * is re-inserted (to move it to the "latest" position).
     */
    private class Op<M : Any>(
        var message: M?,
        var amount: ProgressContext.Amount?,
        val onCancel: (() -> Unit)?,
        val formatAmount: (ProgressContext.Amount) -> String,
        val separator: String,
    )

    /**
     * Run [block] while a progress dialog is shown.
     *
     * @param message initial message, or null for no text.
     * @param onCancel if non-null, the dialog becomes cancellable and this runs when dismissed.
     * @param formatAmount / [separator] control how [ProgressContext.Amount] is rendered.
     *   See the class KDoc for how these combine across concurrent ops.
     *
     * TODO: [formatAmount], [separator] and [onCancel]-derived cancellability are
     *  fixed for the lifetime of an op [ProgressScope.updateProgress] only mutates
     *  message/amount. If a caller needs to change those mid-flight, expose a
     *  dedicated API instead of overloading [updateProgress].
     */
    suspend fun <T> withProgress(
        message: M? = null,
        onCancel: (() -> Unit)? = null,
        formatAmount: (ProgressContext.Amount) -> String =
            { (current, max) -> "$current/$max" },
        separator: String = " ",
        block: suspend ProgressScope<M>.() -> T,
    ): T {
        val opId = nextOpId.incrementAndGet()
        synchronized(lock) {
            activeOps[opId] =
                Op(
                    message = message,
                    amount = null,
                    onCancel = onCancel,
                    formatAmount = formatAmount,
                    separator = separator,
                )
            publishLocked()
        }
        try {
            return ProgressScope(this, opId).block()
        } finally {
            synchronized(lock) {
                activeOps.remove(opId)
                publishLocked()
            }
        }
    }

    /**
     * Updates [opId] in place. The op keeps its position in [activeOps] — re-promoting
     * it would cause the displayed message to flicker between concurrent ops. Updates
     * from non-displayed ops are held internally and shown only when that op becomes
     * the displayed one (i.e. all later-started ops have ended).
     *
     * Only re-publishes if [opId] is currently the displayed op; otherwise the state
     * the UI sees is unchanged.
     */
    internal fun updateOp(
        opId: Long,
        message: M?,
        amount: ProgressContext.Amount?,
    ) {
        synchronized(lock) {
            val op = activeOps[opId] ?: return
            message?.let { op.message = it }
            op.amount = amount
            // Only the displayed op (last-started) drives the published state.
            if (activeOps.entries.last().key == opId) {
                publishLocked()
            }
        }
    }

    /** Called by the UI when the user dismisses the dialog. Fires every active `onCancel`. */
    fun requestCancel() {
        val callbacks = synchronized(lock) { activeOps.values.mapNotNull { it.onCancel } }
        callbacks.forEach { it.invoke() }
    }

    /** Must be called under [lock]. */
    private fun publishLocked() {
        progress.value =
            if (activeOps.isEmpty()) {
                ViewModelProgress.Idle
            } else {
                val latest = activeOps.values.last()
                ViewModelProgress.Active(
                    message = latest.message,
                    amount = latest.amount,
                    cancellable = activeOps.values.any { it.onCancel != null },
                    formatAmount = latest.formatAmount,
                    separator = latest.separator,
                )
            }
    }
}

/** Receiver inside [ProgressManager.withProgress] for mid-operation updates. */
class ProgressScope<M : Any> internal constructor(
    private val manager: ProgressManager<M>,
    private val opId: Long,
) {
    /** A null [message] keeps the current one. */
    fun updateProgress(
        message: M? = null,
        amount: ProgressContext.Amount? = null,
    ) {
        manager.updateOp(opId, message = message, amount = amount)
    }
}

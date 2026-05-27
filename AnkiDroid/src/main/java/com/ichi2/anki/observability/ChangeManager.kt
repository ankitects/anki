/*
 * Copyright (c) 2022 Ankitects Pty Ltd <http://apps.ankiweb.net>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

/*
 * With the Rust backend, operations that modify the collection return a description of changes (OpChanges).
 * The UI can subscribe to these changes, so it can update itself when actions have been performed
 * (eg, the deck list can check if studyQueues has been updated, and if so, it will redraw the list).
 *
 * The optional handler argument can be used so that the initiator of an action can tell when a
 * OpChanges action was caused by itself. This can be useful when the default change behaviour
 * should be ignored, in favour of specific handling (eg the UI wishes to directly update the
 * displayed flag, without redrawing the entire study screen).
 */

package com.ichi2.anki.observability

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import anki.collection.OpChanges
import anki.collection.OpChangesAfterUndo
import anki.collection.OpChangesOnly
import anki.collection.OpChangesWithCount
import anki.collection.OpChangesWithId
import anki.collection.opChanges
import anki.import_export.ImportResponse
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.utils.ext.ifNotZero
import org.jetbrains.annotations.Contract
import timber.log.Timber
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList

object ChangeManager {
    // do not make this a 'fun interface' - lambdas may immediately be GCed
    // due to the use of WeakReference
    interface Subscriber {
        /**
         * Called after a backend method invoked via col.op() or col.opWithProgress()
         * has modified the collection. Subscriber should inspect the changes, and update
         * the UI if necessary.
         *
         * @param changes see [OpChanges].
         * @param handler the initiator of the change. A subscriber may compare this against itself
         * to detect changes it caused, and skip its default handling in favour of more specific updates.
         */
        fun opExecuted(
            changes: OpChanges,
            handler: Any?,
        )
    }

    // Maybe fixes #16217 - CopyOnWriteArrayList makes this object thread-safe
    private val subscribers = CopyOnWriteArrayList(mutableListOf<WeakReference<Subscriber>>())

    val subscriberCount get() = subscribers.size

    /**
     * Subscribes to changes.
     *
     * @param subscriber The object listening for changes.
     * @param owner The lifecycle owner controlling this subscription.
     * Defaults to [subscriber] if it implements [LifecycleOwner] (e.g. Activities/Fragments).
     * If provided, subscription waits for [Lifecycle.State.CREATED] and auto-unsubscribes on destroy.
     */
    @Contract("subscriber -> call")
    fun subscribe(
        subscriber: Subscriber,
        owner: LifecycleOwner? = subscriber as? LifecycleOwner,
    ) {
        val weakRef = WeakReference(subscriber)

        if (owner == null) {
            subscribers.add(weakRef)
            return
        }

        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)) {
            subscribers.add(weakRef)
        }

        owner.lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onCreate(owner: LifecycleOwner) {
                    if (subscribers.none { it.get() === subscriber }) {
                        subscribers.add(weakRef)
                    }
                }

                override fun onDestroy(owner: LifecycleOwner) {
                    unsubscribe(subscriber)
                    owner.lifecycle.removeObserver(this)
                }
            },
        )
    }

    fun unsubscribe(subscriber: Subscriber) {
        subscribers.removeWeakReferences { it == null || it === subscriber }
    }

    private fun notifySubscribers(
        changes: OpChanges,
        handler: Any?,
    ) {
        val expired = mutableListOf<WeakReference<Subscriber>>()
        for (subscriber in subscribers) {
            val ref =
                try {
                    subscriber.get()
                } catch (e: Exception) {
                    CrashReportService.sendExceptionReport(e, "notifySubscribers", "16217: invalid subscriber")
                    null
                }
            if (ref == null) {
                expired.add(subscriber)
            } else {
                try {
                    ref.opExecuted(changes, handler)
                } catch (e: Exception) {
                    CrashReportService.sendExceptionReport(e, "notifySubscribers", "opExecuted failed")
                }
            }
        }
        expired.size.ifNotZero { size -> Timber.v("removing %d expired subscribers", size) }
        for (item in expired) {
            subscribers.remove(item)
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    fun resetForTesting() {
        subscribers.size.ifNotZero { size -> Timber.d("clearing %d subscribers", size) }
        subscribers.clear()
    }

    internal fun <T : Any> notifySubscribers(
        changes: T,
        initiator: Any?,
    ) {
        val opChanges =
            when (changes) {
                is OpChanges -> changes
                is OpChangesWithCount -> changes.changes
                is OpChangesWithId -> changes.changes
                is OpChangesAfterUndo -> changes.changes
                is OpChangesOnly -> changes.changes
                is ImportResponse -> changes.changes
                else -> TODO("unhandled change type of class '${changes::class}'")
            }
        notifySubscribers(opChanges, initiator)
    }

    fun notifySubscribersAllValuesChanged(handler: Any? = null) {
        notifySubscribers(ALL, handler)
    }

    /**
     * An OpChanges that ensures that all data should be considered as potentially changed.
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal val ALL =
        opChanges {
            card = true
            note = true
            deck = true
            tag = true
            notetype = true
            config = true
            deckConfig = true
            mtime = true
            browserTable = true
            browserSidebar = true
            noteText = true
            studyQueues = true
        }
}

private fun <T> MutableList<WeakReference<T>>.removeWeakReferences(block: (T?) -> Boolean) {
    this.removeAll { block(it.get()) }
}

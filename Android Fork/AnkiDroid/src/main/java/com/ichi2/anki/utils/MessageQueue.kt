// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.utils

import androidx.annotation.MainThread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Keeps ViewModel messages pending until the UI has finished displaying them.
 *
 * e.g. Rotating the screen can remove a snackbar immediately after it's displayed.
 *
 * A UI should display [messages] in enqueue order, then call [acknowledge] after each message is handled.
 *
 * @see [Android UI event guidance](https://developer.android.com/topic/architecture/ui-layer/events#consuming-events)
 */
class MessageQueue<T> {
    val messages: StateFlow<List<Message<T>>>
        field = MutableStateFlow<List<Message<T>>>(emptyList())

    private var nextMessageId = MessageId(0)

    @MainThread
    fun enqueue(message: T) {
        val pending = Message(id = nextMessageId++, message = message)
        messages.update { it + pending }
    }

    /** Removes only the acknowledged message. Repeated or stale acknowledgements are harmless. */
    @MainThread
    fun acknowledge(id: MessageId) {
        messages.update { messages -> messages.filterNot { it.id == id } }
    }

    /** [id] distinguishes messages with identical contents within this queue. */
    data class Message<T>(
        val id: MessageId,
        val message: T,
    )

    @JvmInline
    value class MessageId(
        val value: Int,
    ) {
        operator fun inc() = MessageId(value + 1)
    }
}

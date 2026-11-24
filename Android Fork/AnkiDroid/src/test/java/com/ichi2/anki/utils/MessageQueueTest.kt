// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.utils

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.not
import org.junit.Test

class MessageQueueTest {
    private val queue = MessageQueue<String>()

    @Test
    fun `messages remain pending when collectors restart until acknowledged`() =
        runTest {
            queue.enqueue("message")

            // Each first() starts and stops a collector, as when the UI is recreated.
            val firstCollection = queue.messages.first()
            assertThat(firstCollection.single().message, equalTo("message"))
            assertThat(queue.messages.first(), equalTo(firstCollection))

            queue.acknowledge(firstCollection.single().id)

            assertThat(queue.messages.first(), hasSize(0))
        }

    @Test
    fun `acknowledgements distinguish identical messages`() {
        queue.enqueue("message")
        queue.enqueue("message")
        val (first, second) = queue.messages.value
        assertThat(second.message, equalTo(first.message))
        assertThat(second.id, not(equalTo(first.id)))

        queue.acknowledge(first.id)
        queue.acknowledge(first.id)

        assertThat(queue.messages.value, contains(second))
    }

    @Test
    fun `acknowledgements for old IDs do not remove newer messages`() {
        queue.enqueue("message")
        val first = queue.messages.value.single()
        queue.acknowledge(first.id)
        queue.enqueue("message")
        val second = queue.messages.value.single()
        assertThat(second.id, not(equalTo(first.id)))

        queue.acknowledge(first.id)

        assertThat(queue.messages.value, contains(second))
    }

    @Test
    fun `messages queue in enqueue order without a collector`() {
        queue.enqueue("first")
        queue.enqueue("second")
        queue.enqueue("third")

        assertThat(queue.messages.value.map { it.message }, contains("first", "second", "third"))
    }

    @Test
    fun `many pending messages do not block enqueueing`() {
        repeat(100) { queue.enqueue("message") }
        queue.enqueue("last")

        val messages = queue.messages.value
        assertThat(messages, hasSize(101))
        assertThat(messages.last().message, equalTo("last"))
    }
}

// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.withCol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class JvmTestDispatcherTest : JvmTest() {
    @Test
    fun `collection operations run on the coroutine test scheduler`() =
        runTest(context = StandardTestDispatcher()) {
            val testThread = Thread.currentThread()
            var completed = false
            launch {
                withCol {
                    assertSame(testThread, Thread.currentThread())
                    completed = true
                }
            }
            assertFalse(completed)
            runCurrent()
            assertTrue(completed)
        }

    @Test
    fun `a failing coroutine test restores the collection dispatcher`() {
        val original = CollectionManager.setTestDispatcher(Dispatchers.IO)
        try {
            val failure =
                assertFailsWith<IllegalStateException> {
                    runTest { error("expected failure") }
                }
            assertEquals("expected failure", failure.message)
            assertSame(Dispatchers.IO, CollectionManager.setTestDispatcher(Dispatchers.IO))
        } finally {
            CollectionManager.setTestDispatcher(original)
        }
    }

    @Test
    fun `a leaked dispatcher fails the test and still restores the previous queue`() {
        val original = CollectionManager.setTestDispatcher(Dispatchers.IO)
        try {
            assertFailsWith<AssertionError> {
                runTest {
                    CollectionManager.setTestDispatcher(StandardTestDispatcher(testScheduler))
                }
            }
            assertSame(Dispatchers.IO, CollectionManager.setTestDispatcher(Dispatchers.IO))
        } finally {
            CollectionManager.setTestDispatcher(original)
        }
    }
}

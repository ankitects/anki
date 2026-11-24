// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.coroutines

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Tests for [recordCoroutineCaller] */
class RecordCoroutineCallerTest {
    @Test
    fun `failure records the caller while preserving the original exception`() =
        runTest {
            val failure = OperationFailure(123)
            val suppressed = IllegalArgumentException("original diagnostic")
            failure.addSuppressed(suppressed)
            val originalStack = failure.stackTrace.toList()

            val caught = assertFailsWith<OperationFailure> { requestOperation(failure) }

            assertSame(failure, caught)
            assertEquals(originalStack, caught.stackTrace.toList())
            assertEquals(2, caught.suppressed.size)
            assertSame(suppressed, caught.suppressed.first())
            val caller = caught.suppressed.last()
            assertEquals("Coroutine call site", caller.message)
            assertTrue(caller.stackTrace.any { it.methodName == "requestOperation" })
        }

    @Test
    fun `cancellation is unchanged`() =
        runTest {
            val cancellation = CancellationException("cancelled")

            val caught =
                assertFailsWith<CancellationException> {
                    recordCoroutineCaller { throw cancellation }
                }

            assertSame(cancellation, caught)
            assertTrue(caught.suppressed.isEmpty())
        }

    private suspend fun requestOperation(failure: Exception): Nothing =
        recordCoroutineCaller {
            withContext(Dispatchers.Default) { throw failure }
        }

    // This suppresses a helper from kotlinx.coroutines (it doesn't work on exceptions with state),
    // if built-in recovery won't work, then we're asserting on our code and not the framework
    @Suppress("unused")
    private class OperationFailure(
        val code: Int,
    ) : Exception("operation failed")
}

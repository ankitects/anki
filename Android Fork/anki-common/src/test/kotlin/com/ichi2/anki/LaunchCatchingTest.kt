// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import com.ichi2.anki.libanki.exception.InvalidSearchException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.nullValue
import org.junit.Test

class LaunchCatchingTest {
    @Test
    fun `launchCatching does not include class names for InvalidSearchException`() =
        runTest {
            val underlying = IllegalStateException("Invalid search: an and")
            val captured = captureErrorMessage { throw InvalidSearchException(underlying) }

            assertThat(captured, equalTo("Invalid search: an and"))
        }

    @Test
    fun `launchCatching catches a failed child coroutine`() =
        runTest {
            val captured = captureErrorMessage { launch { throw IllegalStateException("child") } }

            assertThat(captured, equalTo("java.lang.IllegalStateException: child"))
        }

    @Test
    fun `launchCatching reports a cancellation it did not cause`() =
        runTest {
            val cancelled = CompletableDeferred<Unit>().apply { cancel() }

            val captured = captureErrorMessage { cancelled.await() }

            assertThat(captured, containsString("CancellationException"))
        }

    @Test
    fun `launchCatching does not report its own cancellation`() =
        runTest {
            var captured: String? = null
            val job = launchCatching(errorMessageHandler = { captured = it }) { awaitCancellation() }
            testScheduler.advanceUntilIdle()

            job.cancel()
            job.join()

            assertThat(captured, nullValue())
        }

    private suspend fun CoroutineScope.captureErrorMessage(block: suspend CoroutineScope.() -> Unit): String? {
        var captured: String? = null
        launchCatching(
            errorMessageHandler = { captured = it },
            block = block,
        ).join()
        return captured
    }
}

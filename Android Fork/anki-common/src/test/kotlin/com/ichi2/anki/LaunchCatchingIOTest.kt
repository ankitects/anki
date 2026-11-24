// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class LaunchCatchingIOTest {
    /** The UI only collects errors while it's STARTED */
    @Test
    fun `an error is kept until the UI collects it`() =
        runTestWithViewModel { viewModel ->
            viewModel.launchCatchingIO { throw IllegalStateException("failed") }
            advanceUntilIdle()

            val error = withTimeout(1.seconds) { viewModel.onError.receive() }

            assertThat(error.message, equalTo("failed"))
        }

    @Test
    fun `a cancellation the task did not cause is shown`() =
        runTestWithViewModel { viewModel ->
            val cancelled = CompletableDeferred<Unit>().apply { cancel() }
            viewModel.launchCatchingIO { cancelled.await() }
            advanceUntilIdle()

            val error = withTimeout(1.seconds) { viewModel.onError.receive() }

            assertIs<CancellationException>(error)
        }

    @Test
    fun `a failed child coroutine is passed to the error handler`() =
        runTestWithViewModel { viewModel ->
            var message: String? = null
            viewModel.launchCatchingIO(errorMessageHandler = { message = it }) {
                launch { throw IllegalStateException("child") }
            }
            advanceUntilIdle()

            assertThat(message, equalTo("java.lang.IllegalStateException: child"))
        }

    private fun runTestWithViewModel(block: suspend TestScope.(ErrorViewModel) -> Unit) =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            Dispatchers.setMain(dispatcher)
            ioDispatcher = dispatcher
            try {
                block(ErrorViewModel())
            } finally {
                ioDispatcher = Dispatchers.IO
                Dispatchers.resetMain()
            }
        }

    private class ErrorViewModel :
        ViewModel(),
        OnErrorListener {
        override val onError = Channel<Exception>(Channel.BUFFERED)
    }
}

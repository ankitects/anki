// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.utils

import com.ichi2.anki.utils.OnlyOnce.Method.UNIT_TEST
import com.ichi2.anki.utils.OnlyOnce.preventSimultaneousExecutions
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test

/** Tests for [OnlyOnce] */
class OnlyOnceTest {
    @Test
    fun `single run works`() =
        runTest {
            var i = 0
            preventMultipleExecutions(wait = true) { i++ }
            assertThat(i, equalTo(1))
        }

    @Test
    fun `simultaneous run is blocked`() =
        runTest {
            var i = 0
            preventMultipleExecutions(wait = false) { i++ }
            preventMultipleExecutions(wait = false) { i++ }
            advanceUntilIdle()
            assertThat(i, equalTo(1))
        }

    @Test
    fun `exceptional run does not block`() =
        runTest {
            var i = 0
            preventMultipleExecutions(wait = true, shouldCatchException = true) { throw IllegalStateException() }
            preventMultipleExecutions(wait = true) { i++ }
            assertThat(i, equalTo(1))
        }

    @Test
    fun `second run is not blocked`() =
        runTest {
            var i = 0
            preventMultipleExecutions(wait = true) { i++ }
            preventMultipleExecutions(wait = true) { i++ }
            assertThat(i, equalTo(2))
        }

    @Test
    fun `cancelled run does not block`() =
        runTest {
            val job = launch { awaitCancellation() }
            preventSimultaneousExecutions(UNIT_TEST) { job }
            runCurrent()

            job.cancel()
            advanceUntilIdle()

            var i = 0
            preventMultipleExecutions(wait = true) { i++ }
            assertThat(i, equalTo(1))
        }

    // catch the exception here otherwise the test scope will catch it and throw it, safe as we expect the exception
    private fun TestScope.preventMultipleExecutions(
        shouldCatchException: Boolean = false,
        wait: Boolean,
        function: () -> Unit,
    ) {
        preventSimultaneousExecutions(UNIT_TEST) {
            launch {
                if (shouldCatchException) {
                    runCatching { function() }
                } else {
                    function()
                }
            }
        }
        if (wait) advanceUntilIdle()
    }
}

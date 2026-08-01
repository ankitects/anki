/*
 *  Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.utils

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.utils.OnlyOnce.Method.UNIT_TEST
import com.ichi2.anki.utils.OnlyOnce.preventSimultaneousExecutions
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnlyOnceTest : RobolectricTest() {
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

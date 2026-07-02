/*
 *  Copyright (c) 2024 Mike Hardy <github@mikehardy.net>
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
package com.ichi2.testutils.common

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.junit.Test
import org.junit.jupiter.api.fail

/**
 * Tests for methods in [assertThrows]
 */
class AnkiAssertTest {
    @Test
    fun failsWithIncorrectException() {
        val assertionError =
            captureAssertionFailure {
                assertThrows<IllegalArgumentException>("IllegalArgumentException is not found") {
                    throw NullPointerException("wrong exception type")
                }
            }
        assertThat(assertionError.message, containsString("unexpected exception type thrown"))
    }

    @Test
    fun failsWithNoException() {
        val assertionError =
            captureAssertionFailure {
                assertThrows<IllegalArgumentException>("No exception is found") {
                    // assertThrows should fail because there is no throws in this block
                }
            }
        assertThat(assertionError.message, containsString("nothing was thrown"))
    }

    /**
     * Asserts that the provided block throws an [AssertionError]
     * @param throwErrorBlock code which should throw an [AssertionError]
     * @return the [AssertionError] thrown by [throwErrorBlock]
     */
    private fun captureAssertionFailure(throwErrorBlock: () -> Unit): AssertionError {
        try {
            throwErrorBlock()
        } catch (e: AssertionError) {
            return e
        }

        // this statement may not be inside the try ... as it would be caught by the 'catch'
        fail("An AssertionError should have been thrown")
    }
}

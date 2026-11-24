// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 Mike Hardy <github@mikehardy.net>

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

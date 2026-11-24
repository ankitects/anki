// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

/**
 * An exception to be thrown by tests
 * Ensures that an actual exception is not mistaken for an exception we want to catch
 */
class TestException(
    message: String,
) : RuntimeException(message)

fun testExceptionWithStackTrace(message: String): TestException {
    try {
        throw TestException(message)
    } catch (e: TestException) {
        return e
    }
}

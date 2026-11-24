// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils.common

import org.junit.function.ThrowingRunnable

/**
 * Asserts that [runnable] throws an exception of type [T] when
 * executed. If it does, the exception object is returned. If it does not throw an exception, an
 * [AssertionError] is thrown. If it throws the wrong type of exception, an
 * [AssertionError] is thrown describing the mismatch; the exception that was actually thrown can
 * be obtained by calling `AssertionError.cause`.
 *
 * @param message the identifying message for the [AssertionError]
 * @param T the expected type of the exception
 * @param runnable a function that is expected to throw an exception when executed
 * @return the exception thrown by [runnable]
 */
inline fun <reified T : Throwable> assertThrows(
    message: String? = null,
    runnable: ThrowingRunnable,
): T = org.junit.Assert.assertThrows(message, T::class.java, runnable)

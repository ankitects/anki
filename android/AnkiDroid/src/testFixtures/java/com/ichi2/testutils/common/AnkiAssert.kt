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

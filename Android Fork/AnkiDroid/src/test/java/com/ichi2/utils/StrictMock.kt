// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.utils

import org.mockito.Mockito
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer

/** returns a mock which will throw on all invocations which are not explicitly provided */
inline fun <reified T> strictMock(): T = Mockito.mock(T::class.java, ThrowingAnswer())

class StrictMock {
    companion object {
        fun <T> strictMock(clazz: Class<T>): T = Mockito.mock(clazz, ThrowingAnswer())
    }
}

// Likely a better way. Using: https://stackoverflow.com/a/36206766

/** Answer for use in a strict mock */
class ThrowingAnswer : Answer<Any?> {
    override fun answer(invocation: InvocationOnMock): Any = throw AssertionError("Unexpected invocation: $invocation")
}

/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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

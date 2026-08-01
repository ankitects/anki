/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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

import com.ichi2.utils.ExceptionUtil.getExceptionMessage
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.IsEqual.equalTo
import org.junit.Test

class ExceptionUtilTest {
    @Test
    fun exceptionMessageSingle() {
        val e = Exception("Hello")

        val message = getExceptionMessage(e)

        assertThat(message, equalTo("Hello"))
    }

    @Test
    fun exceptionMessageNested() {
        val inner = Exception("Inner")
        val e = Exception("Hello", inner)

        val message = getExceptionMessage(e)

        assertThat(message, equalTo("Hello\nInner"))
    }

    @Test
    fun exceptionMessageNull() {
        val message = getExceptionMessage(null)

        assertThat(message, equalTo(""))
    }

    @Test
    fun exceptionMessageNestedNull() {
        // a single null should be displayed, a nested null shouldn't be
        val inner = Exception()
        val e = Exception("Hello", inner)

        val message = getExceptionMessage(e)

        assertThat(message, equalTo("Hello"))
    }
}

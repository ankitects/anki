// SPDX-License-Identifier: GPL-3.0-or-later

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

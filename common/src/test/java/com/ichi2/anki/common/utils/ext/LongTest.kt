// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.utils.ext

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class LongTest {
    @Test
    fun `ifZero - zero`() {
        val result = 0L.ifZero { 2 }

        assertThat(result, equalTo(2L))
    }

    @Test
    fun `ifZero - nonzero`() {
        val result = 1L.ifZero { 2 }
        assertThat(result, equalTo(1L))
    }
}

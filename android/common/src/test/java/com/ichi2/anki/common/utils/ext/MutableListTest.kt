// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.utils.ext

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.empty
import org.junit.Test

class MutableListTest {
    @Test
    fun `replaceWith replaces contents`() {
        val list = mutableListOf(1, 2, 3)
        list.replaceWith(listOf(4, 5))

        assertThat(list, contains(4, 5))
    }

    @Test
    fun `replaceWith works on empty list`() {
        val list = mutableListOf<String>()
        list.replaceWith(listOf("a", "b"))

        assertThat(list, contains("a", "b"))
    }

    @Test
    fun `replaceWith works with empty input`() {
        val list = mutableListOf(1, 2, 3)
        list.replaceWith(emptyList())

        assertThat(list, empty())
    }
}

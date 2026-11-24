// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Eric Li <ericli3690@gmail.com>

package com.ichi2.anki.common.utils.ext

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test

class ByteArrayTest {
    @Test
    fun `finds a newline at the starting index`() {
        assertThat(indexOfNewlineAtOrAfter("ab\ncd", 2), equalTo(2))
    }

    @Test
    fun `finds the first newline after the starting index`() {
        assertThat(indexOfNewlineAtOrAfter("a\nbc\nd", 2), equalTo(4))
    }

    @Test
    fun `ignores newlines before the starting index`() {
        assertThat(indexOfNewlineAtOrAfter("a\nbcd", 2), equalTo(null))
    }

    @Test
    fun `returns null when there is no newline`() {
        assertThat(indexOfNewlineAtOrAfter("abcd", 0), equalTo(null))
    }

    @Test
    fun `returns null for empty input`() {
        assertThat(indexOfNewlineAtOrAfter("", 0), equalTo(null))
    }

    @Test
    fun `clamps a negative starting index to the start`() {
        assertThat(indexOfNewlineAtOrAfter("ab\ncd", -100), equalTo(2))
    }

    @Test
    fun `returns null for a starting index beyond the end`() {
        assertThat(indexOfNewlineAtOrAfter("ab\n", 99), equalTo(null))
    }

    private fun indexOfNewlineAtOrAfter(
        text: String,
        fromIndex: Int,
    ) = text.toByteArray().indexOfNewlineAtOrAfter(fromIndex)
}

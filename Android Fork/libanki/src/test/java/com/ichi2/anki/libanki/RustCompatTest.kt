// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.libanki

import com.ichi2.anki.libanki.utils.RustCompat
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RustCompatTest {
    @Test
    fun `whitespace includes next line and non-breaking spaces`() {
        for (char in listOf(' ', '\t', '\n', '\u0085', '\u00a0', '\u2003', '\u202f', '\u3000')) {
            assertTrue(RustCompat.isWhitespace(char), "U+${char.code.toString(16)}")
        }
    }

    @Test
    fun `whitespace excludes control separators and zero width spaces`() {
        for (char in listOf('a', '\u0000', '\u001c', '\u001d', '\u001e', '\u001f', '\u200b', '\ufeff')) {
            assertFalse(RustCompat.isWhitespace(char), "U+${char.code.toString(16)}")
        }
    }
}

// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.export

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test

class FilenameTest {
    @Test
    fun `a valid name is unchanged`() {
        assertThat(Filename.sanitize("Japanese Vocab").value, equalTo("Japanese Vocab"))
    }

    @Test
    fun `each invalid character is replaced`() {
        assertThat(
            Filename.sanitize("""a\b/c?d<e>f:g*h|i"j^k""").value,
            equalTo("a_b_c_d_e_f_g_h_i_j_k"),
        )
    }

    @Test
    fun `a colon in a deck name is replaced`() {
        assertThat(Filename.sanitize("Ratio 1:2").value, equalTo("Ratio 1_2"))
    }

    @Test
    fun `the subdeck separator is replaced`() {
        assertThat(Filename.sanitize("Japanese::Kanji").value, equalTo("Japanese__Kanji"))
    }
}

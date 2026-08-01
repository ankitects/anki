// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test

class MathJaxFormatTest {
    @Test
    fun `block wraps text in display-math delimiters`() {
        assertThat(
            MathJaxFormat.BLOCK
                .toTextWrapper()
                .format("E=mc^2")
                .result,
            equalTo("""\[E=mc^2\]"""),
        )
    }

    @Test
    fun `chemistry wraps text in mhchem ce delimiters`() {
        assertThat(
            MathJaxFormat.CHEMISTRY
                .toTextWrapper()
                .format("H2O")
                .result,
            equalTo("""\( \ce{H2O} \)"""),
        )
    }
}

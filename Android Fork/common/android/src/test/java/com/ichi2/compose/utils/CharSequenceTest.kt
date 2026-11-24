// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.utils

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class CharSequenceTest {
    @Test
    fun `plain CharSequence becomes annotated text`() {
        val text: CharSequence = StringBuilder("Validation failed")

        assertEquals(AnnotatedString("Validation failed"), text.toAnnotatedString())
    }

    @Test
    fun `annotated text retains its instance and annotations`() {
        val text: CharSequence =
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append("Validation failed")
                }
                addStringAnnotation("help", "validation", 0, length)
            }

        assertSame(text, text.toAnnotatedString())
    }
}

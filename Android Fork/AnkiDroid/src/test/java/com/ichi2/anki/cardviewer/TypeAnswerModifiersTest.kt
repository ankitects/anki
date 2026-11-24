// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.cardviewer

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class TypeAnswerModifiersTest {
    @Test
    fun `parses bare field`() {
        val parsed = TypeAnswerModifiers.parse("Back")
        assertThat(parsed, equalTo(TypeAnswerModifiers("Back", combining = true, cloze = false, noSuggest = false)))
    }

    @Test
    fun `parses nc only`() {
        val parsed = TypeAnswerModifiers.parse("nc:Back")
        assertThat(parsed, equalTo(TypeAnswerModifiers("Back", combining = false, cloze = false, noSuggest = false)))
    }

    @Test
    fun `parses cloze only`() {
        val parsed = TypeAnswerModifiers.parse("cloze:Text")
        assertThat(parsed, equalTo(TypeAnswerModifiers("Text", combining = true, cloze = true, noSuggest = false)))
    }

    @Test
    fun `parses nosuggest only`() {
        val parsed = TypeAnswerModifiers.parse("nosuggest:Back")
        assertThat(parsed, equalTo(TypeAnswerModifiers("Back", combining = true, cloze = false, noSuggest = true)))
    }

    @Test
    fun `parses nosuggest with nc`() {
        val parsed = TypeAnswerModifiers.parse("nosuggest:nc:Back")
        assertThat(parsed, equalTo(TypeAnswerModifiers("Back", combining = false, cloze = false, noSuggest = true)))
    }

    @Test
    fun `parses nosuggest with cloze`() {
        val parsed = TypeAnswerModifiers.parse("nosuggest:cloze:Text")
        assertThat(parsed, equalTo(TypeAnswerModifiers("Text", combining = true, cloze = true, noSuggest = true)))
    }

    /** Modifiers may be prepended by filters in any order — the parser should tolerate that. */
    @Test
    fun `parses modifiers regardless of order`() {
        val ncFirst = TypeAnswerModifiers.parse("nosuggest:nc:Back")
        val nosuggestSecond = TypeAnswerModifiers.parse("nc:nosuggest:Back")
        assertThat(ncFirst.fieldName, equalTo("Back"))
        assertThat(nosuggestSecond.fieldName, equalTo("Back"))
        assertThat(ncFirst.combining, equalTo(false))
        assertThat(nosuggestSecond.combining, equalTo(false))
        assertThat(ncFirst.noSuggest, equalTo(true))
        assertThat(nosuggestSecond.noSuggest, equalTo(true))
    }

    @Test
    fun `parses chained modifiers`() {
        val parsed = TypeAnswerModifiers.parse("cloze:nc:Text")
        assertThat(parsed, equalTo(TypeAnswerModifiers("Text", combining = false, cloze = true, noSuggest = false)))
    }

    @Test
    fun `parses chained modifiers in either order`() {
        val parsed = TypeAnswerModifiers.parse("nc:cloze:Text")
        assertThat(parsed, equalTo(TypeAnswerModifiers("Text", combining = false, cloze = true, noSuggest = false)))
    }
}

// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2018 Mike Hardy <mike@mikehardy.net>

package com.ichi2.anki.backend

import org.junit.Assert.assertEquals
import org.junit.Test

class HtmlUtilsTest {
    @Test
    fun test_stripHTML_will_remove_tags() {
        val strings =
            listOf(
                "<>",
                "<1>",
                "<foo>",
                "<\n>",
                "<\\qwq>",
                "<aa\nsd\nas\n?\n>",
            )
        for (s in strings) {
            assertEquals(
                s.replace("\n", "\\n") + " should be removed.",
                "",
                stripHTML(s),
            )
        }
    }

    @Test
    fun test_stripHTML_will_remove_comments() {
        val strings =
            listOf(
                "<!---->",
                "<!--dd-->",
                "<!--asd asd asd-->",
                "<!--\n-->",
                "<!--\nsd-->",
                "<!--lkl\nklk\n-->",
            )
        for (s in strings) {
            assertEquals(
                s.replace("\n", "\\n") + " should be removed.",
                "",
                stripHTML(s),
            )
        }
    }

    @Test
    fun test_stripSpecialFields_will_remove_type() {
        val input = "test\n\n[[type:Back]]"
        val output = stripSpecialFields(input)
        assertEquals(
            "type field should be removed",
            "test\n\n",
            output,
        )
    }

    @Test
    fun test_stripSpecialFields_will_remove_avRef() {
        val input = "test\n\n[anki:play:q:0]"
        val output = stripSpecialFields(input)
        assertEquals(
            "avRef field should be removed",
            "test\n\n",
            output,
        )
    }
}

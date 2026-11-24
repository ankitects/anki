// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.StudyOptionsFragment.Companion.formatDescription
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for [StudyOptionsFragment.formatDescription].
 */
@RunWith(AndroidJUnit4::class) // required for String -> Spannable conversion in formatDescription
class FormatDeckDescriptionTest {
    // Fixes for #5715: In deck description, ignore what is in style and script tag
    @Test
    fun spanTagsAreNotRemoved() {
        val result = formatDescription("""a<span style="color:red">a=1</span>a""")
        assertEquals("aa=1a", result.toString()) // Note: This is coloured red on the screen
    }

    @Test
    fun scriptTagContentsAreRemoved() {
        val result = formatDescription("a<script>a=1</script>a")
        assertEquals("aa", result.toString())
    }

    @Test
    fun upperCaseScriptTagContentsAreRemoved() {
        val result = formatDescription("a<SCRIPT>a=1</script>a")
        assertEquals("aa", result.toString())
    }

    @Test
    fun scriptTagWithAttributesContentsAreRemoved() {
        val result = formatDescription("""a<script type="application/javascript">a=1</script>a""")
        assertEquals("aa", result.toString())
    }

    @Test
    fun styleTagContentsAreRemoved() {
        val result = formatDescription("a<style>a=1</style>a")
        assertEquals("aa", result.toString())
    }

    @Test
    fun upperCaseStyleTagContentsAreRemoved() {
        val result = formatDescription("a<STYLE>a:1</style>a")
        assertEquals("aa", result.toString())
    }

    @Test
    fun styleTagWithAttributesContentsAreRemoved() {
        val result = formatDescription("""a<style type="text/css">a:1</style>a""")
        assertEquals("aa", result.toString())
    }

    // Begin #5188 - newlines weren't displayed
    @Test // This was originally correct
    fun brIsDisplayedAsNewline() {
        val result = formatDescription("a<br/>a")
        assertEquals("a\na", result.toString())
    }

    @Test
    fun windowsNewlinesAreNewlines() {
        val result = formatDescription("a\r\na")
        assertEquals("a\na", result.toString())
    }

    @Test
    fun unixNewlinesAreNewlines() {
        val result = formatDescription("a\na")
        assertEquals("a\na", result.toString())
    }
    // end #5188
}

/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
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

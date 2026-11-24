// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.tags

import com.ichi2.testutils.JvmTest
import com.ichi2.testutils.rules.IcuCaseFoldingRule
import com.ichi2.testutils.withViewModel
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TagNameTest : JvmTest() {
    @get:Rule
    val caseFolding = IcuCaseFoldingRule()

    @Test
    fun `equivalent names persist collapse changes using the tree spelling`() =
        runTest {
            addBasicNote().update { addTag("café::child") }
            withViewModel(ManageTagsViewModel()) {
                toggleCollapsed(TagName.asValid("cafe\u0301")).join()
                assertTrue("café::child" in (state.value as ManageTagsState.Content).visibleTagNames)
                refreshTags().join()
                assertTrue("café::child" in (state.value as ManageTagsState.Content).visibleTagNames)
            }
        }

    @Test
    fun `identity agrees with the backend tag registry`() {
        val pairs =
            listOf(
                "Science" to "SCIENCE",
                "Straße" to "STRASSE",
                "σ" to "ς",
                "ﬁ" to "fi",
                "ﬃ" to "iff",
                "ﬃ" to "ffi",
                "ﬄ" to "lff",
                "ﬄ" to "ffl",
                "i" to "ı",
                "i" to "İ",
                "café" to "cafe\u0301",
                "\uD801\uDC00" to "\uD801\uDC28",
            )
        for ((left, right) in pairs) {
            val note =
                addBasicNote().update {
                    addTag(left)
                    addTag(right)
                }
            val backendEqual = col.getNote(note.id).tags.size == 1
            val leftTag = TagName.asValid(left)
            val rightTag = TagName.asValid(right)
            assertEquals(backendEqual, leftTag == rightTag, "$left / $right")
            assertEquals(if (backendEqual) 1 else 2, setOf(leftTag, rightTag).size, "$left / $right")
        }
    }

    @Test
    fun `identity preserves spelling for display and case-only renames`() {
        val lower = TagName.asValid("science::Straße")
        val upper = TagName.asValid("SCIENCE::STRASSE")
        assertEquals(lower, upper)
        assertEquals(lower.hashCode(), upper.hashCode())
        assertNotEquals(lower.value, upper.value)
        assertEquals("science::Straße", lower.value)
        assertEquals(lower.value, lower.toString())
        assertEquals("cafe\u0301", TagName.asValid("cafe\u0301").value)
    }

    @Test
    fun `ancestry uses identity and complete components`() {
        val root = TagName.asValid("Straße::café")
        assertTrue(TagName.asValid("STRASSE::cafe\u0301").isSameOrDescendantOf(root))
        assertTrue(TagName.asValid("STRASSE::cafe\u0301::child").isSameOrDescendantOf(root))
        assertFalse(TagName.asValid("Straße::caféteria").isSameOrDescendantOf(root))
        assertFalse(root.isSameOrDescendantOf(TagName.asValid("Straße::café::child")))
        assertFalse(TagName.asValid("ı::child").isSameOrDescendantOf(TagName.asValid("i")))
    }

    @Test
    fun `normalization matches the backend rename operation`() {
        for (input in listOf("cafe\u0301", "a\tb", "a\u007fb", "::a::", "\u0085a\u00a0::\u2003b\u2003")) {
            val note = addBasicNote().update { addTag("source") }
            col.tags.rename("source", input)
            assertEquals(TagName.asValid(col.getNote(note.id).tags.single()), TagName.asValid(input), input)
        }
        // Preserve the tag manager's existing UI policy for ASCII spaces.
        assertEquals("two_words", TagName.asValid("two words").value)
        assertEquals(null, TagName.build(" \t"))
    }

    @Test
    fun `three character folds preserve backend expansion order`() {
        assertEquals(TagName.asValid("iff"), TagName.asValid("ﬃ"))
        assertEquals(TagName.asValid("lff"), TagName.asValid("ﬄ"))
        assertNotEquals(TagName.asValid("ffi"), TagName.asValid("ﬃ"))
        assertNotEquals(TagName.asValid("ffl"), TagName.asValid("ﬄ"))
        assertEquals(TagName.asValid("prefix::iff::suffix"), TagName.asValid("prefix::ﬃ::suffix"))
        assertEquals(TagName.asValid("iffiff"), TagName.asValid("ﬃﬃ"))
    }
}

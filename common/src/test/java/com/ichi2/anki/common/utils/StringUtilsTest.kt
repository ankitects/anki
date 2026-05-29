// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.utils

import com.ichi2.anki.common.utils.StringUtils.toTitleCase
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.nullValue
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.test.assertFailsWith

/** Test of [toTitleCase] */
class StringUtilsTest {
    @Test
    fun toTitleCase_null_is_null() {
        assertThat(toTitleCase(null), nullValue())
    }

    @Test
    fun toTitleCase_single_letter() {
        assertThat(toTitleCase("a"), equalTo("A"))
    }

    @Test
    fun toTitleCase_single_upper_letter() {
        assertThat(toTitleCase("A"), equalTo("A"))
    }

    @Test
    fun toTitleCase_string() {
        assertThat(toTitleCase("aB"), equalTo("Ab"))
    }

    @Test
    fun toTitleCase_empty_string() {
        assertThat(toTitleCase(""), equalTo(""))
    }

    @Test
    fun toTitleCase_blank_string() {
        assertThat(toTitleCase("   "), equalTo("   "))
    }

    @Test
    fun toTitleCase_all_caps() {
        assertThat(toTitleCase("HELLO"), equalTo("Hello"))
    }

    @Test
    fun toTitleCase_multi_word_as_single_entity() {
        assertThat(toTitleCase("HELLO WORLD"), equalTo("Hello world"))
        assertThat(toTitleCase("hello world"), equalTo("Hello world"))
    }

    @Test
    fun trimToLength_under_max() {
        assertThat("hello".trimToLength(10), equalTo("hello"))
    }

    @Test
    fun trimToLength_over_max() {
        assertThat("hello".trimToLength(3), equalTo("hel"))
    }

    @Test
    fun trimToLength_exact_match() {
        assertThat("hello".trimToLength(5), equalTo("hello"))
    }

    @Test
    fun trimToLength_zero() {
        assertThat("hello".trimToLength(0), equalTo(""))
    }

    @Test
    fun lastIndexOfOrNull_not_found() {
        assertNull("hello".lastIndexOfOrNull('z'))
        assertNull("".lastIndexOfOrNull('a'))
    }

    @Test
    fun lastIndexOfOrNull_found_at_start() {
        assertThat("hello".lastIndexOfOrNull('h'), equalTo(0))
    }

    @Test
    fun lastIndexOfOrNull_found_at_end() {
        assertThat("hello".lastIndexOfOrNull('o'), equalTo(4))
    }

    @Test
    fun lastIndexOfOrNull_multiple_occurrences() {
        assertThat("banana".lastIndexOfOrNull('a'), equalTo(5))
        assertThat("aaa".lastIndexOfOrNull('a'), equalTo(2))
    }

    @Test
    fun indexOfOrNull_not_found() {
        assertNull("hello".indexOfOrNull('z'))
        assertNull("".indexOfOrNull('a'))
    }

    @Test
    fun indexOfOrNull_found_at_start() {
        assertThat("hello".indexOfOrNull('h'), equalTo(0))
    }

    @Test
    fun indexOfOrNull_found_at_end() {
        assertThat("hello".indexOfOrNull('o'), equalTo(4))
    }

    @Test
    fun indexOfOrNull_multiple_occurrences() {
        assertThat("banana".indexOfOrNull('a'), equalTo(1))
        assertThat("aaa".indexOfOrNull('a'), equalTo(0))
    }

    @Test
    fun emptyStringMutableList_correct_size() {
        val list = emptyStringMutableList(5)
        assertThat(list.size, equalTo(5))
        assertTrue(list.all { it == "" })
    }

    @Test
    fun emptyStringMutableList_is_mutable() {
        val list = emptyStringMutableList(3)
        list[0] = "modified"
        assertThat(list[0], equalTo("modified"))
    }

    @Test
    fun emptyStringMutableList_zero_size() {
        val list = emptyStringMutableList(0)
        assertThat(list.size, equalTo(0))
    }

    @Test
    fun emptyStringArray_correct_size() {
        val array = emptyStringArray(5)
        assertThat(array.size, equalTo(5))
        assertTrue(array.all { it == "" })
    }

    @Test
    fun emptyStringArray_zero_size() {
        val array = emptyStringArray(0)
        assertThat(array.size, equalTo(0))
    }

    @Test
    fun htmlEncode_less_than() {
        assertThat("<".htmlEncode(), equalTo("&lt;"))
        assertThat("a<b".htmlEncode(), equalTo("a&lt;b"))
    }

    @Test
    fun htmlEncode_greater_than() {
        assertThat(">".htmlEncode(), equalTo("&gt;"))
        assertThat("a>b".htmlEncode(), equalTo("a&gt;b"))
    }

    @Test
    fun htmlEncode_ampersand() {
        assertThat("&".htmlEncode(), equalTo("&amp;"))
        assertThat("a&b".htmlEncode(), equalTo("a&amp;b"))
    }

    @Test
    fun htmlEncode_apostrophe_numeric_entity() {
        assertThat("'".htmlEncode(), equalTo("&#39;"))
        assertThat("don't".htmlEncode(), equalTo("don&#39;t"))
    }

    @Test
    fun htmlEncode_double_quote() {
        assertThat("\"".htmlEncode(), equalTo("&quot;"))
        assertThat("\"hello\"".htmlEncode(), equalTo("&quot;hello&quot;"))
    }

    @Test
    fun htmlEncode_all_special_chars() {
        assertThat("<>&'\"".htmlEncode(), equalTo("&lt;&gt;&amp;&#39;&quot;"))
    }

    @Test
    fun htmlEncode_normal_text_unchanged() {
        assertThat("Hello World 123!".htmlEncode(), equalTo("Hello World 123!"))
    }

    @Test
    fun htmlEncode_empty_string() {
        assertThat("".htmlEncode(), equalTo(""))
    }

    @Test
    fun htmlEncode_complex_html_string() {
        val input = "<script>alert('test & \"hack\"')</script>"
        val expected = "&lt;script&gt;alert(&#39;test &amp; &quot;hack&quot;&#39;)&lt;/script&gt;"
        assertThat(input.htmlEncode(), equalTo(expected))
    }

    @Test
    fun htmlEncode_no_double_encoding() {
        val result = "&<>".htmlEncode()
        assertThat(result, equalTo("&amp;&lt;&gt;"))
        assertFalse(result.contains("&amp;lt;"))
    }

    @Test
    fun ellipsize_input_greater_than_threshold() {
        val expected = "Hello1"
        assertThat(expected.ellipsize(ellipsizeAfter = 5), equalTo("Hello…"))
    }

    @Test
    fun ellipsize_input_equal_to_threshold() {
        val expected = "Hello"
        assertThat(expected.ellipsize(ellipsizeAfter = 5), equalTo("Hello"))
    }

    @Test
    fun ellipsize_input_less_than_threshold() {
        val expected = "Hi"
        assertThat(expected.ellipsize(ellipsizeAfter = 5), equalTo("Hi"))
    }

    @Test
    fun ellipsize_blank_input() {
        assertThat("".ellipsize(ellipsizeAfter = 1), equalTo(""))
    }

    @Test
    fun ellipsize_input_invalid_threshold() {
        assertFailsWith<IllegalArgumentException> { "hello".ellipsize(0) }
        assertFailsWith<IllegalArgumentException> { "hello".ellipsize(-1) }
    }

    @Test
    fun ellipsize_emoji_is_not_split() {
        val input = "Brazil\uD83C\uDDE7\uD83C\uDDF7"
        assertThat(input.ellipsize(6), equalTo("Brazil…"))
        assertThat(input.ellipsize(7), equalTo("Brazil…"))
        assertThat(input.ellipsize(8), equalTo("Brazil…"))
        assertThat(input.ellipsize(9), equalTo("Brazil…"))
        assertThat(input.ellipsize(10), equalTo("Brazil\uD83C\uDDE7\uD83C\uDDF7"))
        assertThat(input + " ".ellipsize(11), equalTo("Brazil\uD83C\uDDE7\uD83C\uDDF7 "))
    }
}

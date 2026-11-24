// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import org.hamcrest.BaseMatcher
import org.hamcrest.Description
import org.hamcrest.Matcher

class DistinctMatcher<T> : BaseMatcher<T>() where T : Iterable<Any> {
    private lateinit var invalid: Map<Any, Int>

    override fun describeTo(description: Description) {
        description.appendText("distinct values")
    }

    override fun describeMismatch(
        item: Any?,
        description: Description,
    ) {
        if (invalid.size == 1) {
            description.appendText("found duplicate: ").appendValue(invalid.keys.joinToString(", "))
        } else {
            description.appendText("found duplicates: ").appendValue(invalid.keys.joinToString(", "))
        }
    }

    override fun matches(arg0: Any): Boolean {
        @Suppress("UNCHECKED_CAST")
        val t = arg0 as T
        invalid = t.groupingBy { it }.eachCount().filter { it.value > 1 }
        return !invalid.any()
    }
}

@Suppress("unused")
fun <T> isDistinct(): Matcher<in T> where T : Iterable<Any> = DistinctMatcher<T>()

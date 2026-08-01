/*
 *  Copyright (c) 2023 David Allison <davidallisongithub@gmail.com>
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

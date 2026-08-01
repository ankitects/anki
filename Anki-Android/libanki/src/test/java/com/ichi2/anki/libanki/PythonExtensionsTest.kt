/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.libanki

import com.ichi2.anki.common.utils.ext.jsonObjectIterable
import com.ichi2.anki.libanki.utils.insert
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.collection.IsIterableContainingInOrder.contains
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test

class PythonExtensionsTest {
    @Test
    fun test_insert_end() {
        val initial = JSONObject(mapOf("a" to "b"))
        val added = JSONObject(mapOf("b" to "c"))

        val arr = JSONArray(listOf(initial))
        arr.insert(1, added)

        assertThat(arr.length(), equalTo(2))
        assertThat(arr.getJSONObject(0), equalTo(initial))
        assertThat(arr.getJSONObject(1), equalTo(added))
    }

    @Test
    fun test_insert_moves_along() {
        val initial = JSONObject(mapOf("a" to "b"))
        val added = JSONObject(mapOf("b" to "c"))

        val arr = JSONArray(listOf(initial))
        arr.insert(0, added)

        assertThat(arr.length(), equalTo(2))
        assertThat(arr.getJSONObject(0), equalTo(added))
        assertThat(arr.getJSONObject(1), equalTo(initial))
    }

    @Test
    fun test_insert_after_end() {
        // insert with index >= len(list) inserts the value at the end of list.
        val initial = JSONObject(mapOf("a" to "b"))
        val added = JSONObject(mapOf("b" to "c"))

        val arr = JSONArray(listOf(initial))
        arr.insert(2, added)

        assertThat(arr.length(), equalTo(2))
        assertThat(arr.getJSONObject(0), equalTo(initial))
        assertThat(arr.getJSONObject(1), equalTo(added))
    }

    @Test
    fun middle_test() {
        val initial = arrayOf("a", "e", "i", "u").map { x -> JSONObject(mapOf(x to x)) }
        val arr = JSONArray(initial)
        arr.insert(3, JSONObject(mapOf("o" to "o")))

        assertThat(arr.jsonObjectIterable().flatMap { x -> listOf(x.keys()).map { xx -> xx.next() } }, contains("a", "e", "i", "o", "u"))
    }
}

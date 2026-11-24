/*
 *  Copyright (c) 2020 Arthur Milchior <arthur@milchior.fr>
 *
 *  This file is free software: you may copy, redistribute and/or modify it
 *  under the terms of the GNU General Public License as published by the
 *  Free Software Foundation, either version 3 of the License, or (at your
 *  option) any later version.
 *
 *  This file is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *  This file incorporates work covered by the following copyright and
 *  permission notice:
 *
 *    Copyright (c) 2002 JSON.org
 *
 *    Permission is hereby granted, free of charge, to any person obtaining a copy
 *    of this software and associated documentation files (the "Software"), to deal
 *    in the Software without restriction, including without limitation the rights
 *    to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *    copies of the Software, and to permit persons to whom the Software is
 *    furnished to do so, subject to the following conditions:
 *
 *    The above copyright notice and this permission notice shall be included in all
 *    copies or substantial portions of the Software.
 *
 *    The Software shall be used for Good, not Evil.
 *
 *    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *    IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *    FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *    AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *    LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *    OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *    SOFTWARE.
 */
package com.ichi2.anki.common.utils.ext

import com.ichi2.anki.common.utils.annotation.KotlinCleanup
import org.json.JSONArray
import org.json.JSONObject

fun JSONArray.deepClone(): JSONArray {
    val clone = JSONArray()
    for (i in 0 until length()) {
        clone.put(
            when (get(i)) {
                is JSONObject -> getJSONObject(i).deepClone()
                is JSONArray -> getJSONArray(i).deepClone()
                else -> get(i)
            },
        )
    }
    return clone
}

fun JSONArray.jsonObjectIterable(): Iterable<JSONObject> = Iterable { jsonObjectIterator() }

@KotlinCleanup("see if jsonObject/string/longIterator() methods can be combined into one")
fun JSONArray.jsonObjectIterator(): Iterator<JSONObject> =
    object : Iterator<JSONObject> {
        private var index = 0

        override fun hasNext(): Boolean = index < length()

        override fun next() =
            getJSONObject(index).also {
                index++
            }
    }

fun JSONArray.stringIterable(): Iterable<String> = Iterable { stringIterator() }

fun JSONArray.stringIterator(): Iterator<String> {
    return object : Iterator<String> {
        private var index = 0

        override fun hasNext(): Boolean = index < length()

        override fun next(): String {
            val string = getString(index)
            index++
            return string
        }
    }
}

/**
 * @return Given an array of objects, return the array of the value with `key`, assuming that they are String.
 * E.g. templates, fields are a JSONArray whose objects have name
 */
fun JSONArray.toStringList(key: String): List<String> = jsonObjectIterable().map { it.getString(key) }

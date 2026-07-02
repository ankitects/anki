/*
*  Copyright (c) 2020 Arthur Milchior <arthur@milchior.fr>
 * Copyright (c) 2024 Ashish Yadav <mailtoashish693@gmail.com>
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

import androidx.annotation.CheckResult
import androidx.annotation.VisibleForTesting
import org.json.JSONArray
import org.json.JSONObject

/*
Each method similar to the methods in JSONObjects. Name changed to add a ,
and it throws JSONException instead of JSONException.
Furthermore, it returns JSONObject and JSONArray

*/

@CheckResult
fun JSONObject.deepClone(): JSONObject = deepClonedInto(JSONObject())

/** deep clone this into clone.
 *
 * Given a subtype [T] of JSONObject, and a JSONObject [clone], we could do
 * ```
 * T t = new T();
 * clone.deepClonedInto(t);
 * ```
 * in order to obtain a deep clone of [clone] of type [T].  */
@VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
fun <T : JSONObject> JSONObject.deepClonedInto(clone: T): T {
    for (key in this.keys()) {
        val value =
            when (get(key)) {
                is JSONObject -> getJSONObject(key).deepClone()
                is JSONArray -> getJSONArray(key).deepClone()
                else -> get(key)
            }
        clone.put(key, value)
    }
    return clone
}

/**
 * Change type from JSONObject to JSONObject.
 *
 * Assuming the whole code use only JSONObject, JSONArray and JSONTokener,
 * there should be no instance of JSONObject or JSONArray which is not a JSONObject or JSONArray.
 *
 * In theory, it would be easy to create a JSONObject similar to a JSONObject. It would suffices to iterate over key and add them here. But this would create two distinct objects, and update here would not be reflected in the initial object. So we must avoid this method.
 * Since the actual map in JSONObject is private, the child class can't edit it directly and can't access it. Which means that there is no easy way to create a JSONObject with the same underlying map. Unless the JSONObject was saved in a variable here. Which would entirely defeat the purpose of inheritance.
 *
 * @param obj A json object
 * @return Exactly the same object, with a different type.
 */

fun fromMap(map: Map<String, Any>) =
    JSONObject().apply {
        map.forEach { (k, v) -> put(k, v) }
    }

/**
 * @return `null` if:
 * * The key does not exist
 * * The value is [null][JSONObject.NULL]
 * * ⚠️ JVM only. The value is not a string (`{ }` etc...)
 * Otherwise, returns the value mapped by [key]. In Android, that means the potentially non-string
 * value is converted to string first.
 */
fun JSONObject.getStringOrNull(key: String): String? {
    if (!has(key)) return null
    if (isNull(key)) return null
    return try {
        // Note that [JSONObject]'s [getString] behavior differs between JVM and Android.
        // In Android, the value is converted to string before being returned.
        // In the JVM, a non string value is ignored and null is returned.
        getString(key)
    } catch (_: Exception) {
        null
    }
}

/**
 * Returns a [Sequence] of all values in the [JSONObject], assuming each value is a [JSONObject]
 *
 * @throws org.json.JSONException if any value is not a [JSONObject].
 */
fun JSONObject.jsonObjectIterable(): Iterable<JSONObject> =
    this
        .keys()
        .asSequence()
        .map { getJSONObject(it) }
        .asIterable()

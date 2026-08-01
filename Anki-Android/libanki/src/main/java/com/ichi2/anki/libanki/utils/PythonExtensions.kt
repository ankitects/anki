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

package com.ichi2.anki.libanki.utils

import com.ichi2.anki.common.json.JSONContainer
import com.ichi2.anki.common.json.JSONObjectHolder
import com.ichi2.anki.common.utils.ext.deepClone
import com.ichi2.anki.common.utils.ext.jsonObjectIterable
import com.ichi2.anki.libanki.DeckConfig
import org.json.JSONArray
import org.json.JSONObject
import java.util.Optional

fun <T> MutableList<T>.append(value: T) {
    this.add(value)
}

fun <T> MutableList<T>.extend(elements: Iterable<T>) {
    this.addAll(elements)
}

fun <T> len(l: Sequence<T>): Long = l.count().toLong()

fun <T> len(l: List<T>): Int = l.size

fun len(l: JSONArray): Long = l.length().toLong()

fun <E> MutableList<E>.pop(i: Int): E = this.removeAt(i)

fun <K, V> HashMap<K, V>.items(): List<Pair<K, V>> =
    this.entries.map {
        Pair(it.key, it.value)
    }

fun <T> List<T>?.isNullOrEmpty(): Boolean = this == null || this.isEmpty()

fun <T> list(vararg elements: T) = mutableListOf(elements)

fun <T> list(values: Collection<T>): List<T> = ArrayList(values)

fun <T> set(values: List<T>): HashSet<T> = HashSet(values)

fun String.join(values: Iterable<String>): String = values.joinToString(this)

fun <E> MutableList<E>.toJsonArray(): JSONArray {
    val array = JSONArray()
    for (i in this) {
        array.put(i)
    }
    return array
}

fun JSONArray.remove(jsonObject: JSONObject) {
    val index = this.index(jsonObject)
    if (!index.isPresent) {
        throw IllegalArgumentException("Could not find $jsonObject")
    }
    this.remove(index.get())
}

fun JSONArray.index(jsonObject: JSONObject): Optional<Int> {
    this.jsonObjectIterable().forEachIndexed { i, value ->
        run {
            if (jsonObject == value) {
                return Optional.of(i)
            }
        }
    }
    return Optional.empty()
}

operator fun JSONObject.set(
    s: String,
    value: String,
) {
    this.put(s, value)
}

operator fun JSONObject.set(
    s: String,
    value: Int,
) {
    this.put(s, value)
}

operator fun JSONObject.set(
    s: String,
    value: Double,
) {
    this.put(s, value)
}

fun JSONArray.append(jsonObject: JSONObject) {
    this.put(jsonObject)
}

/**
 * Insert an item at a given position. O(n) at the first position
 *
 * The first argument is the index of the element before which to insert,
 * so `a.insert(0, x)` inserts at the front of the list,
 * and `a.insert(len(a), x)` is equivalent to `a.append(x)`.
 */
fun JSONArray.insert(
    idx: Int,
    jsonObject: JSONObject,
) {
    if (idx >= this.length()) {
        this.put(jsonObject)
        return
    }

    // shuffle the elements up to make room for the next
    // pointer starts at the last element, and appends, after that, replaces elements
    var pointerIndex = this.length() - 1
    while (pointerIndex >= idx) {
        this.put(pointerIndex + 1, this.getJSONObject(pointerIndex))
        pointerIndex--
    }

    this.put(idx, jsonObject)
}

fun <T : JSONObjectHolder> JSONContainer<T>.append(template: T) = jsonArray.append(template.jsonObject)

fun <T : JSONObjectHolder> JSONContainer<T>.remove(template: T) = jsonArray.remove(template.jsonObject)

fun <T : JSONObjectHolder> JSONContainer<T>.index(template: T) = jsonArray.index(template.jsonObject)

fun <T : JSONObjectHolder> JSONContainer<T>.insert(
    idx: Int,
    template: T,
) = jsonArray.insert(idx, template.jsonObject)

fun <T : JSONObjectHolder> len(templates: JSONContainer<T>) = templates.jsonArray.length()

// Changed signature from `copy.deepcopy(clone_from)`
fun DeckConfig.deepClone() = this.copy(jsonObject = this.jsonObject.deepClone())

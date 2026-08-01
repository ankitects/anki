// SPDX-License-Identifier: GPL-3.0-or-later

@file:Suppress("unused")

package com.ichi2.anki.common.json

import androidx.annotation.VisibleForTesting
import com.ichi2.anki.common.utils.ext.jsonObjectIterator
import org.json.JSONArray
import org.json.JSONObject
import kotlin.reflect.KProperty

interface JSONObjectHolder {
    @VisibleForTesting val jsonObject: JSONObject
}

interface JSONContainer<T : JSONObjectHolder> : Iterable<T> {
    val jsonArray: JSONArray

    fun constructor(obj: JSONObject): T

    override fun iterator() =
        jsonArray
            .jsonObjectIterator()
            .asSequence()
            .map(::constructor)
            .iterator()

    operator fun get(index: Int) = constructor(jsonArray.getJSONObject(index))

    /**
     * Sets/replaces the value at [index].
     *
     * This `null` pads this array to the required length if necessary.
     *
     * @see JSONArray.put
     */
    operator fun set(
        index: Int,
        field: T,
    ) {
        jsonArray.put(index, field.jsonObject)
    }

    fun length() = jsonArray.length()

    val size: Int
        get() = length()

    val indices: IntRange
        get() = this.toList().indices
}

fun JSONObjectHolder.jsonBoolean(key: String) = JsonBooleanDelegate(jsonObject, key)

fun JSONObjectHolder.jsonBoolean(
    key: String,
    defaultValue: Boolean,
) = JsonBooleanDelegate(jsonObject, key, defaultValue)

fun JSONObjectHolder.jsonInt(key: String) = JsonIntDelegate(jsonObject, key)

fun JSONObjectHolder.jsonInt(
    key: String,
    defaultValue: Int,
) = JsonIntDelegate(jsonObject, key, defaultValue)

fun JSONObjectHolder.jsonLong(key: String) = JsonLongDelegate(jsonObject, key)

fun JSONObjectHolder.jsonLong(
    key: String,
    defaultValue: Long,
) = JsonLongDelegate(jsonObject, key, defaultValue)

fun JSONObjectHolder.jsonDouble(key: String) = JsonDoubleDelegate(jsonObject, key)

fun JSONObjectHolder.jsonDouble(
    key: String,
    defaultValue: Double,
) = JsonDoubleDelegate(jsonObject, key, defaultValue)

fun JSONObjectHolder.jsonString(key: String) = JsonStringDelegate(jsonObject, key)

fun JSONObjectHolder.jsonString(
    key: String,
    defaultValue: String,
) = JsonStringDelegate(jsonObject, key, defaultValue)

fun JSONObjectHolder.jsonArray(key: String) = JsonArrayDelegate(jsonObject, key)

class JsonBooleanDelegate(
    private val json: JSONObject,
    private val key: String,
    private val fallback: Boolean? = null,
) {
    operator fun getValue(
        thisRef: Any?,
        property: KProperty<*>,
    ): Boolean = if (fallback != null) json.optBoolean(key, fallback) else json.getBoolean(key)

    operator fun setValue(
        thisRef: Any?,
        property: KProperty<*>,
        value: Boolean,
    ) {
        json.put(key, value)
    }
}

class JsonIntDelegate(
    private val json: JSONObject,
    private val key: String,
    private val fallback: Int? = null,
) {
    operator fun getValue(
        thisRef: Any?,
        property: KProperty<*>,
    ): Int = if (fallback != null) json.optInt(key, fallback) else json.getInt(key)

    operator fun setValue(
        thisRef: Any?,
        property: KProperty<*>,
        value: Int,
    ) {
        json.put(key, value)
    }
}

class JsonLongDelegate(
    private val json: JSONObject,
    private val key: String,
    private val fallback: Long? = null,
) {
    operator fun getValue(
        thisRef: Any?,
        property: KProperty<*>,
    ): Long = if (fallback != null) json.optLong(key, fallback) else json.getLong(key)

    operator fun setValue(
        thisRef: Any?,
        property: KProperty<*>,
        value: Long,
    ) {
        json.put(key, value)
    }
}

class JsonDoubleDelegate(
    private val json: JSONObject,
    private val key: String,
    private val fallback: Double? = null,
) {
    operator fun getValue(
        thisRef: Any?,
        property: KProperty<*>,
    ): Double = if (fallback != null) json.optDouble(key, fallback) else json.getDouble(key)

    operator fun setValue(
        thisRef: Any?,
        property: KProperty<*>,
        value: Double,
    ) {
        json.put(key, value)
    }
}

class JsonStringDelegate(
    private val json: JSONObject,
    private val key: String,
    private val fallback: String? = null,
) {
    operator fun getValue(
        thisRef: Any?,
        property: KProperty<*>,
    ): String = if (fallback != null) json.optString(key, fallback) else json.getString(key)

    operator fun setValue(
        thisRef: Any?,
        property: KProperty<*>,
        value: String,
    ) {
        json.put(key, value)
    }
}

class JsonArrayDelegate(
    private val json: JSONObject,
    private val key: String,
) {
    operator fun getValue(
        thisRef: Any?,
        property: KProperty<*>,
    ): JSONArray = json.getJSONArray(key)

    operator fun setValue(
        thisRef: Any?,
        property: KProperty<*>,
        value: JSONArray,
    ) {
        json.put(key, value)
    }
}

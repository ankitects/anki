// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.utils.ext

import android.util.JsonWriter
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Writes [obj] incrementally as UTF-8, closing the writer when finished. */
internal fun File.writeJson(obj: JSONObject) {
    JsonWriter(bufferedWriter()).use { writer ->
        writer.writeObject(obj)
    }
}

/**
 * Writes [obj] incrementally, without allocating a string containing the whole JSON document.
 * The caller is responsible for flushing or closing this writer.
 */
internal fun JsonWriter.writeObject(obj: JSONObject) {
    beginObject()
    for (key in obj.keys()) {
        name(key)
        writeValue(obj.get(key))
    }
    endObject()
}

private fun JsonWriter.writeValue(value: Any?) {
    when (value) {
        null, JSONObject.NULL -> nullValue()
        is JSONObject -> writeObject(value)
        is JSONArray -> {
            beginArray()
            for (index in 0 until value.length()) {
                writeValue(value.opt(index))
            }
            endArray()
        }
        is Boolean -> value(value)
        is Number -> value(value)
        else -> value(value.toString())
    }
}

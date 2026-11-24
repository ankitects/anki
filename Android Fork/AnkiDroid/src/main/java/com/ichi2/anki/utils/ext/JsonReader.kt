// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.utils.ext

import android.util.JsonReader
import android.util.JsonToken
import android.util.MalformedJsonException
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Reads a UTF-8 JSON object without allocating a string containing the whole document. */
internal fun File.readJson(): JSONObject =
    JsonReader(bufferedReader()).use { reader ->
        reader.readObject().also {
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw MalformedJsonException("Unexpected data after JSON object")
            }
        }
    }

/** Reads one object. The caller is responsible for closing this reader. */
internal fun JsonReader.readObject(): JSONObject {
    if (peek() != JsonToken.BEGIN_OBJECT) {
        throw MalformedJsonException("Expected a JSON object")
    }
    beginObject()
    val result = JSONObject()
    while (hasNext()) {
        result.put(nextName(), readValue())
    }
    endObject()
    return result
}

private fun JsonReader.readValue(): Any =
    when (peek()) {
        JsonToken.BEGIN_OBJECT -> readObject()
        JsonToken.BEGIN_ARRAY -> {
            beginArray()
            val result = JSONArray()
            while (hasNext()) {
                result.put(readValue())
            }
            endArray()
            result
        }
        JsonToken.STRING -> nextString()
        JsonToken.BOOLEAN -> nextBoolean()
        JsonToken.NULL -> {
            nextNull()
            JSONObject.NULL
        }
        JsonToken.NUMBER -> {
            // Parse integers before doubles so 64-bit note type IDs retain their precision.
            val literal = nextString()
            val integer = literal.toLongOrNull()
            if (integer != null) {
                if (integer in Int.MIN_VALUE..Int.MAX_VALUE) integer.toInt() else integer
            } else {
                literal.toDouble().also {
                    if (!it.isFinite()) throw MalformedJsonException("Non-finite JSON number: $literal")
                }
            }
        }
        else -> throw MalformedJsonException("Expected a JSON value, found ${peek()}")
    }

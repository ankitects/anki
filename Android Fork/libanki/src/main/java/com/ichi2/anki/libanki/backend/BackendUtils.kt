// SPDX-License-Identifier: GPL-3.0-or-later

@file:Suppress("FunctionName")

package com.ichi2.anki.libanki.backend

import com.google.protobuf.ByteString
import com.ichi2.anki.common.json.JSONObjectHolder
import com.ichi2.anki.libanki.utils.LibAnkiAlias
import org.json.JSONArray
import org.json.JSONObject

object BackendUtils {
    @LibAnkiAlias("from_json_bytes")
    fun fromJsonBytes(json: ByteString): JSONObject = JSONObject(json.toStringUtf8())

    fun jsonToArray(json: ByteString): JSONArray = JSONArray(json.toStringUtf8())

    fun toByteString(conf: JSONObject): ByteString {
        val asString: String = conf.toString()
        return ByteString.copyFromUtf8(asString)
    }

    @LibAnkiAlias("to_json_bytes")
    fun toJsonBytes(json: JSONObject): ByteString = toByteString(json)

    fun toJsonBytes(json: JSONObjectHolder): ByteString = toJsonBytes(json.jsonObject)
}

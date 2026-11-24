// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.libanki

import anki.config.ConfigKey
import com.google.protobuf.kotlin.toByteStringUtf8
import com.ichi2.anki.libanki.utils.NotInPyLib
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.exceptions.BackendNotFoundException
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class Config(
    val backend: Backend,
) {
    inline fun <reified T> get(key: String): T? =
        try {
            Json.decodeFromString<T>(backend.getConfigJson(key).toStringUtf8())
        } catch (ex: BackendNotFoundException) {
            null
        } catch (ex: SerializationException) {
            null
        }

    inline fun <reified T> set(
        key: String,
        value: T,
    ) {
        val valueString =
            when (value) {
                JSONObject.NULL -> "null"
                is JSONObject, is JSONArray -> value.toString()
                else -> Json.encodeToString(value)
            }
        backend.setConfigJson(key, valueString.toByteStringUtf8(), false)
    }

    fun remove(key: String) {
        backend.removeConfig(key)
    }

    fun getBool(key: ConfigKey.Bool): Boolean = backend.getConfigBool(key)

    fun setBool(
        key: ConfigKey.Bool,
        value: Boolean,
    ) {
        backend.setConfigBool(key, value, false)
    }

    fun getString(key: ConfigKey.String): String = backend.getConfigString(key)

    fun setString(
        key: ConfigKey.String,
        value: String,
    ) {
        backend.setConfigString(key, value, false)
    }

    @NotInPyLib
    inline fun <reified T> get(
        key: String,
        default: T,
    ): T? =
        try {
            Json.decodeFromString<T>(backend.getConfigJson(key).toStringUtf8())
        } catch (ex: BackendNotFoundException) {
            default
        } catch (ex: SerializationException) {
            null
        }

    @NotInPyLib
    fun getObject(
        key: String,
        default: JSONObject,
    ) = try {
        JSONObject(backend.getConfigJson(key).toStringUtf8())
    } catch (ex: BackendNotFoundException) {
        default
    } catch (ex: JSONException) {
        default
    }
}

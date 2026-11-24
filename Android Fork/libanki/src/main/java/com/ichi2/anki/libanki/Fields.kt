// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.libanki

import com.ichi2.anki.common.json.JSONContainer
import org.json.JSONArray
import org.json.JSONObject

/** A collection of [Field] */
@JvmInline
value class Fields(
    override val jsonArray: JSONArray,
) : JSONContainer<Field> {
    override fun constructor(obj: JSONObject) = Field(obj)
}

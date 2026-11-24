// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.libanki

import com.ichi2.anki.common.json.JSONContainer
import com.ichi2.anki.libanki.utils.NotInPyLib
import org.json.JSONArray
import org.json.JSONObject

/**
 * A collection of [CardTemplate]
 *
 * @see NotetypeJson.templates
 */
@JvmInline
@NotInPyLib
value class CardTemplates(
    override val jsonArray: JSONArray,
) : JSONContainer<CardTemplate> {
    override fun constructor(obj: JSONObject) = CardTemplate(obj)
}

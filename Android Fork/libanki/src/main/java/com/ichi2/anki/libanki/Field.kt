// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.libanki

import com.ichi2.anki.common.json.JSONObjectHolder
import com.ichi2.anki.common.json.jsonBoolean
import com.ichi2.anki.common.json.jsonInt
import com.ichi2.anki.common.json.jsonString
import com.ichi2.anki.common.utils.ext.getLongOrNull
import com.ichi2.anki.common.utils.ext.getStringOrNull
import net.ankiweb.rsdroid.RustCleanup
import org.json.JSONObject

/**
 * A [note type][NotetypeJson] contains 1 to many named fields which define what a user can enter.
 * Fields are combined with [card templates][NotetypeJson.templates], to allow the note to generate
 * multiple cards
 *
 * Basic field substitution syntax looks like: `{{Front}}`, where `Front` is defined by [name].
 * This syntax is Anki-specific (similar to [mustache](https://mustache.github.io/mustache.5.html]))
 * and is [documented in the Manual](https://docs.ankiweb.net/templates/fields.html)
 *
 * This class documents field properties, most of which are relevant to inserting/editing notes,
 * with a few related to searching/card rendering
 *
 * In AnkiDroid, this also affects the font and language used in 'type the answer'
 *
 * ### Desktop-only properties
 * TODO: The following properties are used in Anki Desktop (as-of 2024), but not documented
 *  in this class. Accessors should be created.
 *
 * * collapsed
 * * description - text to show inside the field when it's empty
 * * excludeFromSearch
 * * plainText
 * * preventDeletion
 * * rtl
 *
 * ## Further Reading
 * [Anki manual - fields](https://docs.ankiweb.net/templates/fields.html)
 * [Anki manual - customizing fields](https://docs.ankiweb.net/editing.html#customizing-fields)
 */
data class Field(
    override val jsonObject: JSONObject,
) : JSONObjectHolder {
    /**
     * The user-facing name of the field.
     *
     * Card template substitutions use the name (`{{Front}}`, where [name] = `Front`)
     */
    var name by jsonString("name")

    /** The 0-based ordinal of the field */
    val ord by jsonInt("ord")

    val id: Long?
        get() = jsonObject.getLongOrNull("id")

    /**
     * If `false`, the note editor erases the content of this field after a note is created
     *
     * Usage: 'pin/freeze' a field, so a user does not need to type in duplicate values
     */
    var sticky by jsonBoolean("sticky")

    /**
     * The font used in the note editor when editing the note
     *
     * ⚠️: AnkiDroid also uses this for 'type the answer'
     */
    var font by jsonString("font")

    /**
     * The font size used in the note editor when editing the note
     *
     * ⚠️: AnkiDroid also uses this for 'type the answer'
     */
    var fontSize by jsonInt("size")

    /**
     * @see [anki.notetypes.ImageOcclusionField]
     */
    val imageOcclusionTag: String?
        get() = jsonObject.getStringOrNull("tag")

    /** @see ord */
    @RustCleanup("Check JSONObject.NULL")
    fun setOrd(value: Int?) = jsonObject.put("ord", value ?: JSONObject.NULL)

    override fun toString() = jsonObject.toString()
}

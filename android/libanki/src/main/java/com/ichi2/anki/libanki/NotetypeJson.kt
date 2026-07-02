/*
 * Copyright (c) 2020 Arthur Milchior <arthur@milchior.fr>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.libanki

import androidx.annotation.CheckResult
import androidx.annotation.VisibleForTesting
import anki.notetypes.StockNotetype.OriginalStockKind.ORIGINAL_STOCK_KIND_IMAGE_OCCLUSION_VALUE
import anki.notetypes.StockNotetype.OriginalStockKind.ORIGINAL_STOCK_KIND_UNKNOWN_VALUE
import com.ichi2.anki.common.json.JSONObjectHolder
import com.ichi2.anki.common.json.NamedObject
import com.ichi2.anki.common.json.jsonArray
import com.ichi2.anki.common.json.jsonBoolean
import com.ichi2.anki.common.json.jsonInt
import com.ichi2.anki.common.json.jsonLong
import com.ichi2.anki.common.json.jsonString
import com.ichi2.anki.common.utils.ext.deepClone
import com.ichi2.anki.common.utils.ext.toStringList
import com.ichi2.anki.libanki.Consts.DEFAULT_DECK_ID
import org.intellij.lang.annotations.Language
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Represents a note type, a.k.a. Model.
 * The content of an object is described in [https://github.com/ankidroid/Anki-Android/wiki/Database-Structure](https://github.com/ankidroid/Anki-Android/wiki/Database-Structure)
 * Each time the object is modified, `Models.save(this)` should be called, otherwise the change will not be synchronized
 * If a change affect card generation, (i.e. any change on the list of field, or the question side of a card type),
 * `Models.save(this, true)` should be called. However, you should do the change in batch and change only when all are d
 * one, because recomputing the list of card is an expensive operation.
 */
data class NotetypeJson(
    @VisibleForTesting
    override val jsonObject: JSONObject,
) : JSONObjectHolder,
    NamedObject {
    /**
     * Creates a model object from json string
     */
    constructor(
        @Language("JSON") json: String,
    ) : this(JSONObject(json))

    @CheckResult
    fun deepClone() = NotetypeJson(jsonObject.deepClone())

    /**
     * The list of name of fields.
     */
    val fieldsNames: List<String>
        get() = fields.map { it.name }

    fun getField(pos: Int): Field = fields[pos]

    /**
     * @return model did or default deck id (1) if null
     */
    var did: DeckId
        get() = if (jsonObject.isNull("did")) DEFAULT_DECK_ID else jsonObject.optLong("did", Consts.DEFAULT_DECK_ID)
        set(value) {
            jsonObject.put("did", value)
        }

    /**
     * The list of name of the template of this note type.
     * For cloze deletion type, there is a single name, called "cloze" (localized at time of note type creation).
     */
    val templatesNames: List<String>
        get() = jsonObject.getJSONArray("tmpls").toStringList("name")

    val isStd: Boolean
        get() = type == NoteTypeKind.Std

    val isCloze: Boolean
        get() = type == NoteTypeKind.Cloze

    /**
     * The css in common of all card types of this note type.
     */
    var css by jsonString("css")

    /**
     * The preamble for the LaTeX code used in this note type.
     * In AnkiDroid, this can only be used by the CardContentProvider.
     * This is voluntarily not accessible in normal AnkiDroid usage because,
     * after each change of this value all LaTeX content must be recompiled,
     * which requires a desktop with LaTeX installed.
     */
    var latexPre by jsonString("latexPre")

    /**
     * The trailer of the LaTeX code used in this note type.
     * @see latexPre to understand context.
     */
    var latexPost by jsonString("latexPost")

    /**
     * @param sfld Fields of a note of this note type
     * @return The names of non-empty fields
     */
    fun nonEmptyFields(sfld: Array<String>): Set<String> =
        sfld
            .zip(fieldsNames)
            // filter to the fields which are non-empty
            .filter { (sfld, _) -> sfld.trim().isNotEmpty() }
            .mapTo(HashSet()) { (_, fieldName) -> fieldName }

    /**
     * Python method
     * [dict.update](https://docs.python.org/3/library/stdtypes.html?highlight=dict#dict.update)
     *
     * Update the dictionary with the provided key/value pairs, overwriting existing keys
     */
    fun update(updateFrom: NotetypeJson) {
        for (k in updateFrom.jsonObject.keys()) {
            jsonObject.put(k, updateFrom.jsonObject[k])
        }
    }

    /**
     * The array of fields of this note type.
     */
    var fields: Fields
        get() = Fields(jsonObject.getJSONArray("flds"))
        set(value) {
            jsonObject.put("flds", value.jsonArray)
        }

    /**
     * The array of card templates of this note type.
     * For cloze deletion type, the array contain a single element that is the only cloze template of the note type.
     */
    var templates: CardTemplates
        get() = CardTemplates(jsonObject.getJSONArray("tmpls"))
        set(value) {
            jsonObject.put("tmpls", value.jsonArray)
        }

    /**
     * A unique identifier for this note type.
     * The timestamp of the deck creation in millisecond.
     * It's unique in the collection and with high probability unique everywhere.
     * That is, if you import cards using a note type with the same id, it's almost certainly
     * originally the same note type, even if potentially modified since.
     */
    var id: NoteTypeId by jsonLong("id")

    /**
     * The name of the note type.
     */
    override var name by jsonString("name")

    /**
     * One of [anki.notetypes.StockNotetype.OriginalStockKind].
     * Represents the note type that was modified to create the current note type.
     * Can be unset if the note type was created by a version of anki where this value was
     * not recorded.
     * Can be used to check whether a note type is a image occlusion, or
     * to reset the note type to its default value.
     */
    val originalStockKind by jsonInt("originalStockKind", defaultValue = ORIGINAL_STOCK_KIND_UNKNOWN_VALUE)

    val isImageOcclusion: Boolean
        get() =
            try {
                originalStockKind == ORIGINAL_STOCK_KIND_IMAGE_OCCLUSION_VALUE
            } catch (_: JSONException) {
                false
            }

    /**
     * In the card browser, the field noted as "sort field" is the [sortf]-th field. 0-based. */
    var sortf by jsonInt("sortf")

    /**
     * The type of the note type. Can be normal, cloze, or unknown.
     */
    var type: NoteTypeKind
        get() = NoteTypeKind.fromCode(jsonObject.getInt("type"))
        set(value) {
            jsonObject.put("type", value.code)
        }

    /**
     * Timestamp of the last time the note type was modified.
     * sed to decide whether syncing the note type is needed and
     * to resolve conflict when the note type was modified locally and remotely.
     */
    var mod by jsonLong("mod")

    /**
     * -1 if the note type was modified locally since last sync.
     * Otherwise the "usn" value provided by the remote server.
     * Used to know whether this value need to be synced.
     */
    var usn by jsonInt("usn")

    /**
     * Whether latex must be generated as SVG. If false, it's first generated as PDF.
     * This is used to compute the name of the image that represents a LaTeX expression
     * used in a note in this note type.
     * It can't be edited in AnkiDroid because that would require recompiling all LaTeX values
     * which can only be done on a computer with LaTeX installed.
     */
    val latexsvg by jsonBoolean("latexsvg", defaultValue = false)

    /**
     * Defines the requirements for generating cards (for [standard note types][Consts.MODEL_STD])
     *
     * A requirement states that either one of, or all of a set of fields must be non-empty to
     * generate a card using a template. Meaning for a standard note, each template has a
     * requirement, which generates 0 or 1 cards
     *
     * **Example - Basic (optional reversed card):**
     *
     * * Fields: `["Front", "Back", "Add Reverse"]`
     * * `req: [[0, 'any', [0]], [1, 'all', [1, 2]]]`
     *
     * meaning:
     *
     * * Card 1 needs "Front" to be non-empty
     * * Card 2 needs both "Back" and "Add Reverse" to be non-empty
     *
     * The array is of the form `[T, string, list]`, where:
     * - `T` is the ordinal of the template.
     * - `string` is 'none', 'all' or 'any'.
     * - `list` contains ordinals of fields, in increasing order.
     *
     * The output is defined based on the `string`:
     * - if `"none"'`, no cards are generated for this template. `list` should be empty.
     * - if `"all"'`, the card is generated if all fields in `list` are non-empty
     * - if `"any"'`, the card is generated if any field in `list` is non-empty.
     *
     * See [The algorithm to decide how to compute req from the template]
     * (https://github.com/Arthur-Milchior/anki/blob/commented/documentation//templates_generation_rules.md) is explained on:
     */
    @Deprecated(
        "req is no longer used. Exists for backwards compatibility:" +
            "https://forums.ankiweb.net/t/is-req-still-used-or-present/9977",
    )
    var req: JSONArray by jsonArray("req")

    override fun toString(): String = jsonObject.toString()
}

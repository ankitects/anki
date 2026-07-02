/*
 * Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com>
 * Copyright (c) 2014 Houssam Salem <houssam.salem.au@gmail.com>
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
import anki.notes.NoteFieldsCheckResponse
import com.ichi2.anki.common.utils.annotation.KotlinCleanup
import com.ichi2.anki.common.utils.emptyStringArray
import com.ichi2.anki.libanki.Consts.DEFAULT_DECK_ID
import com.ichi2.anki.libanki.backend.model.toBackendNote
import com.ichi2.anki.libanki.utils.LibAnkiAlias
import com.ichi2.anki.libanki.utils.NotInPyLib
import java.util.regex.Pattern

@KotlinCleanup("lots to do")
class Note : Cloneable {
    /**
     * Should only be mutated by addNote()
     */
    var id: NoteId = 0L

    @get:VisibleForTesting
    var guId: String? = null
        private set
    private var _notetype: NotetypeJson? = null

    // Value type can't be lateinit. Thus using a backing field.
    var notetype: NotetypeJson
        get() = _notetype!!
        set(value) {
            _notetype = value
        }

    val noteTypeId: NoteTypeId
        get() = mid

    /** for upstream compatibility, use [noteTypeId] outside libAnki */
    private var mid: NoteTypeId = 0L
    lateinit var tags: MutableList<String>
    lateinit var fields: MutableList<String>
    private var fMap: Map<String, Pair<Int, Field>>? = null
    var usn = 0
        private set
    var mod: Int = 0
        private set

    constructor(col: Collection, id: NoteId) {
        this.id = id
        load(col)
    }

    constructor(col: Collection, backendNote: anki.notes.Note) {
        loadFromBackendNote(col, backendNote)
    }

    companion object {
        fun fromNotetypeId(
            col: Collection,
            ntid: NoteTypeId,
        ): Note {
            val backendNote = col.backend.newNote(ntid)
            return Note(col, backendNote)
        }
    }

    fun load(col: Collection) {
        val note = col.backend.getNote(id)
        loadFromBackendNote(col, note)
    }

    @LibAnkiAlias("_load_from_backend_note")
    private fun loadFromBackendNote(
        col: Collection,
        note: anki.notes.Note,
    ) {
        id = note.id
        guId = note.guid
        mid = note.notetypeId
        notetype = col.notetypes.get(noteTypeId)!! // not in libAnki
        mod = note.mtimeSecs
        usn = note.usn
        // the lists in the protobuf are NOT mutable, even though they cast to MutableList
        tags = note.tagsList.toMutableList()
        fields = note.fieldsList.toMutableList()
        fMap = Notetypes.fieldMap(notetype)
    }

    @NotInPyLib
    fun numberOfCards(col: Collection): Int = cardIds(col).size

    fun cardIds(col: Collection): List<Long> = col.cardIdsOfNote(nid = id)

    fun cards(col: Collection): List<Card> = cardIds(col).map { col.getCard(it) }

    @LibAnkiAlias("ephemeral_card")
    fun ephemeralCard(
        col: Collection,
        ord: Int = 0,
        customNoteType: NotetypeJson? = null,
        customTemplate: CardTemplate? = null,
        fillEmpty: Boolean = false,
        deckId: DeckId = DEFAULT_DECK_ID,
    ): Card {
        val card = Card(col, id = null)
        card.ord = ord
        card.did = deckId

        val model = customNoteType ?: notetype
        val template =
            if (customTemplate != null) {
                customTemplate.deepClone()
            } else {
                val index = if (model.isStd) ord else 0
                model.templates[index]
            }
        // may differ in cloze case
        template.setOrd(card.ord)

        val output =
            TemplateManager.TemplateRenderContext
                .fromCardLayout(
                    note = this,
                    card = card,
                    notetype = model,
                    template = template,
                    fillEmpty = fillEmpty,
                ).render(col)
        card.renderOutput = output
        card.note = this
        return card
    }

    /** The first card, assuming it exists. */
    @CheckResult
    @NotInPyLib
    fun firstCard(col: Collection): Card =
        col.getCard(
            col.db.queryLongScalar(
                "SELECT id FROM cards WHERE nid = ? ORDER BY ord LIMIT 1",
                id,
            ),
        )

    /**
     * Dict interface
     * ***********************************************************
     */
    fun keys(): Array<String> = fMap!!.keys.toTypedArray()

    @KotlinCleanup("see if we can make this immutable")
    fun values(): MutableList<String> = fields

    fun items(): Array<Array<String>> {
        // TODO: Revisit this method. The field order returned differs from Anki.
        // The items here are only used in the note editor, so it's a low priority.
        val result =
            Array(
                fMap!!.size,
            ) { emptyStringArray(2) }
        for (fname in fMap!!.keys) {
            val i = fMap!![fname]!!.first
            result[i][0] = fname
            result[i][1] = fields[i]
        }
        return result
    }

    @LibAnkiAlias("_field_index")
    private fun fieldIndex(key: String): Int {
        val fieldPair =
            fMap!![key]
                ?: throw IllegalArgumentException(
                    String.format(
                        "No field named '%s' found",
                        key,
                    ),
                )
        return fieldPair.first
    }

    @LibAnkiAlias("__getitem__")
    fun getItem(key: String): String = fields[fieldIndex(key)]

    @LibAnkiAlias("__setitem__")
    fun setItem(
        key: String,
        value: String,
    ) {
        fields[fieldIndex(key)] = value
    }

    @LibAnkiAlias("__contains__")
    operator fun contains(key: String): Boolean = fMap!!.containsKey(key)

    @NotInPyLib
    fun setField(
        index: Int,
        value: String,
    ) {
        fields[index] = value
    }

    /**
     * Tags
     * ***********************************************************
     */

    @LibAnkiAlias("has_tag")
    fun hasTag(
        col: Collection,
        tag: String,
    ): Boolean = col.tags.inList(tag, tags)

    /**
     * Add tag. Duplicates will be stripped on save.
     */
    @LibAnkiAlias("add_tag")
    fun addTag(tag: String) {
        tags.add(tag)
    }

    @LibAnkiAlias("remove_tag")
    fun removeTag(tag: String) {
        val rem: MutableList<String> =
            ArrayList(
                tags.size,
            )
        for (t in tags) {
            if (t.equals(tag, ignoreCase = true)) {
                rem.add(t)
            }
        }
        for (r in rem) {
            tags.remove(r)
        }
    }

    @LibAnkiAlias("string_tags")
    fun stringTags(col: Collection): String = col.tags.join(col.tags.canonify(tags))

    @LibAnkiAlias("set_tags_from_str")
    fun setTagsFromStr(
        col: Collection,
        str: String,
    ) {
        tags = col.tags.split(str)
    }

    /**
     * Unique/duplicate check
     * ***********************************************************
     */

    @LibAnkiAlias("fields_check")
    fun fieldsCheck(col: Collection): NoteFieldsCheckResponse.State = col.backend.noteFieldsCheck(toBackendNote()).state

    public override fun clone(): Note =
        try {
            super.clone() as Note
        } catch (e: CloneNotSupportedException) {
            throw RuntimeException(e)
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val note = other as Note
        return id == note.id
    }

    override fun hashCode(): Int = (id xor (id ushr 32)).toInt()

    object ClozeUtils {
        private val clozeRegexPattern = Pattern.compile("\\{\\{c(\\d+)::")

        /**
         * Calculate the next number that should be used if inserting a new cloze deletion.
         * Per the manual the next number should be greater than any existing cloze deletion
         * even if there are gaps in the sequence, and regardless of existing cloze ordering
         *
         * @param fieldValues Iterable of field values that may contain existing cloze deletions
         * @return the next index that a cloze should be inserted at
         */
        @KotlinCleanup("general regex fixes for '.group' being nullable")
        fun getNextClozeIndex(fieldValues: Iterable<String>): Int {
            var highestClozeId = 0
            // Begin looping through the fields
            for (fieldLiteral in fieldValues) {
                // Begin searching in the current field for cloze references
                val matcher = clozeRegexPattern.matcher(fieldLiteral)
                while (matcher.find()) {
                    val detectedClozeId = matcher.group(1)!!.toInt()
                    if (detectedClozeId > highestClozeId) {
                        highestClozeId = detectedClozeId
                    }
                }
            }
            return highestClozeId + 1
        }
    }
}

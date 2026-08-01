/*
 * Copyright (c) 2009 Daniel Svärd <daniel.svard@gmail.com>                             *
 * Copyright (c) 2010 Rick Gruber-Riemer <rick@vanosten.net>                            *
 * Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com>                         *
 * Copyright (c) 2011 Kostas Spyropoulos <inigo.aldana@gmail.com>                       *
 * Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * This file incorporates code under the following license
 * https://github.com/ankitects/anki/blob/c4db4bd2913234d077aa289543da6405a62f53dc/pylib/anki/models.py
 *
 *    Copyright: Ankitects Pty Ltd and contributors
 *    License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
 *
 */

// This file is called models.py in the desktop code for legacy reasons.

@file:Suppress("LiftReturnOrAssignment", "FunctionName")

package com.ichi2.anki.libanki

import androidx.annotation.CheckResult
import anki.collection.OpChanges
import anki.collection.OpChangesWithId
import anki.notetypes.ChangeNotetypeInfo
import anki.notetypes.ChangeNotetypeRequest
import anki.notetypes.Notetype
import anki.notetypes.NotetypeId
import anki.notetypes.NotetypeNameId
import anki.notetypes.NotetypeNameIdUseCount
import anki.notetypes.StockNotetype
import anki.notetypes.restoreNotetypeToStockRequest
import com.google.protobuf.ByteString
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.libanki.Utils.checksum
import com.ichi2.anki.libanki.backend.BackendUtils
import com.ichi2.anki.libanki.backend.BackendUtils.fromJsonBytes
import com.ichi2.anki.libanki.backend.BackendUtils.toJsonBytes
import com.ichi2.anki.libanki.utils.LibAnkiAlias
import com.ichi2.anki.libanki.utils.NotInPyLib
import com.ichi2.anki.libanki.utils.append
import com.ichi2.anki.libanki.utils.index
import com.ichi2.anki.libanki.utils.insert
import com.ichi2.anki.libanki.utils.len
import com.ichi2.anki.libanki.utils.remove
import net.ankiweb.rsdroid.RustCleanup
import net.ankiweb.rsdroid.exceptions.BackendInvalidInputException
import net.ankiweb.rsdroid.exceptions.BackendNotFoundException
import org.intellij.lang.annotations.Language
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

class NoteTypeNameID(
    val name: String,
    val id: NoteTypeId,
) {
    // support extension
    companion object
}

class Notetypes(
    val col: Collection,
) {
    /*
    # Saving/loading registry
    #############################################################
     */

    /**
     * Associating a note type id to its note type.
     */
    @LibAnkiAlias("_cache")
    private val cache = HashMap<NoteTypeId, NotetypeJson>()

    /** Save changes made to provided note type. */
    fun save(notetype: NotetypeJson) {
        // legacy code expects preserve_usn=false behaviour, but that
        // causes a backup entry to be created, which invalidates the
        // v2 review history. So we manually update the usn/mtime here
        notetype.mod = TimeManager.time.intTime()
        notetype.usn = col.usn()
        update(notetype, preserveUsnAndMtime = true)
    }

    /*
    # Caching
    #############################################################
    # A lot of existing code expects to be able to quickly and
    # frequently obtain access to an entire notetype, so we currently
    # need to cache responses from the backend. Please do not
    # access the cache directly!
     */

    @LibAnkiAlias("_update_cache")
    private fun updateCache(nt: NotetypeJson) {
        cache[nt.id] = nt
    }

    @LibAnkiAlias("_remove_from_cache")
    internal fun removeFromCache(ntid: NoteTypeId) {
        cache.remove(ntid)
    }

    @LibAnkiAlias("_get_cached")
    private fun getCached(ntid: NoteTypeId): NotetypeJson? = cache[ntid]

    @NeedsTest("14827: styles are updated after syncing style changes")
    @LibAnkiAlias("_clear_cache")
    fun clearCache() = cache.clear()

    /*
    # Listing note types
    #############################################################
     */

    @LibAnkiAlias("all_names_and_ids")
    fun allNamesAndIds(): Sequence<NoteTypeNameID> =
        col.backend
            .getNotetypeNames()
            .map {
                NoteTypeNameID(it.name, it.id)
            }.asSequence()

    // legacy

    fun ids(): Set<NoteTypeId> = allNamesAndIds().map { it.id }.toSet()

    // only used by importing code
    fun have(id: NoteTypeId): Boolean = allNamesAndIds().any { it.id == id }

    /*
    # Current note type
    #############################################################
     */

    /** Get current model.*/
    @RustCleanup("Should use defaultsForAdding() instead")
    fun current(forDeck: Boolean = true): NotetypeJson {
        var noteType = get(col.decks.current().getLongOrNull("mid"))
        if (!forDeck || noteType == null) {
            noteType = get(col.config.get("curModel") ?: 1L)
        }
        if (noteType != null) {
            return noteType
        }
        return get(allNamesAndIds().first().id)!!
    }

    fun setCurrent(notetype: NotetypeJson) {
        col.config.set("curModel", notetype.id)
    }

    /*
    # Retrieving and creating models
    #############################################################
     */

    @LibAnkiAlias("id_for_name")
    fun idForName(name: String): NoteTypeId? =
        try {
            col.backend.getNotetypeIdByName(name)
        } catch (e: BackendNotFoundException) {
            null
        }

    /** "Get model with ID, or None." */
    fun get(id: NoteTypeId): NotetypeJson? = get(id as NoteTypeId?)

    /** Externally, we do not want to pass in a null id */
    private fun get(id: NoteTypeId?): NotetypeJson? {
        if (id == null) {
            return null
        }
        var nt = getCached(id)
        if (nt == null) {
            try {
                nt =
                    NotetypeJson(
                        BackendUtils.fromJsonBytes(
                            col.backend.getNotetypeLegacy(id),
                        ),
                    )
                updateCache(nt)
            } catch (e: BackendNotFoundException) {
                return null
            }
        }
        return nt
    }

    /** Get all models */
    fun all(): List<NotetypeJson> = allNamesAndIds().map { get(it.id)!! }.toMutableList()

    /** Get model with NAME. */
    fun byName(name: String): NotetypeJson? {
        val id = idForName(name)
        return id?.let { get(it) }
    }

    /** Create a new non-cloze model, and return it. */
    fun new(name: String): NotetypeJson {
        // caller should call save() after modifying
        val nt = newBasicNotetype()
        nt.fields = Fields(JSONArray())
        nt.templates = CardTemplates(JSONArray())
        nt.name = name
        return nt
    }

    fun newBasicNotetype(): NotetypeJson =
        NotetypeJson(
            BackendUtils.fromJsonBytes(
                col.backend.getStockNotetypeLegacy(StockNotetype.Kind.KIND_BASIC),
            ),
        )

    /** Modifies schema. */
    fun remove(id: NoteTypeId) {
        removeFromCache(id)
        col.backend.removeNotetype(id)
    }

    fun add(notetype: NotetypeJson) {
        save(notetype)
    }

    fun ensureNameUnique(notetype: NotetypeJson) {
        val existingId = idForName(notetype.name)
        existingId?.let {
            if (it != notetype.id) {
                // Python uses a float time, but it doesn't really matter, the goal is just a random id.
                notetype.name += "-" + checksum(TimeManager.time.intTimeMS().toString()).substring(0, 5)
            }
        }
    }

    /** Add or update an existing model. Use .save() instead. */
    fun update(
        notetype: NotetypeJson,
        preserveUsnAndMtime: Boolean = true,
    ) {
        removeFromCache(notetype.id)
        ensureNameUnique(notetype)
        notetype.id =
            col.backend.addOrUpdateNotetype(
                json = toJsonBytes(notetype),
                preserveUsnAndMtime = preserveUsnAndMtime,
                skipChecks = preserveUsnAndMtime,
            )
        setCurrent(notetype)
        mutateAfterWrite(notetype)
    }

    /** Update a NotetypeDict. Caller will need to re-load notetype if new fields/cards added. */
    fun updateDict(
        notetype: NotetypeJson,
        skipChecks: Boolean = false,
    ): OpChanges {
        removeFromCache(notetype.id)
        ensureNameUnique(notetype)
        return col.backend.updateNotetypeLegacy(toJsonBytes(notetype), skipChecks)
    }

    @LibAnkiAlias("_mutate_after_write")
    private fun mutateAfterWrite(nt: NotetypeJson) {
        // existing code expects the note type to be mutated to reflect
        // the changes made when adding, such as ordinal assignment :-(
        val updated = get(nt.id)!!
        nt.update(updated)
    }

    /*
    # Tools
    ##################################################
     */

    @NotInPyLib
    fun nids(model: NotetypeJson): List<NoteId> = nids(model.id)

    /** Note ids for M. */
    fun nids(ntid: NoteTypeId): List<NoteId> = col.db.queryLongList("select id from notes where mid = ?", ntid)

    /** Number of note using M. */
    fun useCount(notetype: NotetypeJson): Int = col.db.queryLongScalar("select count() from notes where mid = ?", notetype.id).toInt()

    @RustCleanup("not in libAnki any more - may not be needed")
    fun tmplUseCount(
        notetype: NotetypeJson,
        ord: Int,
    ): Int =
        col.db.queryScalar(
            "select count() from cards, notes where cards.nid = notes.id and notes.mid = ? and cards.ord = ?",
            notetype.id,
            ord,
        )

    /*
    # Copying
    ##################################################
     */

    /** Copy, save and return.
     * This code is currently only used by unit tests. If the  GUI starts to use it, the signature
     * should be updated so that a translated name is passed in. */
    fun copy(notetype: NotetypeJson): NotetypeJson {
        val noteType2 = notetype.deepClone()
        noteType2.name = "${noteType2.name} copy"
        // noteType2.name = col.context.getString(R.string.copy_note_type_name, noteType2.name)
        noteType2.id = 0
        add(noteType2)
        return noteType2
    }

    /*
    # Adding & changing fields
    ##################################################
     */

    @LibAnkiAlias("new_field")
    fun newField(name: String): Field {
        val nt = newBasicNotetype()
        val field = nt.fields[0]
        field.name = name
        field.setOrd(null)
        return field
    }

    /** Modifies schema */
    @LibAnkiAlias("add_field")
    fun addField(
        notetype: NotetypeJson,
        field: Field,
    ) {
        notetype.fields.append(field)
    }

    /** Modifies schema. */
    @LibAnkiAlias("remove_field")
    fun removeField(
        notetype: NotetypeJson,
        field: Field,
    ) {
        notetype.fields.remove(field)
    }

    /** Modifies schema. */
    @LibAnkiAlias("reposition_field")
    fun repositionField(
        notetype: NotetypeJson,
        field: Field,
        idx: Int,
    ) {
        val oldidx = notetype.fields.index(field).get()
        if (oldidx == idx) {
            return
        }

        notetype.fields.remove(field)
        notetype.fields.insert(idx, field)
    }

    @LibAnkiAlias("rename_field")
    fun renameField(
        notetype: NotetypeJson,
        field: Field,
        newName: String,
    ) {
        check(notetype.fields.contains(field)) { "Field to be renamed was not found in the notetype fields" }
        field.name = newName
    }

    /** Modifies schema. */
    @LibAnkiAlias("set_sort_index")
    fun setSortIndex(
        nt: NotetypeJson,
        idx: Int,
    ) {
        require(0 <= idx && idx < len(nt.fields)) { "Selected sort field's index is not valid" }
        nt.sortf = idx
    }

    /*
     legacy
     */
    @RustCleanup("legacy")
    fun addFieldLegacy(
        notetype: NotetypeJson,
        field: Field,
    ) {
        addField(notetype, field)
        if (notetype.id != 0L) {
            save(notetype)
        }
    }

    @RustCleanup("legacy")
    fun remFieldLegacy(
        notetype: NotetypeJson,
        field: Field,
    ) {
        removeField(notetype, field)
        save(notetype)
    }

    @RustCleanup("legacy")
    fun moveFieldLegacy(
        notetype: NotetypeJson,
        field: Field,
        idx: Int,
    ) {
        repositionField(notetype, field, idx)
        save(notetype)
    }

    @RustCleanup("legacy")
    fun renameFieldLegacy(
        notetype: NotetypeJson,
        field: Field,
        newName: String,
    ) {
        renameField(notetype, field, newName)
        save(notetype)
    }

    fun addFieldModChanged(
        notetype: NotetypeJson,
        field: Field,
    ) {
        // similar to Anki's addField; but thanks to assumption that
        // mod is already changed, it never has to throw
        // ConfirmModSchemaException.
        check(col.schemaChanged()) { "Mod was assumed to be already changed, but is not" }
        addFieldLegacy(notetype, field)
    }

    fun addTemplateModChanged(
        notetype: NotetypeJson,
        template: CardTemplate,
    ) {
        // similar to addTemplate, but doesn't throw exception;
        // asserting the model is new.
        check(col.schemaChanged()) { "Mod was assumed to be already changed, but is not" }
        addTemplate(notetype, template)
    }

    /*
    # Adding & changing templates
    ##################################################
     */

    @RustCleanup("Check JSONObject.NULL")
    @LibAnkiAlias("new_template")
    fun newTemplate(name: String): CardTemplate {
        val nt = newBasicNotetype()
        val template = nt.templates[0]
        template.name = name
        template.qfmt = ""
        template.afmt = ""
        template.setOrd(null)
        return template
    }

    /** Modifies schema. */
    @LibAnkiAlias("add_template")
    fun add_template(
        notetype: NotetypeJson,
        template: CardTemplate,
    ) {
        notetype.templates.append(template)
    }

    /** Modifies schema */
    @LibAnkiAlias("remove_template")
    fun removeTemplate(
        notetype: NotetypeJson,
        template: CardTemplate,
    ) {
        check(len(notetype.templates) > 1) { "Attempting to remove the last template" }
        notetype.templates.remove(template)
    }

    /** Modifies schema. */
    @LibAnkiAlias("reposition_template")
    fun repositionTemplate(
        notetype: NotetypeJson,
        template: CardTemplate,
        idx: Int,
    ) {
        val oldidx = notetype.templates.index(template).get()
        if (oldidx == idx) {
            return
        }

        notetype.templates.remove(template)
        notetype.templates.insert(idx, template)
    }

    /** legacy */

    fun addTemplate(
        notetype: NotetypeJson,
        template: CardTemplate,
    ) {
        add_template(notetype, template)
        if (notetype.id != 0L) {
            save(notetype)
        }
    }

    /*
     * Changing notetypes of notes
     * ***********************************************************
     */

    /**
     * @return The ID of the single note type which all supplied notes are using; throws otherwise
     *
     * @throws BackendInvalidInputException notes from different note types were supplied
     * @throws BackendInvalidInputException an empty list was supplied
     * @throws BackendNotFoundException One of the provided IDs was invalid
     */
    @CheckResult
    @LibAnkiAlias("get_single_notetype_of_notes")
    fun getSingleNotetypeOfNotes(noteIds: List<NoteId>): NoteTypeId = col.backend.getSingleNotetypeOfNotes(noteIds)

    @CheckResult
    @LibAnkiAlias("change_notetype_info")
    fun changeNotetypeInfo(
        oldNoteTypeId: NoteTypeId,
        newNoteTypeId: NoteTypeId,
    ): ChangeNotetypeInfo =
        this.col.backend.getChangeNotetypeInfo(
            oldNotetypeId = oldNoteTypeId,
            newNotetypeId = newNoteTypeId,
        )

    /**
     * Assign a new notetype, optionally altering field/template order.
     *
     * To get defaults, use
     *
     * ```kotlin
     * val info = col.models.change_notetype_info(...)
     * val input = info.input
     * input.note_ids.extend([...])
     * ```
     *
     * The `newFields` and `newTemplates` lists are relative to the new notetype's
     * field/template count.
     *
     * Each value represents the index in the previous notetype.
     * -1 indicates the original value will be discarded.
     *
     * **This method updates the schema without confirmation**
     */
    @LibAnkiAlias("change_notetype_of_notes")
    fun changeNotetypeOfNotes(input: ChangeNotetypeRequest): OpChanges {
        val opBytes = col.backend.changeNotetypeRaw(input.toByteArray())
        return OpChanges.parseFrom(opBytes)
    }

    /**
     * Restores a notetype to its original stock kind.
     *
     * @param notetypeId id of the changed notetype
     * @param forceKind optional stock kind to be forced instead of the original kind.
     * Older notetypes did not store their original stock kind, so we allow the UI
     * to pass in an override to use when missing, or for tests.
     */
    @CheckResult
    @LibAnkiAlias("restore_notetype_to_stock")
    fun restoreNotetypeToStock(
        notetypeId: NotetypeId,
        forceKind: StockNotetype.Kind? = null,
    ): OpChanges {
        val msg =
            restoreNotetypeToStockRequest {
                this.notetypeId = notetypeId
                forceKind?.let { this.forceKind = forceKind }
            }
        return col.backend.restoreNotetypeToStock(msg).also {
            // not in libAnki:
            // Remove the specific notetype from cache to ensure consistency after restoration
            removeFromCache(notetypeId.ntid)
        }
    }

    /*
    # Model changing
    ##########################################################################
    # - maps are ord->ord, and there should not be duplicate targets
    # - newModel should be same as m if model is not changing
     */

    /**
     * Modifies the backend schema. Ask the user to confirm schema changes before calling
     *
     * A compatibility wrapper that converts legacy-style arguments and
     * feeds them into a backend request, so that AnkiDroid's editor-bound
     * notetype changing can be used.
     * */
    @Deprecated("Replace with ChangeNoteTypeDialog")
    fun change(
        noteType: NotetypeJson,
        nid: NoteId,
        newModel: NotetypeJson,
        fmap: Map<Int, Int?>,
        cmap: Map<Int, Int?>,
    ): OpChanges {
        val fieldMap = convertLegacyMap(fmap, newModel.fieldsNames.size)
        val templateMap =
            if (cmap.isEmpty() || noteType.isCloze || newModel.isCloze) {
                listOf()
            } else {
                convertLegacyMap(cmap, newModel.templatesNames.size)
            }
        val isCloze = newModel.isCloze || noteType.isCloze
        return col.backend.changeNotetype(
            noteIds = listOf(nid),
            newFields = fieldMap,
            newTemplates = templateMap,
            oldNotetypeId = noteType.id,
            newNotetypeId = newModel.id,
            currentSchema = col.scm,
            oldNotetypeName = noteType.name,
            isCloze = isCloze,
        )
    }

    /** Convert old->new map to list of old indexes/nulls */
    private fun convertLegacyMap(
        map: Map<Int, Int?>,
        newSize: Int,
    ): Iterable<Int> {
        val newToOld = map.entries.filter { it.value != null }.associate { (k, v) -> v to k }
        val output = mutableListOf<Int>()
        for (idx in 0 until newSize) {
            output.append(newToOld[idx] ?: -1)
        }
        return output
    }

    /*
    # Schema hash
    ##########################################################################
     */

    /** Return a hash of the schema, to see if models are compatible. */
    fun scmhash(notetype: NotetypeJson): String {
        var s = ""
        for (f in notetype.fields) {
            s += f.name
        }
        for (t in notetype.templates) {
            s += t.name
        }
        return checksum(s)
    }

    /*
     * Other stuff NOT IN LIBANKI
     * ***********************************************************************************************
     */

    fun count(): Int = allNamesAndIds().count()

    /**
     * Extracted from remTemplate so we can test if removing templates is safe without actually removing them
     * This method will either give you all the card ids for the ordinals sent in related to the model sent in *or*
     * it will return null if the result of deleting the ordinals is unsafe because it would leave notes with no cards
     *
     * @param noteTypeId id of the note type
     * @param ords array of ints, each one is the ordinal a the card template in the given model
     * @return null if deleting ords would orphan notes, list of related card ids to delete if it is safe
     */
    @Suppress("ktlint:standard:max-line-length")
    fun getCardIdsForNoteType(
        noteTypeId: NoteTypeId,
        ords: IntArray,
    ): List<CardId>? {
        val cardIdsToDeleteSql =
            "select c2.id from cards c2, notes n2 where c2.nid=n2.id and n2.mid = ? and c2.ord  in ${Utils.ids2str(ords)}"
        val cids: List<CardId> = col.db.queryLongList(cardIdsToDeleteSql, noteTypeId)
        // Timber.d("cardIdsToDeleteSql was ' %s' and got %s", cardIdsToDeleteSql, Utils.ids2str(cids));
        Timber.d("getCardIdsForModel found %s cards to delete for model %s and ords %s", cids.size, noteTypeId, Utils.ids2str(ords))

        // all notes with this template must have at least two cards, or we could end up creating orphaned notes
        val noteCountPreDeleteSql = "select count(distinct(nid)) from cards where nid in (select id from notes where mid = ?)"
        val preDeleteNoteCount: Int = col.db.queryScalar(noteCountPreDeleteSql, noteTypeId)
        Timber.d("noteCountPreDeleteSql was '%s'", noteCountPreDeleteSql)
        Timber.d("preDeleteNoteCount is %s", preDeleteNoteCount)
        val noteCountPostDeleteSql =
            "select count(distinct(nid)) from cards where nid in (select id from notes where mid = ?) and ord not in ${Utils.ids2str(ords)}"
        Timber.d("noteCountPostDeleteSql was '%s'", noteCountPostDeleteSql)
        val postDeleteNoteCount: Int = col.db.queryScalar(noteCountPostDeleteSql, noteTypeId)
        Timber.d("postDeleteNoteCount would be %s", postDeleteNoteCount)
        if (preDeleteNoteCount != postDeleteNoteCount) {
            Timber.d("There will be orphan notes if these cards are deleted.")
            return null
        }
        Timber.d("Deleting these cards will not orphan notes.")
        return cids
    }

    // These are all legacy and should be removed when possible
    companion object {
        const val NOT_FOUND_NOTE_TYPE = -1L

        fun newTemplate(name: String): CardTemplate =
            CardTemplate(JSONObject(DEFAULT_TEMPLATE)).also {
                it.name = name
            }

        @Language("JSON")
        private const val DEFAULT_TEMPLATE =
            """{"name": "", "ord": null, "qfmt": "", "afmt": "", "did": null, "bqfmt": "","bafmt": "","bfont": "", "bsize": 0 }"""

        /** "Mapping of field name -> (ord, field).  */
        fun fieldMap(notetype: NotetypeJson): Map<String, Pair<Int, Field>> =
            notetype.fields.associateBy({ f -> f.name }, { f -> Pair(f.ord, f) })

        // not in anki
        fun isModelNew(notetype: NotetypeJson): Boolean = notetype.id == 0L

        fun _updateTemplOrds(notetype: NotetypeJson) {
            for ((i, template) in notetype.templates.withIndex()) {
                template.setOrd(i)
            }
        }
    }
}

/**
 * @return null if the key doesn't exist, or the value is not a long. The long value of the key
 * otherwise
 *
 * This better approximates `JSON.get` in the Python
 */
private fun Deck.getLongOrNull(key: String): Long? {
    if (!has(key)) {
        return null
    }
    try {
        return getLong(key)
    } catch (ex: Exception) {
        return null
    }
}

// These take and return bytes that the frontend TypeScript code will encode/decode.
fun Collection.getNotetypeNamesRaw(input: ByteArray): ByteArray = backend.getNotetypeNamesRaw(input)

fun Collection.getFieldNamesRaw(input: ByteArray): ByteArray = backend.getFieldNamesRaw(input)

fun Collection.updateNotetype(updatedNotetype: Notetype): OpChanges {
    val result = backend.updateNotetype(input = updatedNotetype)
    // Remove the specific notetype from cache to ensure consistency after updates
    // This allows the next access to fetch the fresh data from backend
    notetypes.removeFromCache(updatedNotetype.id)
    return result
}

fun Collection.removeNotetype(notetypeId: NoteTypeId): OpChanges {
    val result = backend.removeNotetype(ntid = notetypeId)
    // Remove the specific notetype from cache to ensure consistency after removal
    notetypes.removeFromCache(notetypeId)
    return result
}

fun Collection.addNotetype(newNotetype: Notetype): OpChangesWithId {
    val result = backend.addNotetype(input = newNotetype)
    // For add operations, we need to clear cache since we don't know the final ID beforehand
    // and the operation might affect note type ordering or other cached data
    notetypes.clearCache()
    return result
}

fun Collection.getNotetypeNameIdUseCount(): List<NotetypeNameIdUseCount> = backend.getNotetypeNamesAndCounts()

fun Collection.getNotetype(notetypeId: NoteTypeId): Notetype = backend.getNotetype(ntid = notetypeId)

fun Collection.getNotetypeNames(): List<NotetypeNameId> = backend.getNotetypeNames()

fun Collection.addNotetypeLegacy(json: ByteString): OpChangesWithId {
    val result = backend.addNotetypeLegacy(json = json)
    notetypes.clearCache()
    return result
}

fun Collection.getStockNotetype(kind: StockNotetype.Kind): NotetypeJson =
    NotetypeJson(fromJsonBytes(backend.getStockNotetypeLegacy(kind = kind)))

@NotInPyLib
fun getStockNotetypeKinds(): List<StockNotetype.Kind> = StockNotetype.Kind.entries.filter { it != StockNotetype.Kind.UNRECOGNIZED }

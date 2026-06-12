/*
 *
 * Copyright (c) 2015 Frank Oltmanns <frank.oltmanns@gmail.com>
 * Copyright (c) 2015 Timothy Rae <timothy.rae@gmail.com>
 * Copyright (c) 2016 Mark Carter <mark@marcardar.com>
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
package com.ichi2.anki.provider

import android.annotation.SuppressLint
import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.UriMatcher
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.MatrixCursor
import android.database.sqlite.SQLiteQueryBuilder
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import anki.scheduler.CardAnswer
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.BuildConfig
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.FlashCardsContract
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.common.utils.annotation.KotlinCleanup
import com.ichi2.anki.exception.StorageNotConfiguredException
import com.ichi2.anki.libanki.Card
import com.ichi2.anki.libanki.CardId
import com.ichi2.anki.libanki.CardTemplate
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.Deck
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.Decks
import com.ichi2.anki.libanki.Note
import com.ichi2.anki.libanki.NoteId
import com.ichi2.anki.libanki.NoteTypeId
import com.ichi2.anki.libanki.NoteTypeKind
import com.ichi2.anki.libanki.NotetypeJson
import com.ichi2.anki.libanki.Notetypes
import com.ichi2.anki.libanki.Sound.replaceWithSoundTags
import com.ichi2.anki.libanki.TemplateManager.TemplateRenderContext.TemplateRenderOutput
import com.ichi2.anki.libanki.Utils
import com.ichi2.anki.libanki.exception.ConfirmModSchemaException
import com.ichi2.anki.libanki.exception.EmptyMediaException
import com.ichi2.anki.libanki.sched.DeckNode
import com.ichi2.anki.observability.ChangeManager
import com.ichi2.utils.FileUtil
import com.ichi2.utils.FileUtil.internalizeUri
import com.ichi2.utils.Permissions.arePermissionsDefinedInManifest
import net.ankiweb.rsdroid.BackendException
import net.ankiweb.rsdroid.exceptions.BackendDeckIsFilteredException
import org.json.JSONArray
import org.json.JSONException
import timber.log.Timber
import java.io.File
import java.io.IOException

/**
 * Supported URIs:
 *
 * * .../notes (search for notes)
 * * .../notes/# (direct access to note)
 * * .../notes/#/cards (access cards of note)
 * * .../notes/#/cards/# (access specific card of note)
 * * .../models (search for models)
 * * .../models/# (direct access to model). String id 'current' can be used in place of # for the current note type
 * * .../models/#/fields (access to field definitions of a note type)
 * * .../models/#/templates (access to card templates of a note type)
 * * .../schedule (access the study schedule)
 * * .../decks (access the deck list)
 * * .../decks/# (access the specified deck)
 * * .../selected_deck (access the currently selected deck)
 * * .../media (add media files to anki collection.media)
 * * .../cards (search for cards)
 * * .../cards/# (direct access to card)
 *
 * Note that unlike Android's contact providers:
 *
 *  * it's not possible to access cards of more than one note at a time
 *  * it's not possible to access cards of a note without providing the note's ID
 *
 */

// TODO: Consider streaming Cursor results instead of materializing all rows
//  to avoid potential OOM for large queries.
//  Tracked in: https://github.com/ankidroid/Anki-Android/issues/20253
class CardContentProvider : ContentProvider() {
    companion object {
        // URI types
        private const val NOTES = 1000
        private const val NOTES_ID = 1001
        private const val NOTES_ID_CARDS = 1003
        private const val NOTES_ID_CARDS_ORD = 1004
        private const val NOTES_V2 = 1005
        private const val NOTE_TYPES = 2000
        private const val NOTE_TYPES_ID = 2001
        private const val NOTE_TYPES_ID_EMPTY_CARDS = 2002
        private const val NOTE_TYPES_ID_TEMPLATES = 2003
        private const val NOTE_TYPES_ID_TEMPLATES_ID = 2004
        private const val NOTE_TYPES_ID_FIELDS = 2005
        private const val SCHEDULE = 3000
        private const val DECKS = 4000
        private const val DECK_SELECTED = 4001
        private const val DECKS_ID = 4002
        private const val MEDIA = 5000
        private const val CARDS = 6000
        private const val CARD_ID = 6001
        private val sUriMatcher = UriMatcher(UriMatcher.NO_MATCH)

        /**
         * The names of the columns returned by this content provider differ slightly from the names
         * given of the database columns. This list is used to convert the column names used in a
         * projection by the user into DB column names.
         *
         *
         * This is currently only "_id" (projection) vs. "id" (Anki DB). But should probably be
         * applied to more columns. "MID", "USN", "MOD" are not really user friendly.
         */
        private val sDefaultNoteProjectionDBAccess = FlashCardsContract.Note.DEFAULT_PROJECTION.clone()

        private fun sanitizeNoteProjection(projection: Array<String>?): Array<String> {
            if (projection.isNullOrEmpty()) {
                return sDefaultNoteProjectionDBAccess
            }
            val sanitized = ArrayList<String>(projection.size)
            for (column in projection) {
                val idx = FlashCardsContract.Note.DEFAULT_PROJECTION.indexOf(column)
                if (idx >= 0) {
                    sanitized.add(sDefaultNoteProjectionDBAccess[idx])
                } else {
                    throw IllegalArgumentException("Unknown column $column")
                }
            }
            return sanitized.toTypedArray()
        }

        init {
            fun addUri(
                path: String,
                code: Int,
            ) = sUriMatcher.addURI(FlashCardsContract.AUTHORITY, path, code)
            // Here you can see all the URIs at a glance
            addUri("notes", NOTES)
            addUri("notes_v2", NOTES_V2)
            addUri("notes/#", NOTES_ID)
            addUri("notes/#/cards", NOTES_ID_CARDS)
            addUri("notes/#/cards/#", NOTES_ID_CARDS_ORD)
            addUri("models", NOTE_TYPES)
            addUri("models/*", NOTE_TYPES_ID) // the note type ID can also be "current"
            addUri("models/*/empty_cards", NOTE_TYPES_ID_EMPTY_CARDS)
            addUri("models/*/templates", NOTE_TYPES_ID_TEMPLATES)
            addUri("models/*/templates/#", NOTE_TYPES_ID_TEMPLATES_ID)
            addUri("models/*/fields", NOTE_TYPES_ID_FIELDS)
            addUri("schedule/", SCHEDULE)
            addUri("decks/", DECKS)
            addUri("decks/#", DECKS_ID)
            addUri("selected_deck/", DECK_SELECTED)
            addUri("media", MEDIA)
            addUri("cards", CARDS)
            addUri("cards/#", CARD_ID)

            for (idx in sDefaultNoteProjectionDBAccess.indices) {
                if (sDefaultNoteProjectionDBAccess[idx] == FlashCardsContract.Note._ID) {
                    sDefaultNoteProjectionDBAccess[idx] = "id as _id"
                }
            }
        }
    }

    override fun onCreate(): Boolean {
        // Initialize content provider on startup.
        Timber.d("CardContentProvider: onCreate")
        AnkiDroidApp.makeBackendUsable(context!!)
        return true
    }

    // keeps the nullability declared by the platform
    @Suppress("RedundantNullableReturnType")
    override fun getType(uri: Uri): String? {
        // Find out what data the user is requesting
        return when (sUriMatcher.match(uri)) {
            NOTES_V2, NOTES -> FlashCardsContract.Note.CONTENT_TYPE
            NOTES_ID -> FlashCardsContract.Note.CONTENT_ITEM_TYPE
            NOTES_ID_CARDS, NOTE_TYPES_ID_EMPTY_CARDS -> FlashCardsContract.Card.CONTENT_TYPE
            NOTES_ID_CARDS_ORD -> FlashCardsContract.Card.CONTENT_ITEM_TYPE
            NOTE_TYPES -> FlashCardsContract.Model.CONTENT_TYPE
            NOTE_TYPES_ID -> FlashCardsContract.Model.CONTENT_ITEM_TYPE
            NOTE_TYPES_ID_TEMPLATES -> FlashCardsContract.CardTemplate.CONTENT_TYPE
            NOTE_TYPES_ID_TEMPLATES_ID -> FlashCardsContract.CardTemplate.CONTENT_ITEM_TYPE
            SCHEDULE -> FlashCardsContract.ReviewInfo.CONTENT_TYPE
            DECKS, DECK_SELECTED, DECKS_ID -> FlashCardsContract.Deck.CONTENT_TYPE
            CARDS -> FlashCardsContract.Card.CONTENT_TYPE
            CARD_ID -> FlashCardsContract.Card.CONTENT_ITEM_TYPE
            else -> throw IllegalArgumentException("uri $uri is not supported")
        }
    }

    /**
     * Enforce permissions for queries and inserts on Android M and above.
     * @see knownRogueClient
     */
    private fun shouldEnforceQueryOrInsertSecurity(): Boolean = true

    /**
     * Enforce permissions for all updates on Android M and above.
     * @see knownRogueClient
     */
    private fun shouldEnforceUpdateSecurity(uri: Uri): Boolean = true

    /**
     * The collection, opened if necessary.
     *
     * @throws IllegalStateException if the user has not yet chosen where the collection is
     * stored.
     */
    private fun getColUnsafe(): Collection =
        try {
            CollectionManager.getColUnsafe()
        } catch (e: StorageNotConfiguredException) {
            // StorageNotConfiguredException is not supported by Parcel.writeException
            throw IllegalStateException("AnkiDroid storage is not configured", e)
        }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        order: String?,
    ): Cursor? {
        if (!hasReadWritePermission() && shouldEnforceQueryOrInsertSecurity()) {
            throwSecurityException("query", uri)
        }
        val col = getColUnsafe()
        Timber.d(getLogMessage("query", uri))

        // Find out what data the user is requesting
        return when (sUriMatcher.match(uri)) {
            NOTES_V2 -> {
                // Search for notes using direct SQL query
                val proj = sanitizeNoteProjection(projection)
                val sql = SQLiteQueryBuilder.buildQueryString(false, "notes", proj, selection, null, null, order, null)
                col.db.query(sql, *(selectionArgs ?: arrayOf()))
            }
            NOTES -> {
                // Search for notes using the libanki browser syntax
                val proj = sanitizeNoteProjection(projection)
                val query = selection ?: ""
                val noteIds = col.findNotes(query)
                if (noteIds.isNotEmpty()) {
                    val sel = "id in (${noteIds.joinToString(",")})"
                    val sql = SQLiteQueryBuilder.buildQueryString(false, "notes", proj, sel, null, null, order, null)
                    col.db.query(sql)
                } else {
                    null
                }
            }
            NOTES_ID -> {
                // Direct access note with specific ID
                val noteId = uri.pathSegments[1]
                val proj = sanitizeNoteProjection(projection)
                val sql = SQLiteQueryBuilder.buildQueryString(false, "notes", proj, "id=?", null, null, order, null)
                col.db.query(sql, noteId)
            }
            NOTES_ID_CARDS -> {
                val currentNote = getNoteFromUri(uri, col)
                val columns = projection ?: FlashCardsContract.Card.DEFAULT_PROJECTION
                val rv = MatrixCursor(columns, 1)
                for (currentCard: Card in currentNote.cards(col)) {
                    addCardToCursor(currentCard, rv, col, columns)
                }
                rv
            }
            NOTES_ID_CARDS_ORD -> {
                val currentCard = getCardFromUri(uri, col)
                val columns = projection ?: FlashCardsContract.Card.DEFAULT_PROJECTION
                val rv = MatrixCursor(columns, 1)
                addCardToCursor(currentCard, rv, col, columns)
                rv
            }
            NOTE_TYPES -> {
                val columns = projection ?: FlashCardsContract.Model.DEFAULT_PROJECTION
                val rv = MatrixCursor(columns, 1)
                for (noteTypeId: NoteTypeId in col.notetypes.ids()) {
                    addNoteTypeToCursor(noteTypeId, col.notetypes, rv, columns)
                }
                rv
            }
            NOTE_TYPES_ID -> {
                val noteTypeId = getNoteTypeIdFromUri(uri, col)
                val columns = projection ?: FlashCardsContract.Model.DEFAULT_PROJECTION
                val rv = MatrixCursor(columns, 1)
                addNoteTypeToCursor(noteTypeId, col.notetypes, rv, columns)
                rv
            }
            NOTE_TYPES_ID_TEMPLATES -> {
                // Direct access note type templates
                val currentNoteType = col.notetypes.get(getNoteTypeIdFromUri(uri, col))
                val columns = projection ?: FlashCardsContract.CardTemplate.DEFAULT_PROJECTION
                val rv = MatrixCursor(columns, 1)
                try {
                    for ((ord, template) in currentNoteType!!.templates.withIndex()) {
                        addTemplateToCursor(template, currentNoteType, ord + 1, col.notetypes, rv, columns)
                    }
                } catch (e: JSONException) {
                    throw IllegalArgumentException("Note type is malformed", e)
                }
                rv
            }
            NOTE_TYPES_ID_TEMPLATES_ID -> {
                // Direct access note type template with specific ID
                val ord = uri.lastPathSegment!!.toInt()
                val currentNoteType = col.notetypes.get(getNoteTypeIdFromUri(uri, col))
                val columns = projection ?: FlashCardsContract.CardTemplate.DEFAULT_PROJECTION
                val rv = MatrixCursor(columns, 1)
                try {
                    val template = getTemplateFromUri(uri, col)
                    addTemplateToCursor(template, currentNoteType, ord + 1, col.notetypes, rv, columns)
                } catch (e: JSONException) {
                    throw IllegalArgumentException("Note type is malformed", e)
                }
                rv
            }
            SCHEDULE -> {
                val columns = projection ?: FlashCardsContract.ReviewInfo.DEFAULT_PROJECTION
                val rv = MatrixCursor(columns, 1)
                val selectedDeckBeforeQuery = col.decks.selected()
                var deckIdOfTemporarilySelectedDeck: Long = -1
                var limit = 1 // the number of scheduled cards to return
                var selectionArgIndex = 0

                // parsing the selection arguments
                if (selection != null) {
                    val args = selection.split(",").toTypedArray() // split selection to get arguments like "limit=?"
                    for (arg: String in args) {
                        val keyAndValue = arg.split("=").toTypedArray() // split arguments into key ("limit") and value ("?")
                        try {
                            // check if value is a placeholder ("?"), if so replace with the next value of selectionArgs
                            val value =
                                if ("?" == keyAndValue[1].trim()) {
                                    selectionArgs!![selectionArgIndex++]
                                } else {
                                    keyAndValue[1]
                                }
                            if ("limit" == keyAndValue[0].trim()) {
                                limit = value.toInt()
                            } else if ("deckID" == keyAndValue[0].trim()) {
                                deckIdOfTemporarilySelectedDeck = value.toLong()
                                if (!selectDeckWithCheck(col, deckIdOfTemporarilySelectedDeck)) {
                                    return rv // if the provided deckID is wrong, return empty cursor.
                                }
                            }
                        } catch (nfe: NumberFormatException) {
                            Timber.w(nfe)
                        }
                    }
                }

                // retrieve the number of cards provided by the selection parameter "limit"
                val cards =
                    col.backend
                        .getQueuedCards(
                            fetchLimit = limit,
                            intradayLearningOnly = false,
                        ).cardsList
                        .map { Card(it.card) }

                val buttonCount = 4
                var k = 0
                while (k < limit) {
                    val currentCard = cards.getOrNull(k) ?: break
                    val buttonTexts = JSONArray()
                    var i = 0
                    while (i < buttonCount) {
                        buttonTexts.put(col.sched.nextIvlStr(currentCard, CardAnswer.Rating.forNumber(i)))
                        i++
                    }
                    addReviewInfoToCursor(currentCard, buttonTexts, buttonCount, rv, col, columns)
                    k++
                }
                if (deckIdOfTemporarilySelectedDeck != -1L) { // if the selected deck was changed
                    // change the selected deck back to the one it was before the query
                    col.decks.select(selectedDeckBeforeQuery)
                }
                rv
            }
            DECKS -> {
                val columns = projection ?: FlashCardsContract.Deck.DEFAULT_PROJECTION
                val allDecks = col.sched.deckDueTree()
                val rv = MatrixCursor(columns, 1)
                allDecks.forEach {
                    addDeckToCursor(
                        it.did,
                        it.fullDeckName,
                        getDeckCountsFromDueTreeNode(it),
                        rv,
                        col,
                        columns,
                    )
                }
                rv
            }
            DECKS_ID -> {
                // Direct access deck
                val columns = projection ?: FlashCardsContract.Deck.DEFAULT_PROJECTION
                val rv = MatrixCursor(columns, 1)
                val allDecks = col.sched.deckDueTree()
                val desiredDeckId = uri.pathSegments[1].toLong()
                allDecks.find(desiredDeckId)?.let {
                    addDeckToCursor(it.did, it.fullDeckName, getDeckCountsFromDueTreeNode(it), rv, col, columns)
                }
                rv
            }
            DECK_SELECTED -> {
                val id = col.decks.selected()
                val name = col.decks.name(id)
                val columns = projection ?: FlashCardsContract.Deck.DEFAULT_PROJECTION
                val rv = MatrixCursor(columns, 1)
                val counts = JSONArray(listOf(col.sched.counts()))
                addDeckToCursor(id, name, counts, rv, col, columns)
                rv
            }
            CARDS -> {
                // Search for cards using Anki browser syntax
                val columns = projection ?: FlashCardsContract.Card.DEFAULT_PROJECTION
                val query = selection ?: ""

                val cardIds =
                    try {
                        col.findCards(query)
                    } catch (e: BackendException.BackendSearchException) {
                        throw IllegalArgumentException(
                            "Invalid Anki search query: \"$query\"",
                            e,
                        )
                    }

                // Fast path: Only if _id is requested
                val onlyRequestingId = columns.singleOrNull() == FlashCardsContract.Card._ID

                if (onlyRequestingId) {
                    // Return IDs without fetching card objects
                    val rv = MatrixCursor(columns, cardIds.size)
                    for (cardId in cardIds) {
                        rv.newRow().add(cardId)
                    }
                    rv
                } else {
                    // Get all requested fields
                    val rv = MatrixCursor(columns, cardIds.size)
                    for (cardId in cardIds) {
                        val card = col.getCard(cardId)
                        addCardToCursor(card, rv, col, columns)
                    }
                    rv
                }
            }
            CARD_ID -> {
                // Direct access to specific card by ID
                val cardId = uri.pathSegments[1].toLong()
                val columns = projection ?: FlashCardsContract.Card.DEFAULT_PROJECTION
                val rv = MatrixCursor(columns, 1)
                val card = col.getCard(cardId)
                addCardToCursor(card, rv, col, columns)
                rv
            }
            else -> throw IllegalArgumentException("uri $uri is not supported")
        }
    }

    private fun getDeckCountsFromDueTreeNode(deck: DeckNode): JSONArray =
        JSONArray().apply {
            put(deck.lrnCount)
            put(deck.revCount)
            put(deck.newCount)
        }

    @SuppressLint("CheckResult")
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?,
    ): Int {
        if (!hasReadWritePermission() && shouldEnforceUpdateSecurity(uri)) {
            throwSecurityException("update", uri)
        }
        val col = getColUnsafe()
        Timber.d(getLogMessage("update", uri))

        // Find out what data the user is requesting
        val match = sUriMatcher.match(uri)
        var updated = 0 // Number of updated entries (return value)
        when (match) {
            NOTES_V2, NOTES -> throw IllegalArgumentException("Not possible to update notes directly (only through data URI)")
            NOTES_ID -> {
                /* Direct access note details
                 */
                val currentNote = getNoteFromUri(uri, col)
                // the key of the ContentValues contains the column name
                // the value of the ContentValues contains the row value.
                val valueSet = values!!.valueSet()
                for ((key, tags) in valueSet) {
                    // when the client does not specify FLDS, then don't update the FLDS
                    when (key) {
                        FlashCardsContract.Note.FLDS -> {
                            // Update FLDS
                            Timber.d("CardContentProvider: flds update...")
                            val newFldsEncoded = tags as String
                            val flds = Utils.splitFields(newFldsEncoded)
                            // Check that correct number of flds specified
                            require(flds.size == currentNote.fields.size) { "Incorrect flds argument : $newFldsEncoded" }
                            // Update the note
                            var idx = 0
                            while (idx < flds.size) {
                                currentNote.setField(idx, flds[idx])
                                idx++
                            }
                            updated++
                        }
                        FlashCardsContract.Note.TAGS -> {
                            // Update tags
                            Timber.d("CardContentProvider: tags update...")
                            if (tags != null) {
                                currentNote.setTagsFromStr(col, tags.toString())
                            }
                            updated++
                        }
                        else -> {
                            // Unsupported column
                            throw IllegalArgumentException("Unsupported column: $key")
                        }
                    }
                }
                Timber.d("CardContentProvider: Saving note...")
                col.updateNote(currentNote)
            }
            NOTES_ID_CARDS -> throw UnsupportedOperationException("Not yet implemented")
            NOTES_ID_CARDS_ORD -> {
                val currentCard = getCardFromUri(uri, col)
                var isDeckUpdate = false
                var did = Decks.NOT_FOUND_DECK_ID
                // the key of the ContentValues contains the column name
                // the value of the ContentValues contains the row value.
                val valueSet = values!!.valueSet()
                for ((key) in valueSet) {
                    // Only updates on deck id is supported
                    isDeckUpdate = key == FlashCardsContract.Card.DECK_ID
                    did = values.getAsLong(key)
                }
                require(!col.decks.isFiltered(did)) { "Cards cannot be moved to a filtered deck" }
                /* now update the card
                 */
                if (isDeckUpdate && did >= 0) {
                    Timber.d("CardContentProvider: Moving card to other deck...")
                    currentCard.did = did
                    col.updateCard(currentCard)

                    updated++
                } else {
                    // User tries an operation that is not (yet?) supported.
                    throw IllegalArgumentException("Currently only updates of decks are supported")
                }
            }
            NOTE_TYPES -> throw IllegalArgumentException("Cannot update models in bulk")
            NOTE_TYPES_ID -> {
                // Get the input parameters
                val newNoteTypeName = values!!.getAsString(FlashCardsContract.Model.NAME)
                val newCss = values.getAsString(FlashCardsContract.Model.CSS)
                val newDid = values.getAsString(FlashCardsContract.Model.DECK_ID)
                val newFieldList = values.getAsString(FlashCardsContract.Model.FIELD_NAMES)
                require(newFieldList == null) {
                    // Changing the field names would require a full-sync
                    "Field names cannot be changed via provider"
                }
                val newSortf = values.getAsInteger(FlashCardsContract.Model.SORT_FIELD_INDEX)
                val newType = values.getAsInteger(FlashCardsContract.Model.TYPE)?.let(NoteTypeKind::fromCode)
                val newLatexPost = values.getAsString(FlashCardsContract.Model.LATEX_POST)
                val newLatexPre = values.getAsString(FlashCardsContract.Model.LATEX_PRE)
                // Get the original note JSON
                val noteType = col.notetypes.get(getNoteTypeIdFromUri(uri, col))
                try {
                    // Update noteType name and/or css
                    if (newNoteTypeName != null) {
                        noteType!!.name = newNoteTypeName
                        updated++
                    }
                    if (newCss != null) {
                        noteType!!.css = newCss
                        updated++
                    }
                    if (newDid != null) {
                        if (col.decks.isFiltered(newDid.toLong())) {
                            throw IllegalArgumentException("Cannot set a filtered deck as default deck for a noteType")
                        }
                        noteType!!.did = newDid.toLong()
                        updated++
                    }
                    if (newSortf != null) {
                        noteType!!.sortf = newSortf
                        updated++
                    }
                    if (newType != null) {
                        noteType!!.type = newType
                        updated++
                    }
                    if (newLatexPost != null) {
                        noteType!!.latexPost = newLatexPost
                        updated++
                    }
                    if (newLatexPre != null) {
                        noteType!!.latexPre = newLatexPre
                        updated++
                    }
                    col.notetypes.save(noteType!!)
                } catch (e: JSONException) {
                    Timber.e(e, "JSONException updating noteType")
                }
            }
            NOTE_TYPES_ID_TEMPLATES -> throw IllegalArgumentException("Cannot update templates in bulk")
            NOTE_TYPES_ID_TEMPLATES_ID -> {
                val noteTypeId = values!!.getAsLong(FlashCardsContract.CardTemplate.MODEL_ID)
                val ord = values.getAsInteger(FlashCardsContract.CardTemplate.ORD)
                val name = values.getAsString(FlashCardsContract.CardTemplate.NAME)
                val qfmt = values.getAsString(FlashCardsContract.CardTemplate.QUESTION_FORMAT)
                val afmt = values.getAsString(FlashCardsContract.CardTemplate.ANSWER_FORMAT)
                val bqfmt = values.getAsString(FlashCardsContract.CardTemplate.BROWSER_QUESTION_FORMAT)
                val bafmt = values.getAsString(FlashCardsContract.CardTemplate.BROWSER_ANSWER_FORMAT)
                // Throw exception if read-only fields are included
                if (noteTypeId != null || ord != null) {
                    throw IllegalArgumentException("Updates to mid or ord are not allowed")
                }
                // Update the noteType
                try {
                    val templateOrd = uri.lastPathSegment!!.toInt()
                    val existingNoteType = col.notetypes.get(getNoteTypeIdFromUri(uri, col))
                    val templates = existingNoteType!!.templates
                    val template = templates[templateOrd]
                    if (name != null) {
                        template.name = name
                        updated++
                    }
                    if (qfmt != null) {
                        template.qfmt = qfmt
                        updated++
                    }
                    if (afmt != null) {
                        template.afmt = afmt
                        updated++
                    }
                    if (bqfmt != null) {
                        template.bqfmt = bqfmt
                        updated++
                    }
                    if (bafmt != null) {
                        template.bafmt = bafmt
                        updated++
                    }
                    // Save the note type
                    templates[templateOrd] = template
                    existingNoteType.templates = templates
                    col.notetypes.save(existingNoteType)
                } catch (e: JSONException) {
                    throw IllegalArgumentException("Note type is malformed", e)
                }
            }
            SCHEDULE -> {
                val valueSet = values!!.valueSet()
                var cardOrd = -1
                var noteId: NoteId = -1

                @Suppress("DEPRECATION")
                var ease: com.ichi2.anki.libanki.sched.Ease? = null
                var timeTaken: Long = -1
                var bury = -1
                var suspend = -1
                @Suppress("DEPRECATION")
                for ((key) in valueSet) {
                    when (key) {
                        FlashCardsContract.ReviewInfo.NOTE_ID -> noteId = values.getAsLong(key)
                        FlashCardsContract.ReviewInfo.CARD_ORD -> cardOrd = values.getAsInteger(key)
                        FlashCardsContract.ReviewInfo.EASE ->
                            ease =
                                com.ichi2.anki.libanki.sched.Ease
                                    .fromValue(values.getAsInteger(key))

                        FlashCardsContract.ReviewInfo.TIME_TAKEN ->
                            timeTaken =
                                values.getAsLong(key)

                        FlashCardsContract.ReviewInfo.BURY -> bury = values.getAsInteger(key)
                        FlashCardsContract.ReviewInfo.SUSPEND -> suspend = values.getAsInteger(key)
                    }
                }
                if (cardOrd != -1 && noteId != -1L) {
                    val cardToAnswer: Card = getCard(noteId, cardOrd, col)
                    @Suppress("SENSELESS_COMPARISON")
                    @KotlinCleanup("based on getCard() method, cardToAnswer does seem to be not null")
                    if (cardToAnswer != null) {
                        if (bury == 1) {
                            // bury card
                            buryOrSuspendCard(col, cardToAnswer, true)
                        } else if (suspend == 1) {
                            // suspend card
                            buryOrSuspendCard(col, cardToAnswer, false)
                        } else {
                            answerCard(col, cardToAnswer, ease!!, timeTaken)
                        }
                        updated++
                    } else {
                        Timber.e(
                            "Requested card with noteId %d and cardOrd %d was not found. Either the provided " +
                                "noteId/cardOrd were wrong or the card has been deleted in the meantime.",
                            noteId,
                            cardOrd,
                        )
                    }
                }
            }
            DECKS -> throw IllegalArgumentException("Can't update decks in bulk")
            DECKS_ID -> throw UnsupportedOperationException("Not yet implemented")
            DECK_SELECTED -> {
                val valueSet = values!!.valueSet()
                for ((key) in valueSet) {
                    if (key == FlashCardsContract.Deck.DECK_ID) {
                        val deckId = values.getAsLong(key)
                        if (selectDeckWithCheck(col, deckId)) {
                            updated++
                        }
                    }
                }
            }
            else -> throw IllegalArgumentException("uri $uri is not supported")
        }

        if (updated > 0) {
            notifyAllValuesChanged()
        }

        return updated
    }

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<String>?,
    ): Int {
        if (!hasReadWritePermission()) {
            throwSecurityException("delete", uri)
        }
        val col = getColUnsafe()
        Timber.d(getLogMessage("delete", uri))

        val deletedCount =
            when (sUriMatcher.match(uri)) {
                NOTES_ID -> {
                    col.removeNotes(noteIds = listOf(uri.pathSegments[1].toLong())).count
                }
                NOTE_TYPES_ID_EMPTY_CARDS -> {
                    val noteType = col.notetypes.get(getNoteTypeIdFromUri(uri, col)) ?: return -1
                    val cardIdsToRemove = noteType.getEmptyCardIds(col)

                    col.removeCardsAndOrphanedNotes(cardIdsToRemove).count
                }
                else -> throw UnsupportedOperationException()
            }

        if (deletedCount > 0) {
            notifyAllValuesChanged()
        }

        return deletedCount
    }

    /**
     * This can be used to insert multiple notes into a single deck. The deck is specified as a query parameter.
     *
     * For example: content://com.ichi2.anki.flashcards/notes?deckId=1234567890123
     *
     * @param uri content Uri
     * @param values for notes uri, it is acceptable for values to contain null items. Such items will be skipped
     * @return number of notes added (does not include existing notes that were updated)
     */
    override fun bulkInsert(
        uri: Uri,
        values: Array<ContentValues>,
    ): Int {
        if (!hasReadWritePermission() && shouldEnforceQueryOrInsertSecurity()) {
            throwSecurityException("bulkInsert", uri)
        }

        // by default, #bulkInsert simply calls insert for each item in #values
        // but in some cases, we want to override this behavior
        val match = sUriMatcher.match(uri)
        val deckIdStr = uri.getQueryParameter(FlashCardsContract.Note.DECK_ID_QUERY_PARAM)

        val deckId =
            try {
                deckIdStr?.toLong()
            } catch (e: NumberFormatException) {
                Timber.d(e, "Invalid %s: %s", FlashCardsContract.Note.DECK_ID_QUERY_PARAM, deckIdStr)
                null
            }

        val insertedCount =
            if (match == NOTES && deckId != null) {
                bulkInsertNotes(values, deckId)
            } else {
                // deckId not specified, so default to #super implementation (as in spec version 1)
                super.bulkInsert(uri, values)
            }

        if (insertedCount > 0) {
            notifyAllValuesChanged()
        }

        return insertedCount
    }

    /**
     * This implementation optimizes for when the notes are grouped according to note type.
     */
    private fun bulkInsertNotes(
        valuesArr: Array<ContentValues>?,
        deckId: DeckId,
    ): Int {
        if (valuesArr.isNullOrEmpty()) {
            return 0
        }
        val col = getColUnsafe()
        if (col.decks.isFiltered(deckId)) {
            throw IllegalArgumentException("A filtered deck cannot be specified as the deck in bulkInsertNotes")
        }
        Timber.d("bulkInsertNotes: %d items.\n%s", valuesArr.size, getLogMessage("bulkInsert", null))

        var result = 0
        for (i in valuesArr.indices) {
            val values: ContentValues = valuesArr[i]
            val flds = values.getAsString(FlashCardsContract.Note.FLDS) ?: continue
//                val allowEmpty = AllowEmpty.fromBoolean(values.getAsBoolean(FlashCardsContract.Note.ALLOW_EMPTY))
            val thisNoteTypeId = values.getAsLong(FlashCardsContract.Note.MID)
            if (thisNoteTypeId == null || thisNoteTypeId < 0) {
                Timber.d("Unable to get note type at index: %d", i)
                continue
            }
            val fldsArray = Utils.splitFields(flds)
            // Create empty note
            val newNote = Note.fromNotetypeId(col, thisNoteTypeId)
            // Set fields
            // Check that correct number of flds specified
            if (fldsArray.size != newNote.fields.size) {
                throw IllegalArgumentException("Incorrect flds argument : $flds")
            }
            for (idx in fldsArray.indices) {
                newNote.setField(idx, fldsArray[idx])
            }
            // Set tags
            val tags = values.getAsString(FlashCardsContract.Note.TAGS)
            if (tags != null) {
                newNote.setTagsFromStr(col, tags)
            }
            // Add to collection
            col.addNote(newNote, deckId)
            for (card: Card in newNote.cards(col)) {
                card.did = deckId
                col.updateCard(card)
            }
            result++
        }

        return result
    }

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? {
        if (!hasReadWritePermission() && shouldEnforceQueryOrInsertSecurity()) {
            throwSecurityException("insert", uri)
        }
        val col = getColUnsafe()
        Timber.d(getLogMessage("insert", uri))

        // Find out what data the user is requesting
        val insertedUri =
            when (sUriMatcher.match(uri)) {
                NOTES -> {
                /* Insert new note with specified fields and tags
                 */
                    val noteTypeId = values!!.getAsLong(FlashCardsContract.Note.MID)
                    val flds = values.getAsString(FlashCardsContract.Note.FLDS)
                    val tags = values.getAsString(FlashCardsContract.Note.TAGS)
//                val allowEmpty = AllowEmpty.fromBoolean(values.getAsBoolean(FlashCardsContract.Note.ALLOW_EMPTY))
                    // Create empty note
                    val newNote = Note.fromNotetypeId(col, noteTypeId)
                    // Set fields
                    val fldsArray = Utils.splitFields(flds)
                    // Check that correct number of flds specified
                    if (fldsArray.size != newNote.fields.size) {
                        throw IllegalArgumentException("Incorrect flds argument : $flds")
                    }
                    var idx = 0
                    while (idx < fldsArray.size) {
                        newNote.setField(idx, fldsArray[idx])
                        idx++
                    }
                    // Set tags
                    if (tags != null) {
                        newNote.setTagsFromStr(col, tags)
                    }
                    // Add to collection
                    col.addNote(newNote, newNote.notetype.did)

                    Uri.withAppendedPath(FlashCardsContract.Note.CONTENT_URI, newNote.id.toString())
                }
                NOTES_ID -> throw IllegalArgumentException("Not possible to insert note with specific ID")
                NOTES_ID_CARDS, NOTES_ID_CARDS_ORD -> throw IllegalArgumentException(
                    "Not possible to insert cards directly (only through NOTES)",
                )
                NOTE_TYPES -> {
                    // Get input arguments
                    val noteTypeName = values!!.getAsString(FlashCardsContract.Model.NAME)
                    val css = values.getAsString(FlashCardsContract.Model.CSS)
                    val did = values.getAsLong(FlashCardsContract.Model.DECK_ID)
                    val fieldNames = values.getAsString(FlashCardsContract.Model.FIELD_NAMES)
                    val numCards = values.getAsInteger(FlashCardsContract.Model.NUM_CARDS)
                    val sortf = values.getAsInteger(FlashCardsContract.Model.SORT_FIELD_INDEX)
                    val type = values.getAsInteger(FlashCardsContract.Model.TYPE)?.let(NoteTypeKind::fromCode)
                    val latexPost = values.getAsString(FlashCardsContract.Model.LATEX_POST)
                    val latexPre = values.getAsString(FlashCardsContract.Model.LATEX_PRE)
                    // Throw exception if required fields empty
                    if (noteTypeName == null || fieldNames == null || numCards == null) {
                        throw IllegalArgumentException("Note type name, field_names, and num_cards can't be empty")
                    }
                    if (did != null && col.decks.isFiltered(did)) {
                        throw IllegalArgumentException("Cannot set a filtered deck as default deck for a note type")
                    }
                    // Create a new note type
                    val newNoteType = col.notetypes.new(noteTypeName)
                    try {
                        // Add the fields
                        val allFields = Utils.splitFields(fieldNames)
                        for (f: String? in allFields) {
                            col.notetypes.addFieldLegacy(newNoteType, col.notetypes.newField(f!!))
                        }
                        // Add some empty card templates
                        var idx = 0
                        while (idx < numCards) {
                            val cardName = CollectionManager.TR.cardTemplatesCard(idx + 1)
                            val t = Notetypes.newTemplate(cardName)
                            t.qfmt = "{{${allFields[0]}}}"
                            var answerField: String? = allFields[0]
                            if (allFields.size > 1) {
                                answerField = allFields[1]
                            }
                            t.afmt = "{{FrontSide}}\\n\\n<hr id=answer>\\n\\n{{$answerField}}"
                            col.notetypes.addTemplate(newNoteType, t)
                            idx++
                        }
                        // Add the CSS if specified
                        if (css != null) {
                            newNoteType.css = css
                        }
                        // Add the did if specified
                        if (did != null) {
                            newNoteType.did = did
                        }
                        if (sortf != null && sortf < allFields.size) {
                            newNoteType.sortf = sortf
                        }
                        if (type != null) {
                            newNoteType.type = type
                        }
                        if (latexPost != null) {
                            newNoteType.latexPost = latexPost
                        }
                        if (latexPre != null) {
                            newNoteType.latexPre = latexPre
                        }
                        // Add the note type to collection (from this point on edits will require a full-sync)
                        col.notetypes.add(newNoteType)

                        // Get the mid and return a URI
                        val noteTypeId = newNoteType.id.toString()
                        Uri.withAppendedPath(FlashCardsContract.Model.CONTENT_URI, noteTypeId)
                    } catch (e: JSONException) {
                        Timber.e(e, "Could not set a field of new note type %s", noteTypeName)
                        null
                    }
                }
                NOTE_TYPES_ID -> throw IllegalArgumentException("Not possible to insert note type with specific ID")
                NOTE_TYPES_ID_TEMPLATES -> {
                    run {
                        val noteTypeId: NoteTypeId = getNoteTypeIdFromUri(uri, col)
                        val existingNoteType: NotetypeJson =
                            col.notetypes.get(noteTypeId)
                                ?: throw IllegalArgumentException("note type missing: $noteTypeId")
                        val name: String = values!!.getAsString(FlashCardsContract.CardTemplate.NAME)
                        val qfmt: String = values.getAsString(FlashCardsContract.CardTemplate.QUESTION_FORMAT)
                        val afmt: String = values.getAsString(FlashCardsContract.CardTemplate.ANSWER_FORMAT)
                        val bqfmt: String = values.getAsString(FlashCardsContract.CardTemplate.BROWSER_QUESTION_FORMAT)
                        val bafmt: String = values.getAsString(FlashCardsContract.CardTemplate.BROWSER_ANSWER_FORMAT)
                        try {
                            var t: CardTemplate =
                                Notetypes.newTemplate(name).also { tmpl ->
                                    tmpl.qfmt = qfmt
                                    tmpl.afmt = afmt
                                    tmpl.bqfmt = bqfmt
                                    tmpl.bafmt = bafmt
                                }
                            col.notetypes.addTemplate(existingNoteType, t)
                            col.notetypes.update(existingNoteType)
                            t = existingNoteType.templates.last()
                            ContentUris.withAppendedId(uri, t.ord.toLong())
                        } catch (e: ConfirmModSchemaException) {
                            throw IllegalArgumentException("Unable to add template without user requesting/accepting full-sync", e)
                        } catch (e: JSONException) {
                            throw IllegalArgumentException("Unable to get ord from new template", e)
                        }
                    }
                }
                NOTE_TYPES_ID_TEMPLATES_ID -> throw IllegalArgumentException("Not possible to insert template with specific ORD")
                NOTE_TYPES_ID_FIELDS -> {
                    run {
                        val noteTypeId: NoteTypeId = getNoteTypeIdFromUri(uri, col)
                        val existingNoteType: NotetypeJson =
                            col.notetypes.get(noteTypeId)
                                ?: throw IllegalArgumentException("note type missing: $noteTypeId")
                        val name: String =
                            values!!.getAsString(FlashCardsContract.Model.FIELD_NAME)
                                ?: throw IllegalArgumentException("field name missing for note type: $noteTypeId")
                        val field = col.notetypes.newField(name)
                        try {
                            col.notetypes.addFieldLegacy(existingNoteType, field)

                            val flds = existingNoteType.fields
                            ContentUris.withAppendedId(uri, (flds.length() - 1).toLong())
                        } catch (e: ConfirmModSchemaException) {
                            throw IllegalArgumentException("Unable to insert field: $name", e)
                        } catch (e: JSONException) {
                            throw IllegalArgumentException("Unable to get newly created field: $name", e)
                        }
                    }
                }
                SCHEDULE -> throw IllegalArgumentException("Not possible to perform insert operation on schedule")
                DECKS -> {
                    // Insert new deck with specified name
                    val deckName = values!!.getAsString(FlashCardsContract.Deck.DECK_NAME)
                    var did = col.decks.idForName(deckName)
                    if (did != null) {
                        throw IllegalArgumentException("Deck name already exists: $deckName")
                    }
                    if (!Decks.isValidDeckName(deckName)) {
                        throw IllegalArgumentException("Invalid deck name '$deckName'")
                    }
                    try {
                        did = col.decks.id(deckName)
                    } catch (filteredSubdeck: BackendDeckIsFilteredException) {
                        throw IllegalArgumentException(filteredSubdeck.message)
                    }
                    val deck: Deck = col.decks.getLegacy(did)!!
                    val deckDesc = values.getAsString(FlashCardsContract.Deck.DECK_DESC)
                    if (deckDesc != null) {
                        deck.description = deckDesc
                        col.decks.save(deck)
                    }
                    Uri.withAppendedPath(FlashCardsContract.Deck.CONTENT_ALL_URI, did.toString())
                }
                DECK_SELECTED -> throw IllegalArgumentException("Selected deck can only be queried and updated")
                DECKS_ID -> throw IllegalArgumentException("Not possible to insert deck with specific ID")
                MEDIA ->
                    // insert a media file
                    // contentvalue should have data and preferredFileName values
                    insertMediaFile(values, col)
                else -> throw IllegalArgumentException("uri $uri is not supported")
            }

        if (insertedUri != null) {
            notifyAllValuesChanged()
        }

        return insertedUri
    }

    private fun insertMediaFile(
        values: ContentValues?,
        col: Collection,
    ): Uri? {
        // Insert media file using libanki.Media.addFile and return Uri for the inserted file.
        val fileUri = values!!.getAsString(FlashCardsContract.AnkiMedia.FILE_URI).toUri()
        val preferredName = values.getAsString(FlashCardsContract.AnkiMedia.PREFERRED_NAME)
        return try {
            val cR = context!!.contentResolver
            val media = col.media
            // idea, open input stream and save to cache directory, then
            // pass this (hopefully temporary) file to the media.addFile function.
            val fileMimeType = MimeTypeMap.getSingleton().getExtensionFromMimeType(cR.getType(fileUri)) // return eg "jpeg"
            // should we be enforcing strict mimetypes? which types?
            val tempMediaDir = FileUtil.getAnkiCacheDirectory(context!!, "temp-media")
            if (tempMediaDir == null) {
                Timber.e("insertMediaFile() failed to get cache directory")
                return null
            }
            val tempFile: File
            try {
                tempFile =
                    File.createTempFile(
                        // the beginning of the filename.
                        preferredName + "_",
                        // this is the extension, if null, '.tmp' is used, need to get the extension from MIME type?
                        ".$fileMimeType",
                        File(tempMediaDir),
                    )
                tempFile.deleteOnExit()
            } catch (e: Exception) {
                Timber.w(e, "Could not create temporary media file. ")
                return null
            }
            internalizeUri(fileUri, tempFile, cR)
            val fname = media.addFile(tempFile)
            Timber.d("insert -> MEDIA: fname = %s", fname)
            val f = File(fname)
            Timber.d("insert -> MEDIA: f = %s", f)
            val uriFromF = Uri.fromFile(f)
            Timber.d("insert -> MEDIA: uriFromF = %s", uriFromF)
            Uri.fromFile(File(fname))
        } catch (e: OutOfMemoryError) {
            Timber.e(e, "insert failed from %s", fileUri)
            null
        } catch (e: IOException) {
            Timber.w(e, "insert failed from %s", fileUri)
            null
        } catch (e: EmptyMediaException) {
            Timber.w(e, "insert failed from %s", fileUri)
            null
        }
    }

    private fun addNoteTypeToCursor(
        noteTypeId: NoteTypeId,
        notetypes: Notetypes,
        rv: MatrixCursor,
        columns: Array<String>,
    ) {
        val noteType = notetypes.get(noteTypeId)!!
        val rb = rv.newRow()
        try {
            for (column in columns) {
                when (column) {
                    FlashCardsContract.Model._ID -> rb.add(noteTypeId)
                    FlashCardsContract.Model.NAME -> rb.add(noteType.name)
                    FlashCardsContract.Model.FIELD_NAMES -> {
                        @KotlinCleanup("maybe jsonObject.fieldsNames. Difference: optString vs get")
                        val flds = noteType.fields
                        val allFlds = arrayOfNulls<String>(flds.length())
                        var idx = 0
                        while (idx < flds.length()) {
                            allFlds[idx] = flds[idx].jsonObject.optString("name", "")
                            idx++
                        }
                        @KotlinCleanup("remove requireNoNulls")
                        rb.add(Utils.joinFields(allFlds.requireNoNulls()))
                    }
                    FlashCardsContract.Model.NUM_CARDS -> rb.add(noteType.templates.length())
                    FlashCardsContract.Model.CSS -> rb.add(noteType.css)
                    FlashCardsContract.Model.DECK_ID -> // #6378 - Anki Desktop changed schema temporarily to allow null
                        rb.add(noteType.did)
                    FlashCardsContract.Model.SORT_FIELD_INDEX -> rb.add(noteType.sortf)
                    FlashCardsContract.Model.TYPE -> rb.add(noteType.type.code)
                    FlashCardsContract.Model.LATEX_POST -> rb.add(noteType.latexPost)
                    FlashCardsContract.Model.LATEX_PRE -> rb.add(noteType.latexPre)
                    FlashCardsContract.Model.NOTE_COUNT -> rb.add(notetypes.useCount(noteType))
                    else -> throw UnsupportedOperationException("Queue \"$column\" is unknown")
                }
            }
        } catch (e: JSONException) {
            Timber.e(e, "Error parsing JSONArray")
            throw IllegalArgumentException("Model $noteTypeId is malformed", e)
        }
    }

    private fun addCardToCursor(
        currentCard: Card,
        rv: MatrixCursor,
        col: Collection,
        columns: Array<String>,
    ) {
        val cardName: String =
            try {
                currentCard.template(col).name
            } catch (je: JSONException) {
                throw IllegalArgumentException("Card is using an invalid template", je)
            }
        val question = currentCard.renderOutput(col).questionWithFixedSoundTags()
        val answer = currentCard.renderOutput(col).answerWithFixedSoundTags()
        val rb = rv.newRow()
        for (column in columns) {
            when (column) {
                FlashCardsContract.Card._ID -> rb.add(currentCard.id)
                FlashCardsContract.Card.NOTE_ID -> rb.add(currentCard.nid)
                FlashCardsContract.Card.CARD_ORD -> rb.add(currentCard.ord)
                FlashCardsContract.Card.CARD_NAME -> rb.add(cardName)
                FlashCardsContract.Card.DECK_ID -> rb.add(currentCard.did)
                FlashCardsContract.Card.REPS -> rb.add(currentCard.reps)
                FlashCardsContract.Card.LAPSES -> rb.add(currentCard.lapses)
                FlashCardsContract.Card.TYPE -> rb.add(currentCard.type.code)
                FlashCardsContract.Card.ORIGINAL_DECK_ID -> rb.add(currentCard.oDid)
                FlashCardsContract.Card.QUESTION -> rb.add(question)
                FlashCardsContract.Card.ANSWER -> rb.add(answer)
                FlashCardsContract.Card.QUESTION_SIMPLE -> rb.add(currentCard.renderOutput(col).questionText)
                FlashCardsContract.Card.ANSWER_SIMPLE -> rb.add(currentCard.renderOutput(col, false).answerText)
                FlashCardsContract.Card.ANSWER_PURE -> rb.add(currentCard.pureAnswer(col))
                FlashCardsContract.Card.RAW_QUEUE -> rb.add(currentCard.queue.code)
                FlashCardsContract.Card.RAW_DUE -> rb.add(currentCard.due)
                FlashCardsContract.Card.RAW_ORIGINAL_DUE -> rb.add(currentCard.oDue)
                FlashCardsContract.Card.INTERVAL -> rb.add(currentCard.ivl)
                FlashCardsContract.Card.RAW_SM2_FACTOR -> rb.add(currentCard.factor)
                FlashCardsContract.Card.RAW_LEFT -> rb.add(currentCard.left)
                FlashCardsContract.Card.ORIGINAL_POSITION -> rb.add(currentCard.originalPosition)
                FlashCardsContract.Card.RAW_CUSTOM_DATA -> rb.add(currentCard.customData)
                FlashCardsContract.Card.FSRS_STABILITY -> rb.add(currentCard.memoryStateStability)
                FlashCardsContract.Card.FSRS_DIFFICULTY -> rb.add(currentCard.memoryStateDifficulty)
                FlashCardsContract.Card.FSRS_DESIRED_RETENTION -> rb.add(currentCard.fsrsDesiredRetention)
                FlashCardsContract.Card.FSRS_DECAY -> rb.add(currentCard.decay)
                FlashCardsContract.Card.LAST_REVIEW_TIME_SECONDS -> rb.add(currentCard.lastReviewTimeSecs)
                else -> throw UnsupportedOperationException("Queue \"$column\" is unknown")
            }
        }
    }

    private fun addReviewInfoToCursor(
        currentCard: Card,
        nextReviewTimesJson: JSONArray,
        buttonCount: Int,
        rv: MatrixCursor,
        col: Collection,
        columns: Array<String>,
    ) {
        val rb = rv.newRow()
        for (column in columns) {
            when (column) {
                FlashCardsContract.Card.NOTE_ID -> rb.add(currentCard.nid)
                FlashCardsContract.ReviewInfo.CARD_ORD -> rb.add(currentCard.ord)
                FlashCardsContract.ReviewInfo.BUTTON_COUNT -> rb.add(buttonCount)
                FlashCardsContract.ReviewInfo.NEXT_REVIEW_TIMES -> rb.add(nextReviewTimesJson.toString())
                FlashCardsContract.ReviewInfo.MEDIA_FILES ->
                    rb.add(
                        JSONArray(col.media.filesInStr(currentCard)),
                    )
                else -> throw UnsupportedOperationException("Queue \"$column\" is unknown")
            }
        }
    }

    private fun answerCard(
        col: Collection,
        cardToAnswer: Card?,
        @Suppress("DEPRECATION") ease: com.ichi2.anki.libanki.sched.Ease,
        timeTaken: Long,
    ) {
        try {
            if (cardToAnswer != null) {
                if (timeTaken != -1L) {
                    cardToAnswer.timerStarted = TimeManager.time.intTimeMS() - timeTaken
                }
                col.sched.answerCard(cardToAnswer, CardAnswer.Rating.forNumber(ease.value - 1))
            }
        } catch (e: RuntimeException) {
            Timber.e(e, "answerCard - RuntimeException on answering card")
            CrashReportService.sendExceptionReport(e, "doInBackgroundAnswerCard")
        }
    }

    private fun buryOrSuspendCard(
        col: Collection,
        card: Card?,
        bury: Boolean,
    ) {
        try {
            if (card != null) {
                if (bury) {
                    // bury
                    col.sched.buryCards(listOf(card.id))
                } else {
                    // suspend
                    col.sched.suspendCards(listOf(card.id))
                }
            }
        } catch (e: RuntimeException) {
            Timber.e(e, "buryOrSuspendCard - RuntimeException on burying or suspending card")
            CrashReportService.sendExceptionReport(e, "doInBackgroundBurySuspendCard")
        }
    }

    /**
     * @param [idx] The index of the template in the note type. First template is number 1.
     */
    private fun addTemplateToCursor(
        tmpl: CardTemplate,
        notetype: NotetypeJson?,
        idx: Int,
        notetypes: Notetypes,
        rv: MatrixCursor,
        columns: Array<String>,
    ) {
        try {
            val rb = rv.newRow()
            for (column in columns) {
                when (column) {
                    FlashCardsContract.CardTemplate._ID -> rb.add(idx)
                    FlashCardsContract.CardTemplate.MODEL_ID -> rb.add(notetype!!.id)
                    FlashCardsContract.CardTemplate.ORD -> rb.add(tmpl.ord)
                    FlashCardsContract.CardTemplate.NAME -> rb.add(tmpl.name)
                    FlashCardsContract.CardTemplate.QUESTION_FORMAT -> rb.add(tmpl.qfmt)
                    FlashCardsContract.CardTemplate.ANSWER_FORMAT -> rb.add(tmpl.afmt)
                    FlashCardsContract.CardTemplate.BROWSER_QUESTION_FORMAT -> rb.add(tmpl.bqfmt)
                    FlashCardsContract.CardTemplate.BROWSER_ANSWER_FORMAT -> rb.add(tmpl.bafmt)
                    FlashCardsContract.CardTemplate.CARD_COUNT -> rb.add(notetypes.tmplUseCount(notetype!!, tmpl.ord))
                    else -> throw UnsupportedOperationException(
                        "Support for column \"$column\" is not implemented",
                    )
                }
            }
        } catch (e: JSONException) {
            Timber.e(e, "Error adding template to cursor")
            throw IllegalArgumentException("Template is malformed", e)
        }
    }

    private fun addDeckToCursor(
        id: DeckId,
        name: String,
        deckCounts: JSONArray,
        rv: MatrixCursor,
        col: Collection,
        columns: Array<String>,
    ) {
        val rb = rv.newRow()
        for (column in columns) {
            when (column) {
                FlashCardsContract.Deck.DECK_NAME -> rb.add(name)
                FlashCardsContract.Deck.DECK_ID -> rb.add(id)
                FlashCardsContract.Deck.DECK_COUNTS -> rb.add(deckCounts)
                FlashCardsContract.Deck.OPTIONS -> {
                    val config = col.decks.configDictForDeckId(id).toString()
                    rb.add(config)
                }
                FlashCardsContract.Deck.DECK_DYN -> rb.add(col.decks.isFiltered(id))
                FlashCardsContract.Deck.DECK_DESC -> {
                    val desc = col.decks.current().description
                    rb.add(desc)
                }
            }
        }
    }

    private fun selectDeckWithCheck(
        col: Collection,
        did: DeckId,
    ): Boolean =
        if (col.decks.getLegacy(did) != null) {
            col.decks.select(did)
            true
        } else {
            Timber.e(
                "Requested deck with id %d was not found in deck list. Either the deckID provided was wrong" +
                    " or the deck has been deleted in the meantime.",
                did,
            )
            false
        }

    private fun getCardFromUri(
        uri: Uri,
        col: Collection,
    ): Card {
        val noteId = uri.pathSegments[1].toLong()
        val ord = uri.pathSegments[3].toInt()
        return getCard(noteId, ord, col)
    }

    private fun getCard(
        noteId: NoteId,
        ord: Int,
        col: Collection,
    ): Card {
        val currentNote = col.getNote(noteId)
        var currentCard: Card? = null
        for (card in currentNote.cards(col)) {
            if (card.ord == ord) {
                currentCard = card
            }
        }
        if (currentCard == null) {
            throw IllegalArgumentException("Card with ord $ord does not exist for note $noteId")
        }
        return currentCard
    }

    private fun getNoteFromUri(
        uri: Uri,
        col: Collection,
    ): Note {
        val noteId = uri.pathSegments[1].toLong()
        return col.getNote(noteId)
    }

    private fun getNoteTypeIdFromUri(
        uri: Uri,
        col: Collection,
    ): NoteTypeId =
        if (uri.pathSegments[1] == FlashCardsContract.Model.CURRENT_MODEL_ID) {
            col.notetypes.current().id
        } else {
            try {
                uri.pathSegments[1].toLong()
            } catch (e: NumberFormatException) {
                throw IllegalArgumentException("Note type ID must be either numeric or the String CURRENT_MODEL_ID", e)
            }
        }

    @Throws(JSONException::class)
    private fun getTemplateFromUri(
        uri: Uri,
        col: Collection,
    ): CardTemplate {
        val noteType: NotetypeJson? = col.notetypes.get(getNoteTypeIdFromUri(uri, col))
        val ord = uri.lastPathSegment!!.toInt()
        return noteType!!.templates[ord]
    }

    private fun throwSecurityException(
        methodName: String,
        uri: Uri,
    ) {
        val msg = "Permission not granted for: ${getLogMessage(methodName, uri)}"
        Timber.e("%s", msg)
        throw SecurityException(msg)
    }

    private fun getLogMessage(
        methodName: String,
        uri: Uri?,
    ): String {
        val format = "%s.%s %s (%s)"
        val path = uri?.path
        return String.format(format, javaClass.simpleName, methodName, path, callingPackage)
    }

    private fun hasReadWritePermission(): Boolean =
        if (BuildConfig.DEBUG) { // Allow self-calling of the provider only in debug builds (e.g. for unit tests)
            context!!.checkCallingOrSelfPermission(FlashCardsContract.READ_WRITE_PERMISSION) == PackageManager.PERMISSION_GRANTED
        } else {
            context!!.checkCallingPermission(FlashCardsContract.READ_WRITE_PERMISSION) == PackageManager.PERMISSION_GRANTED
        }

    /** Returns true if the calling package is known to be "rogue" and should be blocked.
     * Calling package might be rogue if it has not declared #READ_WRITE_PERMISSION in its manifest */
    private fun knownRogueClient(): Boolean =
        !context!!.arePermissionsDefinedInManifest(callingPackage!!, FlashCardsContract.READ_WRITE_PERMISSION)
}

/** replaces [anki:play...] with [sound:] */
private fun TemplateRenderOutput.questionWithFixedSoundTags() = replaceWithSoundTags(questionText, this)

/** replaces [anki:play...] with [sound:] */
private fun TemplateRenderOutput.answerWithFixedSoundTags() = replaceWithSoundTags(answerText, this)

/**
 * Returns the answer with anything before the `<hr id=answer>` tag removed
 * TODO inline once the legacy TTS mechanism is removed
 */
fun Card.pureAnswer(col: Collection): String {
    val s = renderOutput(col).answerText
    for (target in arrayOf("<hr id=answer>", "<hr id=\"answer\">")) {
        val pos = s.indexOf(target)
        if (pos == -1) continue
        return s.substring(pos + target.length).trim()
    }
    // neither found
    return s
}

/**
 * Returns the ids of empty cards for a given note type
 */
private fun NotetypeJson.getEmptyCardIds(col: Collection): List<CardId> {
    val noteIdsOfType = col.notetypes.nids(this).toSet()

    return col
        .getEmptyCards()
        .notesList
        .filter { noteIdsOfType.contains(it.noteId) }
        .flatMap { it.cardIdsList }
}

private val Card.memoryStateStability: Float?
    get() = memoryState?.stability

private val Card.memoryStateDifficulty: Float?
    get() = memoryState?.difficulty

private val Card.fsrsDesiredRetention: Float?
    get() = desiredRetention

private fun notifyAllValuesChanged() {
    // TODO: Use more specific OpChanges instead of ALL
    ChangeManager.publishAllValuesChanged()
}

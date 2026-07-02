/*
 * Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com>
 * Copyright (c) 2012 Kostas Spyropoulos <inigo.aldana@gmail.com>
 * Copyright (c) 2022 Ankitects Pty Ltd <http://apps.ankiweb.net>
 * Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General private License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General private License for more details.
 *
 * You should have received a copy of the GNU General private License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 *  This file incorporates code under the following license
 *  https://github.com/ankitects/anki/blob/33a923797afc9655c3b4f79847e1705a1f998d03/pylib/anki/browser.py
 *
 *    Copyright: Ankitects Pty Ltd and contributors
 *    License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
 */

// "FunctionName": many libAnki functions used to have leading _s
@file:Suppress("FunctionName")

package com.ichi2.anki.libanki

import androidx.annotation.CheckResult
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import anki.card_rendering.EmptyCardsReport
import anki.collection.OpChanges
import anki.collection.OpChangesAfterUndo
import anki.collection.OpChangesWithCount
import anki.config.ConfigKey
import anki.config.Preferences
import anki.config.copy
import anki.generic.Empty
import anki.image_occlusion.GetImageForOcclusionResponse
import anki.image_occlusion.GetImageOcclusionNoteResponse
import anki.import_export.CsvMetadata
import anki.import_export.ExportAnkiPackageOptions
import anki.import_export.ExportLimit
import anki.import_export.ImportAnkiPackageOptions
import anki.import_export.ImportCsvRequest
import anki.import_export.ImportResponse
import anki.import_export.csvMetadataRequest
import anki.notes.AddNoteRequest
import anki.scheduler.stateOrNull
import anki.search.BrowserColumns
import anki.search.BrowserRow
import anki.search.SearchNode
import anki.search.SearchNode.Group.Joiner
import anki.search.SortOrderKt.builtin
import anki.search.searchNode
import anki.search.sortOrder
import anki.stats.CardStatsResponse
import anki.stats.CardStatsResponse.StatsRevlogEntry
import anki.sync.MediaSyncStatusResponse
import anki.sync.SyncAuth
import anki.sync.SyncCollectionResponse
import anki.sync.SyncStatusResponse
import anki.sync.fullUploadOrDownloadRequest
import anki.sync.syncLoginRequest
import com.google.protobuf.ByteString
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.common.utils.annotation.KotlinCleanup
import com.ichi2.anki.libanki.CollectionFiles.FolderBasedCollection
import com.ichi2.anki.libanki.CollectionFiles.InMemory
import com.ichi2.anki.libanki.Storage.OpenDbArgs
import com.ichi2.anki.libanki.Utils.ids2str
import com.ichi2.anki.libanki.backend.model.toBackendNote
import com.ichi2.anki.libanki.exception.ConfirmModSchemaException
import com.ichi2.anki.libanki.sched.DummyScheduler
import com.ichi2.anki.libanki.sched.Scheduler
import com.ichi2.anki.libanki.utils.LibAnkiAlias
import com.ichi2.anki.libanki.utils.NotInPyLib
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendException.BackendSearchException
import net.ankiweb.rsdroid.RustCleanup
import net.ankiweb.rsdroid.exceptions.BackendNotFoundException
import timber.log.Timber
import java.io.File

typealias ImportLogWithChanges = anki.import_export.ImportResponse

@NotInPyLib
typealias UndoStepCounter = Int

@NotInPyLib // Literal["AND", "OR"]
enum class SearchJoiner {
    AND,
    OR,
}

@LibAnkiAlias("ComputedMemoryState")
data class ComputedMemoryState(
    val desiredRetention: Float,
    val stability: Float? = null,
    val difficulty: Float? = null,
    val decay: Float? = null,
)

// Anki maintains a cache of used tags so it can quickly present a list of tags
// for autocomplete and in the browser. For efficiency, deletions are not
// tracked, so unused tags can only be removed from the list with a DB check.
//
// This module manages the tag cache and tags for notes.
@KotlinCleanup("inline function in init { } so we don't need to init `crt` etc... at the definition")
@RustCleanup("combine with BackendImportExport")
@RustCleanup("Config is not fully implemented")
@WorkerThread
class Collection(
    /**
     *  The path to the folder containing collection.anki2 database. Must be unicode and openable with [File].
     */

    val collectionFiles: CollectionFiles,
    /**
     * Outside of libanki, you should not access the backend directly for collection operations.
     * Operations that work on a closed collection (eg importing), or do not require a collection
     * at all (eg translations) are the exception.
     */
    val backend: Backend,
    databaseBuilder: (Backend) -> DB,
) {
    val colDb: File
        get() = collectionFiles.requireDiskBasedCollection().colDb

    /** Access backend translations */
    val tr = backend.tr

    @get:JvmName("isDbClosed")
    val dbClosed: Boolean
        get() {
            return dbInternal == null
        }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    fun debugEnsureNoOpenPointers() {
        val result = backend.getActiveSequenceNumbers()
        if (result.isNotEmpty()) {
            val numbers = result.toString()
            throw IllegalStateException("Contained unclosed sequence numbers: $numbers")
        }
    }

    // a lot of legacy code does not check for nullability
    val db: DB
        get() = dbInternal!!

    var dbInternal: DB? = null

    /**
     * Getters/Setters ********************************************************** *************************************
     */

    val media: Media = Media(this)

    lateinit var decks: Decks
        protected set

    val tags: Tags = Tags(this)

    lateinit var config: Config

    @KotlinCleanup("see if we can inline a function inside init {} and make this `val`")
    lateinit var sched: Scheduler
        protected set

    private val lastSync: Long
        get() = db.queryLongScalar("select ls from col")

    var ls: Long = 0
    // END: SQL table columns

    init {
        val created = reopen(databaseBuilder = databaseBuilder)
        _loadScheduler()
        if (created) {
            config.set("schedVer", 2)
            // we need to reload the scheduler: this was previously loaded as V1
            _loadScheduler()
        }
    }

    /*
     * Scheduler
     * ***********************************************************
     */

    /**
     * For backwards compatibility, the v3 scheduler currently returns 2.
     * Use the separate [v3Scheduler] method to check if it is active.
     */
    @LibAnkiAlias("sched_ver")
    fun schedVer(): Int {
        @RustCleanup("move outside this method")
        @LibAnkiAlias("_supported_scheduler_versions")
        val supportedSchedulerVersions = listOf(1, 2)

        // for backwards compatibility, v3 is represented as 2
        val ver = config.get("schedVer") ?: 1
        if (ver in supportedSchedulerVersions) {
            return ver
        } else {
            throw RuntimeException("Unsupported scheduler version")
        }
    }

    @RustCleanup("doesn't match upstream")
    fun _loadScheduler() {
        val ver = schedVer()
        if (ver == 1) {
            sched = DummyScheduler(this)
        } else if (ver == 2) {
            if (!backend.getConfigBool(ConfigKey.Bool.SCHED_2021)) {
                backend.setConfigBool(ConfigKey.Bool.SCHED_2021, true, undoable = false)
            }
            sched = Scheduler(this)
            if (config.get<Int>("creationOffset") == null) {
                val prefs =
                    getPreferences().copy {
                        scheduling = scheduling.copy { newTimezone = true }
                    }
                setPreferences(prefs)
            }
        }
    }

    @LibAnkiAlias("v3_scheduler")
    fun v3Scheduler(): Boolean = schedVer() == 2 && backend.getConfigBool(ConfigKey.Bool.SCHED_2021)

    /**
     * @throws RuntimeException [enabled] requested, but not using the [schedVer][v2 scheduler]
     */
    @LibAnkiAlias("set_v3_scheduler")
    fun setV3Scheduler(enabled: Boolean) {
        if (this.v3Scheduler() != enabled) {
            if (enabled && schedVer() != 2) {
                throw RuntimeException("must upgrade to v2 scheduler first")
            }
            config.setBool(ConfigKey.Bool.SCHED_2021, enabled)
            _loadScheduler()
        }
    }

    /*
     * DB-related
     * ***********************************************************
     */

    // legacy properties; these will likely go away in the future

    val mod: Long
        get() = db.queryLongScalar("select mod from col")

    @RustCleanup("remove")
    @NotInPyLib
    val scm: Long
        get() = db.queryLongScalar("select scm from col")

    /**
     * Disconnect from DB.
     * Python implementation has a save argument for legacy reasons;
     * AnkiDroid always saves as changes are made.
     */
    @Synchronized
    @LibAnkiAlias("close")
    @RustCleanup("doesn't match upstream")
    fun close(
        downgrade: Boolean = false,
        forFullSync: Boolean = false,
    ) {
        if (!dbClosed) {
            if (!forFullSync) {
                backend.closeCollection(downgrade)
            }
            dbInternal = null
            Timber.i("Collection closed")
        }
    }

    @LibAnkiAlias("close_for_full_sync")
    fun closeForFullSync() {
        // save and cleanup, but backend will take care of collection close
        if (dbInternal != null) {
            clearCaches()
            dbInternal = null
        }
    }

    @LibAnkiAlias("_clear_caches")
    private fun clearCaches() {
        notetypes.clearCache()
    }

    /** True if DB was created */
    @RustCleanup("doesn't match upstream")
    @LibAnkiAlias("reopen")
    fun reopen(
        afterFullSync: Boolean = false,
        databaseBuilder: (Backend) -> DB,
    ): Boolean {
        val reopenArgs =
            when (collectionFiles) {
                is InMemory, is CollectionFiles.InMemoryWithMedia -> OpenDbArgs.InMemory
                is FolderBasedCollection -> {
                    OpenDbArgs.Path(collectionFiles.colDb)
                }
            }
        Timber.i("(Re)opening Database: %s", reopenArgs)
        return if (dbClosed) {
            val (database, created) =
                Storage.openDB(
                    args = reopenArgs,
                    backend = backend,
                    afterFullSync = afterFullSync,
                    buildDatabase = databaseBuilder,
                )
            dbInternal = database
            load()
            if (afterFullSync) {
                _loadScheduler()
            }
            created
        } else {
            false
        }
    }

    @NotInPyLib
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    fun load() {
        notetypes = Notetypes(this)
        decks = Decks(this)
        config = Config(backend)
    }

    /**
     * Marks the schema as modified to cause a one-way sync.
     *
     * **This method discards the undo and study queues when returning successfully**
     */
    @LibAnkiAlias("set_schema_modified")
    @RustCleanup("Anki only sets scm, not mod")
    fun setSchemaModified() {
        db.execute(
            "update col set scm=?, mod=?",
            TimeManager.time.intTimeMS(),
            TimeManager.time.intTimeMS(),
        )
    }

    /**
     * Marks the schema as modified to cause a one-way sync, throwing [ConfirmModSchemaException]
     * when [check] is `true` and a one-way sync was not previously required
     * (the schema was not previously modified).
     *
     * **This method discards the undo and study queues when returning successfully**
     * ([check] == `false` OR [check] == `true` and the schema was previously modified).
     *
     * @param check whether to throw [ConfirmModSchemaException] if the schema is unchanged.
     * Use `false` after catching the exception and obtaining user consent.
     *
     * @throws ConfirmModSchemaException if the schema is currently unchanged and [check] is `true`
     */
    @LibAnkiAlias("mod_schema")
    fun modSchema(check: Boolean) {
        if (!schemaChanged()) {
            if (check) {
                /* In Android we can't show a dialog which blocks the main UI thread
                   Therefore we can't wait for the user to confirm if they want to do
                   a one-way sync here, and we instead throw an exception asking the outer
                   code to handle the user's choice */
                throw ConfirmModSchemaException()
            }
        }
        setSchemaModified()
    }

    /** `true` if schema changed since last sync. */
    @LibAnkiAlias("schema_changed")
    @RustCleanup("doesn't match upstream")
    fun schemaChanged(): Boolean = scm > lastSync

    @LibAnkiAlias("usn")
    fun usn(): Int = -1

    /*
     * Import/export
     * ***********************************************************
     */

    /**
     * (Maybe) create a colpkg backup, while keeping the collection open. If the
     * configured backup interval has not elapsed, and force=false, no backup will be created,
     * and this routine will return false.
     *
     * There must not be an active transaction.
     *
     * If `waitForCompletion` is true, block until the backup completes. Otherwise this routine
     * returns quickly, and the backup can be awaited on a background thread with awaitBackupCompletion()
     * to check for success.
     *
     * Backups are automatically expired according to the user's settings.
     */
    @LibAnkiAlias("create_backup")
    fun createBackup(
        backupFolder: String,
        force: Boolean,
        waitForCompletion: Boolean,
    ): Boolean {
        // ensure any pending transaction from legacy code/add-ons has been committed
        val created =
            backend.createBackup(
                backupFolder = backupFolder,
                force = force,
                waitForCompletion = waitForCompletion,
            )
        return created
    }

    /**
     * If a backup is running, block until it completes, throwing if it fails, or already
     * failed, and the status has not yet been checked. On failure, an error is only returned
     * once; subsequent calls are a no-op until another backup is run.
     *
     * @throws Exception if backup creation failed, no-op after first throw
     */
    @LibAnkiAlias("await_backup_completion")
    fun awaitBackupCompletion() {
        backend.awaitBackupCompletion()
    }

    // export_collection_package is in AnkiDroid: BackendExporting.kt

    @LibAnkiAlias("import_anki_package")
    @RustCleanup("different input parameters - OK?")
    fun importAnkiPackage(
        packagePath: String,
        options: ImportAnkiPackageOptions,
    ): ImportResponse = backend.importAnkiPackage(packagePath, options)

    @LibAnkiAlias("export_anki_package")
    fun exportAnkiPackage(
        outPath: String,
        options: ExportAnkiPackageOptions,
        limit: ExportLimit,
    ): Int =
        backend.exportAnkiPackage(
            outPath = outPath,
            options = options,
            limit = limit,
        )

    @LibAnkiAlias("get_csv_metadata")
    fun getCsvMetadata(
        path: String,
        delimiter: CsvMetadata.Delimiter?,
    ): CsvMetadata {
        val request =
            csvMetadataRequest {
                this.path = path
                delimiter?.let { this.delimiter = delimiter }
            }
        return backend.getCsvMetadata(request)
    }

    @LibAnkiAlias("import_csv")
    @RustCleanup("not quite the same")
    fun importCsv(request: ImportCsvRequest): ImportLogWithChanges =
        backend.importCsv(
            path = request.path,
            metadata = request.metadata,
        )

    @LibAnkiAlias("export_note_csv")
    fun exportNoteCsv(
        outPath: String,
        limit: ExportLimit,
        withHtml: Boolean,
        withTags: Boolean,
        withDeck: Boolean,
        withNotetype: Boolean,
        withGuid: Boolean,
    ): Int =
        backend.exportNoteCsv(
            outPath = outPath,
            withHtml = withHtml,
            withTags = withTags,
            withDeck = withDeck,
            withNotetype = withNotetype,
            withGuid = withGuid,
            limit = limit,
        )

    @LibAnkiAlias("export_card_csv")
    fun exportCardCsv(
        outPath: String,
        limit: ExportLimit,
        withHtml: Boolean,
    ): Int =
        backend.exportCardCsv(
            outPath = outPath,
            withHtml = withHtml,
            limit = limit,
        )

    @LibAnkiAlias("import_json_file")
    fun importJsonFile(path: String): ImportLogWithChanges = backend.importJsonFile(path)

    @LibAnkiAlias("import_json_string")
    fun importJsonString(json: String): ImportLogWithChanges = backend.importJsonString(json)

    @LibAnkiAlias("export_dataset_for_research")
    fun exportDatasetForResearch(
        targetPath: String,
        minEntries: Int = 0,
    ) {
        backend.exportDataset(minEntries = minEntries, targetPath = targetPath)
    }

    /*
     * Image Occlusion
     * ***********************************************************
     */

    @CheckResult
    @LibAnkiAlias("get_image_for_occlusion")
    @RustCleanup("path should be nullable")
    fun getImageForOcclusion(path: String): GetImageForOcclusionResponse = backend.getImageForOcclusion(path = path)

    /** Add notetype if missing. */
    @LibAnkiAlias("add_image_occlusion_notetype")
    fun addImageOcclusionNoteType() {
        backend.addImageOcclusionNotetype()
    }

    @CheckResult
    @LibAnkiAlias("add_image_occlusion_note")
    fun addImageOcclusionNote(
        noteTypeId: NoteTypeId,
        imagePath: String,
        occlusions: String,
        header: String,
        backExtra: String,
        tags: List<String>,
    ): OpChanges =
        backend.addImageOcclusionNote(
            notetypeId = noteTypeId,
            imagePath = imagePath,
            occlusions = occlusions,
            header = header,
            backExtra = backExtra,
            tags = tags,
        )

    @CheckResult
    @LibAnkiAlias("get_image_occlusion_note")
    fun getImageOcclusionNote(noteId: NoteId): GetImageOcclusionNoteResponse = backend.getImageOcclusionNote(noteId = noteId)

    @CheckResult
    @LibAnkiAlias("update_image_occlusion_note")
    @RustCleanup("parameters should all be nullable")
    fun updateImageOcclusionNote(
        noteId: NoteId,
        occlusions: String,
        header: String,
        backExtra: String,
        tags: List<String>,
    ): OpChanges =
        backend.updateImageOcclusionNote(
            noteId = noteId,
            occlusions = occlusions,
            header = header,
            backExtra = backExtra,
            tags = tags,
        )

    /*
     * Object helpers
     * ***********************************************************
     */

    /**
     * Return the card with the given ID.
     *
     * @throws BackendNotFoundException if the card does not exist
     */
    @CheckResult
    @LibAnkiAlias("get_card")
    fun getCard(id: CardId): Card = Card(this, id)

    /** Save card changes to database. */
    @LibAnkiAlias("update_cards")
    fun updateCards(
        cards: Iterable<Card>,
        skipUndoEntry: Boolean = false,
    ): OpChanges = backend.updateCards(cards.map { it.toBackendCard() }, skipUndoEntry)

    /** Save card changes to database. */
    @LibAnkiAlias("update_card")
    fun updateCard(
        card: Card,
        skipUndoEntry: Boolean = false,
    ): OpChanges = updateCards(listOf(card), skipUndoEntry)

    @CheckResult
    @LibAnkiAlias("get_note")
    fun getNote(id: NoteId): Note = Note(this, id)

    /** Save note changes to database. */
    @CheckResult
    @LibAnkiAlias("update_notes")
    fun updateNotes(
        notes: Iterable<Note>,
        skipUndoEntry: Boolean = false,
    ): OpChanges =
        backend.updateNotes(
            notes = notes.map { it.toBackendNote() },
            skipUndoEntry = skipUndoEntry,
        )

    /**
     * Save note changes to database.
     */
    @CheckResult
    @LibAnkiAlias("update_note")
    fun updateNote(
        note: Note,
        skipUndoEntry: Boolean = false,
    ): OpChanges = backend.updateNotes(notes = listOf(note.toBackendNote()), skipUndoEntry = skipUndoEntry)

    /*
     * Utils
     * ***********************************************************
     */

    @CheckResult
    @LibAnkiAlias("nextID")
    @RustCleanup("Python returns 'Any' - may fail for Double?")
    @Deprecated("not implemented", level = DeprecationLevel.HIDDEN)
    fun nextId(
        type: String,
        inc: Boolean = true,
    ): Long = TODO()

    /*
     * Notes
     * ***********************************************************
     */

    /**
     * Return a new note with a specific model
     * @param notetype The model to use for the new note
     * @return The new note
     */
    @LibAnkiAlias("new_note")
    fun newNote(notetype: NotetypeJson): Note = Note.fromNotetypeId(this, notetype.id)

    @LibAnkiAlias("add_note")
    fun addNote(
        note: Note,
        deckId: DeckId,
    ): OpChangesWithCount {
        val out = backend.addNote(note.toBackendNote(), deckId)
        note.id = out.noteId
        return out.changes
    }

    @LibAnkiAlias("add_notes")
    @RustCleanup("Implement")
    @Deprecated("Needs implementation", level = DeprecationLevel.HIDDEN)
    fun addNotes(requests: List<AddNoteRequest>): OpChanges? = TODO()
//    {
//        val out = backend.addNotes(requests = requests)
//        for ((idx, request) in requests.withIndex()) {
//            request.note!!.id = out.getNids(idx)
//        }
//        return out.changes
//    }

    @LibAnkiAlias("remove_notes")
    @RustCleanup("remove cids and pass in []")
    fun removeNotes(
        noteIds: Iterable<NoteId> = listOf(),
        cardIds: Iterable<CardId> = listOf(),
    ): OpChangesWithCount =
        backend.removeNotes(noteIds = noteIds, cardIds = cardIds).also {
            Timber.d("removeNotes: %d changes", it.count)
        }

    @LibAnkiAlias("remove_notes_by_card")
    fun removeNotesByCard(cardIds: Iterable<CardId>) {
        backend.removeNotes(noteIds = emptyList(), cardIds = cardIds)
    }

    /**
     * Returns all card IDs linked to the given note.
     *
     * IMPORTANT:
     * A note may not always have cards.
     *
     * This can happen in cases like:
     * - The note type has no card templates (empty cards).
     * - Cards were deleted but the note still exists (orphaned notes).
     */
    @CheckResult
    @LibAnkiAlias("card_ids_of_note")
    fun cardIdsOfNote(nid: NoteId): List<CardId> = backend.cardsOfNote(nid = nid)

    /**
     * Get starting deck and notetype for add screen.
     * An option in the preferences controls whether this will be based on the current deck
     * or current notetype.
     */
    @CheckResult
    @LibAnkiAlias("defaults_for_adding")
    fun defaultsForAdding(currentReviewCard: Card? = null): anki.notes.DeckAndNotetype {
        val homeDeck = currentReviewCard?.currentDeckId() ?: 0L
        return backend.defaultsForAdding(homeDeckOfCurrentReviewCard = homeDeck)
    }

    /**
     * If 'change deck depending on notetype' is enabled in the preferences,
     * return the last deck used with the provided notetype, if any..
     */
    @CheckResult
    @LibAnkiAlias("default_deck_for_notetype")
    @RustCleanup("check if the == 0L logic is necessary")
    fun defaultDeckForNoteType(noteTypeId: NoteTypeId): DeckId? {
        if (config.getBool(ConfigKey.Bool.ADDING_DEFAULTS_TO_CURRENT_DECK)) {
            return null
        }

        val result = backend.defaultDeckForNotetype(ntid = noteTypeId)
        if (result == 0L) return null
        return result
    }

    @CheckResult
    @LibAnkiAlias("note_count")
    fun noteCount(): Int = db.queryScalar("SELECT count() FROM notes")

    /*
     * Cards
     * ***********************************************************
     */

    /**
     * Returns whether the collection contains no cards.
     */
    @LibAnkiAlias("is_empty")
    val isEmpty: Boolean
        get() = db.queryScalar("SELECT 1 FROM cards LIMIT 1") == 0

    @CheckResult
    @LibAnkiAlias("card_count")
    fun cardCount(): Int = db.queryScalar("SELECT count() FROM cards")

    /**
     * You probably want [removeNotesByCard] instead.
     *
     * @return the number of deleted cards. **Note:** if an invalid/duplicate [CardId] is provided,
     * the output count may be less than the input.
     */
    @RustCleanup("maybe deprecate this")
    @LibAnkiAlias("remove_cards_and_orphaned_notes")
    fun removeCardsAndOrphanedNotes(cardIds: Iterable<CardId>): OpChangesWithCount = backend.removeCards(cardIds)

    @LibAnkiAlias("set_deck")
    fun setDeck(
        cardIds: Iterable<CardId>,
        deckId: DeckId,
    ): OpChangesWithCount = backend.setDeck(cardIds = cardIds, deckId = deckId)

    @CheckResult
    @LibAnkiAlias("get_empty_cards")
    fun getEmptyCards(): EmptyCardsReport = backend.getEmptyCards()

    /*
     * Card generation & field checksums/sort fields
     * ***********************************************************
     */

    /** If notes modified directly in database, call this afterwards. */
    @LibAnkiAlias("after_note_updates")
    fun afterNoteUpdates(
        noteIds: List<NoteId>,
        markModified: Boolean,
        generateCards: Boolean = true,
    ) {
        backend.afterNoteUpdates(
            nids = noteIds,
            generateCards = generateCards,
            markNotesModified = markModified,
        )
    }

    /*
     * Finding cards
     * ***********************************************************
     */

    /**
     * Returns [Card IDs][CardId] matching the provided search.
     *
     * To programmatically construct a search string, see [buildSearchString].
     *
     * @see SortOrder
     * @throws BackendSearchException If the query is invalid: `and`; `flag:12` etc...
     */
    @CheckResult
    @LibAnkiAlias("find_cards")
    fun findCards(
        query: String,
        order: SortOrder = SortOrder.NoOrdering,
    ): List<CardId> {
        val mode = buildSortMode(order, findingNotes = false)
        return backend.searchCards(search = query, order = mode)
    }

    /**
     * Returns [Note IDs][NoteId] matching the provided search.
     *
     * To programmatically construct a search string, see [buildSearchString].
     *
     * @see SortOrder
     * @throws BackendSearchException If the query is invalid: `and`; `flag:12` etc...
     */
    @CheckResult
    @LibAnkiAlias("find_notes")
    fun findNotes(
        query: String,
        order: SortOrder = SortOrder.NoOrdering,
    ): List<NoteId> {
        val mode = buildSortMode(order, findingNotes = true)
        return backend.searchNotes(search = query, order = mode)
    }

    @LibAnkiAlias("_build_sort_mode")
    private fun buildSortMode(
        order: SortOrder,
        findingNotes: Boolean,
    ): anki.search.SortOrder =
        sortOrder {
            fun noOrder() = buildSortMode(SortOrder.NoOrdering, findingNotes)
            when (order) {
                // Python: isinstance(order, str)
                is SortOrder.AfterSqlOrderBy -> custom = order.customOrdering
                // Python: isinstance(order, bool); order == False:
                is SortOrder.NoOrdering -> none = Empty.getDefaultInstance()
                // Python: isinstance(order, bool); order == True:
                is SortOrder.UseCollectionOrdering -> {
                    val sortKey = BrowserConfig.sortColumnKey(isNotesMode = findingNotes)
                    val columnKey = config.get<String>(sortKey) ?: "noteFld"
                    // slight deviation from upstream with 'noOrder' returns on nulls.
                    val order = getBrowserColumn(columnKey) ?: return noOrder()
                    val reverseKey = BrowserConfig.sortBackwardsKey(isNotesMode = findingNotes)
                    val reverse = config.get<Boolean>(reverseKey) ?: false
                    val updatedQuery = SortOrder.BuiltinColumnSortKind(order, reverse)
                    // deviates from upstream: recursive call rather than mutating a variable
                    return buildSortMode(updatedQuery, findingNotes)
                }
                is SortOrder.BuiltinColumnSortKind -> {
                    val sort = if (findingNotes) order.column.sortingNotes else order.column.sortingCards
                    if (sort == BrowserColumns.Sorting.SORTING_NONE) return noOrder()

                    builtin =
                        builtin {
                            column = order.column.key
                            reverse = order.reverse
                        }
                }
            }
        }

    /**
     * @return An [OpChangesWithCount] representing the number of affected notes
     */
    @CheckResult
    @LibAnkiAlias("find_and_replace")
    fun findAndReplace(
        nids: List<NoteId>,
        search: String,
        replacement: String,
        regex: Boolean = false,
        field: String? = null,
        matchCase: Boolean = false,
    ): OpChangesWithCount = backend.findAndReplace(nids, search, replacement, regex, matchCase, field ?: "")

    @LibAnkiAlias("field_names_for_note_ids")
    fun fieldNamesForNoteIds(nids: List<NoteId>): List<String> = backend.fieldNamesForNotes(nids)

    // returns array of ("dupestr", [nids])
    // @LibAnkiAlias("find_dupes")
    // fun findDupes(fieldName: String, search: String = ""): List<Pair<String, List<Any>>>

    /*
     * Search Strings
     * ***********************************************************
     */

    /**
     * Construct a search string from the provided [String] or [SearchNode].
     *
     * Note on implementation:
     * Python allows types mixing so the "nodes" parameter was declared here as ```List<Any>``` and
     * each entry will be verified inside the method to be either a [String] or a [SearchNode].
     *
     * ```python
     * def build_search_string(self, *nodes: str | SearchNode, joiner: SearchJoiner = "AND") -> str:
     * ```
     * Usage example:
     *
     * ```kotlin
     *       import anki.search.searchNode
     *       import anki.search.SearchNode
     *       import anki.search.SearchNodeKt.group
     *
     *       val node = searchNode {
     *           group = SearchNodeKt.group {
     *               joiner = SearchNode.Group.Joiner.AND
     *               nodes += searchNode { deck = "a **test** deck" }
     *               nodes += searchNode {
     *                   negated = searchNode {
     *                       tag = "foo"
     *                   }
     *               }
     *               nodes += searchNode { flag = SearchNode.Flag.FLAG_GREEN }
     *           }
     *       }
     *       // yields "deck:a \*\*test\*\* deck" -tag:foo flag:3
     *       val text = col.buildSearchString(listOf(node))
     *   }
     * ```
     * @param stringsOrSearchNodes a list of [String] or [SearchNode] or a combination of [String]
     * and [SearchNode]
     * @throws IllegalArgumentException if [stringsOrSearchNodes] is empty or it has entries which
     * aren't a [String] or a [SearchNode]
     * @throws BackendSearchException if the search is invalid (`and` as a query; `flag:12`)
     */
    // TODO consider implementing a custom dsl for this method, see comments in #19677
    @LibAnkiAlias("build_search_string")
    fun buildSearchString(
        stringsOrSearchNodes: List<Any>,
        joiner: SearchJoiner = SearchJoiner.AND,
    ): String {
        if (!stringsOrSearchNodes.all { it is String || it is SearchNode }) {
            throw IllegalArgumentException("buildSearchString expects a list containing Strings or SearchNodes: $stringsOrSearchNodes")
        }
        val term = groupSearches(stringsOrSearchNodes, joiner)
        return backend.buildSearchString(term)
    }

    /**
     * Join provided [SearchNode]s or [String]s into a single [SearchNode]. If the list has only one
     * entry it will be returned as-is. At least one node must be provided.
     *
     * Note on implementation:
     * Python allows types mixing so the "nodes" parameter was declared here as ```List<Any>``` and
     * each entry will be verified inside the method to be either a [String] or a [SearchNode].
     *
     * ```python
     * def group_searches(self, *nodes: str | SearchNode, joiner: SearchJoiner = "AND") -> SearchNode:
     * ```
     * @param stringsOrSearchNodes a list of [String] or [SearchNode] or a combination of [String]
     * and [SearchNode]
     * @throws IllegalArgumentException if no nodes are provided
     */
    @LibAnkiAlias("group_searches")
    fun groupSearches(
        stringsOrSearchNodes: List<Any>,
        joiner: SearchJoiner = SearchJoiner.AND,
    ): SearchNode {
        if (stringsOrSearchNodes.isEmpty()) throw IllegalArgumentException("At least one entry must be provided!")
        val searchNodes =
            stringsOrSearchNodes.map {
                when (it) {
                    is String -> searchNode { parsableText = it }
                    is SearchNode -> it
                    else -> throw IllegalArgumentException("groupSearches expects a list containing Strings or SearchNodes found: $it")
                }
            }
        return if (searchNodes.size > 1) {
            searchNode {
                group =
                    SearchNode.Group
                        .newBuilder()
                        .addAllNodes(searchNodes)
                        .setJoiner(toPbSearchSeparator(joiner))
                        .build()
            }
        } else {
            searchNodes[0]
        }
    }

    /**
     * AND or OR `additional_term` to `existing_term`, without wrapping `existing_term` in brackets.
     * Used by the Browse screen to avoid adding extra brackets when joining.
     * If you're building a search query yourself, you probably don't need this.
     */
    @LibAnkiAlias("join_searches")
    fun joinSearches(
        existingNode: SearchNode,
        additionalNode: SearchNode,
        operator: SearchJoiner,
    ): String {
        val searchString =
            backend.joinSearchNodes(
                joiner = toPbSearchSeparator(operator),
                existingNode = existingNode,
                additionalNode = additionalNode,
            )
        return searchString
    }

    /**
     * If nodes of the same type as `replacement_node` are found in existing_node, replace them.
     *
     * You can use this to replace any "deck" clauses in a search with a different deck for example.
     */
    @LibAnkiAlias("replace_in_search_node")
    fun replaceInSearchNode(
        existingNode: SearchNode,
        replacementNode: SearchNode,
    ): String = backend.replaceSearchNode(existingNode = existingNode, replacementNode = replacementNode)

    @LibAnkiAlias("_pb_search_separator")
    private fun toPbSearchSeparator(operator: SearchJoiner): Joiner =
        when (operator) {
            SearchJoiner.AND -> Joiner.AND
            SearchJoiner.OR -> Joiner.OR
        }

    /*
     * Browser Table
     * ***********************************************************
     */

    /**
     * Returns all browser columns.
     *
     * @see getBrowserColumn
     */
    @LibAnkiAlias("all_browser_columns")
    fun allBrowserColumns(): List<BrowserColumns.Column> = backend.allBrowserColumns()

    /**
     * Returns a browser column by key.
     *
     * @see allBrowserColumns
     */
    @LibAnkiAlias("get_browser_column")
    fun getBrowserColumn(key: String): BrowserColumns.Column? {
        for (column in backend.allBrowserColumns()) {
            if (column.key == key) {
                return column
            }
        }
        return null
    }

    /**
     * Returns a [BrowserRow], cells dependent on [Backend.setActiveBrowserColumns]
     *
     * WARN: As this is a latency-sensitive call, most callers should use [Backend.browserRowForId]
     *
     * @param id Either a [CardId] or a [NoteId], depending on the value of
     * [ConfigKey.Bool.BROWSER_TABLE_SHOW_NOTES_MODE]
     *
     * @see [setBrowserCardColumns]
     * @see [setBrowserNoteColumns]
     */
    // For performance, this does not match upstream:
    // https://github.com/ankitects/anki/blob/1fb1cbbf85c48a54c05cb4442b1b424a529cac60/pylib/anki/collection.py#L869-L881
    @LibAnkiAlias("browser_row_for_id")
    fun browserRowForId(id: Long): BrowserRow = backend.browserRowForId(id)

    /** Return the stored card column names and ensure the backend columns are set and in sync. */
    @LibAnkiAlias("load_browser_card_columns")
    fun loadBrowserCardColumns(): List<String> {
        val columns = config.get<List<String>>(BrowserConfig.ACTIVE_CARD_COLUMNS_KEY, BrowserDefaults.CARD_COLUMNS)!!
        backend.setActiveBrowserColumns(columns)
        return columns
    }

    @LibAnkiAlias("set_browser_card_columns")
    fun setBrowserCardColumns(columns: List<String>) {
        config.set(BrowserConfig.ACTIVE_CARD_COLUMNS_KEY, columns)
        backend.setActiveBrowserColumns(columns)
    }

    /** Return the stored note column names and ensure the backend columns are set and in sync. */
    @LibAnkiAlias("load_browser_note_columns")
    fun loadBrowserNoteColumns(): List<String> {
        val columns =
            config.get<List<String>>(
                BrowserConfig.ACTIVE_NOTE_COLUMNS_KEY,
                BrowserDefaults.NOTE_COLUMNS,
            )!!
        backend.setActiveBrowserColumns(columns)
        return columns
    }

    @LibAnkiAlias("set_browser_note_columns")
    fun setBrowserNoteColumns(columns: List<String>) {
        config.set(BrowserConfig.ACTIVE_NOTE_COLUMNS_KEY, columns)
        backend.setActiveBrowserColumns(columns)
    }

    /*
     * Stats
     * ***********************************************************
     */

    // def stats(self) -> anki.stats.CollectionStats:

    /**
     * Returns the data required to show card stats.
     *
     * If you wish to display the stats in a HTML table like Anki does,
     * you can use the .js file directly - see this add-on for an example:
     * https://ankiweb.net/shared/info/2179254157
     */
    @CheckResult
    @LibAnkiAlias("card_stats_data")
    fun cardStatsData(cardId: CardId): CardStatsResponse = backend.cardStats(cardId)

    @CheckResult
    @LibAnkiAlias("get_review_logs")
    fun getReviewLogs(cardId: CardId): List<StatsRevlogEntry> = backend.getReviewLogs(cardId)

    @RustCleanup("check sched.studiedToday")
    @CheckResult
    @LibAnkiAlias("studied_today")
    fun studiedToday(): String = backend.studiedToday()

    /*
     * Undo
     * ***********************************************************
     */

    /** See [UndoStatus] */
    @CheckResult
    @RustCleanup("doesn't match upstream")
    @LibAnkiAlias("undo_status")
    fun undoStatus(): UndoStatus = UndoStatus.from(backend.getUndoStatus())

    /**
     * Add an empty undo entry with the given name.
     * The return value can be used to merge subsequent changes
     * with [mergeUndoEntries].
     *
     * You should only use this with your own custom actions - when
     * extending default Anki behaviour, you should merge into an
     * existing undo entry instead, so the existing undo name is
     * preserved, and changes are processed correctly.
     */
    @LibAnkiAlias("add_custom_undo_entry")
    fun addCustomUndoEntry(name: String): UndoStepCounter = backend.addCustomUndoEntry(name)

    /**
     * Combine multiple undoable operations into one.
     *
     * After a standard Anki action, you can use
     * [undoStatus()][undoStatus].[lastStep][UndoStatus.lastStep] to retrieve the target to
     * merge into. When defining your own custom actions, you can use [addCustomUndoEntry]
     * to define a custom undo name.
     */
    @LibAnkiAlias("merge_undo_entries")
    fun mergeUndoEntries(target: UndoStepCounter): OpChanges = backend.mergeUndoEntries(target)

    /**
     * Undo the last backend operation.
     *
     * Should be called via [undoableOp], which will notify
     * [ChangeManager.Subscriber] of the changes.
     *
     * Will throw if no undo operation is possible (due to legacy code
     * directly mutating the database).
     */
    @LibAnkiAlias("undo")
    fun undo(): OpChangesAfterUndo = backend.undo()

    /**
     * Returns result of backend redo operation, or throws UndoEmpty.
     */
    @RustCleanup("document exception")
    @LibAnkiAlias("redo")
    fun redo(): OpChangesAfterUndo = backend.redo()

    @Deprecated("Not implemented")
    @LibAnkiAlias("op_made_changes")
    fun opMadeChanges(changes: OpChanges): Nothing = TODO()

    /**
     * Return undo status if undo available on backend.
     *
     * If backend has undo available, clear the Kotlin undo state.
     */
    @RustCleanup("docs don't match reality ")
    @LibAnkiAlias("_check_backend_undo_status")
    private fun checkBackendUndoStatus(): UndoStatus? {
        val status = backend.getUndoStatus()
        @Suppress("LiftReturnOrAssignment")
        if (status.undo.any() || status.redo.any()) {
            return UndoStatus.from(status)
        } else {
            return null
        }
    }

    /*
     * DB maintenance
     * ***********************************************************
     */

    /**
     * Fixes and optimizes the database. If any errors are encountered, a list of
     * problems is returned. Throws if DB is unreadable.
     */
    @RustCleanup("doesn't match upstream")
    @LibAnkiAlias("fix_integrity")
    fun fixIntegrity(): List<String> = backend.checkDatabase()

    @LibAnkiAlias("optimize")
    fun optimize() {
        db.execute("vacuum")
        db.execute("analyze")
    }

    /*
     * ***********************************************************
     */

    lateinit var notetypes: Notetypes
        protected set

    /** Change the flag color of the specified cards. flag=0 removes flag. */
    @CheckResult
    @LibAnkiAlias("set_user_flag_for_cards")
    fun setUserFlagForCards(
        cids: Iterable<Long>,
        flag: Int,
    ): OpChangesWithCount = backend.setFlag(cardIds = cids, flag = flag)

    @Deprecated("Recommended to use CollectionManager.setWantsAbort")
    @LibAnkiAlias("set_wants_abort")
    fun setWantsAbort() {
        backend.setWantsAbort()
    }

    @NotInPyLib
    fun setWantsAbortRaw(input: ByteArray): ByteArray = backend.setWantsAbortRaw(input = input)

    @CheckResult
    @LibAnkiAlias("i18n_resources")
    fun i18nResources(modules: Iterable<String>): ByteString = backend.i18nResources(modules)

    /** Takes raw input from TypeScript frontend and returns suitable translations. */
    @CheckResult
    @NotInPyLib
    fun i18nResourcesRaw(input: ByteArray): ByteArray = backend.i18nResourcesRaw(input = input)

    @LibAnkiAlias("abort_media_sync")
    fun abortMediaSync() {
        backend.abortMediaSync()
    }

    @LibAnkiAlias("abort_sync")
    fun abortSync() {
        backend.abortSync()
    }

    @LibAnkiAlias("full_upload_or_download")
    fun fullUploadOrDownload(
        auth: SyncAuth?,
        serverUsn: Int?,
        upload: Boolean,
    ) {
        backend.fullUploadOrDownload(
            fullUploadOrDownloadRequest {
                auth?.let { this.auth = it }
                serverUsn?.let { this.serverUsn = it }
                this.upload = upload
            },
        )
    }

    @LibAnkiAlias("sync_login")
    fun syncLogin(
        username: String,
        password: String,
        endpoint: String?,
    ): SyncAuth =
        backend.syncLogin(
            syncLoginRequest {
                this.username = username
                this.password = password
                // default endpoint used here, if it is null
                if (endpoint != null) {
                    this.endpoint = endpoint
                }
            },
        )

    @LibAnkiAlias("sync_collection")
    fun syncCollection(
        auth: SyncAuth,
        syncMedia: Boolean,
    ): SyncCollectionResponse = backend.syncCollection(auth, syncMedia)

    @LibAnkiAlias("sync_media")
    fun syncMedia(auth: SyncAuth) = backend.syncMedia(auth)

    @CheckResult
    @Suppress("unused")
    @LibAnkiAlias("sync_status")
    fun syncStatus(auth: SyncAuth): SyncStatusResponse = backend.syncStatus(input = auth)

    /** This will throw if the sync failed with an error. */
    @CheckResult
    @LibAnkiAlias("media_sync_status")
    fun mediaSyncStatus(auth: SyncAuth): MediaSyncStatusResponse = backend.mediaSyncStatus()

    @CheckResult
    @LibAnkiAlias("ankihub_login")
    fun ankiHubLogin(
        id: String,
        password: String,
    ): String = backend.ankihubLogin(id, password)

    @LibAnkiAlias("ankihub_logout")
    fun ankiHubLogin(token: String) {
        backend.ankihubLogout(token)
    }

    @CheckResult
    @LibAnkiAlias("get_preferences")
    fun getPreferences(): Preferences = backend.getPreferences()

    @LibAnkiAlias("set_preferences")
    fun setPreferences(preferences: Preferences): OpChanges = backend.setPreferences(preferences)

    @CheckResult
    @Deprecated("Not intended for public consumption at this time.")
    @LibAnkiAlias("render_markdown")
    fun renderMarkdown(
        text: String,
        sanitize: Boolean = true,
    ): String = backend.renderMarkdown(markdown = text, sanitize = sanitize)

    @CheckResult
    @LibAnkiAlias("compare_answer")
    fun compareAnswer(
        expected: String,
        provided: String,
        combining: Boolean = true,
    ): String = backend.compareAnswer(expected = expected, provided = provided, combining = combining)

    @CheckResult
    @LibAnkiAlias("extract_cloze_for_typing")
    fun extractClozeForTyping(
        text: String,
        ordinal: Int,
    ): String = backend.extractClozeForTyping(text = text, ordinal = ordinal)

    @CheckResult
    @LibAnkiAlias("compute_memory_state")
    fun computeMemoryState(cardId: CardId): ComputedMemoryState {
        val resp = backend.computeMemoryState(cardId)
        if (resp.stateOrNull != null) {
            return ComputedMemoryState(
                desiredRetention = resp.desiredRetention,
                stability = resp.state.stability,
                difficulty = resp.state.difficulty,
                decay = resp.decay,
            )
        }
        return ComputedMemoryState(
            desiredRetention = resp.desiredRetention,
            decay = resp.decay,
        )
    }

    /** The delta days of fuzz applied if reviewing the card in v3. */
    @CheckResult
    @LibAnkiAlias("fuzz_delta")
    fun fuzzDelta(
        cardId: CardId,
        interval: Int,
    ): Int = backend.fuzzDelta(cardId = cardId, interval = interval)

    /*
     * Timeboxing
     * ***********************************************************
     * Note: this will likely be removed in a future version of libAnki
     */

    private var startTime: Long = 0L
    private var startReps: Int = 0

    @LibAnkiAlias("startTimebox")
    fun startTimebox() {
        startTime = TimeManager.time.intTime()
        startReps = sched.numberOfAnswersRecorded
    }

    data class TimeboxReached(
        val secs: Int,
        val reps: Int,
    )

    /**
     * Return (elapsedTime, reps) if timebox reached, or null.
     * Automatically restarts timebox if expired.
     */
    @LibAnkiAlias("timeboxReached")
    fun timeboxReached(): TimeboxReached? {
        if (sched.timeboxSecs() == 0) {
            // timeboxing disabled
            return null
        }
        val elapsed = TimeManager.time.intTime() - startTime
        val limit = sched.timeboxSecs()
        return if (elapsed > limit) {
            TimeboxReached(
                limit,
                sched.numberOfAnswersRecorded - startReps,
            ).also {
                startTimebox()
            }
        } else {
            null
        }
    }

    /*
     * Raw methods used by Anki Pages
     * ***********************************************************
     * Not upstream: methods for communication between the Svelte UI and backend
     * These methods should be blocking (e.g. `latestProgress` should directly use the backend)
     */

    @NotInPyLib
    fun getImageForOcclusionRaw(input: ByteArray): ByteArray = backend.getImageForOcclusionRaw(input = input)

    @NotInPyLib
    fun getImageOcclusionNoteRaw(input: ByteArray): ByteArray = backend.getImageOcclusionNoteRaw(input = input)

    @NotInPyLib
    fun getImageOcclusionFieldsRaw(input: ByteArray): ByteArray = backend.getImageOcclusionFieldsRaw(input = input)

    @NotInPyLib
    fun addImageOcclusionNoteRaw(input: ByteArray): ByteArray = backend.addImageOcclusionNoteRaw(input = input)

    @NotInPyLib
    fun updateImageOcclusionNoteRaw(input: ByteArray): ByteArray = backend.updateImageOcclusionNoteRaw(input = input)

    @NotInPyLib
    fun congratsInfoRaw(input: ByteArray): ByteArray = backend.congratsInfoRaw(input = input)

    @NotInPyLib
    fun getSchedulingStatesWithContextRaw(input: ByteArray): ByteArray = backend.getSchedulingStatesWithContextRaw(input = input)

    @NotInPyLib
    fun setSchedulingStatesRaw(input: ByteArray): ByteArray = backend.setSchedulingStatesRaw(input = input)

    @NotInPyLib
    fun getChangeNotetypeInfoRaw(input: ByteArray): ByteArray = backend.getChangeNotetypeInfoRaw(input = input)

    @NotInPyLib
    fun changeNotetypeRaw(input: ByteArray): ByteArray = backend.changeNotetypeRaw(input = input)

    @NotInPyLib
    fun importJsonStringRaw(input: ByteArray): ByteArray = backend.importJsonStringRaw(input = input)

    @NotInPyLib
    fun importJsonFileRaw(input: ByteArray): ByteArray = backend.importJsonFileRaw(input = input)

    @NotInPyLib
    fun getIgnoredBeforeCountRaw(input: ByteArray): ByteArray = backend.getIgnoredBeforeCountRaw(input = input)

    @NotInPyLib
    fun getRetentionWorkloadRaw(input: ByteArray): ByteArray = backend.getRetentionWorkloadRaw(input = input)

    fun simulateFsrsWorkloadRaw(input: ByteArray): ByteArray = backend.simulateFsrsWorkloadRaw(input = input)

    @NotInPyLib
    fun evaluateParamsLegacyRaw(input: ByteArray): ByteArray = backend.evaluateParamsLegacyRaw(input = input)
}

@NotInPyLib
fun EmptyCardsReport.emptyCids(): List<CardId> = notesList.flatMap { it.cardIdsList }

// Python code has a cardsOfNote, but not vice-versa yet
@CheckResult
@NotInPyLib
fun Collection.notesOfCards(cids: Iterable<CardId>): List<NoteId> =
    db.queryLongList("select distinct nid from cards where id in ${ids2str(cids)}")

/**
 * returns the list of cloze ordinals in a note
 *
 * `"{{c1::A}} {{c3::B}}" => [1, 3]`
 */
@CheckResult
@NotInPyLib
fun Collection.clozeNumbersInNote(n: Note): List<Int> {
    // the call appears to be non-deterministic. Sort ascending
    return backend
        .clozeNumbersInNote(n.toBackendNote())
        .sorted()
}

/**
 * Given a list of potential Card Ids, return the subset which are Ids of cards in the collection
 */
@NotInPyLib
@CheckResult
fun Collection.filterToValidCards(cards: LongArray?): List<CardId> = db.queryLongList("select id from cards where id in " + ids2str(cards))

/**
 * @return [File] referencing the media folder (`collection.media`)
 *
 * @throws UnsupportedOperationException if the collection is in-memory
 */
@NotInPyLib
@CheckResult
fun Collection.requireMediaFolder() = collectionFiles.requireMediaFolder()

/**
 * [File] referencing the media folder (`collection.media`)
 *
 * (testing) `null` if the collection is in-memory
 */
@NotInPyLib
@get:CheckResult
val Collection.mediaFolder: File? get() = collectionFiles.mediaFolder

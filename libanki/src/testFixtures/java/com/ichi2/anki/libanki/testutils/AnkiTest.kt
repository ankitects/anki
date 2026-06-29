// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.libanki.testutils

import android.annotation.SuppressLint
import anki.notetypes.Notetype
import anki.notetypes.copy
import com.ichi2.anki.libanki.Card
import com.ichi2.anki.libanki.CardType
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.Consts
import com.ichi2.anki.libanki.Deck
import com.ichi2.anki.libanki.DeckConfig
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.Decks
import com.ichi2.anki.libanki.Note
import com.ichi2.anki.libanki.NoteTypeId
import com.ichi2.anki.libanki.NotetypeJson
import com.ichi2.anki.libanki.Notetypes
import com.ichi2.anki.libanki.QueueType
import com.ichi2.anki.libanki.addNotetype
import com.ichi2.anki.libanki.exception.ConfirmModSchemaException
import com.ichi2.anki.libanki.getNotetype
import com.ichi2.anki.libanki.testutils.ext.addNote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import net.ankiweb.rsdroid.exceptions.BackendDeckIsFilteredException
import timber.log.Timber
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * marker interface for classes which contain tests and access the Anki collection
 *
 * Android (AnkiDroid/Robolectric) is not required for these tests to run
 */
interface AnkiTest {
    val col: Collection

    val collectionManager: TestCollectionManager

    fun addBasicNote(
        front: String = "Front",
        back: String = "Back",
    ): Note = addNoteUsingNoteTypeName("Basic", front, back)

    fun addRevBasicNoteDueToday(
        @Suppress("SameParameterValue") front: String,
        @Suppress("SameParameterValue") back: String,
    ): Note {
        val note = addBasicNote(front, back)
        val card = note.firstCard()
        card.queue = QueueType.Rev
        card.type = CardType.Rev
        card.due = col.sched.today
        col.updateCards(listOf(card), skipUndoEntry = true)
        return note
    }

    fun addBasicAndReversedNote(
        front: String = "Front",
        back: String = "Back",
    ): Note = addNoteUsingNoteTypeName("Basic (and reversed card)", front, back)

    fun addBasicWithTypingNote(
        @Suppress("SameParameterValue") front: String,
        @Suppress("SameParameterValue") back: String,
    ): Note = addNoteUsingNoteTypeName("Basic (type in the answer)", front, back)

    fun addClozeNote(
        text: String,
        extra: String = "Extra",
    ): Note =
        col.newNote(col.notetypes.byName("Cloze")!!).apply {
            setItem("Text", text)
            col.addNote(this)
        }

    fun addNoteUsingNoteTypeName(
        name: String?,
        vararg fields: String,
    ): Note {
        val noteType =
            col.notetypes.byName((name)!!)
                ?: throw IllegalArgumentException("Could not find note type '$name'")
        // PERF: if we modify newNote(), we can return the card and return a Pair<Note, Card> here.
        // Saves a database trip afterwards.
        val n = col.newNote(noteType)
        for ((i, field) in fields.withIndex()) {
            n.setField(i, field)
        }
        check(col.addNote(n) != 0) { "Could not add note: {${fields.joinToString(separator = ", ")}}" }
        return n
    }

    /**
     * Create a new note type in the collection.
     * @param name the name of the note type
     * @param fields the name of the fields of the note type
     * @param qfmt the question template
     * @param afmt the answer template
     * @return the name of note type, [name]
     */
    fun addStandardNoteType(
        name: String,
        fields: Array<String>,
        qfmt: String,
        afmt: String,
        templateCount: Int = 1,
    ): String {
        val noteType = col.notetypes.new(name)
        for (field in fields) {
            col.notetypes.addFieldLegacy(noteType, col.notetypes.newField(field))
        }
        repeat(templateCount) { idx ->
            val t =
                Notetypes.newTemplate("Card ${idx + 1}").also { tmpl ->
                    tmpl.qfmt = qfmt
                    tmpl.afmt = afmt
                }
            col.notetypes.addTemplate(noteType, t)
        }
        col.notetypes.add(noteType)
        return name
    }

    fun addClozeNoteType(name: String = "Cloze2"): NoteTypeId =
        col
            .addNotetype(
                col.notetypes.cloze.proto().copy {
                    id = 0
                    this.name = name
                },
            ).id

    /** Adds a note with Text to Speech functionality */
    fun addTextToSpeechNote(
        front: String,
        back: String,
    ) {
        addStandardNoteType("TTS", arrayOf("Front", "Back"), "{{Front}}{{tts en_GB:Front}}", "{{tts en_GB:Front}}<br>{{Back}}")
        addNoteUsingNoteTypeName("TTS", front, back)
    }

    fun addField(
        notetype: NotetypeJson,
        name: String,
    ) {
        try {
            col.notetypes.addFieldLegacy(notetype, col.notetypes.newField(name))
        } catch (e: ConfirmModSchemaException) {
            throw RuntimeException(e)
        }
    }

    fun ensureCollectionLoadIsSynchronous() {
        // HACK: We perform this to ensure that onCollectionLoaded is performed synchronously when startLoadingCollection
        // is called.
        col
    }

    fun addDeck(
        deckName: String?,
        setAsSelected: Boolean = false,
    ): DeckId =
        try {
            col.decks.id(deckName!!).also { did ->
                if (setAsSelected) col.decks.select(did)
            }
        } catch (filteredAncestor: BackendDeckIsFilteredException) {
            throw RuntimeException(filteredAncestor)
        }

    fun addDynamicDeck(
        name: String,
        search: String? = null,
    ): DeckId {
        return try {
            col.decks.newFiltered(name).also { did ->
                if (search == null) return@also
                val deck = col.decks.getLegacy(did)!!
                deck.getJSONArray("terms").getJSONArray(0).put(0, search)
                col.decks.save(deck)
                col.sched.rebuildFilteredDeck(did)
            }
        } catch (filteredAncestor: BackendDeckIsFilteredException) {
            throw RuntimeException(filteredAncestor)
        }
    }

    /** Ensures `DeckUtils.isCollectionEmpty` returns `false` */
    fun ensureNonEmptyCollection() {
        addNotes(1)
    }

    fun selectDefaultDeck() {
        col.decks.select(Consts.DEFAULT_DECK_ID)
    }

    /**
     * Returns the 'Custom Study Session' deck if present, null otherwise.
     * TODO this matches directly on 'Custom Study Session' and doesn't handle i18n!
     */
    val Decks.customStudySession: Deck?
        get() = byName("Custom Study Session")

    /** Adds [count] notes in the same deck with the same front & back */
    fun addNotes(
        count: Int,
        front: String = "Front",
    ): List<Note> = List(count) { addBasicNote(front = front) }

    /**
     * Adds [count] notes into the specified [queueType] of the provided deck.
     */
    fun addNoteToDeck(
        deckId: DeckId,
        count: Int = 1,
        queueType: QueueType = QueueType.New,
    ) = addNotes(count).forEach {
        it.firstCard().update {
            did = deckId
            queue = queueType
        }
    }

    /**
     * Convenience method for chaining [addDeck] and [addNoteToDeck].
     *
     * Usage: `val deckId = addDeck("My Deck").withNotes(count = 5, queueType = QueueType.New)`
     */
    fun DeckId.withNotes(
        count: Int,
        queueType: QueueType = QueueType.New,
    ): DeckId =
        this.apply {
            addNoteToDeck(this, count = count, queueType = queueType)
        }

    /**
     * Convenience method for chaining [addDeck] and [addNoteToDeck].
     *
     * Usage: `val deckId = addDeck("My Deck").withNote(queueType = QueueType.New)`
     */
    fun DeckId.withNote(queueType: QueueType = QueueType.New): DeckId =
        this.apply {
            addNoteToDeck(this, count = 1, queueType = queueType)
        }

    fun Note.moveToDeck(
        deckName: String,
        createDeckIfMissing: Boolean = true,
    ) {
        val deckId: DeckId? =
            if (createDeckIfMissing) {
                col.decks.id(deckName)
            } else {
                col.decks.idForName(deckName)
            }
        check(deckId != null) { "$deckName not found" }

        updateCards { did = deckId }
    }

    /** helper method to update deck config */
    fun updateDeckConfig(
        deckId: DeckId,
        function: DeckConfig.() -> Unit,
    ) {
        val deckConfig = col.decks.configDictForDeckId(deckId)
        function(deckConfig)
        col.decks.save(deckConfig)
    }

    /** Helper method to update a note */
    @SuppressLint("CheckResult")
    fun Note.update(block: Note.() -> Unit): Note {
        block(this)
        col.updateNote(this)
        return this
    }

    /** Helper method to all cards of a note */
    fun Note.updateCards(update: Card.() -> Unit): Note {
        cards().forEach { it.update(update) }
        return this
    }

    /** Helper method to update a card */
    fun Card.update(update: Card.() -> Unit): Card {
        update(this)
        this@AnkiTest.col.updateCard(this, skipUndoEntry = true)
        return this
    }

    /** Helper method to suspend all cards of a note */
    fun Note.suspendAll(): Note {
        col.sched.suspendCards(cardIds(col))
        return this
    }

    fun NotetypeJson.createClone(): NotetypeJson {
        val targetNotetype = requireNotNull(col.notetypes.byName(name)) { "could not find note type '$name'" }
        val newNotetype =
            targetNotetype.deepClone().apply {
                id = 0
                name = "$name+"
            }
        col.notetypes.add(newNotetype)
        return col.notetypes.byName("$name+")!!
    }

    /** Returns the note types matching [predicate] */
    fun Notetypes.filter(predicate: (NotetypeJson) -> Boolean): List<NotetypeJson> = all().filter { predicate(it) }

    fun Card.note() = this.note(col)

    fun Card.note(reload: Boolean) = this.note(col, reload)

    fun Card.noteType() = this.noteType(col)

    fun Card.template() = this.template(col)

    fun Card.question() = this.question(col)

    fun Card.question(
        reload: Boolean = false,
        browser: Boolean = false,
    ) = this.question(col, reload, browser)

    fun Card.answer() = this.answer(col)

    fun Card.load() = this.load(col)

    fun Note.load() = this.load(col)

    fun Note.cards() = this.cards(col)

    fun Note.firstCard() = this.firstCard(col)

    fun Note.cids() = this.cardIds(col)

    fun Note.numberOfCards() = this.numberOfCards(col)

    // TODO remove this. not in libanki
    @SuppressLint("CheckResult")
    fun Note.flush() {
        col.updateNote(this)
    }

    fun setupTestDispatcher(dispatcher: TestDispatcher) {
    }

    /** * A wrapper around the standard [kotlinx.coroutines.test.runTest] that
     * takes care of updating the dispatcher used by CollectionManager as well.
     * * An argument could be made for using [StandardTestDispatcher] and
     * explicitly advanced coroutines with advanceUntilIdle(), but there are
     * issues with using it at the moment:
     * * - Any usage of CollectionManager with runBlocking() will hang. tearDown()
     * calls runBlocking() twice, which prevents tests from finishing.
     * - The hang is not limited to the scope of runTest(). Even if the runBlocking
     * calls in tearDown() are selectively moved into this function,
     * when a coroutine test fails, the next regular test
     * that executes after it will call runBlocking(), and it then hangs.
     *
     * A fix for this might require either wrapping all tests in runTest(),
     * or finding some other way to isolate the coroutine and non-coroutine tests
     * on separate threads/processes.
     *
     * @param dispatchTimeout The test fails with an AssertionError if not completed within this
     * time
     * */
    fun runTest(
        context: CoroutineContext = EmptyCoroutineContext,
        dispatchTimeout: Duration = DEFAULT_TEST_TIMEOUT,
        times: Int = 1,
        testBody: suspend TestScope.() -> Unit,
    ) {
        // Use a unified scheduler on `Dispatchers.Main` and runTest uses, so
        // advanceUntilIdle()/runCurrent() handle coroutines launched on `Main`.
        val scheduler =
            (context[ContinuationInterceptor] as? TestDispatcher)?.scheduler
                ?: TestCoroutineScheduler()
        val dispatcher = UnconfinedTestDispatcher(scheduler)
        Dispatchers.setMain(dispatcher)
        setupTestDispatcher(dispatcher)

        repeat(times) {
            if (times != 1) Timber.d("------ Executing test $it/$times ------")
            kotlinx.coroutines.test.runTest(context + scheduler, dispatchTimeout) {
                runTestInner(testBody)
            }
        }
    }

    /** Runs [testBody], supporting [TestScope]-specific setup & teardown */
    suspend fun TestScope.runTestInner(testBody: suspend TestScope.() -> Unit) {
        testBody()
    }

    val Notetypes.basic
        get() = byName("Basic")!!

    val Notetypes.basicAndReversed
        get() = byName("Basic (and reversed card)")!!

    val Notetypes.cloze
        get() = byName("Cloze")!!

    /**
     * Returns the backend protobuf of the note type
     */
    fun NotetypeJson.proto(): Notetype = col.getNotetype(this.id)
}

/**
 * The default timeout for tests. An [AssertionError] is thrown if execution takes longer than this
 * time.
 *
 * Do not change this, instead determine why a test is taking longer than a minute.
 */
val DEFAULT_TEST_TIMEOUT: Duration = 60.seconds

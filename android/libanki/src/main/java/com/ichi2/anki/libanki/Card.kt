/*
 * Copyright (c) 2009 Daniel Svärd daniel.svard@gmail.com>
 * Copyright (c) 2011 Norbert Nagold norbert.nagold@gmail.com>
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

import androidx.annotation.VisibleForTesting
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.common.utils.ext.ifZero
import com.ichi2.anki.libanki.TemplateManager.TemplateRenderContext.TemplateRenderOutput
import com.ichi2.anki.libanki.utils.LibAnkiAlias
import com.ichi2.anki.libanki.utils.NotInPyLib
import net.ankiweb.rsdroid.RustCleanup

private typealias BackendCard = anki.cards.Card
private typealias FSRSMemoryState = anki.cards.FsrsMemoryState

/**
 * Identifies the card template or cloze deletion which the card refers to.
 *
 * Values:
 * - **card templates**: from 0 to [`templates.size - 1`][NotetypeJson.templates]
 * - **cloze deletions**: `{{c1::}}` refers to ord 0. etc...
 *
 * Primarily used during rendering to determine the template to select, or cloze to hide.
 *
 * Defined as [Card.ord]
 */
typealias CardOrdinal = Int

/**
 * A Card is the ultimate entity subject to review; it encapsulates the scheduling parameters (from which to derive
 * the next interval), the note it is derived from (from which field data is retrieved), its own ownership (which deck it
 * currently belongs to), and the retrieval of presentation elements (filled-in templates).
 *
 * Card presentation has two components: the question (front) side and the answer (back) side. The presentation of the
 * card is derived from the template of the card's Card Type. The Card Type is a component of the Note Type (see Models)
 * that this card is derived from.
 *
 * This class is responsible for:
 * - Storing and retrieving database entries that map to Cards in the Collection
 * - Providing the HTML representation of the Card's question and answer
 * - Recording the results of review (answer chosen, time taken, etc)
 *
 * It does not:
 * - Generate new cards (see [Collection])
 * - Store the templates or the style sheet (see [NotetypeJson])
 *
 * Type: 0=new, 1=learning, 2=due
 * Queue: same as above, and:
 * -1=suspended, -2=user buried, -3=sched buried
 * Due is used differently for different queues.
 * - new queue: note id or random int
 * - rev queue: integer day
 * - lrn queue: integer timestamp
 */
open class Card : Cloneable {
    /**
     * Time in MS when timer was started
     */
    var timerStarted: Long = 0L

    // Record time spent reviewing in MS in order to restore when resuming.
    @NotInPyLib
    private var elapsedTime: Long = 0

    @set:VisibleForTesting
    var id: CardId = 0
    var nid: NoteId = 0
    var did: DeckId = 0

    /**
     * @see CardOrdinal
     */
    var ord: CardOrdinal = 0
    var mod: Long = 0
    private var usn = 0

    var type: CardType = CardType.New

    var queue: QueueType = QueueType.New
    var due: Int = 0
    var ivl = 0
    var factor = 0

    @set:VisibleForTesting
    var reps = 0
    var lapses = 0
    var left = 0
    var oDue: Int = 0
    var oDid: DeckId = 0
    var originalPosition: Int? = null
    var customData: String = ""
        private set

    @VisibleForTesting
    var flags = 0
    var memoryState: FSRSMemoryState? = null
        private set
    var desiredRetention: Float? = null
        private set
    var decay: Float? = null
        private set

    var lastReviewTimeSecs: Long? = null
        private set

    var renderOutput: TemplateRenderOutput? = null
    var note: Note? = null

    constructor(card: BackendCard) {
        loadFromBackendCard(card)
    }

    constructor(col: Collection, id: CardId? = null) {
        if (id != null) {
            this.id = id
            load(col)
        } else {
            loadFromBackendCard(BackendCard.getDefaultInstance())
        }
    }

    @LibAnkiAlias("load")
    fun load(col: Collection) {
        val card = col.backend.getCard(id)
        loadFromBackendCard(card)
    }

    @LibAnkiAlias("_load_from_backend_card")
    private fun loadFromBackendCard(card: BackendCard) {
        renderOutput = null
        note = null
        id = card.id
        nid = card.noteId
        did = card.deckId
        ord = card.templateIdx
        mod = card.mtimeSecs
        usn = card.usn
        type = CardType.fromCode(card.ctype)
        queue = QueueType.fromCode(card.queue)
        due = card.due
        ivl = card.interval
        factor = card.easeFactor
        reps = card.reps
        lapses = card.lapses
        left = card.remainingSteps
        oDue = card.originalDue
        oDid = card.originalDeckId
        flags = card.flags
        originalPosition = if (card.hasOriginalPosition()) card.originalPosition else null
        customData = card.customData
        memoryState = if (card.hasMemoryState()) card.memoryState else null
        desiredRetention = if (card.hasDesiredRetention()) card.desiredRetention else null
        decay = if (card.hasDecay()) card.decay else null
        lastReviewTimeSecs = if (card.hasLastReviewTimeSecs()) card.lastReviewTimeSecs else null
    }

    @LibAnkiAlias("_to_backend_card")
    fun toBackendCard() =
        anki.cards.card {
            id = this@Card.id
            noteId = nid
            deckId = did
            templateIdx = ord
            ctype = type.code
            queue = this@Card.queue.code
            due = this@Card.due
            interval = ivl
            easeFactor = factor
            reps = this@Card.reps
            lapses = this@Card.lapses
            remainingSteps = left
            originalDue = oDue
            originalDeckId = oDid
            flags = this@Card.flags
            customData = this@Card.customData
            this@Card.originalPosition?.let { originalPosition = it }
            this@Card.memoryState?.let { memoryState = it }
            this@Card.desiredRetention?.let { desiredRetention = it }
            this@Card.decay?.let { decay = it }
            this@Card.lastReviewTimeSecs?.let { lastReviewTimeSecs = it }
        }

    @LibAnkiAlias("question")
    fun question(
        col: Collection,
        reload: Boolean = false,
        browser: Boolean = false,
    ): String = renderOutput(col, reload, browser).questionAndStyle()

    @LibAnkiAlias("answer")
    fun answer(col: Collection): String = renderOutput(col).answerAndStyle()

    @LibAnkiAlias("question_av_tags")
    fun questionAvTags(col: Collection): List<AvTag> = renderOutput(col).questionAvTags

    @LibAnkiAlias("answer_av_tags")
    fun answerAvTags(col: Collection): List<AvTag> = renderOutput(col).answerAvTags

    /**
     * @throws net.ankiweb.rsdroid.exceptions.BackendInvalidInputException: If the card does not exist
     */
    @LibAnkiAlias("render_output")
    open fun renderOutput(
        col: Collection,
        reload: Boolean = false,
        browser: Boolean = false,
    ): TemplateRenderOutput {
        if (renderOutput == null || reload) {
            renderOutput = TemplateManager.TemplateRenderContext.fromExistingCard(col, this, browser).render(col)
        }
        return renderOutput!!
    }

    @LibAnkiAlias("note")
    open fun note(
        col: Collection,
        reload: Boolean = false,
    ): Note {
        if (note == null || reload) {
            note = col.getNote(nid)
        }
        return note!!
    }

    @LibAnkiAlias("note_type")
    open fun noteType(col: Collection): NotetypeJson = note(col).notetype

    @LibAnkiAlias("template")
    fun template(col: Collection): CardTemplate {
        val notetype = noteType(col)
        val templates = notetype.templates
        return if (notetype.isStd) {
            templates[ord]
        } else {
            templates[0]
        }
    }

    @LibAnkiAlias("start_timer")
    fun startTimer() {
        timerStarted = TimeManager.time.intTimeMS()
    }

    @LibAnkiAlias("current_deck_id")
    fun currentDeckId() = oDid.ifZero { did }

    /**
     * Time limit for answering in milliseconds.
     */
    @LibAnkiAlias("time_limit")
    fun timeLimit(col: Collection): Int {
        val conf = col.decks.configDictForDeckId(currentDeckId())
        return conf.maxTaken * 1000
    }

    /*
     * Time taken to answer card, in integer MS.
     */
    @LibAnkiAlias("time_taken")
    fun timeTaken(col: Collection): Int {
        // Indeed an int. Difference between two big numbers is still small.
        val total = (TimeManager.time.intTimeMS() - timerStarted).toInt()
        return kotlin.math.min(total, timeLimit(col))
    }

    /**
     * Save the currently elapsed reviewing time so it can be restored on resume.
     *
     * Use this method whenever a review session (activity) has been paused. Use the resumeTimer()
     * method when the session resumes to start counting review time again.
     */
    @NotInPyLib
    fun stopTimer() {
        elapsedTime = TimeManager.time.intTimeMS() - timerStarted
    }

    /**
     * Resume the timer that counts the time spent reviewing this card.
     *
     * Unlike the desktop client, AnkiDroid must pause and resume the process in the middle of
     * reviewing. This method is required to keep track of the actual amount of time spent in
     * the reviewer and *must* be called on resume before any calls to timeTaken(col) take place
     * or the result of timeTaken(col) will be wrong.
     */
    @NotInPyLib
    fun resumeTimer() {
        timerStarted = TimeManager.time.intTimeMS() - elapsedTime
    }

    @LibAnkiAlias("should_show_timer")
    fun shouldShowTimer(col: Collection): Boolean {
        val conf = col.decks.configDictForDeckId(currentDeckId())
        return conf.timer
    }

    @LibAnkiAlias("replay_question_audio_on_answer_side")
    fun replayQuestionAudioOnAnswerSide(col: Collection) = col.decks.configDictForDeckId(currentDeckId()).replayq

    @LibAnkiAlias("autoplay")
    fun autoplay(col: Collection): Boolean = col.decks.configDictForDeckId(currentDeckId()).autoplay

    @NotInPyLib
    public override fun clone(): Card =
        try {
            super.clone() as Card
        } catch (e: CloneNotSupportedException) {
            throw RuntimeException(e)
        }

    @LibAnkiAlias("description")
    override fun toString(): String {
        val declaredFields = this.javaClass.declaredFields
        val members: MutableList<String?> = ArrayList(declaredFields.size)
        for (f in declaredFields) {
            try {
                // skip non-useful elements
                if (SKIP_PRINT.contains(f.name)) {
                    continue
                }
                members.add("'${f.name}': ${f[this]}")
            } catch (e: IllegalAccessException) {
                members.add("'${f.name}': N/A")
            } catch (e: IllegalArgumentException) {
                members.add("'${f.name}': N/A")
            }
        }
        return members.joinToString(",  ")
    }

    override fun equals(other: Any?): Boolean =
        if (other is Card) {
            this.id == other.id
        } else {
            super.equals(other)
        }

    override fun hashCode(): Int {
        // Map a long to an int. For API>=24 you would just do `Long.hashCode(this.getId())`
        return (this.id xor (this.id ushr 32)).toInt()
    }

    // upstream's function returns an int between 0 and 7 (included).
    @LibAnkiAlias("user_flag")
    fun userFlag() = flags and 0b111

    /**
     * Set the first three bits of [flags] to [flag]. Don't change the other ones.
     */
    // Upstream's function take an int and raise if it's not between 0 and 7 included.
    // We take a flag for the sake of typing clarity.
    @RustCleanup("deprecated in Anki: use col.set_user_flag_for_cards() instead")
    @LibAnkiAlias("set_user_flag")
    fun setUserFlag(flag: Int) {
        flags = setFlagInInt(flags, flag)
    }

    companion object {
        /** A list of class members to skip in the [toString] representation */
        @NotInPyLib // inlined in pylib: 'description'
        val SKIP_PRINT: Set<String> =
            HashSet(
                listOf(
                    "Companion",
                    "SKIP_PRINT",
                    "\$assertionsDisabled",
                    "note",
                    "renderOutput",
                    "timerStarted",
                    "col",
                ),
            )

        /**
         * Returns [flags] with the 3 first bits set as in [flag]
         */
        @NotInPyLib
        fun setFlagInInt(
            flags: Int,
            flag: Int,
        ): Int {
            require(flag in 0..7) { "flag outside of expected [0, 7] interval" }
            // Setting the 3 firsts bits to 0, keeping the remaining.
            val extraData = flags and 7.inv()
            // flag in 3 fist bits, same data as in mFlags everywhere else
            return extraData or flag
        }
    }
}

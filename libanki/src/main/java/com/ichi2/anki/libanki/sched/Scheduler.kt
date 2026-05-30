/*
 * Copyright (c) 2022 Ankitects Pty Ltd <https://apps.ankiweb.net>
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

package com.ichi2.anki.libanki.sched

import androidx.annotation.CheckResult
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import anki.collection.OpChanges
import anki.collection.OpChangesWithCount
import anki.config.ConfigKey
import anki.config.OptionalStringConfigKey
import anki.config.optionalStringConfigKey
import anki.decks.DeckTreeNode
import anki.decks.FilteredDeckForUpdate
import anki.frontend.SchedulingStatesWithContext
import anki.i18n.FormatTimespanRequest
import anki.scheduler.BuryOrSuspendCardsRequest
import anki.scheduler.CardAnswer
import anki.scheduler.CardAnswer.Rating
import anki.scheduler.CongratsInfoResponse
import anki.scheduler.CustomStudyDefaultsResponse
import anki.scheduler.CustomStudyRequest
import anki.scheduler.QueuedCards
import anki.scheduler.RepositionDefaultsResponse
import anki.scheduler.SchedTimingTodayResponse
import anki.scheduler.SchedulingContext
import anki.scheduler.SchedulingState
import anki.scheduler.SchedulingStates
import anki.scheduler.UnburyDeckRequest
import anki.scheduler.cardAnswer
import anki.scheduler.scheduleCardsAsNewRequest
import com.ichi2.anki.common.time.SECONDS_PER_DAY
import com.ichi2.anki.common.time.TimeManager.time
import com.ichi2.anki.libanki.Card
import com.ichi2.anki.libanki.CardId
import com.ichi2.anki.libanki.CardType
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.DeckConfig
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.EpochSeconds
import com.ichi2.anki.libanki.NoteId
import com.ichi2.anki.libanki.QueueType
import com.ichi2.anki.libanki.Utils
import com.ichi2.anki.libanki.utils.LibAnkiAlias
import com.ichi2.anki.libanki.utils.NotInPyLib
import net.ankiweb.rsdroid.RustCleanup
import timber.log.Timber
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * A parameter for [Scheduler.setDueDate]
 * This contains 3 elements:
 * * start (int)
 * * end (int - optional)
 * * change interval (optional - represented as a "!" suffix)
 *
 * examples:
 * ```
 * 0 = today
 * 1! = tomorrow + change interval to 1
 * 3-7 = random choice of 3-7 days
 * ```
 */
@JvmInline
@NotInPyLib
value class SetDueDateDays(
    val value: String,
)

data class CurrentQueueState(
    val topCard: Card,
    val countsIndex: Counts.Queue,
    var states: SchedulingStates,
    val context: SchedulingContext,
    val counts: Counts,
    val timeboxReached: Collection.TimeboxReached?,
    val learnAheadSecs: Int,
    val customSchedulingJs: String,
) {
    fun schedulingStatesWithContext(): SchedulingStatesWithContext =
        anki.frontend.schedulingStatesWithContext {
            states = this@CurrentQueueState.states
            context = this@CurrentQueueState.context
        }
}

@WorkerThread
open class Scheduler(
    val col: Collection,
) {
    /** Legacy API */
    open val card: Card?
        get() =
            queuedCards.cardsList.firstOrNull()?.card?.let {
                Card(it).apply { startTimer() }
            }

    fun currentQueueState(): CurrentQueueState? {
        val queue = queuedCards
        return queue.cardsList.firstOrNull()?.let {
            CurrentQueueState(
                topCard = Card(it.card).apply { startTimer() },
                countsIndex =
                    when (it.queue) {
                        QueuedCards.Queue.NEW -> Counts.Queue.NEW
                        QueuedCards.Queue.LEARNING -> Counts.Queue.LRN
                        QueuedCards.Queue.REVIEW -> Counts.Queue.REV
                        QueuedCards.Queue.UNRECOGNIZED, null -> TODO("unrecognized queue")
                    },
                states = it.states,
                context = it.context,
                counts = Counts(queue.newCount, queue.learningCount, queue.reviewCount),
                timeboxReached = col.timeboxReached(),
                learnAheadSecs = learnAheadSeconds(),
                customSchedulingJs = col.config.get("cardStateCustomizer") ?: "",
            )
        }
    }

    /** The time labels for the four answer buttons. */
    @LibAnkiAlias("describe_next_states")
    fun describeNextStates(states: SchedulingStates): List<String> = col.backend.describeNextStates(states)

    private val queuedCards: QueuedCards
        get() = col.backend.getQueuedCards(fetchLimit = 1, intradayLearningOnly = false)

    open fun answerCard(
        info: CurrentQueueState,
        rating: Rating,
    ): OpChanges =
        col.backend.answerCard(buildAnswer(info.topCard, info.states, rating)).also {
            numberOfAnswersRecorded += 1
        }

    /** Legacy path, used by tests. */
    open fun answerCard(
        card: Card,
        rating: Rating,
    ) {
        val states = col.backend.getSchedulingStates(card.id)
        col.backend.answerCard(
            buildAnswer(card = card, states = states, rating = rating),
        )
        numberOfAnswersRecorded += 1
        // tests assume the card was mutated
        card.load(col)
    }

    /** True if new state marks the card as a leech. */
    @LibAnkiAlias("state_is_leech")
    fun stateIsLeech(state: SchedulingState): Boolean = col.backend.stateIsLeech(state)

    fun buildAnswer(
        card: Card,
        states: SchedulingStates,
        rating: Rating,
    ): CardAnswer =
        cardAnswer {
            cardId = card.id
            currentState = states.current
            newState = stateFromEase(states, rating)
            this.rating = rating
            answeredAtMillis = time.intTimeMS()
            millisecondsTaken = card.timeTaken(col)
        }

    /** Update card to provided state, and remove it from queue. */
    @LibAnkiAlias("answer_card")
    fun answerCard(input: CardAnswer): OpChanges = col.backend.answerCard(input)

    /**
     * @return Number of new, rev and lrn card to review in selected deck. Sum of elements of counts.
     */
    fun totalCount(): Int = counts().count()

    fun counts(): Counts =
        queuedCards.let {
            Counts(it.newCount, it.learningCount, it.reviewCount)
        }

    // only used by tests
    fun newCount(): Int = counts().new

    // only used by a test
    fun lrnCount(): Int = counts().lrn

    /** Only used by tests. */
    fun countIdx(): Counts.Queue =
        when (queuedCards.cardsList.first().queue) {
            QueuedCards.Queue.NEW -> Counts.Queue.NEW
            QueuedCards.Queue.LEARNING -> Counts.Queue.LRN
            QueuedCards.Queue.REVIEW -> Counts.Queue.REV
            QueuedCards.Queue.UNRECOGNIZED, null -> TODO("unrecognized queue")
        }

    /**
     * Number of [answerCard] was called since this scheduler object was created.
     * Note that when the user undo a review, this number is not decremented.
     */
    var numberOfAnswersRecorded: Int = 0

    /** Only provided for legacy unit tests. */
    fun nextIvl(
        card: Card,
        rating: Rating,
    ): Long {
        val states = col.backend.getSchedulingStates(card.id)
        val state = stateFromEase(states, rating)
        return intervalForState(state)
    }

    /**
     * Update a V1 scheduler collection to V2. Requires full sync.
     *
     * @throws com.ichi2.anki.libanki.exception.ConfirmModSchemaException
     */
    fun upgradeToV2() {
        col.modSchema(check = true)
        col.backend.upgradeScheduler()
        col._loadScheduler()
    }

    /**
     * @param cids Ids of cards to bury
     */
    fun buryCards(cids: Iterable<CardId>): OpChangesWithCount = buryCards(cids, manual = true)

    /**
     * @param cids Ids of cards to unbury
     */
    fun unburyCards(cids: Iterable<CardId>): OpChanges = col.backend.restoreBuriedAndSuspendedCards(cids)

    /**
     * @param ids Id of cards to suspend
     */
    open fun suspendCards(ids: Iterable<CardId>): OpChangesWithCount {
        val cids = ids.toList()
        Timber.i("suspending %d card(s)", cids.size)
        return col.backend.buryOrSuspendCards(
            cardIds = cids,
            noteIds = listOf(),
            mode = BuryOrSuspendCardsRequest.Mode.SUSPEND,
        )
    }

    open fun suspendNotes(ids: Iterable<NoteId>): OpChangesWithCount =
        col.backend.buryOrSuspendCards(
            cardIds = listOf(),
            noteIds = ids,
            mode = BuryOrSuspendCardsRequest.Mode.SUSPEND,
        )

    /**
     * @param ids Id of cards to unsuspend
     */
    open fun unsuspendCards(ids: Iterable<CardId>): OpChanges {
        val cids = ids.toList()
        Timber.i("unsuspending %d card(s)", cids.size)
        return col.backend.restoreBuriedAndSuspendedCards(
            cids = cids,
        )
    }

    /**
     * @param cids Ids of the cards to bury
     * @param manual Whether bury is made manually or not. Only useful for sched v2.
     */
    @VisibleForTesting
    open fun buryCards(
        cids: Iterable<CardId>,
        manual: Boolean,
    ): OpChangesWithCount {
        val mode =
            if (manual) {
                BuryOrSuspendCardsRequest.Mode.BURY_USER
            } else {
                BuryOrSuspendCardsRequest.Mode.BURY_SCHED
            }
        return col.backend.buryOrSuspendCards(
            cardIds = cids,
            noteIds = listOf(),
            mode = mode,
        )
    }

    /**
     * Bury all cards for note until next session.
     * @param nids The id of the targeted note.
     */
    open fun buryNotes(nids: List<NoteId>): OpChangesWithCount =
        col.backend.buryOrSuspendCards(
            cardIds = listOf(),
            noteIds = nids,
            mode = BuryOrSuspendCardsRequest.Mode.BURY_USER,
        )

    @RustCleanup("check if callers use the correct UnburyDeckRequest.Mode for their cases")
    fun unburyDeck(
        deckId: DeckId,
        mode: UnburyDeckRequest.Mode = UnburyDeckRequest.Mode.ALL,
    ): OpChanges = col.backend.unburyDeck(deckId, mode)

    /**
     * @return Whether there are buried card is selected deck
     */
    fun haveBuried(): Boolean {
        val info = congratulationsInfo()
        return info.haveUserBuried || info.haveSchedBuried
    }

    /** @return whether there are cards in learning, with review due the same
     * day, in the selected decks.
     */
    open fun hasCardsTodayAfterStudyAheadLimit(): Boolean = col.backend.congratsInfo().secsUntilNextLearn < 86_400

    /**
     * @param ids Ids of cards to put at the end of the new queue.
     */
    open fun forgetCards(
        ids: List<CardId>,
        restorePosition: Boolean = false,
        resetCounts: Boolean = false,
    ): OpChanges {
        val request =
            scheduleCardsAsNewRequest {
                cardIds.addAll(ids)
                log = true
                this.restorePosition = restorePosition
                this.resetCounts = resetCounts
            }
        return col.backend.scheduleCardsAsNew(request)
    }

    /**
     * Put cards in review queue with a new interval in days (min, max).
     *
     * @param ids The list of card ids to be affected
     * @param imin the minimum interval (inclusive)
     * @param imax The maximum interval (inclusive)
     */
    open fun reschedCards(
        ids: List<CardId>,
        imin: Int,
        imax: Int,
    ): OpChanges = col.backend.setDueDate(ids, "$imin-$imax!", OptionalStringConfigKey.getDefaultInstance())

    /**
     * Set cards to be due in [days], turning them into review cards if necessary.
     * `days` can be of the form '5' or '5..7'. See [SetDueDateDays]
     * If `config_key` is provided, provided days will be remembered in config.
     */
    fun setDueDate(
        cardIds: List<CardId>,
        days: SetDueDateDays,
        configKey: ConfigKey.String? = null,
    ): OpChanges {
        val key: OptionalStringConfigKey?
        if (configKey != null) {
            key = optionalStringConfigKey { this.key = configKey }
        } else {
            key = null
        }

        Timber.i("updating due date of %d card(s) to '%s'", cardIds.size, days.value)

        return col.backend.setDueDate(
            cardIds = cardIds,
            days = days.value,
            // this value is optional; the auto-generated typing is wrong
            configKey = key ?: OptionalStringConfigKey.getDefaultInstance(),
        )
    }

    /**
     * @param cids Ids of card to set to new and sort
     * @param start The lowest due value for those cards
     * @param step The step between two successive due value set to those cards
     * @param shuffle Whether the list should be shuffled.
     * @param shift Whether the cards already new should be shifted to make room for cards of cids
     */
    open fun sortCards(
        cids: List<CardId>,
        start: Int,
        step: Int = 1,
        shuffle: Boolean = false,
        shift: Boolean = false,
    ): OpChangesWithCount =
        col.backend.sortCards(
            cardIds = cids,
            startingFrom = start,
            stepSize = step,
            randomize = shuffle,
            shiftExisting = shift,
        )

    /**
     * Randomize the cards of did
     * @param did Id of a deck
     */
    open fun randomizeCards(did: DeckId) {
        col.backend.sortDeck(deckId = did, randomize = true)
    }

    /**
     * Sort the cards of deck `id` by creation date of the note
     * @param did Id of a deck
     */
    open fun orderCards(did: DeckId) {
        col.backend.sortDeck(deckId = did, randomize = false)
    }

    /**
     * @param newc Extra number of NEW cards to see today in selected deck
     * @param rev Extra number of REV cards to see today in selected deck
     */
    open fun extendLimits(
        newc: Int,
        rev: Int,
    ) {
        col.backend.extendLimits(
            deckId = col.decks.selected(),
            newDelta = newc,
            reviewDelta = rev,
        )
    }

    /**
     * Rebuilds a filtered deck.
     * @param did id of deck to rebuild. 0 means current deck.
     */
    @LibAnkiAlias("rebuild_filtered_deck")
    fun rebuildFilteredDeck(did: DeckId) = col.backend.rebuildFilteredDeck(did)

    /**
     * Removes all cards from a filtered deck.
     * @param did id of deck to empty. 0 means current deck.
     */
    @LibAnkiAlias("empty_filtered_deck")
    fun emptyFilteredDeck(did: DeckId) = col.backend.emptyFilteredDeck(did)

    /**
     * Gets the filtered deck with given [did] or creates a new one if [did] = 0.
     */
    @LibAnkiAlias("get_or_create_filtered_deck")
    fun getOrCreateFilteredDeck(did: DeckId): FilteredDeckForUpdate = col.backend.getOrCreateFilteredDeck(did = did)

    @LibAnkiAlias("add_or_update_filtered_deck")
    fun addOrUpdateFilteredDeck(input: FilteredDeckForUpdate) = col.backend.addOrUpdateFilteredDeck(input)

    @LibAnkiAlias("filtered_deck_order_labels")
    fun filteredDeckOrderLabels() = col.backend.filteredDeckOrderLabels()

    fun deckDueTree(): DeckNode = deckTree(true)

    /**
     * Returns a tree of decks with counts. If [topDeckId] is provided, only the according subtree is
     * returned.
     *
     * // TODO look into combining this method with parameterless deckDueTree
     */
    @LibAnkiAlias("deck_due_tree")
    fun deckDueTree(topDeckId: DeckId? = null): DeckTreeNode? {
        val tree = col.backend.deckTree(now = time.intTime())
        if (topDeckId != null) {
            return col.decks.findDeckInTree(tree, topDeckId)
        }
        return tree
    }

    fun deckTree(includeCounts: Boolean): DeckNode = DeckNode(col.backend.deckTree(now = if (includeCounts) time.intTime() else 0), "")

    fun deckLimit(): String = Utils.ids2str(col.decks.active())

    fun congratulationsInfo(): CongratsInfoResponse = col.backend.congratsInfo()

    fun haveManuallyBuried(): Boolean = congratulationsInfo().haveUserBuried

    fun haveBuriedSiblings(): Boolean = congratulationsInfo().haveSchedBuried

    @CheckResult
    fun customStudy(request: CustomStudyRequest): OpChanges = col.backend.customStudy(request)

    @CheckResult
    fun customStudyDefaults(deckId: DeckId): CustomStudyDefaultsResponse = col.backend.customStudyDefaults(deckId)

    @CheckResult
    fun repositionDefaults(): RepositionDefaultsResponse = col.backend.repositionDefaults()

    @LibAnkiAlias("active_decks")
    fun activeDecks(): List<DeckId> = col.db.queryLongList("SELECT id FROM active_decks")

    /**
     * @return Number of new card in current deck and its descendants. Capped at [REPORT_LIMIT]
     */
    @Suppress("ktlint:standard:max-line-length")
    fun totalNewForCurrentDeck(): Int =
        col.db.queryScalar(
            "SELECT count() FROM cards WHERE id IN (SELECT id FROM cards WHERE did IN ${deckLimit()} AND queue = ${QueueType.New.code} LIMIT ?)",
            REPORT_LIMIT,
        )

    fun studiedToday(): String = col.backend.studiedToday()

    /**
     * @return Number of days since creation of the collection.
     */
    open val today: Int
        get() = timingToday().daysElapsed

    /**
     * @return Timestamp of when the day ends. Takes into account hour at which day change for anki and timezone
     */
    open val dayCutoff: EpochSeconds
        get() = timingToday().nextDayAt

    private fun timingToday(): SchedTimingTodayResponse = col.backend.schedTimingToday()

    /** true if there are any rev cards due.  */
    @Suppress("ktlint:standard:max-line-length")
    open fun revDue() =
        col.db
            .queryScalar(
                """SELECT 1 FROM cards WHERE did IN ${deckLimit()} AND queue = ${QueueType.Rev.code} AND due <= ? LIMIT 1""",
                today,
            ) != 0

    /** true if there are any new cards due.  */
    @Suppress("ktlint:standard:max-line-length")
    open fun newDue(): Boolean =
        col.db.queryScalar("SELECT 1 FROM cards WHERE did IN ${deckLimit()} AND queue = ${QueueType.New.code} LIMIT 1") !=
            0

    private val etaCache: DoubleArray = doubleArrayOf(-1.0, -1.0, -1.0, -1.0, -1.0, -1.0)

    /**
     * Return an estimate, in minutes, for how long it will take to complete all the reps in `counts`.
     *
     * The estimator builds rates for each queue type by looking at 10 days of history from the revlog table. For
     * efficiency, and to maintain the same rates for a review session, the rates are cached and reused until a
     * reload is forced.
     *
     * Notes:
     * - Because the revlog table does not record deck IDs, the rates cannot be reduced to a single deck and thus cover
     * the whole collection which may be inaccurate for some decks.
     * - There is no efficient way to determine how many lrn cards are generated by each new card. This estimator
     * assumes 1 card is generated as a compromise.
     * - If there is no revlog data to work with, reasonable defaults are chosen as a compromise to predicting 0 minutes.
     *
     * @param counts An array of [new, lrn, rev] counts from the scheduler's counts() method.
     * @param reload Force rebuild of estimator rates using the revlog.
     */
    @Suppress("ktlint:standard:max-line-length")
    fun eta(
        counts: Counts,
        reload: Boolean = true,
    ): Int {
        var newRate: Double
        var newTime: Double
        var revRate: Double
        var revTime: Double
        var relrnRate: Double
        var relrnTime: Double
        if (reload || etaCache[0] == -1.0) {
            col
                .db
                .query(
                    """select
                          avg(case when type = ${CardType.New.code} then case when ease > 1 then 1.0 else 0.0 end else null end) as newRate,
                          avg(case when type = ${CardType.New.code} then time else null end) as newTime,
                          avg(case when type in (${CardType.Lrn.code}, ${CardType.Relearning.code}) then case when ease > 1 then 1.0 else 0.0 end else null end) as revRate,
                          avg(case when type in (${CardType.Lrn.code}, ${CardType.Relearning.code}) then time else null end) as revTime,
                          avg(case when type = ${CardType.Rev.code} then case when ease > 1 then 1.0 else 0.0 end else null end) as relrnRate,
                          avg(case when type = ${CardType.Rev.code} then time else null end) as relrnTime
                        from revlog where id > ?""",
                    (col.sched.dayCutoff - (10 * SECONDS_PER_DAY)) * 1000,
                ).use { cur ->
                    if (!cur.moveToFirst()) {
                        return -1
                    }
                    newRate = cur.getDouble(0)
                    newTime = cur.getDouble(1)
                    revRate = cur.getDouble(2)
                    revTime = cur.getDouble(3)
                    relrnRate = cur.getDouble(4)
                    relrnTime = cur.getDouble(5)
                }

            // If the collection has no revlog data to work with, assume a 20 second average rep for that type
            newTime = if (newTime == 0.0) 20000.0 else newTime
            revTime = if (revTime == 0.0) 20000.0 else revTime
            relrnTime = if (relrnTime == 0.0) 20000.0 else relrnTime
            // And a 100% success rate
            newRate = if (newRate == 0.0) 1.0 else newRate
            revRate = if (revRate == 0.0) 1.0 else revRate
            relrnRate = if (relrnRate == 0.0) 1.0 else relrnRate
            etaCache[0] = newRate
            etaCache[1] = newTime
            etaCache[2] = revRate
            etaCache[3] = revTime
            etaCache[4] = relrnRate
            etaCache[5] = relrnTime
        } else {
            newRate = etaCache[0]
            newTime = etaCache[1]
            revRate = etaCache[2]
            revTime = etaCache[3]
            relrnRate = etaCache[4]
            relrnTime = etaCache[5]
        }

        // Calculate the total time for each queue based on the historical average duration per rep
        val newTotal = newTime * counts.new
        val relrnTotal = relrnTime * counts.lrn
        val revTotal = revTime * counts.rev

        // Now we have to predict how many additional relrn cards are going to be generated while reviewing the above
        // queues, and how many relrn cards *those* reps will generate (and so on, until 0).

        // Every queue has a failure rate, and each failure will become a relrn
        var toRelrn = counts.new // Assume every new card becomes 1 relrn
        toRelrn += ceil((1 - relrnRate) * counts.lrn).toInt()
        toRelrn += ceil((1 - revRate) * counts.rev).toInt()

        // Use the accuracy rate of the relrn queue to estimate how many reps we will end up with if the cards
        // currently in relrn continue to fail at that rate. Loop through the failures of the failures until we end up
        // with no predicted failures left.

        // Cap the lower end of the success rate to ensure the loop ends (it could be 0 if no revlog history, or
        // negative for other reasons). 5% seems reasonable to ensure the loop doesn't iterate too much.
        relrnRate = max(relrnRate, 0.05)
        var futureReps = 0
        do {
            // Truncation ensures the failure rate always decreases
            val failures = ((1 - relrnRate) * toRelrn).toInt()
            futureReps += failures
            toRelrn = failures
        } while (toRelrn > 1)
        val futureRelrnTotal = relrnTime * futureReps
        return ((newTotal + relrnTotal + revTotal + futureRelrnTotal) / 60000).roundToLong().toInt()
    }

    /** Used only by V1/V2, and unit tests.
     * @param card A random card
     * @return The conf of the deck of the card.
     */
    fun cardConf(card: Card): DeckConfig = col.decks.configDictForDeckId(card.did)

    /*
      Next time reports ********************************************************
     ***************************************
     */

    /**
     * Return the next interval for a card and ease as a string.
     *
     * For a given card and ease, this returns a string that shows when the card will be shown again when the
     * specific ease button (AGAIN, GOOD etc.) is touched. This uses unit symbols like “s” rather than names
     * (“second”), like Anki desktop.
     *
     * @param context The app context, used for localization
     * @param card The card being reviewed
     * @param rating The button number (easy, good etc.)
     * @return A string like “1 min” or “1.7 mo”
     */
    open fun nextIvlStr(
        card: Card,
        rating: Rating,
    ): String {
        val secs = nextIvl(card, rating)
        return col.backend.formatTimespan(secs.toFloat(), FormatTimespanRequest.Context.ANSWER_BUTTONS)
    }

    fun learnAheadSeconds(): Int = col.config.get("collapseTime") ?: 1200

    fun timeboxSecs(): Int = col.config.get("timeLim") ?: 0
}

const val REPORT_LIMIT = 99999

private fun stateFromEase(
    states: SchedulingStates,
    rating: Rating,
): SchedulingState =
    when (rating) {
        Rating.AGAIN -> states.again
        Rating.HARD -> states.hard
        Rating.GOOD -> states.good
        Rating.EASY -> states.easy
        Rating.UNRECOGNIZED -> TODO("invalid ease")
    }

private fun intervalForState(state: SchedulingState): Long =
    when (state.kindCase) {
        SchedulingState.KindCase.NORMAL -> intervalForNormalState(state.normal)
        SchedulingState.KindCase.FILTERED -> intervalForFilteredState(state.filtered)
        SchedulingState.KindCase.KIND_NOT_SET, null -> TODO("invalid scheduling state")
    }

private fun intervalForNormalState(normal: SchedulingState.Normal): Long =
    when (normal.kindCase) {
        SchedulingState.Normal.KindCase.NEW -> 0
        SchedulingState.Normal.KindCase.LEARNING -> normal.learning.scheduledSecs.toLong()
        SchedulingState.Normal.KindCase.REVIEW -> normal.review.scheduledDays.toLong() * 86400
        SchedulingState.Normal.KindCase.RELEARNING ->
            normal.relearning.learning.scheduledSecs
                .toLong()
        SchedulingState.Normal.KindCase.KIND_NOT_SET, null -> TODO("invalid normal state")
    }

private fun intervalForFilteredState(filtered: SchedulingState.Filtered): Long =
    when (filtered.kindCase) {
        SchedulingState.Filtered.KindCase.PREVIEW -> filtered.preview.scheduledSecs.toLong()
        SchedulingState.Filtered.KindCase.RESCHEDULING -> intervalForNormalState(filtered.rescheduling.originalState)
        SchedulingState.Filtered.KindCase.KIND_NOT_SET, null -> TODO("invalid filtered state")
    }

fun Collection.computeFsrsParamsRaw(input: ByteArray): ByteArray = backend.computeFsrsParamsRaw(input = input)

fun Collection.computeOptimalRetentionRaw(input: ByteArray): ByteArray = backend.computeOptimalRetentionRaw(input = input)

fun Collection.evaluateParamsRaw(input: ByteArray): ByteArray = backend.evaluateParamsRaw(input = input)

fun Collection.simulateFsrsReviewRaw(input: ByteArray): ByteArray = backend.simulateFsrsReviewRaw(input = input)

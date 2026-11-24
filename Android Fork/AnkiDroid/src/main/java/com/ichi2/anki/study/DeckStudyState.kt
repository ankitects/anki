// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.study

import anki.scheduler.CongratsInfoResponse
import com.ichi2.anki.common.time.SECONDS_PER_DAY
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.DeckId
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Collection facts about the selected deck, independent of the screen displaying them.
 *
 * Contents and availability are separate: cards can be buried, limited, scheduled for later,
 * or temporarily borrowed by a filtered deck. Completion details retain those distinctions.
 */
data class DeckStudyState(
    val deckId: DeckId,
    val kind: DeckKind,
    /** Includes subdecks and cards temporarily borrowed by filtered decks. */
    val cardCount: Int,
    val newCardsAvailable: Int,
    val learningCardsAvailable: Int,
    val reviewCardsAvailable: Int,
    val congratulations: CongratsInfoResponse,
) {
    val availability: StudyAvailability
        get() =
            if (newCardsAvailable + learningCardsAvailable + reviewCardsAvailable > 0) {
                StudyAvailability.CardsAvailable
            } else {
                StudyAvailability.NoCardsAvailable
            }

    val isEmptyRegularDeck: Boolean get() = kind == DeckKind.Regular && cardCount == 0

    /** Time until learning becomes available, accounting for the learn-ahead limit. */
    val nextLearningIn: Duration?
        get() = congratulations.secsUntilNextLearn.takeIf { it < SECONDS_PER_DAY }?.seconds
}

enum class DeckKind {
    Regular,
    Filtered,
}

enum class StudyAvailability {
    CardsAvailable,
    NoCardsAvailable,
}

/** Read under the collection lock so identity, contents and scheduling refer to the same deck. */
fun Collection.currentDeckStudyState(): DeckStudyState {
    val deck = decks.current()
    val counts = sched.counts()
    return DeckStudyState(
        deckId = deck.id,
        kind = if (deck.isFiltered) DeckKind.Filtered else DeckKind.Regular,
        cardCount = decks.cardCount(deck.id, includeSubdecks = true),
        newCardsAvailable = counts.new,
        learningCardsAvailable = counts.lrn,
        reviewCardsAvailable = counts.rev,
        congratulations = sched.congratulationsInfo(),
    )
}

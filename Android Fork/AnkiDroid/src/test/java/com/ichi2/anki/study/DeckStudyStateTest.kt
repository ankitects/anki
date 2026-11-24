// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.study

import anki.scheduler.CardAnswer.Rating
import com.ichi2.anki.libanki.Consts.DEFAULT_DECK_ID
import com.ichi2.testutils.JvmTest
import org.json.JSONArray
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class DeckStudyStateTest : JvmTest() {
    @Test
    fun `hidden Default retains its identity and empty regular deck state`() {
        addDeck("Other")
        assertNull(col.sched.deckDueTree().find(DEFAULT_DECK_ID))
        val undo = col.undoStatus()

        val state = col.currentDeckStudyState()

        assertEquals(DEFAULT_DECK_ID, state.deckId)
        assertEquals(DeckKind.Regular, state.kind)
        assertTrue(state.isEmptyRegularDeck)
        assertEquals(StudyAvailability.NoCardsAvailable, state.availability)
        assertNull(state.nextLearningIn)
        assertEquals(DEFAULT_DECK_ID, col.decks.selected())
        assertEquals(undo, col.undoStatus())
    }

    @Test
    fun `empty filtered deck retains its kind`() {
        val deckId = addDynamicDeck("Filtered", "deck:Missing")

        val state = col.currentDeckStudyState()

        assertEquals(deckId, state.deckId)
        assertEquals(DeckKind.Filtered, state.kind)
        assertEquals(0, state.cardCount)
        assertFalse(state.isEmptyRegularDeck)
        assertEquals(StudyAvailability.NoCardsAvailable, state.availability)
    }

    @Test
    fun `parent contents and availability include subdecks`() {
        val child = addDeck("Parent::Child")
        col.setDeck(listOf(addBasicNote().firstCard().id), child)
        val parent = col.decks.id("Parent")
        col.decks.select(parent)

        val state = col.currentDeckStudyState()

        assertEquals(parent, state.deckId)
        assertEquals(1, state.cardCount)
        assertEquals(1, state.newCardsAvailable)
        assertEquals(StudyAvailability.CardsAvailable, state.availability)
    }

    @Test
    fun `suspended cards remain content without being available`() {
        val card = addBasicNote().firstCard()
        col.sched.suspendCards(listOf(card.id))

        val state = col.currentDeckStudyState()

        assertEquals(1, state.cardCount)
        assertFalse(state.isEmptyRegularDeck)
        assertEquals(StudyAvailability.NoCardsAvailable, state.availability)
    }

    @Test
    fun `cards borrowed by a filtered deck remain part of the regular deck`() {
        addBasicNote()
        addDynamicDeck("Filtered", "")
        col.decks.select(DEFAULT_DECK_ID)

        val state = col.currentDeckStudyState()

        assertEquals(1, state.cardCount)
        assertFalse(state.isEmptyRegularDeck)
        assertEquals(StudyAvailability.NoCardsAvailable, state.availability)
    }

    @Test
    fun `learning later and available cards can coexist`() {
        col.config.set("collapseTime", 0)
        val config = col.decks.configDictForDeckId(DEFAULT_DECK_ID)
        config.new.delays = JSONArray(listOf(1))
        col.decks.save(config)
        addBasicNote()
        col.sched.answerCard(assertNotNull(col.sched.card), Rating.AGAIN)
        assertNull(col.sched.card)
        val waiting = col.currentDeckStudyState()
        assertEquals(StudyAvailability.NoCardsAvailable, waiting.availability)
        assertTrue(assertNotNull(waiting.nextLearningIn) in 60.seconds..75.seconds)

        addBasicNote("Another")
        val available = col.currentDeckStudyState()

        assertEquals(StudyAvailability.CardsAvailable, available.availability)
        assertEquals(2, available.cardCount)
        assertNotNull(available.nextLearningIn)
    }

    @Test
    fun `daily limits and buried cards are retained together`() {
        preventDayRolloverAlarmFromUnburyingCards()
        val buried = addBasicNote().firstCard()
        col.sched.buryCards(listOf(buried.id))
        addBasicNote("Limited")
        val config = col.decks.configDictForDeckId(DEFAULT_DECK_ID)
        config.new.perDay = 0
        col.decks.save(config)

        // Exercise a day-rollover alarm query after the card has been buried.
        simulateDayRolloverAlarmCutoffQuery()
        val state = col.currentDeckStudyState()

        assertEquals(StudyAvailability.NoCardsAvailable, state.availability)
        assertEquals(2, state.cardCount)
        assertTrue(state.congratulations.newRemaining)
        assertTrue(state.congratulations.haveUserBuried)
    }
}

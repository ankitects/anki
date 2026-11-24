// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Onur Dursun <onurgt@gmail.com>

package com.ichi2.anki.libanki

import com.ichi2.anki.libanki.testutils.InMemoryAnkiTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Regression cover for the deck-picker subtitle total (issue 17605), pinning the
 * two facts that constrain how the total may be computed.
 */
class DeckTreeDueCountsTest : InMemoryAnkiTest() {
    /**
     * The uncapped per-deck fields ignore the daily limit, so summing them gives
     * the backlog rather than today's due. Guards against using `*Uncapped`
     * fields in the subtitle total (issue 17605).
     */
    @Test
    fun `summing uncapped counts over-reports the due total`() {
        val conf = col.decks.configDictForDeckId(1)
        conf.new.perDay = 10
        col.decks.save(conf)
        repeat(20) { addBasicNote("front $it", "back") }

        // Limit-aware total: what the subtitle should show.
        val limitAwareNew = col.sched.counts().new
        assertEquals(10, limitAwareNew)

        // Sum of the uncapped per-deck fields.
        val uncappedTotal =
            col.sched.deckDueTree().sumOf { deck ->
                deck.node.newUncapped + deck.node.reviewUncapped +
                    deck.node.intradayLearning + deck.node.interdayLearningUncapped
            }
        assertEquals(20, uncappedTotal, "uncapped sum is the full new pool, not today's due")
        assertTrue(uncappedTotal > limitAwareNew, "summing uncapped over-reports the due total")
    }

    /**
     * Reading per-deck counts by selecting each deck (the `getQueuedCards` approach)
     * is not side-effect free: `select` registers an undoable op, clobbering the
     * user's undo history and bumping the collection mod time (which drives sync).
     */
    @Test
    fun `selecting a deck to read its counts mutates undo and sync state`() {
        val other = col.decks.id("Other")
        addBasicNote("front", "back")

        val undoBefore = col.undoStatus().undo
        val modBefore = col.mod
        Thread.sleep(2)

        col.decks.select(other)

        assertEquals("Select Deck", col.undoStatus().undo, "select registers an undoable op")
        assertNotEquals(undoBefore, col.undoStatus().undo, "previous undo step is clobbered")
        assertTrue(col.mod > modBefore, "select bumps the collection mod time (sync)")
    }
}

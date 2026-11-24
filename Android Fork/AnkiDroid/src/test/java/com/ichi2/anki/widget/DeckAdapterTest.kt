// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Vedant Kakade <vedantkakade05@gmail.com>

package com.ichi2.anki.widget

import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CommonString
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.deckpicker.DeckFilters
import com.ichi2.anki.deckpicker.DisplayDeckNode
import com.ichi2.anki.deckpicker.filterAndFlattenDisplay
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.widgets.DeckAdapter
import com.ichi2.anki.widgets.deckNodeDiffCallback
import com.ichi2.anki.withDeckPicker
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class DeckAdapterTest : RobolectricTest() {
    @Test
    fun ensureDeckSelectionUpdatesCorrectly() {
        val deck1Id = addDeck("Deck 1")
        val deck2Id = addDeck("Deck 2")
        val deck3Id = addDeck("Deck 3")

        val node =
            col.sched.deckDueTree().filterAndFlattenDisplay(
                DeckFilters.create(""),
                deck1Id,
            )

        assertTrue(node.first { it.did == deck1Id }.isSelected)

        val afterDeck2 = node.map { it.withUpdatedDeckId(deck2Id) }
        assertFalse(actual = afterDeck2.first { it.did == deck1Id }.isSelected)
        assertTrue(actual = afterDeck2.first { it.did == deck2Id }.isSelected)

        val afterDeck3 = afterDeck2.map { it.withUpdatedDeckId(deck3Id) }
        assertFalse(actual = afterDeck3.first { it.did == deck2Id }.isSelected)
        assertTrue(actual = afterDeck3.first { it.did == deck3Id }.isSelected)
    }

    @Test
    fun `selecting a pressed deck does not start a new ripple`() {
        val initiallySelected = addDeck("Initially selected")
        val pressedDeck = addDeck("Pressed")
        col.decks.select(initiallySelected)

        withDeckPicker(deckCount = 0) { deckPicker ->
            val adapter = deckPicker.deckPickerBinding.decks.adapter as DeckAdapter
            val pressedRow = deckPicker.deckHolder(pressedDeck).itemView
            pressedRow.isPressed = true
            val originalRipple = pressedRow.background

            adapter.updateSelectedDeck(pressedDeck)
            advanceRobolectricLooperUntil { adapter.currentList.single { it.did == pressedDeck }.isSelected }

            val selectedBackground = deckPicker.deckHolder(pressedDeck).itemView.background
            // A replacement drawable must not inherit the press and start a second ripple.
            assertTrue(
                selectedBackground === originalRipple || android.R.attr.state_pressed !in selectedBackground.state,
                "Selecting a pressed deck restarted its ripple",
            )
        }
    }

    @Test
    fun `card count changes allow the deck row to cross-fade`() {
        val deckId = addDeck("Counts")
        val original = col.sched.deckDueTree().find(deckId)!!
        val before = DisplayDeckNode.from(original, matchesSearchOrChild = true, selectedDeckId = deckId)
        val counts = original.node
        val updates =
            listOf(
                counts.toBuilder().setNewCount(1).build(),
                counts.toBuilder().setLearnCount(1).build(),
                counts.toBuilder().setReviewCount(1).build(),
            )
        for (updatedCounts in updates) {
            val after =
                DisplayDeckNode.from(
                    original.copy(node = updatedCounts),
                    matchesSearchOrChild = true,
                    selectedDeckId = deckId,
                )

            // A null payload allows cross-fading for both increasing and decreasing counts.
            assertNull(deckNodeDiffCallback.getChangePayload(before, after))
            assertNull(deckNodeDiffCallback.getChangePayload(after, before))
        }
    }

    @Test
    fun `toggling subdecks preserves the arrow view and updates its icon`() {
        val parentDeck = addDeck("Parent")
        addDeck("Parent::Child")
        col.decks.select(parentDeck)

        withDeckPicker(deckCount = 0) { deckPicker ->
            val adapter = deckPicker.deckPickerBinding.decks.adapter as DeckAdapter
            val parent = deckPicker.deckHolder(parentDeck)
            val changePayloads = mutableListOf<Any?>()
            adapter.observeItemRangeChanges { _, _, payload -> changePayloads.add(payload) }

            repeat(2) {
                val wasCollapsed = adapter.currentList.single { it.did == parentDeck }.collapsed
                parent.binding.deckExpander.performClick()
                advanceRobolectricLooperUntil {
                    adapter.currentList.single { it.did == parentDeck }.collapsed != wasCollapsed
                }

                assertTrue(changePayloads.isNotEmpty())
                assertTrue(changePayloads.all { it != null }, "A full row update interrupts the arrow ripple")
                assertSame(parent, deckPicker.deckHolder(parentDeck))
                assertEquals(
                    deckPicker.getString(if (wasCollapsed) CommonString.collapse else CommonString.expand),
                    parent.binding.deckExpander.contentDescription,
                )
                changePayloads.clear()
            }
        }
    }

    private fun DeckPicker.deckHolder(deckId: DeckId): DeckAdapter.ViewHolder {
        val decks = deckPickerBinding.decks
        val adapter = decks.adapter as DeckAdapter
        // Background list diffs can finish after the initial main looper drain.
        advanceRobolectricLooperUntil(lazyMessage = { "Deck $deckId was not laid out" }) {
            val position = adapter.currentList.indexOfFirst { it.did == deckId }
            position >= 0 && decks.findViewHolderForAdapterPosition(position) != null
        }
        val position = adapter.currentList.indexOfFirst { it.did == deckId }
        return decks.findViewHolderForAdapterPosition(position) as DeckAdapter.ViewHolder
    }

    private fun RecyclerView.Adapter<*>.observeItemRangeChanges(listener: (Int, Int, Any?) -> Unit) {
        registerAdapterDataObserver(
            object : RecyclerView.AdapterDataObserver() {
                override fun onItemRangeChanged(
                    positionStart: Int,
                    itemCount: Int,
                    payload: Any?,
                ) {
                    listener(positionStart, itemCount, payload)
                }
            },
        )
    }
}

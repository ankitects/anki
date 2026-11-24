// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 Akshit Sinha <akshitsinha3@gmail.com>

package com.ichi2.anki.dialogs

import android.view.View
import android.widget.Filterable
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.testing.launchFragment
import androidx.recyclerview.widget.RecyclerView
import anki.decks.SetDeckCollapsedRequest
import anki.decks.deckTreeNode
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.libanki.sched.DeckNode
import com.ichi2.anki.model.SelectableDeck
import com.ichi2.utils.TypedFilter
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers
import org.hamcrest.Matchers.containsInAnyOrder
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.not
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertIs
import kotlin.test.assertTrue
import com.ichi2.anki.ankicommon.R as AnkiCommonR
import com.ichi2.anki.common.android.R as CommonR

@RunWith(RobolectricTestRunner::class)
class DeckSelectionDialogTest : RobolectricTest() {
    @Test
    fun verifyDeckDisplayName() {
        val input = "deck::sub-deck::sub-deck2::sub-deck3"
        val expected = "sub-deck3"

        val deck = SelectableDeck.Deck(1234, input)
        val actual: String = deck.getDisplayName(targetContext)

        assertThat(actual, Matchers.equalTo(expected))
    }

    @Test
    fun testDialogCreation() {
        val decks: List<SelectableDeck> = listOf(SelectableDeck.Deck(5L, "deck"))
        val dialogTitle = "Select Deck"
        val summaryMessage = "Choose a deck from the list"

        val dialog = DeckSelectionDialog.newInstance(title = dialogTitle, templateEditorMessage = summaryMessage, decks = decks)
        assertNotNull(dialog)
        assertEquals(dialogTitle, dialog.arguments?.getString("arg_title"))
        assertEquals(summaryMessage, dialog.arguments?.getString("arg_template_editor_message"))
    }

    @Test
    fun `selecting child deck does not collect parent or sibling ids`() {
        // Build tree: Parent (id=1) -> Child1 (id=2), Child2 (id=3)
        val child1 = makeNode("Child1", deckId = 2, level = 2)
        val child2 = makeNode("Child2", deckId = 3, level = 2)
        val parent = makeNode("Parent", deckId = 1, level = 1, children = listOf(child1, child2))

        val selectedChild = parent.find(2L)!!
        val idsToRemove = mutableSetOf(selectedChild.did)
        selectedChild.forEach { idsToRemove.add(it.did) }

        assertThat(idsToRemove, containsInAnyOrder(2L))
        assertThat(idsToRemove, not(hasItem(1L)))
        assertThat(idsToRemove, not(hasItem(3L)))
    }

    @Test
    fun `selecting parent deck with nested children collects all descendants`() {
        // Build tree: A (id=1) -> B (id=2) -> C (id=3)
        //                      -> D (id=4)
        val c = makeNode("C", deckId = 3, level = 3)
        val b = makeNode("B", deckId = 2, level = 2, children = listOf(c))
        val d = makeNode("D", deckId = 4, level = 2)
        val a = makeNode("A", deckId = 1, level = 1, children = listOf(b, d))

        val idsToRemove = mutableSetOf(a.did)
        a.forEach { idsToRemove.add(it.did) }

        assertThat(idsToRemove, containsInAnyOrder(1L, 2L, 3L, 4L))
    }

    @Test
    fun `search entered while the deck tree loads remains applied`() {
        addDeck("Chosen")
        addDeck("Unrelated")
        val decks = allDecks()
        holdingCollectionAccess { scheduler ->
            launchDeckSelectionDialog(decks).use { scenario ->
                scenario.onFragment { picker ->
                    val search = picker.openSearch()
                    search.setQuery("Chosen", false)
                    val recycler = picker.binding.decks
                    recycler.filterAndWait("Chosen")
                    assertEquals(0, recycler.adapter!!.itemCount)

                    scheduler.advanceUntilIdle()
                    advanceRobolectricLooper()

                    assertEquals("Chosen", search.query.toString())
                    assertEquals("The active search should only show Chosen", 1, recycler.adapter!!.itemCount)
                }
            }
        }
    }

    @Test
    fun `search before load displays a matching child under a collapsed parent`() {
        addDeck("Parent::Chosen")
        collapseDeck("Parent")
        val decks = allDecks()
        holdingCollectionAccess { scheduler ->
            launchDeckSelectionDialog(decks).use { scenario ->
                scenario.onFragment { picker ->
                    picker.openSearch().setQuery("Chosen", false)
                    val recycler = picker.binding.decks
                    recycler.filterAndWait("Chosen")
                    assertEquals(0, recycler.adapter!!.itemCount)

                    scheduler.advanceUntilIdle()
                    advanceRobolectricLooper()

                    assertEquals(1, recycler.adapter!!.itemCount)
                    val row = requireNotNull(recycler.findViewHolderForAdapterPosition(0))
                    assertEquals(
                        "The matching child must be visible, not only counted by the adapter",
                        View.VISIBLE,
                        row.itemView.visibility,
                    )
                }
            }
        }
    }

    @Test
    fun `filter computed before loading and published after loading includes matching deck`() {
        addDeck("Chosen")
        addDeck("Unrelated")
        val decks = allDecks()
        holdingCollectionAccess { scheduler ->
            launchDeckSelectionDialog(decks).use { scenario ->
                scenario.onFragment { picker ->
                    val adapter = picker.binding.decks.adapter!!
                    val filter = assertIs<Filterable>(adapter).filter
                    val typedFilter = TypedFilter::class.java
                    // Android Filter computes on its worker thread and publishes on main.
                    // Hold publication while the collection load completes on main.
                    val perform = typedFilter.getDeclaredMethod("performFiltering", CharSequence::class.java)
                    perform.isAccessible = true
                    val pendingResults = requireNotNull(perform.invoke(filter, "Chosen"))
                    assertEquals(0, adapter.itemCount)

                    scheduler.advanceUntilIdle()
                    advanceRobolectricLooper()
                    assertTrue(adapter.itemCount > 0, "The tree has loaded before the search result is delivered")

                    val publish = typedFilter.getDeclaredMethod("publishResults", CharSequence::class.java, pendingResults.javaClass)
                    publish.isAccessible = true
                    publish.invoke(filter, "Chosen", pendingResults)
                    advanceRobolectricLooper()
                    assertEquals(
                        "The matching deck must not disappear when an earlier empty snapshot is published",
                        1,
                        adapter.itemCount,
                    )
                }
            }
        }
    }

    @Test
    fun `collapsing a search result keeps unrelated decks filtered out`() {
        addDeck("Parent::Child")
        addDeck("Unrelated")
        launchDeckSelectionDialog(allDecks()).use { scenario ->
            advanceRobolectricLooper()
            scenario.onFragment { picker ->
                val search = picker.openSearch()
                search.setQuery("Parent", false)
                val recycler = picker.binding.decks
                advanceRobolectricLooperUntil { recycler.adapter!!.itemCount == 1 }
                advanceRobolectricLooper()

                val row = assertIs<DeckSelectionDialog.DecksArrayAdapter.ViewHolder>(recycler.findViewHolderForAdapterPosition(0))
                assertTrue(row.expander.performClick())
                advanceRobolectricLooper()

                assertEquals("Parent", search.query.toString())
                assertEquals("Only the collapsed matching parent should remain", 1, recycler.adapter!!.itemCount)
            }
        }
    }

    @Test
    fun `multi selection stays open when the last search match is selected`() {
        addDeck("Chosen")
        addDeck("Unrelated")
        launchDeckSelectionDialog(allDecks(), allowMultipleSelection = true).use { scenario ->
            advanceRobolectricLooper()
            scenario.onFragment { picker ->
                picker.openSearch().setQuery("Chosen", false)
                val recycler = picker.binding.decks
                advanceRobolectricLooperUntil { recycler.adapter!!.itemCount == 1 }
                advanceRobolectricLooper()

                val dialog = picker.requireDialog()
                assertTrue(requireNotNull(recycler.findViewHolderForAdapterPosition(0)).itemView.performClick())
                advanceRobolectricLooper()

                assertTrue(dialog.isShowing, "Other unselected decks remain; clearing the query should allow selecting them")
            }
        }
    }

    private fun allDecks() = col.decks.allNamesAndIds().map { SelectableDeck.Deck(it) }

    private fun collapseDeck(name: String) {
        val deckId = requireNotNull(col.decks.idForName(name))
        col.decks.setCollapsed(deckId, true, SetDeckCollapsedRequest.Scope.REVIEWER)
        val tree = col.sched.deckDueTree()
        assertTrue(tree.find(deckId)!!.collapsed)
    }

    private fun launchDeckSelectionDialog(
        decks: List<SelectableDeck>,
        allowMultipleSelection: Boolean = false,
    ) = launchFragment<DeckSelectionDialog>(
        fragmentArgs = DeckSelectionDialog.newInstance(decks = decks, allowMultipleSelection = allowMultipleSelection).arguments,
        themeResId = CommonR.style.Theme_AnkiDroid,
    )

    private fun DeckSelectionDialog.openSearch(): SearchView {
        val searchItem = binding.toolbar.menu.findItem(AnkiCommonR.id.action_filter)
        searchItem.expandActionView()
        return assertIs<SearchView>(searchItem.actionView)
    }

    private fun RecyclerView.filterAndWait(query: String) {
        var filtered = false
        assertIs<Filterable>(adapter).filter.filter(query) { filtered = true }
        advanceRobolectricLooperUntil { filtered }
    }

    private fun holdingCollectionAccess(block: (TestCoroutineScheduler) -> Unit) {
        val queue = StandardTestDispatcher()
        val previousQueue = CollectionManager.setTestDispatcher(queue, useReentrantLock = false)
        try {
            block(queue.scheduler)
        } finally {
            CollectionManager.setTestDispatcher(previousQueue)
        }
    }

    private fun makeNode(
        name: String,
        deckId: Long,
        level: Int,
        collapsed: Boolean = false,
        children: List<DeckNode> = emptyList(),
    ): DeckNode {
        val treeNode =
            deckTreeNode {
                this.name = name
                this.deckId = deckId
                this.level = level
                this.collapsed = collapsed
                children.forEach { this.children.add(it.node) }
                this.reviewCount = 0
                this.newCount = 0
                this.learnCount = 0
                this.filtered = false
            }
        return DeckNode(treeNode, name)
    }
}

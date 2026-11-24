// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText:  Copyright (c) 2025 Gautam Bhetanabhotla <gautamarcturus@gmail.com>

package com.ichi2.anki.deckpicker

import androidx.annotation.VisibleForTesting
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.sched.DeckNode
import com.ichi2.anki.libanki.utils.append
import com.ichi2.anki.timebudget.TimeBudgetBadge

/**
 * An immutable variant of a [DeckNode]. Instantiated right before
 * we want to display it. The list being submitted to the [ListViewAdapter]
 * is a list of [DisplayDeckNode]s. This class only contains the information
 * needed to display it on the screen, hence no data of a node's children and parent.
 */
@ConsistentCopyVisibility
data class DisplayDeckNode private constructor(
    val did: DeckId,
    val fullDeckName: String,
    val lastDeckNameComponent: String,
    val collapsed: Boolean,
    val canCollapse: Boolean,
    val depth: Int,
    val filtered: Boolean,
    val newCount: Int,
    val lrnCount: Int,
    val revCount: Int,
    val isSelected: Boolean,
    /** Shown instead of the counts for decks studied with an RWKV-Instant time budget. */
    val timeBudget: TimeBudgetBadge? = null,
) {
    // DeckNode is mutable, so use a lateinit var so '==' doesn't include it in the comparison
    lateinit var deckNode: DeckNode

    fun withUpdatedDeckId(deckId: DeckId): DisplayDeckNode =
        this.copy(isSelected = this.did == deckId).also { updated ->
            updated.deckNode = this.deckNode
        }

    companion object {
        fun from(
            node: DeckNode,
            matchesSearchOrChild: Boolean,
            selectedDeckId: DeckId,
            timeBudgets: Map<DeckId, TimeBudgetBadge> = emptyMap(),
        ): DisplayDeckNode =
            DisplayDeckNode(
                did = node.did,
                fullDeckName = node.fullDeckName,
                lastDeckNameComponent = node.lastDeckNameComponent,
                collapsed = node.collapsed,
                canCollapse = node.children.any() && matchesSearchOrChild,
                depth = node.depth,
                filtered = node.filtered,
                newCount = node.newCount,
                lrnCount = node.lrnCount,
                revCount = node.revCount,
                isSelected = node.did == selectedDeckId,
                timeBudget = timeBudgets[node.did],
            ).apply {
                this.deckNode = node
            }
    }
}

/** Convert the tree into a flat list of [DisplayDeckNode]s, where matching decks and the children/parents
 * are included. Decks inside collapsed decks are not considered. */
fun DeckNode.filterAndFlattenDisplay(
    filter: DeckFilters,
    selectedDeckId: DeckId,
    timeBudgets: Map<DeckId, TimeBudgetBadge> = emptyMap(),
): List<DisplayDeckNode> {
    val list = mutableListOf<DisplayDeckNode>()
    filterAndFlattenDisplayInner(filter, list, parentMatched = false, selectedDeckId, timeBudgets)
    return list
}

private fun DeckNode.filterAndFlattenDisplayInner(
    filter: DeckFilters,
    list: MutableList<DisplayDeckNode>,
    parentMatched: Boolean,
    selectedDeckId: DeckId,
    timeBudgets: Map<DeckId, TimeBudgetBadge>,
) {
    if (!isSyntheticDeck && (filter.accept(fullDeckName) || parentMatched)) {
        this.addVisibleToList(list, matchesSearchOrChild = true, selectedDeckId, timeBudgets)
        return
    }

    // When searching, ignore collapsed state and always search children
    val searching = filter.isActive()
    if (collapsed && !searching) {
        return
    }

    if (!isSyntheticDeck) {
        list.append(
            DisplayDeckNode.from(
                this,
                matchesSearchOrChild = false,
                selectedDeckId = selectedDeckId,
                timeBudgets = timeBudgets,
            ),
        )
    }
    val startingLen = list.size
    for (child in children) {
        child.filterAndFlattenDisplayInner(filter, list, parentMatched = false, selectedDeckId, timeBudgets)
    }
    if (!isSyntheticDeck && startingLen == list.size) {
        // we don't include ourselves if no children matched
        list.removeAt(list.lastIndex)
    }
}

private fun DeckNode.addVisibleToList(
    list: MutableList<DisplayDeckNode>,
    matchesSearchOrChild: Boolean,
    selectedDeckId: DeckId,
    timeBudgets: Map<DeckId, TimeBudgetBadge>,
) {
    list.append(DisplayDeckNode.from(this, matchesSearchOrChild, selectedDeckId, timeBudgets))
    if (!collapsed) {
        for (child in children) {
            child.addVisibleToList(list, matchesSearchOrChild, selectedDeckId, timeBudgets)
        }
    }
}

@VisibleForTesting
fun DeckNode.addVisibleToList(list: MutableList<DeckNode>) {
    list.append(this)
    if (!collapsed) {
        for (child in children) {
            child.addVisibleToList(list)
        }
    }
}

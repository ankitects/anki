/*
 *  Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>
 *  Copyright (c) 2025 Gautam Bhetanabhotla <gautamarcturus@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.deckpicker

import androidx.annotation.VisibleForTesting
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.sched.DeckNode
import com.ichi2.anki.libanki.utils.append

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
): List<DisplayDeckNode> {
    val list = mutableListOf<DisplayDeckNode>()
    filterAndFlattenDisplayInner(filter, list, parentMatched = false, selectedDeckId)
    return list
}

private fun DeckNode.filterAndFlattenDisplayInner(
    filter: DeckFilters,
    list: MutableList<DisplayDeckNode>,
    parentMatched: Boolean,
    selectedDeckId: DeckId,
) {
    if (!isSyntheticDeck && (filter.accept(fullDeckName) || parentMatched)) {
        this.addVisibleToList(list, matchesSearchOrChild = true, selectedDeckId)
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
            ),
        )
    }
    val startingLen = list.size
    for (child in children) {
        child.filterAndFlattenDisplayInner(filter, list, parentMatched = false, selectedDeckId)
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
) {
    list.append(DisplayDeckNode.from(this, matchesSearchOrChild, selectedDeckId))
    if (!collapsed) {
        for (child in children) {
            child.addVisibleToList(list, matchesSearchOrChild, selectedDeckId)
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

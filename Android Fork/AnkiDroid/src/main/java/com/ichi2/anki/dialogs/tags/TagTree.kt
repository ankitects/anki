// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Tarek Mohamed Abdalla <tarekkma@gmail.com>

package com.ichi2.anki.dialogs.tags

import com.ichi2.utils.TagsUtil
import java.util.Locale
import java.util.Stack
import java.util.TreeSet

/** Tag hierarchy, visible rows and selection policy used by the tag dialog. */
internal class TagTree(
    private val tags: TagsList,
) {
    enum class State { INDETERMINATE, UNCHECKED, CHECKED }

    /**
     * A row in the hierarchy. [subtreeSize] includes this row and its visible descendants,
     * retaining that count while collapsed so expanding it need not walk the subtree.
     */
    data class Node(
        val tag: String,
        val parent: Node?,
        val children: ArrayList<Node>,
        val level: Int,
        var subtreeSize: Int,
        var isExpanded: Boolean,
    ) {
        /**
         * Return the number of visible nodes in the subtree.
         * If [isExpanded] is set to false, the subtree is collapsed, only the node itself is visible.
         * Otherwise, the subtree is expanded and visible.
         *
         * @return The number of visible nodes in the subtree.
         */
        fun getContributeSize(): Int = if (isExpanded) subtreeSize else 1

        /**
         * @return If the node has one or more children.
         */
        fun isNotLeaf(): Boolean = children.isNotEmpty()

        /**
         * Toggle the expand state of the node, then iterate up to the root to maintain the tree.
         * Should never be called on the root node [treeRoot].
         */
        fun toggleIsExpanded() {
            isExpanded = !isExpanded
            val delta = if (isExpanded) subtreeSize - 1 else 1 - subtreeSize
            for (ancestor in iterateAncestorsOf(this)) {
                ancestor.subtreeSize += delta
                if (!ancestor.isExpanded) {
                    break
                }
            }
        }

        companion object {
            fun iterateAncestorsOf(start: Node) =
                object : Iterator<Node> {
                    var current = start

                    override fun hasNext(): Boolean = current.parent != null

                    override fun next(): Node {
                        current = current.parent!!
                        return current
                    }
                }
        }
    }

    private val filteredList: ArrayList<String>
    private lateinit var treeRoot: Node
    private val tagToIsExpanded = HashMap<String, Boolean>()

    var hasVisibleNestedTag = false
        private set

    val size: Int get() = treeRoot.subtreeSize

    init {
        tags.sort()
        filteredList = ArrayList(tags.toList())
        tags.forEach { tagToIsExpanded[it] = tags.isChecked(it) || tags.isIndeterminate(it) }
        rebuildTagTree()
    }

    fun state(node: Node): State =
        when {
            tags.isIndeterminate(node.tag) -> State.INDETERMINATE
            tags.isChecked(node.tag) -> State.CHECKED
            else -> State.UNCHECKED
        }

    fun hasSelectedDescendants(node: Node): Boolean = tags.hasSelectedDescendants(node.tag)

    /** Toggle the tag and update all ancestors, including those hidden by a filter. */
    fun toggleChecked(node: Node): List<Node> {
        when (state(node)) {
            State.UNCHECKED -> tags.check(node.tag, false)
            State.INDETERMINATE -> if (hasSelectedDescendants(node)) tags.check(node.tag, false) else tags.uncheck(node.tag)
            State.CHECKED -> if (hasSelectedDescendants(node)) tags.setIndeterminate(node.tag) else tags.uncheck(node.tag)
        }
        val changed =
            listOf(node) +
                Node
                    .iterateAncestorsOf(node)
                    .asSequence()
                    .filter { it.parent != null }
                    .toList()
        for (current in changed) {
            val hasSelectedDescendants = hasSelectedDescendants(current)
            if (!tags.isChecked(current.tag) && hasSelectedDescendants) tags.setIndeterminate(current.tag)
            if (tags.isIndeterminate(current.tag) && !tags.isPartiallySelected(current.tag) && !hasSelectedDescendants) {
                tags.uncheck(current.tag)
            }
        }
        return changed
    }

    fun toggleExpanded(node: Node) {
        node.toggleIsExpanded()
        tagToIsExpanded[node.tag] = node.isExpanded
    }

    fun refreshSelection() = rebuildTagTree()

    fun publishResults(
        results: List<String>,
        expandTarget: String = "",
    ) {
        filteredList.clear()
        filteredList.addAll(results)
        tags.sort()
        rebuildTagTree(expandTarget)
    }

    operator fun get(index: Int): Node? {
        var remain = index
        var node = treeRoot
        while (remain < node.subtreeSize) {
            for (child in node.children) {
                if (remain >= child.getContributeSize()) {
                    remain -= child.getContributeSize()
                } else {
                    if (remain == 0) {
                        return child
                    } else {
                        remain -= 1
                        node = child
                        break
                    }
                }
            }
        }
        return null
    }

    private fun rebuildTagTree(expandTarget: String = "") {
        // init mapping for newly added tags
        filteredList.forEach {
            if (!tagToIsExpanded.containsKey(it)) {
                tagToIsExpanded[it] = tags.isChecked(it) || tags.isIndeterminate(it)
            }
        }
        TagsUtil.getTagAncestors(expandTarget).forEach {
            if (tagToIsExpanded.containsKey(it)) {
                tagToIsExpanded[it] = true
            }
        }
        hasVisibleNestedTag = false
        val stack = Stack<Node>()
        treeRoot = Node("", null, ArrayList(), -1, 0, true)
        stack.add(treeRoot)

        fun stackPopAndPushUp() {
            val popped = stack.pop()
            stack.peek().subtreeSize += popped.getContributeSize()
        }
        for (tag in filteredList) {
            // root will never be popped
            while (stack.size > 1) {
                if (!tag.startsWith(stack.peek().tag + "::", ignoreCase = true)) {
                    stackPopAndPushUp()
                } else {
                    break
                }
            }
            val parent = stack.peek()
            val node =
                Node(tag, parent, ArrayList(), parent.level + 1, 1, tagToIsExpanded[tag]!!)
            parent.children.add(node)
            stack.add(node)
            if (stack.size > 2) {
                hasVisibleNestedTag = true
            }
        }
        while (stack.size > 1) {
            stackPopAndPushUp()
        }
    }

    fun filterResults(
        constraint: CharSequence,
        items: List<String>,
    ): List<String> {
        val shownTags = TreeSet<String>()
        val filterPattern = constraint.toString().lowercase(Locale.getDefault()).trim()
        val crucialTags =
            items.filter {
                it.lowercase(Locale.getDefault()).contains(filterPattern)
            }
        shownTags.addAll(crucialTags)
        // the ancestors should be displayed as well
        for (tag in crucialTags) {
            shownTags.addAll(TagsUtil.getTagAncestors(tag))
        }
        // show tags in the relative order in original list
        val res = items.filter { shownTags.contains(it) }
        // mark shown tags as expanded, so that they will be expanded next time the tree is built
        res.forEach { tagToIsExpanded[it] = true }
        return res
    }
}

/*
 Copyright (c) 2021 Tarek Mohamed Abdalla <tarekkma@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.dialogs.tags

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Filterable
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.annotation.VisibleForTesting
import androidx.recyclerview.widget.RecyclerView
import com.ichi2.anki.OnContextAndLongClickListener
import com.ichi2.anki.OnContextAndLongClickListener.Companion.setOnContextAndLongClickListener
import com.ichi2.anki.R
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.databinding.ItemTagBinding
import com.ichi2.ui.CheckBoxTriStates
import com.ichi2.ui.CheckBoxTriStates.State.CHECKED
import com.ichi2.ui.CheckBoxTriStates.State.INDETERMINATE
import com.ichi2.ui.CheckBoxTriStates.State.UNCHECKED
import com.ichi2.utils.TagsUtil
import com.ichi2.utils.TypedFilter
import java.util.Locale
import java.util.Stack
import java.util.TreeSet

/**
 * @param tags A reference to the [TagsList]
 */
class TagsArrayAdapter(
    private val tags: TagsList,
    private val onSelectionUpdate: () -> Unit,
) : RecyclerView.Adapter<TagsArrayAdapter.ViewHolder>(),
    Filterable {
    class ViewHolder(
        val binding: ItemTagBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        internal lateinit var node: TagTreeNode

        @get:VisibleForTesting(otherwise = VisibleForTesting.NONE)
        val text: String
            get() = node.tag

        @get:VisibleForTesting(otherwise = VisibleForTesting.NONE)
        val isChecked: Boolean
            get() = binding.checkBoxView.isChecked

        @get:VisibleForTesting(otherwise = VisibleForTesting.NONE)
        val checkboxState: CheckBoxTriStates.State
            get() = binding.checkBoxView.state
    }

    /**
     * The node class of the tag tree.
     * @param tag The full tag that the node represents.
     * @param parent The parent of the node (null for the root).
     * @param children The children of the node.
     * @param level The level of the tag in the tree (-1 for the root).
     * @param subtreeSize The size of the subtree if the node is expanded. This exists because
     * we want to save the [isExpanded] state of child tags, even if the parent is not expanded.
     * When we expand a tag, we do not want to walk the tree to determine how many nodes were added or removed.
     * @param isExpanded whether the node is expanded or not
     * @param subtreeCheckedCnt The number of checked nodes in the subtree (self included). This exists
     * because we want to dynamically turn a checkbox from unchecked into indeterminate if at least one of its
     * descendants is checked, or from indeterminate to unchecked if all of its descendants are unchecked.
     * @param vh The reference to currently bound [ViewHolder]. A node bound with some [ViewHolder] must have [vh] nonnull.
     * @see onBindViewHolder for the binding
     */
    @NeedsTest("Make sure that the data structure works properly.")
    data class TagTreeNode(
        val tag: String,
        val parent: TagTreeNode?,
        val children: ArrayList<TagTreeNode>,
        val level: Int,
        var subtreeSize: Int,
        var isExpanded: Boolean,
        var subtreeCheckedCnt: Int,
        var vh: ViewHolder?,
    ) {
        /**
         * Get or set the checkbox state of the currently bound ViewHolder.
         * [vh] must be nonnull.
         */
        private var checkBoxState: CheckBoxTriStates.State?
            get() = vh?.binding?.checkBoxView?.state
            set(state) {
                state?.let { vh?.binding?.checkBoxView?.state = it }
            }

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

        /**
         * Set the cycle style of the node's [vh]'s checkbox.
         * For nodes without checked descendants: CHECKED -> UNCHECKED -> CHECKED -> ...
         * For nodes with checked descendants: CHECKED -> INDETERMINATE -> CHECKED -> ...
         *
         * @param tags The [TagsList] that manages tags.
         */
        fun updateCheckBoxCycleStyle(tags: TagsList) {
            val realSubtreeCnt = subtreeCheckedCnt - if (tags.isChecked(tag)) 1 else 0
            val hasDescendantChecked = realSubtreeCnt > 0
            vh?.binding?.checkBoxView?.cycleIndeterminateToChecked = hasDescendantChecked
            vh?.binding?.checkBoxView?.cycleCheckedToIndeterminate = hasDescendantChecked
        }

        /**
         * When the checkbox of the node changes, update [subtreeCheckedCnt]s of itself and its ancestors.
         * If the checkbox is INDETERMINATE now, it must be CHECKED before according to the checkbox cycle style.
         * @see updateCheckBoxCycleStyle
         *
         * Update the checkbox to INDETERMINATE if it was UNCHECK and now [subtreeCheckedCnt] > 0.
         * Update the checkbox to UNCHECK if it was INDETERMINATE and now [subtreeCheckedCnt] == 0.
         *
         * @param tags The [TagsList] that manages tags.
         */
        fun onCheckStateChanged(tags: TagsList) {
            val delta = if (checkBoxState == CHECKED) 1 else -1

            fun update(node: TagTreeNode) {
                node.subtreeCheckedCnt += delta
                if (node.checkBoxState == UNCHECKED && node.subtreeCheckedCnt > 0) {
                    tags.setIndeterminate(node.tag)
                    node.checkBoxState = INDETERMINATE
                }
                if (node.checkBoxState == INDETERMINATE && node.subtreeCheckedCnt == 0) {
                    tags.uncheck(node.tag)
                    node.checkBoxState = UNCHECKED
                }
                node.updateCheckBoxCycleStyle(tags)
                node.vh
                    ?.binding
                    ?.checkBoxView
                    ?.refreshDrawableState()
            }
            update(this)
            for (ancestor in iterateAncestorsOf(this)) {
                // do not perform update on the virtual root
                if (ancestor.parent != null) {
                    update(ancestor)
                }
            }
        }

        companion object {
            fun iterateAncestorsOf(start: TagTreeNode) =
                object : Iterator<TagTreeNode> {
                    var current = start

                    override fun hasNext(): Boolean = current.parent != null

                    override fun next(): TagTreeNode {
                        current = current.parent!!
                        return current
                    }
                }
        }
    }

    /**
     * A subset of all tags in [tags] satisfying the user's search.
     * @see getFilter
     */
    private val filteredList: ArrayList<String>

    /**
     * The root node of the tag tree.
     */
    private lateinit var treeRoot: TagTreeNode

    /**
     * A mapping from tag strings to corresponding nodes.
     */
    private val tagToNode: HashMap<String, TagTreeNode>

    /**
     * Whether there exists visible nested tag.
     * Recalculated everytime [buildTagTree] is called.
     */
    private var hasVisibleNestedTag: Boolean

    /**
     * A mapping from tag strings to its expand state.
     * Used to restore expand states between filtering results.
     * Must be updated every time a node's [TagTreeNode.isExpanded] changed.
     */
    private val tagToIsExpanded: HashMap<String, Boolean>

    /**
     * Context and Long click listener for each tag item. Used to add a subtag for the clicked tag.
     * The full tag is passed through View.tag
     */
    var tagContextAndLongClickListener: OnContextAndLongClickListener? = null

    fun sortData() {
        tags.sort()
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val binding = ItemTagBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        val vh = ViewHolder(binding)
        // clicking the checkbox toggles the tag's check state
        binding.checkBoxView.setOnClickListener {
            val checkBox = binding.checkBoxView
            when (checkBox.state) {
                CHECKED -> tags.check(vh.node.tag, false)
                UNCHECKED -> tags.uncheck(vh.node.tag)
                INDETERMINATE -> tags.setIndeterminate(vh.node.tag)
            }
            vh.node.onCheckStateChanged(tags)
            onSelectionUpdate()
        }
        // clicking other parts toggles the expansion state, or the checkbox (for leaf)
        vh.itemView.setOnClickListener {
            if (vh.node.isNotLeaf()) {
                vh.node.toggleIsExpanded()
                tagToIsExpanded[vh.node.tag] = !tagToIsExpanded[vh.node.tag]!!
                updateExpanderBackgroundImage(binding.expandButton, vh.node)
                // content of RecyclerView may change due to the expansion / collapse
                val deltaSize = vh.node.subtreeSize - 1
                if (vh.node.isExpanded) {
                    notifyItemRangeInserted(vh.layoutPosition + 1, deltaSize)
                } else {
                    notifyItemRangeRemoved(vh.layoutPosition + 1, deltaSize)
                }
            } else {
                // tapping on a leaf node toggles the checkbox
                binding.checkBoxView.performClick()
                binding.checkBoxView.refreshDrawableState()
            }
        }
        // context and long clicking a tag opens the add tag dialog with the current tag as the prefix
        vh.itemView.setOnContextAndLongClickListener(tagContextAndLongClickListener)
        return vh
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) {
        val binding = holder.binding
        holder.node = getVisibleTagTreeNode(position)!!
        holder.node.vh = holder
        holder.itemView.tag = holder.node.tag

        if (hasVisibleNestedTag) {
            binding.expandButton.visibility = if (holder.node.isNotLeaf()) View.VISIBLE else View.INVISIBLE
            updateExpanderBackgroundImage(binding.expandButton, holder.node)
            // shift according to the level
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMarginStart(HIERARCHY_SHIFT_BASE * holder.node.level)
            binding.expandButton.layoutParams = lp
        } else {
            // do not add padding if there is no visible nested tag
            binding.expandButton.visibility = View.GONE
        }
        binding.expandButton.contentDescription = holder.itemView.context.getString(R.string.expand_tag, holder.node.tag.replace("::", " "))

        binding.textView.text = TagsUtil.getTagParts(holder.node.tag).last()

        if (tags.isIndeterminate(holder.node.tag)) {
            binding.checkBoxView.state = INDETERMINATE
        } else {
            binding.checkBoxView.state = if (tags.isChecked(holder.node.tag)) CHECKED else UNCHECKED
        }
        holder.node.updateCheckBoxCycleStyle(tags)
    }

    /**
     * Find the [TagTreeNode] of the [index]-th visible tag.
     * Implemented by walking the tree using [TagTreeNode.subtreeSize].
     *
     * @param index The index of the node to find.
     * @return The corresponding [TagTreeNode]. Null if out-of-bound.
     */
    private fun getVisibleTagTreeNode(index: Int): TagTreeNode? {
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

    /**
     * @return The number of visible tags.
     */
    override fun getItemCount(): Int = treeRoot.subtreeSize

    /**
     * Build the tag tree. The tags have been sorted using the hierarchical comparator
     * [TagsUtil.compareTag], which leads to a DFN order. Use a stack to build the tree without
     * recursion.
     * The initial expand states are inherited through [tagToIsExpanded].
     * A special tag can be set so that the path to it is expanded.
     *
     * @param expandTarget The target tag to expand. Do nothing if it is empty or not found.
     */
    @NeedsTest("#18481 - case insensitivity")
    private fun buildTagTree(expandTarget: String) {
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
        val stack = Stack<TagTreeNode>()
        treeRoot = TagTreeNode("", null, ArrayList(), -1, 0, true, 0, null)
        stack.add(treeRoot)
        tagToNode.clear()

        fun stackPopAndPushUp() {
            val popped = stack.pop()
            stack.peek().subtreeSize += popped.getContributeSize()
            stack.peek().subtreeCheckedCnt += popped.subtreeCheckedCnt
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
                TagTreeNode(tag, parent, ArrayList(), parent.level + 1, 1, tagToIsExpanded[tag]!!, if (tags.isChecked(tag)) 1 else 0, null)
            parent.children.add(node)
            tagToNode[tag] = node
            stack.add(node)
            if (stack.size > 2) {
                hasVisibleNestedTag = true
            }
        }
        while (stack.size > 1) {
            stackPopAndPushUp()
        }
    }

    /**
     * Set the background resource of the [ImageButton] according to information of the given [TagTreeNode].
     *
     * @param button The [ImageButton] to update.
     * @param node The corresponding [TagTreeNode].
     */
    private fun updateExpanderBackgroundImage(
        button: ImageButton,
        node: TagTreeNode,
    ) {
        // More custom display related to the node can be added here.
        // For example, display some icon if the node is a leaf? (assets required)
        when (node.isExpanded) {
            true -> button.setBackgroundResource(R.drawable.ic_expand_more_black_24dp_xml)
            false -> button.setBackgroundResource(R.drawable.ic_baseline_chevron_right_24)
        }
    }

    override fun getFilter(): TagsFilter = TagsFilter()

    // Custom Filter class - as seen in http://stackoverflow.com/a/29792313/1332026
    inner class TagsFilter : TypedFilter<String>({ tags.toList() }) {
        /**
         * A tag may be set so that the path to it is expanded immediately after the filter displays the result.
         * When empty, nothing is to be expanded.
         * It is cleared every time after the filtered result is calculated.
         * @see publishResults
         */
        private var expandTarget = String()

        override fun filterResults(
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

        override fun publishResults(
            constraint: CharSequence?,
            results: List<String>,
        ) {
            filteredList.clear()
            filteredList.addAll(results)
            sortData()
            buildTagTree(expandTarget)
            expandTarget = String()
            notifyDataSetChanged()
        }

        /**
         * Set the target to expand in the next filtering (optional).
         * @see expandTarget
         */
        fun setExpandTarget(tag: String) {
            expandTarget = tag
        }
    }

    init {
        sortData()
        filteredList = ArrayList(tags.toList())
        tagToNode = HashMap()
        tagToIsExpanded = HashMap()
        // set the initial expand state according to its checked state
        tags.forEach { tagToIsExpanded[it] = tags.isChecked(it) || tags.isIndeterminate(it) }
        hasVisibleNestedTag = false
        buildTagTree(String())
    }

    companion object {
        const val HIERARCHY_SHIFT_BASE = 50
    }
}

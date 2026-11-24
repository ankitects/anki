// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Tarek Mohamed Abdalla <tarekkma@gmail.com>

package com.ichi2.anki.dialogs.tags

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Filterable
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.annotation.VisibleForTesting
import androidx.recyclerview.widget.RecyclerView
import com.ichi2.anki.CommonString
import com.ichi2.anki.OnContextAndLongClickListener
import com.ichi2.anki.OnContextAndLongClickListener.Companion.setOnContextAndLongClickListener
import com.ichi2.anki.R
import com.ichi2.anki.databinding.ItemTagBinding
import com.ichi2.ui.CheckBoxTriStates
import com.ichi2.ui.CheckBoxTriStates.State.CHECKED
import com.ichi2.ui.CheckBoxTriStates.State.INDETERMINATE
import com.ichi2.ui.CheckBoxTriStates.State.UNCHECKED
import com.ichi2.utils.TagsUtil
import com.ichi2.utils.TypedFilter
import java.util.IdentityHashMap
import com.ichi2.anki.common.android.R as CommonR

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
        internal lateinit var node: TagTree.Node
            private set

        internal fun bindNode(
            node: TagTree.Node,
            holders: MutableMap<TagTree.Node, ViewHolder>,
        ) {
            clearNodeBinding(holders)
            this.node = node
            holders[node] = this
        }

        internal fun clearNodeBinding(holders: MutableMap<TagTree.Node, ViewHolder>) {
            // If a note already has a replacement holder, preserve its binding.
            if (::node.isInitialized && holders[node] === this) {
                holders.remove(node)
            }
        }

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

    private val tree = TagTree(tags)
    private val boundHolders = IdentityHashMap<TagTree.Node, ViewHolder>()

    /**
     * Context and Long click listener for each tag item. Used to add a subtag for the clicked tag.
     * The full tag is passed through View.tag
     */
    var tagContextAndLongClickListener: OnContextAndLongClickListener? = null

    fun sortData() {
        tags.sort()
    }

    /** Refresh the tag tree and checkbox states after bulk selection changes. */
    fun notifyCheckedStatusesChanged() {
        boundHolders.clear()
        tree.refreshSelection()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val binding = ItemTagBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        val vh = ViewHolder(binding)
        // clicking the checkbox toggles the tag's check state
        binding.checkBoxView.setOnClickListener {
            tree.toggleChecked(vh.node).forEach { node -> boundHolders[node]?.let(::bindCheckBox) }
            onSelectionUpdate()
        }
        // clicking other parts toggles the expansion state, or the checkbox (for leaf)
        vh.itemView.setOnClickListener {
            if (vh.node.isNotLeaf()) {
                tree.toggleExpanded(vh.node)
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
        holder.bindNode(tree[position]!!, boundHolders)
        holder.itemView.tag = holder.node.tag

        if (tree.hasVisibleNestedTag) {
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
        binding.expandButton.contentDescription =
            holder.itemView.context.getString(CommonString.expand_tag, holder.node.tag.replace("::", " "))

        binding.textView.text = TagsUtil.getTagParts(holder.node.tag).last()

        bindCheckBox(holder)
    }

    private fun bindCheckBox(holder: ViewHolder) {
        holder.binding.checkBoxView.apply {
            state =
                when (tree.state(holder.node)) {
                    TagTree.State.INDETERMINATE -> INDETERMINATE
                    TagTree.State.CHECKED -> CHECKED
                    TagTree.State.UNCHECKED -> UNCHECKED
                }
            val hasSelectedDescendants = tree.hasSelectedDescendants(holder.node)
            cycleIndeterminateToChecked = hasSelectedDescendants
            cycleCheckedToIndeterminate = hasSelectedDescendants
            refreshDrawableState()
        }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        holder.clearNodeBinding(boundHolders)
        super.onViewRecycled(holder)
    }

    override fun getItemCount(): Int = tree.size

    /**
     * Set the background resource of the [ImageButton] according to information of the given [TagTree.Node].
     *
     * @param button The [ImageButton] to update.
     * @param node The corresponding [TagTree.Node].
     */
    private fun updateExpanderBackgroundImage(
        button: ImageButton,
        node: TagTree.Node,
    ) {
        // More custom display related to the node can be added here.
        // For example, display some icon if the node is a leaf? (assets required)
        when (node.isExpanded) {
            true -> button.setBackgroundResource(R.drawable.ic_expand_more_black_24dp_xml)
            false -> button.setBackgroundResource(CommonR.drawable.ic_baseline_chevron_right_24)
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
        ): List<String> = tree.filterResults(constraint, items)

        override fun publishResults(
            constraint: CharSequence?,
            results: List<String>,
        ) {
            boundHolders.clear()
            tree.publishResults(results, expandTarget)
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

    companion object {
        const val HIERARCHY_SHIFT_BASE = 50
    }
}

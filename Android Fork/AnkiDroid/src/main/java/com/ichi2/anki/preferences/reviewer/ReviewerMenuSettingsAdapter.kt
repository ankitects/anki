// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences.reviewer

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.ichi2.anki.R
import com.ichi2.anki.databinding.ItemReviewerMenuBinding
import com.ichi2.anki.databinding.ItemReviewerMenuDisplayTypeBinding
import java.util.Objects

/**
 * Provides bindings from menu items and display types (headings) to [RecyclerView] views
 * and support for dragging menu items to change display types or reorder.
 *
 * Handles ViewHolders for two classes:
 * * [ReviewerMenuSettingsRecyclerItem.DisplayType] - Headings: Always show, Menu only, etc...
 *   * [DisplayTypeViewHolder]
 * * [ReviewerMenuSettingsRecyclerItem.Action] - Study screen menu items: Undo, Flag, etc...
 *   * [ActionViewHolder]
 *
 * @see ReviewerMenuSettingsFragment
 * @see ReviewerMenuSettingsRecyclerItem
 */
class ReviewerMenuSettingsAdapter(
    private val items: MutableList<ReviewerMenuSettingsRecyclerItem>,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    init {
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (ReviewerMenuSettingsType.entries[viewType]) {
            ReviewerMenuSettingsType.ACTION -> {
                val binding = ItemReviewerMenuBinding.inflate(inflater, parent, false)
                ActionViewHolder(binding)
            }
            ReviewerMenuSettingsType.DISPLAY_TYPE -> {
                val binding = ItemReviewerMenuDisplayTypeBinding.inflate(inflater, parent, false)
                DisplayTypeViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
    ) {
        val item = items[position]
        when (holder) {
            is ActionViewHolder -> holder.bind((item as ReviewerMenuSettingsRecyclerItem.Action).viewerAction)
            is DisplayTypeViewHolder -> holder.bind((item as ReviewerMenuSettingsRecyclerItem.DisplayType).menuDisplayType)
        }
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int = items[position].viewType.ordinal

    override fun getItemId(position: Int): Long {
        val item = items[position]
        return Objects.hash(item.viewType.ordinal, item).toLong()
    }

    private var onDragHandleTouchedListener: ((RecyclerView.ViewHolder) -> Unit)? = null

    fun setOnDragHandleTouchedListener(listener: (RecyclerView.ViewHolder) -> Unit) {
        this.onDragHandleTouchedListener = listener
    }

    /** @see [R.layout.item_reviewer_menu] */
    private inner class ActionViewHolder(
        private val binding: ItemReviewerMenuBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(action: ViewerAction) {
            binding.title.text = action.title(itemView.context)
            action.drawableRes?.let { binding.icon.setBackgroundResource(it) }

            binding.dragHandle.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    onDragHandleTouchedListener?.invoke(this)
                }
                return@setOnTouchListener false
            }
        }
    }

    /** @see [R.layout.item_reviewer_menu_display_type] */
    private class DisplayTypeViewHolder(
        private val binding: ItemReviewerMenuDisplayTypeBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(displayCategory: MenuDisplayType) {
            binding.title.setText(displayCategory.title)
        }
    }
}

enum class ReviewerMenuSettingsType {
    ACTION,
    DISPLAY_TYPE,
}

/**
 * @param viewType type to be returned at [RecyclerView.Adapter.getItemViewType]
 */
sealed class ReviewerMenuSettingsRecyclerItem(
    val viewType: ReviewerMenuSettingsType,
) {
    data class Action(
        val viewerAction: ViewerAction,
    ) : ReviewerMenuSettingsRecyclerItem(ReviewerMenuSettingsType.ACTION)

    data class DisplayType(
        val menuDisplayType: MenuDisplayType,
    ) : ReviewerMenuSettingsRecyclerItem(ReviewerMenuSettingsType.DISPLAY_TYPE)
}

// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences.reviewer

import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.ichi2.anki.utils.ext.swapPositions

/**
 * A [ItemTouchHelper.Callback] for the [ReviewerMenuSettingsAdapter].
 *
 * It allows drag and dropping of [ReviewerMenuSettingsAdapter.ActionViewHolder], but not of
 * [ReviewerMenuSettingsAdapter.DisplayTypeViewHolder], or any kind of swipe.
 *
 * [setOnClearViewListener] can be used to set an action to run after the user interaction has ended
 * (see [clearView]).
 */
class ReviewerMenuSettingsTouchHelperCallback(
    private val items: MutableList<ReviewerMenuSettingsRecyclerItem>,
) : ItemTouchHelper.Callback() {
    private val movementFlags = makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
    ): Int =
        if (viewHolder.itemViewType == ReviewerMenuSettingsType.DISPLAY_TYPE.ordinal) {
            0
        } else {
            movementFlags
        }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder,
    ): Boolean {
        val fromPosition = viewHolder.absoluteAdapterPosition
        val toPosition = target.absoluteAdapterPosition

        // `Always show` should always be the first element, so don't allow moving above it
        if (toPosition == 0) return false

        items.swapPositions(fromPosition, toPosition)
        recyclerView.adapter?.notifyItemMoved(fromPosition, toPosition)
        return true
    }

    override fun clearView(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
    ) {
        super.clearView(recyclerView, viewHolder)
        onClearViewListener?.onClearView(items)
    }

    private var onClearViewListener: OnClearViewListener<ReviewerMenuSettingsRecyclerItem>? = null

    /** Sets a listener to be called after [clearView] */
    fun setOnClearViewListener(listener: OnClearViewListener<ReviewerMenuSettingsRecyclerItem>) {
        onClearViewListener = listener
    }

    override fun onSwiped(
        viewHolder: RecyclerView.ViewHolder,
        direction: Int,
    ) {
        // do nothing
    }
}

fun interface OnClearViewListener<T> {
    fun onClearView(items: List<T>)
}

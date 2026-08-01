/*
 *  Copyright (c) 2024 Brayan Oliveira <brayandso.dev@gmail.com>
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
        if (viewHolder.itemViewType == ReviewerMenuSettingsRecyclerItem.DISPLAY_TYPE_VIEW_TYPE) {
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

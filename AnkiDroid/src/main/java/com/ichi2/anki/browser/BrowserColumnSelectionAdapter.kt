/*
 *  Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.browser

import android.os.Build
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.ichi2.anki.R
import com.ichi2.anki.browser.BrowserColumnSelectionRecyclerItem.ColumnItem
import com.ichi2.anki.browser.BrowserColumnSelectionRecyclerItem.UsageItem
import com.ichi2.anki.browser.ColumnUsage.AVAILABLE
import com.ichi2.anki.databinding.ItemBrowserColumnsEntryBinding
import com.ichi2.anki.databinding.ItemBrowserColumnsHeadingBinding
import com.ichi2.anki.utils.ext.swapPositions

class BrowserColumnSelectionAdapter(
    val items: MutableList<BrowserColumnSelectionRecyclerItem>,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    /** @return an ordered collection of the columns a user wants to display */
    val selected
        get() = displayed.map { it.columnType }

    /** @return an ordered collection of the columns a user wants to display */
    val displayed: List<ColumnWithSample>
        get() =
            items
                .asSequence()
                .withIndex()
                .filter { it.index < positionOfAvailableHeading }
                .map { it.value }
                .filterIsInstance<ColumnItem>()
                .map { it.column }
                .toList()

    /** @return an ordered collection of the columns a user may display */
    val available: List<ColumnWithSample>
        get() =
            items
                .asSequence()
                .withIndex()
                .filter { it.index > positionOfAvailableHeading }
                .map { it.value }
                .filterIsInstance<ColumnItem>()
                .map { it.column }
                .toList()

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            BrowserColumnSelectionRecyclerItem.COLUMN_VIEW_TYPE ->
                ColumnViewHolder(ItemBrowserColumnsEntryBinding.inflate(inflater, parent, false))

            BrowserColumnSelectionRecyclerItem.USAGE_VIEW_TYPE -> {
                UsageViewHolder(ItemBrowserColumnsHeadingBinding.inflate(inflater, parent, false))
            }
            else -> throw IllegalArgumentException("Unexpected viewType")
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
    ) {
        val item = items[position]
        when (holder) {
            is ColumnViewHolder -> holder.bind((item as ColumnItem).column)
            is UsageViewHolder -> holder.bind((item as UsageItem).columnUsage)
        }
    }

    /**
     * Index of [ColumnUsage.AVAILABLE] in the list.
     * Columns before this heading are used in the Browser
     */
    private val positionOfAvailableHeading
        get() = items.indexOfFirst { it is UsageItem && it.columnUsage == AVAILABLE }

    private fun onToggle(fromPosition: Int) {
        // if we're moving up, this moves it 1 above the heading.
        // if we're moving down, this moves it 1 below the heading
        val toPosition = positionOfAvailableHeading
        items.move(fromPosition, toPosition)
        notifyItemMoved(fromPosition, toPosition)

        notifyItemChanged(toPosition)
    }

    fun refreshDataset() {
        // this needs to be done after onMoved, or the drag operation sometimes completes early
        // when on a tablet
        notifyItemRangeChanged(0, items.size)
    }

    fun <T> MutableList<T>.move(
        fromIndex: Int,
        toIndex: Int,
    ) {
        val item = this.removeAt(fromIndex)
        this.add(toIndex, item)
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int = items[position].viewType

    private var onDragHandleTouchedListener: ((RecyclerView.ViewHolder) -> Unit)? = null

    fun setOnDragHandleTouchedListener(listener: (RecyclerView.ViewHolder) -> Unit) {
        this.onDragHandleTouchedListener = listener
    }

    /**
     * @see R.layout.item_browser_columns_entry
     */
    private inner class ColumnViewHolder(
        private val binding: ItemBrowserColumnsEntryBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(column: ColumnWithSample) {
            column.label.let { binding.columnTitle.text = it }
            column.sampleValue.let { binding.columnExample.text = it }

            binding.buttonToggleColumn.apply {
                // NICE_TO_HAVE: animate between + and -
                val isExclude = absoluteAdapterPosition < positionOfAvailableHeading
                setImageResource(if (isExclude) R.drawable.ic_remove else R.drawable.ic_add)

                val label = context.getString(if (isExclude) R.string.exclude_column else R.string.include_column)
                contentDescription = label
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    tooltipText = label
                }
                setOnClickListener {
                    onToggle(absoluteAdapterPosition)
                }
            }

            binding.dragHandle.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    onDragHandleTouchedListener?.invoke(this)
                }
                false
            }
        }
    }

    /** @see [R.layout.item_browser_columns_heading] */
    private class UsageViewHolder(
        private val binding: ItemBrowserColumnsHeadingBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(columnUsage: ColumnUsage) {
            binding.title.text = itemView.context.getString(columnUsage.titleRes)
        }
    }
}

/**
 * A [ItemTouchHelper.Callback] for the [BrowserColumnSelectionAdapter].
 */
open class BrowserColumnSelectionTouchHelperCallback(
    private val items: MutableList<BrowserColumnSelectionRecyclerItem>,
) : ItemTouchHelper.Callback() {
    private val movementFlags = makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
    ): Int =
        if (viewHolder.itemViewType == BrowserColumnSelectionRecyclerItem.USAGE_VIEW_TYPE) {
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

        // `Available` should always be the first element, so don't allow moving above it
        if (toPosition == 0) return false

        items.swapPositions(fromPosition, toPosition)
        recyclerView.adapter?.notifyItemMoved(fromPosition, toPosition)
        return true
    }

    override fun onSwiped(
        viewHolder: RecyclerView.ViewHolder,
        direction: Int,
    ) {
        // do nothing
    }
}

/**
 * An item in [BrowserColumnSelectionAdapter], either a column usage heading (Displayed/Available)
 * or a draggable column
 *
 * @param viewType type to be returned at [RecyclerView.Adapter.getItemViewType]
 */
sealed class BrowserColumnSelectionRecyclerItem(
    val viewType: Int,
) {
    data class ColumnItem(
        val column: ColumnWithSample,
    ) : BrowserColumnSelectionRecyclerItem(COLUMN_VIEW_TYPE)

    data class UsageItem(
        val columnUsage: ColumnUsage,
    ) : BrowserColumnSelectionRecyclerItem(USAGE_VIEW_TYPE)

    companion object {
        const val COLUMN_VIEW_TYPE = 0
        const val USAGE_VIEW_TYPE = 1
    }
}

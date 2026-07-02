/*
 * Copyright (c) 2022 lukstbit <52494258+lukstbit@users.noreply.github.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.notetype

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ichi2.anki.R
import com.ichi2.anki.databinding.ItemManageNoteTypeBinding
import com.ichi2.anki.notetype.NoteTypesAdapter.NoteTypeViewHolder
import com.ichi2.anki.utils.ext.usingStyledAttributes

private val notetypeNamesAndCountDiff =
    object : DiffUtil.ItemCallback<NoteTypeItemState>() {
        override fun areItemsTheSame(
            oldItem: NoteTypeItemState,
            newItem: NoteTypeItemState,
        ): Boolean =
            oldItem.id == newItem.id &&
                oldItem.name == newItem.name &&
                oldItem.useCount == newItem.useCount &&
                oldItem.isSelected == newItem.isSelected

        override fun areContentsTheSame(
            oldItem: NoteTypeItemState,
            newItem: NoteTypeItemState,
        ): Boolean =
            oldItem.id == newItem.id &&
                oldItem.name == newItem.name &&
                oldItem.useCount == newItem.useCount &&
                oldItem.isSelected == newItem.isSelected
    }

internal class NoteTypesAdapter(
    private val context: Context,
    private val onItemClick: (NoteTypeItemState) -> Unit,
    private val onItemLongClick: (NoteTypeItemState) -> Unit,
    private val onItemChecked: (NoteTypeItemState, Boolean) -> Unit,
    private val onEditCards: (NoteTypeItemState) -> Unit,
    private val onRename: (NoteTypeItemState) -> Unit,
    private val onDelete: (NoteTypeItemState) -> Unit,
) : ListAdapter<NoteTypeItemState, NoteTypeViewHolder>(notetypeNamesAndCountDiff) {
    private val layoutInflater = LayoutInflater.from(context)

    var isInMultiSelectMode: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): NoteTypeViewHolder =
        NoteTypeViewHolder(
            binding = ItemManageNoteTypeBinding.inflate(layoutInflater, parent, false),
            onDelete = onDelete,
            onRename = onRename,
            onEditCards = onEditCards,
            onItemClick = onItemClick,
            onItemLongClick = onItemLongClick,
            onItemChecked = onItemChecked,
        )

    override fun onBindViewHolder(
        holder: NoteTypeViewHolder,
        position: Int,
    ) {
        holder.bind(getItem(position))
    }

    inner class NoteTypeViewHolder(
        private val binding: ItemManageNoteTypeBinding,
        onItemClick: (NoteTypeItemState) -> Unit,
        onItemLongClick: (NoteTypeItemState) -> Unit,
        onItemChecked: (NoteTypeItemState, Boolean) -> Unit,
        onEditCards: (NoteTypeItemState) -> Unit,
        onRename: (NoteTypeItemState) -> Unit,
        onDelete: (NoteTypeItemState) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {
        private val selectableItemBackground: Int =
            context.usingStyledAttributes(null, intArrayOf(android.R.attr.selectableItemBackground)) {
                getResourceId(0, 0)
            }
        private val selectedNoteTypeBackground: Int =
            context.usingStyledAttributes(null, intArrayOf(R.attr.currentDeckBackground)) {
                getResourceId(0, 0)
            }

        private var noteTypeItemState: NoteTypeItemState? = null

        init {
            itemView.setOnClickListener { noteTypeItemState?.let(onItemClick) }
            itemView.setOnLongClickListener {
                noteTypeItemState?.let(onItemLongClick)
                true
            }
            binding.editCardsButton.setOnClickListener { noteTypeItemState?.let(onEditCards) }
            binding.moreActionsButton.setOnClickListener {
                PopupMenu(context, binding.moreActionsButton)
                    .apply {
                        inflate(R.menu.note_types_more_actions)
                        setOnMenuItemClickListener { item ->
                            when (item.itemId) {
                                R.id.action_rename -> noteTypeItemState?.let(onRename)
                                R.id.action_delete -> noteTypeItemState?.let(onDelete)
                                else -> error("Unexpected menu item!")
                            }
                            true
                        }
                    }.show()
            }
            binding.checkbox.setOnCheckedChangeListener { _, isChecked ->
                noteTypeItemState?.let { onItemChecked(it, isChecked) }
            }
        }

        fun bind(state: NoteTypeItemState) {
            this.noteTypeItemState = state
            itemView.setBackgroundResource(if (state.isSelected) selectedNoteTypeBackground else selectableItemBackground)
            binding.editCardsButton.isVisible = !isInMultiSelectMode
            binding.moreActionsButton.isVisible = !isInMultiSelectMode
            binding.checkbox.isVisible = isInMultiSelectMode
            binding.checkbox.isChecked = isInMultiSelectMode && state.isSelected
            binding.noteName.text = state.name
            binding.noteUseCount.text =
                context.resources.getQuantityString(
                    R.plurals.model_browser_of_type,
                    state.useCount,
                    state.useCount,
                )
        }
    }
}

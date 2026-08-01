/*
 * Copyright (c) 2024 Ashish Yadav <mailtoashish693@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.dialogs

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.ichi2.anki.Flag
import com.ichi2.anki.databinding.ItemEditFlagBinding
import com.ichi2.utils.moveCursorToEnd
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Adapter for the RecyclerView displaying flag items.
 *
 * @param lifecycleScope The CoroutineScope used for launching coroutines.
 */
class FlagAdapter(
    private val lifecycleScope: CoroutineScope,
) : ListAdapter<FlagItem, FlagAdapter.FlagViewHolder>(FlagItemDiffCallback()) {
    class FlagViewHolder(
        binding: ItemEditFlagBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        val flagImageView: ImageView = binding.icFlag
        val flagNameText: TextView = binding.flagName
        val flagNameEdit: TextInputEditText = binding.flagNameEditText
        val editButton: MaterialButton = binding.actionEditFlag
        val saveButton: MaterialButton = binding.actionSaveFlagName
        val cancelButton: MaterialButton = binding.actionCancelFlagRename

        val flagNameViewLayout: LinearLayout = binding.flagNameViewLayout
        val flagNameEditLayout: LinearLayout = binding.editFlagNameLayout
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): FlagViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemEditFlagBinding.inflate(inflater, parent, false)
        return FlagViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: FlagViewHolder,
        position: Int,
    ) {
        val flagItem = getItem(position)

        holder.flagImageView.setImageResource(flagItem.icon)

        holder.flagNameEditLayout.visibility = View.GONE

        holder.flagNameText.text = flagItem.title
        holder.flagNameEdit.setText(flagItem.title)

        holder.editButton.setOnClickListener {
            flagItem.isInEditMode = true
            holder.flagNameViewLayout.visibility = View.GONE
            holder.flagNameEditLayout.visibility = View.VISIBLE
            holder.flagNameEdit.requestFocus()
            holder.flagNameEdit.moveCursorToEnd()
            val inputMethodManager = holder.flagNameEdit.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            inputMethodManager.showSoftInput(holder.flagNameEdit, InputMethodManager.SHOW_IMPLICIT)
        }

        holder.saveButton.setOnClickListener {
            val updatedTextName =
                holder.flagNameEdit.text
                    .toString()
                    .ifEmpty { flagItem.title }
            holder.flagNameViewLayout.visibility = View.VISIBLE
            holder.flagNameEditLayout.visibility = View.GONE
            val updatedFlagItem = flagItem.copy(title = updatedTextName)
            val updatedDataset = currentList.toMutableList()
            if (updatedFlagItem.title != flagItem.title) {
                lifecycleScope.launch {
                    flagItem.renameTo(updatedTextName)
                }
            }
            updatedFlagItem.isInEditMode = false
            updatedDataset[position] = updatedFlagItem
            submitList(updatedDataset)
        }

        holder.cancelButton.setOnClickListener {
            holder.flagNameViewLayout.visibility = View.VISIBLE
            holder.flagNameEditLayout.visibility = View.GONE
            flagItem.isInEditMode = false
        }
    }

    class FlagItemDiffCallback : DiffUtil.ItemCallback<FlagItem>() {
        override fun areItemsTheSame(
            oldItem: FlagItem,
            newItem: FlagItem,
        ): Boolean = oldItem.flag == newItem.flag

        override fun areContentsTheSame(
            oldItem: FlagItem,
            newItem: FlagItem,
        ): Boolean = oldItem.title == newItem.title
    }
}

/**
 * Data class representing a flag item.
 *
 * @property flag The ordinal value of the flag.
 * @property title The title or name of the flag.
 * @property icon The icon resource ID of the flag.
 * @property isInEditMode Whether the flag is being edited.
 */
data class FlagItem(
    val flag: Flag,
    val title: String,
    val icon: Int,
    var isInEditMode: Boolean = false,
) {
    /**
     * Renames the flag
     *
     * @param newName The new name for the flag.
     */
    suspend fun renameTo(newName: String) = flag.rename(newName)
}

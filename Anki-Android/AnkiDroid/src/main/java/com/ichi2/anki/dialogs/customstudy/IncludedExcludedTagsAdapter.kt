/*
 * Copyright (c) 2025 lukstbit <52494258+lukstbit@users.noreply.github.com>
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
package com.ichi2.anki.dialogs.customstudy

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.recyclerview.widget.RecyclerView
import anki.scheduler.CustomStudyDefaultsResponse
import com.ichi2.anki.databinding.ItemRequireExcludeTagBinding
import com.ichi2.anki.dialogs.customstudy.IncludedExcludedTagsAdapter.TagsSelectionMode.Exclude
import com.ichi2.anki.dialogs.customstudy.IncludedExcludedTagsAdapter.TagsSelectionMode.Include

/**
 * Shows a simple list of tags from which the user can select for a custom study session. For
 * simplicity this adapter is used for both types of lists.
 *
 * @param mode determines how the tags in the list backing this adapter are to be handled(either as
 * included or excluded tags for the custom study)
 *
 * @see TagLimitFragment
 * @see TagIncludedExcluded
 */
@SuppressLint("UseKtx") // properties initialization issues when using the extension function
class IncludedExcludedTagsAdapter(
    context: Context,
    val mode: TagsSelectionMode,
) : RecyclerView.Adapter<IncludedExcludedTagsAdapter.RequireExcludeTagsViewHolder>() {
    private val inflater = LayoutInflater.from(context)
    var tags: MutableList<TagIncludedExcluded> = mutableListOf()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    /**
     * Included tags are enabled only if the relevant checkbox is checked.
     */
    var isEnabled = false
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    /** Default background for a tag that is not selected. */
    private val selectableItemBackground: Int

    /** Background color for a tag that is selected(references R.attr.colorPrimary). */
    // TODO implement a ripple effect or combine the two backgrounds into one background drawable
    @ColorRes
    private val selectedItemBackground: Int

    init {
        val ta =
            context.obtainStyledAttributes(
                intArrayOf(android.R.attr.selectableItemBackground, androidx.appcompat.R.attr.colorPrimary),
            )
        selectableItemBackground = ta.getResourceId(0, 0)
        selectedItemBackground = ta.getColor(1, Color.BLUE)
        ta.recycle()
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): RequireExcludeTagsViewHolder =
        RequireExcludeTagsViewHolder(
            binding = ItemRequireExcludeTagBinding.inflate(inflater, parent, false),
        )

    override fun getItemCount(): Int = tags.size

    override fun onBindViewHolder(
        holder: RequireExcludeTagsViewHolder,
        position: Int,
    ) {
        val model = tags[position]
        holder.tagView.text = model.userFacingLabel
        val isSelected =
            when (mode) {
                Include -> model.isIncluded
                Exclude -> model.isExcluded
            }
        if (isSelected) {
            holder.tagView.setBackgroundColor(selectedItemBackground)
        } else {
            holder.tagView.setBackgroundResource(selectableItemBackground)
        }
        // "included" tags are allowed only if the relevant checkbox is checked
        holder.tagView.isEnabled = !(mode == Include && !isEnabled)
        holder.tagView.setOnClickListener {
            when (mode) {
                Include -> tags[position].isIncluded = !tags[position].isIncluded
                Exclude -> tags[position].isExcluded = !tags[position].isExcluded
            }
            notifyDataSetChanged()
        }
    }

    inner class RequireExcludeTagsViewHolder(
        binding: ItemRequireExcludeTagBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        val tagView: TextView = binding.tagView
    }

    enum class TagsSelectionMode {
        Include,
        Exclude,
    }
}

/** @see [CustomStudyDefaultsResponse.Tag] */
data class TagIncludedExcluded(
    val name: String,
    var isIncluded: Boolean = false,
    var isExcluded: Boolean = false,
) {
    val userFacingLabel: String
        get() = name.replace("_", " ")
}

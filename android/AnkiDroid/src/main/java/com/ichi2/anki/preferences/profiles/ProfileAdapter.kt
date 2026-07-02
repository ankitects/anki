/*
 * Copyright (c) 2025 Ashish Yadav <mailtoashish693@gmail.com>
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

package com.ichi2.anki.preferences.profiles

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.ichi2.anki.R

/**
 * Represents a user entity with a unique ID and display name.
 *
 * @property id Unique identifier for the user.
 * @property name Full name of the user.
 */
data class User(
    val id: Int,
    val name: String,
)

/**
 * RecyclerView adapter for displaying a list of users with options
 * to edit and delete each user.
 *
 * @param onEditClick Callback invoked when the edit button of a user item is clicked.
 * @param onDeleteClick Callback invoked when the delete button of a user item is clicked.
 */
class ProfilesAdapter(
    private val onEditClick: (User) -> Unit,
    private val onDeleteClick: (User) -> Unit,
) : androidx.recyclerview.widget.ListAdapter<User, ProfilesAdapter.ProfileViewHolder>(UserDiffCallback()) {
    inner class ProfileViewHolder(
        itemView: View,
    ) : RecyclerView.ViewHolder(itemView) {
        val tvInitial: TextView = itemView.findViewById(R.id.tvInitial)
        val tvUserName: TextView = itemView.findViewById(R.id.tvUserName)
        val btnEdit: MaterialButton = itemView.findViewById(R.id.edit_profile_button)
        val btnDelete: MaterialButton = itemView.findViewById(R.id.delete_profile_button)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ProfileViewHolder {
        val view =
            LayoutInflater
                .from(parent.context)
                .inflate(R.layout.item_profiles, parent, false)
        return ProfileViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: ProfileViewHolder,
        position: Int,
    ) {
        val user = getItem(position)

        holder.tvInitial.text = user.name
            .firstOrNull()
            ?.uppercaseChar()
            ?.toString() ?: "?"
        holder.tvUserName.text = user.name

        holder.btnEdit.setOnClickListener { onEditClick(user) }
        holder.btnDelete.setOnClickListener { onDeleteClick(user) }
    }
}

class UserDiffCallback : DiffUtil.ItemCallback<User>() {
    override fun areItemsTheSame(
        oldItem: User,
        newItem: User,
    ): Boolean = oldItem.id == newItem.id

    override fun areContentsTheSame(
        oldItem: User,
        newItem: User,
    ): Boolean = oldItem == newItem
}

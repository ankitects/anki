/*
 *  Copyright (c) 2025 Eric Li <ericli3690@gmail.com>
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

package com.ichi2.anki.reviewreminders

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Paint
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.materialswitch.MaterialSwitch
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.databinding.ItemScheduleRemindersBinding
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.anki.common.android.R as CommonR

class ScheduleRemindersAdapter(
    private val retrieveDeckNameFromID: (DeckId, callback: (deckName: String) -> Unit) -> Unit,
    private val retrieveCanUserAccessDeck: (DeckId, callback: (isDeckAccessible: Boolean) -> Unit) -> Unit,
    private val toggleReminderEnabled: (ReviewReminderId, ReviewReminderScope) -> Unit,
    private val editReminder: (ReviewReminder) -> Unit,
) : ListAdapter<ReviewReminder, ScheduleRemindersAdapter.ViewHolder>(diffCallback) {
    class ViewHolder(
        binding: ItemScheduleRemindersBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        var reminder: ReviewReminder? = null
        val context: Context = binding.root.context
        val deckTextView: TextView = binding.remindersListDeckText
        val timeTextView: TextView = binding.remindersListTimeText
        val switchView: MaterialSwitch = binding.remindersListSwitch
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemScheduleRemindersBinding.inflate(inflater, parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) {
        val reminder = getItem(position)
        holder.reminder = reminder

        holder.timeTextView.text = reminder.time.toFormattedString(holder.context)

        holder.itemView.setOnClickListener { editReminder(reminder) }

        holder.switchView.isChecked = reminder.enabled
        holder.switchView.setOnClickListener { toggleReminderEnabled(reminder.id, reminder.scope) }

        errorReminderIfDeckNotFound(reminder.scope, holder)
    }

    /**
     * Marks a review reminder's ViewHolder in the UI as errored-out if its corresponding deck cannot be found.
     * Otherwise, sets its ViewHolder's style to a normal state and sets the deck name text.
     * Never errors-out a global review reminder.
     *
     * We do this instead of immediately deleting the reminder because there are many reasons why a deck ID might
     * be unavailable (ex. the user might undo a deletion, restore their collection from a backup, etc.),
     * and we don't want to delete the user's reminder without their consent. Reminder deletion should
     * be an explicit action; leaving errored-out reminders in the UI allows the user to explicitly decide
     * what to do with them.
     */
    private fun errorReminderIfDeckNotFound(
        scope: ReviewReminderScope,
        holder: ViewHolder,
    ) {
        val activeTextColor = getThemeColor(holder.context, normalTextThemeAttribute)
        val activeTrackColor = getThemeColor(holder.context, normalPrimaryColorThemeAttribute)
        val inactiveTextColor = holder.context.getColor(erroredReviewReminderColor)
        val inactiveTrackColor = holder.context.getColor(erroredReviewReminderColor)

        when (scope) {
            is ReviewReminderScope.Global -> {
                holder.deckTextView.text = with(holder.context) { TR.sentenceCase.allDecks }
                setTextViewStrikethrough(holder.timeTextView, false)
                setViewHolderColors(holder, activeTextColor, activeTrackColor)
            }
            is ReviewReminderScope.DeckSpecific ->
                retrieveCanUserAccessDeck(scope.did) { isDeckAccessible ->
                    if (isDeckAccessible) {
                        retrieveDeckNameFromID(scope.did) { holder.deckTextView.text = it }
                        setTextViewStrikethrough(holder.timeTextView, false)
                        setViewHolderColors(holder, activeTextColor, activeTrackColor)
                    } else {
                        holder.deckTextView.text = "Deck not found"
                        setTextViewStrikethrough(holder.timeTextView, true)
                        setViewHolderColors(holder, inactiveTextColor, inactiveTrackColor)
                    }
                }
        }
    }

    /**
     * Sets the text color and switch track color of a ViewHolder.
     */
    private fun setViewHolderColors(
        holder: ViewHolder,
        textColor: Int,
        trackColor: Int,
    ) {
        with(holder) {
            deckTextView.setTextColor(textColor)
            timeTextView.setTextColor(textColor)
            switchView.trackTintList =
                ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_checked)),
                    intArrayOf(trackColor),
                )
        }
    }

    /**
     * Sets or unsets strikethrough on a TextView.
     */
    private fun setTextViewStrikethrough(
        textView: TextView,
        setStrikethrough: Boolean,
    ) {
        textView.paintFlags =
            if (setStrikethrough) {
                textView.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                textView.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }
    }

    /**
     * Caches theme color values by resource id.
     * Maps resource id to resolved color int.
     */
    private val cachedThemeColors = mutableMapOf<Int, Int>()

    /**
     * Returns the theme color for the given resource id, with caching.
     * Used for resetting the text color of a reminder in the UI if it was previously errored-out.
     */
    private fun getThemeColor(
        context: Context,
        resId: Int,
    ): Int =
        cachedThemeColors.getOrPut(resId) {
            TypedValue()
                .apply {
                    context.theme.resolveAttribute(resId, this, true)
                }.data
        }

    @Suppress("MayBeConstant")
    companion object {
        /**
         * Theme attribute for the primary color used in the normal (non-errored-out) state of a review reminder.
         * Used for the switch track color of a reminder in the UI if it is not errored-out.
         * The corresponding color resource can be obtained via [getThemeColor].
         */
        private val normalPrimaryColorThemeAttribute: Int = android.R.attr.colorPrimary

        /**
         * Theme attribute for the text color used in the normal (non-errored-out) state of a review reminder.
         * Used for the text color of a reminder in the UI if it is not errored-out.
         * The corresponding color resource can be obtained via [getThemeColor].
         */
        private val normalTextThemeAttribute: Int = com.google.android.material.R.attr.colorOnSurface

        /**
         * Color of the activated switch and text of an element in the review reminder UI list when its review reminder
         * is errored-out. A deck-specific review reminder can become errored-out if its corresponding deck cannot be found.
         */
        private val erroredReviewReminderColor: Int = CommonR.color.material_grey_500

        private val diffCallback =
            object : DiffUtil.ItemCallback<ReviewReminder>() {
                override fun areItemsTheSame(
                    oldItem: ReviewReminder,
                    newItem: ReviewReminder,
                ): Boolean = oldItem.id == newItem.id

                override fun areContentsTheSame(
                    oldItem: ReviewReminder,
                    newItem: ReviewReminder,
                ): Boolean = oldItem == newItem
            }
    }
}

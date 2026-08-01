/*
 *  Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.scheduling

import androidx.annotation.DrawableRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.libanki.CardId
import com.ichi2.anki.libanki.CardType
import com.ichi2.anki.libanki.sched.SetDueDateDays
import com.ichi2.anki.observability.undoableOp
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * 0 = today
 * @see SetDueDateDays
 */
typealias NumberOfDaysInFuture = Int

/**
 * Represents the interval between reviews in days.
 *
 * - A value of `0` means the next review is due today.
 * - Indicates the number of days until a card's next review.
 */
typealias ReviewIntervalDays = Int

/**
 * [ViewModel] for [SetDueDateDialog]
 */
class SetDueDateViewModel : ViewModel() {
    /** Whether the value may be submitted */
    val isValidFlow = MutableStateFlow(false)

    /** The cards to change the due date of */
    lateinit var cardIds: List<CardId>

    /** Whether the Free Spaced Repetition Scheduler is enabled */
    // initialized in init()
    private var fsrsEnabled: Boolean = false
        set(value) {
            field = value
            Timber.d("fsrsEnabled : %b", value)
            if (value) {
                Timber.d("updateIntervalToMatchDueDate forced to true: FSRS is enabled")
                this.updateIntervalToMatchDueDate = true
            }
        }

    /** Whether the user can set [updateIntervalToMatchDueDate] */
    val canSetUpdateIntervalToMatchDueDate
        // this only makes sense in SM-2, where the due date does not directly impact the next
        // interval calculation. In FSRS, the current date is taken into account
        // so ivl should match due date for simplicity
        get() = !fsrsEnabled

    /**
     * The number of cards which will be affected
     *
     * Primarily used for plurals
     */
    val cardCount
        get() = cardIds.size

    /** The number of days in the future if we are on [Tab.SINGLE_DAY] */
    var nextSingleDayDueDate: NumberOfDaysInFuture? = null
        set(value) {
            field =
                if (value != null && value >= 0) {
                    value
                } else {
                    null
                }
            Timber.d("update SINGLE_DAY to %s", field)
            refreshIsValid()
        }

    var dateRange: DateRange = DateRange()
        private set

    internal var currentTab = Tab.SINGLE_DAY
        set(value) {
            Timber.i("selected tab %s", value)
            field = value
            refreshIsValid()
        }

    /**
     * If `true`, the interval of the card is updated to match the calculated due date
     *
     * This is only supported when using SM-2. In FSRS, there's no reason for ivl != due date,
     * as the date when a card is seen affects the scheduling.
     *
     * @throws UnsupportedOperationException if unset when FSRS is enabled
     */
    var updateIntervalToMatchDueDate: Boolean = false
        get() = if (fsrsEnabled) true else field
        set(value) {
            Timber.d("updateIntervalToMatchDueDate: %b", value)
            if (fsrsEnabled && !value) {
                throw UnsupportedOperationException("due date must match interval if using FSRS")
            }
            field = value
        }

    /**
     * The current interval of the card.
     *
     * The value represents the number of days.
     * The value is not-null if exactly one card is selected, which is the only case
     * in which the view should display the interval.
     */
    val currentInterval = MutableStateFlow<ReviewIntervalDays?>(null)

    fun init(
        cardIds: LongArray,
        fsrsEnabled: Boolean,
    ) {
        this.cardIds = cardIds.toList()
        this.fsrsEnabled = fsrsEnabled

        initCurrentInterval(cardIds)
    }

    private fun initCurrentInterval(cardIds: LongArray) {
        // Current interval cannot be shown if multiple cards are selected
        if (cardCount > 1) {
            return
        }

        viewModelScope.launch {
            withCol {
                getCard(cardIds.first()).let {
                    // Only show interval if card is in the review state
                    // New, learning, or relearning cards have an interval of 0 and should not display it
                    currentInterval.value = if (it.type == CardType.Rev) it.ivl else null
                }
            }
        }
    }

    fun setNextDateRangeStart(value: Int?) {
        Timber.d("updated date range start to %s", value)
        dateRange.start = value
        refreshIsValid()
    }

    fun setNextDateRangeEnd(value: Int?) {
        Timber.d("updated date range end to %s", value)
        dateRange.end = value
        refreshIsValid()
    }

    private fun refreshIsValid() {
        val isValid =
            when (currentTab) {
                Tab.SINGLE_DAY -> nextSingleDayDueDate.let { it != null && it >= 0 }
                Tab.DATE_RANGE -> dateRange.isValid()
            }
        isValidFlow.update { isValid }
    }

    fun calculateDaysParameter(): SetDueDateDays? {
        val dateRange =
            when (currentTab) {
                Tab.SINGLE_DAY -> nextSingleDayDueDate?.let { "$it" }
                Tab.DATE_RANGE -> dateRange.toDaysParameter()
            } ?: return null

        // add a "!" suffix if necessary
        val param = if (this.updateIntervalToMatchDueDate) "$dateRange!" else dateRange
        return SetDueDateDays(param)
    }

    /**
     * Updates the due date of [cardIds] based on the current state.
     * @return The number of cards affected, or `null` if an error occurred
     */
    fun updateDueDateAsync() =
        viewModelScope.async {
            val days = calculateDaysParameter() ?: return@async null
            // TODO: Provide a config parameter - we can use this to set a 'last used value' in the UI
            // when the screen is opened
            undoableOp { sched.setDueDate(cardIds, days) }
            return@async cardIds.size
        }

    enum class Tab(
        val position: Int,
        @DrawableRes val icon: Int,
        val text: Int,
    ) {
        /** Set the due date to a single day */
        SINGLE_DAY(0, R.drawable.calendar_single_day, R.string.set_due_date_day),

        /** Sets the due date randomly between a range of days */
        DATE_RANGE(1, R.drawable.calendar_date_range, R.string.set_due_date_date_range),
    }

    class DateRange(
        var start: NumberOfDaysInFuture? = null,
        var end: NumberOfDaysInFuture? = null,
    ) {
        fun isValid(): Boolean {
            val start = start ?: return false
            val end = end ?: return false
            if (start < 0) return false // 0 is valid -> today
            return start <= end
        }

        fun toDaysParameter(): String? {
            if (!isValid()) return null
            if (start == end) return "$start"
            return "$start-$end"
        }

        override fun toString() = "$start – $end"
    }
}

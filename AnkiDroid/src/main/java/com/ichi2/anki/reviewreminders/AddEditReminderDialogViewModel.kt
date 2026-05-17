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

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.ichi2.anki.ALL_DECKS_ID
import com.ichi2.anki.libanki.Consts
import com.ichi2.anki.libanki.DeckId
import timber.log.Timber

/**
 * Represents the state of an [AddEditReminderDialog]'s UI. Does not represent the [ReviewReminder] object itself.
 * For example, instead of storing the card trigger threshold as a [ReviewReminderCardTriggerThreshold], we store an Int, since that's
 * the input type the user is using to enter the threshold into the app. In other words, this class reflects the concrete
 * EditText fields in the dialog, not abstract backend data representations.
 */
class AddEditReminderDialogViewModel(
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    /**
     * The dialog mode of the [AddEditReminderDialog] which is using this ViewModel. Retrieved via arguments.
     */
    private val dialogMode =
        requireNotNull(
            savedStateHandle.get<AddEditReminderDialog.DialogMode>(AddEditReminderDialog.ARGS_DIALOG_MODE),
        ) { "dialogMode is required" }

    private val _time =
        MutableLiveData(
            when (dialogMode) {
                is AddEditReminderDialog.DialogMode.Add -> ReviewReminderTime.getCurrentTime()
                is AddEditReminderDialog.DialogMode.Edit -> dialogMode.reminderToBeEdited.time
            },
        )
    val time: LiveData<ReviewReminderTime> = _time

    /**
     * Here, we set an immediate default value for the deck selected based on the dialog mode.
     * However, it is possible that the deck with this deck ID does not currently exist in the collection
     * (ex. due to a deleted deck, changed collection folder, etc.). Since checking for this case requires
     * accessing the collection, we handle it in [AddEditReminderDialog.setInitialDeckSelection].
     */
    private val _deckSelected =
        MutableLiveData(
            when (dialogMode) {
                is AddEditReminderDialog.DialogMode.Add -> {
                    when (dialogMode.schedulerScope) {
                        is ReviewReminderScope.Global -> ALL_DECKS_ID
                        is ReviewReminderScope.DeckSpecific -> dialogMode.schedulerScope.did
                    }
                }
                is AddEditReminderDialog.DialogMode.Edit -> {
                    when (dialogMode.reminderToBeEdited.scope) {
                        is ReviewReminderScope.Global -> ALL_DECKS_ID
                        is ReviewReminderScope.DeckSpecific -> dialogMode.reminderToBeEdited.scope.did
                    }
                }
            },
        )

    /**
     * [ALL_DECKS_ID] is used to represent All Decks (i.e. [ReviewReminderScope.Global]) being selected.
     */
    val deckSelected: LiveData<DeckId> = _deckSelected

    private val _cardTriggerThreshold =
        MutableLiveData(
            when (dialogMode) {
                is AddEditReminderDialog.DialogMode.Add -> INITIAL_CARD_THRESHOLD
                is AddEditReminderDialog.DialogMode.Edit -> dialogMode.reminderToBeEdited.cardTriggerThreshold.threshold
            },
        )
    val cardTriggerThreshold: LiveData<Int> = _cardTriggerThreshold

    private val _onlyNotifyIfNoReviews =
        MutableLiveData(
            when (dialogMode) {
                is AddEditReminderDialog.DialogMode.Add -> INITIAL_ONLY_NOTIFY_IF_NO_REVIEWS
                is AddEditReminderDialog.DialogMode.Edit -> dialogMode.reminderToBeEdited.onlyNotifyIfNoReviews
            },
        )
    val onlyNotifyIfNoReviews: LiveData<Boolean> = _onlyNotifyIfNoReviews

    private val _advancedSettingsOpen = MutableLiveData(INITIAL_ADVANCED_SETTINGS_OPEN)
    val advancedSettingsOpen: LiveData<Boolean> = _advancedSettingsOpen

    fun setTime(time: ReviewReminderTime) {
        Timber.i("Updated time to %s", time)
        _time.value = time
    }

    fun setDeckSelected(deckId: DeckId) {
        Timber.i("Updated deck selected to %s", deckId)
        _deckSelected.value = deckId
    }

    fun setCardTriggerThreshold(threshold: Int) {
        Timber.i("Updated card trigger threshold to %s", threshold)
        _cardTriggerThreshold.value = threshold
    }

    fun toggleOnlyNotifyIfNoReviews() {
        Timber.i("Toggled onlyNotifyIfNoReviews from %s", _onlyNotifyIfNoReviews.value)
        _onlyNotifyIfNoReviews.value = !(_onlyNotifyIfNoReviews.value ?: false)
    }

    fun toggleAdvancedSettingsOpen() {
        Timber.i("Toggled advanced settings open from %s", _advancedSettingsOpen.value)
        _advancedSettingsOpen.value = !(_advancedSettingsOpen.value ?: false)
    }

    /**
     * Packages up the state of this ViewModel as a newly-created [ReviewReminder].
     * Used when the user clicks on the "OK" button in the dialog.
     */
    fun outputStateAsReminder(): ReviewReminder =
        ReviewReminder.createReviewReminder(
            time = time.value ?: ReviewReminderTime.getCurrentTime(),
            cardTriggerThreshold =
                ReviewReminderCardTriggerThreshold(
                    threshold = cardTriggerThreshold.value ?: INITIAL_CARD_THRESHOLD,
                ),
            scope =
                when (deckSelected.value) {
                    ALL_DECKS_ID -> ReviewReminderScope.Global
                    else ->
                        ReviewReminderScope.DeckSpecific(
                            did = deckSelected.value ?: Consts.DEFAULT_DECK_ID,
                        )
                },
            enabled =
                when (dialogMode) {
                    is AddEditReminderDialog.DialogMode.Add -> true
                    is AddEditReminderDialog.DialogMode.Edit -> dialogMode.reminderToBeEdited.enabled
                },
            onlyNotifyIfNoReviews = onlyNotifyIfNoReviews.value ?: INITIAL_ONLY_NOTIFY_IF_NO_REVIEWS,
        )

    companion object {
        /**
         * The default minimum card trigger threshold that is filled into the dialog when a new review
         * reminder is being created. Since this is set to one, the default behaviour is that users
         * will not get notified about a deck if there are no cards to review for that deck.
         * Users may choose to instead set it to zero, or any other non-negative integer value.
         * This is an Int because that is what the EditText's inputType is.
         */
        private const val INITIAL_CARD_THRESHOLD: Int = 1

        /**
         * The default value for whether a notification should only be fired if no reviews have been done today
         * for the corresponding deck / all decks. Since this is set to false, the default behaviour is that
         * notifications will always be sent, regardless of whether reviews have been done today.
         */
        private const val INITIAL_ONLY_NOTIFY_IF_NO_REVIEWS = false

        /**
         * Whether the advanced settings dropdown is initially open.
         * We start with it closed to avoid overwhelming the user.
         */
        private const val INITIAL_ADVANCED_SETTINGS_OPEN = false
    }
}

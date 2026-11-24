// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Eric Li <ericli3690@gmail.com>

package com.ichi2.anki.reviewreminders

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.ichi2.anki.common.ALL_DECKS_ID
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
            savedStateHandle.get<AddEditReminderDialog.DialogMode>(AddEditReminderDialog.ARG_DIALOG_MODE),
        ) { "dialogMode is required" }

    val time: LiveData<ReviewReminderTime>
        field =
        MutableLiveData(
            when (dialogMode) {
                is AddEditReminderDialog.DialogMode.Add -> ReviewReminderTime.getCurrentTime()
                is AddEditReminderDialog.DialogMode.Edit -> dialogMode.reminderToBeEdited.time
            },
        )

    /**
     * Here, we set an immediate default value for the deck selected based on the dialog mode.
     * However, it is possible that the deck with this deck ID does not currently exist in the collection
     * (ex. due to a deleted deck, changed collection folder, etc.). Since checking for this case requires
     * accessing the collection, we handle it in [AddEditReminderDialog.setInitialDeckSelection].
     *
     * [ALL_DECKS_ID] is used to represent All Decks (i.e. [ReviewReminderScope.Global]) being selected.
     */
    val deckSelected: LiveData<DeckId>
        field =
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

    val cardTriggerThreshold: LiveData<Int>
        field =
        MutableLiveData(
            when (dialogMode) {
                is AddEditReminderDialog.DialogMode.Add -> INITIAL_CARD_THRESHOLD
                is AddEditReminderDialog.DialogMode.Edit -> dialogMode.reminderToBeEdited.cardTriggerThreshold.threshold
            },
        )

    val countNew: LiveData<Boolean>
        field =
        MutableLiveData(
            when (dialogMode) {
                is AddEditReminderDialog.DialogMode.Add -> INITIAL_COUNT_NEW
                is AddEditReminderDialog.DialogMode.Edit -> dialogMode.reminderToBeEdited.thresholdFilter.countNew
            },
        )

    val countLrn: LiveData<Boolean>
        field =
        MutableLiveData(
            when (dialogMode) {
                is AddEditReminderDialog.DialogMode.Add -> INITIAL_COUNT_LRN
                is AddEditReminderDialog.DialogMode.Edit -> dialogMode.reminderToBeEdited.thresholdFilter.countLrn
            },
        )

    val countRev: LiveData<Boolean>
        field =
        MutableLiveData(
            when (dialogMode) {
                is AddEditReminderDialog.DialogMode.Add -> INITIAL_COUNT_REV
                is AddEditReminderDialog.DialogMode.Edit -> dialogMode.reminderToBeEdited.thresholdFilter.countRev
            },
        )

    val onlyNotifyIfNoReviews: LiveData<Boolean>
        field =
        MutableLiveData(
            when (dialogMode) {
                is AddEditReminderDialog.DialogMode.Add -> INITIAL_ONLY_NOTIFY_IF_NO_REVIEWS
                is AddEditReminderDialog.DialogMode.Edit -> dialogMode.reminderToBeEdited.onlyNotifyIfNoReviews
            },
        )

    val advancedSettingsOpen: LiveData<Boolean>
        field = MutableLiveData(INITIAL_ADVANCED_SETTINGS_OPEN)

    fun setTime(newTime: ReviewReminderTime) {
        Timber.i("Updated time to %s", newTime)
        time.value = newTime
    }

    fun setDeckSelected(deckId: DeckId) {
        Timber.i("Updated deck selected to %s", deckId)
        deckSelected.value = deckId
    }

    fun setCardTriggerThreshold(threshold: Int) {
        Timber.i("Updated card trigger threshold to %s", threshold)
        cardTriggerThreshold.value = threshold
    }

    fun toggleCountNew() {
        Timber.i("Toggled count new from %s", countNew.value)
        countNew.value = !(countNew.value ?: false)
    }

    fun toggleCountLrn() {
        Timber.i("Toggled count lrn from %s", countLrn.value)
        countLrn.value = !(countLrn.value ?: false)
    }

    fun toggleCountRev() {
        Timber.i("Toggled count rev from %s", countRev.value)
        countRev.value = !(countRev.value ?: false)
    }

    fun toggleOnlyNotifyIfNoReviews() {
        Timber.i("Toggled onlyNotifyIfNoReviews from %s", onlyNotifyIfNoReviews.value)
        onlyNotifyIfNoReviews.value = !(onlyNotifyIfNoReviews.value ?: false)
    }

    fun toggleAdvancedSettingsOpen() {
        Timber.i("Toggled advanced settings open from %s", advancedSettingsOpen.value)
        advancedSettingsOpen.value = !(advancedSettingsOpen.value ?: false)
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
            thresholdFilter =
                ReviewReminderThresholdFilter(
                    countNew = countNew.value ?: INITIAL_COUNT_NEW,
                    countLrn = countLrn.value ?: INITIAL_COUNT_LRN,
                    countRev = countRev.value ?: INITIAL_COUNT_REV,
                ),
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
         * The default setting for whether new cards are counted when checking the card trigger threshold.
         * This value, and the other default settings for whether certain kinds of cards are counted
         * when checking the card trigger threshold, are all set to true, as removing some card types
         * from card trigger threshold consideration is a form of advanced review reminder customization.
         */
        private const val INITIAL_COUNT_NEW = true

        /**
         * The default setting for whether cards in learning are counted when checking the card trigger threshold.
         * @see INITIAL_COUNT_NEW
         */
        private const val INITIAL_COUNT_LRN = true

        /**
         * The default setting for whether cards in review are counted when checking the card trigger threshold.
         * @see INITIAL_COUNT_NEW
         */
        private const val INITIAL_COUNT_REV = true

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

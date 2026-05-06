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

import android.app.Dialog
import android.content.res.Configuration
import android.os.Bundle
import android.os.Parcelable
import android.text.format.DateFormat
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.ichi2.anki.ALL_DECKS_ID
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.databinding.DialogAddEditReminderBinding
import com.ichi2.anki.dialogs.ConfirmationDialog
import com.ichi2.anki.dialogs.registerDeckSelectedHandler
import com.ichi2.anki.dialogs.startDeckSelection
import com.ichi2.anki.isDefaultDeckEmpty
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.libanki.Consts
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.model.SelectableDeck
import com.ichi2.anki.reviewreminders.AddEditReminderDialog.Companion.getInstance
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.utils.ext.getParcelableCompat
import com.ichi2.anki.utils.ext.showDialogFragment
import com.ichi2.utils.DisplayUtils.resizeWhenSoftInputShown
import com.ichi2.utils.Permissions
import com.ichi2.utils.customView
import com.ichi2.utils.negativeButton
import com.ichi2.utils.neutralButton
import com.ichi2.utils.positiveButton
import kotlinx.parcelize.Parcelize
import timber.log.Timber

class AddEditReminderDialog : DialogFragment() {
    /**
     * Possible states of this dialog.
     * In particular, whether this dialog will be used to add a new review reminder or edit an existing one.
     */
    @Parcelize
    sealed class DialogMode : Parcelable {
        /**
         * Adding a new review reminder. Requires the editing scope of [ScheduleRemindersFragment] as an argument so that the dialog can
         * pick a default deck to add to (or, if the scope is global, so that the dialog can
         * show that the review reminder will default to being a global reminder).
         */
        data class Add(
            val schedulerScope: ReviewReminderScope,
        ) : DialogMode()

        /**
         * Editing an existing review reminder. Requires the reminder being edited so that the
         * dialog's fields can be populated with its information.
         */
        data class Edit(
            val reminderToBeEdited: ReviewReminder,
        ) : DialogMode()
    }

    private val viewModel: AddEditReminderDialogViewModel by viewModels()

    private lateinit var binding: DialogAddEditReminderBinding

    /**
     * The mode of this dialog, retrieved from arguments and set by [getInstance].
     * @see DialogMode
     */
    private val dialogMode: DialogMode by lazy {
        requireNotNull(
            requireArguments().getParcelableCompat<DialogMode>(ARGS_DIALOG_MODE),
        ) {
            "Dialog mode cannot be null"
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        super.onCreateDialog(savedInstanceState)
        binding = DialogAddEditReminderBinding.inflate(layoutInflater)
        Timber.d("dialog mode: %s", dialogMode.toString())

        val dialogBuilder =
            AlertDialog.Builder(requireActivity()).apply {
                customView(binding.root)
                positiveButton(R.string.dialog_ok)
                neutralButton(R.string.dialog_cancel)

                if (dialogMode is DialogMode.Edit) {
                    negativeButton(R.string.dialog_positive_delete)
                }
            }
        val dialog = dialogBuilder.create()

        // We cannot create onClickListeners by directly using the lambda argument of positiveButton / negativeButton
        // because setting the onClickListener that way makes the dialog auto-dismiss upon the lambda completing.
        // We may need to abort submission or deletion. Hence we manually set the click listener here and only
        // dismiss conditionally from within the click listener methods (see onSubmit and onDelete).
        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            positiveButton.setOnClickListener { onSubmit() }
            negativeButton?.setOnClickListener { onDelete() } // delete button does not exist in Add mode, hence null check
        }

        registerDeckSelectedHandler(action = ::onDeckSelected)

        Timber.d("Setting up fields")
        setUpToolbar()
        setUpTimeButton()
        setInitialDeckSelection()
        setUpAdvancedDropdown()
        setUpCardThresholdInput()
        setUpOnlyNotifyIfNoReviewsCheckbox()

        dialog.window?.let { resizeWhenSoftInputShown(it) }
        return dialog
    }

    private fun setUpToolbar() {
        binding.addEditReminderToolbar.title =
            getString(
                when (dialogMode) {
                    is DialogMode.Add -> R.string.add_review_reminder
                    is DialogMode.Edit -> R.string.edit_review_reminder
                },
            )
    }

    private fun setUpTimeButton() {
        binding.addEditReminderTimeButton.setOnClickListener {
            Timber.i("Time button clicked")
            val time = viewModel.time.value ?: ReviewReminderTime.getCurrentTime()
            showTimePickerDialog(time.hour, time.minute)
        }
        viewModel.time.observe(this) { time ->
            binding.addEditReminderTimeButton.text = time.toFormattedString(requireContext())
        }
    }

    private fun setInitialDeckSelection() {
        binding.addEditReminderDeckName.setOnClickListener { startDeckSelection(allowAll = true, allowFiltered = true) }
        launchCatchingTask {
            Timber.d("Setting up deck name view")
            val (selectedDeckId, selectedDeckName) = getValidDeckSelection()
            Timber.d("Initial selection of deck %s(id=%d)", selectedDeckName, selectedDeckId)
            binding.addEditReminderDeckName.text = selectedDeckName
            viewModel.setDeckSelected(selectedDeckId)
        }
    }

    /**
     * Checks to see if the ViewModel's selected deck is valid and exists. If it does not, we select
     * a valid deck (either the default deck or "all decks", depending on whether the default deck
     * is present or not).
     *
     * @return a [Pair] with the [DeckId] and name of the selected valid deck
     */
    private suspend fun getValidDeckSelection(): Pair<DeckId, String> {
        suspend fun getFallbackSelection(): Pair<DeckId, String> =
            if (isDefaultDeckEmpty()) {
                Pair(ALL_DECKS_ID, getString(R.string.card_browser_all_decks))
            } else {
                Pair(Consts.DEFAULT_DECK_ID, withCol { decks.name(Consts.DEFAULT_DECK_ID) })
            }

        return when (val currentlySelectedDeckID = viewModel.deckSelected.value) {
            ALL_DECKS_ID -> Pair(ALL_DECKS_ID, getString(R.string.card_browser_all_decks))
            Consts.DEFAULT_DECK_ID -> getFallbackSelection()
            null -> getFallbackSelection()
            else -> {
                val doesDeckExist = withCol { decks.have(currentlySelectedDeckID) }
                if (doesDeckExist) {
                    Pair(currentlySelectedDeckID, withCol { decks.name(currentlySelectedDeckID) })
                } else {
                    getFallbackSelection()
                }
            }
        }
    }

    private fun setUpAdvancedDropdown() {
        binding.addEditReminderAdvancedDropdown.setOnClickListener {
            viewModel.toggleAdvancedSettingsOpen()
        }
        viewModel.advancedSettingsOpen.observe(this) { advancedSettingsOpen ->
            when (advancedSettingsOpen) {
                true -> {
                    binding.addEditReminderAdvancedContent.isVisible = true
                    binding.addEditReminderAdvancedDropdownIcon.setBackgroundResource(DROPDOWN_EXPANDED_CHEVRON)
                }
                false -> {
                    binding.addEditReminderAdvancedContent.isVisible = false
                    binding.addEditReminderAdvancedDropdownIcon.setBackgroundResource(DROPDOWN_COLLAPSED_CHEVRON)
                }
            }
        }
    }

    private fun setUpCardThresholdInput() {
        binding.addEditReminderCardThresholdInput.setText(viewModel.cardTriggerThreshold.value.toString())
        binding.addEditReminderCardThresholdInput.doOnTextChanged { text, _, _, _ ->
            val value: Int? = text.toString().toIntOrNull()
            binding.addEditReminderCardThresholdInputWrapper.error =
                when {
                    (value == null) -> "Please enter a whole number of cards"
                    (value < 0) -> "The threshold must be at least 0"
                    else -> null
                }
            viewModel.setCardTriggerThreshold(value ?: 0)
        }
    }

    private fun setUpOnlyNotifyIfNoReviewsCheckbox() {
        binding.addEditReminderOnlyNotifyIfNoReviewsSection.setOnClickListener {
            viewModel.toggleOnlyNotifyIfNoReviews()
        }
        binding.addEditReminderOnlyNotifyIfNoReviewsCheckbox.setOnClickListener {
            viewModel.toggleOnlyNotifyIfNoReviews()
        }
        viewModel.onlyNotifyIfNoReviews.observe(this) { onlyNotifyIfNoReviews ->
            binding.addEditReminderOnlyNotifyIfNoReviewsCheckbox.isChecked = onlyNotifyIfNoReviews
        }
    }

    /**
     * Show the time picker dialog for selecting a time with a given hour and minute.
     * Does not automatically dismiss the old dialog.
     */
    private fun showTimePickerDialog(
        hour: Int,
        minute: Int,
    ) {
        val dialog =
            MaterialTimePicker
                .Builder()
                .setTheme(R.style.TimePickerStyle)
                .setTimeFormat(if (DateFormat.is24HourFormat(activity)) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H)
                .setHour(hour)
                .setMinute(minute)
                .build()
        dialog.addOnPositiveButtonClickListener {
            viewModel.setTime(ReviewReminderTime(dialog.hour, dialog.minute))
        }
        dialog.show(parentFragmentManager, TIME_PICKER_TAG)
    }

    /**
     * For some reason, the TimePicker dialog does not automatically redraw itself properly when the device rotates.
     * Thus, if the TimePicker dialog is active, we manually show a new copy and then dismiss the old one.
     * We need to show the new one before dismissing the old one to ensure there is no annoying flicker.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val previousDialog = parentFragmentManager.findFragmentByTag(TIME_PICKER_TAG) as? MaterialTimePicker
        previousDialog?.let {
            showTimePickerDialog(it.hour, it.minute)
            it.dismiss()
        }
    }

    private fun onSubmit() {
        Timber.i("Submitted dialog")
        // Do nothing if numerical fields are invalid
        binding.addEditReminderCardThresholdInputWrapper.error?.let {
            binding.root.showSnackbar(R.string.something_wrong)
            return
        }

        val reminderToBeReturned = viewModel.outputStateAsReminder()
        Timber.d("Reminder to be returned: %s", reminderToBeReturned)
        setFragmentResult(
            REQUEST_ADD_EDIT_REMINDER,
            Bundle().apply {
                putParcelable(KEY_REMINDER_MODE, dialogMode)
                putParcelable(KEY_REMINDER_RESULT, reminderToBeReturned)
            },
        )

        // Request notification permissions from the user if they have not been requested due to review reminders ever before
        if (!Prefs.reminderNotifsRequestShown) {
            Permissions.showNotificationsPermissionBottomSheetIfNeeded(requireActivity(), parentFragmentManager) {
                Prefs.reminderNotifsRequestShown = true
            }
        }

        dismiss()
    }

    private fun onDelete() {
        Timber.i("Selected delete reminder button")

        val confirmationDialog = ConfirmationDialog()
        confirmationDialog.setArgs(
            "Delete this reminder?",
            "This action cannot be undone.",
        )
        confirmationDialog.setConfirm {
            setFragmentResult(
                REQUEST_ADD_EDIT_REMINDER,
                Bundle().apply {
                    // dialogMode should always be DialogMode.Edit in this case since the delete button only exists in Edit mode
                    putParcelable(KEY_REMINDER_MODE, dialogMode)
                    putParcelable(KEY_REMINDER_RESULT, null)
                },
            )
            dismiss()
        }

        showDialogFragment(confirmationDialog)
    }

    private fun onDeckSelected(deck: SelectableDeck?) {
        Timber.d("Deck selected in deck spinner: %s", deck)
        val selectedDeckId: DeckId =
            when (deck) {
                is SelectableDeck.Deck -> deck.deckId
                is SelectableDeck.AllDecks -> ALL_DECKS_ID
                null -> return
            }
        viewModel.setDeckSelected(selectedDeckId)
        binding.addEditReminderDeckName.text = deck.getDisplayName(requireContext())
    }

    companion object {
        /**
         * Icon that shows next to the advanced settings section when the dropdown is open.
         */
        private val DROPDOWN_EXPANDED_CHEVRON = R.drawable.ic_expand_more_black_24dp_xml

        /**
         * Icon that shows next to the advanced settings section when the dropdown is closed.
         */
        private val DROPDOWN_COLLAPSED_CHEVRON = R.drawable.ic_baseline_chevron_right_24

        /**
         * Arguments key for the dialog mode to open this dialog in.
         * Public so that [AddEditReminderDialogViewModel] can also access it to populate its initial state.
         *
         * @see DialogMode
         */
        const val ARGS_DIALOG_MODE = "args_dialog_mode"

        /**
         * Fragment result key for receiving the result of a recently closed [AddEditReminderDialog].
         */
        private const val REQUEST_ADD_EDIT_REMINDER = "request_add_edit"

        /**
         * Fragment result bundle key for the [DialogMode] of a recently closed [AddEditReminderDialog].
         */
        private const val KEY_REMINDER_MODE = "key_reminder_mode"

        /**
         * Fragment result bundle key for the [ReviewReminder] result of a recently closed [AddEditReminderDialog].
         */
        private const val KEY_REMINDER_RESULT = "key_reminder_result"

        /**
         * Unique fragment tag for the Material TimePicker shown for setting the time of a review reminder.
         */
        private const val TIME_PICKER_TAG = "REMINDER_TIME_PICKER_DIALOG"

        /**
         * Register a fragment result listener to listen for results from a recently closed [AddEditReminderDialog].
         * If the reminder has been deleted, the [ReviewReminder] argument to the callback will be null.
         *
         * @param action The callback to be executed when a result is received
         */
        fun Fragment.registerAddEditReminderHandler(
            action: (newOrModifiedReminder: ReviewReminder?, modeOfFinishedDialog: DialogMode) -> Unit,
        ) {
            setFragmentResultListener(REQUEST_ADD_EDIT_REMINDER) { _, bundle ->
                Timber.i("Received fragment result from add/edit dialog")
                val modeOfFinishedDialog =
                    bundle.getParcelableCompat<DialogMode>(
                        KEY_REMINDER_MODE,
                    ) ?: return@setFragmentResultListener
                val newOrModifiedReminder = bundle.getParcelableCompat<ReviewReminder>(KEY_REMINDER_RESULT)
                action(newOrModifiedReminder, modeOfFinishedDialog)
            }
        }

        /**
         * Creates a new instance of this dialog with the given dialog mode.
         */
        fun getInstance(dialogMode: DialogMode): AddEditReminderDialog =
            AddEditReminderDialog().apply {
                arguments =
                    Bundle().apply {
                        putParcelable(ARGS_DIALOG_MODE, dialogMode)
                    }
            }
    }
}

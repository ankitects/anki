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
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.core.os.BundleCompat
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.ichi2.anki.ALL_DECKS_ID
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.dialogs.ConfirmationDialog
import com.ichi2.anki.isDefaultDeckEmpty
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.libanki.Consts
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.model.SelectableDeck
import com.ichi2.anki.reviewreminders.AddEditReminderDialog.Companion.getInstance
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.startDeckSelection
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

    private lateinit var contentView: View

    /**
     * The mode of this dialog, retrieved from arguments and set by [getInstance].
     * @see DialogMode
     */
    private val dialogMode: DialogMode by lazy {
        requireNotNull(
            BundleCompat.getParcelable(requireArguments(), DIALOG_MODE_ARGUMENTS_KEY, DialogMode::class.java),
        ) {
            "Dialog mode cannot be null"
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        super.onCreateDialog(savedInstanceState)
        contentView = layoutInflater.inflate(R.layout.dialog_add_edit_reminder, null)
        Timber.d("dialog mode: %s", dialogMode.toString())

        val dialogBuilder =
            AlertDialog.Builder(requireActivity()).apply {
                customView(contentView)
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

        Timber.d("Setting up fields")
        setUpToolbar()
        setUpTimeButton()
        setInitialDeckSelection()
        setUpAdvancedDropdown()
        setUpCardThresholdInput()
        setUpOnlyNotifyIfNoReviewsCheckbox()

        // For getting the result of the deck selection sub-dialog from ScheduleRemindersFragment
        // See ScheduleRemindersFragment.onDeckSelected for more information
        setFragmentResultListener(ScheduleRemindersFragment.DECK_SELECTION_RESULT_REQUEST_KEY) { _, bundle ->
            val selectedDeck =
                BundleCompat.getParcelable(
                    bundle,
                    ScheduleRemindersFragment.DECK_SELECTION_RESULT_REQUEST_KEY,
                    SelectableDeck::class.java,
                )
            Timber.d("Received result from deck selection sub-dialog: %s", selectedDeck)
            val selectedDeckId: DeckId =
                when (selectedDeck) {
                    is SelectableDeck.Deck -> selectedDeck.deckId
                    is SelectableDeck.AllDecks -> ALL_DECKS_ID
                    else -> Consts.DEFAULT_DECK_ID
                }
            viewModel.setDeckSelected(selectedDeckId)
            this.dialog?.findViewById<TextView>(R.id.add_edit_reminder_deck_name)?.text =
                selectedDeck?.getDisplayName(requireContext())
        }

        dialog.window?.let { resizeWhenSoftInputShown(it) }
        return dialog
    }

    private fun setUpToolbar() {
        val toolbar = contentView.findViewById<Toolbar>(R.id.add_edit_reminder_toolbar)
        toolbar.title =
            getString(
                when (dialogMode) {
                    is DialogMode.Add -> R.string.add_review_reminder
                    is DialogMode.Edit -> R.string.edit_review_reminder
                },
            )
    }

    private fun setUpTimeButton() {
        val timeButton = contentView.findViewById<MaterialButton>(R.id.add_edit_reminder_time_button)
        timeButton.setOnClickListener {
            Timber.i("Time button clicked")
            val time = viewModel.time.value ?: ReviewReminderTime.getCurrentTime()
            showTimePickerDialog(time.hour, time.minute)
        }
        viewModel.time.observe(this) { time ->
            timeButton.text = time.toFormattedString(requireContext())
        }
    }

    private fun setInitialDeckSelection() {
        val deckName = contentView.findViewById<TextView>(R.id.add_edit_reminder_deck_name)
        deckName.setOnClickListener { startDeckSelection(all = true, filtered = true) }
        launchCatchingTask {
            Timber.d("Setting up deck name view")
            val (selectedDeckId, selectedDeckName) = getValidDeckSelection()
            Timber.d("Initial selection of deck %s(id=%d)", selectedDeckName, selectedDeckId)
            deckName.text = selectedDeckName
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
        val advancedDropdown = contentView.findViewById<LinearLayout>(R.id.add_edit_reminder_advanced_dropdown)
        val advancedDropdownIcon = contentView.findViewById<ImageView>(R.id.add_edit_reminder_advanced_dropdown_icon)
        val advancedContent = contentView.findViewById<LinearLayout>(R.id.add_edit_reminder_advanced_content)

        advancedDropdown.setOnClickListener {
            viewModel.toggleAdvancedSettingsOpen()
        }
        viewModel.advancedSettingsOpen.observe(this) { advancedSettingsOpen ->
            when (advancedSettingsOpen) {
                true -> {
                    advancedContent.isVisible = true
                    advancedDropdownIcon.setBackgroundResource(DROPDOWN_EXPANDED_CHEVRON)
                }
                false -> {
                    advancedContent.isVisible = false
                    advancedDropdownIcon.setBackgroundResource(DROPDOWN_COLLAPSED_CHEVRON)
                }
            }
        }
    }

    private fun setUpCardThresholdInput() {
        val cardThresholdInputWrapper = contentView.findViewById<TextInputLayout>(R.id.add_edit_reminder_card_threshold_input_wrapper)
        val cardThresholdInput = contentView.findViewById<EditText>(R.id.add_edit_reminder_card_threshold_input)
        cardThresholdInput.setText(viewModel.cardTriggerThreshold.value.toString())
        cardThresholdInput.doOnTextChanged { text, _, _, _ ->
            val value: Int? = text.toString().toIntOrNull()
            cardThresholdInputWrapper.error =
                when {
                    (value == null) -> "Please enter a whole number of cards"
                    (value < 0) -> "The threshold must be at least 0"
                    else -> null
                }
            viewModel.setCardTriggerThreshold(value ?: 0)
        }
    }

    private fun setUpOnlyNotifyIfNoReviewsCheckbox() {
        val contentSection = contentView.findViewById<LinearLayout>(R.id.add_edit_reminder_only_notify_if_no_reviews_section)
        val checkbox = contentView.findViewById<MaterialCheckBox>(R.id.add_edit_reminder_only_notify_if_no_reviews_checkbox)
        contentSection.setOnClickListener {
            viewModel.toggleOnlyNotifyIfNoReviews()
        }
        checkbox.setOnClickListener {
            viewModel.toggleOnlyNotifyIfNoReviews()
        }
        viewModel.onlyNotifyIfNoReviews.observe(this) { onlyNotifyIfNoReviews ->
            checkbox.isChecked = onlyNotifyIfNoReviews
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
        val cardThresholdInputWrapper = contentView.findViewById<TextInputLayout>(R.id.add_edit_reminder_card_threshold_input_wrapper)
        cardThresholdInputWrapper.error?.let {
            contentView.showSnackbar(R.string.something_wrong)
            return
        }

        val reminderToBeReturned = viewModel.outputStateAsReminder()
        Timber.d("Reminder to be returned: %s", reminderToBeReturned)
        setFragmentResult(
            ScheduleRemindersFragment.ADD_EDIT_DIALOG_RESULT_REQUEST_KEY,
            Bundle().apply {
                putParcelable(ScheduleRemindersFragment.ADD_EDIT_DIALOG_RESULT_REQUEST_KEY, reminderToBeReturned)
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
                ScheduleRemindersFragment.ADD_EDIT_DIALOG_RESULT_REQUEST_KEY,
                Bundle().apply {
                    putParcelable(ScheduleRemindersFragment.ADD_EDIT_DIALOG_RESULT_REQUEST_KEY, null)
                },
            )
            dismiss()
        }

        showDialogFragment(confirmationDialog)
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
        const val DIALOG_MODE_ARGUMENTS_KEY = "dialog_mode"

        /**
         * Unique fragment tag for the Material TimePicker shown for setting the time of a review reminder.
         */
        private const val TIME_PICKER_TAG = "REMINDER_TIME_PICKER_DIALOG"

        /**
         * Creates a new instance of this dialog with the given dialog mode.
         */
        fun getInstance(dialogMode: DialogMode): AddEditReminderDialog =
            AddEditReminderDialog().apply {
                arguments =
                    Bundle().apply {
                        putParcelable(DIALOG_MODE_ARGUMENTS_KEY, dialogMode)
                    }
            }
    }
}

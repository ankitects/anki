// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Eric Li <ericli3690@gmail.com>

package com.ichi2.anki.reviewreminders

import android.app.Dialog
import android.os.Bundle
import android.os.Parcelable
import android.text.format.DateFormat
import androidx.annotation.AttrRes
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import com.google.android.material.chip.Chip
import com.google.android.material.color.MaterialColors
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.common.ALL_DECKS_ID
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
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.anki.utils.ext.getParcelableCompat
import com.ichi2.anki.utils.ext.requireParcelable
import com.ichi2.anki.utils.ext.showDialogFragment
import com.ichi2.anki.utils.showDialogFragment
import com.ichi2.utils.DisplayUtils.setDialogKeyboardResize
import com.ichi2.utils.Permissions
import com.ichi2.utils.customView
import com.ichi2.utils.negativeButton
import com.ichi2.utils.neutralButton
import com.ichi2.utils.outlineWindow
import com.ichi2.utils.positiveButton
import kotlinx.parcelize.Parcelize
import timber.log.Timber
import com.ichi2.anki.common.android.R as CommonR

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

    @VisibleForTesting
    internal lateinit var binding: DialogAddEditReminderBinding
        private set

    /**
     * The mode of this dialog, retrieved from arguments and set by [getInstance].
     * @see DialogMode
     */
    private val dialogMode: DialogMode by lazy {
        requireArguments().requireParcelable(ARG_DIALOG_MODE)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        super.onCreateDialog(savedInstanceState)
        binding = DialogAddEditReminderBinding.inflate(layoutInflater)
        Timber.d("dialog mode: %s", dialogMode.toString())

        val dialogBuilder =
            AlertDialog.Builder(requireActivity()).apply {
                customView(binding.root)
                positiveButton(CommonString.dialog_ok)
                neutralButton(CommonString.dialog_cancel)

                if (dialogMode is DialogMode.Edit) {
                    negativeButton(CommonString.dialog_positive_delete)
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
        setUpCountChips()
        setUpOnlyNotifyIfNoReviewsCheckbox()

        setDialogKeyboardResize(dialog)
        return dialog
    }

    /**
     * MaterialTimePicker loses its callbacks when it is redrawn upon device rotation due to a known issue:
     *
     * https://github.com/material-components/material-components-android/issues/4310
     *
     * Whenever this dialog is hosted inside an activity that does not have `android:configChanges="orientation"`
     * set in the manifest (ex. PreferencesActivity), the activity will be destroyed and recreated upon rotation,
     * causing the dialog to be redrawn and lose its callbacks. Hence, we must reattach them via onResume.
     *
     * @see onConfigurationChanged
     */
    override fun onResume() {
        super.onResume()
        val timePickerDialog = parentFragmentManager.findFragmentByTag(TIME_PICKER_TAG) as? MaterialTimePicker
        timePickerDialog?.let {
            it.outlineWindow()
            it.clearOnPositiveButtonClickListeners()
            it.addOnPositiveButtonClickListener {
                viewModel.setTime(ReviewReminderTime(timePickerDialog.hour, timePickerDialog.minute))
            }
        }
    }

    private fun setUpToolbar() {
        binding.addEditReminderToolbar.title =
            getString(
                when (dialogMode) {
                    is DialogMode.Add -> CommonString.add_review_reminder
                    is DialogMode.Edit -> CommonString.edit_review_reminder
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
                Pair(ALL_DECKS_ID, TR.sentenceCase.allDecks)
            } else {
                Pair(Consts.DEFAULT_DECK_ID, withCol { decks.name(Consts.DEFAULT_DECK_ID) })
            }

        return when (val currentlySelectedDeckID = viewModel.deckSelected.value) {
            ALL_DECKS_ID -> Pair(ALL_DECKS_ID, TR.sentenceCase.allDecks)
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
                    (value == null) -> getString(CommonString.add_edit_reminder_card_threshold_error_not_a_number)
                    (value < 0) -> getString(CommonString.add_edit_reminder_card_threshold_error_negative)
                    else -> null
                }
            viewModel.setCardTriggerThreshold(value ?: 0)
        }
    }

    /**
     * Convenience data class for setting up the chips for whether to count new, learning, and review cards
     * when considering the card trigger threshold.
     * @see setUpCountChips
     */
    private data class CountViewsAndActions(
        val chip: Chip,
        val label: String,
        @AttrRes val colorAttr: Int,
        val actionOnClick: () -> Unit,
        val state: LiveData<Boolean>,
    )

    private fun setUpCountChips() {
        val countViewsAndActionsItems =
            listOf(
                CountViewsAndActions(
                    chip = binding.addEditReminderCountNewChip,
                    label = TR.actionsNew(),
                    colorAttr = CommonR.attr.newCountColor,
                    actionOnClick = viewModel::toggleCountNew,
                    state = viewModel.countNew,
                ),
                CountViewsAndActions(
                    chip = binding.addEditReminderCountLrnChip,
                    label = TR.schedulingLearning(),
                    colorAttr = CommonR.attr.learnCountColor,
                    actionOnClick = viewModel::toggleCountLrn,
                    state = viewModel.countLrn,
                ),
                CountViewsAndActions(
                    chip = binding.addEditReminderCountRevChip,
                    label = TR.studyingToReview(),
                    colorAttr = CommonR.attr.reviewCountColor,
                    actionOnClick = viewModel::toggleCountRev,
                    state = viewModel.countRev,
                ),
            )

        countViewsAndActionsItems.forEach { item ->
            item.chip.text = item.label
            item.chip.setTextColor(MaterialColors.getColor(requireContext(), item.colorAttr, 0))
            item.chip.setOnClickListener { item.actionOnClick() }
            item.state.observe(this) { value ->
                item.chip.isChecked = value
            }
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
     *
     * We must use `dialog.show` here (thus technically causing the MaterialTimePicker dialog to be rendered
     * on top of the AddEditReminderDialog, rather than replacing it as is standard) and cannot use the
     * standard [showDialogFragment] method. This is because there are certain actions that need to be performed
     * if the screen rotates while the time picker dialog is open. See [onResume]. The standard methods remove the old
     * dialog before showing the new one, meaning on-rotation actions would not be possible without layering the
     * MaterialTimePicker dialog on top of the AddEditReminderDialog.
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
        dialog.outlineWindow()
        dialog.show(parentFragmentManager, TIME_PICKER_TAG)
    }

    /** Outlines the picker's window in E-ink once its dialog exists. See [outlineWindow]. */
    private fun MaterialTimePicker.outlineWindow() {
        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) = requireDialog().outlineWindow()
            },
        )
    }

    private fun onSubmit() {
        Timber.i("Submitted dialog")
        // Do nothing if numerical fields are invalid
        binding.addEditReminderCardThresholdInputWrapper.error?.let {
            binding.root.showSnackbar(CommonString.something_wrong)
            return
        }

        val reminderToBeReturned = viewModel.outputStateAsReminder()
        Timber.d("Reminder to be returned: %s", reminderToBeReturned)
        setFragmentResult(
            REQUEST_ADD_EDIT_REMINDER,
            Bundle().apply {
                putParcelable(RESULT_MODE, dialogMode)
                putParcelable(RESULT_REMINDER, reminderToBeReturned)
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
            title = getString(CommonString.add_edit_reminder_delete_confirmation_title),
            message = getString(CommonString.add_edit_reminder_delete_confirmation_message),
            positiveButtonText = getString(CommonString.dialog_positive_delete),
        )
        confirmationDialog.setConfirm {
            setFragmentResult(
                REQUEST_ADD_EDIT_REMINDER,
                Bundle().apply {
                    // dialogMode should always be DialogMode.Edit in this case since the delete button only exists in Edit mode
                    putParcelable(RESULT_MODE, dialogMode)
                    putParcelable(RESULT_REMINDER, null)
                },
            )
            dismiss()
        }

        parentFragmentManager.showDialogFragment(confirmationDialog)
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
        private val DROPDOWN_COLLAPSED_CHEVRON = CommonR.drawable.ic_baseline_chevron_right_24

        /**
         * Arguments key for the dialog mode to open this dialog in.
         * Public so that [AddEditReminderDialogViewModel] can also access it to populate its initial state.
         *
         * @see DialogMode
         */
        const val ARG_DIALOG_MODE = "arg_dialog_mode"

        /**
         * Fragment result key for receiving the result of a recently closed [AddEditReminderDialog].
         */
        private const val REQUEST_ADD_EDIT_REMINDER = "request_add_edit"

        /**
         * Fragment result bundle key for the [DialogMode] of a recently closed [AddEditReminderDialog].
         */
        private const val RESULT_MODE = "result_mode"

        /**
         * Fragment result bundle key for the [ReviewReminder] result of a recently closed [AddEditReminderDialog].
         */
        private const val RESULT_REMINDER = "result_reminder"

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
            childFragmentManager.setFragmentResultListener(REQUEST_ADD_EDIT_REMINDER, viewLifecycleOwner) { _, bundle ->
                Timber.i("Received fragment result from add/edit dialog")
                val modeOfFinishedDialog =
                    bundle.getParcelableCompat<DialogMode>(
                        RESULT_MODE,
                    ) ?: return@setFragmentResultListener
                val newOrModifiedReminder = bundle.getParcelableCompat<ReviewReminder>(RESULT_REMINDER)
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
                        putParcelable(ARG_DIALOG_MODE, dialogMode)
                    }
            }
    }
}

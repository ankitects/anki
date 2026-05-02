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
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.BundleCompat
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commit
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.canUserAccessDeck
import com.ichi2.anki.databinding.FragmentScheduleRemindersBinding
import com.ichi2.anki.dialogs.DeckSelectionDialog
import com.ichi2.anki.dialogs.registerDeckSelectedHandler
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.model.SelectableDeck
import com.ichi2.anki.services.AlarmManagerService
import com.ichi2.anki.snackbar.BaseSnackbarBuilderProvider
import com.ichi2.anki.snackbar.SnackbarBuilder
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.utils.ext.showDialogFragment
import com.ichi2.anki.withProgress
import dev.androidbroadcast.vbpd.viewBinding
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Fragment for creating, viewing, editing, and deleting review reminders.
 */
class ScheduleReminders :
    Fragment(R.layout.fragment_schedule_reminders),
    BaseSnackbarBuilderProvider {
    /**
     * Whether this fragment has been opened to edit all review reminders or just a specific deck's reminders.
     * @see ReviewReminderScope
     */
    private val scheduleRemindersScope: ReviewReminderScope by lazy {
        BundleCompat.getParcelable(
            requireArguments(),
            EXTRAS_SCOPE_KEY,
            ReviewReminderScope::class.java,
        ) ?: ReviewReminderScope.Global
    }

    private val binding by viewBinding(FragmentScheduleRemindersBinding::bind)

    private val troubleshootingViewModel: ReminderTroubleshootingViewModel by activityViewModels {
        reminderTroubleshootingViewModelFactory(requireContext())
    }

    private lateinit var adapter: ScheduleRemindersAdapter

    private var troubleshootingSnackbar: Snackbar? = null

    override val baseSnackbarBuilder: SnackbarBuilder = {
        anchorView = binding.floatingActionButtonAdd
    }

    /**
     * The reminders currently being displayed in the UI. To make changes to this list show up on screen,
     * use [triggerUIUpdate]. Note that editing this map does not also automatically write to the database.
     * Writing to the database must be done separately.
     */
    private lateinit var reminders: ReviewReminderGroup

    /**
     * Retrieving deck names for a given deck ID in [retrieveDeckNameFromID] requires a call to the collection.
     * However, most reminders in the RecyclerView will often be from the same deck (and are guaranteed to be if
     * this fragment is opened in [ReviewReminderScope.DeckSpecific] mode). Hence, we cache deck names.
     */
    private val cachedDeckNames: HashMap<DeckId, String> = hashMapOf()

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        // Set up toolbar
        reloadToolbarText()
        (requireActivity() as AppCompatActivity).setSupportActionBar(binding.toolbar)
        requireActivity().addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(
                    menu: Menu,
                    menuInflater: MenuInflater,
                ) {
                    menuInflater.inflate(R.menu.schedule_reminders, menu)
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean =
                    when (menuItem.itemId) {
                        R.id.action_troubleshoot -> {
                            openTroubleshootingScreen()
                            true
                        }
                        else -> false
                    }
            },
            viewLifecycleOwner,
        )
        // Set up add button
        binding.floatingActionButtonAdd.setOnClickListener { addReminder() }

        // Troubleshoot snackbar: shown persistently when checks find a warning/error.
        // Tapping "Fix" opens the full troubleshooting screen.
        lifecycleScope.launch {
            troubleshootingViewModel.state
                .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
                .collect { state ->
                    val message =
                        when (state.summaryStatus) {
                            SummaryStatus.Ok, SummaryStatus.Warning -> {
                                troubleshootingSnackbar?.dismiss()
                                troubleshootingSnackbar = null
                                return@collect
                            }
                            SummaryStatus.Error -> "Reminders are unavailable"
                        }
                    if (troubleshootingSnackbar?.isShown == true) {
                        troubleshootingSnackbar?.setText(message)
                        return@collect
                    }
                    troubleshootingSnackbar =
                        showSnackbar(text = message, duration = Snackbar.LENGTH_INDEFINITE) {
                            setAction("Fix") { openTroubleshootingScreen() }
                        }
                }
        }

        // Set up recycler view
        val layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.layoutManager = layoutManager
        binding.recyclerView.addItemDecoration(
            DividerItemDecoration(
                requireContext(),
                layoutManager.orientation,
            ),
        )

        // Set up adapter, pass functionality to it
        adapter =
            ScheduleRemindersAdapter(
                ::retrieveDeckNameFromID,
                ::retrieveCanUserAccessDeck,
                ::toggleReminderEnabled,
                ::editReminder,
            )
        binding.recyclerView.adapter = adapter

        // Retrieve reminders based on the editing scope
        launchCatchingTask { loadDatabaseRemindersIntoUI() }

        registerDeckSelectedHandler(action = ::onDeckSelected)
        // If the user creates or edits a review reminder, the dialog for doing so opens
        // Once their changes are complete, the dialog closes and this fragment is reloaded
        // Hence, we check for any fragment results here and update the database accordingly
        setFragmentResultListener(ADD_EDIT_DIALOG_RESULT_REQUEST_KEY) { _, bundle ->
            val modeOfFinishedDialog =
                BundleCompat.getParcelable(
                    requireArguments(),
                    ACTIVE_DIALOG_MODE_ARGUMENTS_KEY,
                    AddEditReminderDialog.DialogMode::class.java,
                ) ?: return@setFragmentResultListener
            val newOrModifiedReminder =
                BundleCompat.getParcelable(
                    bundle,
                    ADD_EDIT_DIALOG_RESULT_REQUEST_KEY,
                    ReviewReminder::class.java,
                )
            Timber.d("Dialog result received with recent dialog mode: %s", modeOfFinishedDialog)
            handleAddEditDialogResult(newOrModifiedReminder, modeOfFinishedDialog)
        }
    }

    private fun reloadToolbarText() {
        Timber.d("Reloading toolbar text")
        binding.toolbar.title = getString(R.string.schedule_reminders_do_not_translate)
        when (val scope = scheduleRemindersScope) {
            is ReviewReminderScope.Global -> {}
            is ReviewReminderScope.DeckSpecific ->
                launchCatchingTask {
                    binding.toolbar.subtitle = scope.getDeckName()
                }
        }
    }

    /**
     * Fetch all reminders from the database and put them into the RecyclerView.
     */
    private suspend fun loadDatabaseRemindersIntoUI() {
        Timber.d("Loading review reminders from database")
        reminders =
            catchDatabaseExceptions {
                when (val scope = scheduleRemindersScope) {
                    is ReviewReminderScope.Global -> {
                        ReviewRemindersDatabase.getAllAppWideReminders() + ReviewRemindersDatabase.getAllDeckSpecificReminders()
                    }
                    is ReviewReminderScope.DeckSpecific -> ReviewRemindersDatabase.getRemindersForDeck(scope.did)
                }
            } ?: ReviewReminderGroup()
        triggerUIUpdate()
        Timber.d("Database review reminders successfully loaded")
    }

    /**
     * When a [AddEditReminderDialog] instance finishes, we handle the result of the dialog fragment via this method.
     */
    private fun handleAddEditDialogResult(
        newOrModifiedReminder: ReviewReminder?,
        modeOfFinishedDialog: AddEditReminderDialog.DialogMode,
    ) {
        Timber.d("Handling add/edit dialog result: mode=%s reminder=%s", modeOfFinishedDialog, newOrModifiedReminder)
        updateDatabaseForAddEditDialog(newOrModifiedReminder, modeOfFinishedDialog)
        updateUIForAddEditDialog(newOrModifiedReminder, modeOfFinishedDialog)
        updateAlarmsForAddEditDialog(newOrModifiedReminder, modeOfFinishedDialog)
        // Feedback
        showSnackbar(
            when (modeOfFinishedDialog) {
                is AddEditReminderDialog.DialogMode.Add -> "Successfully added new review reminder"
                is AddEditReminderDialog.DialogMode.Edit -> {
                    when (newOrModifiedReminder) {
                        null -> "Successfully deleted review reminder"
                        else -> "Successfully edited review reminder"
                    }
                }
            },
        )
    }

    /**
     * Write the new or modified reminder to the database.
     * @see handleAddEditDialogResult
     */
    private fun updateDatabaseForAddEditDialog(
        newOrModifiedReminder: ReviewReminder?,
        modeOfFinishedDialog: AddEditReminderDialog.DialogMode,
    ) {
        launchCatchingTask {
            catchDatabaseExceptions {
                if (modeOfFinishedDialog is AddEditReminderDialog.DialogMode.Edit) {
                    // Delete the existing reminder if we're in edit mode
                    // This action must be separated from writing the modified reminder because the user may have updated the reminder's deck,
                    // meaning we need to delete the old reminder in the old deck, then add a new reminder to the new deck
                    val reminderToDelete = modeOfFinishedDialog.reminderToBeEdited
                    Timber.d("Deleting old reminder from database")
                    when (reminderToDelete.scope) {
                        is ReviewReminderScope.Global -> ReviewRemindersDatabase.editAllAppWideReminders(deleteReminder(reminderToDelete))
                        is ReviewReminderScope.DeckSpecific ->
                            ReviewRemindersDatabase.editRemindersForDeck(
                                reminderToDelete.scope.did,
                                deleteReminder(reminderToDelete),
                            )
                    }
                }
                newOrModifiedReminder?.let { reminder ->
                    Timber.d("Writing new or modified reminder to database")
                    when (reminder.scope) {
                        is ReviewReminderScope.Global -> ReviewRemindersDatabase.editAllAppWideReminders(upsertReminder(reminder))
                        is ReviewReminderScope.DeckSpecific ->
                            ReviewRemindersDatabase.editRemindersForDeck(
                                reminder.scope.did,
                                upsertReminder(reminder),
                            )
                    }
                }
            }
        }
    }

    /**
     * Update the RecyclerView with the new or modified reminder.
     * @see handleAddEditDialogResult
     */
    private fun updateUIForAddEditDialog(
        newOrModifiedReminder: ReviewReminder?,
        modeOfFinishedDialog: AddEditReminderDialog.DialogMode,
    ) {
        if (modeOfFinishedDialog is AddEditReminderDialog.DialogMode.Edit) {
            Timber.d("Deleting old reminder from UI")
            reminders.remove(modeOfFinishedDialog.reminderToBeEdited.id)
        }
        newOrModifiedReminder?.let {
            if (scheduleRemindersScope == ReviewReminderScope.Global || scheduleRemindersScope == it.scope) {
                Timber.d("Adding new reminder to UI")
                reminders[it.id] = it
            }
        }
        triggerUIUpdate()
    }

    /**
     * Update the AlarmManager notifications for the new or modified reminder.
     * @see handleAddEditDialogResult
     */
    private fun updateAlarmsForAddEditDialog(
        newOrModifiedReminder: ReviewReminder?,
        modeOfFinishedDialog: AddEditReminderDialog.DialogMode,
    ) {
        if (modeOfFinishedDialog is AddEditReminderDialog.DialogMode.Edit) {
            AlarmManagerService.unscheduleReviewReminderNotifications(
                requireContext(),
                modeOfFinishedDialog.reminderToBeEdited,
            )
        }
        newOrModifiedReminder?.let {
            if (it.enabled) {
                AlarmManagerService.scheduleReviewReminderNotification(
                    requireContext(),
                    it,
                )
            }
        }
    }

    /**
     * Retrieves a deck name from the collection for a given deck ID and passes it to the provided callback.
     * Used by the [ScheduleRemindersAdapter] because it cannot access the collection directly.
     */
    private fun retrieveDeckNameFromID(
        did: DeckId,
        callback: (deckName: String) -> Unit,
    ) {
        launchCatchingTask {
            val deckName = cachedDeckNames.getOrPut(did) { withCol { decks.name(did) } }
            callback(deckName)
        }
    }

    /**
     * Retrieves whether the user can access the deck with the given ID and passes the result to the provided callback.
     * Basically, checks whether the deck exists, with some exceptions: see [canUserAccessDeck].
     * Used by the [ScheduleRemindersAdapter] because it cannot access the collection directly.
     */
    private fun retrieveCanUserAccessDeck(
        did: DeckId,
        callback: (isDeckAccessible: Boolean) -> Unit,
    ) {
        launchCatchingTask {
            val isDeckAccessible = canUserAccessDeck(did)
            Timber.d("Checked for whether deck with id %s can be accessed: %s", did, isDeckAccessible)
            callback(isDeckAccessible)
        }
    }

    /**
     * Toggles whether a review reminder is enabled, i.e. whether its notifications will fire.
     * Saves this information immediately to the database and then updates the UI.
     */
    private fun toggleReminderEnabled(
        id: ReviewReminderId,
        scope: ReviewReminderScope,
    ) {
        Timber.d("Toggling reminder enabled state: %s", id)
        val reminder = reminders[id] ?: return
        val newState = !reminder.enabled

        // Update database
        launchCatchingTask {
            catchDatabaseExceptions {
                when (scope) {
                    is ReviewReminderScope.Global -> ReviewRemindersDatabase.editAllAppWideReminders(toggleReminder(reminder))
                    is ReviewReminderScope.DeckSpecific -> ReviewRemindersDatabase.editRemindersForDeck(scope.did, toggleReminder(reminder))
                }
            }
        }

        // Update UI
        reminder.enabled = newState
        triggerUIUpdate()

        // Update scheduled AlarmManager notifications
        when (newState) {
            true -> AlarmManagerService.scheduleReviewReminderNotification(requireContext(), reminder)
            false -> AlarmManagerService.unscheduleReviewReminderNotifications(requireContext(), reminder)
        }
    }

    /**
     * Opens a screen where the user can see why reminders may not fire as expected
     * @see ReminderTroubleshootingFragment
     */
    private fun openTroubleshootingScreen() {
        troubleshootingSnackbar?.dismiss()
        parentFragmentManager.commit {
            replace(
                R.id.fragment_container,
                ReminderTroubleshootingFragment(),
                SingleFragmentActivity.FRAGMENT_TAG,
            )
            addToBackStack(null)
        }
    }

    /**
     * The method that runs when the "+" icon is pressed, allowing the user to create a new review reminder.
     * Opens [AddEditReminderDialog] in [AddEditReminderDialog.DialogMode.Add] mode.
     */
    private fun addReminder() {
        Timber.d("Adding new review reminder")
        val dialogMode = AddEditReminderDialog.DialogMode.Add(scheduleRemindersScope)
        val dialog = AddEditReminderDialog.getInstance(dialogMode)
        // Save the dialog mode so that we refer back to it once the dialog closes
        requireArguments().putParcelable(ACTIVE_DIALOG_MODE_ARGUMENTS_KEY, dialogMode)
        showDialogFragment(dialog)
    }

    /**
     * The method that runs when an existing reminder is tapped, allowing the user to change its fields.
     * Opens [AddEditReminderDialog] in [AddEditReminderDialog.DialogMode.Edit] mode.
     */
    private fun editReminder(reminder: ReviewReminder) {
        Timber.d("Editing review reminder: %s", reminder.id)
        val dialogMode = AddEditReminderDialog.DialogMode.Edit(reminder)
        val dialog = AddEditReminderDialog.getInstance(dialogMode)
        // Save the dialog mode so that we refer back to it once the dialog closes
        requireArguments().putParcelable(ACTIVE_DIALOG_MODE_ARGUMENTS_KEY, dialogMode)
        showDialogFragment(dialog)
    }

    /**
     * [AddEditReminderDialog] requires to catch changes from [DeckSelectionDialog]. However,
     * [AddEditReminderDialog] is removed from the fragment stack when the [DeckSelectionDialog]
     * appears, so we set [ScheduleReminders] as the receiver and forward data to
     * [AddEditReminderDialog] when a deck is selected.
     */
    private fun onDeckSelected(deck: SelectableDeck?) {
        Timber.d("Deck selected in deck spinner: %s", deck)
        setFragmentResult(
            DECK_SELECTION_RESULT_REQUEST_KEY,
            Bundle().apply {
                putParcelable(DECK_SELECTION_RESULT_REQUEST_KEY, deck)
            },
        )
    }

    /**
     * Trigger a RecyclerView UI update for ScheduleReminders.
     * If there are no reminders to display, show the "No Reminders" placeholder icon and text.
     */
    private fun triggerUIUpdate() {
        val listToDisplay =
            reminders
                .getRemindersList()
                .sortedBy { it.time.toSecondsFromMidnight() }
                .toList()
        adapter.submitList(listToDisplay)
        binding.noRemindersPlaceholder.isVisible = listToDisplay.isEmpty()
    }

    override fun onResume() {
        super.onResume()
        troubleshootingViewModel.refreshChecks()
    }

    companion object {
        /**
         * Arguments key for passing the [ReviewReminderScope] to open this fragment with.
         */
        private const val EXTRAS_SCOPE_KEY = "scope"

        /**
         * Arguments key for storing the current or latest [AddEditReminderDialog] instance.
         * We save this so we can pass [onDeckSelected] onward to the dialog
         * and so we can determine what reminder has been recently edited.
         */
        private const val ACTIVE_DIALOG_MODE_ARGUMENTS_KEY = "active_dialog_mode"

        /**
         * Fragment result key for receiving the result of [AddEditReminderDialog].
         * Public so [AddEditReminderDialog] can access it, too.
         */
        const val ADD_EDIT_DIALOG_RESULT_REQUEST_KEY = "add_edit_reminder_dialog_result_request_key"

        /**
         * Fragment result key for sending [AddEditReminderDialog] the result of the deck spinner selection event.
         * Public so [AddEditReminderDialog] can access it, too.
         * @see onDeckSelected
         */
        const val DECK_SELECTION_RESULT_REQUEST_KEY = "reminder_deck_selection_result_request_key"

        /**
         * Wrapper for database access in this fragment.
         * Shows an error dialog via [ReviewRemindersDatabase.checkDeserializationErrors] if there are deserialization errors.
         * Shows a progress dialog if database access takes a long time.
         */
        private suspend fun <T> Fragment.catchDatabaseExceptions(block: suspend () -> T): T? =
            withProgress { block() }.also {
                ReviewRemindersDatabase.checkDeserializationErrors(requireContext())
            }

        /**
         * Creates an intent to start the ScheduleReminders fragment.
         * @param context
         * @param scope The editing scope of the ScheduleReminders fragment.
         * @return The new intent.
         */
        fun getIntent(
            context: Context,
            scope: ReviewReminderScope,
        ): Intent =
            SingleFragmentActivity
                .getIntent(
                    context,
                    ScheduleReminders::class,
                    Bundle().apply {
                        putParcelable(EXTRAS_SCOPE_KEY, scope)
                    },
                ).apply {
                    Timber.i("launching ScheduleReminders for %s scope", scope)
                }
    }
}

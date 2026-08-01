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
import android.os.Parcelable
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.annotation.IdRes
import androidx.core.view.MenuProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.statusBars
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.canUserAccessDeck
import com.ichi2.anki.databinding.FragmentScheduleRemindersBinding
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.requireAnkiActivity
import com.ichi2.anki.reviewreminders.AddEditReminderDialog.Companion.registerAddEditReminderHandler
import com.ichi2.anki.runCatching
import com.ichi2.anki.services.AlarmManagerService
import com.ichi2.anki.snackbar.BaseSnackbarBuilderProvider
import com.ichi2.anki.snackbar.SnackbarBuilder
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.utils.ConfigAwareSingleFragmentActivity
import com.ichi2.anki.utils.ext.getParcelableCompat
import com.ichi2.anki.utils.ext.launchCollectionInLifecycleScope
import com.ichi2.anki.utils.showDialogFragment
import com.ichi2.anki.withProgress
import com.ichi2.utils.Permissions.openAppNotificationsSettingsScreen
import dev.androidbroadcast.vbpd.viewBinding
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import timber.log.Timber

/**
 * Fragment for creating, viewing, editing, and deleting review reminders.
 */
class ScheduleRemindersFragment :
    Fragment(R.layout.fragment_schedule_reminders),
    BaseSnackbarBuilderProvider {
    /**
     * Possible toolbar types that can be displayed with this fragment.
     * @see FragmentHost
     */
    @Parcelize
    enum class ToolbarType : Parcelable {
        /** For if this fragment is hosted within an external activity which handles its own toolbar. */
        EXTERNAL,

        /** For if the toolbar should look like the collapsible ones present in the Settings screen. */
        INTERNAL_COLLAPSIBLE,

        /** For if the toolbar should be simple, fragment-owned, and non-collapsible. */
        INTERNAL_NON_COLLAPSIBLE,
    }

    /**
     * Possible hosts of this fragment. Certain stylistic changes need to be made based on where this
     * fragment is opened from / nested within.
     *
     * TODO: Implement edge-to-edge for Settings, StudyOptionsActivity, and ConfigAwareSingleFragmentActivity.
     * Then, remove the supportsEdgeToEdge property below and test this fragment's UI behavior 1) on both small and wide screens,
     * 2) with all app display themes, and 3) from all possible locations this fragment can be opened from. In particular,
     * make sure there is no weird clipping of the collapsible toolbar content scrim when this fragment is opened from the Settings screen upon scrolling.
     *
     * @param containerId The XML ID of the container in which this fragment is hosted.
     * @param toolbarType The type of toolbar to display for this fragment.
     * @param supportsEdgeToEdge Whether the host of this fragment currently supports edge-to-edge rendering.
     * The legacy fitsSystemWindows property is deprecated and should be migrated away from.
     */
    @Parcelize
    enum class FragmentHost(
        @IdRes val containerId: Int,
        val toolbarType: ToolbarType,
        val supportsEdgeToEdge: Boolean,
    ) : Parcelable {
        /**
         * App-wide review reminders editing screen accessed via Settings.
         * @see com.ichi2.anki.preferences.PreferencesActivity
         */
        SETTINGS(
            containerId = R.id.settings_container,
            toolbarType = ToolbarType.INTERNAL_COLLAPSIBLE,
            supportsEdgeToEdge = false,
        ),

        /**
         * Side-by-side view of a specific deck's review reminders on large screens.
         * @see com.ichi2.anki.DeckPicker.tryShowScheduleRemindersPanel
         */
        STUDY_OPTIONS_FRAGMENT(
            containerId = R.id.studyoptions_fragment,
            toolbarType = ToolbarType.INTERNAL_NON_COLLAPSIBLE,
            supportsEdgeToEdge = true,
        ),

        /**
         * Full-screen view of a specific deck's review reminders on small screens when launched after
         * viewing the study options screen.
         * @see com.ichi2.anki.StudyOptionsActivity
         */
        STUDY_OPTIONS_FRAME(
            containerId = R.id.studyoptions_frame,
            toolbarType = ToolbarType.EXTERNAL,
            supportsEdgeToEdge = false,
        ),

        /**
         * Full-screen view of a specific deck's review reminders on small screens when launched
         * via long-pressing a deck in the DeckPicker.
         * @see com.ichi2.anki.deckpicker.DeckPickerViewModel.scheduleReviewReminders
         */
        STANDALONE_ACTIVITY(
            containerId = R.id.fragment_container,
            toolbarType = ToolbarType.INTERNAL_NON_COLLAPSIBLE,
            supportsEdgeToEdge = false,
        ),
    }

    /**
     * Whether this fragment has been opened to edit all review reminders or just a specific deck's reminders.
     * If no arguments have been passed to this fragment, defaults to [ReviewReminderScope.Global]. This default is a used
     * code path, as opening this fragment from the Settings screen is accomplished via a preference_headers android:fragment field
     * rather than a custom on-click handler for the sake of simplicity.
     *
     * @see ReviewReminderScope
     */
    private val scheduleRemindersScope: ReviewReminderScope by lazy {
        (arguments ?: Bundle()).getParcelableCompat<ReviewReminderScope>(ARG_SCOPE)
            ?: ReviewReminderScope.Global
    }

    /**
     * The [FragmentHost] of this fragment, which determines how certain UI elements are displayed.
     * If no arguments have been passed to this fragment, defaults to [FragmentHost.SETTINGS]. This default is a used
     * code path, as opening this fragment from the Settings screen is accomplished via a preference_headers android:fragment field
     * rather than a custom on-click handler for the sake of simplicity.
     *
     * @see FragmentHost
     */
    private val host: FragmentHost by lazy {
        (arguments ?: Bundle()).getParcelableCompat<FragmentHost>(ARG_HOST)
            ?: FragmentHost.SETTINGS
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

        // Set up root layout insets if the host of this fragment does not support edge-to-edge
        if (host.supportsEdgeToEdge) {
            binding.rootLayout.fitsSystemWindows = false // No need for legacy insets behavior
        } else {
            binding.rootLayout.fitsSystemWindows = true // Legacy insets behavior to avoid overlapping the status bar
            if (host.toolbarType == ToolbarType.INTERNAL_NON_COLLAPSIBLE) {
                // The legacy behavior is broken for the non-collapsible toolbar, also implement a manual workaround for it
                setNonCollapsibleToolbarInsets()
            }
        }

        // Set up toolbar
        when (host.toolbarType) {
            ToolbarType.EXTERNAL -> setupExternalActivityToolbar()
            ToolbarType.INTERNAL_COLLAPSIBLE -> setupInternalFragmentToolbar(isCollapsible = true)
            ToolbarType.INTERNAL_NON_COLLAPSIBLE -> setupInternalFragmentToolbar(isCollapsible = false)
        }

        binding.floatingActionButtonAdd.setOnClickListener { addReminder() }
        troubleshootingViewModel.state.launchCollectionInLifecycleScope(::setupTroubleshootingSnackbar)

        // Set up recycler view
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter =
            ScheduleRemindersAdapter(
                ::retrieveDeckNameFromID,
                ::retrieveCanUserAccessDeck,
                ::toggleReminder,
                ::editReminder,
            )
        binding.recyclerView.adapter = adapter

        // Retrieve reminders based on the editing scope
        launchCatchingTask { loadDatabaseRemindersIntoUI() }

        // If the user creates or edits a review reminder, the dialog for doing so opens
        // Once their changes are complete, the dialog closes and this fragment is reloaded
        // Hence, we check for any fragment results and update the database accordingly
        registerAddEditReminderHandler { newOrModifiedReminder, modeOfFinishedDialog ->
            Timber.i("Received result from add/edit dialog: mode=%s reminder=%s", modeOfFinishedDialog, newOrModifiedReminder)
            handleAddEditDialogResult(newOrModifiedReminder, modeOfFinishedDialog)
        }
    }

    override fun onDestroyView() {
        troubleshootingSnackbar?.dismiss()
        super.onDestroyView()
    }

    /**
     * Sets up the troubleshooting snackbar which is shown persistently when checks find a warning/error.
     * Tapping "Fix" opens the full troubleshooting screen.
     */
    private fun setupTroubleshootingSnackbar(state: ReminderTroubleshootingState) {
        val message =
            when (state.summaryStatus) {
                SummaryStatus.Ok, SummaryStatus.Warning -> {
                    troubleshootingSnackbar?.dismiss()
                    troubleshootingSnackbar = null
                    return
                }
                SummaryStatus.Error -> "Reminders are unavailable"
            }
        if (troubleshootingSnackbar?.isShown == true) {
            troubleshootingSnackbar?.setText(message)
            return
        }
        troubleshootingSnackbar =
            showSnackbar(text = message, duration = Snackbar.LENGTH_INDEFINITE) {
                setAction("Fix") { openTroubleshootingScreen() }
            }
    }

    private fun setupExternalActivityToolbar() {
        binding.appbar.isVisible = false
        requireAnkiActivity().apply {
            addMenuProvider(
                menuProvider,
                viewLifecycleOwner,
                Lifecycle.State.RESUMED,
            )
            retrieveSubtitle { subtitle ->
                setToolbarText(title = "Review reminders", subtitle = subtitle)
            }
            invalidateMenu()
        }
    }

    private fun setupInternalFragmentToolbar(isCollapsible: Boolean) {
        binding.appbar.isVisible = true
        val toolbar =
            if (isCollapsible) {
                binding.collapsingToolbarLayout.isVisible = true
                binding.nonCollapsibleToolbar.isVisible = false
                binding.toolbar // Use collapsible toolbar
            } else {
                binding.collapsingToolbarLayout.isVisible = false
                binding.nonCollapsibleToolbar.isVisible = true
                binding.nonCollapsibleToolbar // Use non-collapsible toolbar
            }

        toolbar.apply {
            addMenuProvider(
                menuProvider,
                viewLifecycleOwner,
                Lifecycle.State.RESUMED,
            )

            title = "Review reminders"
            retrieveSubtitle { subtitle = it }

            setNavigationOnClickListener {
                if (parentFragmentManager.backStackEntryCount > 0) {
                    parentFragmentManager.popBackStack()
                } else {
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        }
    }

    /**
     * The collapsible and non-collapsible toolbar are both located within the appbar.
     * At most one is visible at a time (both are hidden if an external toolbar is being used).
     * They must be nested within the same appbar because having more than one appbar causes issues with where
     * the second one is rendered on the screen. If edge-to-edge is not implemented for the [FragmentHost] of this fragment
     * yet, this fragment's layout and appbar has the fitsSystemWindows attribute set to ensure its
     * child toolbar is rendered below the status bar.
     *
     * However, because the collapsible toolbar is before the non-collapsible toolbar in the layout file,
     * it consumes the fitsSystemWindows inset first and does not pass any to the non-collapsible toolbar.
     * Hence, we manually set the insets of the non-collapsible toolbar when it is visible
     * via the modern setOnApplyWindowInsetsListener API. We cannot use this API for both the
     * collapsible and non-collapsible toolbars and then omit fitsSystemWindows on the appbar. This is
     * because doing so causes UI glitches within the status bar when the collapsible toolbar transitions
     * between its expanded and collapsed states.
     *
     * This should only be used for the non-collapsible toolbar if edge-to-edge is not implemented on the host yet.
     */
    private fun setNonCollapsibleToolbarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout) { _, insets ->
            val bars = insets.getInsets(statusBars() or displayCutout())
            binding.appbar.updatePadding(left = bars.left, top = bars.top, right = bars.right)
            insets
        }
    }

    private fun retrieveSubtitle(setSubtitle: (String?) -> Unit) {
        viewLifecycleOwner.lifecycleScope.launch {
            requireActivity().runCatching {
                val subtitle =
                    when (val scope = scheduleRemindersScope) {
                        is ReviewReminderScope.Global -> null
                        is ReviewReminderScope.DeckSpecific -> scope.getDeckName()
                    }
                setSubtitle(subtitle)
            }
        }
    }

    private val menuProvider: MenuProvider =
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
                    R.id.action_notification_settings -> {
                        openAppNotificationsSettingsScreen()
                        true
                    }
                    else -> false
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
                    is ReviewReminderScope.Global -> ReviewRemindersDatabase.getAllReminders()
                    is ReviewReminderScope.DeckSpecific -> ReviewRemindersDatabase.getRemindersForScope(scope)
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
                    ReviewRemindersDatabase.deleteReminder(reminderToDelete)
                }
                newOrModifiedReminder?.let { reminder ->
                    Timber.d("Writing new or modified reminder to database")
                    ReviewRemindersDatabase.insertReminder(reminder)
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
                    attemptImmediateNotification = false,
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
    private fun toggleReminder(reminder: ReviewReminder) {
        Timber.d("Toggling reminder enabled state: %s", reminder.id)

        // Update database
        launchCatchingTask {
            catchDatabaseExceptions {
                ReviewRemindersDatabase.toggleReminder(reminder)
            }
        }

        // Update UI
        reminders.toggleEnabled(reminder.id)
        triggerUIUpdate()

        // Update scheduled AlarmManager notifications
        val updatedReminder = reminders[reminder.id] ?: return
        when (updatedReminder.enabled) {
            true ->
                AlarmManagerService.scheduleReviewReminderNotification(
                    requireContext(),
                    updatedReminder,
                    attemptImmediateNotification = false,
                )
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
                host.containerId,
                ReminderTroubleshootingFragment.newInstance(host),
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
        childFragmentManager.showDialogFragment(dialog)
    }

    /**
     * The method that runs when an existing reminder is tapped, allowing the user to change its fields.
     * Opens [AddEditReminderDialog] in [AddEditReminderDialog.DialogMode.Edit] mode.
     */
    private fun editReminder(reminder: ReviewReminder) {
        Timber.d("Editing review reminder: %s", reminder.id)
        val dialogMode = AddEditReminderDialog.DialogMode.Edit(reminder)
        val dialog = AddEditReminderDialog.getInstance(dialogMode)
        childFragmentManager.showDialogFragment(dialog)
    }

    /**
     * Trigger a RecyclerView UI update for this fragment.
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
        private const val ARG_SCOPE = "arg_scope"

        /**
         * Arguments key for passing information about the [FragmentHost] to this fragment.
         */
        private const val ARG_HOST = "arg_host"

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
         * Creates an intent to launch this fragment in a [ConfigAwareSingleFragmentActivity].
         * We should not manually handle orientation changes because the dialogs nested within this
         * fragment are complicated and best handled by the default behavior. Hence, we must use the
         * config-aware version of SingleFragmentActivity.
         *
         * @param context
         * @param scope The editing scope of this fragment.
         * @return The new intent.
         */
        fun getIntent(
            context: Context,
            scope: ReviewReminderScope,
        ): Intent =
            ConfigAwareSingleFragmentActivity
                .getIntent(
                    context,
                    ScheduleRemindersFragment::class,
                    Bundle().apply {
                        putParcelable(ARG_SCOPE, scope)
                        putParcelable(ARG_HOST, FragmentHost.STANDALONE_ACTIVITY)
                    },
                ).apply {
                    Timber.i("launching ScheduleRemindersFragment for %s scope", scope)
                }

        /**
         * Returns an instance of this fragment for a specific [ReviewReminderScope] and [FragmentHost].
         * @param scope The editing scope of this fragment.
         * @param host Where this fragment is embedded; determines some UI behaviour.
         * @return The new fragment instance.
         */
        fun newInstance(
            scope: ReviewReminderScope,
            host: FragmentHost,
        ): ScheduleRemindersFragment =
            ScheduleRemindersFragment().apply {
                arguments =
                    Bundle().apply {
                        putParcelable(ARG_SCOPE, scope)
                        putParcelable(ARG_HOST, host)
                    }
                Timber.i(
                    "Creating ScheduleRemindersFragment for %s scope, host=%s",
                    scope,
                    host.name,
                )
            }
    }
}

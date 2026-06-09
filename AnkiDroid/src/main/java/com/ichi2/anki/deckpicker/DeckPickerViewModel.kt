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

package com.ichi2.anki.deckpicker

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Build
import androidx.annotation.CheckResult
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import anki.card_rendering.EmptyCardsReport
import anki.collection.OpChanges
import anki.decks.SetDeckCollapsedRequest
import anki.i18n.GeneratedTranslations
import anki.sync.SyncStatusResponse
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.CollectionManager.withOpenColOrNull
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.InitialActivity
import com.ichi2.anki.OnErrorListener
import com.ichi2.anki.PermissionSet
import com.ichi2.anki.common.destinations.BrowserDestination
import com.ichi2.anki.common.destinations.DeckOptionsDestination
import com.ichi2.anki.configureRenderingMode
import com.ichi2.anki.launchCatchingIO
import com.ichi2.anki.libanki.CardId
import com.ichi2.anki.libanki.Consts
import com.ichi2.anki.libanki.Consts.DEFAULT_DECK_ID
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.Decks
import com.ichi2.anki.libanki.sched.DeckNode
import com.ichi2.anki.libanki.undoAvailable
import com.ichi2.anki.libanki.undoLabel
import com.ichi2.anki.libanki.utils.extend
import com.ichi2.anki.noteeditor.NoteEditorLauncher
import com.ichi2.anki.notetype.ManageNoteTypesDestination
import com.ichi2.anki.observability.undoableOp
import com.ichi2.anki.performBackupInBackground
import com.ichi2.anki.reviewreminders.ScheduleRemindersDestination
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.syncAuth
import com.ichi2.anki.utils.Destination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.ankiweb.rsdroid.RustCleanup
import net.ankiweb.rsdroid.exceptions.BackendNetworkException
import timber.log.Timber
import com.ichi2.anki.common.destinations.Destination as NavigateDestination

/**
 * ViewModel for the [DeckPicker]
 */
class DeckPickerViewModel :
    ViewModel(),
    OnErrorListener {
    val flowOfStartupResponse = MutableStateFlow<StartupResponse?>(null)

    private val flowOfDeckDueTree = MutableStateFlow<DeckNode?>(null)

    /** The root of the tree displaying all decks */
    var dueTree: DeckNode?
        get() = flowOfDeckDueTree.value
        private set(value) {
            flowOfDeckDueTree.value = value
        }

    /** User filter of the deck list. Shown as a search in the UI */
    private val flowOfCurrentDeckFilter = MutableStateFlow(DeckFilters.create(""))

    /**
     * Keep track of which deck was last given focus in the deck list. If we find that this value
     * has changed between deck list refreshes, we need to recenter the deck list to the new current
     * deck.
     */
    val flowOfFocusedDeck = MutableStateFlow<DeckId?>(null)

    var focusedDeck: DeckId?
        get() = flowOfFocusedDeck.value
        set(value) {
            flowOfFocusedDeck.value = value
        }

    /**
     * Used if the Deck Due Tree is mutated
     */
    private val flowOfRefreshDeckList = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val flowOfDeckList =
        combine(
            flowOfDeckDueTree,
            flowOfCurrentDeckFilter,
            flowOfFocusedDeck,
            flowOfRefreshDeckList.onStart { emit(Unit) },
        ) { tree, filter, _, _ ->
            if (tree == null) return@combine FlattenedDeckList.empty

            // TODO: use flowOfFocusedDeck once it's set on all instances
            val currentDeckId = withCol { decks.current().getLong("id") }
            Timber.i("currentDeckId: %d", currentDeckId)

            FlattenedDeckList(
                data = tree.filterAndFlattenDisplay(filter, currentDeckId),
                hasSubDecks = tree.children.any { it.children.any() },
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = FlattenedDeckList.empty)

    /**
     * @see deleteDeck
     * @see DeckDeletionResult
     */
    val deckDeletedNotification = MutableSharedFlow<DeckDeletionResult>(extraBufferCapacity = 1)
    val emptyCardsNotification = MutableSharedFlow<EmptyCardsResult>(extraBufferCapacity = 1)
    val flowOfDestination = MutableSharedFlow<Destination>(extraBufferCapacity = 1)
    val flowOfNavigate = MutableSharedFlow<NavigateDestination>(extraBufferCapacity = 1)
    override val onError = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val flowOfExportDeck = MutableSharedFlow<DeckId>()
    val flowOfCreateShortcut = MutableSharedFlow<ShortcutData>()
    val flowOfDisableShortcuts = MutableSharedFlow<List<String>>()

    /**
     * A notification that the study counts have changed
     */
    // TODO: most of the recalculation should be moved inside the ViewModel
    val flowOfDeckCountsChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    var loadDeckCounts: Job? = null
        private set

    /**
     * Tracks the scheduler version for which the upgrade dialog was last shown,
     * to avoid repeatedly prompting the user for the same collection version.
     */
    private var schedulerUpgradeDialogShownForVersion: Long? = null

    val flowOfPromptUserToUpdateScheduler = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val flowOfCollectionHasNoCards = MutableStateFlow(true)

    val flowOfDeckListInInitialState =
        combine(flowOfDeckDueTree, flowOfCollectionHasNoCards) { tree, noCards ->
            if (tree == null) return@combine null
            // Check if default deck is the only available and there are no cards
            tree.onlyHasDefaultDeck() && noCards
        }.stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = null)

    val flowOfCardsDue =
        combine(flowOfDeckDueTree, flowOfDeckListInInitialState) { tree, inInitialState ->
            if (tree == null || inInitialState != false) return@combine null
            tree.newCount + tree.revCount + tree.lrnCount
        }

    /** "Studied N cards in 0 seconds today */
    val flowOfStudiedTodayStats = MutableStateFlow("")

    /** Flow that determines when the resizing divider should be visible */
    val flowOfResizingDividerVisible =
        combine(flowOfDeckListInInitialState, flowOfCollectionHasNoCards) { isInInitialState, hasNoCards ->
            !(isInInitialState == true || hasNoCards)
        }

    // HACK: dismiss a legacy progress bar
    // TODO: Replace with better progress handling for first load/corrupt collections
    // This MutableSharedFlow has replay=1 due to a race condition between its collector being started
    // and a possible early emission that occurs when the user is on a metered network and a dialog has to show up
    // to ask the user if they want to trigger a sync. Normally, the spinning progress indicator is
    // dismissed via an emission to this flow after the sync is completed, but if the metered network
    // warning dialog appears, we should immediately refresh the UI in case the user decides not to sync.
    // Otherwise, the progress indicator remains indefinitely. This replay=1 ensures that the collector will
    // receive the dismissal event even if it starts after the emission.
    val flowOfDecksReloaded = MutableSharedFlow<Unit>(extraBufferCapacity = 1, replay = 1)

    // TODO: Use a sensible default rather than null
    val flowOfOptionsMenuState = MutableStateFlow<OptionsMenuState?>(null)

    /**
     * Deletes the provided deck, child decks. and all cards inside.
     *
     * This is a slow operation and should be inside `withProgress`
     *
     * @param did ID of the deck to delete
     */
    @CheckResult // This is a slow operation and should be inside `withProgress`
    fun deleteDeck(did: DeckId) =
        viewModelScope.launch {
            val deckName = withCol { decks.getLegacy(did)!!.name }
            val changes = undoableOp { decks.remove(listOf(did)) }
            // After deletion: decks.current() reverts to Default, necessitating `focusedDeck`
            // to match and avoid unnecessary scrolls in `renderPage()`.
            focusedDeck = Consts.DEFAULT_DECK_ID

            deckDeletedNotification.emit(
                DeckDeletionResult(deckName = deckName, cardsDeleted = changes.count),
            )
        }

    /**
     * Deletes the currently selected deck
     *
     * This is a slow operation and should be inside `withProgress`
     */
    @CheckResult
    fun deleteSelectedDeck() =
        viewModelScope.launch {
            val targetDeckId = withCol { decks.selected() }
            deleteDeck(targetDeckId).join()
        }

    /**
     * Removes cards in [report] from the collection.
     *
     * @param report a report about the empty cards found
     * @param preserveNotes If `true`, and a note in [report] would be removed,
     * retain the first card
     */
    fun deleteEmptyCards(
        report: EmptyCardsReport,
        preserveNotes: Boolean,
    ) = viewModelScope.launch {
        // https://github.com/ankitects/anki/blob/39e293b27d36318e00131fd10144755eec8d1922/qt/aqt/emptycards.py#L98-L109
        val toDelete = mutableListOf<CardId>()

        for (note in report.notesList) {
            if (preserveNotes && note.willDeleteNote) {
                // leave first card
                toDelete.extend(note.cardIdsList.drop(1))
            } else {
                toDelete.extend(note.cardIdsList)
            }
        }
        val result = undoableOp { removeCardsAndOrphanedNotes(toDelete) }
        emptyCardsNotification.emit(EmptyCardsResult(cardsDeleted = result.count))
    }

    // TODO: move withProgress to the ViewModel, so we don't return 'Job'
    fun emptyFilteredDeck(deckId: DeckId): Job =
        viewModelScope.launch {
            Timber.i("empty filtered deck %s", deckId)
            withCol { decks.select(deckId) }
            undoableOp { sched.emptyFilteredDeck(decks.selected()) }
            flowOfDeckCountsChanged.emit(Unit)
        }

    /**
     * Rebuilds a filtered deck with its current filter settings
     */
    @CheckResult
    fun rebuildFilteredDeck(deckId: DeckId): Job =
        viewModelScope.launch {
            Timber.i("rebuilding filtered deck %s", deckId)
            withCol {
                decks.select(deckId)
                sched.rebuildFilteredDeck(decks.selected())
            }
            flowOfDeckCountsChanged.emit(Unit)
        }

    /**
     * Marks [deckId] as the currently selected deck and updates the selection in the deck list.
     */
    fun selectDeck(deckId: DeckId) =
        viewModelScope.launch {
            // TODO: should we always reset the Card Browser default deck here?
            withCol { decks.select(deckId) }
            focusedDeck = deckId
            flowOfRefreshDeckList.emit(Unit)
        }

    fun browseCards(deckId: DeckId) =
        launchCatchingIO {
            withCol { decks.select(deckId) }
            flowOfNavigate.emit(BrowserDestination.ToDeck(deckId))
        }

    fun addNote(
        deckId: DeckId?,
        setAsCurrent: Boolean,
    ) = launchCatchingIO {
        if (deckId != null && setAsCurrent) {
            withCol { decks.select(deckId) }
        }
        flowOfDestination.emit(NoteEditorLauncher.AddNote(deckId))
    }

    val flowOfShowContextMenu = MutableSharedFlow<DeckId>(extraBufferCapacity = 1)

    data class RightClickMenuRequest(
        val deckId: DeckId,
        val x: Float,
        val y: Float,
    )

    val flowOfShowRightClickContextMenu = MutableSharedFlow<RightClickMenuRequest>(extraBufferCapacity = 1)

    fun requestContextMenu(deckId: DeckId) =
        viewModelScope.launch {
            selectDeck(deckId).join()
            flowOfShowContextMenu.emit(deckId)
        }

    fun requestRightClickContextMenu(
        deckId: DeckId,
        x: Float,
        y: Float,
    ) = viewModelScope.launch {
        selectDeck(deckId).join()
        flowOfShowRightClickContextMenu.emit(RightClickMenuRequest(deckId, x, y))
    }

    /**
     * Opens the Manage Note Types screen.
     */
    fun openManageNoteTypes() = launchCatchingIO { flowOfDestination.emit(ManageNoteTypesDestination()) }

    /**
     * Opens study options for the provided deck
     *
     * @param deckId Deck to open options for
     * @param isFiltered (optional) optimization for when we know the deck is filtered
     */
    fun openDeckOptions(
        deckId: DeckId,
        isFiltered: Boolean? = null,
    ) = launchCatchingIO {
        // open cram options if filtered deck, otherwise open regular options
        val filtered = isFiltered ?: withCol { decks.isFiltered(deckId) }
        flowOfNavigate.emit(DeckOptionsDestination(deckId = deckId, isFiltered = filtered))
    }

    fun unburyDeck(deckId: DeckId) =
        launchCatchingIO {
            undoableOp<OpChanges> { sched.unburyDeck(deckId) }
        }

    fun scheduleReviewReminders(deckId: DeckId) =
        viewModelScope.launch {
            flowOfDestination.emit(ScheduleRemindersDestination(deckId))
        }

    /**
     * Launch an asynchronous task to rebuild the deck list and recalculate the deck counts. Use this
     * after any change to a deck (e.g., rename, importing, add/delete) that needs to be reflected
     * in the deck list.
     *
     * This method also triggers an update for the widget to reflect the newly calculated counts.
     */
    // TODO: this should not be executed if syncing - called after sync is completed
    @RustCleanup("backup with 5 minute timer, instead of deck list refresh")
    fun updateDeckList(): Job =
        viewModelScope.launch(Dispatchers.IO) {
            // WARN: On a regular sync, this blocks until the sync completes
            // On a full sync, the collection is closed
            if (!CollectionManager.isOpenUnsafe()) {
                return@launch
            }
            if (Build.FINGERPRINT != "robolectric") {
                // uses user's desktop settings to determine whether a backup
                // actually happens
                launchCatchingIO { performBackupInBackground() }
            }
            Timber.d("updateDeckList")
            reloadDeckCounts().join()
        }

    fun reloadDeckCounts(): Job {
        loadDeckCounts?.cancel()
        val loadDeckCounts =
            viewModelScope.launch(Dispatchers.IO) {
                Timber.d("Refreshing deck list")
                val (deckDueTree, collectionHasNoCards) =
                    withCol {
                        Pair(sched.deckDueTree(), isEmpty)
                    }
                dueTree = deckDueTree

                flowOfCollectionHasNoCards.value = collectionHasNoCards

                // TODO: This is in the wrong place
                // Backend returns studiedToday() with newlines for HTML formatting,so we replace them with spaces.
                flowOfStudiedTodayStats.value = withCol { sched.studiedToday().replace("\n", " ") }

                /**
                 * Checks the current scheduler version and prompts the upgrade dialog if using the legacy version.
                 * Ensures the dialog is only shown once per collection load, even if [updateDeckList()] is called multiple times.
                 */
                val currentSchedulerVersion = withCol { config.get("schedVer") as? Long ?: 1L }

                if (currentSchedulerVersion == 1L && schedulerUpgradeDialogShownForVersion != 1L) {
                    schedulerUpgradeDialogShownForVersion = 1L
                    flowOfPromptUserToUpdateScheduler.emit(Unit)
                } else {
                    schedulerUpgradeDialogShownForVersion = currentSchedulerVersion
                }

                // TODO: This is in the wrong place
                // current deck may have changed
                focusedDeck = withCol { decks.current().id }
                refreshUndoMenuState()

                flowOfDecksReloaded.emit(Unit)
            }
        this.loadDeckCounts = loadDeckCounts
        return loadDeckCounts
    }

    fun updateDeckFilter(filterText: String) {
        Timber.d("filter: %s", filterText)
        flowOfCurrentDeckFilter.value = DeckFilters.create(filterText)
    }

    fun toggleDeckExpand(deckId: DeckId) =
        viewModelScope.launch {
            // update stored state
            dueTree?.find(deckId)?.run {
                collapsed = !collapsed
                // Anki uses scope as Reviewer in deck browser
                withCol {
                    decks.setCollapsed(
                        did,
                        collapsed = collapsed,
                        SetDeckCollapsedRequest.Scope.REVIEWER,
                    )
                }
            }
            flowOfRefreshDeckList.emit(Unit)
        }

    /**
     * Requests export for the specified deck
     */
    fun exportDeck(deckId: DeckId) =
        launchCatchingIO {
            flowOfExportDeck.emit(deckId)
        }

    /**
     * Find the position of a deck in the flattened deck list.
     * If the deck is a child of a collapsed deck, returns the position of the parent deck.
     * Returns 0 if the deck is not found.
     */
    fun findDeckPosition(deckId: DeckId): Int {
        val currentDeckList = flowOfDeckList.value.data
        currentDeckList.forEachIndexed { index, treeNode ->
            if (treeNode.did == deckId) {
                return index
            }
        }

        // If the deck is not in our list, search using the immediate parent
        val collapsedDeck = dueTree?.find(deckId) ?: return 0
        val parent = collapsedDeck.parent?.get() ?: return 0
        return findDeckPosition(parent.did)
    }

    /**
     * Prepares data for creating a deck shortcut
     */
    fun createIcon(deckId: DeckId) =
        launchCatchingIO {
            val (shortLabel, longLabel) =
                withCol {
                    val fullName = decks.name(deckId)
                    Pair(
                        Decks.basename(fullName),
                        fullName,
                    )
                }
            flowOfCreateShortcut.emit(
                ShortcutData(
                    deckId = deckId,
                    shortLabel = shortLabel,
                    longLabel = longLabel,
                ),
            )
        }

    /** Disables the shortcut of the deck and the children belonging to it.*/
    fun disableDeckAndChildrenShortcuts(deckId: DeckId) =
        launchCatchingIO {
            val deckTreeDids = dueTree?.find(deckId)?.map { it.did.toString() } ?: emptyList()
            flowOfDisableShortcuts.emit(deckTreeDids)
        }

    sealed class StartupResponse {
        data class RequestPermissions(
            val requiredPermissions: PermissionSet,
        ) : StartupResponse()

        /**
         * The app failed to start and is probably unusable (e.g. No disk space/DB corrupt)
         *
         * @see InitialActivity.StartupFailure
         */
        data class FatalError(
            val failure: InitialActivity.StartupFailure,
        ) : StartupResponse()

        data object Success : StartupResponse()
    }

    /**
     * The first call in showing dialogs for startup - error or success.
     * Attempts startup if storage permission has been acquired, else, it requests the permission
     *
     * @see flowOfStartupResponse
     */
    fun handleStartup(environment: AnkiDroidEnvironment) {
        if (!environment.hasRequiredPermissions()) {
            Timber.i("${this.javaClass.simpleName}: postponing startup code - permission screen shown")
            flowOfStartupResponse.value = StartupResponse.RequestPermissions(environment.requiredPermissions)
            return
        }

        Timber.d("handleStartup: Continuing after permission granted")
        val failure = InitialActivity.getStartupFailureType(environment::initializeAnkiDroidFolder)
        if (failure != null) {
            flowOfStartupResponse.value = StartupResponse.FatalError(failure)
            return
        }

        // successful startup

        configureRenderingMode()

        flowOfStartupResponse.value = StartupResponse.Success
    }

    interface AnkiDroidEnvironment {
        fun hasRequiredPermissions(): Boolean

        val requiredPermissions: PermissionSet

        fun initializeAnkiDroidFolder(): Boolean
    }

    /** Represents [dueTree] as a list */
    data class FlattenedDeckList(
        val data: List<DisplayDeckNode>,
        val hasSubDecks: Boolean,
    ) {
        companion object {
            val empty = FlattenedDeckList(emptyList(), hasSubDecks = false)
        }
    }

    /**
     * Fetches the current sync icon state for the menu
     */
    suspend fun fetchSyncIconState(): SyncIconState {
        if (!Prefs.displaySyncStatus) return SyncIconState.Normal
        val auth = syncAuth() ?: return SyncIconState.NotLoggedIn
        return try {
            // Use CollectionManager to ensure that this doesn't block 'deck count' tasks
            // throws if a .colpkg import or similar occurs just before this call
            val output = withContext(Dispatchers.IO) { CollectionManager.getBackend().syncStatus(auth) }
            if (output.hasNewEndpoint() && output.newEndpoint.isNotEmpty()) {
                Prefs.currentSyncUri = output.newEndpoint
            }
            when (output.required) {
                SyncStatusResponse.Required.NO_CHANGES -> SyncIconState.Normal
                SyncStatusResponse.Required.NORMAL_SYNC -> SyncIconState.PendingChanges
                SyncStatusResponse.Required.FULL_SYNC -> SyncIconState.OneWay
                SyncStatusResponse.Required.UNRECOGNIZED, null -> TODO("unexpected required response")
            }
        } catch (_: BackendNetworkException) {
            SyncIconState.Normal
        } catch (e: Exception) {
            Timber.d(e, "error obtaining sync status: collection likely closed")
            SyncIconState.Normal
        }
    }

    /**
     * Current state of the options menu, or `null` if the collection is inaccessible.
     *
     * Updated by [refreshMenuState] and [refreshUndoMenuState].
     */
    val optionsMenuState: OptionsMenuState? get() = flowOfOptionsMenuState.value

    /**
     * Recomputes the full options menu state from the current collection.
     */
    suspend fun refreshMenuState() {
        flowOfOptionsMenuState.value =
            withOpenColOrNull {
                val searchIcon = decks.count() >= 10
                val undoLabel = undoLabel()
                val undoAvailable = undoAvailable()
                // besides checking for cards being available also consider if we have empty decks
                val isColEmpty = isEmpty && decks.count() == 1
                // the correct sync status is fetched in the next call so "Normal" is used as a placeholder
                OptionsMenuState(searchIcon, undoLabel, SyncIconState.Normal, undoAvailable, isColEmpty)
            }?.let { (searchIcon, undoLabel, _, undoAvailable, isColEmpty) ->
                val syncIcon = fetchSyncIconState()
                OptionsMenuState(searchIcon, undoLabel, syncIcon, undoAvailable, isColEmpty)
            }
    }

    /**
     * Refreshes only the undo-related fields of the menu state, leaving the rest untouched.
     *
     * @see refreshMenuState
     */
    private suspend fun refreshUndoMenuState() {
        withOpenColOrNull {
            val newUndoLabel = undoLabel()
            val newUndoAvailable = undoAvailable()
            flowOfOptionsMenuState.update { current ->
                current?.copy(undoLabel = newUndoLabel, undoAvailable = newUndoAvailable)
            }
        }
    }

    @SuppressLint("UseKtx")
    fun getPreviousVersion(
        preferences: SharedPreferences,
        current: Long,
    ): Long {
        var previous: Long
        try {
            previous = preferences.getLong(UPGRADE_VERSION_KEY, current)
        } catch (e: ClassCastException) {
            Timber.w(e)
            previous =
                try {
                    // set 20900203 to default value, as it's the latest version that stores integer in shared prefs
                    preferences.getInt(UPGRADE_VERSION_KEY, 20900203).toLong()
                } catch (cce: ClassCastException) {
                    Timber.w(cce)
                    // Previous versions stored this as a string.
                    val s = preferences.getString(UPGRADE_VERSION_KEY, "")
                    // The last version of AnkiDroid that stored this as a string was 2.0.2.
                    // We manually set the version here, but anything older will force a DB check.
                    if ("2.0.2" == s) {
                        40
                    } else {
                        0
                    }
                }
            Timber.d("Updating shared preferences stored key %s type to long", UPGRADE_VERSION_KEY)
            // Expected Editor.putLong to be called later to update the value in shared prefs
            preferences.edit().remove(UPGRADE_VERSION_KEY).apply()
        }
        Timber.i("Previous AnkiDroid version: %s", previous)
        return previous
    }

    companion object {
        const val UPGRADE_VERSION_KEY = "lastUpgradeVersion"
    }
}

/** Result of [DeckPickerViewModel.deleteDeck] */
data class DeckDeletionResult(
    val deckName: String,
    val cardsDeleted: Int,
) {
    /**
     * @see GeneratedTranslations.browsingCardsDeletedWithDeckname
     */
    // TODO: Somewhat questionable meaning: {count} cards deleted from {deck_name}.
    @CheckResult
    fun toHumanReadableString() =
        TR.browsingCardsDeletedWithDeckname(
            count = cardsDeleted,
            deckName = deckName,
        )
}

/** Result of [DeckPickerViewModel.deleteEmptyCards] */
data class EmptyCardsResult(
    val cardsDeleted: Int,
) {
    /**
     * @see GeneratedTranslations.emptyCardsDeletedCount */
    @CheckResult
    fun toHumanReadableString() = TR.emptyCardsDeletedCount(cardsDeleted)
}

fun DeckNode.onlyHasDefaultDeck() = children.singleOrNull()?.did == DEFAULT_DECK_ID

/**
 * Data for creating a deck shortcut
 * @param shortLabel the basename of the deck (e.g., "Verbs" for "Language::English::Verbs")
 * @param longLabel the full deck name (e.g., "Language::English::Verbs")
 */
data class ShortcutData(
    val deckId: DeckId,
    val shortLabel: String,
    val longLabel: String,
)

enum class SyncIconState {
    Normal,
    PendingChanges,
    OneWay,
    NotLoggedIn,
}

/** Menu state data for the options menu */
data class OptionsMenuState(
    val searchIcon: Boolean,
    /** If undo is available, a string describing the action. */
    val undoLabel: String?,
    val syncIcon: SyncIconState,
    val undoAvailable: Boolean,
    val isColEmpty: Boolean,
)

// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import android.os.Bundle
import android.os.Parcelable
import androidx.annotation.CheckResult
import androidx.core.os.BundleCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import anki.collection.OpChanges
import anki.collection.OpChangesWithCount
import anki.search.BrowserColumns
import anki.search.BrowserRow
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.Flag
import com.ichi2.anki.browser.CardBrowserViewModel.ChangeMultiSelectMode.MultiSelectCause
import com.ichi2.anki.browser.CardBrowserViewModel.ChangeMultiSelectMode.SingleSelectCause
import com.ichi2.anki.browser.CardBrowserViewModel.ToggleSelectionState.SELECT_ALL
import com.ichi2.anki.browser.CardBrowserViewModel.ToggleSelectionState.SELECT_NONE
import com.ichi2.anki.browser.FindAndReplaceDialogFragment.Companion.ALL_FIELDS_AS_FIELD
import com.ichi2.anki.browser.FindAndReplaceDialogFragment.Companion.TAGS_AS_FIELD
import com.ichi2.anki.browser.RepositionCardsRequest.RepositionData
import com.ichi2.anki.browser.search.SavedSearch
import com.ichi2.anki.browser.search.SavedSearches
import com.ichi2.anki.browser.search.SearchFilters
import com.ichi2.anki.browser.search.SearchRequest
import com.ichi2.anki.browser.search.SearchString
import com.ichi2.anki.common.ALL_DECKS_ID
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.common.destinations.CardInfoDestination
import com.ichi2.anki.common.destinations.CardInfoDestination.EntryPoint
import com.ichi2.anki.common.destinations.NoteEditorDestination
import com.ichi2.anki.common.ui.TransitionDirection
import com.ichi2.anki.common.utils.ext.indexOfOrNull
import com.ichi2.anki.export.ExportDialogFragment.ExportType
import com.ichi2.anki.launchCatchingIO
import com.ichi2.anki.libanki.Card
import com.ichi2.anki.libanki.CardId
import com.ichi2.anki.libanki.CardType
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.NoteId
import com.ichi2.anki.libanki.QueueType
import com.ichi2.anki.libanki.QueueType.ManuallyBuried
import com.ichi2.anki.libanki.QueueType.SiblingBuried
import com.ichi2.anki.libanki.notesOfCards
import com.ichi2.anki.model.CardStateFilter
import com.ichi2.anki.model.CardsOrNotes
import com.ichi2.anki.model.CardsOrNotes.CARDS
import com.ichi2.anki.model.CardsOrNotes.NOTES
import com.ichi2.anki.model.SelectableDeck
import com.ichi2.anki.model.SortType
import com.ichi2.anki.observability.undoableOp
import com.ichi2.anki.preferences.SharedPreferencesProvider
import com.ichi2.anki.progress.HasProgress
import com.ichi2.anki.progress.ProgressManager
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.utils.ViewModelSavedStateHandle
import com.ichi2.anki.utils.ext.getCardOrNull
import com.ichi2.anki.utils.ext.setUserFlagForCards
import com.ichi2.anki.utils.savedStateViewModelFactory
import com.ichi2.utils.TagsUtil.getUpdatedTags
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flattenMerge
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import net.ankiweb.rsdroid.BackendException
import org.jetbrains.annotations.VisibleForTesting
import timber.log.Timber
import java.io.File
import java.util.Collections
import kotlin.math.max
import kotlin.math.min

/**
 * Whether the current sort is reversed (descending). `true` if reversed.
 */
typealias ReverseDirection = Boolean

// TODO: move the tag computation to ViewModel

/**
 * ViewModel for [com.ichi2.anki.CardBrowser]
 *
 * @param lastDeckIdRepository returns the last selected ID. See [LastDeckIdRepository]
 * @param idsFileDirectory Location to store data too large to pass via intent. See [IdsFile.getDirectory]
 * @param options Options passed to CardBrowser on startup
 * @param preferences Accessor for `SharedPreferences`
 * @param isFragmented `true` if a NoteEditor side panel is displayed (x-large displays)
 * @param initialDeckId Deck requested by the caller, applied only on the first launch
 * @param manualInit test-only: defer `initCompleted` until `manualInit()` is called
 */
@NeedsTest("columIndex1/2 config is not not updated on init")
@NeedsTest("13442: selected deck is not changed, as this affects the reviewer")
@NeedsTest("search is called after launch()")
class CardBrowserViewModel(
    private val lastDeckIdRepository: LastDeckIdRepository,
    private val idsFileDirectory: File,
    options: CardBrowserLaunchOptions?,
    preferences: SharedPreferencesProvider,
    val isFragmented: Boolean,
    private val initialDeckId: DeckId?,
    val savedStateHandle: ViewModelSavedStateHandle,
    private val browserOptionsRepository: BrowserOptionsRepository =
        BrowserOptionsRepository(preferences.sharedPrefs()),
    private val manualInit: Boolean = false,
) : ViewModel(),
    SharedPreferencesProvider by preferences,
    HasProgress<CardBrowserProgress> {
    override val progressManager = ProgressManager<CardBrowserProgress>()

    // TODO: abstract so we can use a `Context` and `pref_display_filenames_in_browser_key`
    val showMediaFilenames = sharedPrefs().getBoolean("card_browser_show_media_filenames", false)

    /** A job which ensures that parallel searches do not occur */
    var searchJob: Job? = null
        private set

    // temporary flow for refactoring - called when cards are cleared
    val flowOfCardsUpdated = MutableSharedFlow<Unit>()

    val cards = BrowserRowCollection(CARDS, mutableListOf())

    val flowOfSearchState =
        MutableSharedFlow<SearchState>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    val flowOfLastCompletedSearch: StateFlow<SearchState.Completed?> =
        flowOfSearchState
            .filterIsInstance<SearchState.Completed>()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = null,
            )

    // Keep feedback while the browser is stopped (e.g. saving in the note editor).
    // The UI consumes it once; a newer search discards feedback for the old results.
    val flowOfSearchFeedback: StateFlow<SearchState.Completed?>
        field = MutableStateFlow<SearchState.Completed?>(null)

    fun consumeSearchFeedback(search: SearchState.Completed): Boolean = flowOfSearchFeedback.compareAndSet(search, null)

    /**
     * Commands to drive the note editor either in a fragment or a standalone activity
     * @see NoteEditorCommand
     */
    val flowOfNoteEditorCommand = MutableSharedFlow<NoteEditorCommand>()

    sealed interface NoteEditorCommand {
        /** Tablet pane: show pane and load the editor at [destination]. */
        data class LoadInPane(
            val destination: NoteEditorDestination,
        ) : NoteEditorCommand

        /** Phone: launch the standalone NoteEditor activity at [destination]. */
        data class LaunchActivity(
            val destination: NoteEditorDestination,
        ) : NoteEditorCommand

        /** Tablet pane: hide the pane (no row available). */
        data object HidePane : NoteEditorCommand

        companion object
    }

    /** text in the search box (potentially unsubmitted) */
    // this does not currently bind to the value in the UI and is only used for posting
    val flowOfFilterQuery = MutableSharedFlow<String>()

    /**
     * Whether the browser is working in Cards mode or Notes mode.
     * default: [CARDS]
     * */
    val flowOfCardsOrNotes: StateFlow<CardsOrNotes> = browserOptionsRepository.cardsOrNotes
    val cardsOrNotes: CardsOrNotes get() = flowOfCardsOrNotes.value

    /**
     * Ensures [paneRow] points to a row in the current [cards] list, falling back to the
     * first row when [paneRow] no longer visible.
     *
     * No-op on phones.
     */
    private fun ensurePaneRowValid() {
        paneRow =
            when {
                !isFragmented -> null
                cards.isEmpty() -> null
                paneRow == null || paneRow !in cards -> cards.first()
                else -> paneRow
            }
    }

    var cardIdToBeScrolledTo: CardId? = null
        private set

    val flowOfScrollRequest = MutableSharedFlow<RowSelection>()

    /**
     * Whether the current sort is reversed (descending).
     *
     * `null` when no sort is applied ([SortType.NoOrdering]).
     */
    val flowOfReverseDirection: MutableStateFlow<ReverseDirection?> = MutableStateFlow(null)

    /** Emits each time the user changes the sort order, with data for a snackbar */
    val flowOfSortTypeChanged = MutableSharedFlow<SortChangeNotification>()

    val flowOfCurrentSort: StateFlow<SortChangeNotification?>
        field = MutableStateFlow<SortChangeNotification?>(null)

    /**
     * A map from column backend key to backend column definition
     *
     * @see [flowOfColumnHeadings]
     */
    private val flowOfAllColumns = MutableStateFlow<Map<String, BrowserColumns.Column>>(mapOf())

    /** All available browser columns. @see [flowOfAllColumns] */
    val allColumns get() = flowOfAllColumns.value.values

    val flowOfActiveColumns =
        MutableStateFlow(
            BrowserColumnCollection(
                listOf(
                    CardBrowserColumn.QUESTION,
                    CardBrowserColumn.ANSWER,
                ),
            ),
        )

    @get:VisibleForTesting
    val activeColumns
        get() = flowOfActiveColumns.value.columns

    val flowOfSearchQueryExpanded = MutableStateFlow(false)

    private val searchQueryInputFlow = MutableStateFlow<String?>(null)

    /** The query which is currently in the search box, potentially null. Only set when search box was open  */
    val tempSearchQuery get() = searchQueryInputFlow.value

    /** Whether the current element in the search bar can be saved */
    val flowOfCanSearch =
        searchQueryInputFlow
            .map { it?.isNotEmpty() == true }
            .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = false)

    val flowOfIsTruncated: StateFlow<Boolean> = browserOptionsRepository.isTruncated
    val isTruncated: Boolean get() = flowOfIsTruncated.value

    val shouldIgnoreAccents: Boolean get() = browserOptionsRepository.ignoreAccentsInSearch.value

    val defaultBrowserSearch: String get() = browserOptionsRepository.defaultBrowserSearch.value

    private val _selectedRows: MutableSet<CardOrNoteId> = Collections.synchronizedSet(LinkedHashSet())

    // immutable accessor for _selectedRows
    val selectedRows: Set<CardOrNoteId> get() = _selectedRows

    val flowOfMultiSelectModeChanged =
        savedStateHandle.getMutableStateFlow<ChangeMultiSelectMode>(
            key = STATE_MULTISELECT,
            initialValue = SingleSelectCause.Other,
        )

    @Parcelize
    data class RowSelection(
        val rowId: CardOrNoteId,
        val topOffset: Int,
    ) : Parcelable

    val isInMultiSelectMode
        get() = flowOfMultiSelectModeChanged.value.resultedInMultiSelect

    private val refreshSelectedRowsFlow = MutableSharedFlow<Unit>()
    val flowOfSelectedRows: Flow<Set<CardOrNoteId>> =
        flowOf(selectedRows)
            .combine(refreshSelectedRowsFlow) { row, _ -> row }
            .combine(flowOfMultiSelectModeChanged) { rows, multiSelect ->
                if (!multiSelect.resultedInMultiSelect) emptySet() else rows
            }

    val flowOfToggleSelectionState =
        flowOfSelectedRows
            .map { selectedRows ->
                // if all rows are selected: 'SELECT_NONE', otherwise: 'SELECT_ALL'
                return@map if (selectedRows.size >= rowCount) SELECT_NONE else SELECT_ALL
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.Lazily,
                initialValue = SELECT_NONE,
            )

    /**
     * If cards are marked or flagged
     */
    val flowOfCardStateChanged = MutableSharedFlow<Unit>()

    // Queue effects while the browser's STARTED collector is stopped.
    val uiEffects: ReceiveChannel<BrowserUiEffect>
        field = Channel<BrowserUiEffect>(Channel.BUFFERED)

    val flowOfChangeNoteType = MutableSharedFlow<ChangeNoteTypeResponse>()

    /**
     * Opens a prompt for the user to input a saved search name
     *
     * The parameter is the 'searchTerms' to be used in the saved search
     */
    val flowOfSaveSearchNamePrompt = MutableSharedFlow<String>()

    /**
     * The row whose note is loaded in the trailing pane ([isFragmented] mode only).
     *
     * `null` when the pane displays no row:
     * * on phones.
     * * when there are no search results.
     *
     * @see NoteEditorCommand
     */
    val flowOfPaneRow: StateFlow<CardOrNoteId?>
        field = MutableStateFlow<CardOrNoteId?>(null)
    var paneRow: CardOrNoteId?
        get() = flowOfPaneRow.value
        private set(value) {
            flowOfPaneRow.value = value
        }

    suspend fun queryAllSelectedCardIds() = selectedRows.queryCardIds(this.cardsOrNotes)

    suspend fun queryAllSelectedNoteIds() = selectedRows.queryNoteIds(this.cardsOrNotes)

    /**
     * Returns the list of Card IDs that should be updated when editing [row].
     *
     * In 'Notes' mode, this includes all cards of the row's note (sibling cards).
     * In 'Cards' mode, this returns only the selected cards.
     */
    suspend fun getCardIdsForNoteEditor(row: CardOrNoteId): List<CardId> {
        // a note row maps to no cards in a corrupted collection: see toCardId
        val cardId = row.toCardId(cardsOrNotes) ?: return emptyList()

        return if (cardsOrNotes == NOTES) {
            withCol {
                getCard(cardId).note(this).cardIds(this)
            }
        } else {
            if (isInMultiSelectMode) {
                queryAllSelectedCardIds()
            } else {
                listOf(cardId)
            }
        }
    }

    /** Builds a destination to edit [row], or `null` if there's nothing to edit. */
    suspend fun editNoteDestination(row: CardOrNoteId): NoteEditorDestination? {
        val cardIds = getCardIdsForNoteEditor(row)
        if (cardIds.isEmpty()) {
            Timber.w("EditSelection skipped: card list is empty")
            return null
        }
        return NoteEditorDestination.EditSelection(
            cardIds = cardIds,
            animation = TransitionDirection.DEFAULT,
            inCardBrowserActivity = isFragmented,
        )
    }

    fun requestChangeNoteType() =
        viewModelScope.launch {
            val noteIds = queryAllSelectedNoteIds()
            Timber.i("requestChangeNoteType: querying %d selected notes", noteIds.size)
            flowOfChangeNoteType.emit(
                when {
                    noteIds.isEmpty() -> ChangeNoteTypeResponse.NoSelection
                    !noteIds.allOfSameNoteType() -> ChangeNoteTypeResponse.MixedSelection
                    else -> ChangeNoteTypeResponse.ChangeNoteType.from(noteIds)
                },
            )
        }

    @VisibleForTesting
    internal suspend fun queryAllCardIds() = cards.queryCardIds()

    var lastSelectedId: CardOrNoteId? = null

    val lastDeckId: DeckId?
        get() = lastDeckIdRepository.lastDeckId

    fun setSelectedDeck(
        deck: SelectableDeck,
        feedbackMode: BrowserSearchFeedbackMode = BrowserSearchFeedbackMode.NONE,
    ) {
        Timber.i("setting deck: %s", deck)

        lastDeckIdRepository.lastDeckId =
            when (deck) {
                is SelectableDeck.AllDecks -> ALL_DECKS_ID
                is SelectableDeck.Deck -> deck.deckId
            }

        val deckFilter =
            when (deck) {
                is SelectableDeck.AllDecks -> emptyList()
                is SelectableDeck.Deck -> listOf(deck.toDeckNameId())
            }

        val updatedFilter = searchRequestFlow.value.copyFilters { it.copy(decks = deckFilter) }
        launchSearchForCards(updatedFilter, forceRefresh = false, feedbackMode = feedbackMode)
    }

    val searchRequestFlow = MutableStateFlow(SearchRequest(query = ""))

    var searchTerms
        get() = searchRequestFlow.value.query
        private set(value) {
            searchRequestFlow.value = searchRequestFlow.value.copy(query = value)
        }

    val flowOfDeckId =
        searchRequestFlow.map {
            it.filters.decks
                .firstOrNull()
                ?.id
        }

    val deckId: DeckId?
        get() =
            searchRequestFlow.value.filters.decks
                .firstOrNull()
                ?.id

    suspend fun queryCardInfoDestination(): CardInfoDestination? {
        val firstSelectedCard = selectedRows.firstOrNull()?.toCardId(cardsOrNotes) ?: return null
        return CardInfoDestination(firstSelectedCard, EntryPoint.CURRENT_CARD_BROWSE)
    }

    private suspend fun getInitialDeck(): SelectableDeck {
        suspend fun consumeIntentDeck(): SelectableDeck.Deck? {
            if (savedStateHandle.get<Boolean>(STATE_LAUNCH_INTENT_CONSUMED) == true) return null
            savedStateHandle[STATE_LAUNCH_INTENT_CONSUMED] = true
            val deckId = initialDeckId ?: return null
            val name = withCol { decks.nameIfExists(deckId) } ?: return null
            return SelectableDeck.Deck(deckId = deckId, name = name)
        }

        // Intent-supplied deck takes precedence, but only on the first launch
        consumeIntentDeck()?.let { deck -> return deck }

        val lastDeckId = lastDeckId
        if (lastDeckId == ALL_DECKS_ID) {
            return SelectableDeck.AllDecks
        }

        // If a valid value for last deck exists then use it, otherwise use libanki selected deck
        val idToUse =
            if (lastDeckId != null && withCol { decks.getLegacy(lastDeckId) != null }) {
                lastDeckId
            } else {
                withCol { decks.selected() }
            }

        return SelectableDeck.Deck(deckId = idToUse, name = withCol { decks.name(idToUse) })
    }

    val flowOfInitCompleted = MutableStateFlow(false)

    /**
     * Cards/notes to select when the next search completes, restored from [STATE_MULTISELECT_VALUES].
     *
     * While non-empty, the rows do not yet reflect the saved selection, so
     * [generateExpensiveSavedState] saves this instead of [selectedRows]
     */
    private var pendingSelectionRestore: List<CardOrNoteId>

    /**
     * The file backing the most recently saved [STATE_MULTISELECT_VALUES], until replaced or the activity finishes.
     */
    private var multiselectStateFile: IdsFile? = null

    val flowOfColumnHeadings: StateFlow<List<ColumnHeading>> =
        combine(flowOfActiveColumns, flowOfCardsOrNotes, flowOfAllColumns) { activeColumns, cardsOrNotes, allColumns ->
            if (allColumns.isEmpty()) return@combine emptyList()
            Timber.d("updated headings for %d columns", activeColumns.count)
            activeColumns.columns.map {
                ColumnHeading(
                    label = allColumns[it.ankiColumnKey]!!.getLabel(cardsOrNotes),
                    ankiColumnKey = it.ankiColumnKey,
                )
            }
            // stateIn is required for tests
        }.stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = emptyList())

    /**
     * Whether the task launched from CardBrowserViewModel.init has completed.
     *
     * If `false`, we don't have the initial values to perform the first search
     */
    @get:VisibleForTesting
    val initCompleted get() = flowOfInitCompleted.value

    /**
     * A search should be triggered if these properties change
     */
    private val searchRequested =
        flowOf(flowOfCardsOrNotes)
            .flattenMerge()

    /**
     * Emits an item when:
     * * [initCompleted] is true
     * * A property which defines the search has been changed ([searchRequested])
     *
     * @see launchSearchForCards
     */
    private val performSearchFlow =
        flowOfInitCompleted.combineTransform(searchRequested) { init, _ ->
            if (!init) return@combineTransform
            emit(Unit)
        }

    init {
        Timber.d("CardBrowserViewModel::init, launchOptions: '${options?.javaClass?.simpleName}'")

        // restore selection state
        val idsFile =
            savedStateHandle.get<Bundle>(STATE_MULTISELECT_VALUES)?.let { bundle ->
                BundleCompat.getParcelable(bundle, STATE_MULTISELECT_VALUES, IdsFile::class.java)
            }
        multiselectStateFile = idsFile
        pendingSelectionRestore =
            try {
                idsFile?.getIdsOrNull()?.map { CardOrNoteId(it) }
            } catch (e: Exception) {
                Timber.w(e, "failed to read STATE_MULTISELECT_VALUES")
                CrashReportService.sendExceptionReport(
                    e = e,
                    origin = "CardBrowserViewModel",
                    onlyIfSilent = true,
                )
                // fallback to no selections, but still in multiselect mode
                null
            } ?: emptyList()

        var selectAllDecks = false
        when (options) {
            is CardBrowserLaunchOptions.SystemContextMenu -> {
                searchTerms = options.search.toString()
            }
            is CardBrowserLaunchOptions.SearchQueryJs -> {
                searchTerms = options.search
                selectAllDecks = options.allDecks
            }
            is CardBrowserLaunchOptions.DeepLink -> {
                searchTerms = options.search
            }
            is CardBrowserLaunchOptions.ScrollToCard -> {
                cardIdToBeScrolledTo = options.cardId
            }
            null -> {}
        }

        val initialFeedbackMode =
            when (options) {
                is CardBrowserLaunchOptions.DeepLink,
                is CardBrowserLaunchOptions.SystemContextMenu,
                is CardBrowserLaunchOptions.SearchQueryJs,
                -> BrowserSearchFeedbackMode.SEARCH_RESULTS
                is CardBrowserLaunchOptions.ScrollToCard, null -> BrowserSearchFeedbackMode.NONE
            }
        var firstSearch = true
        performSearchFlow
            .onEach {
                Timber.d("performSearchFlow -> launching search")
                // After initialization, this flow only runs when cards/notes mode changes.
                val feedbackMode = if (firstSearch) initialFeedbackMode else BrowserSearchFeedbackMode.COUNT_ONLY
                firstSearch = false
                launchSearchForCards(feedbackMode = feedbackMode)
            }.launchIn(viewModelScope)

        flowOfCardsOrNotes
            .onEach { cardsOrNotes ->
                Timber.d("loading columns for %s mode", cardsOrNotes)
                updateActiveColumns(BrowserColumnCollection.load(sharedPrefs(), cardsOrNotes))
            }.launchIn(viewModelScope)

        viewModelScope.launch {
            browserOptionsRepository.load()

            // Apply the default search (if available)
            if (Prefs.devUsingCardBrowserSearchView) {
                searchTerms = searchTerms.ifEmpty { defaultBrowserSearch }
            }

            val initialDeckId = if (selectAllDecks) SelectableDeck.AllDecks else getInitialDeck()
            // PERF: slightly inefficient if the source was lastDeckId
            setSelectedDeck(initialDeckId, feedbackMode = initialFeedbackMode)
            refreshBackendColumns()

            refreshSortState()

            Timber.i("initCompleted")

            if (!manualInit) {
                flowOfInitCompleted.update { true }
            }
        }

        // use setSavedStateProvider as IdsFile writes to disk, so only write when necessary
        savedStateHandle.setSavedStateProvider(STATE_MULTISELECT_VALUES) {
            Timber.d("setSavedStateProvider executed")
            generateExpensiveSavedState()
        }
    }

    @VisibleForTesting // far too complicated to mock setSavedStateProvider
    fun generateExpensiveSavedState() =
        Bundle().apply {
            // a restored selection not yet applied to the rows is still the selection to save
            saveMultiselectState(pendingSelectionRestore.ifEmpty { selectedRows.toList() })
        }

    private fun Bundle.saveMultiselectState(selection: List<CardOrNoteId>) {
        // Write the replacement before deleting the previous snapshot, so write failures preserve it.
        val idsFile =
            if (selection.isEmpty()) {
                null
            } else {
                IdsFile(idsFileDirectory, selection.map { it.cardOrNoteId }, IdsFile.Purpose.SELECTION)
            }
        multiselectStateFile?.removeSafely("CardBrowserViewModel")
        multiselectStateFile = idsFile
        if (idsFile != null) putParcelable(STATE_MULTISELECT_VALUES, idsFile)
    }

    /**
     * Delete the file backing the last saved selection when the activity is permanently dismissed.
     *
     * Call from [com.ichi2.anki.common.utils.ext.onPermanentDismissal], never from [onCleared].
     */
    internal fun deleteSavedSelectionFile() {
        multiselectStateFile?.removeSafely("CardBrowserViewModel")
        multiselectStateFile = null
    }

    /**
     * Called if `onCreate` is called again, which may be due to the collection being reopened
     *
     * If this is the case, the backend has lost the active columns state, which is required for
     * [transformBrowserRow]
     */
    fun onReinit() {
        // this can occur after process death, if so, the ViewModel starts normally
        if (!initCompleted) return

        Timber.d("onReinit: executing")

        // we currently have no way to test whether setActiveBrowserColumns was called
        // so set it again. This needs to be done immediately to ensure that the RecyclerView
        // gets correct values when initialized
        CollectionManager
            .getBackend()
            .setActiveBrowserColumns(flowOfActiveColumns.value.backendKeys)

        // if the language has changed, the backend column labels may have changed
        viewModelScope.launch {
            refreshBackendColumns()
            refreshSortState()
        }
    }

    /** Handles an update to the list of backend columns */
    private suspend fun refreshBackendColumns() {
        flowOfAllColumns.emit(withCol { allBrowserColumns() }.associateBy { it.key })
    }

    /** Handles an update of the visible columns */
    private suspend fun updateActiveColumns(columns: BrowserColumnCollection) {
        Timber.d("updating active columns")
        withCol { backend.setActiveBrowserColumns(columns.backendKeys) }
        flowOfActiveColumns.update { columns }
    }

    /**
     * Reloads active columns from SharedPreferences if they differ from the in-memory copy.
     */
    fun refreshColumnsFromPrefs() =
        viewModelScope.launch {
            if (!initCompleted) return@launch
            val stored = BrowserColumnCollection.load(sharedPrefs(), cardsOrNotes)
            if (stored.columns == activeColumns) return@launch
            Timber.d("refreshColumnsFromPrefs: columns changed externally, reloading")
            updateActiveColumns(stored)
        }

    @VisibleForTesting
    fun manualInit() {
        require(manualInit) { "'manualInit' should be true" }
        flowOfInitCompleted.update { true }
        Timber.d("manualInit")
    }

    /**
     * Handles a tap on a row.
     *
     * Outside multi-select: opens the note editor for the tapped row.
     *
     * In multi-select: toggles the row's selection.
     *
     * When deselecting a row in fragmented mode, the trailing pane is updated:
     *
     * - CARDS - selects another selected row
     * - NOTES - selection is unchanged (bug?)
     */
    fun onTap(rowSelection: RowSelection) =
        launchCatchingIO(errorMessageHandler = { /* only log */ }) {
            val id = rowSelection.rowId
            if (isInMultiSelectMode) {
                val wasSelected = id in selectedRows
                toggleRowSelection(rowSelection)
                // when in mutliselect, only deselecting should update the pane
                if (wasSelected && isFragmented) {
                    paneRow = id
                    editNoteDestination(id)?.let { flowOfNoteEditorCommand.emit(NoteEditorCommand.LoadInPane(it)) }
                }
            } else {
                setNoteEditorRow(id)
            }
        }

    fun handleRowLongPress(rowSelection: RowSelection) =
        viewModelScope.launch {
            val id = rowSelection.rowId
            if (isInMultiSelectMode && lastSelectedId != null) {
                selectRowsBetween(lastSelectedId!!, id)
            } else {
                toggleRowSelection(rowSelection)
            }
        }

    /**
     * Handles right-click on a row, by default delegating to onLongPress
     */
    fun handleRightClick(rowSelection: RowSelection) {
        viewModelScope.launch {
            val id = rowSelection.rowId
            if (isInMultiSelectMode && lastSelectedId != null) {
                selectRowsBetween(lastSelectedId!!, id)
            } else {
                toggleRowSelection(rowSelection)
            }
        }
    }

    /**
     * Opens the note editor for the given row.
     *
     * When [fragmented][isFragmented], [row] is loaded in the trailing pane and becomes the
     * [paneRow], otherwise the standalone NoteEditor activity is launched.
     */
    private fun setNoteEditorRow(row: CardOrNoteId) =
        viewModelScope.launch {
            if (isFragmented) {
                paneRow = row
            } else {
                endMultiSelectMode(SingleSelectCause.OpenNoteEditorActivity)
            }
            val destination = editNoteDestination(row) ?: return@launch
            flowOfNoteEditorCommand.emit(
                if (isFragmented) NoteEditorCommand.LoadInPane(destination) else NoteEditorCommand.LaunchActivity(destination),
            )
        }

    /**
     * Opens the note editor for the first selected row (if multi-selecting), else the first row.
     *
     * @return `false` if there are no rows to edit
     */
    fun openNoteEditorForCurrentlySelectedRow(): Boolean {
        if (cards.isEmpty()) return false
        val row = (if (isInMultiSelectMode) selectedRows.firstOrNull() else cards.firstOrNull()) ?: return false
        setNoteEditorRow(row)
        return true
    }

    /** Whether any rows are selected */
    fun hasSelectedAnyRows(): Boolean = selectedRows.isNotEmpty()

    /** Coordinates browser events; callers retain their existing coroutine error handling. */
    suspend fun onEvent(event: BrowserEvent) {
        when (event) {
            BrowserEvent.ToggleMarkRequested -> toggleMark()
            is BrowserEvent.CollectionChanged -> handleCollectionChanged(event)
        }
    }

    private suspend fun handleCollectionChanged(event: BrowserEvent.CollectionChanged) {
        if (event.origin == BrowserEvent.CollectionChanged.Origin.BROWSER) return
        val changes = event.changes
        if (!changes.browserSidebar && !changes.browserTable && !changes.noteText && !changes.card) return

        val feedbackMode =
            if (event.origin == BrowserEvent.CollectionChanged.Origin.NOTE_EDITOR) {
                BrowserSearchFeedbackMode.COUNT_ONLY
            } else {
                BrowserSearchFeedbackMode.NONE
            }
        launchSearchForCards(feedbackMode = feedbackMode)
        endMultiSelectMode(SingleSelectCause.Other)
        uiEffects.send(BrowserUiEffect.RefreshControls)
    }

    /**
     * All the notes of the selected cards will be marked
     * If one or more card is unmarked, all will be marked,
     * otherwise, they will be unmarked
     */
    private suspend fun toggleMark() {
        progressManager.withProgress {
            val cardIds = queryAllSelectedCardIds()
            if (cardIds.isEmpty()) {
                Timber.i("Not marking cards - nothing selected")
                return@withProgress
            }
            undoableOp(this@CardBrowserViewModel) {
                val noteIds = notesOfCards(cardIds)
                // if all notes are marked, remove the mark
                // if no notes are marked, add the mark
                // if there is a mix, enable the mark on all
                val wantMark = !noteIds.all { getNote(it).hasTag(this@undoableOp, "marked") }
                Timber.i("setting mark = %b for %d notes", wantMark, noteIds.size)
                if (wantMark) {
                    tags.bulkAdd(noteIds, "marked")
                } else {
                    tags.bulkRemove(noteIds, "marked")
                }
            }
            flowOfCardStateChanged.emit(Unit)
        }
    }

    /**
     * Deletes the selected notes,
     * @return the number of deleted notes
     */
    @NeedsTest("Deleting the focused row is properly handled;#18639")
    suspend fun deleteSelectedNotes(): Int =
        progressManager.withProgress(message = CardBrowserProgress.DELETING_NOTES) {
            val cardIds = queryAllSelectedCardIds()
            // reset the pane row if that row is about to be deleted
            if (paneRow?.cardOrNoteId in cardIds) {
                paneRow = null
            }
            undoableOp(this@CardBrowserViewModel) { removeNotes(cardIds = cardIds) }
                .count
                .also {
                    endMultiSelectMode(SingleSelectCause.Other)
                    refreshSearch()
                }
        }

    fun setCardsOrNotes(newValue: CardsOrNotes) =
        viewModelScope.launch {
            browserOptionsRepository.setCardsOrNotes(newValue)
            refreshSortState()
        }

    fun setTruncated(value: Boolean) = viewModelScope.launch { browserOptionsRepository.setIsTruncated(value) }

    fun setIgnoreAccents(value: Boolean) = viewModelScope.launch { browserOptionsRepository.setIgnoreAccentsInSearch(value) }

    fun setDefaultSearchText(text: String) =
        viewModelScope.launch {
            if (!Prefs.devUsingCardBrowserSearchView) return@launch
            browserOptionsRepository.setDefaultBrowserSearch(text)
        }

    fun selectAll(): Job? {
        if (!_selectedRows.addAll(cards)) return null
        Timber.d("selecting all: %d item(s)", cards.size)
        return onAppendSelectedRows(MultiSelectCause.Other)
    }

    fun selectNone(): Job? {
        if (_selectedRows.isEmpty()) return null
        Timber.d("selecting none")
        val removalReason =
            SingleSelectCause.Other.apply {
                this.previouslySelectedRowIds = _selectedRows.toSet()
            }
        _selectedRows.clear()
        return onRemoveSelectedRows(disableMultiSelectIfEmpty = false, reason = removalReason)
    }

    /**
     * If all rows are selected, select none, otherwise select all
     */
    fun toggleSelectAllOrNone(): Job? {
        Timber.i("Toggle select all / none")
        return when (flowOfToggleSelectionState.value) {
            SELECT_ALL -> selectAll()
            SELECT_NONE -> selectNone()
        }
    }

    fun toggleRowSelection(rowSelection: RowSelection): Job {
        val id = rowSelection.rowId
        var result: Job
        if (_selectedRows.contains(id)) {
            _selectedRows.remove(id)
            result = onRemoveSelectedRows(reason = SingleSelectCause.DeselectRow(rowSelection))
        } else {
            _selectedRows.add(id)
            result = onAppendSelectedRows(MultiSelectCause.RowSelected(rowSelection))
        }
        Timber.d("toggled selecting id '%s'; %d selected", id, selectedRowCount())
        lastSelectedId = id
        return result
    }

    @VisibleForTesting
    fun selectRowAtPosition(
        pos: Int,
        rowSelection: RowSelection,
    ) {
        if (_selectedRows.add(cards[pos])) {
            onAppendSelectedRows(MultiSelectCause.RowSelected(rowSelection))
        }
    }

    /** Selects rows by id. The ids are not confirmed to be in [cards] */
    private fun selectUnvalidatedRowIds(unvalidatedIds: List<CardOrNoteId>) {
        if (unvalidatedIds.isEmpty()) return

        val validCardOrNoteIds = cards.toSet()
        val ids = unvalidatedIds.filter { validCardOrNoteIds.contains(it) }
        Timber.d("selecting %d rows", ids.size)
        if (_selectedRows.addAll(ids)) {
            onAppendSelectedRows(MultiSelectCause.Other)
        }
    }

    fun selectRowsBetween(
        start: CardOrNoteId,
        end: CardOrNoteId,
    ) {
        val startPos = cards.indexOf(start)
        val endPos = cards.indexOf(end)

        selectRowsBetweenPositions(startPos, endPos)
    }

    /**
     * @throws BackendException if the row is deleted
     */
    fun transformBrowserRow(id: CardOrNoteId): Pair<BrowserRow, Boolean> {
        val row = CollectionManager.getBackend().browserRowForId(id.cardOrNoteId)
        val isSelected = selectedRows.contains(id)
        return Pair(row, isSelected)
    }

    /**
     * Selects the cards between [startPos] and [endPos]
     */
    fun selectRowsBetweenPositions(
        startPos: Int,
        endPos: Int,
    ) {
        val begin = min(startPos, endPos)
        val end = max(startPos, endPos)
        Timber.d("selecting indices between %d and %d", begin, end)
        val cards = (begin..end).map { cards[it] }
        if (_selectedRows.addAll(cards)) {
            onAppendSelectedRows(MultiSelectCause.Other)
        }
    }

    /** emits a new value in [flowOfSelectedRows] */
    private fun onAppendSelectedRows(reason: MultiSelectCause) =
        viewModelScope.launch {
            if (_selectedRows.any()) {
                flowOfMultiSelectModeChanged.value = reason
            }
            refreshSelectedRowsFlow.emit(Unit)
            Timber.d("refreshed selected rows")
        }

    private fun onRemoveSelectedRows(
        disableMultiSelectIfEmpty: Boolean = true,
        reason: SingleSelectCause,
    ) = viewModelScope.launch {
        if (!_selectedRows.any() && disableMultiSelectIfEmpty) {
            flowOfMultiSelectModeChanged.value = reason
        }
        refreshSelectedRowsFlow.emit(Unit)
        Timber.d("refreshed selected rows")
    }

    fun selectedRowCount(): Int = selectedRows.size

    suspend fun selectedNoteCount() = selectedRows.queryNoteIds(cardsOrNotes).distinct().size

    fun hasSelectedAllDecks(): Boolean =
        searchRequestFlow.value.filters.decks
            .isEmpty()

    /**
     * Updates the [SortType] and updates the search results
     */
    fun setSortType(sortType: SortType) =
        viewModelScope.launch {
            Timber.i("setting sort type: %s", sortType)

            sortType.save(cardsOrNotes)

            val notification = updateSortState(sortType)
            flowOfSortTypeChanged.emit(notification)

            launchSearchForCards()
        }

    private fun buildSortChangeNotification(sortType: SortType): SortChangeNotification =
        when (sortType) {
            is SortType.NoOrdering -> SortChangeNotification.NoOrdering
            is SortType.CollectionOrdering -> {
                val columnLabel =
                    flowOfAllColumns.value[sortType.key.value]?.getLabel(cardsOrNotes)
                        ?: sortType.key.value
                val type =
                    runCatching { CardBrowserColumn.fromColumnKey(sortType.key.value) }
                        .getOrNull()
                        ?.type(cardsOrNotes)
                        ?: ColumnType.UNSPECIFIED
                SortChangeNotification.CollectionOrdering(
                    columnLabel = columnLabel,
                    type = type,
                    reverse = sortType.reverse,
                )
            }
        }

    private suspend fun refreshSortState() {
        updateSortState(SortType.build(cardsOrNotes))
    }

    private fun updateSortState(sortType: SortType): SortChangeNotification {
        val notification = buildSortChangeNotification(sortType)
        flowOfReverseDirection.value = (sortType as? SortType.CollectionOrdering)?.reverse
        flowOfCurrentSort.value = notification
        return notification
    }

    /**
     * Updates the backend with a new collection of columns
     *
     * @param columns the new columns to use
     * @param cardsOrNotes the mode to update columns for. If this is the active mode, then flows
     *  will be updated with the new columns
     *
     * @return Whether the operation was successful (a valid list was provided, and it was a change)
     */
    @CheckResult
    fun updateActiveColumns(
        columns: List<CardBrowserColumn>,
        cardsOrNotes: CardsOrNotes,
    ): Boolean {
        if (columns.isEmpty()) {
            Timber.d("updateColumns: no columns")
            return false
        }
        if (activeColumns == columns) {
            Timber.d("updateColumns: no changes")
            return false
        }

        // update the backend with the new columns
        val columnCollection =
            BrowserColumnCollection.replace(sharedPrefs(), cardsOrNotes, columns).newColumns

        // A user can edit the non-active columns if they:
        // * Edit the cards/notes setting in the browser options
        // * Edit the visible columns
        // * Save the columns and discard the options changes
        val isEditingCurrentHeadings = cardsOrNotes == this.cardsOrNotes
        Timber.d("editing columns for current headings: %b", isEditingCurrentHeadings)

        if (isEditingCurrentHeadings) {
            viewModelScope.launch {
                updateActiveColumns(columnCollection)
            }
        }

        return true
    }

    /**
     * Toggles the 'suspend' state of the selected cards
     *
     * If all cards are suspended, unsuspend all
     * If no cards are suspended, suspend all
     * If there is a mix, suspend all
     *
     * Refreshes the results with count feedback after the edit.
     */
    fun toggleSuspendCards() =
        viewModelScope.launch {
            if (!hasSelectedAnyRows()) {
                return@launch
            }
            progressManager.withProgress {
                Timber.d("toggling selected cards suspend status")
                val cardIds = queryAllSelectedCardIds()

                undoableOp<OpChanges>(this@CardBrowserViewModel) {
                    val wantUnsuspend = cardIds.all { getCard(it).queue == QueueType.Suspended }
                    if (wantUnsuspend) {
                        sched.unsuspendCards(cardIds)
                    } else {
                        sched.suspendCards(cardIds).changes
                    }
                }.also(::refreshAfterEdit)
                Timber.d("finished 'toggleSuspendCards'")
            }
        }

    /**
     * if all cards are buried, unbury all
     * if no cards are buried, bury all
     * if there is a mix, bury all
     *
     * if no cards are checked, do nothing
     *
     * @return Whether the operation was bury/unbury, and the number of affected cards.
     * `null` if nothing happened
     */
    suspend fun toggleBury(): BuryResult? {
        if (!hasSelectedAnyRows()) {
            Timber.w("no cards to bury")
            return null
        }

        // https://github.com/ankitects/anki/blob/074becc0cee1e9ae59be701ad6c26787f74b4594/qt/aqt/browser/browser.py#L896-L902
        fun Card.isBuried(): Boolean = queue == ManuallyBuried || queue == SiblingBuried

        return progressManager.withProgress {
            val cardIds = queryAllSelectedCardIds()

            // this variable exists as `undoableOp` needs an OpChanges as return value
            var wasBuried: Boolean? = null
            undoableOp {
                // this differs from Anki Desktop which uses the first selected card to determine the
                // 'checked' status
                val wantUnbury = cardIds.all { getCard(it).isBuried() }

                wasBuried = !wantUnbury
                if (wantUnbury) {
                    Timber.i("unburying %d cards", cardIds.size)
                    sched.unburyCards(cardIds)
                } else {
                    Timber.i("burying %d cards", cardIds.size)
                    sched.buryCards(cardIds).changes
                }
            }
            BuryResult(wasBuried = wasBuried!!, count = cardIds.size)
        }
    }

    fun querySelectionExportData(): Pair<ExportType, List<Long>>? {
        if (!hasSelectedAnyRows()) return null
        return when (this.cardsOrNotes) {
            CARDS -> Pair(ExportType.Cards, selectedRows.map { it.cardOrNoteId })
            NOTES -> Pair(ExportType.Notes, selectedRows.map { it.cardOrNoteId })
        }
    }

    /**
     * Obtains data to be displayed to the user then sent to [repositionSelectedRows]
     */
    @NeedsTest("verify behavior for repositioning with 'Randomize order'")
    suspend fun prepareToRepositionCards(): RepositionCardsRequest {
        val selectedCardIds = queryAllSelectedCardIds()

        // Separate repositionable and non-repositionable cards.
        // TODO: Add a timeout for this card-by-card scan on very large selections.
        val (repositionableIds, skippedIds) =
            withCol {
                selectedCardIds.partition { cardId ->
                    canRepositionCard(getCard(cardId))
                }
            }

        // If no cards can be repositioned, return error
        if (repositionableIds.isEmpty()) {
            return RepositionCardsRequest.NoRepositionableCardsError
        }

        // The full partition already ran, so this value is exact for now.
        val unsupportedCardCount = UnsupportedCardCount.Count(skippedIds.size)

        // query obtained from Anki Desktop
        // https://github.com/ankitects/anki/blob/1fb1cbbf85c48a54c05cb4442b1b424a529cac60/qt/aqt/operations/scheduling.py#L117
        try {
            return withCol {
                val (min, max) =
                    db
                        .query("select min(due), max(due) from cards where type=? and odid=0", CardType.New.code)
                        .use {
                            it.moveToNext()
                            Pair(max(0, it.getInt(0)), it.getInt(1))
                        }
                val defaults = sched.repositionDefaults()
                RepositionData(
                    min = min,
                    max = max,
                    random = defaults.random,
                    shift = defaults.shift,
                    unsupportedCardCount = unsupportedCardCount,
                )
            }
        } catch (e: Exception) {
            // TODO: Remove this once we've verified no production errors
            Timber.w(e, "getting min/max position")
            CrashReportService.sendExceptionReport(e, "prepareToRepositionCards")
            return RepositionData(
                min = null,
                max = null,
                unsupportedCardCount = unsupportedCardCount,
            )
        }
    }

    /**
     * @see [com.ichi2.anki.libanki.sched.Scheduler.sortCards]
     * @return the number of cards which were repositioned
     */
    suspend fun repositionSelectedRows(
        position: Int,
        step: Int,
        shuffle: Boolean,
        shift: Boolean,
    ): Int =
        progressManager.withProgress {
            val ids = queryAllSelectedCardIds()

            Timber.d("repositioning %d cards to %d", ids.size, position)
            undoableOp {
                sched.sortCards(cids = ids, position, step = step, shuffle = shuffle, shift = shift)
            }.count
        }

    /** Returns the number of rows of the current result set  */
    val rowCount: Int
        get() = cards.size

    fun getRowAtPosition(position: Int) = cards[position]

    fun getPositionOfId(id: CardOrNoteId) = cards.indexOfOrNull(id)

    suspend fun savedSearches(): List<SavedSearch> = SavedSearches.loadFromConfig()

    suspend fun removeSavedSearch(searchName: String) = SavedSearches.removeByName(searchName)

    @CheckResult
    suspend fun saveSearch(search: SavedSearch): SaveSearchResult {
        val (searchAdded, _) = SavedSearches.add(search)
        return if (searchAdded) SaveSearchResult.SUCCESS else SaveSearchResult.ALREADY_EXISTS
    }

    /**
     * Sets the filter query (legacy): 'is:suspended'
     */
    private suspend fun setFilterQuery(filterQuery: String) {
        this.flowOfFilterQuery.emit(filterQuery)
        this.searchRequestFlow.value =
            searchRequestFlow.value.copy(
                query = filterQuery,
            )
        launchSearchForCards(feedbackMode = BrowserSearchFeedbackMode.SEARCH_RESULTS)
    }

    fun setQuery(
        query: String,
        forceRefresh: Boolean = true,
        feedbackMode: BrowserSearchFeedbackMode = BrowserSearchFeedbackMode.NONE,
    ) = viewModelScope.launch {
        val newValue = searchRequestFlow.value.copy(query = query)
        if (!forceRefresh && withCol { searchRequestFlow.value.toSearchString() == newValue.toSearchString() }) {
            Timber.i("skipped duplicate search launch")
            return@launch
        }

        searchRequestFlow.value = newValue
        launchSearchForCards(feedbackMode = feedbackMode)
    }

    /**
     * Searches for all marked notes and replaces the current search results with these marked notes.
     */
    fun searchForMarkedNotes() =
        viewModelScope.launch {
            // only intended to be used if the user has no selection
            if (hasSelectedAnyRows()) return@launch
            setFilterQuery("tag:marked")
        }

    /**
     * Searches for all suspended cards and replaces the current search results with these suspended cards.
     */
    fun searchForSuspendedCards() =
        viewModelScope.launch {
            // only intended to be used if the user has no selection
            if (hasSelectedAnyRows()) return@launch
            setFilterQuery("is:suspended")
        }

    suspend fun setFlagFilter(flag: Flag) {
        Timber.i("filtering to flag: %s", flag)
        val flagSearchTerm = "flag:${flag.code}"
        val searchTerms =
            when {
                searchTerms.contains("flag:") -> searchTerms.replaceFirst("flag:.".toRegex(), flagSearchTerm)
                searchTerms.isNotEmpty() -> "$flagSearchTerm $searchTerms"
                else -> flagSearchTerm
            }
        setFilterQuery(searchTerms)
    }

    /**
     * Updates the tags of selected/checked notes and saves them to the disk
     * @param selectedTags list of checked tags
     * @param indeterminateTags a list of tags which can checked or unchecked, should be ignored if not expected
     * For more info on [selectedTags] and [indeterminateTags] see [com.ichi2.anki.dialogs.tags.TagsDialogListener.onSelectedTags]
     */
    suspend fun editSelectedCardsTags(
        selectedTags: List<String>,
        indeterminateTags: List<String>,
    ) = progressManager.withProgress {
        val selectedNoteIds = queryAllSelectedNoteIds().distinct()
        undoableOp(this@CardBrowserViewModel) {
            val selectedNotes =
                selectedNoteIds
                    .map { noteId -> getNote(noteId) }
                    .onEach { note ->
                        val previousTags: List<String> = note.tags
                        val updatedTags = getUpdatedTags(previousTags, selectedTags, indeterminateTags)
                        note.setTagsFromStr(this@undoableOp, tags.join(updatedTags))
                    }
            updateNotes(selectedNotes)
        }.also(::refreshAfterEdit)
    }

    suspend fun filterByTags(
        selectedTags: List<String>,
        cardState: CardStateFilter,
    ) {
        val searchString =
            withCol {
                SearchRequest(
                    query = cardState.toSearch,
                    filters = SearchFilters.EMPTY.copy(tags = selectedTags),
                ).toSearchString()
            }.getOrThrow()

        // until we use SearchRequest for everything, we need to use () to ensure the OR
        // takes precedence over an 'AND'
        setFilterQuery("(${searchString.value})")
    }

    /** Previewing */
    suspend fun queryPreviewIntentData(): PreviewerDestination =
        progressManager.withProgress {
            // If in NOTES mode, we show one Card per Note, as this matches Anki Desktop
            if (selectedRowCount() > 1) {
                PreviewerDestination(currentIndex = 0, IdsFile(idsFileDirectory, queryAllSelectedCardIds(), IdsFile.Purpose.PREVIEW))
            } else {
                // Preview all cards, starting from the one that is currently selected
                val startIndex = indexOfFirstCheckedCard() ?: 0
                PreviewerDestination(startIndex, IdsFile(idsFileDirectory, queryOneCardIdPerNote(), IdsFile.Purpose.PREVIEW))
            }
        }

    private suspend fun queryOneCardIdPerNote(): List<CardId> = cards.queryOneCardIdPerRow()

    /** @return the index of the first checked card in [cards], or `null` if no cards are checked */
    private fun indexOfFirstCheckedCard(): Int? {
        val idToFind = selectedRows.firstOrNull() ?: return null
        return cards.indexOf(idToFind)
    }

    fun setSearchQueryExpanded(expand: Boolean) {
        if (expand) {
            expandSearchQuery()
        } else {
            collapseSearchQuery()
        }
    }

    private fun collapseSearchQuery() {
        searchQueryInputFlow.update { null }
        flowOfSearchQueryExpanded.update { false }
    }

    private fun expandSearchQuery() {
        flowOfSearchQueryExpanded.update { true }
    }

    fun updateQueryText(newText: String) {
        searchQueryInputFlow.update { newText }
    }

    fun removeUnsubmittedInput() {
        Timber.i("clearing unsubmitted input")
        searchQueryInputFlow.update { null }
    }

    fun moveSelectedCardsToDeck(deckId: DeckId): Deferred<OpChangesWithCount> =
        viewModelScope.async {
            progressManager.withProgress {
                val selectedCardIds = queryAllSelectedCardIds()
                undoableOp {
                    setDeck(selectedCardIds, deckId)
                }
            }
        }

    suspend fun updateSelectedCardsFlag(flag: Flag): List<CardId> =
        progressManager.withProgress {
            val idsToChange = queryAllSelectedCardIds()
            undoableOp(this@CardBrowserViewModel) { setUserFlagForCards(cids = idsToChange, flag = flag) }
            flowOfCardStateChanged.emit(Unit)
            idsToChange
        }

    /**
     * Turn off [Multi-Select Mode][isInMultiSelectMode] and return to normal state
     */
    fun endMultiSelectMode(reason: SingleSelectCause) {
        reason.previouslySelectedRowIds = _selectedRows.toSet()
        _selectedRows.clear()
        flowOfMultiSelectModeChanged.value = reason
    }

    /**
     * @param forceRefresh if `true`, perform a search even if the search query is unchanged
     */
    fun launchSearchForCards(
        query: String,
        forceRefresh: Boolean = true,
    ) = launchSearchForCards(
        searchRequestFlow.value.copy(query = query),
        forceRefresh,
    )

    /**
     * @param forceRefresh if `true`, perform a search even if the search query is unchanged
     * @param feedbackMode the feedback requested for this search, resolved by [browserSearchFeedback]
     */
    fun launchSearchForCards(
        searchRequest: SearchRequest,
        forceRefresh: Boolean,
        feedbackMode: BrowserSearchFeedbackMode = BrowserSearchFeedbackMode.NONE,
    ) = viewModelScope.launch {
        Timber.d("launching search [new syntax]: '%s'", searchRequest)

        context(_: Collection)
        fun SearchRequest.asSearchString(): SearchString? = this.toSearchString().getOrNull()
        val isDuplicateSearch = !forceRefresh && withCol { searchRequestFlow.value.asSearchString() == searchRequest.asSearchString() }
        // Equivalent queries can still change the text and chip filters shown to the user.
        searchRequestFlow.value = searchRequest
        if (isDuplicateSearch) {
            Timber.d("skipping duplicate search: forceRefresh is false")
            return@launch
        }

        launchSearchForCards(feedbackMode = feedbackMode)
    }

    /**
     * Once the search completes, [pendingSelectionRestore] is applied to the rows
     *
     * @see com.ichi2.anki.searchForRows
     */
    fun launchSearchForCards(feedbackMode: BrowserSearchFeedbackMode = BrowserSearchFeedbackMode.NONE) {
        if (!initCompleted) return

        viewModelScope.launch {
            searchJob?.cancel()
            flowOfSearchFeedback.value = null
            // update the UI while we're searching
            clearCardsList()

            // Another request may have started a search while clearCardsList() was suspended.
            searchJob?.cancel()
            searchJob =
                launchCatchingIO(
                    errorMessageHandler = { error -> flowOfSearchState.emit(SearchState.Error(error)) },
                ) {
                    val searchString = withCol { searchRequestFlow.value.toSearchString().getOrThrow() }
                    flowOfSearchState.emit(SearchState.Searching)
                    val sortOrder = SortType.buildSortOrder()
                    Timber.d("performing search: '%s'; order: %s", searchString, sortOrder)
                    val cards = com.ichi2.anki.searchForRows(searchString, sortOrder, cardsOrNotes)
                    Timber.d("Search returned %d card(s)", cards.size)

                    withContext(Dispatchers.Main) {
                        ensureActive()
                        // The adapter takes snapshots on the main thread.
                        this@CardBrowserViewModel.cards.replaceWith(cardsOrNotes, cards)
                    }
                    ensurePaneRowValid()
                    if (isFragmented) flowOfNoteEditorCommand.emit(NoteEditorCommand.fromCurrentSearchState())
                    val completed = SearchState.Completed.fromCurrentState(feedbackMode)
                    withContext(Dispatchers.Main) {
                        ensureActive()
                        flowOfSearchState.emit(completed)
                        flowOfSearchFeedback.value = completed.takeUnless { feedbackMode == BrowserSearchFeedbackMode.NONE }
                    }
                    selectUnvalidatedRowIds(pendingSelectionRestore)
                    pendingSelectionRestore = emptyList()
                }

            viewModelScope.launch {
                val targetId = cardIdToBeScrolledTo ?: return@launch
                searchJob?.join()
                cardIdToBeScrolledTo = null
                // validate targetId, even if in cards mode
                val card =
                    withCol { getCardOrNull(targetId) } ?: run {
                        Timber.w("Unable to find card %d", targetId)
                        return@launch
                    }
                val rowId = CardOrNoteId.fromCard(card, cardsOrNotes)
                flowOfScrollRequest.emit(RowSelection(rowId, topOffset = 0))
            }
        }
    }

    private fun refreshSearch() = launchSearchForCards()

    /** Refreshes edits which use the result count as their feedback. */
    private fun refreshAfterEdit(changes: OpChanges) {
        if (changes.browserSidebar || changes.browserTable || changes.noteText || changes.card) {
            endMultiSelectMode(SingleSelectCause.Other)
            launchSearchForCards(feedbackMode = BrowserSearchFeedbackMode.COUNT_ONLY)
        }
    }

    private suspend fun clearCardsList() {
        cards.reset()
        flowOfCardsUpdated.emit(Unit)
    }

    suspend fun queryCardIdAtPosition(index: Int): CardId = cards.queryCardIdsAt(index).first()

    /**
     * Obtains two lists of column headings with preview data
     * (preview uses the first row of data, if it exists)
     *
     * The two lists are:
     * (1): An ordered list of columns which is displayed to the user
     * (2): A list of columns which are available to display to the user
     */
    suspend fun previewColumnHeadings(cardsOrNotes: CardsOrNotes): Pair<List<ColumnWithSample>, List<ColumnWithSample>> {
        val currentColumns =
            when {
                // if we match, use the loaded the columns
                cardsOrNotes == this.cardsOrNotes -> activeColumns
                else -> BrowserColumnCollection.load(sharedPrefs(), cardsOrNotes).columns
            }

        val columnsWithSample = ColumnWithSample.loadSample(cards.firstOrNull(), cardsOrNotes)

        // we return this as two lists as 'currentColumns' uses the collection ordering
        return Pair(
            columnsWithSample
                .filter { currentColumns.contains(it.columnType) }
                .sortedBy { currentColumns.indexOf(it.columnType) },
            columnsWithSample.filter { !currentColumns.contains(it.columnType) },
        )
    }

    suspend fun loadColumnSelection(cardsOrNotes: CardsOrNotes) = progressManager.withProgress { previewColumnHeadings(cardsOrNotes) }

    suspend fun loadFindAndReplaceNoteIds() = progressManager.withProgress { queryAllSelectedNoteIds() }

    /**
     * Replaces occurrences of search with the new value.
     *
     * @return the number of affected notes
     * @see com.ichi2.anki.libanki.Collection.findAndReplace
     * @see com.ichi2.anki.libanki.Tags.findAndReplace
     */
    fun findAndReplace(result: FindReplaceResult) =
        viewModelScope.async {
            progressManager.withProgress {
                // TODO pass the selection as the user saw it in the dialog to avoid running "find
                //  and replace" on a different selection
                val noteIds = if (result.onlyOnSelectedNotes) queryAllSelectedNoteIds() else emptyList()

                if (result.field == TAGS_AS_FIELD) {
                    undoableOp {
                        tags.findAndReplace(noteIds, result.search, result.replacement, result.regex, result.matchCase)
                    }.count
                } else {
                    val field =
                        if (result.field == ALL_FIELDS_AS_FIELD) null else result.field
                    undoableOp {
                        findAndReplace(noteIds, result.search, result.replacement, result.regex, field, result.matchCase)
                    }.count
                }
            }
        }

    fun updateSelectedColumn(
        selectedColumn: ColumnHeading,
        newColumn: ColumnWithSample,
    ) = viewModelScope.launch {
        val replacementKey = selectedColumn.ankiColumnKey
        val replacements =
            activeColumns.toMutableList().apply {
                replaceAll { if (it.ankiColumnKey == replacementKey) newColumn.columnType else it }
            }
        updateActiveColumns(replacements, cardsOrNotes)
    }

    /** Opens the UI to save the current [tempSearchQuery] as a saved search */
    fun saveCurrentSearch() =
        viewModelScope.launch {
            val query = tempSearchQuery
            if (query.isNullOrEmpty()) {
                Timber.d("not prompting to saving search: no query")
                return@launch
            }
            flowOfSaveSearchNamePrompt.emit(query)
        }

    /**
     * Builds a [SearchState.Completed] event reflecting the current ViewModel state.
     *
     * @param feedbackMode the feedback requested for this search.
     */
    private fun SearchState.Completed.Companion.fromCurrentState(
        feedbackMode: BrowserSearchFeedbackMode = BrowserSearchFeedbackMode.NONE,
    ): SearchState.Completed =
        SearchState.Completed(
            rowCount = rowCount,
            cardsOrNotes = cardsOrNotes,
            feedbackMode = feedbackMode,
            allDecksSelected = hasSelectedAllDecks(),
        )

    /** Builds the post-search trailing-pane command from current ViewModel state (tablet only). */
    private suspend fun NoteEditorCommand.Companion.fromCurrentSearchState(): NoteEditorCommand =
        paneRow?.let { row -> editNoteDestination(row) }?.let { NoteEditorCommand.LoadInPane(it) }
            ?: NoteEditorCommand.HidePane

    companion object {
        /** Intent extra carrying the [DeckId] the browser should open scoped to. */
        const val EXTRA_DECK_ID = "deckId"

        /** Intent extra carrying a [CardId] to auto-scroll to once the browser opens. */
        const val EXTRA_CARD_ID_KEY = "cardId"

        /** Intent extra (`String`) carrying the search to run. */
        const val EXTRA_SEARCH_QUERY = "search_query"

        /** Intent extra (`Boolean`) for whether [EXTRA_SEARCH_QUERY] should search all decks. */
        const val EXTRA_ALL_DECKS = "all_decks"

        /** Prevents one-shot extras from being re-applied after process death. */
        private const val STATE_LAUNCH_INTENT_CONSUMED = "launchIntentConsumed"

        const val STATE_MULTISELECT = "multiselect"
        const val STATE_MULTISELECT_VALUES = "multiselect_values"

        fun factory(
            lastDeckIdRepository: LastDeckIdRepository,
            idsFileDirectory: File,
            isFragmented: Boolean,
            preferencesProvider: SharedPreferencesProvider? = null,
            options: CardBrowserLaunchOptions?,
            initialDeckId: DeckId?,
        ) = savedStateViewModelFactory { handle ->
            CardBrowserViewModel(
                lastDeckIdRepository,
                idsFileDirectory,
                options,
                preferencesProvider ?: AnkiDroidApp.sharedPreferencesProvider,
                isFragmented,
                initialDeckId,
                handle,
            )
        }
    }

    enum class ToggleSelectionState {
        SELECT_ALL,
        SELECT_NONE,
    }

    sealed interface ChangeNoteTypeResponse {
        data object NoSelection : ChangeNoteTypeResponse

        data object MixedSelection : ChangeNoteTypeResponse

        @ConsistentCopyVisibility
        data class ChangeNoteType private constructor(
            val noteIds: List<NoteId>,
        ) : ChangeNoteTypeResponse {
            companion object {
                @CheckResult
                fun from(ids: List<NoteId>): ChangeNoteType {
                    require(ids.isNotEmpty()) { "a non-empty list must be provided" }
                    return ChangeNoteType(ids.distinct())
                }
            }
        }
    }

    /**
     * @param wasBuried `true` if all cards were buried, `false` if unburied
     * @param count the number of affected cards
     */
    data class BuryResult(
        val wasBuried: Boolean,
        val count: Int,
    )

    sealed class ChangeMultiSelectMode : Parcelable {
        val resultedInMultiSelect: Boolean get() =
            when (this) {
                is MultiSelectCause -> true
                is SingleSelectCause -> false
            }

        @Parcelize
        sealed class SingleSelectCause : ChangeMultiSelectMode() {
            data class DeselectRow(
                val selection: RowSelection,
            ) : SingleSelectCause()

            data object OpenNoteEditorActivity : SingleSelectCause()

            data object NavigateBack : SingleSelectCause()

            data object Other : SingleSelectCause()

            @IgnoredOnParcel
            var previouslySelectedRowIds: Set<CardOrNoteId>? = null
        }

        @Parcelize
        sealed class MultiSelectCause : ChangeMultiSelectMode() {
            data class RowSelected(
                val selection: RowSelection,
            ) : MultiSelectCause()

            data object Other : MultiSelectCause()
        }
    }

    /** Whether [CardBrowserViewModel] is processing a search */
    sealed interface SearchState {
        /** The class is initializing */
        data object Initializing : SearchState

        /** A search is in progress */
        data object Searching : SearchState

        /** A search has been completed */
        data class Completed(
            val rowCount: Int,
            val cardsOrNotes: CardsOrNotes,
            /** The feedback requested by the caller for this search. */
            val feedbackMode: BrowserSearchFeedbackMode,
            /** The deck scope used by this search, before any subsequent UI changes. */
            val allDecksSelected: Boolean,
        ) : SearchState {
            companion object
        }

        /**
         * A search error, for instance:
         *
         * [net.ankiweb.rsdroid.BackendException.BackendSearchException]
         *
         * Invalid search: an `and` was found but it is not connecting two search terms.
         * If you want to search for the word itself, wrap it in double quotes: `"and"`.
         */
        data class Error(
            val error: String,
        ) : SearchState
    }
}

enum class SaveSearchResult {
    ALREADY_EXISTS,
    SUCCESS,
}

/** The operation [CardBrowserViewModel] is running, shown as a progress message by the UI. */
enum class CardBrowserProgress {
    DELETING_NOTES,
}

/**
 * Determines if a card can be repositioned.
 *
 * Mirrors Anki upstream logic in `set_new_position()`: https://github.com/ankitects/anki/blob/967992304627bb2bc690afd70b28760f09c2a021/rslib/src/scheduler/new.rs#L65-L80
 * - if `card.type == CardType.New`, it's repositionable
 * - otherwise, if `card.queue == QueueType.New`, it's repositionable
 *
 * @param card The card to check
 * @return true if the card can be repositioned, false otherwise
 */
private fun canRepositionCard(card: Card): Boolean = card.type == CardType.New || card.queue == QueueType.New

/** Count of selected cards that cannot be repositioned. */
sealed interface UnsupportedCardCount {
    data class Count(
        val value: Int,
    ) : UnsupportedCardCount

    /** Used when we short-circuit the scan (for example, after timeout). */
    data object Undetermined : UnsupportedCardCount
}

sealed class RepositionCardsRequest {
    /** None of the selected cards can be repositioned */
    data object NoRepositionableCardsError : RepositionCardsRequest()

    /** Should contain queue top & bottom positions. Null on error.
     * `unsupportedCardCount` uses [UnsupportedCardCount.Undetermined] when scan is short-circuited.
     */
    class RepositionData(
        val min: Int?,
        val max: Int?,
        val random: Boolean = false,
        val shift: Boolean = false,
        val unsupportedCardCount: UnsupportedCardCount = UnsupportedCardCount.Count(0),
    ) : RepositionCardsRequest() {
        val queueTop: Int?
        val queueBottom: Int?

        init {
            if (min != null && max != null) {
                // queue top: the lower of the two
                queueTop = min(min, max)
                queueBottom = max(min, max)
            } else {
                queueTop = null
                queueBottom = null
            }
        }

        fun toHumanReadableContent(): String? {
            if (queueTop == null || queueBottom == null) return null
            // ints are required for the translation
            return TR.browsingQueueTop(queueTop) + "\n" + TR.browsingQueueBottom(queueBottom)
        }
    }
}

/**
 * Whether the provided notes all have the same the same [note type][com.ichi2.anki.libanki.NoteTypeId]
 */
private suspend fun List<NoteId>.allOfSameNoteType(): Boolean {
    val noteIds = this
    return withCol { notetypes.nids(getNote(noteIds.first()).noteTypeId) }.toSet().let { set ->
        noteIds.all { set.contains(it) }
    }
}

@Parcelize
data class ColumnHeading(
    val label: String,
    val ankiColumnKey: String,
) : Parcelable

/**
 * Data needed to show a snackbar when the sort order is changed
 */
sealed interface SortChangeNotification {
    /** [SortType.NoOrdering] was selected */
    data object NoOrdering : SortChangeNotification

    /**
     * @param columnLabel The localized label for the column
     * @param type The data type of the column. Used to explain the sort direction
     * @param reverse Whether the sort is ascending/descending
     */
    data class CollectionOrdering(
        val columnLabel: String,
        val type: ColumnType,
        val reverse: Boolean,
    ) : SortChangeNotification
}

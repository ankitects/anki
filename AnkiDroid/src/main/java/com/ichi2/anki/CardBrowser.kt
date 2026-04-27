/*
 * Copyright (c) 2010 Norbert Nagold <norbert.nagold@gmail.com>
 * Copyright (c) 2012 Kostas Spyropoulos <inigo.aldana@gmail.com>
 * Copyright (c) 2014 Timothy Rae <perceptualchaos2@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki

import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.annotation.LayoutRes
import androidx.annotation.MainThread
import androidx.annotation.VisibleForTesting
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import anki.collection.OpChanges
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.browser.BrowserRowCollection
import com.ichi2.anki.browser.CardBrowserFragment
import com.ichi2.anki.browser.CardBrowserLaunchOptions
import com.ichi2.anki.browser.CardBrowserViewModel
import com.ichi2.anki.browser.CardBrowserViewModel.ChangeMultiSelectMode
import com.ichi2.anki.browser.CardBrowserViewModel.ChangeMultiSelectMode.SingleSelectCause
import com.ichi2.anki.browser.CardBrowserViewModel.NoteEditorCommand
import com.ichi2.anki.browser.CardBrowserViewModel.SearchState
import com.ichi2.anki.browser.CardBrowserViewModel.SearchState.Initializing
import com.ichi2.anki.browser.CardBrowserViewModel.SearchState.Searching
import com.ichi2.anki.browser.CardOrNoteId
import com.ichi2.anki.browser.SaveSearchResult
import com.ichi2.anki.browser.SharedPreferencesLastDeckIdRepository
import com.ichi2.anki.browser.registerFindReplaceHandler
import com.ichi2.anki.browser.search.SearchString
import com.ichi2.anki.browser.search.findCards
import com.ichi2.anki.browser.search.findNotes
import com.ichi2.anki.browser.toCardBrowserLaunchOptions
import com.ichi2.anki.common.ALL_DECKS_ID
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.common.utils.annotation.KotlinCleanup
import com.ichi2.anki.databinding.ActivityCardBrowserBinding
import com.ichi2.anki.dialogs.DiscardChangesDialog
import com.ichi2.anki.dialogs.SavedBrowserSearchesDialogFragment
import com.ichi2.anki.dialogs.registerDeckSelectedHandler
import com.ichi2.anki.dialogs.registerSaveSearchHandler
import com.ichi2.anki.dialogs.registerSavedSearchActionHandler
import com.ichi2.anki.dialogs.startDeckSelection
import com.ichi2.anki.dialogs.tags.TagsDialogFactory
import com.ichi2.anki.dialogs.tags.TagsDialogListener
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.SortOrder
import com.ichi2.anki.model.CardStateFilter
import com.ichi2.anki.model.CardsOrNotes
import com.ichi2.anki.model.CardsOrNotes.CARDS
import com.ichi2.anki.model.CardsOrNotes.NOTES
import com.ichi2.anki.model.SelectableDeck
import com.ichi2.anki.noteeditor.NoteEditorLauncher
import com.ichi2.anki.observability.ChangeManager
import com.ichi2.anki.scheduling.registerOnForgetHandler
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.ui.ResizablePaneManager
import com.ichi2.anki.utils.ext.addPrepareMenuProvider
import com.ichi2.anki.utils.ext.launchCollectionInLifecycleScope
import com.ichi2.anki.utils.ext.onAllFragmentsLoaded
import com.ichi2.ui.CardBrowserSearchView
import com.ichi2.utils.AndroidUiUtils.hideKeyboard
import com.ichi2.utils.LanguageUtil
import net.ankiweb.rsdroid.RustCleanup
import timber.log.Timber

@Suppress("LeakingThis")
// The class is only 'open' due to testing
@KotlinCleanup("scan through this class and add attributes - in process")
open class CardBrowser :
    NavigationDrawerActivity(),
    TagsDialogListener,
    ChangeManager.Subscriber,
    MenuHost {
    /**
     * Provides an instance of NoteEditorLauncher for adding a note
     */
    @get:VisibleForTesting
    val addNoteLauncher: NoteEditorLauncher
        get() = createAddNoteLauncher(viewModel)

    fun onDeckSelected(deck: SelectableDeck?) {
        deck?.let { deck -> viewModel.setSelectedDeck(deck) }
    }

    override var fragmented: Boolean
        get() = viewModel.isFragmented
        set(_) {
            throw UnsupportedOperationException()
        }

    lateinit var viewModel: CardBrowserViewModel

    private lateinit var binding: ActivityCardBrowserBinding

    lateinit var cardBrowserFragment: CardBrowserFragment

    private var actionBarTitle: TextView? = null

    private val searchView: CardBrowserSearchView?
        get() = cardBrowserFragment.legacySearchView

    lateinit var tagsDialogFactory: TagsDialogFactory
    private val searchItem: MenuItem? get() = cardBrowserFragment.searchItem
    private val mySearchesItem: MenuItem? get() = cardBrowserFragment.mySearchesItem

    // Dev option for Issue 18709
    // TODO: Broken currently; needs R.layout.activity_card_browser_searchview
    val useSearchView: Boolean
        get() = Prefs.devUsingCardBrowserSearchView

    // delegate the menu to the SearchBar in the fragment

    val menuHost: MenuHost?
        get() =
            if (useSearchView) {
                if (this::cardBrowserFragment.isInitialized) cardBrowserFragment else null
            } else {
                null
            }

    @Suppress("unused")
    @get:LayoutRes
    private val layout: Int
        get() = if (useSearchView) R.layout.activity_card_browser_searchview else R.layout.activity_card_browser

    private var onAddNoteActivityResult =
        registerForActivityResult(StartActivityForResult()) { result: ActivityResult ->
            Timber.d("onAddNoteActivityResult: resultCode=%d", result.resultCode)
            if (result.resultCode == RESULT_OK) {
                forceRefreshSearch(useSearchTextValue = true)
            }
        }

    init {
        ChangeManager.subscribe(this)
    }

    /**
     * Updates the browser's ui after a user search was saved based on the result of the operation.
     * @param saveSearchResult the result of the save search operation
     * @see [CardBrowser.registerSaveSearchHandler]
     */
    fun updateAfterUserSearchIsSaved(saveSearchResult: SaveSearchResult) {
        when (saveSearchResult) {
            SaveSearchResult.ALREADY_EXISTS ->
                showSnackbar(
                    R.string.card_browser_list_my_searches_new_search_error_dup,
                    Snackbar.LENGTH_SHORT,
                )
            SaveSearchResult.SUCCESS -> {
                searchView!!.setQuery("", false)
                mySearchesItem!!.isVisible = true
                showSnackbar(
                    R.string.card_browser_list_my_searches_successful_save,
                    Snackbar.LENGTH_SHORT,
                )
            }
        }
    }

    private val multiSelectOnBackPressedCallback =
        object : OnBackPressedCallback(enabled = false) {
            override fun handleOnBackPressed() {
                Timber.i("back pressed - exiting multiselect")
                viewModel.endMultiSelectMode(SingleSelectCause.NavigateBack)
            }
        }

    @MainThread
    @NeedsTest("search bar is set after selecting a saved search as first action")
    private fun searchForQuery(query: String) {
        // setQuery before expand does not set the view's value
        searchItem?.expandActionView()
        searchView?.setQuery(query, submit = true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        tagsDialogFactory = TagsDialogFactory(this).attachToActivity<TagsDialogFactory>(this)
        super.onCreate(savedInstanceState)
        binding = ActivityCardBrowserBinding.inflate(layoutInflater)
        if (!ensureStoragePermissions()) {
            return
        }

        // Set a simple RESULT_OK. The Reviewer or calling activity will now
        // rely on ChangeManager/OpChanges to detect if a refresh is needed.
        setResult(RESULT_OK)

        val launchOptions = intent?.toCardBrowserLaunchOptions() // must be called after super.onCreate()

        setViewBinding(binding)
        initNavigationDrawer(findViewById(android.R.id.content))

        /**
         * Check if noteEditorFrame is not null and if its visibility is set to VISIBLE.
         * If both conditions are true, assign true to the variable [fragmented], otherwise assign false.
         * [fragmented] will be true if the view size is large otherwise false
         */
        // TODO: Consider refactoring by storing noteEditorFrame and similar views in a sealed class (e.g., FragmentAccessor).
        val fragmented =
            Prefs.devIsCardBrowserFragmented &&
                !useSearchView &&
                binding.noteEditorFrame?.visibility == View.VISIBLE
        Timber.i("Using split Browser: %b", fragmented)

        if (fragmented) {
            ResizablePaneManager(
                parentLayout = requireNotNull(binding.cardBrowserXlView) { "cardBrowserXlView" },
                divider = requireNotNull(binding.cardBrowserResizingDivider) { "cardBrowserResizingDivider" },
                leftPane = requireNotNull(binding.cardBrowserFrame) { "cardBrowserFrame" },
                rightPane = requireNotNull(binding.noteEditorFrame) { "noteEditorFrame" },
                sharedPrefs = Prefs.getUiConfig(this),
                leftPaneWeightKey = PREF_CARD_BROWSER_PANE_WEIGHT,
                rightPaneWeightKey = PREF_NOTE_EDITOR_PANE_WEIGHT,
            )
        } else {
            binding.noteEditorFrame?.isVisible = false
            binding.cardBrowserResizingDivider?.isVisible = false
        }

        // must be called once we have an accessible collection
        viewModel = createViewModel(launchOptions, fragmented)

        cardBrowserFragment = supportFragmentManager.findFragmentById(R.id.card_browser_frame) as? CardBrowserFragment
            ?: CardBrowserFragment().also { fragment ->
                supportFragmentManager.commit {
                    replace(R.id.card_browser_frame, fragment)
                }
            }

        if (!useSearchView) {
            // initialize the lateinit variables
            // Load reference to action bar title
            actionBarTitle = findViewById(R.id.toolbar_title)
            // new deck selection is only available when the new search view is not used
            findViewById<LinearLayout>(R.id.toolbar_content).setOnClickListener { startDeckSelection(skipEmptyDefault = true) }
        }

        startLoadingCollection()

        setupFlows()
        setupNewSearchView()
        registerOnForgetHandler { viewModel.queryAllSelectedCardIds() }
        registerSaveSearchHandler()
        registerDeckSelectedHandler(action = ::onDeckSelected)
        registerFindReplaceHandler { result ->
            launchCatchingTask {
                withProgress {
                    val count =
                        withProgress {
                            viewModel.findAndReplace(result)
                        }.await()
                    showSnackbar(TR.browsingNotesUpdated(count))
                }
            }
        }
        registerSavedSearchActionHandler { type, searchName ->
            if (searchName == null) return@registerSavedSearchActionHandler
            when (type) {
                SavedBrowserSearchesDialogFragment.TYPE_SEARCH_SELECTED -> {
                    Timber.d("Selecting saved search selection named: %s", searchName)
                    launchCatchingTask {
                        val search = viewModel.savedSearches().find { it.name == searchName } ?: return@launchCatchingTask
                        Timber.d("OnSelection using search terms: %s", search.query)
                        searchForQuery(search.query)
                    }
                }
                SavedBrowserSearchesDialogFragment.TYPE_SEARCH_REMOVED -> {
                    Timber.d("Removing saved search named: %s", searchName)
                    launchCatchingTask {
                        val (_, updatedFilters) = viewModel.removeSavedSearch(searchName)
                        if (updatedFilters.isEmpty()) {
                            mySearchesItem!!.isVisible = false
                        }
                    }
                }
                else -> error("Unexpected saved search action: $type")
            }
        }
        setupMenuProvider()
    }

    fun setupMenuProvider() {
        // the drawerToggle has priority over other menu items
        addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(
                    menu: Menu,
                    menuInflater: MenuInflater,
                ) {
                    if (!viewModel.isInMultiSelectMode) {
                        restoreDrawerIcon()
                    } else {
                        showBackIcon()
                    }
                }

                override fun onMenuItemSelected(menuItem: MenuItem) = drawerToggle.onOptionsItemSelected(menuItem)
            },
        )

        // add a MenuProvider for multi-select
        addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(
                    menu: Menu,
                    menuInflater: MenuInflater,
                ) {
                }

                override fun onPrepareMenu(menu: Menu) {
                    if (!viewModel.isInMultiSelectMode) return

                    // set the number of selected rows
                    actionBarTitle?.text = String.format(LanguageUtil.getLocaleCompat(resources), "%d", viewModel.selectedRowCount())
                    findViewById<TextView>(R.id.deck_name)?.isVisible = !viewModel.hasSelectedAnyRows() && !viewModel.isInMultiSelectMode
                    findViewById<TextView>(R.id.subtitle)?.isVisible = !viewModel.hasSelectedAnyRows() && !viewModel.isInMultiSelectMode
                }

                override fun onMenuItemSelected(menuItem: MenuItem) = false
            },
        )

        // Update the menu for a fragmented state
        // Added last so all changes take priority
        onAllFragmentsLoaded {
            addPrepareMenuProvider { menu ->
                if (!fragmented) return@addPrepareMenuProvider

                /** Return the menu item with a particular identifier or `null` */
                operator fun Menu.get(id: Int): MenuItem? = this.findItem(id)

                // NoteEditorFragment: Remove save/preview note options if there are no notes
                if (viewModel.rowCount == 0) {
                    menu[R.id.action_save]?.isVisible = false
                    menu[R.id.action_preview]?.isVisible = false
                }

                menu[R.id.action_edit_note]?.isVisible = false

                // TODO: https://github.com/ankidroid/Anki-Android/issues/20206
                // This blocks a user from previewing all cards
                menu[R.id.action_preview_many]?.isVisible = false
            }
        }
    }

    private fun setupNewSearchView() {
        if (!useSearchView) return
        supportActionBar?.hide()
    }

    override fun setupBackPressedCallbacks() {
        onBackPressedDispatcher.addCallback(this, multiSelectOnBackPressedCallback)
        super.setupBackPressedCallbacks()
    }

    private fun showSaveChangesDialog(launcher: NoteEditorLauncher) {
        DiscardChangesDialog.showDialog(
            context = this,
            positiveButtonText = this.getString(R.string.save),
            negativeButtonText = this.getString(R.string.discard),
            // The neutral button allows the user to back out of the action,
            // e.g., if they accidentally triggered a navigation or card selection.
            neutralButtonText = this.getString(R.string.dialog_cancel),
            message = this.getString(R.string.save_changes_message),
            positiveMethod = {
                launchCatchingTask {
                    fragment?.saveNote()
                    loadNoteEditorFragment(launcher)
                }
            },
            negativeMethod = {
                loadNoteEditorFragment(launcher)
            },
            neutralMethod = {},
        )
    }

    private fun loadNoteEditorFragment(launcher: NoteEditorLauncher) {
        val noteEditorFragment = NoteEditorFragment.newInstance(launcher)
        supportFragmentManager.commit {
            replace(R.id.note_editor_frame, noteEditorFragment)
        }
        // Invalidate options menu so that note editor menu will show
        invalidateOptionsMenu()
    }

    /**
     * Retrieves the `NoteEditor` fragment if it is present in the fragment container
     */
    val fragment: NoteEditorFragment?
        get() = supportFragmentManager.findFragmentById(R.id.note_editor_frame) as? NoteEditorFragment

    @Suppress("UNUSED_PARAMETER")
    private fun setupFlows() {
        // provides a name for each flow receiver to improve stack traces

        fun onSearchQueryExpanded(searchQueryExpanded: Boolean) {
            Timber.d("query expansion changed: %b", searchQueryExpanded)
            if (searchQueryExpanded) {
                searchItem?.expandActionView()
            } else {
                searchItem?.collapseActionView()
                // invalidate options menu so that disappeared icons would appear again
                invalidateOptionsMenu()
            }
        }

        fun onSelectedRowsChanged(rows: Set<Any>) = invalidateOptionsMenu()

        fun onFilterQueryChanged(filterQuery: String) {
            // setQuery before expand does not set the view's value
            searchItem?.expandActionView()
            searchView?.setQuery(filterQuery, submit = false)
        }

        fun onDeckIdChanged(deckId: DeckId?) {
            if (useSearchView) return
            launchCatchingTask {
                findViewById<TextView>(R.id.deck_name)?.text =
                    when (deckId) {
                        null -> getString(R.string.card_browser_all_decks)
                        ALL_DECKS_ID -> getString(R.string.card_browser_all_decks)
                        else -> withCol { decks.getLegacy(deckId)?.name }
                    }
            }
        }

        fun onMultiSelectModeChanged(modeChange: ChangeMultiSelectMode) {
            if (modeChange.resultedInMultiSelect) {
                // Turn on Multi-Select Mode so that the user can select multiple cards at once.
                Timber.d("load multiselect mode")
                // show title and hide spinner
                actionBarTitle?.visibility = View.VISIBLE
                findViewById<TextView>(R.id.deck_name)?.isVisible = false
                findViewById<TextView>(R.id.subtitle)?.isVisible = false
                multiSelectOnBackPressedCallback.isEnabled = true
            } else {
                Timber.d("end multiselect mode")
                findViewById<TextView>(R.id.deck_name)?.isVisible = true
                findViewById<TextView>(R.id.subtitle)?.isVisible = true
                actionBarTitle?.visibility = View.GONE
                multiSelectOnBackPressedCallback.isEnabled = false
            }
            // reload the actionbar using the multi-select mode actionbar
            invalidateOptionsMenu()
        }

        fun searchStateChanged(searchState: SearchState) {
            Timber.d("search state: %s", searchState)

            when (searchState) {
                Initializing -> { }
                Searching -> {
                    if ("" != viewModel.searchTerms && searchView != null) {
                        searchView!!.setQuery(viewModel.searchTerms, false)
                        searchItem!!.expandActionView()
                    }
                }
                is SearchState.Completed -> {
                    findViewById<TextView>(R.id.subtitle)?.text = searchState.formatCardCount(resources)
                    // HACK: required now we use MenuProvider for searches
                    // this causes a very brief flicker, as we call `setQuery` to restore the menu state
                    searchView?.post { searchView?.clearFocus() }
                }
                is SearchState.Error -> {
                    showError(searchState.error, crashReportData = null)
                }
            }
        }

        fun onNoteEditorCommand(command: NoteEditorCommand) {
            launchCatchingTask {
                when (command) {
                    is NoteEditorCommand.LoadInPane -> {
                        binding.noteEditorFrame?.isVisible = true
                        if (fragment?.hasUnsavedChanges() == true) {
                            showSaveChangesDialog(command.launcher)
                        } else {
                            loadNoteEditorFragment(command.launcher)
                        }
                    }
                    is NoteEditorCommand.LaunchActivity -> {
                        startActivity(command.launcher.toIntent(this@CardBrowser))
                    }
                    NoteEditorCommand.HidePane -> {
                        binding.noteEditorFrame?.isVisible = false
                        invalidateOptionsMenu()
                    }
                }
            }
        }

        viewModel.flowOfSearchQueryExpanded.launchCollectionInLifecycleScope(::onSearchQueryExpanded)
        viewModel.flowOfSelectedRows.launchCollectionInLifecycleScope(::onSelectedRowsChanged)
        viewModel.flowOfFilterQuery.launchCollectionInLifecycleScope(::onFilterQueryChanged)
        viewModel.flowOfDeckId.launchCollectionInLifecycleScope(::onDeckIdChanged)
        viewModel.flowOfMultiSelectModeChanged.launchCollectionInLifecycleScope(::onMultiSelectModeChanged)
        viewModel.flowOfSearchState.launchCollectionInLifecycleScope(::searchStateChanged)
        viewModel.flowOfNoteEditorCommand.launchCollectionInLifecycleScope(::onNoteEditorCommand)
    }

    private fun hideKeyboard() {
        Timber.d("hideKeyboard()")
        searchView?.let { view ->
            view.clearFocus()
            view.hideKeyboard()
        }
    }

    // Finish initializing the activity after the collection has been correctly loaded
    override fun onCollectionLoaded(col: Collection) {
        super.onCollectionLoaded(col)
        Timber.d("onCollectionLoaded()")
        registerReceiver()

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
    }

    override fun onKeyUp(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean {
        if (cardBrowserFragment.onKeyUp(keyCode, event)) {
            return true
        }

        // This method is called even when the user is typing in the search text field.
        // So we must ensure that all shortcuts uses a modifier.
        // A shortcut without modifier would be triggered while the user types, which is not what we want.
        when (keyCode) {
            KeyEvent.KEYCODE_E -> {
                if (event.isCtrlPressed) {
                    Timber.i("Ctrl+E: Add Note")
                    launchCatchingTask { addNoteFromCardBrowser() }
                    return true
                }
            }
            KeyEvent.KEYCODE_F -> {
                if (event.isCtrlPressed) {
                    Timber.i("Ctrl+F - Find notes")
                    searchItem?.expandActionView()
                    return true
                }
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onPause() {
        super.onPause()
        // If the user entered something into the search, but didn't press "search", clear this.
        // It's confusing if the bar is shown with a query that does not relate to the data on the screen
        viewModel.removeUnsubmittedInput()
    }

    override fun onResume() {
        super.onResume()
        selectNavigationItem(R.id.nav_browser)
        searchView?.post {
            hideKeyboard()
        }
    }

    override fun onNavigationPressed() {
        if (viewModel.isInMultiSelectMode) {
            viewModel.endMultiSelectMode(SingleSelectCause.NavigateBack)
        } else {
            super.onNavigationPressed()
        }
    }

    fun addNoteFromCardBrowser() {
        onAddNoteActivityResult.launch(addNoteLauncher.toIntent(this))
    }

    public override fun onSaveInstanceState(outState: Bundle) {
        // Save current search terms
        outState.putString("mSearchTerms", viewModel.searchTerms)
        super.onSaveInstanceState(outState)
    }

    public override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        viewModel.onReinit()
        viewModel.setQuery(
            savedInstanceState.getString("mSearchTerms", ""),
            forceRefresh = false,
        )
    }

    private fun forceRefreshSearch(useSearchTextValue: Boolean = false) {
        if (useSearchTextValue && searchView != null) {
            viewModel.setQuery(searchView!!.query.toString())
        } else {
            viewModel.launchSearchForCards()
        }
    }

    @RustCleanup("this isn't how Desktop Anki does it")
    override fun onSelectedTags(
        selectedTags: List<String>,
        indeterminateTags: List<String>,
        stateFilter: CardStateFilter,
    ) {
        cardBrowserFragment.onSelectedTags(selectedTags, indeterminateTags, stateFilter)
    }

    private fun refreshBrowserUI() {
        hideProgressBar()
        // reload whole view
        forceRefreshSearch()
        viewModel.endMultiSelectMode(SingleSelectCause.Other)
        invalidateOptionsMenu() // maybe the availability of undo changed
    }

    // all we need to do is select all decks
    fun searchAllDecks() = viewModel.setSelectedDeck(SelectableDeck.AllDecks)

    /**
     * Returns the current deck name, "All Decks" if all decks are selected, or "Unknown"
     * Do not use this for any business logic, as this will return inconsistent data
     * with the collection.
     */
    val selectedDeckNameForUi: String
        get() =
            try {
                when (val deckId = viewModel.lastDeckId) {
                    null -> getString(R.string.card_browser_unknown_deck_name)
                    ALL_DECKS_ID -> getString(R.string.card_browser_all_decks)
                    else -> getColUnsafe.decks.name(deckId)
                }
            } catch (e: Exception) {
                Timber.w(e, "Unable to get selected deck name")
                getString(R.string.card_browser_unknown_deck_name)
            }

    /**
     * Implementation of `by viewModels()` for use in [onCreate]
     *
     * @see showedActivityFailedScreen - we may not have AnkiDroidApp.instance and therefore can't
     * create the ViewModel
     *
     * @param fragmented True if `noteEditorFrame` is non-null (x-large displays)
     */
    private fun createViewModel(
        launchOptions: CardBrowserLaunchOptions?,
        fragmented: Boolean,
    ) = ViewModelProvider(
        viewModelStore,
        CardBrowserViewModel.factory(
            lastDeckIdRepository = AnkiDroidApp.instance.sharedPrefsLastDeckIdRepository,
            cacheDir = cacheDir,
            options = launchOptions,
            isFragmented = fragmented,
        ),
        defaultViewModelCreationExtras,
    )[CardBrowserViewModel::class.java]

    override fun opExecuted(
        changes: OpChanges,
        handler: Any?,
    ) {
        if (handler === this || handler === viewModel) {
            return
        }

        if (changes.browserSidebar ||
            changes.browserTable ||
            changes.noteText ||
            changes.card
        ) {
            // We refresh the Browser's own UI
            refreshBrowserUI()
        }
    }

    override val shortcuts
        get() = cardBrowserFragment.shortcuts

    // region MenuHost delegation

    // This only supports delegation of menus defined using MenuProvider, not `onCreateOptionsMenu`
    //
    // When delegating a MenuHost to a fragment, the fragment is attached after `super.onCreate`
    // of the activity.
    //
    // As the activity calls `addMenuProvider` inside `super.onCreate()`, the fragment would not be
    //  initialized
    //
    // Calls to activity.addMenuProvider are done after the fragment is initialized
    // so this delegation works as long as the activity is using `onCreateOptionsMenu`

    override fun addMenuProvider(provider: MenuProvider) {
        menuHost?.addMenuProvider(provider) ?: super.addMenuProvider(provider)
    }

    override fun addMenuProvider(
        provider: MenuProvider,
        owner: LifecycleOwner,
    ) {
        menuHost?.addMenuProvider(provider, owner) ?: super.addMenuProvider(provider, owner)
    }

    override fun addMenuProvider(
        provider: MenuProvider,
        owner: LifecycleOwner,
        state: Lifecycle.State,
    ) {
        menuHost?.addMenuProvider(provider, owner, state) ?: super.addMenuProvider(provider, owner, state)
    }

    override fun removeMenuProvider(provider: MenuProvider) {
        menuHost?.removeMenuProvider(provider) ?: super.removeMenuProvider(provider)
    }

    override fun invalidateMenu() {
        menuHost?.invalidateMenu() ?: super.invalidateMenu()
    }

    override fun invalidateOptionsMenu() {
        super.invalidateOptionsMenu()
        menuHost?.invalidateMenu()
    }

    // endregion

    companion object {
        // Keys for saving pane weights in SharedPreferences
        private const val PREF_CARD_BROWSER_PANE_WEIGHT = "cardBrowserPaneWeight"
        private const val PREF_NOTE_EDITOR_PANE_WEIGHT = "noteEditorPaneWeight"

        // Values related to persistent state data
        fun clearLastDeckId() = SharedPreferencesLastDeckIdRepository.clearLastDeckId()

        @VisibleForTesting
        fun createAddNoteLauncher(viewModel: CardBrowserViewModel): NoteEditorLauncher =
            NoteEditorLauncher.AddNoteFromCardBrowser(viewModel)
    }
}

suspend fun searchForRows(
    search: SearchString,
    order: SortOrder,
    cardsOrNotes: CardsOrNotes,
): BrowserRowCollection =
    withCol {
        when (cardsOrNotes) {
            CARDS -> findCards(search, order)
            NOTES -> findNotes(search, order)
        }
    }.let { ids ->
        BrowserRowCollection(cardsOrNotes, ids.map { CardOrNoteId(it) }.toMutableList())
    }

/** Renders the row count for [this] event as a localized "X cards/notes" string. */
fun SearchState.Completed.formatCardCount(resources: android.content.res.Resources): String =
    resources.getQuantityString(
        if (cardsOrNotes == CARDS) R.plurals.card_browser_subtitle else R.plurals.card_browser_subtitle_notes_mode,
        rowCount,
        rowCount,
    )

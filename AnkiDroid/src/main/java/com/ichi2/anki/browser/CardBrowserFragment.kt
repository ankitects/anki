/*
 *  Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.browser

import android.content.DialogInterface
import android.graphics.Color
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.SubMenu
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.annotation.CheckResult
import androidx.annotation.LayoutRes
import androidx.annotation.VisibleForTesting
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.ThemeUtils
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.MenuHost
import androidx.core.view.MenuHostHelper
import androidx.core.view.MenuProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import anki.collection.OpChanges
import com.google.android.material.chip.Chip
import com.google.android.material.color.MaterialColors
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.search.SearchBar
import com.google.android.material.search.SearchView
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.ALL_DECKS_ID
import com.ichi2.anki.AnkiActivityProvider
import com.ichi2.anki.CardBrowser
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.getColUnsafe
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.Flag
import com.ichi2.anki.R
import com.ichi2.anki.android.input.ShortcutGroup
import com.ichi2.anki.android.input.shortcut
import com.ichi2.anki.android.menu.SearchBarMenuHost
import com.ichi2.anki.browser.CardBrowserViewModel.ChangeMultiSelectMode
import com.ichi2.anki.browser.CardBrowserViewModel.ChangeMultiSelectMode.MultiSelectCause
import com.ichi2.anki.browser.CardBrowserViewModel.ChangeMultiSelectMode.SingleSelectCause
import com.ichi2.anki.browser.CardBrowserViewModel.RowSelection
import com.ichi2.anki.browser.CardBrowserViewModel.SearchState
import com.ichi2.anki.browser.CardBrowserViewModel.SearchState.Initializing
import com.ichi2.anki.browser.CardBrowserViewModel.SearchState.Searching
import com.ichi2.anki.browser.CardBrowserViewModel.ToggleSelectionState
import com.ichi2.anki.browser.CardBrowserViewModel.ToggleSelectionState.SELECT_ALL
import com.ichi2.anki.browser.CardBrowserViewModel.ToggleSelectionState.SELECT_NONE
import com.ichi2.anki.browser.RepositionCardFragment.Companion.REQUEST_REPOSITION_NEW_CARDS
import com.ichi2.anki.browser.RepositionCardsRequest.NoRepositionableCardsError
import com.ichi2.anki.browser.RepositionCardsRequest.RepositionData
import com.ichi2.anki.browser.search.AdvancedSearchFragment
import com.ichi2.anki.browser.search.CardBrowserSearchViewModel
import com.ichi2.anki.browser.search.CardBrowserSearchViewModel.UserMessage
import com.ichi2.anki.browser.search.CardStateBottomSheetFragment
import com.ichi2.anki.browser.search.FlagsBottomSheetFragment
import com.ichi2.anki.browser.search.SearchRequest
import com.ichi2.anki.browser.search.SearchString
import com.ichi2.anki.browser.search.StandardSearchFragment
import com.ichi2.anki.browser.search.formatChipDescription
import com.ichi2.anki.browser.search.iconRes
import com.ichi2.anki.browser.search.savedFilters
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.common.utils.annotation.KotlinCleanup
import com.ichi2.anki.dialogs.BrowserOptionsDialog
import com.ichi2.anki.dialogs.CardBrowserOrderDialog
import com.ichi2.anki.dialogs.DeckSelectionDialog
import com.ichi2.anki.dialogs.DeckSelectionDialog.DeckSelectionListener
import com.ichi2.anki.dialogs.SimpleMessageDialog
import com.ichi2.anki.dialogs.tags.TagsDialog
import com.ichi2.anki.dialogs.tags.TagsDialogFactory
import com.ichi2.anki.dialogs.tags.TagsDialogListener
import com.ichi2.anki.export.ExportDialogFragment
import com.ichi2.anki.filtered.FilteredDeckOptionsFragment
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.undoAvailable
import com.ichi2.anki.libanki.undoLabel
import com.ichi2.anki.model.CardStateFilter
import com.ichi2.anki.model.CardsOrNotes.CARDS
import com.ichi2.anki.model.LegacySortType
import com.ichi2.anki.model.SelectableDeck
import com.ichi2.anki.observability.ChangeManager
import com.ichi2.anki.observability.undoableOp
import com.ichi2.anki.requireAnkiActivity
import com.ichi2.anki.requireNavigationDrawerActivity
import com.ichi2.anki.scheduling.ForgetCardsDialog
import com.ichi2.anki.scheduling.SetDueDateDialog
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.ui.attachFastScroller
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.anki.ui.internationalization.toSentenceCase
import com.ichi2.anki.undoAndShowSnackbar
import com.ichi2.anki.utils.ext.addPrepareMenuProvider
import com.ichi2.anki.utils.ext.getCurrentDialogFragment
import com.ichi2.anki.utils.ext.hasCheckedBackground
import com.ichi2.anki.utils.ext.ifNotZero
import com.ichi2.anki.utils.ext.launchCollectionInLifecycleScope
import com.ichi2.anki.utils.ext.setFragmentResultListener
import com.ichi2.anki.utils.ext.sharedPrefs
import com.ichi2.anki.utils.ext.showDialogFragment
import com.ichi2.anki.utils.ext.visibleItemPositions
import com.ichi2.anki.utils.hideKeyboard
import com.ichi2.anki.utils.showDialogFragmentImpl
import com.ichi2.anki.withProgress
import com.ichi2.ui.CardBrowserSearchView
import com.ichi2.utils.TagsUtil.getUpdatedTags
import com.ichi2.utils.increaseHorizontalPaddingOfOverflowMenuIcons
import com.ichi2.utils.moveCursorToEnd
import com.ichi2.utils.replaceText
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import net.ankiweb.rsdroid.Translations
import timber.log.Timber

// Minor BUG: 'don't keep activities' and huge selection
// At some point, starting between 35k and 60k selections, the scroll position is lost on recreation
// This occurred on a Pixel 9 Pro, Android 15
class CardBrowserFragment :
    Fragment(),
    AnkiActivityProvider,
    ChangeManager.Subscriber,
    TagsDialogListener,
    SearchBarMenuHost {
    val activityViewModel: CardBrowserViewModel by activityViewModels()
    val viewModel: CardBrowserFragmentViewModel by viewModels()
    val searchViewModel: CardBrowserSearchViewModel by activityViewModels()

    override val ankiActivity: CardBrowser
        get() = requireAnkiActivity() as CardBrowser

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    lateinit var cardsAdapter: BrowserMultiColumnAdapter

    @VisibleForTesting
    lateinit var cardsListView: RecyclerView

    /** LayoutManager for [cardsListView] */
    val layoutManager: LinearLayoutManager
        get() = cardsListView.layoutManager as LinearLayoutManager

    @VisibleForTesting
    lateinit var browserColumnHeadings: ViewGroup

    lateinit var toggleRowSelections: ImageButton

    private lateinit var progressIndicator: LinearProgressIndicator

    // DEFECT: Doesn't need to be a local
    private var tagsDialogListenerAction: TagsDialogListenerAction? = null
    private val tagsDialogFactory: TagsDialogFactory
        get() = ankiActivity.tagsDialogFactory

    private var undoSnackbar: Snackbar? = null

    // Dev option for Issue 18709
    private val useSearchView: Boolean
        get() = requireCardBrowserActivity().useSearchView

    // only usable if 'useSearchView' is set
    override var searchBar: SearchBar? = null

    @VisibleForTesting
    internal var searchView: SearchView? = null
    private var decksChip: Chip? = null
    private var tagsChip: Chip? = null
    private val useNewTaggingLogic get() = tagsChip != null
    private var cardStateChip: Chip? = null
    private var flagsChip: Chip? = null
    private var sortChip: Chip? = null

    // region legacy menu handling
    var mySearchesItem: MenuItem? = null
    var searchItem: MenuItem? = null
    var legacySearchView: CardBrowserSearchView? = null
    private var saveSearchItem: MenuItem? = null
    // endregion

    private var toggleAdvancedSearch: Button? = null

    // region SearchBarMenuHost
    override val menuInflater: MenuInflater? get() = activity?.menuInflater
    override val menuHostHelper = MenuHostHelper { invalidateSearchBarMenu() }
    // endregion

    @get:LayoutRes
    private val layout: Int
        get() = if (useSearchView) R.layout.fragment_card_browser_searchview else R.layout.fragment_card_browser

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? = inflater.inflate(layout, container, false)

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        // Selected cards aren't restored on activity recreation,
        // so it is necessary to dismiss the change deck dialog
        getCurrentDialogFragment<DeckSelectionDialog>()?.let { dialogFragment ->
            if (dialogFragment.requireArguments().getBoolean(CHANGE_DECK_KEY, false)) {
                Timber.d("onCreate(): Change deck dialog dismissed")
                dialogFragment.dismiss()
            }
        }

        cardsListView =
            view.findViewById<RecyclerView>(R.id.card_browser_list).apply {
                attachFastScroller(R.id.browser_scroller)
            }
        DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL).apply {
            setDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.browser_divider)!!)
            cardsListView.addItemDecoration(this)
        }
        cardsAdapter =
            BrowserMultiColumnAdapter(
                requireContext(),
                activityViewModel,
                onTap = ::onTap,
                onLongPress = { rowId ->
                    activityViewModel.handleRowLongPress(rowId.toRowSelection())
                },
                onRightClick = { rowId ->
                    activityViewModel.handleRightClick(rowId.toRowSelection())
                },
            )
        cardsListView.adapter = cardsAdapter
        cardsAdapter.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
        val layoutManager = LinearLayoutManager(requireContext())
        cardsListView.layoutManager = layoutManager
        cardsListView.addItemDecoration(DividerItemDecoration(requireContext(), layoutManager.orientation))

        browserColumnHeadings = view.findViewById(R.id.browser_column_headings)
        toggleRowSelections =
            view.findViewById<ImageButton>(R.id.toggle_row_selections).apply {
                setOnClickListener { activityViewModel.toggleSelectAllOrNone() }
            }

        progressIndicator = view.findViewById(R.id.browser_progress)

        decksChip =
            view.findViewById<Chip>(R.id.decks_chip)?.apply {
                setOnClickListener { viewModel.openDeckSelectionDialog() }
            }
        tagsChip =
            view.findViewById<Chip>(R.id.tags_chip)?.apply {
                setOnClickListener { showFilterByTagsDialog() }
            }
        cardStateChip =
            view.findViewById<Chip>(R.id.card_state_chip)?.apply {
                setOnClickListener {
                    val fragment = CardStateBottomSheetFragment()
                    fragment.show(childFragmentManager, CardStateBottomSheetFragment.TAG)
                }
            }
        flagsChip =
            view.findViewById<Chip>(R.id.flags_chip)?.apply {
                setOnClickListener {
                    launchCatchingTask {
                        FlagsBottomSheetFragment.createInstance().show(childFragmentManager)
                    }
                }
            }
        sortChip =
            view.findViewById<Chip>(R.id.sort_chip)?.apply {
                setOnClickListener { changeDisplayOrder() }
            }
        searchBar =
            view.findViewById<SearchBar>(R.id.search_bar)?.apply {
                setNavigationOnClickListener {
                    requireNavigationDrawerActivity().onNavigationPressed()
                }
            }

        fun FragmentTransaction.removeByTag(tag: String): FragmentTransaction {
            val fragment = childFragmentManager.findFragmentByTag(tag) ?: return this
            return remove(fragment)
        }

        searchView =
            view.findViewById<SearchView>(R.id.search_view)?.apply {
                editText.doAfterTextChanged { searchViewModel.onSearchTextChanged(it.toString()) }
                addTransitionListener { _, _, state ->
                    if (state == SearchView.TransitionState.SHOWING) {
                        getOrCreateSearchFragment(StandardSearchFragment.TAG, ::StandardSearchFragment)
                        searchViewModel.isScreenOpenFlow.value = true
                        return@addTransitionListener
                    }
                    if (state == SearchView.TransitionState.SHOWN) {
                        editText.setText(activityViewModel.searchRequestFlow.value.query)
                        editText.moveCursorToEnd()
                        return@addTransitionListener
                    }

                    if (state != SearchView.TransitionState.HIDDEN) return@addTransitionListener
                    // clear state on hide
                    childFragmentManager
                        .commit {
                            removeByTag(StandardSearchFragment.TAG)
                            removeByTag(AdvancedSearchFragment.TAG)
                        }

                    // Exiting out the SearchView should reset the state
                    // The ViewModel remains active as it's tied to the host fragment.
                    searchViewModel.resetSearchState(activityViewModel.searchRequestFlow.value)
                }
                editText.setOnEditorActionListener { _, actionId, event ->
                    if (actionId == EditorInfo.IME_ACTION_SEARCH || event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                        searchViewModel.submitCurrentSearch()
                        true
                    } else {
                        false
                    }
                }
            }
        toggleAdvancedSearch =
            view.findViewById<Button>(R.id.toggle_advanced_search)?.apply {
                setOnClickListener {
                    searchViewModel.toggleAdvancedSearch()
                }
            }

        setupFlows()

        setupFragmentResultListeners()

        setupMenu()
    }

    private fun setupMenu() {
        val menuHost: MenuHost = requireCardBrowserActivity()

        fun MenuItem.setupUndo() {
            isVisible = getColUnsafe().undoAvailable()
            title = getColUnsafe().undoLabel()
        }

        fun SubMenu.setupFlags() {
            val flagGroupId = 1001
            val subMenu = this
            lifecycleScope.launch {
                for ((flag, displayName) in Flag.queryDisplayNames()) {
                    val item =
                        subMenu
                            .add(flagGroupId, flag.code, Menu.NONE, displayName)
                            .setIcon(flag.drawableRes)
                    if (flag == Flag.NONE) {
                        val color = ThemeUtils.getThemeAttrColor(requireContext(), android.R.attr.colorControlNormal)
                        item.icon?.mutate()?.setTint(color)
                    }
                }
            }
        }

        // add a MenuProvider for 'no selection'
        menuHost.addMenuProvider(
            object : MenuProvider {
                // fix lint issues due to line length
                val vm get() = activityViewModel

                fun isKeyboardVisible(view: View?): Boolean =
                    view?.let {
                        ViewCompat.getRootWindowInsets(it)?.isVisible(WindowInsetsCompat.Type.ime())
                    } ?: false

                override fun onCreateMenu(
                    menu: Menu,
                    menuInflater: MenuInflater,
                ) {
                    if (vm.isInMultiSelectMode) return
                    Timber.d("onCreateMenu()")
                    menuInflater.inflate(R.menu.card_browser, menu)
                    menu.findItem(R.id.action_search_by_flag).subMenu?.setupFlags()
                    // note: this menu item is available with and without a selection of items
                    menu.findItem(R.id.action_find_replace)?.title = TR.sentenceCase.findAndReplace

                    if (!useSearchView) {
                        searchItem = menu.findItem(R.id.action_search)
                        searchItem!!.setOnActionExpandListener(
                            object : MenuItem.OnActionExpandListener {
                                override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                                    vm.setSearchQueryExpanded(true)
                                    return true
                                }

                                override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                                    if (item.actionView == searchView) {
                                        if (isKeyboardVisible(searchView)) {
                                            Timber.d("keyboard is visible, hiding it")
                                            hideKeyboard()
                                            return false
                                        }
                                    }
                                    vm.setSearchQueryExpanded(false)
                                    // SearchView doesn't support empty queries so we always reset the search when collapsing
                                    legacySearchView!!.setQuery("", false)
                                    vm.setQuery("")
                                    return true
                                }
                            },
                        )
                        legacySearchView =
                            (searchItem!!.actionView as CardBrowserSearchView).apply {
                                queryHint = resources.getString(R.string.card_browser_search_hint)
                                setMaxWidth(Integer.MAX_VALUE)
                                setOnQueryTextListener(
                                    object :
                                        androidx.appcompat.widget.SearchView.OnQueryTextListener {
                                        override fun onQueryTextChange(newText: String): Boolean {
                                            if (this@apply.ignoreValueChange) {
                                                return true
                                            }
                                            vm.updateQueryText(newText)
                                            return true
                                        }

                                        override fun onQueryTextSubmit(query: String): Boolean {
                                            vm.setQuery(query)
                                            legacySearchView!!.clearFocus()
                                            return true
                                        }
                                    },
                                )
                            }
                        // Fixes #6500 - keep the search consistent if coming back from note editor
                        // Fixes #9010 - consistent search after drawer change calls invalidateOptionsMenu
                        if (!vm.tempSearchQuery.isNullOrEmpty() || vm.searchTerms.isNotEmpty()) {
                            searchItem!!.expandActionView() // This calls legacySearchView.setOnSearchClickListener
                            val toUse =
                                if (!vm.tempSearchQuery.isNullOrEmpty()) vm.tempSearchQuery else vm.searchTerms
                            legacySearchView!!.setQuery(toUse!!, false)
                        }
                        legacySearchView!!.setOnSearchClickListener {
                            // Provide SearchView with the previous search terms
                            legacySearchView!!.setQuery(vm.searchTerms, false)
                        }
                    }

                    saveSearchItem = menu.findItem(R.id.action_save_search)
                }

                override fun onPrepareMenu(menu: Menu) {
                    if (vm.isInMultiSelectMode) return

                    // qtMiscCreateFilteredDeck() contains QT Accelerators ('&') in Belarusian
                    menu.findItem(R.id.action_create_filtered_deck).title = getString(R.string.new_dynamic_deck)

                    saveSearchItem?.isVisible = legacySearchView?.query?.isNotEmpty() != false

                    mySearchesItem = menu.findItem(R.id.action_list_my_searches)
                    mySearchesItem!!.isVisible = getColUnsafe().config.savedFilters.isNotEmpty()

                    menu.findItem(R.id.action_select_all)?.isVisible =
                        vm.rowCount > 0 && vm.selectedRowCount() < vm.rowCount

                    menu.findItem(R.id.action_preview_many)?.isVisible =
                        vm.rowCount > 0

                    menu.findItem(R.id.action_undo).setupUndo()
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                    if (vm.isInMultiSelectMode) return false

                    prepareForUndoableOperation()

                    Flag.entries.find { it.ordinal == menuItem.itemId }?.let { flag ->
                        launchCatchingTask { vm.setFlagFilter(flag) }
                        return true
                    }

                    when (menuItem.itemId) {
                        R.id.action_add_note_from_card_browser -> {
                            requireCardBrowserActivity().addNoteFromCardBrowser()
                            return true
                        }
                        R.id.action_save_search -> {
                            vm.saveCurrentSearch()
                            return true
                        }
                        R.id.action_list_my_searches -> {
                            requireCardBrowserActivity().showSavedSearches()
                            return true
                        }
                        R.id.action_undo -> {
                            Timber.w("CardBrowser:: Undo pressed")
                            requireCardBrowserActivity().onUndo()
                            return true
                        }
                        R.id.action_preview_many -> {
                            requireCardBrowserActivity().onPreview()
                            return true
                        }
                        R.id.action_sort_by_size -> {
                            changeDisplayOrder()
                            return true
                        }
                        R.id.action_show_marked -> {
                            activityViewModel.searchForMarkedNotes()
                            return true
                        }
                        R.id.action_show_suspended -> {
                            activityViewModel.searchForSuspendedCards()
                            return true
                        }
                        R.id.action_search_by_tag -> {
                            showFilterByTagsDialog()
                            return true
                        }
                        R.id.action_select_all -> {
                            activityViewModel.selectAll()
                            return true
                        }
                        R.id.action_open_options -> {
                            showOptionsDialog()
                            return true
                        }
                        R.id.action_create_filtered_deck -> {
                            showFilteredDeckScreen()
                            return true
                        }
                        R.id.action_find_replace -> {
                            showFindAndReplaceDialog()
                            return true
                        }
                    }

                    return false
                }
            },
        )

        // partial 'multi-select' menu provider
        menuHost.addMenuProvider(
            object : MenuProvider {
                val vm get() = activityViewModel

                private fun canPerformCardInfo(): Boolean = vm.selectedRowCount() == 1

                private fun canPerformMultiSelectEditNote(): Boolean = vm.selectedRowCount() == 1

                override fun onCreateMenu(
                    menu: Menu,
                    menuInflater: MenuInflater,
                ) {
                    if (!activityViewModel.isInMultiSelectMode) return
                    menuInflater.inflate(R.menu.card_browser_multiselect, menu)
                    menu.findItem(R.id.action_flag).subMenu?.setupFlags()
                    requireContext().increaseHorizontalPaddingOfOverflowMenuIcons(menu)
                }

                override fun onPrepareMenu(menu: Menu) {
                    if (!vm.isInMultiSelectMode) return

                    menu.findItem(R.id.action_reschedule_cards).title = TR.sentenceCase.setDueDate
                    menu.findItem(R.id.action_grade_now).title = TR.sentenceCase.gradeNow

                    // note: this menu item is available with and without a selection of items
                    menu.findItem(R.id.action_find_replace)?.title = TR.sentenceCase.findAndReplace

                    menu.findItem(R.id.action_undo).setupUndo()

                    menu.findItem(R.id.action_flag).isVisible = vm.hasSelectedAnyRows()
                    menu.findItem(R.id.action_suspend_card).apply {
                        title = TR.sentenceCase.toggleSuspend
                        // TODO: I don't think this icon is necessary
                        setIcon(R.drawable.ic_suspend)
                        isVisible = vm.hasSelectedAnyRows()
                    }
                    menu.findItem(R.id.action_toggle_bury).apply {
                        title = TR.sentenceCase.toggleBury
                        isVisible = vm.hasSelectedAnyRows()
                    }
                    menu.findItem(R.id.action_mark_card).apply {
                        title = TR.browsingToggleMark()
                        setIcon(R.drawable.ic_star_border_white)
                        isVisible = vm.hasSelectedAnyRows()
                    }
                    menu.findItem(R.id.action_change_note_type).apply {
                        title = TR.sentenceCase.changeNoteType
                        isVisible = vm.hasSelectedAnyRows()
                    }
                    menu.findItem(R.id.action_change_deck).isVisible = vm.hasSelectedAnyRows()
                    menu.findItem(R.id.action_reposition_cards).isVisible = vm.hasSelectedAnyRows()
                    menu.findItem(R.id.action_grade_now).isVisible = vm.hasSelectedAnyRows()
                    menu.findItem(R.id.action_reschedule_cards).isVisible = vm.hasSelectedAnyRows()
                    menu.findItem(R.id.action_edit_tags).isVisible = vm.hasSelectedAnyRows()
                    menu.findItem(R.id.action_reset_cards_progress).isVisible = vm.hasSelectedAnyRows()

                    menu.findItem(R.id.action_export_selected).apply {
                        this.title =
                            if (vm.cardsOrNotes == CARDS) {
                                resources.getQuantityString(
                                    R.plurals.card_browser_export_cards,
                                    vm.selectedRowCount(),
                                )
                            } else {
                                resources.getQuantityString(
                                    R.plurals.card_browser_export_notes,
                                    vm.selectedRowCount(),
                                )
                            }
                        isVisible = vm.hasSelectedAnyRows()
                    }

                    menu.findItem(R.id.action_edit_note).isVisible = canPerformMultiSelectEditNote()
                    menu.findItem(R.id.action_view_card_info).isVisible = canPerformCardInfo()

                    val deleteNoteItem =
                        menu.findItem(R.id.action_delete_card).apply {
                            isVisible = vm.hasSelectedAnyRows()
                        }

                    launchCatchingTask {
                        deleteNoteItem.apply {
                            this.title =
                                resources.getQuantityString(
                                    R.plurals.card_browser_delete_notes,
                                    vm.selectedNoteCount(),
                                )
                        }
                    }
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                    if (!vm.isInMultiSelectMode) return false

                    Timber.d("CardBrowserFragment::onMenuItemSelected")
                    prepareForUndoableOperation()

                    Flag.entries.find { it.ordinal == menuItem.itemId }?.let { flag ->
                        updateFlagForSelectedRows(flag)
                        return true
                    }

                    when (menuItem.itemId) {
                        android.R.id.home -> {
                            vm.endMultiSelectMode(SingleSelectCause.NavigateBack)
                            return true
                        }
                        R.id.action_delete_card -> {
                            deleteSelectedNotes()
                            return true
                        }
                        R.id.action_mark_card -> {
                            toggleMark()
                            return true
                        }
                        R.id.action_suspend_card -> {
                            toggleSuspendCards()
                            return true
                        }
                        R.id.action_toggle_bury -> {
                            toggleBury()
                            return true
                        }
                        R.id.action_change_deck -> {
                            showChangeDeckDialog()
                            return true
                        }
                        R.id.action_reset_cards_progress -> {
                            Timber.i("CardBrowserFragment:: Reset progress button pressed")
                            onResetProgress()
                            return true
                        }
                        R.id.action_reschedule_cards -> {
                            Timber.i("CardBrowserFragment:: Reschedule button pressed")
                            rescheduleSelectedCards()
                            return true
                        }
                        R.id.action_reposition_cards -> {
                            repositionSelectedCards()
                            return true
                        }
                        R.id.action_edit_tags -> {
                            showEditTagsDialog()
                            return true
                        }
                        R.id.action_export_selected -> {
                            exportSelected()
                            return true
                        }
                        R.id.action_create_filtered_deck -> {
                            showFilteredDeckScreen()
                        }
                        R.id.action_find_replace -> {
                            showFindAndReplaceDialog()
                            return true
                        }
                        R.id.action_change_note_type -> {
                            Timber.i("Menu: Change note type")
                            vm.requestChangeNoteType()
                            return true
                        }
                        R.id.action_undo -> {
                            Timber.w("CardBrowser:: Undo pressed")
                            requireCardBrowserActivity().onUndo()
                            return true
                        }
                        R.id.action_preview_many -> {
                            requireCardBrowserActivity().onPreview()
                            return true
                        }
                        R.id.action_edit_note -> {
                            requireCardBrowserActivity().openNoteEditorForCurrentlySelectedNote()
                            return true
                        }
                        R.id.action_view_card_info -> {
                            requireCardBrowserActivity().displayCardInfo()
                            return true
                        }
                        R.id.action_grade_now -> {
                            Timber.i("CardBrowser:: Grade now button pressed")
                            requireCardBrowserActivity().openGradeNow()
                            return true
                        }
                    }
                    return false
                }
            },
            viewLifecycleOwner,
        )

        // searchview specific logic
        menuHost.addPrepareMenuProvider { menu ->
            if (!useSearchView) return@addPrepareMenuProvider

            // icons have been added for all 'unselected' items
            if (!activityViewModel.isInMultiSelectMode) {
                (menu as? MenuBuilder)?.setOptionalIconsVisible(true)
            }

            // reorder 'preview' to appear before 'add'
            val preview = menu.findItem(R.id.action_preview_many)
            if (activityViewModel.cards.size > 0) {
                preview?.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            }

            menu.findItem(R.id.action_search)?.isVisible = false
            menu.findItem(R.id.action_list_my_searches)?.isVisible = false
            menu.findItem(R.id.action_save_search)?.isVisible = false
            menu.findItem(R.id.action_search_by_tag)?.isVisible = false
            menu.findItem(R.id.action_show_marked)?.isVisible = false
            menu.findItem(R.id.action_show_suspended)?.isVisible = false
            menu.findItem(R.id.action_search_by_flag)?.isVisible = false
            menu.findItem(R.id.action_show_suspended)?.isVisible = false
            menu.findItem(R.id.action_sort_by_size)?.isVisible = false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (::cardsListView.isInitialized) {
            cardsListView.adapter = null
        }
    }

    @Suppress("UNUSED_PARAMETER", "unused")
    private fun setupFlows() {
        fun onIsTruncatedChanged(isTruncated: Boolean) = cardsAdapter.notifyDataSetChanged()

        fun cardsUpdatedChanged(unit: Unit) = cardsAdapter.notifyDataSetChanged()

        fun onColumnsChanged(columnCollection: BrowserColumnCollection) {
            Timber.d("columns changed")
            cardsAdapter.notifyDataSetChanged()
        }

        fun onMultiSelectModeChanged(modeChange: ChangeMultiSelectMode) {
            val inMultiSelect = modeChange.resultedInMultiSelect
            toggleRowSelections.isVisible = inMultiSelect

            // update adapter to remove check boxes
            cardsAdapter.notifyDataSetChanged()
            if (modeChange is SingleSelectCause.DeselectRow) {
                cardsAdapter.notifyDataSetChanged()
                autoScrollTo(modeChange.selection)
            } else if (modeChange is MultiSelectCause.RowSelected) {
                cardsAdapter.notifyDataSetChanged()
                autoScrollTo(modeChange.selection)
            } else if (modeChange is SingleSelectCause && !modeChange.previouslySelectedRowIds.isNullOrEmpty()) {
                // if any visible rows are selected, anchor on the first row

                // obtain the offset of the row before we call notifyDataSetChanged
                val rowPositionAndOffset =
                    try {
                        val visibleRowIds = layoutManager.visibleItemPositions.map { activityViewModel.getRowAtPosition(it) }
                        val firstVisibleRowId = visibleRowIds.firstOrNull { modeChange.previouslySelectedRowIds!!.contains(it) }
                        firstVisibleRowId?.let { firstVisibleRowId.toRowSelection() }
                    } catch (e: Exception) {
                        Timber.w(e)
                        null
                    }
                cardsAdapter.notifyDataSetChanged()
                rowPositionAndOffset?.let { autoScrollTo(it) }
            }
        }

        fun searchStateChanged(searchState: SearchState) {
            cardsAdapter.notifyDataSetChanged()
            progressIndicator.isVisible = searchState == Initializing || searchState == Searching
        }

        fun onSelectedRowsChanged(rows: Set<Any>) = cardsAdapter.notifyDataSetChanged()

        fun onCardsMarkedEvent(unit: Unit) {
            cardsAdapter.notifyDataSetChanged()
        }

        fun onColumnNamesChanged(columnCollection: List<ColumnHeading>) {
            Timber.d("column names changed")
            browserColumnHeadings.removeAllViews()

            val layoutInflater = LayoutInflater.from(browserColumnHeadings.context)
            for (column in columnCollection) {
                Timber.d("setting up column %s", column)
                val columnView = layoutInflater.inflate(R.layout.view_browser_column_heading, browserColumnHeadings, false) as TextView

                columnView.text = column.label

                // Attach click listener to open the selection dialog
                columnView.setOnClickListener {
                    Timber.d("Clicked column: ${column.label}")
                    showColumnSelectionDialog(column)
                }

                // Attach long press listener to open the manage column dialog
                columnView.setOnLongClickListener {
                    Timber.d("Long-pressed column: ${column.label}")
                    val dialog = BrowserColumnSelectionFragment.createInstance(activityViewModel.cardsOrNotes)
                    dialog.show(parentFragmentManager, null)
                    true
                }
                browserColumnHeadings.addView(columnView)
            }
        }

        fun onToggleSelectionStateUpdated(selectionState: ToggleSelectionState) {
            toggleRowSelections.setImageResource(
                when (selectionState) {
                    SELECT_ALL -> R.drawable.ic_select_all_white
                    SELECT_NONE -> R.drawable.ic_deselect_white
                },
            )
            toggleRowSelections.contentDescription =
                getString(
                    when (selectionState) {
                        SELECT_ALL -> R.string.card_browser_select_all
                        SELECT_NONE -> R.string.card_browser_select_none
                    },
                )
        }

        fun onSearchForDecks(decks: List<SelectableDeck>) {
            val dialog =
                DeckSelectionDialog.newInstance(
                    title = getString(R.string.search_deck),
                    summaryMessage = null,
                    keepRestoreDefaultButton = false,
                    decks = decks,
                )
            showDialogFragmentImpl(childFragmentManager, dialog)
        }

        fun advancedSearchChanged(inAdvancedSearch: Boolean) {
            toggleAdvancedSearch?.text = if (inAdvancedSearch) "Basic search" else "Advanced search"

            if (searchView == null) return

            val standard = getOrCreateSearchFragment(StandardSearchFragment.TAG, ::StandardSearchFragment)
            val advanced = getOrCreateSearchFragment(AdvancedSearchFragment.TAG, ::AdvancedSearchFragment)

            childFragmentManager.commit {
                hide(if (inAdvancedSearch) standard else advanced)
                show(if (inAdvancedSearch) advanced else standard)
            }
        }

        fun onSearchViewTextChanged(text: String) {
            searchView?.editText?.replaceText(text)
        }

        fun onCanSaveChanged(canSave: Boolean) {
            // while using the Material 2 SearchView, invalidating the menu causes a keyboard
            // flicker, so update the state directly.
            saveSearchItem?.isVisible = canSave
        }

        fun onSearchViewClosed(value: Unit) {
            searchView?.hide()
        }

        @NeedsTest("displayed filter")
        fun onSearchSubmitted(value: SearchRequest) {
            // WARN: This changes the text when the SearchView is open
            launchCatchingTask { searchBar?.setText(value.toUserSpannable()) }

            Timber.i("relaying submitted search to activity")
            activityViewModel.launchSearchForCards(value, forceRefresh = false)
        }

        fun onUserMessage(message: UserMessage) =
            when (message) {
                UserMessage.SEARCH_SAVED ->
                    showSnackbar(
                        R.string.card_browser_list_my_searches_successful_save,
                        Snackbar.LENGTH_SHORT,
                    )
                UserMessage.SAVED_SEARCH_DUPLICATE_ADDED ->
                    showSnackbar(
                        R.string.card_browser_list_my_searches_new_search_error_dup,
                        Snackbar.LENGTH_SHORT,
                    )
                UserMessage.SAVED_SEARCH_DELETED ->
                    showSnackbar(
                        R.string.card_browser_list_my_searches_successful_deleted,
                        Snackbar.LENGTH_SHORT,
                    )
                UserMessage.SAVED_SEARCH_NAME_DOES_NOT_EXIST ->
                    showSnackbar(
                        R.string.something_wrong,
                        Snackbar.LENGTH_SHORT,
                    )
            }

        fun onSearchRequestUpdated(search: SearchRequest) {
            Timber.d("syncing searchview state from chip updates")
            val filters = search.filters

            decksChip?.text = filters.decks.firstOrNull()?.name ?: getString(R.string.card_browser_all_decks)
            decksChip?.hasCheckedBackground = filters.decks.any()

            tagsChip?.text = formatChipDescription(filters.tags, emptyValue = "Tags")
            tagsChip?.hasCheckedBackground = filters.tags.any()

            cardStateChip?.text = formatChipDescription(filters.cardStates.map { it.label }, emptyValue = "Card state")
            cardStateChip?.chipIcon =
                ContextCompat.getDrawable(requireContext(), filters.cardStates.firstOrNull().iconRes)?.also {
                    if (filters.cardStates.isEmpty()) {
                        DrawableCompat.setTint(it, MaterialColors.getColor(requireContext(), androidx.appcompat.R.attr.colorPrimary, 0))
                    }
                }
            cardStateChip?.hasCheckedBackground = filters.cardStates.any()

            // Flags: Use the icon/checked state to explain what the filter is.
            flagsChip?.text =
                formatChipDescription(
                    entries = filters.flags,
                    singleValue = if (filters.flags.singleOrNull() == Flag.NONE) "No Flag" else TR.browsingFlag(),
                    nonSingleValue = TR.browsingSidebarFlags(),
                )
            flagsChip?.chipIcon =
                ContextCompat.getDrawable(requireContext(), filters.flags.firstOrNull().iconRes)?.also {
                    if (filters.flags.isEmpty()) {
                        DrawableCompat.setTint(it, MaterialColors.getColor(requireContext(), androidx.appcompat.R.attr.colorPrimary, 0))
                    }
                }
            flagsChip?.hasCheckedBackground = filters.flags.any()

            searchViewModel.syncState(search)
        }

        fun reverseDirectionChanged(direction: ReverseDirection) {
            sortChip?.scaleY = if (!direction.orderAsc) 1.0f else -1.0f
        }

        activityViewModel.reverseDirectionFlow.launchCollectionInLifecycleScope(::reverseDirectionChanged)
        activityViewModel.flowOfIsTruncated.launchCollectionInLifecycleScope(::onIsTruncatedChanged)
        activityViewModel.flowOfSelectedRows.launchCollectionInLifecycleScope(::onSelectedRowsChanged)
        activityViewModel.flowOfActiveColumns.launchCollectionInLifecycleScope(::onColumnsChanged)
        activityViewModel.flowOfCardsUpdated.launchCollectionInLifecycleScope(::cardsUpdatedChanged)
        activityViewModel.flowOfMultiSelectModeChanged.launchCollectionInLifecycleScope(::onMultiSelectModeChanged)
        activityViewModel.flowOfSearchState.launchCollectionInLifecycleScope(::searchStateChanged)
        activityViewModel.flowOfColumnHeadings.launchCollectionInLifecycleScope(::onColumnNamesChanged)
        activityViewModel.flowOfCardStateChanged.launchCollectionInLifecycleScope(::onCardsMarkedEvent)
        activityViewModel.flowOfToggleSelectionState.launchCollectionInLifecycleScope(::onToggleSelectionStateUpdated)
        viewModel.flowOfSearchForDecks.launchCollectionInLifecycleScope(::onSearchForDecks)
        activityViewModel.flowOfScrollRequest.launchCollectionInLifecycleScope(::autoScrollTo)
        activityViewModel.flowOfCanSearch.launchCollectionInLifecycleScope(::onCanSaveChanged)
        searchViewModel.advancedSearchFlow.launchCollectionInLifecycleScope(::advancedSearchChanged)
        searchViewModel.searchTextFlow.launchCollectionInLifecycleScope(::onSearchViewTextChanged)
        searchViewModel.closeSearchViewFlow.launchCollectionInLifecycleScope(::onSearchViewClosed)
        searchViewModel.submittedSearchFlow.filterNotNull().launchCollectionInLifecycleScope(::onSearchSubmitted)
        searchViewModel.userMessageFlow.filterNotNull().launchCollectionInLifecycleScope(::onUserMessage)
        activityViewModel.searchRequestFlow.launchCollectionInLifecycleScope(::onSearchRequestUpdated)
    }

    private fun setupFragmentResultListeners() {
        ankiActivity.setFragmentResultListener(REQUEST_REPOSITION_NEW_CARDS) { _, bundle ->
            repositionCardsNoValidation(
                position = bundle.getInt(RepositionCardFragment.ARG_POSITION),
                step = bundle.getInt(RepositionCardFragment.ARG_STEP),
                shuffle = bundle.getBoolean(RepositionCardFragment.ARG_RANDOM),
                shift = bundle.getBoolean(RepositionCardFragment.ARG_SHIFT),
            )
        }
    }

    override fun opExecuted(
        changes: OpChanges,
        handler: Any?,
    ) {
        // TODO: dismiss undoSnackbar if it would undo a new action
        if (handler === this || handler === activityViewModel) {
            return
        }

        if (changes.browserSidebar ||
            changes.browserTable ||
            changes.noteText ||
            changes.card
        ) {
            cardsAdapter.notifyDataSetChanged()
        }
    }

    fun onKeyUp(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean {
        // This method is called even when the user is typing in the search text field.
        // So we must ensure that all shortcuts uses a modifier.
        // A shortcut without modifier would be triggered while the user types, which is not what we want.
        when (keyCode) {
            KeyEvent.KEYCODE_A -> {
                if (event.isCtrlPressed && event.isShiftPressed) {
                    Timber.i("Ctrl+Shift+A - Show edit tags dialog")
                    showEditTagsDialog()
                    return true
                } else if (event.isCtrlPressed) {
                    Timber.i("Ctrl+A - Select All")
                    activityViewModel.selectAll()
                    return true
                }
            }
            KeyEvent.KEYCODE_E -> {
                if (event.isCtrlPressed && event.isShiftPressed) {
                    Timber.i("Ctrl+Shift+E: Export selected cards")
                    exportSelected()
                    return true
                }
            }
            KeyEvent.KEYCODE_D -> {
                if (event.isCtrlPressed) {
                    Timber.i("Ctrl+D: Change Deck")
                    showChangeDeckDialog()
                    return true
                }
            }
            KeyEvent.KEYCODE_K -> {
                if (event.isCtrlPressed) {
                    Timber.i("Ctrl+K: Toggle Mark")
                    toggleMark()
                    return true
                }
            }
            KeyEvent.KEYCODE_R -> {
                if (event.isCtrlPressed && event.isAltPressed) {
                    Timber.i("Ctrl+Alt+R - Reschedule")
                    rescheduleSelectedCards()
                    return true
                }
            }
            KeyEvent.KEYCODE_F -> {
                if (event.isCtrlPressed && event.isAltPressed) {
                    Timber.i("CTRL+ALT+F - Find and replace")
                    showFindAndReplaceDialog()
                    return true
                }
            }
            KeyEvent.KEYCODE_N -> {
                if (event.isCtrlPressed && event.isAltPressed) {
                    Timber.i("Ctrl+Alt+N: Reset card progress")
                    onResetProgress()
                    return true
                }
            }
            KeyEvent.KEYCODE_T -> {
                if (event.isCtrlPressed && event.isAltPressed) {
                    Timber.i("Ctrl+Alt+T: Toggle cards/notes")
                    showOptionsDialog()
                    return true
                } else if (event.isCtrlPressed) {
                    Timber.i("Ctrl+T: Show filter by tags dialog")
                    showFilterByTagsDialog()
                    return true
                }
            }
            KeyEvent.KEYCODE_S -> {
                if (event.isCtrlPressed && event.isShiftPressed) {
                    Timber.i("Ctrl+Shift+S: Reposition selected cards")
                    repositionSelectedCards()
                    return true
                    // Ctrl+Alt+S / Ctrl+S in the activity take priority
                } else if (!event.isCtrlPressed && event.isAltPressed) {
                    Timber.i("Alt+S: Show suspended cards")
                    activityViewModel.searchForSuspendedCards()
                    return true
                }
            }
            KeyEvent.KEYCODE_J -> {
                if (event.isCtrlPressed && event.isShiftPressed) {
                    Timber.i("Ctrl+Shift+J: Toggle bury cards")
                    toggleBury()
                    return true
                } else if (event.isCtrlPressed) {
                    Timber.i("Ctrl+J: Toggle suspended cards")
                    toggleSuspendCards()
                    return true
                }
            }
            KeyEvent.KEYCODE_O -> {
                if (event.isCtrlPressed) {
                    Timber.i("Ctrl+O: Show order dialog")
                    changeDisplayOrder()
                    return true
                }
            }
            KeyEvent.KEYCODE_M -> {
                if (event.isCtrlPressed) {
                    Timber.i("Ctrl+M: Search marked notes")
                    activityViewModel.searchForMarkedNotes()
                    return true
                }
            }
            KeyEvent.KEYCODE_ESCAPE -> {
                Timber.i("ESC: Select none")
                activityViewModel.selectNone()
                return true
            }
        }
        return false
    }

    private fun showColumnSelectionDialog(selectedColumn: ColumnHeading) {
        Timber.d("Fetching available columns for: ${selectedColumn.label}")

        // Prevent multiple dialogs from opening
        if (parentFragmentManager.findFragmentByTag(ColumnSelectionDialogFragment.TAG) != null) {
            Timber.d("ColumnSelectionDialog is already shown, ignoring duplicate click.")
            return
        }

        lifecycleScope.launch {
            val (_, availableColumns) = activityViewModel.previewColumnHeadings(activityViewModel.cardsOrNotes)

            if (availableColumns.isEmpty()) {
                Timber.w("No available columns to replace ${selectedColumn.label}")
                showSnackbar(R.string.no_columns_available)
                return@launch
            }

            val dialog = ColumnSelectionDialogFragment.newInstance(selectedColumn)
            dialog.show(parentFragmentManager, ColumnSelectionDialogFragment.TAG)
        }
    }

    // TODO: Move this to ViewModel and test
    @VisibleForTesting
    fun onTap(id: CardOrNoteId) =
        launchCatchingTask {
            activityViewModel.focusedRow = id
            if (activityViewModel.isInMultiSelectMode) {
                val wasSelected = activityViewModel.selectedRows.contains(id)
                activityViewModel.toggleRowSelection(id.toRowSelection())
                // Load NoteEditor on trailing side if card is selected
                if (wasSelected) {
                    activityViewModel.currentCardId = id.toCardId(activityViewModel.cardsOrNotes)
                    requireCardBrowserActivity().loadNoteEditorFragmentIfFragmented()
                }
            } else {
                val cardId = activityViewModel.queryDataForCardEdit(id)
                requireCardBrowserActivity().setNoteEditorCard(cardId)
            }
        }

    // TODO: This dialog should survive activity recreation
    fun showChangeDeckDialog() =
        launchCatchingTask {
            if (!activityViewModel.hasSelectedAnyRows()) {
                Timber.i("Not showing Change Deck - No Cards")
                return@launchCatchingTask
            }
            val selectableDecks =
                activityViewModel
                    .getAvailableDecks()
            val dialog = getChangeDeckDialog(selectableDecks)
            showDialogFragment(dialog)
        }

    /** All the notes of the selected cards will be marked
     * If one or more card is unmarked, all will be marked,
     * otherwise, they will be unmarked  */
    @NeedsTest("Test that the mark get toggled as expected for a list of selected cards")
    @VisibleForTesting
    fun toggleMark() =
        launchCatchingTask {
            withProgress { activityViewModel.toggleMark() }
        }

    fun toggleSuspendCards() = launchCatchingTask { withProgress { activityViewModel.toggleSuspendCards().join() } }

    /** @see CardBrowserViewModel.toggleBury */
    fun toggleBury() =
        launchCatchingTask {
            val result = withProgress { activityViewModel.toggleBury() } ?: return@launchCatchingTask
            // show a snackbar as there's currently no colored background for buried cards
            val message =
                when (result.wasBuried) {
                    true -> TR.studyingCardsBuried(result.count)
                    false -> resources.getQuantityString(R.plurals.unbury_cards_feedback, result.count, result.count)
                }
            showUndoSnackbar(message)
        }

    fun rescheduleSelectedCards() {
        if (!activityViewModel.hasSelectedAnyRows()) {
            Timber.i("Attempted reschedule - no cards selected")
            return
        }

        launchCatchingTask {
            val allCardIds = activityViewModel.queryAllSelectedCardIds()
            Timber.i(
                "Reschedule: mode=%s, selected rows=%d, cards=%d",
                activityViewModel.cardsOrNotes,
                activityViewModel.selectedRows.size,
                allCardIds.size,
            )
            showDialogFragment(SetDueDateDialog.newInstance(allCardIds))
        }
    }

    /** @see repositionCardsNoValidation */
    fun repositionSelectedCards(): Boolean {
        Timber.i("CardBrowser:: Reposition button pressed")
        launchCatchingTask {
            when (val repositionCardsResult = activityViewModel.prepareToRepositionCards()) {
                is NoRepositionableCardsError -> {
                    // No selected cards can be repositioned
                    showDialogFragment(
                        SimpleMessageDialog.newInstance(
                            title = getString(R.string.vague_error),
                            message = getString(R.string.reposition_card_not_new_error),
                            reload = false,
                        ),
                    )
                    return@launchCatchingTask
                }
                is RepositionData -> {
                    val top = repositionCardsResult.queueTop
                    val bottom = repositionCardsResult.queueBottom
                    if (top == null || bottom == null) {
                        showSnackbar(R.string.something_wrong)
                        return@launchCatchingTask
                    }
                    val repositionDialog =
                        RepositionCardFragment.newInstance(
                            queueTop = top,
                            queueBottom = bottom,
                            random = repositionCardsResult.random,
                            shift = repositionCardsResult.shift,
                        )
                    showDialogFragment(repositionDialog)
                }
            }
        }
        return true
    }

    fun deleteSelectedNotes() =
        launchCatchingTask {
            withProgress(R.string.deleting_selected_notes) {
                activityViewModel.deleteSelectedNotes()
            }.ifNotZero { noteCount ->
                val deletedMessage = resources.getQuantityString(R.plurals.card_browser_cards_deleted, noteCount, noteCount)
                showUndoSnackbar(deletedMessage)
            }
        }

    fun onResetProgress() {
        launchCatchingTask {
            val allCardIds = activityViewModel.queryAllSelectedCardIds()
            Timber.i(
                "Reset Progress: mode=%s, selected rows=%d, cards=%d",
                activityViewModel.cardsOrNotes,
                activityViewModel.selectedRows.size,
                allCardIds.size,
            )
        }
        showDialogFragment(ForgetCardsDialog())
    }

    fun exportSelected() {
        val (type, selectedIds) = activityViewModel.querySelectionExportData() ?: return
        ExportDialogFragment.newInstance(type, selectedIds).show(parentFragmentManager, "exportDialog")
    }

    fun showOptionsDialog() {
        val dialog = BrowserOptionsDialog.newInstance(activityViewModel.cardsOrNotes, activityViewModel.isTruncated)
        dialog.show(parentFragmentManager, "browserOptionsDialog")
    }

    fun showFilteredDeckScreen() {
        launchCatchingTask {
            withProgress {
                val currentSearch = activityViewModel.searchTerms
                // the desktop browser only adds a deck if it's directly selected while we select a
                // deck outside the search box. So we need to look at the current search string
                // and manually set a deck if one is not found
                val search =
                    if (currentSearch.isNotEmpty()) {
                        if (currentSearch.contains("deck:")) {
                            currentSearch
                        } else {
                            "${buildDeckNameSearch()} $currentSearch"
                        }
                    } else {
                        buildDeckNameSearch()
                    }
                val intent = FilteredDeckOptionsFragment.getIntent(requireContext(), search = search)
                startActivity(intent)
            }
        }
    }

    /**
     * Returns a search string for the current selected deck to be used for building a filtered
     * deck in the form of "deck:A". If the selected deck is "All Decks" then return a general deck
     * search string.
     */
    private suspend fun buildDeckNameSearch(): String? =
        if (activityViewModel.deckId == ALL_DECKS_ID) {
            "deck:_*"
        } else {
            activityViewModel.deckId?.let {
                // decks.name() returns 'no deck' if a deck doesn't exist for that did
                "\"deck:${withCol { decks.get(it, default = true)?.name }}\""
            }
        }

    fun changeDisplayOrder() {
        showDialogFragment(
            // TODO: move this into the ViewModel
            CardBrowserOrderDialog.newInstance { dialog: DialogInterface, which: Int ->
                dialog.dismiss()
                activityViewModel.changeCardOrder(LegacySortType.fromCardBrowserLabelIndex(which))
            },
        )
    }

    fun updateFlagForSelectedRows(flag: Flag) =
        launchCatchingTask {
            // list of cards with updated flags
            val updatedCardIds = withProgress { activityViewModel.updateSelectedCardsFlag(flag) }

            ankiActivity.onCardsUpdated(updatedCardIds)
        }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    fun filterByTag(vararg tags: String) {
        tagsDialogListenerAction = TagsDialogListenerAction.FILTER
        onSelectedTags(tags.toList(), emptyList(), CardStateFilter.ALL_CARDS)
        filterByTags(tags.toList(), CardStateFilter.ALL_CARDS)
    }

    fun showEditTagsDialog() {
        if (!activityViewModel.hasSelectedAnyRows()) {
            Timber.d("showEditTagsDialog: called with empty selection")
        }
        tagsDialogListenerAction = TagsDialogListenerAction.EDIT_TAGS
        lifecycleScope.launch {
            val noteIds = activityViewModel.queryAllSelectedNoteIds()
            val dialog =
                tagsDialogFactory.newTagsDialog().withArguments(
                    requireContext(),
                    type = TagsDialog.DialogType.EDIT_TAGS,
                    noteIds = noteIds,
                )
            showDialogFragment(dialog)
        }
    }

    fun showFilterByTagsDialog() {
        launchCatchingTask {
            tagsDialogListenerAction = TagsDialogListenerAction.FILTER
            val dialog =
                tagsDialogFactory.newTagsDialog().withArguments(
                    context = requireContext(),
                    type = TagsDialog.DialogType.FILTER_BY_TAG,
                    noteIds = emptyList(),
                    checkedTags =
                        if (useNewTaggingLogic) {
                            ArrayList(
                                activityViewModel.searchRequestFlow.value.filters.tags,
                            )
                        } else {
                            ArrayList()
                        },
                )
            showDialogFragment(dialog)
        }
    }

    override fun onSelectedTags(
        selectedTags: List<String>,
        indeterminateTags: List<String>,
        stateFilter: CardStateFilter,
    ) {
        when (tagsDialogListenerAction) {
            TagsDialogListenerAction.FILTER -> filterByTags(selectedTags, stateFilter)
            TagsDialogListenerAction.EDIT_TAGS ->
                launchCatchingTask {
                    editSelectedCardsTags(selectedTags, indeterminateTags)
                }
            else -> {}
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun showFindAndReplaceDialog() {
        lifecycleScope.launch {
            withProgress {
                val noteIds = activityViewModel.queryAllSelectedNoteIds()
                val fragment = FindAndReplaceDialogFragment.newInstance(requireContext(), noteIds)
                fragment.show(parentFragmentManager, FindAndReplaceDialogFragment.TAG)
            }
        }
    }

    @KotlinCleanup("DeckSelectionListener is almost certainly a bug - deck!!")
    @VisibleForTesting
    internal fun getChangeDeckDialog(selectableDecks: List<SelectableDeck>?): DeckSelectionDialog {
        val dialog =
            DeckSelectionDialog.newInstance(
                getString(R.string.move_all_to_deck),
                null,
                false,
                selectableDecks!!,
            )
        // Add change deck argument so the dialog can be dismissed
        // after activity recreation, since the selected cards will be gone with it
        dialog.requireArguments().putBoolean(CHANGE_DECK_KEY, true)
        dialog.deckSelectionListener =
            DeckSelectionListener { deck: SelectableDeck? ->
                require(deck is SelectableDeck.Deck) { "Expected non-null deck" }
                moveSelectedCardsToDeck(deck.deckId)
            }
        return dialog
    }

    /**
     * Change Deck
     * @param did Id of the deck
     */
    @VisibleForTesting
    internal fun moveSelectedCardsToDeck(did: DeckId): Job =
        launchCatchingTask {
            val changed = withProgress { activityViewModel.moveSelectedCardsToDeck(did).await() }
            showUndoSnackbar(TR.browsingCardsUpdated(changed.count))
        }

    @VisibleForTesting
    internal fun repositionCardsNoValidation(
        position: Int,
        step: Int,
        shuffle: Boolean,
        shift: Boolean,
    ) = launchCatchingTask {
        val count =
            withProgress {
                activityViewModel.repositionSelectedRows(
                    position = position,
                    step = step,
                    shuffle = shuffle,
                    shift = shift,
                )
            }
        showSnackbar(
            TR.browsingChangedNewPosition(count),
            Snackbar.LENGTH_SHORT,
        )
    }

    private fun showUndoSnackbar(message: CharSequence) {
        showSnackbar(message) {
            setAction(R.string.undo) { launchCatchingTask { undoAndShowSnackbar() } }
            undoSnackbar = this
        }
    }

    private fun calculateTopOffset(cardPosition: Int): Int {
        val firstVisiblePosition = layoutManager.findFirstVisibleItemPosition()
        val view = cardsListView.getChildAt(cardPosition - firstVisiblePosition)
        return view?.top ?: 0
    }

    private fun autoScrollTo(rowSelection: RowSelection) {
        val newPosition = activityViewModel.getPositionOfId(rowSelection.rowId) ?: return
        layoutManager.scrollToPositionWithOffset(newPosition, rowSelection.topOffset)
    }

    private fun CardOrNoteId.toRowSelection() =
        RowSelection(rowId = this, topOffset = calculateTopOffset(activityViewModel.getPositionOfId(this)!!))

    private fun requireCardBrowserActivity(): CardBrowser = requireActivity() as CardBrowser

    /**
     * Updates the tags of selected/checked notes and saves them to the disk
     * @param selectedTags list of checked tags
     * @param indeterminateTags a list of tags which can checked or unchecked, should be ignored if not expected
     * For more info on [selectedTags] and [indeterminateTags] see [com.ichi2.anki.dialogs.tags.TagsDialogListener.onSelectedTags]
     */
    private suspend fun editSelectedCardsTags(
        selectedTags: List<String>,
        indeterminateTags: List<String>,
    ) = withProgress {
        val selectedNoteIds = activityViewModel.queryAllSelectedNoteIds().distinct()
        undoableOp {
            val selectedNotes =
                selectedNoteIds
                    .map { noteId -> getNote(noteId) }
                    .onEach { note ->
                        val previousTags: List<String> = note.tags
                        val updatedTags = getUpdatedTags(previousTags, selectedTags, indeterminateTags)
                        note.setTagsFromStr(this@undoableOp, tags.join(updatedTags))
                    }
            updateNotes(selectedNotes)
        }
    }

    private fun filterByTags(
        selectedTags: List<String>,
        cardState: CardStateFilter,
    ) = launchCatchingTask {
        if (useNewTaggingLogic) {
            val updatedSearch =
                activityViewModel.searchRequestFlow.value.copy(
                    filters =
                        activityViewModel.searchRequestFlow.value.filters.copy(
                            tags = selectedTags,
                        ),
                )
            activityViewModel.launchSearchForCards(updatedSearch, forceRefresh = false)
        } else {
            activityViewModel.filterByTags(selectedTags, cardState)
        }
    }

    fun prepareForUndoableOperation() {
        // dismiss undo-snackbar if shown to avoid race condition
        // (when another operation will be performed on the model, it will undo the latest operation)
        val snackbar = undoSnackbar ?: return
        if (snackbar.isShown) {
            snackbar.dismiss()
        }
    }

    val shortcuts get() =
        ShortcutGroup(
            listOf(
                shortcut("Ctrl+Shift+A", R.string.edit_tags_dialog),
                shortcut("Ctrl+A", R.string.card_browser_select_all),
                shortcut("Ctrl+Shift+E", Translations::exportingExport),
                shortcut("Ctrl+E", R.string.menu_add_note),
                shortcut("E", R.string.cardeditor_title_edit_card),
                shortcut("Ctrl+D", R.string.card_browser_change_deck),
                shortcut("Ctrl+K", Translations::browsingToggleMark),
                shortcut("Ctrl+Alt+R", Translations::browsingReschedule),
                shortcut("DEL", R.string.delete_card_title),
                shortcut("Ctrl+Alt+N", R.string.reset_card_dialog_title),
                shortcut("Ctrl+Alt+T", R.string.toggle_cards_notes),
                shortcut("Ctrl+T", R.string.card_browser_search_by_tag),
                shortcut("Ctrl+Shift+S", Translations::actionsReposition),
                shortcut("Ctrl+Alt+S", R.string.card_browser_list_my_searches),
                shortcut("Ctrl+S", R.string.card_browser_list_my_searches_save),
                shortcut("Alt+S", R.string.card_browser_show_suspended),
                shortcut("Ctrl+Shift+G", Translations::actionsGradeNow),
                shortcut("Ctrl+Shift+J", Translations::browsingToggleBury),
                shortcut("Ctrl+J", Translations::browsingToggleSuspend),
                shortcut("Ctrl+Shift+I", Translations::actionsCardInfo),
                shortcut("Ctrl+O", R.string.show_order_dialog),
                shortcut("Ctrl+M", R.string.card_browser_show_marked),
                shortcut("Esc", R.string.card_browser_select_none),
                shortcut("Ctrl+1", R.string.gesture_flag_red),
                shortcut("Ctrl+2", R.string.gesture_flag_orange),
                shortcut("Ctrl+3", R.string.gesture_flag_green),
                shortcut("Ctrl+4", R.string.gesture_flag_blue),
                shortcut("Ctrl+5", R.string.gesture_flag_pink),
                shortcut("Ctrl+6", R.string.gesture_flag_turquoise),
                shortcut("Ctrl+7", R.string.gesture_flag_purple),
            ),
            R.string.card_browser_context_menu,
        )

    private enum class TagsDialogListenerAction {
        FILTER,
        EDIT_TAGS,
    }

    companion object {
        /**
         * Argument key to add on change deck dialog,
         * so it can be dismissed on activity recreation,
         * since the cards are unselected when this happens
         */
        private const val CHANGE_DECK_KEY = "CHANGE_DECK"
    }
}

/**
 * Updates the content of the [SearchView] to a provided fragment, retaining state.
 *
 * This method searches through child fragments by [tag]. [createFragment] is called if the fragment
 * is not found.
 */
private fun Fragment.getOrCreateSearchFragment(
    tag: String,
    createFragment: () -> Fragment,
): Fragment {
    val existing = childFragmentManager.findFragmentByTag(tag)
    if (existing != null) return existing

    val created = createFragment()
    childFragmentManager.commit {
        add(R.id.search_view_content_container, created, tag)
    }
    return created
}

@CheckResult
suspend fun SearchRequest.toUserSpannable(): Spannable {
    val text = withCol { this@toUserSpannable.toSearchString() }.getOrDefault(SearchString.EMPTY)
    return this.toUserSpannable(text)
}

@CheckResult
fun SearchRequest.toUserSpannable(searchString: SearchString): Spannable = buildUserSpannable(this.query, searchString)

@CheckResult
fun SearchHistory.SearchHistoryEntry.toUserSpannable(searchString: SearchString) = buildUserSpannable(this.query, searchString)

fun buildUserSpannable(
    query: String,
    searchString: SearchString,
): SpannableString {
    // TODO: handle theming, don't hardcode gray
    val spannable = SpannableString(searchString.value)

    // gray out the additional filters, as they're handled by chips
    if (searchString.value.startsWith(query)) {
        spannable.setSpan(
            ForegroundColorSpan(Color.GRAY),
            query.length,
            searchString.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
    }

    return spannable
}

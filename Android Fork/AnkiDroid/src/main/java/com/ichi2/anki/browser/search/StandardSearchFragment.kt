// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser.search

import android.content.Context
import android.os.Bundle
import android.text.SpannableString
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.color.MaterialColors
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CommonString
import com.ichi2.anki.Flag
import com.ichi2.anki.R
import com.ichi2.anki.browser.SearchHistory.SearchHistoryEntry
import com.ichi2.anki.browser.search.CardBrowserSearchViewModel.SearchHistoryItems
import com.ichi2.anki.browser.toUserSpannable
import com.ichi2.anki.common.utils.ellipsize
import com.ichi2.anki.databinding.FragmentStandardSearchBinding
import com.ichi2.anki.databinding.ViewSavedSearchItemBinding
import com.ichi2.anki.databinding.ViewSearchHistoryItemBinding
import com.ichi2.anki.dialogs.ManageSavedSearchAction
import com.ichi2.anki.dialogs.SaveBrowserSearchDialogFragment
import com.ichi2.anki.dialogs.SavedBrowserSearchesDialogFragment
import com.ichi2.anki.dialogs.registerChildDeckSelectedHandler
import com.ichi2.anki.dialogs.registerSaveSearchHandler
import com.ichi2.anki.dialogs.registerSavedSearchActionHandler
import com.ichi2.anki.dialogs.startDeckSelection
import com.ichi2.anki.dialogs.tags.TagsDialog
import com.ichi2.anki.dialogs.tags.TagsDialogFactory
import com.ichi2.anki.dialogs.tags.TagsDialogListener
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.libanki.DeckNameId
import com.ichi2.anki.model.CardStateFilter
import com.ichi2.anki.model.SelectableDeck
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.anki.utils.ext.alignWithFirstLineOf
import com.ichi2.anki.utils.ext.collectInLifecycle
import com.ichi2.anki.utils.ext.collectInViewLifecycle
import com.ichi2.anki.utils.ext.hasCheckedBackground
import dev.androidbroadcast.vbpd.viewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Search overlay with shared filter chips above the Basic search or Search tools content. */
class StandardSearchFragment :
    Fragment(R.layout.fragment_standard_search),
    TagsDialogListener {
    @VisibleForTesting
    val binding by viewBinding(FragmentStandardSearchBinding::bind)

    @VisibleForTesting
    val viewModel: CardBrowserSearchViewModel by activityViewModels()

    private lateinit var tagsDialogFactory: TagsDialogFactory

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tagsDialogFactory = TagsDialogFactory(this).attachToActivity<TagsDialogFactory>(requireActivity())

        registerSaveSearchHandler {
            viewModel.addSavedSearch(it)
        }
        registerSavedSearchActionHandler {
            when (it) {
                is ManageSavedSearchAction.SelectSearch -> {
                    viewModel.submitSavedSearch(it.search)
                }
                is ManageSavedSearchAction.Delete -> {
                    viewModel.deleteSavedSearch(it.search)
                }
            }
        }
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        binding.toggleAdvancedSearch.apply {
            isVisible = Prefs.devUsingCardBrowserSearchTools
            setOnClickListener { viewModel.toggleAdvancedSearch() }
        }

        registerChildDeckSelectedHandler(action = ::onDeckSelected)

        setupChips()
        setupSearchHistory()
        setupSavedSearches()

        viewModel.advancedSearchFlow.collectInViewLifecycle { advanced ->
            binding.basicSearchContent.isVisible = !advanced
            binding.advancedSearchContent.isVisible = advanced
        }
    }

    // TODO: multi-selection handling for all chips
    private fun setupChips() {
        binding.decksChip.setOnClickListener {
            // TODO: see onDeckSelected
            startDeckSelection(title = getString(CommonString.search_deck), asChild = true, skipEmptyDefault = true)
        }

        binding.tagsChip.setOnClickListener {
            // see onSelectedTags
            launchCatchingTask {
                tagsDialogFactory.show(
                    requireActivity(),
                    type = TagsDialog.DialogType.FILTER_BY_TAG,
                    checkedTags = ArrayList(viewModel.filtersFlow.value.tags),
                )
            }
        }

        binding.cardStateChip.setOnClickListener {
            CardStateBottomSheetFragment.show(childFragmentManager)
        }

        binding.flagsChip.setOnClickListener {
            launchCatchingTask {
                FlagsBottomSheetFragment.createInstance(requireContext()).show(childFragmentManager)
            }
        }

        viewModel.filtersFlow.collectInLifecycle {
            binding.decksChip.hasCheckedBackground = it.decks.any()
            binding.tagsChip.hasCheckedBackground = it.tags.any()
            binding.cardStateChip.hasCheckedBackground = it.cardStates.any()
            binding.flagsChip.hasCheckedBackground = it.flags.any()

            binding.decksChip.text = it.decks.firstOrNull()?.name ?: TR.sentenceCase.allDecks
            binding.tagsChip.text = formatChipDescription(it.tags, emptyValue = "Tags")
            binding.cardStateChip.text = formatChipDescription(it.cardStates.map { it.label }, emptyValue = "Card state")
            binding.cardStateChip.chipIcon = it.cardStates.firstOrNull().getIcon(requireContext())

            binding.flagsChip.text =
                formatChipDescription(
                    it.flags,
                    singleValue = if (it.flags.singleOrNull() == Flag.NONE) "No Flag" else TR.browsingFlag(),
                    nonSingleValue = TR.browsingSidebarFlags(),
                )
            binding.flagsChip.chipIcon =
                ContextCompat.getDrawable(requireContext(), it.flags.firstOrNull().iconRes)?.also { drawable ->
                    if (it.flags.isEmpty()) {
                        DrawableCompat.setTint(
                            drawable.mutate(),
                            MaterialColors.getColor(requireContext(), androidx.appcompat.R.attr.colorPrimary, 0),
                        )
                    }
                }
        }
    }

    private fun onDeckSelected(deck: SelectableDeck?) {
        viewModel.setDecksFilter(deck?.toDeckNameIdList() ?: return)
    }

    override fun onSelectedTags(
        selectedTags: List<String>,
        indeterminateTags: List<String>,
        stateFilter: CardStateFilter,
    ) {
        viewModel.setTagsFilter(selectedTags)
    }

    private fun setupSearchHistory() {
        class SearchHistoryItemUiModel(
            val entry: SearchHistoryEntry,
            val searchString: SearchString?,
        ) {
            // as we have the rendered SearchString, we can produce a Spannable without using the
            // Anki collection
            fun toSpannable() = searchString?.let { entry.toUserSpannable(it) } ?: SpannableString("")
        }

        fun toUiModels(items: SearchHistoryItems): List<SearchHistoryItemUiModel> =
            items.entryToSearchString.map { SearchHistoryItemUiModel(entry = it.first, searchString = it.second) }

        class SearchHistoryAdapter(
            private val context: Context,
            searches: List<SearchHistoryItemUiModel>,
        ) : ArrayAdapter<SearchHistoryItemUiModel>(context, 0, searches) {
            override fun getView(
                position: Int,
                convertView: View?,
                parent: ViewGroup,
            ): View {
                val binding =
                    if (convertView != null) {
                        ViewSearchHistoryItemBinding.bind(convertView)
                    } else {
                        ViewSearchHistoryItemBinding.inflate(LayoutInflater.from(context), parent, false)
                    }

                val item = getItem(position)!!

                fun openSavedSearchNamePrompt() {
                    Timber.i("opening 'save search' name input dialog")
                    val dialog =
                        SaveBrowserSearchDialogFragment.newInstance(searchQuery = item.entry.query)

                    dialog.show(childFragmentManager, "savedSearchName")
                }

                binding.title.text = item.toSpannable()
                binding.root.setOnClickListener { viewModel.selectSearchHistoryEntry(item.entry) }
                binding.favorite.setOnClickListener { openSavedSearchNamePrompt() }
                return binding.root
            }
        }

        val searchHistoryAdapter =
            SearchHistoryAdapter(
                context = requireContext(),
                searches = toUiModels(viewModel.displayedSearchHistoryFlow.value),
            )
        binding.searchHistory.apply {
            adapter = searchHistoryAdapter
            setOnItemClickListener { _, _, position, _ ->
                viewModel.selectSearchHistoryEntry(getItemAtPosition(position) as SearchHistoryEntry)
            }
        }

        binding.toggleSearchHistory.setOnClickListener {
            viewModel.toggleHistoryExpanded()
        }

        // replace the data when the displayed search history is updated
        viewModel.displayedSearchHistoryFlow.collectInLifecycle {
            searchHistoryAdapter.clear()
            searchHistoryAdapter.addAll(toUiModels(it))
        }

        viewModel.showHistoryToggleFlow.collectInLifecycle {
            binding.toggleSearchHistory.isVisible = it
        }

        viewModel.isHistoryExpandedFlow.collectInLifecycle {
            binding.toggleSearchHistory.setText(if (it) CommonString.card_browser_see_less else CommonString.card_browser_see_more)
        }

        viewModel.searchHistoryAvailableFlow.collectInLifecycle {
            binding.searchHistoryContainer.isVisible = it
        }
    }

    private fun setupSavedSearches() {
        class SavedSearchAdapter(
            private val context: Context,
            searches: List<SavedSearch>,
        ) : ArrayAdapter<SavedSearch>(context, 0, searches) {
            override fun getView(
                position: Int,
                convertView: View?,
                parent: ViewGroup,
            ): View {
                val binding =
                    if (convertView != null) {
                        ViewSavedSearchItemBinding.bind(convertView)
                    } else {
                        ViewSavedSearchItemBinding.inflate(LayoutInflater.from(context), parent, false).also { binding ->
                            binding.root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                                binding.favorite.alignWithFirstLineOf(binding.title)
                            }
                        }
                    }

                val item = getItem(position)!!
                // Bound the preview before layout to avoid ANRs on very large saved searches.
                binding.title.text = item.name.ellipsize(MAX_SAVED_SEARCH_PREVIEW_LENGTH)
                binding.content.text = item.query.ellipsize(MAX_SAVED_SEARCH_PREVIEW_LENGTH)

                binding.root.setOnClickListener { viewModel.submitSavedSearch(item) }
                binding.insertSavedSearch.setOnClickListener { viewModel.applySavedSearch(item) }

                return binding.root
            }
        }

        val savedSearches = viewModel.savedSearchesFlow.value.toMutableList()
        val adapter =
            SavedSearchAdapter(
                requireContext(),
                savedSearches,
            )
        binding.savedSearches.adapter = adapter

        viewModel.savedSearchesFlow.collectInLifecycle {
            withContext(Dispatchers.Main) {
                savedSearches.clear()
                savedSearches.addAll(it)
                adapter.notifyDataSetChanged()
            }
        }

        viewModel.canManageSavedSearchesFlow.collectInLifecycle {
            binding.manageSavedSearchesContainer.isVisible = it
        }

        binding.manageSavedSearchesContainer.setOnClickListener {
            binding.manageSavedSearches.performClick()
            val dialog = SavedBrowserSearchesDialogFragment.newInstance(savedSearches)
            dialog.show(childFragmentManager, SavedBrowserSearchesDialogFragment.TAG)
        }
    }

    companion object {
        const val TAG = "STANDARD"

        private const val MAX_SAVED_SEARCH_PREVIEW_LENGTH = 500
    }
}

fun SelectableDeck.toDeckNameIdList(): List<DeckNameId>? =
    when (this) {
        is SelectableDeck.AllDecks -> emptyList()
        is SelectableDeck.Deck -> {
            if (this.deckId == 0L) emptyList() else listOf(DeckNameId(this.name, this.deckId))
        }
    }

fun formatChipDescription(
    tags: List<String>,
    emptyValue: String,
) = when (tags.size) {
    0 -> emptyValue
    1 -> tags.single()
    else -> "${tags.first()} +${tags.size - 1}"
}

fun <T> formatChipDescription(
    entries: List<T>,
    singleValue: String,
    nonSingleValue: String,
    // TODO: i18n
) = when (entries.size) {
    0 -> nonSingleValue
    1 -> singleValue
    else -> "$singleValue +${entries.size - 1}"
}

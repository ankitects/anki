/*
 *  Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>
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
import com.ichi2.anki.Flag
import com.ichi2.anki.R
import com.ichi2.anki.browser.SearchHistory.SearchHistoryEntry
import com.ichi2.anki.browser.search.CardBrowserSearchViewModel.SearchHistoryItems
import com.ichi2.anki.browser.toUserSpannable
import com.ichi2.anki.databinding.FragmentStandardSearchBinding
import com.ichi2.anki.databinding.ViewSavedSearchItemBinding
import com.ichi2.anki.databinding.ViewSearchHistoryItemBinding
import com.ichi2.anki.dialogs.DeckSelectionDialog
import com.ichi2.anki.dialogs.ManageSavedSearchAction
import com.ichi2.anki.dialogs.SaveBrowserSearchDialogFragment
import com.ichi2.anki.dialogs.SavedBrowserSearchesDialogFragment
import com.ichi2.anki.dialogs.registerSaveSearchHandler
import com.ichi2.anki.dialogs.registerSavedSearchActionHandler
import com.ichi2.anki.dialogs.tags.TagsDialog
import com.ichi2.anki.dialogs.tags.TagsDialogFactory
import com.ichi2.anki.dialogs.tags.TagsDialogListener
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.libanki.DeckNameId
import com.ichi2.anki.model.CardStateFilter
import com.ichi2.anki.model.SelectableDeck
import com.ichi2.anki.utils.ext.hasCheckedBackground
import com.ichi2.anki.utils.ext.launchCollectionInLifecycleScope
import com.ichi2.anki.utils.ext.showDialogFragment
import dev.androidbroadcast.vbpd.viewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

class StandardSearchFragment :
    Fragment(R.layout.fragment_standard_search),
    DeckSelectionDialog.DeckSelectionListener,
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

        binding.toggleAdvancedSearch.setOnClickListener { viewModel.toggleAdvancedSearch() }

        setupChips()
        setupSearchHistory()
        setupSavedSearches()
    }

    // TODO: multi-selection handling for all chips
    private fun setupChips() {
        binding.decksChip.setOnClickListener {
            launchCatchingTask {
                // TODO: see onDeckSelected
                val decks = listOf(SelectableDeck.AllDecks) + SelectableDeck.fromCollection(includeFiltered = true)
                val dialog =
                    DeckSelectionDialog.newInstance(
                        title = getString(R.string.search_deck),
                        decks = decks,
                    )
                dialog.show(childFragmentManager, "selectDeck")
            }
        }

        binding.tagsChip.setOnClickListener {
            // see onSelectedTags
            launchCatchingTask {
                val dialog =
                    tagsDialogFactory.newTagsDialog().withArguments(
                        context = requireContext(),
                        type = TagsDialog.DialogType.FILTER_BY_TAG,
                        noteIds = emptyList(),
                        checkedTags = ArrayList(viewModel.filtersFlow.value.tags),
                    )
                showDialogFragment(dialog)
            }
        }

        binding.cardStateChip.setOnClickListener {
            val sheet = CardStateBottomSheetFragment()
            sheet.show(childFragmentManager, CardStateBottomSheetFragment.TAG)
        }

        binding.flagsChip.setOnClickListener {
            launchCatchingTask {
                FlagsBottomSheetFragment.createInstance().show(childFragmentManager)
            }
        }

        viewModel.filtersFlow.launchCollectionInLifecycleScope {
            binding.decksChip.hasCheckedBackground = it.decks.any()
            binding.tagsChip.hasCheckedBackground = it.tags.any()
            binding.cardStateChip.hasCheckedBackground = it.cardStates.any()
            binding.flagsChip.hasCheckedBackground = it.flags.any()

            binding.decksChip.text = it.decks.firstOrNull()?.name ?: getString(R.string.card_browser_all_decks)
            binding.tagsChip.text = formatChipDescription(it.tags, emptyValue = "Tags")
            binding.cardStateChip.text = formatChipDescription(it.cardStates.map { it.label }, emptyValue = "Card state")
            binding.cardStateChip.chipIcon =
                ContextCompat.getDrawable(requireContext(), it.cardStates.firstOrNull().iconRes)?.also { drawable ->
                    if (it.cardStates.isEmpty()) {
                        DrawableCompat.setTint(
                            drawable,
                            MaterialColors.getColor(requireContext(), androidx.appcompat.R.attr.colorPrimary, 0),
                        )
                    }
                }
            binding.cardStateChip.chipIcon = ContextCompat.getDrawable(requireContext(), it.cardStates.firstOrNull().iconRes)

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
                            drawable,
                            MaterialColors.getColor(requireContext(), androidx.appcompat.R.attr.colorPrimary, 0),
                        )
                    }
                }
        }
    }

    override fun onDeckSelected(deck: SelectableDeck?) {
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
        viewModel.displayedSearchHistoryFlow.launchCollectionInLifecycleScope {
            searchHistoryAdapter.clear()
            searchHistoryAdapter.addAll(toUiModels(it))
        }

        viewModel.showHistoryToggleFlow.launchCollectionInLifecycleScope {
            binding.toggleSearchHistory.isVisible = it
        }

        viewModel.isHistoryExpandedFlow.launchCollectionInLifecycleScope {
            binding.toggleSearchHistory.setText(if (it) R.string.card_browser_see_less else R.string.card_browser_see_more)
        }

        viewModel.searchHistoryAvailableFlow.launchCollectionInLifecycleScope {
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
                        ViewSavedSearchItemBinding.inflate(LayoutInflater.from(context), parent, false)
                    }

                val item = getItem(position)!!
                binding.title.text = item.name
                binding.content.text = item.query

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

        viewModel.savedSearchesFlow.launchCollectionInLifecycleScope {
            withContext(Dispatchers.Main) {
                savedSearches.clear()
                savedSearches.addAll(it)
                adapter.notifyDataSetChanged()
            }
        }

        viewModel.canManageSavedSearchesFlow.launchCollectionInLifecycleScope {
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

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

import android.os.Bundle
import android.os.Parcelable
import android.view.View
import android.view.ViewGroup
import androidx.core.os.BundleCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.ichi2.anki.R
import com.ichi2.anki.browser.search.AdvancedSearchFieldsTab.FieldSearch
import com.ichi2.anki.browser.search.AdvancedSearchFragment.OptionData
import com.ichi2.anki.browser.search.AdvancedSearchFragment.OptionType
import com.ichi2.anki.browser.search.AdvancedSearchFragment.OptionType.InsertExample
import com.ichi2.anki.databinding.DialogGenericRecyclerViewBinding
import com.ichi2.anki.databinding.FragmentAdvancedSearchBinding
import com.ichi2.anki.databinding.ItemAdvancedSearchBinding
import com.ichi2.anki.dialogs.FieldSelectionDialog
import com.ichi2.anki.dialogs.FieldSelectionDialog.Companion.registerFieldSelectionHandler
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.model.ResultType
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.utils.openUrl
import dev.androidbroadcast.vbpd.viewBinding
import kotlinx.parcelize.Parcelize
import timber.log.Timber

/**
 * Helps a user build an advanced search string, explaining options listed in the
 *  [searching section of the Manual](https://docs.ankiweb.net/searching.html)
 *
 * These options are not easily representable via chips, such as:
 * * **boolean operations**: (`-cat`)
 * * **searches within types**: (`deck:a*`)
 * * **regex**: `"re:(some|another).*thing"`
 * * **properties**: `prop:ivl>=10`
 */
class AdvancedSearchFragment : Fragment(R.layout.fragment_advanced_search) {
    private val binding by viewBinding(FragmentAdvancedSearchBinding::bind)

    @Suppress("unused")
    private val viewModel: CardBrowserSearchViewModel by activityViewModels()

    @Parcelize
    sealed class OptionType : Parcelable {
        data class InsertExample(
            val example: String,
        ) : OptionType()

        data class SelectField(
            val resultType: ResultType,
        ) : OptionType()
    }

    @Parcelize
    data class OptionData(
        val title: String,
        val example: String,
        val type: OptionType,
    ) : Parcelable {
        constructor(title: String, example: String) : this(title, example, InsertExample(example))
    }

    data class TabData(
        val title: String,
        val options: List<OptionData> = listOf(),
    )

    private val tabData =
        listOf(
            TabData(
                "Fields",
                AdvancedSearchFieldsTab.options.toOptionData() +
                    listOf(
                        OptionData("Search multiple fields", "fi*ld:text"),
                    ),
            ),
            // TODO: Find tag without subtags?
            TabData(
                "Tags",
                listOf(
                    OptionData("Notes with a tag or its subtags", "tag:text"),
                    OptionData("Notes with no tag", "tag:none"),
                    OptionData("Search tags for multiple values", "tag:tex*"),
                ),
            ),
            TabData(
                "Decks",
                listOf(
                    OptionData("Cards in a deck or subdecks", "deck:french"),
                    OptionData("Cards in a subdeck", "deck:french::words"),
                    OptionData("Cards in a deck (excluding subdecks)", "deck:french -deck:french::*"),
                    OptionData("Cards in a deck which has a space in the name", "\"deck:a deck\""),
                    OptionData("Cards in filtered decks", "deck:filtered"),
                    OptionData("Cards in non-filtered decks", "-deck:filtered"),
                ),
            ),
            TabData(
                "Presets",
                listOf(
                    OptionData("Cards which use a preset", "preset:\"Default\""),
                ),
            ),
            TabData(
                "Card templates",
                listOf(
                    OptionData("Cards which use a card template", "\"card:Card 1\""),
                    OptionData("Find cards by template index", "card:2"),
                ),
            ),
            TabData(
                "Note types",
                listOf(
                    OptionData("Cards which use a note type", "note:basic"),
                ),
            ),
//        // TODO: Regex?
//        TabData("Regex", listOf(
//        )),
            TabData(
                "State",
                listOf(
                    OptionData("New cards", "is:new"),
                    OptionData("Cards in learning", "is:learn"),
                    OptionData("Reviews (both due and not due) and lapsed cards.", "is:review"),
                    OptionData("Cards that have been automatically or manually suspended.", "is:suspended"),
                    OptionData("Cards that have been buried.", "is:buried"),
                    OptionData("Cards that have been buried automatically.", "is:buried-sibling"),
                    OptionData("Cards that have been manually buried.", "is:buried-manually"),
                    OptionData("Cards which have lapsed and are awaiting relearning", "is:learn is:review"),
                    OptionData("Review cards, excluding lapses", "-is:learn is:review"),
                    OptionData("Cards in learning for the first time", "is:learn -is:review"),
                ),
            ),
            // TODO: get flags using labels from backend
            TabData(
                "Flags",
                listOf(
                    OptionData("Cards with no flag", "flag:0"),
                    OptionData("Cards with a red flag", "flag:1"),
                    OptionData("Cards with an orange flag", "flag:2"),
                    OptionData("Cards with a green flag", "flag:3"),
                    OptionData("Cards with a blue flag", "flag:4"),
                    OptionData("Cards with a pink flag", "flag:5"),
                    OptionData("Cards with a turquoise flag", "flag:6"),
                    OptionData("Cards with a purple flag", "flag:7"),
                ),
            ),
            TabData(
                "Card properties",
                listOf(
                    OptionData("Interval", "prop:ivl>=10"),
                    OptionData("Due", "prop:ivl>=10"),
                    OptionData("Overdue cards", "prop:due<=1"),
                    OptionData("Due tomorrow", "prop:due=1"),
                    OptionData("Number of answers", "prop:reps<10"),
                    OptionData("Number of lapses", "prop:lapses>3"),
                    OptionData("Cards answered yesterday", "prop:rated=-1"),
                    OptionData("Cards rescheduled using Set due date or Reschedule cards on change.", "prop:resched=0"),
                    OptionData("Ease", "prop:ease!=2.5"),
                    OptionData("Position", "prop:pos<=100"),
                    OptionData("Stability", "prop:s>21"),
                    OptionData("Difficulty", "prop:d>0.3"),
                    OptionData("Retrievability", "prop:r<0.9"),
                ),
            ),
            TabData(
                "Card events",
                listOf(
                    OptionData("Cards added recently", "added:1"),
                    OptionData("Cards with recent note text edits", "edited:1"),
                    OptionData("Cards answered in the past", "rated:1"),
                    OptionData("Cards answered again", "rated:1:1"),
                    OptionData("Cards answered hard", "rated:1:2"),
                    OptionData("Cards answered good", "rated:1:3"),
                    OptionData("Cards answered easy", "rated:1:4"),
                    OptionData("Cards first answered in the past", "introduced:7"),
                ),
            ),
            TabData(
                "IDs",
                listOf(
                    OptionData("Search by Note IDs", "nid:123"),
                    OptionData("Search by Card IDs", "cid:123,456,789"),
                ),
            ),
            TabData(
                "Custom data",
                listOf(
                    OptionData("Has custom data", "has-cd:v"),
                    OptionData("Query numeric custom data", "prop:cdn:d>5"),
                    OptionData("Query textual custom data", "prop:cds:v=reschedule"),
                ),
            ),
        )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupFieldRequestListeners()
    }

    /**
     * Sets up listeners for [FieldSelectionDialog]
     *
     * @see AdvancedSearchFieldsTab
     * @see registerFieldSelectionHandler
     */
    private fun setupFieldRequestListeners() {
        childFragmentManager.registerFieldSelectionHandler { resultType, fieldName ->
            val fieldSearch =
                AdvancedSearchFieldsTab.options[resultType] ?: run {
                    Timber.w("resultType '%s' was unhandled", resultType)
                    showSnackbar(R.string.something_wrong)
                    return@registerFieldSelectionHandler
                }
            launchCatchingTask {
                val searchString = fieldSearch.buildSearchString(fieldName)
                viewModel.appendAdvancedSearch(searchString)
            }
        }
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        binding.viewPager.adapter = AdvancedSearchStateAdapter()
        TabLayoutMediator(
            binding.tabLayout,
            binding.viewPager,
        ) { tab: TabLayout.Tab, position: Int ->
            tab.text = tabData[position].title
        }.attach()
        binding.tabLayout.selectTab(binding.tabLayout.getTabAt(0))
        binding.advancedHelp.setOnClickListener {
            Timber.i("opening advanced help")
            openUrl(R.string.link_help_searching)
        }
    }

    inner class AdvancedSearchStateAdapter : FragmentStateAdapter(this@AdvancedSearchFragment) {
        override fun createFragment(position: Int): Fragment = SelectAdvancedSearchFragment.createInstance(tabData[position].options)

        override fun getItemCount() = tabData.size
    }

    /**
     * Displays a list of Advanced Search items with a name and an example
     *
     * Selecting a list item results in a call to [CardBrowserSearchViewModel.appendAdvancedSearch]
     *
     * @see OptionData
     */
    class SelectAdvancedSearchFragment : Fragment(R.layout.dialog_generic_recycler_view) {
        val binding by viewBinding(DialogGenericRecyclerViewBinding::bind)

        val data: List<OptionData>
            get() =
                requireNotNull(
                    BundleCompat.getParcelableArrayList(
                        requireArguments(),
                        ARG_OPTION_DATA,
                        OptionData::class.java,
                    ),
                )

        fun onOptionSelected(optionData: OptionData) {
            when (optionData.type) {
                is InsertExample -> viewModel.appendAdvancedSearch(optionData.example)
                is OptionType.SelectField ->
                    launchCatchingTask {
                        val dialog = FieldSelectionDialog.createInstance(optionData.type.resultType)
                        dialog.show(parentFragmentManager, FieldSelectionDialog.TAG)
                    }
            }
        }

        private val viewModel: CardBrowserSearchViewModel by activityViewModels()

        override fun onViewCreated(
            view: View,
            savedInstanceState: Bundle?,
        ) {
            super.onViewCreated(view, savedInstanceState)

            binding.root.layoutManager = LinearLayoutManager(context)
            binding.root.adapter =
                object : RecyclerView.Adapter<ViewHolder>() {
                    override fun onCreateViewHolder(
                        parent: ViewGroup,
                        viewType: Int,
                    ): ViewHolder {
                        val binding =
                            ItemAdvancedSearchBinding.inflate(
                                layoutInflater,
                                parent,
                                false,
                            )
                        return ViewHolder(binding)
                    }

                    override fun onBindViewHolder(
                        holder: ViewHolder,
                        position: Int,
                    ) {
                        holder.binding.title.text = data[position].title
                        holder.binding.sample.text = data[position].example

                        holder.binding.root.setOnClickListener {
                            onOptionSelected(data[position])
                        }
                    }

                    override fun getItemCount(): Int = data.size
                }
        }

        class ViewHolder(
            val binding: ItemAdvancedSearchBinding,
        ) : RecyclerView.ViewHolder(binding.root)

        companion object {
            private const val ARG_OPTION_DATA = "data"

            fun createInstance(data: List<OptionData>) =
                SelectAdvancedSearchFragment().apply {
                    arguments =
                        Bundle().apply {
                            putParcelableArrayList(ARG_OPTION_DATA, ArrayList(data))
                        }
                }
        }
    }

    companion object {
        const val TAG = "ADVANCED"
    }
}

private fun Map<ResultType, FieldSearch>.toOptionData(): List<OptionData> =
    this.map {
        OptionData(
            title = it.value.title,
            example = it.value.example,
            type = OptionType.SelectField(it.key),
        )
    }

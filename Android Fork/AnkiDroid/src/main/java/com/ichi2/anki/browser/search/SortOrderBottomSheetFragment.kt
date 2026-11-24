// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser.search

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.CheckResult
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import anki.search.BrowserColumns.Sorting
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.browser.BrowserColumnKey
import com.ichi2.anki.browser.CardBrowserColumn
import com.ichi2.anki.browser.CardBrowserViewModel
import com.ichi2.anki.browser.ColumnType
import com.ichi2.anki.browser.getLabel
import com.ichi2.anki.browser.humanReadableExplanation
import com.ichi2.anki.browser.search.SortOrderBottomSheetFragment.ColumnUiModel.AnkiColumn
import com.ichi2.anki.browser.search.ui.SortDirection
import com.ichi2.anki.browser.search.ui.toSortDirection
import com.ichi2.anki.databinding.FragmentBottomSheetListBinding
import com.ichi2.anki.databinding.ViewBrowserSortOrderBottomSheetItemBinding
import com.ichi2.anki.databinding.ViewBrowserSortOrderSectionHeaderBinding
import com.ichi2.anki.model.CardsOrNotes
import com.ichi2.anki.model.SortType
import com.ichi2.anki.utils.ext.behavior
import com.ichi2.anki.utils.ext.requireParcelable
import com.ichi2.anki.utils.isWindowCompact
import dev.androidbroadcast.vbpd.viewBinding
import timber.log.Timber

private typealias Reverse = Boolean?

/**
 * A [BottomSheetDialogFragment] allowing selection of the sort order of the Card Browser
 *
 * Entitled "Choose display order"
 *
 * The fragment displays a list of columns which can be sorted by:
 * Active, then Available, and allows a user to sort  on each column in either an ascending or
 * descending order.
 *
 * Columns have [sort types][ColumnType] which are displayed (text, numerical, dates etc...)
 *
 * 'No sorting' is also offered as an option.
 *
 * @param viewModelProviderFactory A factory producing a [CardBrowserViewModel]
 */
class SortOrderBottomSheetFragment(
    private val viewModelProviderFactory: ViewModelProvider.Factory = ViewModelProvider.NewInstanceFactory(),
) : BottomSheetDialogFragment(R.layout.fragment_bottom_sheet_list) {
    @VisibleForTesting
    val viewModel: CardBrowserViewModel by activityViewModels { viewModelProviderFactory }

    @VisibleForTesting
    val binding by viewBinding(FragmentBottomSheetListBinding::bind)

    @VisibleForTesting
    val currentSortType: SortType
        get() = requireArguments().requireParcelable<SortType>(ARG_CURRENT_SORT_TYPE)

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        with(binding.title) {
            isVisible = true
            text = getString(CommonString.card_browser_change_display_order_title)
        }

        with(this.behavior) {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
            isDraggable = false
            if (resources.isWindowCompact()) {
                maxWidth = ViewGroup.LayoutParams.MATCH_PARENT
            }
        }

        binding.list.adapter =
            SortOrderHolderAdapter(
                columns = ColumnUiModel.buildList(viewModel),
                currentlySelectedSort = currentSortType,
                onItemClickedListener = { sortType ->
                    viewModel.setSortType(sortType)
                    this@SortOrderBottomSheetFragment.dismiss()
                },
            )
    }

    /**
     * Display the dialog, adding the fragment to the given [FragmentManager].
     *
     * @param manager The [FragmentManager] this fragment will be added to.
     *
     * @see BottomSheetDialogFragment.show
     */
    fun show(manager: FragmentManager) = this.show(manager, TAG)

    /**
     * An item displayed in the sort order list
     *
     * - header: [SectionHeader]
     * - interactive row: [ColumnUiModel]
     */
    sealed interface SortListItem

    /** A non-interactive section header placed between groups of [ColumnUiModel]s. */
    data class SectionHeader(
        @StringRes val titleRes: Int,
    ) : SortListItem

    /**
     * The data to display a column (or 'no ordering') for selection as the sort column
     */
    sealed class ColumnUiModel : SortListItem {
        data object NoOrdering : ColumnUiModel()

        /**
         * @see anki.search.BrowserColumns.Column
         */
        data class AnkiColumn(
            val key: BrowserColumnKey,
            val label: String,
            val tooltipValue: String?,
            val canBeSorted: Boolean,
            val isShownInUI: Boolean,
            val type: ColumnType,
        ) : ColumnUiModel() {
            override fun toString() = this.label

            /**
             * The reason why a key is unavailable for sorting
             *
             * This assumes that the column is already confirmed to be unavailable
             * (e.g. FSRS columns return a reason, but are usable in cards mode)
             */
            @get:StringRes
            val sortUnavailableReason: Int?
                get() {
                    if (canBeSorted) return null

                    return when (this.key.value) {
                        // Questin/Answer require rendering the template, which is too expensive to
                        // do for all cards in the collection. 'Sort Field' can be used instead
                        CardBrowserColumn.QUESTION.ankiColumnKey,
                        CardBrowserColumn.ANSWER.ankiColumnKey,
                        -> CommonString.card_browser_order_subtitle_use_sort_field

                        // FSRS data is stored in JSON in the card table, it's not yet feasible to
                        // sort on this data
                        CardBrowserColumn.FSRS_DIFFICULTY.ankiColumnKey,
                        CardBrowserColumn.FSRS_STABILITY.ankiColumnKey,
                        CardBrowserColumn.FSRS_RETRIEVABILITY.ankiColumnKey,
                        -> CommonString.card_browser_order_subtitle_cards_mode_only

                        else -> null
                    }
                }
        }

        /**
         * Whether the item is usable in the current state
         *
         * 'Question' is unavailable in Cards mode
         */
        val available: Boolean
            get() =
                when (this) {
                    is NoOrdering -> true
                    is AnkiColumn -> this.canBeSorted
                }

        val tooltip: String? get() =
            when (this) {
                NoOrdering -> null
                is AnkiColumn -> this.tooltipValue
            }

        @CheckResult
        fun getLabel(context: Context): String =
            when (this) {
                NoOrdering -> context.getString(CommonString.card_browser_order_no_sorting_title)
                is AnkiColumn -> this.label
            }

        @CheckResult
        fun getSubtitle(
            context: Context,
            sort: SortType.CollectionOrdering?,
        ): String? {
            val subtitleRes: Int? =
                when (this) {
                    // No Ordering's subtitle is 'Faster'
                    is NoOrdering -> CommonString.card_browser_order_no_sorting_subtitle
                    // the selected column has an explanation of the sort: "Low to high"
                    is AnkiColumn if sort != null && sort.key == key ->
                        type.humanReadableExplanation(descending = sort.reverse)
                    // if a column is unavailable, the subtitle is the reason that it's unavailable
                    is AnkiColumn if !canBeSorted -> sortUnavailableReason
                    else -> null
                }
            return subtitleRes?.let { context.getString(it) }
        }

        @CheckResult
        fun toSortType(reverse: Reverse): SortType =
            when (this) {
                is NoOrdering -> SortType.NoOrdering
                is AnkiColumn ->
                    SortType.CollectionOrdering(
                        key = this.key,
                        reverse = requireNotNull(reverse),
                    )
            }

        companion object {
            @CheckResult
            fun buildList(viewModel: CardBrowserViewModel): List<SortListItem> {
                // start with our default order. Anything unknown is at the end of the list
                val orderIndex = CardBrowserColumn.entries.withIndex().associate { it.value.ankiColumnKey to it.index }
                val sortedColumns = viewModel.allColumns.sortedWith(compareBy { orderIndex[it.key] ?: Int.MAX_VALUE })

                // active columns are visible in the UI. These come first, in UI order
                val activeColumnMap = viewModel.activeColumns.withIndex().associate { it.value.ankiColumnKey to it.index }
                val sortedWithActive = sortedColumns.sortedWith(compareBy { activeColumnMap[it.key] ?: Int.MAX_VALUE })

                // columns not in [CardBrowserColumn] fall back to [ColumnType.UNSPECIFIED]
                val typeByKey = CardBrowserColumn.entries.associate { it.ankiColumnKey to it.type(viewModel.cardsOrNotes) }

                val ankiColumnsList =
                    sortedWithActive.map { column ->
                        // some Anki columns can't be sorted on.
                        // Display them, but mark them as unavailable
                        val canBeSorted =
                            when (viewModel.cardsOrNotes) {
                                CardsOrNotes.CARDS -> column.sortingCards != Sorting.SORTING_NONE
                                CardsOrNotes.NOTES -> column.sortingNotes != Sorting.SORTING_NONE
                            }

                        // TODO: design a tooltip using `column.getTooltip`
                        val label = column.getLabel(viewModel.cardsOrNotes)

                        val isActive = activeColumnMap.containsKey(column.key)

                        AnkiColumn(
                            key = BrowserColumnKey.from(column),
                            label = label,
                            tooltipValue = null,
                            isShownInUI = isActive,
                            canBeSorted = canBeSorted,
                            type = typeByKey[column.key] ?: ColumnType.UNSPECIFIED,
                        )
                    }

                // three groups: browser columns, sortable but hidden, unavailable
                val active = ankiColumnsList.filter { it.isShownInUI && it.canBeSorted }
                val available = ankiColumnsList.filter { !it.isShownInUI && it.canBeSorted }
                val unavailable = ankiColumnsList.filter { !it.canBeSorted }

                return buildList {
                    add(NoOrdering)
                    if (active.isNotEmpty()) {
                        add(SectionHeader(CommonString.user_active_columns))
                        addAll(active)
                    }
                    if (available.isNotEmpty()) {
                        add(SectionHeader(CommonString.user_potential_columns))
                        addAll(available)
                    }
                    if (unavailable.isNotEmpty()) {
                        add(SectionHeader(CommonString.card_browser_order_section_unavailable))
                        addAll(unavailable)
                    }
                }
            }
        }
    }

    /**
     * @see ViewBrowserSortOrderBottomSheetItemBinding
     */
    @VisibleForTesting
    inner class SortOrderHolderAdapter(
        @VisibleForTesting
        val columns: List<SortListItem>,
        private val currentlySelectedSort: SortType,
        private val onItemClickedListener: ((SortType) -> Unit) = { },
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemViewType(position: Int): Int =
            when (columns[position]) {
                is SectionHeader -> VIEW_TYPE_HEADER
                is ColumnUiModel -> VIEW_TYPE_COLUMN
            }

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int,
        ): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return when (viewType) {
                VIEW_TYPE_HEADER ->
                    HeaderHolder(ViewBrowserSortOrderSectionHeaderBinding.inflate(inflater, parent, false))
                else ->
                    Holder(ViewBrowserSortOrderBottomSheetItemBinding.inflate(inflater, parent, false))
            }
        }

        override fun onBindViewHolder(
            holder: RecyclerView.ViewHolder,
            position: Int,
        ) {
            when (holder) {
                is HeaderHolder -> {
                    val header = columns[position] as SectionHeader
                    holder.binding.sectionHeader.setText(header.titleRes)
                }
                is Holder -> {
                    val column = this.columns[position] as ColumnUiModel
                    bindColumnHolder(holder, column)
                }
            }
        }

        private fun bindColumnHolder(
            holder: Holder,
            column: ColumnUiModel,
        ) {
            val context = holder.binding.root.context

            setupAvailability(holder, available = column.available)

            // highlight the current row
            holder.binding.root.background =
                if (column.isCurrentSortOrder()) {
                    ContextCompat.getDrawable(context, R.drawable.background_sort_order_selected_row)
                } else {
                    null
                }

            // setup title/subtitle
            holder.binding.text.text = column.getLabel(context)
            column.getSubtitle(context, currentlySelectedSort as? SortType.CollectionOrdering).let {
                holder.binding.subtitle.isVisible = it != null
                holder.binding.subtitle.text = it
            }

            setupSortControls(column, holder)
        }

        private fun setupSortControls(
            column: ColumnUiModel,
            holder: Holder,
        ) {
            val pill = holder.binding.sortPill
            val root = holder.binding.root

            when (column) {
                is AnkiColumn -> {
                    pill.isVisible = true

                    pill.columnType = column.type
                    val sort = currentlySelectedSort as? SortType.CollectionOrdering
                    val activeDirection: SortDirection? =
                        if (sort != null && sort.key == column.key) sort.reverse.toSortDirection() else null
                    pill.activeDirection = activeDirection
                    pill.isEnabled = column.available
                    pill.onDirectionClicked = { direction ->
                        if (direction == activeDirection) {
                            Timber.i("clicked active direction for %s; dismissing dialog", column)
                            this@SortOrderBottomSheetFragment.dismiss()
                        } else {
                            Timber.i("sort direction clicked: %s for column %s", direction, column)
                            onItemClickedListener(column.toSortType(direction.isReverse))
                        }
                    }
                    pill.columnContentDescription = column.label

                    // when tapped, select the left pill if unselected, otherwise flip the selection
                    root.isClickable = column.available
                    if (column.available) {
                        root.setOnClickListener {
                            val next = activeDirection?.flipped() ?: SortDirection.Ascending
                            Timber.i("row tap: column=%s next=%s", column, next)
                            onItemClickedListener(column.toSortType(next.isReverse))
                        }
                    } else {
                        root.setOnClickListener(null)
                    }
                }
                is ColumnUiModel.NoOrdering -> {
                    pill.isVisible = false
                    root.isClickable = true
                    root.setOnClickListener {
                        Timber.i("NoOrdering row clicked")
                        onItemClickedListener(column.toSortType(null))
                    }
                }
            }
        }

        /**
         * Updates visibility/enabled status of a row
         */
        private fun setupAvailability(
            holder: Holder,
            available: Boolean,
        ) {
            holder.binding.root.isEnabled = available
            val disabledAlpha = if (available) 1.0f else 0.4f
            holder.binding.text.alpha = disabledAlpha
            holder.binding.sortPill.alpha = disabledAlpha
            // the reason for unavailability, so keep it visible
            holder.binding.subtitle.alpha = 1.0f
        }

        private fun ColumnUiModel.isCurrentSortOrder() =
            when (this) {
                is ColumnUiModel.NoOrdering -> currentlySelectedSort is SortType.NoOrdering
                is AnkiColumn ->
                    currentlySelectedSort is SortType.CollectionOrdering &&
                        this.key == currentlySelectedSort.key
            }

        override fun getItemCount() = columns.size

        inner class Holder(
            val binding: ViewBrowserSortOrderBottomSheetItemBinding,
        ) : RecyclerView.ViewHolder(binding.root)

        inner class HeaderHolder(
            val binding: ViewBrowserSortOrderSectionHeaderBinding,
        ) : RecyclerView.ViewHolder(binding.root)
    }

    companion object {
        const val TAG = "SortOrderBottomSheetFragment"

        const val ARG_CURRENT_SORT_TYPE = "currentSortType"

        private const val VIEW_TYPE_COLUMN = 0
        private const val VIEW_TYPE_HEADER = 1

        suspend fun createInstance(cardsOrNotes: CardsOrNotes) =
            SortOrderBottomSheetFragment().apply {
                val sortData = SortType.build(cardsOrNotes)

                Timber.i("creating SortOrderBottomSheetFragment with %s", sortData)

                arguments = Bundle().apply { putParcelable(ARG_CURRENT_SORT_TYPE, sortData) }
            }
    }
}

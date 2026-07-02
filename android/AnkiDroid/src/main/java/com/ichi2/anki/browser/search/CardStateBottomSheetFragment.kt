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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.ichi2.anki.R
import com.ichi2.anki.databinding.FragmentBottomSheetListBinding
import com.ichi2.anki.databinding.ItemBrowserFilterBottomSheetBinding
import com.ichi2.anki.utils.ext.behavior
import dev.androidbroadcast.vbpd.viewBinding

/**
 * A [BottomSheetDialogFragment] allowing selection of 0-many [CardState]s
 */
class CardStateBottomSheetFragment : BottomSheetDialogFragment(R.layout.fragment_bottom_sheet_list) {
    private val viewModel: CardBrowserSearchViewModel by activityViewModels()
    private val binding by viewBinding(FragmentBottomSheetListBinding::bind)

    val adapter = HolderAdapter(CardState.entries)

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        this.behavior.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
            isDraggable = false
        }

        adapter.checkedItems.addAll(viewModel.filtersFlow.value.cardStates)
        adapter.onItemCheckedListener = { _ ->
            onItemsSelected(adapter.checkedItems)
        }
        adapter.onItemClickedListener = { clickedItem ->
            val newSelection =
                when (clickedItem) {
                    // If the item is selected and tapped again, deselect it
                    adapter.checkedItems.singleOrNull() -> emptySet()
                    else -> setOf(clickedItem)
                }
            onItemsSelected(newSelection)
            dismiss()
        }

        binding.list.adapter = adapter
    }

    fun onItemsSelected(states: Set<CardState>) {
        // TODO: Should state should be a set? This may affect the summary.

        viewModel.setCardStateFilter(states.toList())
    }

    class HolderAdapter(
        val states: List<CardState>,
    ) : RecyclerView.Adapter<HolderAdapter.Holder>() {
        val checkedItems: MutableSet<CardState> = mutableSetOf()

        var onItemClickedListener: ((CardState) -> Unit) = {
        }
        var onItemCheckedListener: ((CardState) -> Unit) = {
        }

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int,
        ): Holder {
            val binding =
                ItemBrowserFilterBottomSheetBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false,
                )
            return Holder(binding)
        }

        override fun onBindViewHolder(
            holder: Holder,
            position: Int,
        ) {
            val state = this.states[position]
            holder.binding.text.text = state.label
            holder.binding.icon.setImageResource(state.iconRes)
            holder.binding.root.setOnClickListener { onItemClickedListener(state) }
            holder.binding.checkbox.apply {
                this.isChecked = checkedItems.contains(state)
                setOnCheckedChangeListener { _, _ ->
                    if (checkedItems.contains(state)) {
                        checkedItems.remove(state)
                    } else {
                        checkedItems.add(state)
                    }

                    this.isChecked = checkedItems.contains(state)
                    onItemCheckedListener(state)
                }
            }
        }

        override fun getItemCount() = states.size

        class Holder(
            val binding: ItemBrowserFilterBottomSheetBinding,
        ) : RecyclerView.ViewHolder(binding.root)
    }

    companion object {
        const val TAG = "CardStateBottomSheetFragment"
    }
}

@get:DrawableRes
val CardState?.iconRes
    get() =
        when (this) {
            null -> R.drawable.ic_card_state_default
            CardState.New -> R.drawable.ic_card_state_new
            CardState.Learning -> R.drawable.ic_card_state_learning
            CardState.Review -> R.drawable.ic_card_state_review
            CardState.Buried -> R.drawable.ic_card_state_buried
            CardState.Suspended -> R.drawable.ic_card_state_suspended
        }

// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser.search

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.color.MaterialColors
import com.ichi2.anki.R
import com.ichi2.anki.databinding.FragmentBottomSheetListBinding
import com.ichi2.anki.databinding.ItemBrowserFilterBottomSheetBinding
import com.ichi2.anki.utils.ext.behavior
import com.ichi2.anki.utils.ext.showOnce
import dev.androidbroadcast.vbpd.viewBinding
import com.ichi2.anki.common.android.R as CommonR

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
            holder.binding.checkbox.contentDescription = state.label
            holder.binding.icon.setImageDrawable(state.getIcon(holder.itemView.context))
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

        /** Keeps at most one card-state filter open in [manager]. */
        fun show(manager: FragmentManager) = CardStateBottomSheetFragment().showOnce(manager, TAG)
    }
}

/** Returns the card-state icon tinted with the current theme's state color. */
fun CardState?.getIcon(context: Context): Drawable? {
    val iconRes = if (this == null) R.drawable.ic_card_state_default else R.drawable.ic_card_state
    val colorAttr =
        when (this) {
            null -> androidx.appcompat.R.attr.colorPrimary
            CardState.New -> CommonR.attr.newCountColor
            CardState.Learning -> CommonR.attr.learnCountColor
            CardState.Review -> CommonR.attr.reviewCountColor
            CardState.Buried -> CommonR.attr.buriedColor
            CardState.Suspended -> CommonR.attr.suspendedColor
        }
    return ContextCompat.getDrawable(context, iconRes)?.mutate()?.also {
        DrawableCompat.setTint(it, MaterialColors.getColor(context, colorAttr, 0))
    }
}

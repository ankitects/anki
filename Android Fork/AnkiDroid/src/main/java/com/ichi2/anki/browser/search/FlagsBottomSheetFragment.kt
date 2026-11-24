// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser.search

import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.core.os.BundleCompat
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.ichi2.anki.Flag
import com.ichi2.anki.R
import com.ichi2.anki.databinding.FragmentBottomSheetListBinding
import com.ichi2.anki.databinding.ItemBrowserFilterBottomSheetBinding
import com.ichi2.anki.utils.ext.behavior
import com.ichi2.anki.utils.ext.showOnce
import dev.androidbroadcast.vbpd.viewBinding
import kotlinx.parcelize.Parcelize
import timber.log.Timber

/**
 * A [BottomSheetDialogFragment] allowing selection of 0-many [Flags][Flag]
 */
class FlagsBottomSheetFragment : BottomSheetDialogFragment(R.layout.fragment_bottom_sheet_list) {
    private val viewModel: CardBrowserSearchViewModel by activityViewModels()
    private val binding by viewBinding(FragmentBottomSheetListBinding::bind)

    private lateinit var adapter: FlagHolderAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val flags =
            requireNotNull(
                BundleCompat.getParcelableArrayList(requireArguments(), ARG_FLAGS, FlagUiModel::class.java),
            ) { ARG_FLAGS }
        adapter = FlagHolderAdapter(flags.toMutableList())
    }

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

        adapter.checkedItems.addAll(viewModel.filtersFlow.value.flags)
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

    fun onItemsSelected(flags: Set<Flag>) {
        // TODO: Summary is wrong based on selection order of entries
        // TODO: State should be a set(?)

        viewModel.setFlagsFilter(flags.toList())
    }

    @Parcelize
    class FlagUiModel(
        val flag: Flag,
        val label: String,
    ) : Parcelable

    class FlagHolderAdapter(
        val states: MutableList<FlagUiModel>,
    ) : RecyclerView.Adapter<FlagHolderAdapter.Holder>() {
        val checkedItems: MutableSet<Flag> = mutableSetOf()

        var onItemClickedListener: ((Flag) -> Unit) = {
        }
        var onItemCheckedListener: ((Flag) -> Unit) = {
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
            val model = this.states[position]
            holder.binding.text.text = model.label
            holder.binding.checkbox.contentDescription = model.label
            holder.binding.icon.setImageResource(model.flag.drawableRes)
            // TODO: Long press to rename
            holder.binding.root.setOnClickListener { onItemClickedListener(model.flag) }
            holder.binding.checkbox.apply {
                this.isChecked = checkedItems.contains(model.flag)
                setOnCheckedChangeListener { _, _ ->
                    if (checkedItems.contains(model.flag)) {
                        checkedItems.remove(model.flag)
                    } else {
                        checkedItems.add(model.flag)
                    }

                    this.isChecked = checkedItems.contains(model.flag)
                    onItemCheckedListener(model.flag)
                }
            }
        }

        override fun getItemCount() = states.size

        class Holder(
            val binding: ItemBrowserFilterBottomSheetBinding,
        ) : RecyclerView.ViewHolder(binding.root)
    }

    /**
     * Display the dialog, adding the fragment to the given [FragmentManager].
     *
     * @param manager The [FragmentManager] this fragment will be added to.
     *
     * @see BottomSheetDialogFragment.show
     */
    fun show(manager: FragmentManager) = showOnce(manager, TAG)

    companion object {
        const val TAG = "FlagsBottomSheetFragment"
        private const val ARG_FLAGS = "flagData"

        suspend fun createInstance(context: Context): FlagsBottomSheetFragment =
            FlagsBottomSheetFragment().apply {
                Timber.d("Building 'FlagsBottomSheetFragment' dialog")

                val userDefinedNames = Flag.queryDisplayNames(context)
                val flags =
                    Flag.entries.map {
                        FlagUiModel(it, label = userDefinedNames.getValue(it))
                    }

                arguments = Bundle().apply { putParcelableArrayList(ARG_FLAGS, ArrayList(flags)) }
            }
    }
}

@get:DrawableRes
val Flag?.iconRes
    get() =
        when (this) {
            null -> R.drawable.ic_flag_transparent
            else -> this.drawableRes
        }

// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Siddhesh Jondhale <jondhale2004@gmail.com>

package com.ichi2.anki.browser

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import androidx.appcompat.app.AlertDialog
import androidx.core.os.BundleCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.common.utils.dp
import com.ichi2.anki.databinding.ItemColumnSelectionBinding
import com.ichi2.anki.utils.ext.requireParcelable
import com.ichi2.utils.setPaddingRelative
import kotlinx.coroutines.launch
import timber.log.Timber

class ColumnSelectionDialogFragment : DialogFragment() {
    private val viewModel: CardBrowserViewModel by activityViewModels()
    private val columnToReplace: ColumnHeading by lazy {
        requireArguments().requireParcelable(ARG_SELECTED_COLUMN)
    }

    private var availableColumns: List<ColumnWithSample> = emptyList()

    override fun onSaveInstanceState(outState: Bundle) {
        with(outState) {
            outState.putParcelableArrayList(STATE_AVAILABLE_COLUMNS, availableColumns.toCollection(ArrayList()))
            super.onSaveInstanceState(this)
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): AlertDialog {
        val listView =
            ListView(requireContext()).apply {
                setPaddingRelative(
                    start = 0.dp,
                    end = 0.dp,
                    top = 24.dp,
                    bottom = 0.dp,
                )
            }

        val adapter =
            object : ArrayAdapter<ColumnWithSample>(
                requireContext(),
                R.layout.item_column_selection,
                mutableListOf(),
            ) {
                override fun getView(
                    position: Int,
                    convertView: View?,
                    parent: ViewGroup,
                ): View {
                    val binding =
                        if (convertView != null) {
                            ItemColumnSelectionBinding.bind(convertView)
                        } else {
                            ItemColumnSelectionBinding.inflate(layoutInflater, parent, false)
                        }

                    val column = getItem(position)

                    binding.columnTitle.text =
                        column?.label ?: getString(CommonString.no_columns_available)

                    binding.columnExample.text =
                        if (column?.sampleValue.isNullOrBlank()) "-" else column.sampleValue

                    return binding.root
                }
            }
        listView.adapter = adapter
        listView.divider = null

        lifecycleScope.launch {
            // Load the available columns either from the viewModel or savedInstanceState bundle
            availableColumns =
                if (savedInstanceState == null) {
                    viewModel.previewColumnHeadings(viewModel.cardsOrNotes).second
                } else {
                    BundleCompat
                        .getParcelableArrayList(
                            savedInstanceState,
                            STATE_AVAILABLE_COLUMNS,
                            ColumnWithSample::class.java,
                        )!!
                        .toList()
                }
            adapter.clear()
            adapter.addAll(availableColumns)
            adapter.notifyDataSetChanged()
        }

        listView.setOnItemClickListener { _, _, position, _ ->
            val selected = adapter.getItem(position)
            if (selected == null || selected.label == getString(CommonString.no_columns_available)) {
                Timber.d("Ignoring click on 'No Columns Available'")
                return@setOnItemClickListener
            }
            viewModel.updateSelectedColumn(columnToReplace, selected)
            dismissAllowingStateLoss()
        }

        val container =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(listView)
            }

        return AlertDialog
            .Builder(requireActivity())
            .setTitle(getString(CommonString.manage_browser_column))
            .setView(container)
            .setNegativeButton(android.R.string.cancel) { _, _ -> dismissAllowingStateLoss() }
            .create()
    }

    companion object {
        const val TAG = "ColumnSelectionDialog"

        private const val ARG_SELECTED_COLUMN = "selected_column"
        private const val STATE_AVAILABLE_COLUMNS = "availableColumns"

        fun newInstance(selectedColumn: ColumnHeading): ColumnSelectionDialogFragment =
            ColumnSelectionDialogFragment().apply {
                arguments = Bundle().apply { putParcelable(ARG_SELECTED_COLUMN, selectedColumn) }
            }
    }
}

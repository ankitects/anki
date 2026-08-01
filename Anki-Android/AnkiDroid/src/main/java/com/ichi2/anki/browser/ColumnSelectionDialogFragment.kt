/*
 *  Copyright (c) 2025 Siddhesh Jondhale <jondhale2004@gmail.com>
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
import com.ichi2.anki.R
import com.ichi2.anki.databinding.ItemColumnSelectionBinding
import com.ichi2.anki.utils.ext.requireParcelable
import com.ichi2.utils.dp
import com.ichi2.utils.setPaddingRelative
import kotlinx.coroutines.launch
import timber.log.Timber

class ColumnSelectionDialogFragment : DialogFragment() {
    private val viewModel: CardBrowserViewModel by activityViewModels()
    private val columnToReplace: ColumnHeading by lazy {
        requireArguments().requireParcelable(SELECTED_COLUMN)
    }

    private var availableColumns: List<ColumnWithSample> = emptyList()

    override fun onSaveInstanceState(outState: Bundle) {
        with(outState) {
            outState.putParcelableArrayList(AVAILABLE_COLUMNS, availableColumns.toCollection(ArrayList()))
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
                        column?.label ?: getString(R.string.no_columns_available)

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
                    BundleCompat.getParcelableArrayList(savedInstanceState, AVAILABLE_COLUMNS, ColumnWithSample::class.java)!!.toList()
                }
            adapter.clear()
            adapter.addAll(availableColumns)
            adapter.notifyDataSetChanged()
        }

        listView.setOnItemClickListener { _, _, position, _ ->
            val selected = adapter.getItem(position)
            if (selected == null || selected.label == getString(R.string.no_columns_available)) {
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
            .setTitle(getString(R.string.manage_browser_column))
            .setView(container)
            .setNegativeButton(android.R.string.cancel) { _, _ -> dismissAllowingStateLoss() }
            .create()
    }

    companion object {
        const val TAG = "ColumnSelectionDialog"

        private const val SELECTED_COLUMN = "selected_column"
        private const val AVAILABLE_COLUMNS = "availableColumns"

        fun newInstance(selectedColumn: ColumnHeading): ColumnSelectionDialogFragment =
            ColumnSelectionDialogFragment().apply {
                arguments = Bundle().apply { putParcelable(SELECTED_COLUMN, selectedColumn) }
            }
    }
}

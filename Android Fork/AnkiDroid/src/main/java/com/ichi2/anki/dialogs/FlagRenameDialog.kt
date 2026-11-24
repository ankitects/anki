// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.dialogs

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ichi2.anki.CommonString
import com.ichi2.anki.Flag
import com.ichi2.anki.databinding.DialogRenameFlagBinding
import com.ichi2.utils.customView
import com.ichi2.utils.title
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * A DialogFragment for renaming flags through a RecyclerView.
 */
class FlagRenameDialog : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogRenameFlagBinding.inflate(requireActivity().layoutInflater)
        val builder =
            AlertDialog.Builder(requireContext()).apply {
                customView(view = binding.root, 4, 4, 4, 4)
                title(CommonString.rename_flag)
            }
        val dialog = builder.create()

        setupRecyclerView(binding)
        return dialog
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        activity?.invalidateOptionsMenu()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.clearFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
        )
    }

    private fun setupRecyclerView(binding: DialogRenameFlagBinding) =
        requireActivity().lifecycleScope.launch {
            val flagItems = createFlagList()
            val flagAdapter = FlagAdapter(lifecycleScope = lifecycleScope)
            binding.recyclerViewFlags.adapter = flagAdapter
            flagAdapter.submitList(flagItems)
            binding.recyclerViewFlags.layoutManager = LinearLayoutManager(requireContext())
        }

    private suspend fun createFlagList(): List<FlagItem> {
        Timber.d("Creating flag list")
        return Flag
            .queryDisplayNames(requireContext())
            .filter { it.key != Flag.NONE }
            .map { (flag, displayName) ->
                FlagItem(
                    flag = flag,
                    title = displayName,
                    icon = flag.drawableRes,
                )
            }
    }
}

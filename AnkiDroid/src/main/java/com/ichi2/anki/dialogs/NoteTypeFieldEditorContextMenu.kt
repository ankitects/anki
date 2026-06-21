// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import com.ichi2.anki.NoteTypeFieldEditor
import com.ichi2.anki.R
import com.ichi2.anki.analytics.AnalyticsDialogFragment
import com.ichi2.utils.create
import timber.log.Timber

/**
 * [NoteTypeFieldEditor]'s context menu
 */
class NoteTypeFieldEditorContextMenu : AnalyticsDialogFragment() {
    @SuppressLint("CheckResult")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val availableItems = NoteTypeFieldEditorContextMenuAction.entries.sortedBy { it.order }

        return AlertDialog.Builder(requireActivity()).create {
            setTitle(requireArguments().getString(KEY_LABEL))
            setItems(availableItems.map { resources.getString(it.actionTextId) }.toTypedArray()) { _, index ->
                (activity as? NoteTypeFieldEditor)?.run { handleAction(availableItems[index]) }
                    ?: Timber.e("ContextMenu used from outside of its target activity!")
            }
        }
    }

    enum class NoteTypeFieldEditorContextMenuAction(
        val order: Int,
        @StringRes val actionTextId: Int,
    ) {
        Reposition(0, R.string.model_field_editor_reposition_menu),
        Sort(1, R.string.model_field_editor_sort_field),
        Rename(2, R.string.model_field_editor_rename),
        Delete(3, R.string.model_field_editor_delete),
        AddLanguageHint(4, R.string.model_field_editor_language_hint),
    }

    companion object {
        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        const val KEY_LABEL = "key_label"

        fun newInstance(label: String): NoteTypeFieldEditorContextMenu =
            NoteTypeFieldEditorContextMenu().apply {
                arguments = Bundle().apply { putString(KEY_LABEL, label) }
            }
    }
}

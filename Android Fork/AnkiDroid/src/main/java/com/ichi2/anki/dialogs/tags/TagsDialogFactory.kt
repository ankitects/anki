// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Tarek Mohamed <tarekkma@gmail.com>

package com.ichi2.anki.dialogs.tags

import androidx.annotation.MainThread
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.ichi2.anki.dialogs.tags.TagsDialog.DialogType
import com.ichi2.anki.libanki.NoteId
import com.ichi2.anki.utils.ext.DIALOG_FRAGMENT_TAG
import com.ichi2.anki.utils.ext.showDialogFragment
import com.ichi2.utils.ExtendedFragmentFactory
import timber.log.Timber

class TagsDialogFactory(
    val listener: TagsDialogListener,
) : ExtendedFragmentFactory() {
    override fun instantiate(
        classLoader: ClassLoader,
        className: String,
    ): Fragment {
        val cls = loadFragmentClass(classLoader, className)
        return if (cls == TagsDialog::class.java) {
            newTagsDialog()
        } else {
            super.instantiate(classLoader, className)
        }
    }

    private fun newTagsDialog(): TagsDialog = TagsDialog(listener)

    /** Preserves an open tag editor or filter and its unconfirmed selections. */
    @MainThread
    fun show(
        activity: FragmentActivity,
        type: DialogType = DialogType.EDIT_TAGS,
        noteIds: List<NoteId> = emptyList(),
        checkedTags: ArrayList<String> = arrayListOf(),
    ) {
        val existingDialog = activity.supportFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG)
        if (type != DialogType.CUSTOM_STUDY && existingDialog is TagsDialog && existingDialog.type == type) {
            Timber.d("Ignoring tags request (%s): dialog is already open", type)
            return
        }
        val dialog = newTagsDialog().withArguments(activity, type, noteIds, checkedTags)
        activity.showDialogFragment(dialog)
    }
}

/*
 * Copyright (c) 2025 lukstbit <52494258+lukstbit@users.noreply.github.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.dialogs.customstudy

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.databinding.FragmentTagLimitBinding
import com.ichi2.anki.dialogs.customstudy.IncludedExcludedTagsAdapter.TagsSelectionMode.Exclude
import com.ichi2.anki.dialogs.customstudy.IncludedExcludedTagsAdapter.TagsSelectionMode.Include
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.utils.customView
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.title
import kotlinx.coroutines.launch

/**
 * Fragment that allows the user to select tags to include and/or exclude for the custom study
 * session that is being created. Similar to the desktop TagLimit class.
 * https://github.com/ankitects/anki/blob/main/qt/aqt/taglimit.py#L17
 *
 * Will return, as a fragment result, two lists of tags(as Strings) representing the included and
 * excluded tags for custom studying.
 *
 * @see CustomStudyDialog
 */
class TagLimitFragment : DialogFragment() {
    private lateinit var binding: FragmentTagLimitBinding

    private val deckId
        get() = requireArguments().getLong(ARG_DECK_ID)
    private lateinit var tagsIncludedAdapter: IncludedExcludedTagsAdapter
    private lateinit var tagsExcludedAdapter: IncludedExcludedTagsAdapter

    override fun onAttach(context: Context) {
        super.onAttach(context)
        tagsIncludedAdapter = IncludedExcludedTagsAdapter(context, Include)
        tagsExcludedAdapter = IncludedExcludedTagsAdapter(context, Exclude)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = FragmentTagLimitBinding.inflate(layoutInflater)
        binding.tagsIncluded.adapter = tagsIncludedAdapter
        binding.tagsExcluded.adapter = tagsExcludedAdapter

        val includeCheck =
            binding.requireOneOrMoreCheckBox.apply {
                text = TR.customStudyRequireOneOrMoreOfThese()
                setOnCheckedChangeListener { _, isChecked ->
                    tagsIncludedAdapter.isEnabled = isChecked
                }
            }
        binding.excludeLabel.text =
            TR.customStudySelectTagsToExclude()
        val dialog =
            AlertDialog
                .Builder(requireContext())
                .title(text = TR.sentenceCase.selectiveStudy)
                .customView(binding.root)
                .negativeButton(R.string.dialog_cancel)
                .positiveButton(R.string.dialog_ok, null)
                .create()

        var allowSubmit = true
        // we set the listener here so 'ok' doesn't immediately close the dialog because we don't
        // allow more than 100 tags combined to be selected. In this situation we don't close the
        // dialog(so the user can still act to fix this) and show a Snackbar
        dialog.setOnShowListener {
            dialog.positiveButton.setOnClickListener {
                // prevent race conditions
                if (!allowSubmit) return@setOnClickListener
                allowSubmit = false
                // take the selection only if the require checkbox is currently checked
                val tagsToInclude =
                    if (includeCheck.isChecked) {
                        tagsIncludedAdapter.tags.filter { it.isIncluded }.map { it.name }
                    } else {
                        emptyList()
                    }
                val tagsToExclude =
                    tagsExcludedAdapter.tags.filter { it.isExcluded }.map { it.name }
                if (tagsToInclude.size + tagsToExclude.size > 100) {
                    showSnackbar(TR.errors100TagsMax().withCollapsedWhitespace())
                    return@setOnClickListener
                }
                // send the selection to the custom study dialog to setup the session
                setFragmentResult(
                    REQUEST_CUSTOM_STUDY_TAGS,
                    Bundle().apply {
                        putStringArrayList(KEY_INCLUDED_TAGS, ArrayList(tagsToInclude))
                        putStringArrayList(KEY_EXCLUDED_TAGS, ArrayList(tagsToExclude))
                    },
                )
                dismiss()
            }
        }
        return dialog
    }

    // https://github.com/ankitects/anki/blob/main/pylib/anki/lang.py#L243
    private fun String.withCollapsedWhitespace(): String = replace("\\s+", " ")

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            binding.loadingViews.isVisible = true
            binding.contentViews.isVisible = false
            val customStudyDefaults = withCol { sched.customStudyDefaults(deckId) }
            binding.requireOneOrMoreCheckBox.isChecked =
                customStudyDefaults.tagsList.any { tag -> tag.include }
            val tags =
                customStudyDefaults.tagsList.map { backendTag ->
                    TagIncludedExcluded(backendTag.name, backendTag.include, backendTag.exclude)
                }
            tagsIncludedAdapter.tags = tags.toMutableList() // make a copy
            tagsExcludedAdapter.tags = tags.toMutableList() // make a copy
            binding.loadingViews.isVisible = false
            binding.contentViews.isVisible = true
        }
    }

    companion object {
        const val TAG = "TagLimitFragment"
        const val REQUEST_CUSTOM_STUDY_TAGS = "request_custom_study_tags"
        const val KEY_INCLUDED_TAGS = "key_included_tags"
        const val KEY_EXCLUDED_TAGS = "key_excluded_tags"
        private const val ARG_DECK_ID = "arg_deck_id"

        fun newInstance(deckId: DeckId) =
            TagLimitFragment().apply {
                arguments = Bundle().apply { putLong(ARG_DECK_ID, deckId) }
            }
    }
}

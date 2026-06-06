/*
 *  Copyright (c) 2024 Brayan Oliveira <brayandso.dev@gmail.com>
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
package com.ichi2.anki.previewer

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.ichi2.anki.R
import com.ichi2.anki.databinding.FragmentTemplatePreviewerBinding
import com.ichi2.anki.libanki.CardOrdinal
import com.ichi2.anki.snackbar.BaseSnackbarBuilderProvider
import com.ichi2.anki.snackbar.SnackbarBuilder
import com.ichi2.anki.workarounds.SafeWebViewLayout
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import timber.log.Timber

class TemplatePreviewerFragment :
    CardViewerFragment(R.layout.fragment_template_previewer),
    BaseSnackbarBuilderProvider {
    override val viewModel: TemplatePreviewerViewModel by viewModels()

    lateinit var binding: FragmentTemplatePreviewerBinding

    override val webViewLayout: SafeWebViewLayout get() = binding.webViewLayout

    override val baseSnackbarBuilder: SnackbarBuilder
        get() = { anchorView = binding.showAnswer }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        // binding must be set before super.onViewCreated
        // as super.onViewCreated depends on webViewLayout, which depends on the binding
        binding = FragmentTemplatePreviewerBinding.bind(view)

        // The backing NotetypeFile may be missing after process death if the OS cleared
        // its cache dir. Bail out before the ViewModel is constructed; the constructor
        // would otherwise throw on `getNotetype()`.
        if (!TemplatePreviewerArguments.isUsable(requireArguments())) {
            Timber.w("Notetype file missing on previewer open; finishing")
            requireActivity().finish()
            return
        }

        super.onViewCreated(view, savedInstanceState)

        binding.showAnswer.setOnClickListener { viewModel.toggleShowAnswer() }
        viewModel.showingAnswer
            .onEach { showingAnswer ->
                binding.showAnswer.text =
                    if (showingAnswer) {
                        getString(R.string.hide_answer)
                    } else {
                        getString(R.string.show_answer)
                    }
            }.launchIn(lifecycleScope)

        binding.webViewContainer.setFrameStyle()
    }

    /**
     * Updates the content displayed in the previewer with the provided fields and tags
     *
     * Should not be called for cloze deletions, since they they have dynamic ord
     *
     * @param fields The list of field values to display
     * @param tags The list of tags associated with the note
     */
    fun updateContent(
        fields: List<String>,
        tags: List<String>,
    ) {
        viewModel.updateContent(fields, tags)
    }

    /**
     * Retrieves a safe cloze ordinal number for cloze deletions.
     *
     * @return The safe cloze ordinal number
     */
    suspend fun getSafeClozeOrd(): CardOrdinal = viewModel.getSafeClozeOrd()

    companion object {
        const val ARGS_KEY = "templatePreviewerArgs"

        fun newInstance(arguments: TemplatePreviewerArguments): TemplatePreviewerFragment =
            TemplatePreviewerFragment().apply {
                val args = Bundle().apply { putParcelable(ARGS_KEY, arguments) }
                this.arguments = args
            }
    }
}

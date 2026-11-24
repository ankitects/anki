// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.mediacheck

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.annotation.CallSuper
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CommonPlurals
import com.ichi2.anki.CommonString
import com.ichi2.anki.CrashReportData.Companion.toCrashReportData
import com.ichi2.anki.R
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.common.utils.android.getColorFromAttr
import com.ichi2.anki.databinding.FragmentMediaCheckBinding
import com.ichi2.anki.progress.observeProgress
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.anki.utils.ext.collectInViewLifecycle
import com.ichi2.anki.utils.observeMessages
import com.ichi2.anki.utils.showAndAwaitDismissal
import com.ichi2.anki.workarounds.OnWebViewRecreatedListener
import com.ichi2.anki.workarounds.SafeWebViewClient
import com.ichi2.utils.cancelable
import com.ichi2.utils.message
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import com.ichi2.utils.title
import com.ichi2.utils.toRGBHex
import dev.androidbroadcast.vbpd.viewBinding

/**
 * MediaCheckFragment for displaying a list of media files that are either unused or missing.
 * It allows users to tag missing media files or delete unused ones.
 **/
class MediaCheckFragment :
    Fragment(R.layout.fragment_media_check),
    OnWebViewRecreatedListener {
    private val viewModel: MediaCheckViewModel by viewModels()

    private val binding by viewBinding(FragmentMediaCheckBinding::bind)

    private var lastReport: String? = null

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.apply {
            setTitle(TR.sentenceCase.checkMediaTitle)
            setNavigationOnClickListener {
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }

        (requireActivity() as AppCompatActivity).setSupportActionBar(binding.toolbar)

        observeProgress(viewModel) { progress -> getString(progress.messageRes) }
        viewModel.checkMedia()

        viewModel.mediaCheckResult.collectInViewLifecycle { result ->
            updateWebView(result?.report.orEmpty())
            if (result != null) {
                binding.tagMissingMediaButton.isVisible = result.missingCount != 0
                binding.deleteUsedMediaButton.isVisible = result.unusedCount != 0
                if (result.haveTrash) setupMenu()
            }
        }

        observeMessages(viewModel.pendingMessages, viewModel::messageShown, ::showMessage)

        setupButtonListeners()
    }

    private fun setupMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(
                    menu: Menu,
                    menuInflater: MenuInflater,
                ) {
                    menuInflater.inflate(R.menu.media_check_menu, menu)
                    menu.findItem(R.id.action_restore_trash).apply {
                        isVisible = true
                        title = TR.sentenceCase.restoreDeleted
                    }
                    menu.findItem(R.id.action_empty_trash).apply {
                        isVisible = true
                        title = TR.sentenceCase.emptyTrash
                    }
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean =
                    when (menuItem.itemId) {
                        R.id.action_restore_trash -> {
                            viewModel.restoreTrash()
                            true
                        }
                        R.id.action_empty_trash -> {
                            viewModel.deleteTrash()
                            true
                        }
                        else -> false
                    }
            },
            viewLifecycleOwner,
        )
    }

    private fun updateWebView(report: String) {
        lastReport = report
        val backgroundColor = getColorFromAttr(requireContext(), android.R.attr.colorBackground)
        val textColor = getColorFromAttr(requireContext(), android.R.attr.textColorPrimary)

        val backgroundColorHex = backgroundColor.toRGBHex()
        val textColorHex = textColor.toRGBHex()

        val html =
            """
            <html>
                <body style="
                    background-color: $backgroundColorHex;
                    color: $textColorHex;
                    padding: 0px 8px;
                    font-size:14px;
                    white-space: pre-wrap;">$report
                </body>
            </html>
            """.trimIndent()

        binding.webView.setWebViewClient(object : SafeWebViewClient() {})
        binding.webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }

    override fun onWebViewRecreated() {
        lastReport?.let { updateWebView(it) }
    }

    @CallSuper
    override fun onDestroyView() {
        binding.webView.destroy()
        super.onDestroyView()
    }

    private fun setupButtonListeners() {
        binding.tagMissingMediaButton.apply {
            // mediaCheckAddTag => "Tag Missing"
            text = TR.sentenceCase.tagMissing

            setOnClickListener {
                viewModel.tagMissing(tag = TR.mediaCheckMissingMediaTag())
            }
        }

        binding.deleteUsedMediaButton.apply {
            text = TR.sentenceCase.checkMediaDeleteUnused

            setOnClickListener {
                deleteConfirmationDialog()
            }
        }
    }

    private fun deleteConfirmationDialog() {
        AlertDialog.Builder(requireContext()).show {
            message(text = TR.mediaCheckDeleteUnusedConfirm())
            positiveButton(CommonString.dialog_positive_delete) { viewModel.deleteUnusedMedia() }
            negativeButton(CommonString.dialog_cancel)
        }
    }

    private suspend fun showMessage(message: MediaCheckMessage) {
        when (message) {
            is MediaCheckMessage.TagsAdded ->
                showResultDialog(
                    CommonString.check_media_tags_added,
                    TR.browsingNotesUpdated(message.notesUpdated),
                )
            is MediaCheckMessage.MediaDeleted ->
                showResultDialog(
                    CommonString.delete_media_result_title,
                    resources.getQuantityString(CommonPlurals.delete_media_result_message, message.count, message.count),
                )
            MediaCheckMessage.TrashEmptied -> showResultDialog(message = TR.mediaCheckTrashEmptied())
            MediaCheckMessage.TrashRestored -> showResultDialog(message = TR.mediaCheckTrashRestored())
            is MediaCheckMessage.Failed -> showErrorDialog(message.exception)
        }
    }

    private suspend fun showResultDialog(
        @StringRes titleRes: Int? = null,
        message: String,
    ) {
        AlertDialog.Builder(requireContext()).showAndAwaitDismissal {
            titleRes?.let { title(it) }
            message(text = message)
            positiveButton(CommonString.dialog_ok) { requireActivity().finish() }
            cancelable(false)
        }
    }

    private suspend fun showErrorDialog(exception: Exception) {
        AlertDialog.Builder(requireContext()).showAndAwaitDismissal {
            title(CommonString.vague_error)
            message(text = exception.localizedMessage ?: exception.toString())
            positiveButton(CommonString.dialog_ok)
        }
        exception.toCrashReportData(requireActivity()).sendCrashReport()
    }

    companion object {
        fun getIntent(context: Context): Intent = SingleFragmentActivity.getIntent(context, MediaCheckFragment::class)
    }
}

@get:StringRes
private val MediaCheckProgress.messageRes: Int
    get() =
        when (this) {
            MediaCheckProgress.CHECKING_MEDIA -> CommonString.check_media_message
            MediaCheckProgress.ADDING_TAGS -> CommonString.check_media_adding_missing_tag
            MediaCheckProgress.DELETING_MEDIA -> CommonString.delete_media_message
        }

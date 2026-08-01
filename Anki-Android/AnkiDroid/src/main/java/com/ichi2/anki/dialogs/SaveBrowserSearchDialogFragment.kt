// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 lukstbit <52494258+lukstbit@users.noreply.github.com>

package com.ichi2.anki.dialogs

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.CardBrowser
import com.ichi2.anki.R
import com.ichi2.anki.browser.search.SavedSearch
import com.ichi2.anki.dialogs.SaveBrowserSearchDialogFragment.Companion.ARG_SEARCH_QUERY
import com.ichi2.anki.dialogs.SaveBrowserSearchDialogFragment.Companion.ARG_SEARCH_QUERY_NAME
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.utils.input
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import com.ichi2.utils.title
import timber.log.Timber

/**
 * Dialog that allows the user to save searches made in [CardBrowser] to make them available for
 * easy access.
 *
 * @see CardBrowser
 * @see SavedBrowserSearchesDialogFragment
 */
class SaveBrowserSearchDialogFragment : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // TODO: validation on duplicate name (case sensitive)
        val searchQuery =
            requireArguments().getString(ARG_SEARCH_QUERY) ?: error("Missing search query")
        return AlertDialog
            .Builder(requireContext())
            .show {
                title(text = getString(R.string.card_browser_list_my_searches_save))
                positiveButton(R.string.dialog_ok)
                negativeButton(R.string.dialog_cancel)
                setView(R.layout.dialog_generic_text_input)
            }.input(
                hint = getString(R.string.card_browser_list_my_searches_new_name),
                allowEmpty = false,
                displayKeyboard = true,
                waitForPositiveButton = true,
            ) { dialog, name ->
                Timber.d("Saving user search: %s with given name %s", searchQuery, name)
                setFragmentResult(
                    REQUEST_SAVE_SEARCH,
                    Bundle().apply {
                        putString(ARG_SEARCH_QUERY, searchQuery)
                        putCharSequence(ARG_SEARCH_QUERY_NAME, name)
                    },
                )
                dialog.dismiss()
            }
    }

    companion object {
        const val REQUEST_SAVE_SEARCH = "request_save_search"

        /**
         * The actual search query that the user is saving to use later.
         */
        const val ARG_SEARCH_QUERY = "arg_search_query"

        /**
         * The name given by the user to the saved search.
         */
        const val ARG_SEARCH_QUERY_NAME = "arg_search_query_name"

        fun newInstance(searchQuery: String): SaveBrowserSearchDialogFragment =
            SaveBrowserSearchDialogFragment().apply {
                arguments =
                    Bundle().apply {
                        putString(ARG_SEARCH_QUERY, searchQuery)
                    }
            }
    }
}

/**
 * Registers a fragment result listener to update [CardBrowser] after the user successfully saves a
 * previously made search from the toolbar.
 */
fun CardBrowser.registerSaveSearchHandler() {
    supportFragmentManager.setFragmentResultListener(
        SaveBrowserSearchDialogFragment.REQUEST_SAVE_SEARCH,
        this,
    ) { _, bundle ->
        val savedSearchName =
            bundle.getString(ARG_SEARCH_QUERY_NAME)
        if (savedSearchName.isNullOrEmpty()) {
            showSnackbar(
                R.string.card_browser_list_my_searches_new_search_error_empty_name,
                Snackbar.LENGTH_SHORT,
            )
            return@setFragmentResultListener
        }
        val toSave =
            SavedSearch(
                name = savedSearchName,
                query = bundle.getString(ARG_SEARCH_QUERY) ?: return@setFragmentResultListener,
            )

        launchCatchingTask {
            val saveStatus = viewModel.saveSearch(toSave)
            updateAfterUserSearchIsSaved(saveStatus)
        }
    }
}

/**
 * Registers a fragment result listener, [onSaveSearch] should handle creating the requested
 * saved search
 */
fun Fragment.registerSaveSearchHandler(onSaveSearch: (SavedSearch) -> Unit) {
    childFragmentManager.setFragmentResultListener(
        SaveBrowserSearchDialogFragment.REQUEST_SAVE_SEARCH,
        this,
    ) { _, bundle ->
        val savedSearchName =
            bundle.getString(ARG_SEARCH_QUERY_NAME)
        if (savedSearchName.isNullOrEmpty()) {
            showSnackbar(
                R.string.card_browser_list_my_searches_new_search_error_empty_name,
                Snackbar.LENGTH_SHORT,
            )
            return@setFragmentResultListener
        }

        val savedSearch =
            SavedSearch(
                name = savedSearchName,
                query = bundle.getString(ARG_SEARCH_QUERY) ?: return@setFragmentResultListener,
            )

        onSaveSearch(savedSearch)
    }
}

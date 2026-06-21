// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 Ankitects Pty Ltd <http://apps.ankiweb.net>

package com.ichi2.anki

import android.content.Intent
import android.net.Uri
import androidx.fragment.app.FragmentActivity
import anki.collection.OpChangesOnly
import anki.import_export.ImportAnkiPackageRequest
import anki.search.SearchNode
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.browser.CardBrowserViewModel
import com.ichi2.anki.libanki.importCsvRaw
import com.ichi2.anki.observability.undoableOp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun importAnkiPackageUndoable(input: ByteArray): ByteArray {
    val request = ImportAnkiPackageRequest.parseFrom(input)
    val path = Uri.encode(request.packagePath, "/")
    return withContext(Dispatchers.Main) {
        val output = withCol { importAnkiPackage(path, request.options) }
        undoableOp { output.changes }
        output.toByteArray()
    }
}

suspend fun importCsvRaw(input: ByteArray): ByteArray =
    withContext(Dispatchers.Main) {
        val output = withCol { importCsvRaw(input) }
        val changes = OpChangesOnly.parseFrom(output)
        undoableOp { changes }
        output
    }

/**
 * Css to hide the "Show" button from the final backend import page. As the user could import a lot
 * of notes, on pressing "Show" the native CardBrowser would be called with a search query
 * comprising of all the notes ids. This would result in a crash or very slow behavior in the
 * CardBrowser.
 *
 * NOTE: this should be used only with [android.webkit.WebView.evaluateJavascript].
 */
val hideShowButtonCss =
    """
    javascript:(
        function() {
            var hideShowButtonStyle = '.desktop-only { display: none !important; }';
            var newStyle = document.createElement('style');                    
            newStyle.appendChild(document.createTextNode(hideShowButtonStyle));
            document.head.appendChild(newStyle);       
        }
    )()
    """.trimIndent()

/**
 * Calls the native [CardBrowser] to display the results of the search query constructed from the
 * input. This method will always return the received input.
 */
suspend fun FragmentActivity.searchInBrowser(input: ByteArray): ByteArray {
    val searchString = withCol { buildSearchString(listOf(SearchNode.parseFrom(input))) }
    val starterIntent =
        Intent(this, CardBrowser::class.java).apply {
            putExtra(CardBrowserViewModel.EXTRA_SEARCH_QUERY, searchString)
            putExtra(CardBrowserViewModel.EXTRA_ALL_DECKS, true)
        }
    startActivity(starterIntent)
    return input
}

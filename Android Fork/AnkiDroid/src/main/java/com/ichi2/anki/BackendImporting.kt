// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 Ankitects Pty Ltd <http://apps.ankiweb.net>

package com.ichi2.anki

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.withStarted
import anki.collection.OpChangesOnly
import anki.import_export.ImportAnkiPackageRequest
import anki.search.SearchNode
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.common.destinations.BrowserDestination
import com.ichi2.anki.common.destinations.navigate
import com.ichi2.anki.libanki.importCsvRaw
import com.ichi2.anki.observability.undoableOp
import com.ichi2.utils.ImportUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

suspend fun importAnkiPackageUndoable(input: ByteArray): ByteArray {
    val request = ImportAnkiPackageRequest.parseFrom(input)
    // The page decodes the cached path from ImportUtils; restore its encoded filename.
    val path = ImportUtils.encodeFileName(File(request.packagePath))
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
 *
 * @param awaitPageShown called once the query is built, as the page may be hidden while the collection is busy
 */
suspend fun FragmentActivity.searchInBrowser(
    input: ByteArray,
    awaitPageShown: suspend () -> Unit,
): ByteArray {
    val searchString = withCol { buildSearchString(listOf(SearchNode.parseFrom(input))) }
    awaitPageShown()
    // Collection access can suspend past onStop, so wait until the activity is visible again.
    withStarted {
        navigate(BrowserDestination.Search(query = searchString, allDecks = true))
    }
    return input
}

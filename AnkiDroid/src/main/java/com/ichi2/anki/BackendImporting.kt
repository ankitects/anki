/*
 * Copyright (c) 2022 Ankitects Pty Ltd <http://apps.ankiweb.net>
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

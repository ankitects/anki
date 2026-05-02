/*
 *  Copyright (c) 2022 Brayan Oliveira <brayandso.dev@gmail.com>
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
package com.ichi2.anki.pages

import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintJob
import android.print.PrintManager
import android.view.View
import androidx.core.content.ContextCompat.getSystemService
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.common.time.getTimestamp
import com.ichi2.anki.databinding.PageStatisticsBinding
import com.ichi2.anki.dialogs.registerDeckSelectedHandler
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.model.SelectableDeck
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.startDeckSelection
import com.ichi2.anki.withProgress
import dev.androidbroadcast.vbpd.viewBinding
import timber.log.Timber

class Statistics : PageFragment(R.layout.page_statistics) {
    override val pagePath: String = "graphs"
    private val binding by viewBinding(PageStatisticsBinding::bind)

    /** The PrintJob launched by this Fragment */
    // After killing the app, printManager.printJobs can still list active jobs
    private var pendingPrintJob: PrintJob? = null

    @Suppress("deprecation", "API35 properly handle edge-to-edge")
    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        binding.deckName.setOnClickListener { startDeckSelection(all = false, filtered = false) }
        binding.appBar
            .addLiftOnScrollListener { _, backgroundColor ->
                activity?.window?.statusBarColor = backgroundColor
            }

        binding.toolbar.apply {
            menu.findItem(R.id.action_export_stats).title = CollectionManager.TR.statisticsSavePdf()
            setOnMenuItemClickListener { item ->
                if (item.itemId == R.id.action_export_stats) {
                    exportWebViewContentAsPDF()
                }
                true
            }
        }
        registerDeckSelectedHandler(action = ::onDeckSelected)
        requireActivity().launchCatchingTask {
            withProgress {
                val deckName =
                    savedInstanceState?.getString(KEY_DECK_NAME, null) ?: withCol { decks.current().name }
                changeDeck(deckName)
            }
        }
    }

    /** Prepares and initiates a printing task for the content(stats) displayed in the WebView.
     * It uses the Android PrintManager service to create a print job, based on the content of the WebView.
     * The resulting output is a PDF document. **/
    private fun exportWebViewContentAsPDF() {
        if (pendingPrintJob?.isActive == true) {
            Timber.w("Duplicate print attempted - skipping")
            showSnackbar(R.string.already_in_progress)
            return
        }
        Timber.i("Saving Stats to PDF")
        val printManager = getSystemService(requireContext(), PrintManager::class.java) ?: return
        val currentDateTime = getTimestamp(TimeManager.time)
        val jobName = "${getString(R.string.app_name)}-stats-$currentDateTime"
        val printAdapter = webViewLayout.createPrintDocumentAdapter(jobName)
        pendingPrintJob =
            printManager.print(
                jobName,
                printAdapter,
                PrintAttributes.Builder().build(),
            )
    }

    private fun onDeckSelected(deck: SelectableDeck?) {
        if (deck == null) return
        require(deck is SelectableDeck.Deck)
        changeDeck(deck.name)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_DECK_NAME, binding.deckName.text.toString())
    }

    /**
     * Updates the ui with the new selected deck. Doesn't change the backend.
     *
     * This method includes a workaround to change the deck in the webview by finding the text box
     * and replacing the deck name with the selected deck name from the dialog and updating the
     * stats. See issue #3394 in Anki repository.
     **/
    private fun changeDeck(selectedDeckName: String) {
        binding.deckName.text = selectedDeckName
        val javascriptCode =
            """
            var textBox = document.getElementById("statisticsSearchText");
            textBox.value = "deck:\"$selectedDeckName\"";
            textBox.dispatchEvent(new Event("input", { bubbles: true }));
            textBox.dispatchEvent(new Event("change"));
            """.trimIndent()
        webViewLayout.evaluateJavascript(javascriptCode, null)
    }

    companion object {
        private const val KEY_DECK_NAME = "key_deck_name"
    }
}

private val PrintJob.isActive: Boolean
    get() = !isCompleted && !isFailed && !isCancelled

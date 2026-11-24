// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintJob
import android.print.PrintManager
import android.view.View
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat.getSystemService
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.common.time.getTimestamp
import com.ichi2.anki.databinding.PageStatisticsBinding
import com.ichi2.anki.dialogs.registerDeckSelectedHandler
import com.ichi2.anki.dialogs.startDeckSelection
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.model.SelectableDeck
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.updateBottomNavOnScroll
import com.ichi2.anki.withProgress
import dev.androidbroadcast.vbpd.viewBinding
import timber.log.Timber

class Statistics : PageFragment(R.layout.page_statistics) {
    override val pagePath: String = "graphs"
    private val binding by viewBinding(PageStatisticsBinding::bind)

    /** The PrintJob launched by this Fragment */
    // After killing the app, printManager.printJobs can still list active jobs
    private var pendingPrintJob: PrintJob? = null

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        // Hide the back arrow when requested (e.g. hosted in bottom nav)
        if (arguments?.getBoolean(ARG_HIDE_BACK_BUTTON) == true) {
            binding.toolbar.navigationIcon = null
        }

        binding.deckName.setOnClickListener {
            startDeckSelection(allowAll = false, allowFiltered = false, skipEmptyDefault = true)
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
                    savedInstanceState?.getString(STATE_DECK_NAME, null) ?: withCol { decks.current().name }
                changeDeck(deckName)
            }
        }
    }

    override fun onWebViewCreated() {
        // Also called when the WebView is recreated after a renderer crash, so the listener is re-attached.
        webViewLayout.setOnScrollChangeListener { webView, _, scrollY, _, oldScrollY ->
            val dy = scrollY - oldScrollY
            // Hiding the bar makes the page taller, which pulls a scroll at the bottom back up.
            // That scroll comes from the resize, not the user: reacting to it would show the bar again.
            if (!webView.scrolledUpToBottom(dy)) updateBottomNavOnScroll(dy)
        }
    }

    /** Prepares and initiates a printing task for the content(stats) displayed in the WebView.
     * It uses the Android PrintManager service to create a print job, based on the content of the WebView.
     * The resulting output is a PDF document. **/
    private fun exportWebViewContentAsPDF() {
        if (pendingPrintJob?.isActive == true) {
            Timber.w("Duplicate print attempted - skipping")
            showSnackbar(CommonString.already_in_progress)
            return
        }
        Timber.i("Saving Stats to PDF")
        val printManager = getSystemService(requireContext(), PrintManager::class.java) ?: return
        val currentDateTime = getTimestamp(TimeManager.time)
        val jobName = "${getString(R.string.app_name)}-stats-$currentDateTime"
        val printAdapter =
            webViewLayout.createPrintDocumentAdapter(jobName) ?: run {
                Timber.w("Skipping stats PDF export; WebView is destroyed")
                return
            }
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
        outState.putString(STATE_DECK_NAME, binding.deckName.text.toString())
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
        const val ARG_HIDE_BACK_BUTTON = "hideBackButton"
        private const val STATE_DECK_NAME = "key_deck_name"
    }
}

private val PrintJob.isActive: Boolean
    get() = !isCompleted && !isFailed && !isCancelled

/**
 * Whether scrolling up by [dy] left this view at the bottom of its scrollable content.
 *
 * Scrolling up moves away from the bottom, so this only happens when the view grew and scrolled
 * itself back into range.
 */
@VisibleForTesting
internal fun View.scrolledUpToBottom(dy: Int) = dy < 0 && canScrollVertically(-1) && !canScrollVertically(1)

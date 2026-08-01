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

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import anki.collection.OpChanges
import anki.collection.Progress
import com.google.android.material.appbar.MaterialToolbar
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.ProgressContext
import com.ichi2.anki.R
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.updateDeckConfigsRaw
import com.ichi2.anki.observability.undoableOp
import com.ichi2.anki.utils.openUrl
import com.ichi2.anki.withProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

@NeedsTest("15130: pressing back: icon + button should return to options if the manual is open")
@NeedsTest("17905: pressing back before the webpage is ready closes the screen")
class DeckOptions : PageFragment() {
    private val deckId: DeckId by lazy { requireArguments().getLong(KEY_DECK_ID) }

    override val pagePath: String by lazy {
        val deckId = requireArguments().getLong(KEY_DECK_ID)
        "deck-options/$deckId"
    }
    private var webViewIsReady = false

    /**
     * Callback enabled when the manual is opened in the deck options.
     * It requests the webview to go back to the Deck Options.
     */
    private val onBackFromManual =
        object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                Timber.v("webView: navigating back")
                webViewLayout.goBack()
            }
        }

    /**
     * Callback used when nothing is on top of the deck options, neither manual nor modal.
     * It sends the webview a request to deal with the closing request, requesting confirmation if
     * that would lose the local changes and otherwise close the webview.
     */
    private val onBackFromDeckOptions =
        object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Timber.v("DeckOptions: requesting the webview to handle the user close request.")
                if (webViewIsReady) {
                    webViewLayout.evaluateJavascript("anki.deckOptionsPendingChanges()") {
                        // Callback is handled in the WebView:
                        //  * A 'discard changes' dialog may be shown, using confirm()
                        //  * if no changes, or changes discarded, `deckOptionsRequireClose` is called
                        //    which PostRequestHandler handles and calls on this fragment

                        // Used to handle an edge-case when the page could not be fully loaded and therefore the anki-call is unavailable
                        value ->
                        if (value == "null") {
                            actuallyClose()
                        }
                    }
                } else {
                    // The webview is not yet loaded, no change could have occurred, we can safely close it.
                    actuallyClose()
                }
            }
        }

    /**
     * Close the view, discarding change if needed.
     */
    fun actuallyClose() {
        onBackFromDeckOptions.isEnabled = false
        Timber.v("webView: navigating back")
        launchCatchingTask {
            // Required to be in a task to ensure the callback is disabled.
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    /**
     * Callback used when a modal is opened in the webview. It requests the webview to close it.
     */
    @NeedsTest("disabled by default")
    @NeedsTest("enabled if a modal is displayed")
    @NeedsTest("disabled if a modal is hidden")
    @NeedsTest("disabled if back button is pressed: no error")
    @NeedsTest("disabled if back button is pressed: with error closing modal")
    private val onBackFromModal =
        object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                Timber.i("back button: closing displayed modal")
                try {
                    webViewLayout.evaluateJavascript(
                        """
                        document.getElementsByClassName("modal show")[0]
                        .getElementsByClassName("btn-close")[0].click()
                        """.trimIndent(),
                    ) {}
                } catch (e: Exception) {
                    CrashReportService.sendExceptionReport(e, "DeckOptions:onCloseBootstrapModalCallback")
                } finally {
                    // Even if we fail, disable the callback so the next call succeeds
                    this.isEnabled = false
                }
            }
        }

    /**
     * Listens to bootstrap open and close events
     */
    inner class ModalJavaScriptInterfaceListener {
        @JavascriptInterface
        fun onEvent(request: String) {
            when (request) {
                "open" -> {
                    Timber.d("WebVew modal opened")
                    onBackFromModal.isEnabled = true
                }
                "close" -> {
                    Timber.d("WebView modal closed")
                    onBackFromModal.isEnabled = false
                }
                else -> Timber.w("Unknown command: $request")
            }
        }
    }

    /** @see onWebViewReady */
    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        pageLoadingIndicator.isVisible = true
        super.onViewCreated(view, savedInstanceState)
        launchCatchingTask {
            val deckName = withCol { decks.name(deckId, default = true) }
            view.findViewById<MaterialToolbar>(R.id.toolbar).title = deckName
        }
    }

    override fun onWebViewCreated() {
        // addJavascriptInterface needs to happen before loadUrl
        webViewLayout.addJavascriptInterface(ModalJavaScriptInterfaceListener(), "ankidroid")
        Timber.d("Added JS Interface: 'ankidroid")
    }

    @NeedsTest("going back on a manual page takes priority over closing a modal")
    override fun onCreateWebViewClient(savedInstanceState: Bundle?): PageWebViewClient {
        activity?.onBackPressedDispatcher?.addCallback(this, onBackFromDeckOptions)
        activity?.onBackPressedDispatcher?.addCallback(this, onBackFromModal)
        // going back on a manual page takes priority over closing a modal
        activity?.onBackPressedDispatcher?.addCallback(this, onBackFromManual)

        return object : PageWebViewClient() {
            private val ankiManualHostRegex = Regex("^docs\\.ankiweb\\.net$")

            /** @see onWebViewReady */
            override fun onShowWebView(webView: WebView) {
                // no-op: handled in onVebViewReady
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?,
            ): Boolean {
                // #16715: ensure that the fragment can't be used for general web browsing
                val host = request?.url?.host ?: return shouldOverrideUrlLoading(view, request)
                return if (ankiManualHostRegex.matches(host)) {
                    super.shouldOverrideUrlLoading(view, request)
                } else {
                    openUrl(request.url)
                    true
                }
            }
        }.apply {
            onPageFinishedCallbacks.add { view ->
                Timber.v("canGoBack: %b", view.canGoBack())
                onBackFromManual.isEnabled = view.canGoBack()
                // reset the modal state on page load
                // clicking a link to the online manual closes the modal and reloads the page
                onBackFromModal.isEnabled = false
                listenToModalShowHideEvents()
            }
        }
    }

    /**
     * Passes bootstrap modal show/hide events to [ModalJavaScriptInterfaceListener]
     */
    private fun listenToModalShowHideEvents() {
        // this function is called multiple times on one document, only register the listener once
        // we use the command name as this is a valid identifier
        fun getListenerJs(
            event: String,
            command: String,
        ): String =
            """
            if (!document.added$command) {
                console.log("listening to '$command'");
                document.added$command = true
                document.addEventListener("$event", () => { ankidroid.onEvent("$command"); })
            }"""

        // event names:
        // https://github.com/ankitects/anki/blob/85f034b144ea17f90319b76d2c7d0feaa491eaa5/ts/lib/components/HelpModal.svelte
        val openJs = getListenerJs("shown.bs.modal", "open")
        val closeJs = getListenerJs("hidden.bs.modal", "close")

        webViewLayout.evaluateJavascript(openJs) {}
        webViewLayout.evaluateJavascript(closeJs) {}
    }

    fun onWebViewReady() {
        Timber.d("WebView ready to receive input")
        webViewIsReady = true
        webViewLayout.isVisible = true
        pageLoadingIndicator.isVisible = false
    }

    companion object {
        private const val KEY_DECK_ID = "deckId"

        fun getIntent(
            context: Context,
            deckId: DeckId,
        ): Intent =
            SingleFragmentActivity.getIntent(
                context,
                fragmentClass = DeckOptions::class,
                arguments = Bundle().apply { putLong(KEY_DECK_ID, deckId) },
            )
    }
}

suspend fun FragmentActivity.updateDeckConfigsRaw(input: ByteArray): ByteArray {
    val output =
        withContext(Dispatchers.Main) {
            withProgress(
                extractProgress = {
                    text = this.toOptimizingPresetString() ?: getString(R.string.dialog_processing)
                },
            ) {
                withContext(Dispatchers.IO) {
                    withCol { updateDeckConfigsRaw(input) }
                }
            }
        }
    undoableOp { OpChanges.parseFrom(output) }
    withContext(Dispatchers.Main) { finish() }
    return output
}

/**
 * ```
 * Optimizing preset 1/20
 * 5.2% of 1000 reviews
 * ```
 *
 * @return the above string, or `null` if [ProgressContext] has no
 * [compute parameters][Progress.hasComputeParams]
 */
private fun ProgressContext.toOptimizingPresetString(): String? {
    if (!progress.hasComputeParams()) return null

    val value = progress.computeParams
    val label =
        TR.deckConfigOptimizingPreset(
            currentCount = value.currentPreset,
            totalCount = value.totalPresets,
        )
    val pct = if (value.total > 0) (value.current.toDouble() / value.total.toDouble() * 100.0) else 0.0
    val reviewsLabel =
        TR.deckConfigPercentOfReviews(
            pct = "%.1f".format(pct),
            reviews = value.reviews,
        )
    return label + "\n" + reviewsLabel
}

private fun FragmentActivity.requireDeckOptionsFragment(): DeckOptions {
    require(this is SingleFragmentActivity) { "activity must be SingleFragmentActivity" }
    return requireNotNull(this.fragment as? DeckOptions?) { "fragment must be DeckOptions" }
}

/**
 * Called when Deck Options WebView is ready to receive requests.
 */
fun FragmentActivity.deckOptionsReady(input: ByteArray): ByteArray {
    requireDeckOptionsFragment().onWebViewReady()
    return input
}

/**
 * Force closing the deck options
 *
 * This is called after a 'discard changes?' dialog is accepted
 */
fun FragmentActivity.deckOptionsRequireClose(input: ByteArray): ByteArray {
    requireDeckOptionsFragment().actuallyClose()
    return input
}

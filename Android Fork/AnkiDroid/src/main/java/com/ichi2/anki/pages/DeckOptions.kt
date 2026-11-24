// SPDX-License-Identifier: GPL-3.0-or-later

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
import anki.collection.ComputeParamsProgress
import anki.collection.OpChanges
import anki.deck_config.UpdateDeckConfigsMode
import anki.deck_config.UpdateDeckConfigsRequest
import com.google.android.material.appbar.MaterialToolbar
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.CommonString
import com.ichi2.anki.ProgressContext
import com.ichi2.anki.R
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.updateDeckConfigsRaw
import com.ichi2.anki.observability.undoableOp
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.withProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger

@NeedsTest("15130: pressing back: icon + button should return to options if the manual is open")
@NeedsTest("17905: pressing back before the webpage is ready closes the screen")
class DeckOptions : PageFragment() {
    private val deckId: DeckId by lazy { requireArguments().getLong(ARG_DECK_ID) }

    override val pagePath: String by lazy {
        val deckId = requireArguments().getLong(ARG_DECK_ID)
        "deck-options/$deckId"
    }
    private var webViewIsReady = false
    private val saveAndOptimizeReload = SaveAndOptimizeReload()
    private val saveAndCloseMutex = Mutex()

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
        // A preceding normal save may already have closed the activity.
        if (activity?.isFinishing == true) return
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

    override suspend fun handlePostRequest(
        uri: PostRequestUri,
        bytes: ByteArray,
    ): ByteArray =
        when (uri.backendMethodName) {
            // A save can suspend while waiting for the collection; a later close must not overtake it.
            // Other requests, including progress polling, must remain able to run during a save.
            "updateDeckConfigs", "deckOptionsRequireClose" -> saveAndCloseMutex.withLock { super.handlePostRequest(uri, bytes) }
            else -> super.handlePostRequest(uri, bytes)
        }

    override fun onWebViewCreated() {
        // addJavascriptInterface needs to happen before loadUrl
        webViewLayout.addJavascriptInterface(ModalJavaScriptInterfaceListener(), "ankidroid")
        webViewLayout.addJavascriptInterface(saveAndOptimizeReload, "ankidroidSave")
        Timber.d("Added JS Interface: 'ankidroid")
    }

    @NeedsTest("going back on a manual page takes priority over closing a modal")
    override fun onCreateWebViewClient(savedInstanceState: Bundle?): PageWebViewClient {
        activity?.onBackPressedDispatcher?.addCallback(this, onBackFromDeckOptions)
        activity?.onBackPressedDispatcher?.addCallback(this, onBackFromModal)
        // going back on a manual page takes priority over closing a modal
        activity?.onBackPressedDispatcher?.addCallback(this, onBackFromManual)

        return object : PageWebViewClient() {
            /** @see onWebViewReady */
            override fun onShowWebView(webView: WebView) {
                // no-op: handled in onVebViewReady
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?,
            ): Boolean {
                // #16715: ensure that the fragment can't be used for general web browsing
                val url = request?.url ?: return true
                if (
                    request.isForMainFrame &&
                    url.toString().substringBefore('#') == view?.url?.substringBefore('#') &&
                    saveAndOptimizeReload.deferReloadIfSaving()
                ) {
                    return true
                }
                return if (request.isForMainFrame && url.scheme == "https" && url.encodedAuthority == "docs.ankiweb.net") {
                    false
                } else {
                    super.shouldOverrideUrlLoading(view, request)
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
                injectEasyDaysScrollFix()
            }
        }
    }

    /** HACK(#20929): vertical scrolling no longer changes values, but drag-to-change is disabled */
    private fun injectEasyDaysScrollFix() {
        // TODO: Do this properly if not fixed upstream by 2027
        val js =
            """
            (function() {
                if (document._easyDaysFix) return;
                document._easyDaysFix = true;

                // disable all touch events
                const style = document.createElement('style');
                style.textContent = '.easy-days-settings input[type="range"]{pointer-events:none}';
                document.head.appendChild(style);

                // re-implement 'click', so the value can still be set
                document.addEventListener('click', (e) => {
                    const container = e.target.closest('.easy-days-settings .input-container');
                    if (!container) return;
                    const slider = container.querySelector('input[type="range"]');

                    const rect = slider.getBoundingClientRect();
                    const fraction = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width));

                    const min = parseFloat(slider.min);
                    const max = parseFloat(slider.max);
                    const step = parseFloat(slider.step) || 1;
                    const newValue = Math.round((min + (max - min) * fraction) / step) * step;

                    if (parseFloat(slider.value) === newValue) return;
                    slider.value = newValue;
                    slider.dispatchEvent(new Event('input', { bubbles: true }));
                });
            })();
            """.trimIndent()
        webViewLayout.evaluateJavascript(js) {}
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
        saveAndOptimizeReload.onPageReady()
        setParameterUnlockClickTimeout()
    }

    /** Request a reload after Save & Optimize's response has reached the page. Called on main. */
    fun reloadAfterSave() {
        saveAndOptimizeReload.requestReload()
    }

    /**
     * AnkiDroid workaround for the frontend reloading before its save response has been read.
     *
     * 1. The fetch wrapper calls [started] before the frontend can request a reload.
     * 2. [requestReload] schedules navigation; [deferReloadIfSaving] holds premature frontend reloads.
     * 3. Reading the response body calls [finished]: success releases the reload; failure cancels it.
     *
     * Remove this when the bundled frontend awaits state.save() before reloading.
     */
    inner class SaveAndOptimizeReload {
        // JavaScript bridge calls run on a WebView thread; navigation callbacks run on main.
        private val pendingSaves = AtomicInteger()
        private var reloadRequested = false // Accessed only on the main thread.

        fun onPageReady() {
            pendingSaves.set(0)
            reloadRequested = false
            trackSaveResponse()
        }

        @JavascriptInterface
        fun started() {
            // Do this synchronously: posting to main could let the reload arrive first.
            pendingSaves.incrementAndGet()
        }

        /** Called on main. Returning true keeps the current document and its request alive. */
        fun deferReloadIfSaving(): Boolean {
            if (pendingSaves.get() == 0) return false
            requestReload()
            return true
        }

        fun requestReload() {
            reloadRequested = true
        }

        @JavascriptInterface
        fun finished(success: Boolean) {
            pendingSaves.decrementAndGet()
            launchCatchingTask {
                if (!success) {
                    // Keep the edited form on screen so the user can retry.
                    reloadRequested = false
                    return@launchCatchingTask
                }
                if (pendingSaves.get() != 0 || !reloadRequested || view == null) return@launchCatchingTask

                reloadRequested = false
                webViewLayout.reload()
            }
        }

        /**
         * Tracks the HTTP body read, not completion of frontend decoding or state.save().
         *
         * The native handler finishes saving before returning its response. Waiting for blob()
         * also avoids interrupting the frontend's network body read when we reload. Subsequent
         * decoding is not awaited: the new page reads the saved settings from the backend.
         */
        private fun trackSaveResponse() {
            webViewLayout.evaluateJavascript(
                """
                (() => {
                    const originalFetch = window.fetch;

                    async function readSaveBody(readBlob) {
                        try {
                            const body = await readBlob();
                            ankidroidSave.finished(true);
                            return body;
                        } catch (error) {
                            ankidroidSave.finished(false);
                            throw error; // Leave error reporting to the frontend.
                        }
                    }

                    window.fetch = async (input, init) => {
                        if (input !== "/_anki/updateDeckConfigs") return originalFetch(input, init);
                        ankidroidSave.started();

                        let response;
                        try {
                            response = await originalFetch(input, init);
                        } catch (error) {
                            ankidroidSave.finished(false);
                            throw error;
                        }
                        if (!response.ok) {
                            ankidroidSave.finished(false);
                            return response;
                        }

                        // fetch() only waits for headers. Anki's postProto reads the body with
                        // blob(); keep navigation blocked until that read actually completes.
                        const readBlob = response.blob.bind(response);
                        response.blob = () => readSaveBody(readBlob);
                        return response;
                    };
                })();
                """.trimIndent(),
            )
        }
    }

    /**
     * The FSRS parameters are locked until they are tapped three times in quick succession.
     *
     * Use [Prefs.doubleTapInterval] for this.
     *
     * See: https://github.com/ankitects/anki/blob/d036c2ade428b65d47c677bf3887a7402312c5c5/ts/routes/deck-options/ParamsInput.svelte
     */
    private fun setParameterUnlockClickTimeout() {
        val minimumTimeoutMs = Prefs.doubleTapInterval
        // The setter and default are only defined while `ParamsInput` is mounted, which requires
        // FSRS to be enabled, and are reassigned each time it mounts.
        // Intercept the assignments so this functionality works the first time someone enables
        // FSRS.
        webViewLayout.evaluateJavascript(
            """
            (() => {
                globalThis.anki ||= {};
                let setter = globalThis.anki.setParameterUnlockClickTimeoutMs;
                const apply = () => {
                    const defaultMs = globalThis.anki.defaultParameterUnlockClickTimeoutMs;
                    if (setter === undefined || defaultMs === undefined) return;
                    setter(Math.max(defaultMs, $minimumTimeoutMs));
                };
                Object.defineProperty(globalThis.anki, "setParameterUnlockClickTimeoutMs", {
                    configurable: true,
                    enumerable: true,
                    get: () => setter,
                    // the default is assigned after the setter: defer until both are defined
                    set: (value) => { setter = value; queueMicrotask(apply); },
                });
                apply();
            })();
            """.trimIndent(),
        )
    }

    companion object {
        private const val ARG_DECK_ID = "deckId"

        fun getIntent(
            context: Context,
            deckId: DeckId,
        ): Intent =
            SingleFragmentActivity.getIntent(
                context,
                fragmentClass = DeckOptions::class,
                arguments = Bundle().apply { putLong(ARG_DECK_ID, deckId) },
            )
    }
}

suspend fun FragmentActivity.updateDeckConfigsRaw(input: ByteArray): ByteArray {
    val output =
        withContext(Dispatchers.Main) {
            withProgress(
                extractProgress = {
                    // TODO: Don't use the amount yet, unused as a progress indicator, and
                    //  duplicates computeMemory's label
                    text = this.toProgressText() ?: getString(CommonString.dialog_processing)
                },
            ) {
                withContext(Dispatchers.IO) {
                    withCol { updateDeckConfigsRaw(input) }
                }
            }
        }
    undoableOp { OpChanges.parseFrom(output) }
    withContext(Dispatchers.Main) {
        if (UpdateDeckConfigsRequest.parseFrom(input).mode == UpdateDeckConfigsMode.UPDATE_DECK_CONFIGS_MODE_COMPUTE_ALL_PARAMS) {
            // Anki 26.05 relies on us to request a reload; 26.09 also requests one from JavaScript.
            // Both requests share a flag, so the page reloads once, after reading this response.
            requireDeckOptionsFragment().reloadAfterSave()
        } else {
            finish()
        }
    }
    return output
}

/**
 * Returns a string indicating progress, such as:
 *
 * ```
 * Optimizing preset 1/20
 * 5.2% of 1000 reviews
 * ```
 *
 * @return the above string, or `null` if a string could not be generated
 */
private fun ProgressContext.toProgressText(): String? =
    when {
        progress.hasComputeParams() -> progress.computeParams.toProgressText()
        progress.hasComputeMemory() -> progress.computeMemory.label // Updating cards: X/Y...
        else -> null
    }

/**
 * ```
 * Optimizing preset 1/20
 * 5.2% of 1000 reviews
 * ```
 */
private fun ComputeParamsProgress.toProgressText(): String {
    val label =
        TR.deckConfigOptimizingPreset(
            currentCount = currentPreset,
            totalCount = totalPresets,
        )
    val pct = if (total > 0) (current.toDouble() / total.toDouble() * 100.0) else 0.0
    val reviewsLabel =
        TR.deckConfigPercentOfReviews(
            pct = "%.1f".format(pct),
            reviews = reviews,
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

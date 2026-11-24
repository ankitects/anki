// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import android.os.Bundle
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebViewClient
import androidx.annotation.CallSuper
import androidx.annotation.LayoutRes
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.ichi2.anki.R
import com.ichi2.anki.workarounds.OnWebViewRecreatedListener
import com.ichi2.anki.workarounds.SafeWebViewLayout
import com.ichi2.themes.Themes
import com.ichi2.utils.showDialogIfWebViewOutdated
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import timber.log.Timber

/**
 * Base class for displaying Anki HTML pages
 */
abstract class PageFragment(
    @LayoutRes contentLayoutId: Int = R.layout.fragment_page,
) : Fragment(contentLayoutId),
    PostRequestHandler,
    OnWebViewRecreatedListener {
    lateinit var webViewLayout: SafeWebViewLayout
    protected abstract val pagePath: String

    /** Whether this page is shown. Lazy, as a restored fragment only gets its hidden state after it's constructed */
    private val isPageShownFlow by lazy { MutableStateFlow(!isHidden) }

    /**
     * A loading indicator for the page. May be shown before the WebView is loaded to
     * stop flickering
     *
     * @exception IllegalStateException if accessed before [onViewCreated]
     */
    val pageLoadingIndicator: CircularProgressIndicator
        get() = requireView().findViewById(R.id.page_loading)

    /**
     * Override this to set a custom [WebViewClient] to the page.
     * This is called in [onViewCreated].
     *
     * @param savedInstanceState If non-null, this fragment is being re-constructed
     * from a previous saved state as given here.
     */
    protected open fun onCreateWebViewClient(savedInstanceState: Bundle?) = PageWebViewClient()

    protected open fun onWebViewCreated() { }

    /**
     * When the webview calls `BridgeCommand("foo")`, the PageFragment execute `bridgeCommands["foo"]`.
     * By default, only bridge command is allowed, subclasses must redefine it if they expect bridge commands.
     */
    open val bridgeCommands: Map<String, () -> Unit> = mapOf()

    /**
     * Ensures that [pageWebViewClient] can receive `bridgeCommand` requests and execute the command from [bridgeCommands].
     */
    private fun setupBridgeCommand(pageWebViewClient: PageWebViewClient) {
        if (bridgeCommands.isEmpty()) {
            return
        }
        webViewLayout.addJavascriptInterface(
            object : Any() {
                @JavascriptInterface
                fun bridgeCommandImpl(request: String) {
                    bridgeCommands.getOrDefault(request) {
                        Timber.d("Unknown request received %s", request)
                    }()
                }
            },
            "bridgeCommandInterface",
        )
        pageWebViewClient.onPageFinishedCallbacks.add { webView ->
            webView.evaluateJavascript(
                "bridgeCommand = function(request){ bridgeCommandInterface.bridgeCommandImpl(request); };",
            ) {}
        }
    }

    @CallSuper
    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        webViewLayout = view.findViewById(R.id.webview_layout)

        // the pages can't send requests without PostRequestBridge, which requires a modern WebView
        val isOutdated =
            with(requireContext()) {
                showDialogIfWebViewOutdated {
                    requireActivity().finish()
                }
            }
        if (isOutdated) {
            Timber.w("${this::class.simpleName} requires modern WebView version, aborting load")
            return
        }
        view.findViewById<MaterialToolbar>(R.id.toolbar)?.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        setupWebView(savedInstanceState)
    }

    private fun setupWebView(savedInstanceState: Bundle?) {
        val pageWebViewClient = onCreateWebViewClient(savedInstanceState)
        webViewLayout.apply {
            setAcceptThirdPartyCookies(true)
            with(settings) {
                javaScriptEnabled = true
                displayZoomControls = false
                builtInZoomControls = true
                setSupportZoom(true)
            }
            setWebViewClient(pageWebViewClient)
            setWebChromeClient(PageChromeClient())
            setupBridgeCommand(pageWebViewClient)
            onWebViewCreated()
        }
        PostRequestBridge(this, viewLifecycleOwner.lifecycleScope).attachTo(webViewLayout)
        val nightMode = if (Themes.isNightTheme) "#night" else ""
        val url = "${PostRequestBridge.BASE_URL}$pagePath$nightMode".toUri()
        Timber.i("Loading $url")
        webViewLayout.loadUrl(url.toString())
    }

    override suspend fun handlePostRequest(
        uri: PostRequestUri,
        bytes: ByteArray,
    ): ByteArray {
        val methodName = uri.backendMethodName ?: throw IllegalArgumentException("unhandled request: $uri")

        val pageScope = lifecycleScope
        val resolvedUiMethod =
            when (val uiResponse = activity.handleUiPostRequest(methodName, bytes) { awaitPageShown(pageScope) }) {
                is UiPostRequestResponse.Handled -> return uiResponse.data
                is UiPostRequestResponse.UnknownMethod -> false
                is UiPostRequestResponse.Ignored -> true
            }

        return handleCollectionPostRequest(methodName, bytes) ?: run {
            if (!resolvedUiMethod) {
                Timber.w("Unknown TS method called.")
                Timber.d("No handlers resolve TS method %s", methodName)
            }
            throw IllegalArgumentException("unhandled method: $methodName")
        }
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        isPageShownFlow.value = !hidden
    }

    /**
     * Suspends until this page is shown. Bottom navigation hides tabs without stopping them, so a
     * hidden page is still resumed and could open a screen over the tab the user switched to.
     *
     * @param pageScope the [lifecycleScope] from when the request arrived. The wait runs in it, so it
     * fails if the page is removed. Removal gives the page a new [lifecycleScope] and resets
     * [isHidden] without calling [onHiddenChanged].
     */
    private suspend fun awaitPageShown(pageScope: CoroutineScope) {
        pageScope.async { isPageShownFlow.first { isShown -> isShown } }.await()
    }

    @CallSuper
    override fun onDestroyView() {
        webViewLayout.destroy()
        super.onDestroyView()
    }

    override fun onWebViewRecreated() {
        setupWebView(null)
    }
}

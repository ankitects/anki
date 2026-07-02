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
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.CallSuper
import androidx.annotation.LayoutRes
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.ichi2.anki.R
import com.ichi2.anki.workarounds.OnWebViewRecreatedListener
import com.ichi2.anki.workarounds.SafeWebViewLayout
import com.ichi2.themes.Themes
import com.ichi2.utils.WebViewVersion
import com.ichi2.utils.showDialogIfWebViewOutdated
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
    private lateinit var server: AnkiServer
    protected abstract val pagePath: String

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

    protected open val minimumWebViewVersion: WebViewVersion? = null

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
        server = AnkiServer(this).also { it.start() }
        webViewLayout = view.findViewById(R.id.webview_layout)

        minimumWebViewVersion?.let { minVersion ->
            val isOutdated =
                with(requireContext()) {
                    showDialogIfWebViewOutdated(minVersion) {
                        requireActivity().finish()
                    }
                }
            if (isOutdated) {
                Timber.w("${this::class.simpleName} requires modern WebView version, aborting load")
                return
            }
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
        val nightMode = if (Themes.isNightTheme) "#night" else ""
        val url = "${server.baseUrl()}$pagePath$nightMode".toUri()
        Timber.i("Loading $url")
        webViewLayout.loadUrl(url.toString())
    }

    override suspend fun handlePostRequest(
        uri: PostRequestUri,
        bytes: ByteArray,
    ): ByteArray {
        val methodName = uri.backendMethodName ?: throw IllegalArgumentException("unhandled request: $uri")

        val resolvedUiMethod =
            when (val uiResponse = activity.handleUiPostRequest(methodName, bytes)) {
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

    @CallSuper
    override fun onDestroyView() {
        server.stop()
        webViewLayout.safeDestroy()
        super.onDestroyView()
    }

    override fun onWebViewRecreated(webView: WebView) {
        setupWebView(null)
    }
}

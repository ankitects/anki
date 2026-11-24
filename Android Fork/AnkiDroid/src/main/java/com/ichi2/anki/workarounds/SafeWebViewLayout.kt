// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.workarounds

import android.annotation.SuppressLint
import android.content.Context
import android.print.PrintDocumentAdapter
import android.util.AttributeSet
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.ValueCallback
import android.webkit.WebBackForwardList
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.annotation.MainThread
import androidx.core.view.ancestors
import androidx.fragment.app.Fragment
import androidx.fragment.app.findFragment
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.ichi2.anki.BuildConfig
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.common.crashreporting.runCatchingWithReport
import timber.log.Timber

open class SafeWebViewLayout :
    FrameLayout,
    OnRenderProcessGoneListener {
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    private enum class WebViewState {
        ACTIVE,
        DESTROYED_RECOVERABLE,
        DESTROYED_TERMINAL,
    }

    /** May be destroyed: prefer [activeWebView] unless the destroyed instance is needed. */
    private var uncheckedWebView: WebView = createWebView()

    private var webViewState = WebViewState.ACTIVE

    /** The inner [WebView], or `null` while it is destroyed. Public methods should go through this. */
    private val activeWebView: WebView?
        get() {
            if (webViewState == WebViewState.ACTIVE) return uncheckedWebView
            Timber.w("WebView call ignored: WebView was destroyed")
            return null
        }

    var scrollBars: Int
        get() = uncheckedWebView.scrollBarStyle
        set(value) {
            activeWebView?.scrollBarStyle = value
        }

    @NeedsTest("Verify background color applies to inner WebView")
    override fun setBackgroundColor(color: Int) {
        super.setBackgroundColor(color)
        activeWebView?.setBackgroundColor(color)
    }

    protected open fun createWebView() = WebView(context)

    init {
        addView(uncheckedWebView, webViewLayoutParams)
    }

    // Not guarded when not [WebViewState.ACTIVE]: callers rarely use these after destroy and a no-op is impossible.
    @get:MainThread
    val settings: WebSettings get() = uncheckedWebView.settings

    @get:MainThread
    @Suppress("DEPRECATION")
    val scale get() = uncheckedWebView.scale

    @MainThread
    fun zoomBy(zoomFactor: Float) {
        activeWebView?.zoomBy(zoomFactor)
    }

    @MainThread
    fun setWebViewClient(webViewClient: SafeWebViewClient) {
        val webView = activeWebView ?: return
        webViewClient.setOnRenderProcessGoneListener(this)
        webView.webViewClient = webViewClient
    }

    @MainThread
    fun setWebChromeClient(webChromeClient: WebChromeClient) {
        activeWebView?.webChromeClient = webChromeClient
    }

    @MainThread
    fun evaluateJavascript(
        script: String,
        resultCallback: ((String) -> Unit)? = null,
    ) {
        // Without a callback, the WebView does not send the result back.
        activeWebView?.evaluateJavascript(script, resultCallback?.let { callback -> ValueCallback<String> { callback(it) } })
    }

    @MainThread
    fun addJavascriptInterface(
        javascriptInterface: Any,
        name: String,
    ) {
        activeWebView?.addJavascriptInterface(javascriptInterface, name)
    }

    /**
     * Requires [WebViewFeature.WEB_MESSAGE_LISTENER]
     *
     * @see WebViewCompat.addWebMessageListener
     */
    @SuppressLint("RequiresFeature") // checked by the caller
    @MainThread
    fun addWebMessageListener(
        jsObjectName: String,
        allowedOriginRules: Set<String>,
        listener: WebViewCompat.WebMessageListener,
    ) {
        val webView = activeWebView ?: return
        WebViewCompat.addWebMessageListener(webView, jsObjectName, allowedOriginRules, listener)
    }

    /**
     * Requires [WebViewFeature.DOCUMENT_START_SCRIPT]
     *
     * @see WebViewCompat.addDocumentStartJavaScript
     */
    @SuppressLint("RequiresFeature") // checked by the caller
    @MainThread
    fun addDocumentStartJavaScript(
        script: String,
        allowedOriginRules: Set<String>,
    ) {
        val webView = activeWebView ?: return
        WebViewCompat.addDocumentStartJavaScript(webView, script, allowedOriginRules)
    }

    @MainThread
    fun loadUrl(url: String) {
        activeWebView?.loadUrl(url)
    }

    @MainThread
    fun loadDataWithBaseURL(
        baseUrl: String?,
        data: String,
        mimeType: String?,
        encoding: String?,
        historyUrl: String?,
    ) {
        activeWebView?.loadDataWithBaseURL(baseUrl, data, mimeType, encoding, historyUrl)
    }

    @MainThread
    fun setAcceptThirdPartyCookies(accept: Boolean) {
        val webView = activeWebView ?: return
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, accept)
    }

    @MainThread
    fun goBack() {
        activeWebView?.goBack()
    }

    @MainThread
    fun canGoBack(): Boolean = activeWebView?.canGoBack() ?: false

    @MainThread
    fun clearHistory() {
        activeWebView?.clearHistory()
    }

    @MainThread
    fun copyBackForwardList(): WebBackForwardList = uncheckedWebView.copyBackForwardList()

    @MainThread
    fun setDownloadListener(listener: DownloadListener?) {
        activeWebView?.setDownloadListener(listener)
    }

    @MainThread
    fun pageUp(): Boolean = activeWebView?.pageUp(false) ?: false

    @MainThread
    fun pageDown(): Boolean = activeWebView?.pageDown(false) ?: false

    @MainThread
    fun reload() {
        activeWebView?.reload()
    }

    @MainThread
    fun focusOnWebView() {
        activeWebView?.requestFocus()
    }

    @MainThread
    fun scrollVerticallyBy(y: Int) {
        val webView = activeWebView ?: return
        if (webView.canScrollVertically(y)) {
            webView.scrollBy(0, y)
        }
    }

    @MainThread
    fun createPrintDocumentAdapter(documentName: String): PrintDocumentAdapter? = activeWebView?.createPrintDocumentAdapter(documentName)

    override fun setOnScrollChangeListener(l: OnScrollChangeListener?) {
        activeWebView?.setOnScrollChangeListener(l)
    }

    /**
     * Replaces the terminated inner [WebView] after a render process crash when recreation is possible.
     *
     * When recreation is skipped (layout not in a usable fragment/window state), the terminated
     * [WebView] is destroyed and not replaced; further calls on this layout are guarded until
     * [onAttachedToWindow] recreates the inner [WebView] from [WebViewState.DESTROYED_RECOVERABLE].
     */
    override fun onRenderProcessGone(webView: WebView) {
        if (webView !== uncheckedWebView) {
            destroyWebView(webView)
            return
        }

        // Always remove and destroy the terminated WebView first. Android requires this even when
        // we skip recreation (e.g. fragment view already gone). See:
        // https://developer.android.com/develop/ui/views/layout/webapps/handle-termination
        removeView(webView)
        webView.destroy()

        if (!isAttachedToWindow) {
            Timber.w("skipping WebView recreation; layout is not attached to a window")
            webViewState = WebViewState.DESTROYED_RECOVERABLE
            return
        }

        val listener = findListener()
        if (listener == null) {
            Timber.w("skipping WebView recreation; no OnWebViewRecreatedListener found")
            webViewState = WebViewState.DESTROYED_RECOVERABLE
            return
        }

        recreateInnerWebView(listener)
    }

    /** Finds the [OnWebViewRecreatedListener] from the host Fragment, falling back to the host Activity. */
    private fun findListener(): OnWebViewRecreatedListener? {
        val fragment = findFragmentOrNull()
        if (fragment != null && fragment.view == null) {
            Timber.w("skipping WebView recreation; fragment view is gone")
            return null
        }
        val host: Any = fragment ?: context
        if (host is OnWebViewRecreatedListener) return host
        val message = "'${host.javaClass.simpleName}' must implement OnWebViewRecreatedListener"
        if (BuildConfig.DEBUG && !isInEditMode) throw IllegalStateException(message)
        Timber.w("%s; WebView recreation may not be handled", message)
        return null
    }

    private fun findFragmentOrNull(): Fragment? =
        try {
            findFragment<Fragment>()
        } catch (e: IllegalStateException) {
            null
        }

    private fun recreateInnerWebView(listener: OnWebViewRecreatedListener) {
        uncheckedWebView = createWebView()
        webViewState = WebViewState.ACTIVE
        addView(uncheckedWebView, webViewLayoutParams)
        listener.onWebViewRecreated()
    }

    private fun tryRecoverDestroyedWebViewIfNeeded(listener: OnWebViewRecreatedListener) {
        if (webViewState != WebViewState.DESTROYED_RECOVERABLE) return
        // For Fragment hosts, ensure the layout is still in the fragment's current view hierarchy.
        val fragment = findFragmentOrNull()
        if (fragment != null) {
            val fragmentView = fragment.view ?: return
            if (this !== fragmentView && ancestors.none { it === fragmentView }) {
                Timber.w("skipping WebView recovery; layout is not in the fragment's current view hierarchy")
                return
            }
        }
        recreateInnerWebView(listener)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val listener = findListener() ?: return
        tryRecoverDestroyedWebViewIfNeeded(listener)
    }

    /**
     * Destroys the inner [WebView] with [destroyWebView]: unlike [WebView.destroy], this also
     * stops loading, clears callbacks and removes it from the layout.
     *
     * Afterwards, calls on this layout are ignored and the [WebView] is never recreated.
     */
    @MainThread
    fun destroy() {
        when (webViewState) {
            WebViewState.ACTIVE -> {
                destroyWebView(uncheckedWebView, this)
                // Mark destroyed even if [destroyWebView] partially failed; using a partially torn-down
                // WebView is unsafe.
            }
            // Crash cleanup already destroyed the native WebView; promote to terminal so reattach
            // does not recover.
            WebViewState.DESTROYED_RECOVERABLE -> Unit
            WebViewState.DESTROYED_TERMINAL -> {
                Timber.w("destroy called after WebView was destroyed")
                return
            }
        }
        webViewState = WebViewState.DESTROYED_TERMINAL
    }

    companion object {
        private val webViewLayoutParams =
            ViewGroup.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT,
            )

        /**
         * Clean up and destroy the [webView] to prevent memory leaks.
         *
         * Stops any loading, clears callbacks, and removes the view from its [parent].
         */
        fun destroyWebView(
            webView: WebView?,
            parent: ViewGroup? = webView?.parent as? ViewGroup,
        ) {
            Timber.d("Destroying WebView")

            runCatchingWithReport("destroyWebView", onlyIfSilent = true) {
                webView?.apply {
                    stopLoading()
                    loadUrl("about:blank")
                    webChromeClient = null
                    // remove listeners this class exposes
                    setOnScrollChangeListener(null)
                }

                // remove WebView from parent view
                parent?.removeView(webView) ?: Timber.w("WebView parent is null")
            }

            // attempt to run destroy() even if the above fails
            runCatchingWithReport("destroyWebView", onlyIfSilent = true) {
                webView?.destroy()
            }
        }
    }
}

/**
 * Listener for [SafeWebViewLayout.onRenderProcessGone], called after the internal [WebView] is
 * replaced due to a render process crash or the system killing it to free memory.
 *
 * Any [Fragment] containing a [SafeWebViewLayout] **must** implement this interface. In debug builds,
 * a missing implementation will throw at [SafeWebViewLayout.onAttachedToWindow()]
 *
 * @see SafeWebViewLayout.onRenderProcessGone
 */
fun interface OnWebViewRecreatedListener {
    /**
     * Reconfigures the [SafeWebViewLayout] after [SafeWebViewLayout.onRenderProcessGone] has
     * replaced its [WebView]. Implementations must reapply, through the layout, all clients,
     * settings and content that were configured on the original [WebView], as
     * [SafeWebViewLayout] only handles the structural replacement and cannot reapply app level
     * configuration.
     *
     * To manually trigger this path, call `webViewLayout.loadUrl("chrome://crash")`.
     * Automated testing requires an instrumented test; a unit test is not sufficient.
     */
    fun onWebViewRecreated()
}

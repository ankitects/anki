// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import android.graphics.Bitmap
import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.core.net.toUri
import androidx.core.view.isVisible
import com.google.android.material.color.MaterialColors
import com.ichi2.anki.OnPageFinishedCallback
import com.ichi2.anki.utils.openUrl
import com.ichi2.anki.workarounds.SafeWebViewClient
import com.ichi2.anki.workarounds.SafeWebViewLayout
import com.ichi2.utils.AssetHelper.guessMimeType
import com.ichi2.utils.toRGBHex
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

/**
 * Base WebViewClient to be used on [PageFragment]
 */
open class PageWebViewClient : SafeWebViewClient() {
    val onPageFinishedCallbacks: MutableList<OnPageFinishedCallback> = mutableListOf()

    /** Whether [url] is one of our pages, served from [PostRequestBridge.BASE_URL] */
    private fun isInternalUrl(url: Uri): Boolean = url.scheme == BASE_URI.scheme && url.encodedAuthority == BASE_URI.encodedAuthority

    /**
     * Keeps bundled routes in this WebView and opens external HTTP(S) main-frame links outside it.
     *
     * WebView skips this callback for POST navigations and app-initiated [WebView.loadUrl] calls.
     */
    override fun shouldOverrideUrlLoading(
        view: WebView?,
        request: WebResourceRequest?,
    ): Boolean {
        val url = request?.url ?: return true
        if (isInternalUrl(url)) {
            return SvelteKitPage.fromPath(url.path.orEmpty()) == null
        }
        if (request.isForMainFrame && url.scheme in listOf("http", "https")) {
            view?.context?.openUrl(url)
        }
        return true
    }

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest,
    ): WebResourceResponse? {
        val path = request.url.path
        if (request.method != "GET" || path == null || !isInternalUrl(request.url)) return null
        if (path == "/favicon.png") {
            return WebResourceResponse("image/x-icon", null, ByteArrayInputStream(byteArrayOf()))
        }

        val page = SvelteKitPage.fromPath(path)
        val assetPath =
            if (path.startsWith("/_app/")) {
                "backend/sveltekit/app/${path.substring(6)}"
            } else if (page != null) {
                "backend/sveltekit/index.html"
            } else {
                return null
            }

        try {
            val mimeType = guessMimeType(assetPath)
            val inputStream =
                view.context.assets.open(assetPath).let { asset ->
                    if (page?.disableCsp == true) {
                        removeBundledCsp(asset)
                    } else {
                        asset
                    }
                }
            val response = WebResourceResponse(mimeType, null, inputStream)
            if ("immutable" in path) {
                response.responseHeaders = mapOf("Cache-Control" to "max-age=31536000")
            }
            return response
        } catch (_: IOException) {
            Timber.w("Not found %s", assetPath)
        }
        return null
    }

    override fun onPageStarted(
        view: WebView?,
        url: String?,
        favicon: Bitmap?,
    ) {
        super.onPageStarted(view, url, favicon)
        view?.let { webView ->
            val bgColor = MaterialColors.getColor(webView, android.R.attr.colorBackground).toRGBHex()
            webView.evaluateAfterDOMContentLoaded(
                """document.body.style.setProperty("background-color", "$bgColor", "important");
                    console.log("Background color set");""",
            )
        }
    }

    /**
     * Shows the WebView after the page is loaded
     *
     * This may be overridden if additional 'screen ready' logic is provided by the backend
     * @see DeckOptions
     */
    open fun onShowWebView(webView: WebView) {
        Timber.v("Displaying WebView")
        webView.isVisible = true
        (webView.parent as? SafeWebViewLayout)?.isVisible = true
    }

    override fun onPageFinished(
        view: WebView?,
        url: String?,
    ) {
        super.onPageFinished(view, url)
        if (view == null) return
        onPageFinishedCallbacks.map { callback -> callback.onPageFinished(view) }
        /* webView is invisible by default to avoid flashes while
         * the page is loaded, and can be made visible again after it finishes loading */
        onShowWebView(view)
    }
}

private val BASE_URI = PostRequestBridge.BASE_URL.toUri()

private val SVELTEKIT_CSP_META = Regex("""<meta http-equiv="content-security-policy" content="[^"]*">""")

/** Reads and closes [asset], returning the HTML without its bundled CSP meta tag. */
private fun removeBundledCsp(asset: InputStream): InputStream {
    val html = asset.bufferedReader().use { reader -> reader.readText() }
    val htmlWithoutCsp = html.replace(SVELTEKIT_CSP_META, "")
    return htmlWithoutCsp.byteInputStream()
}

fun WebView.evaluateAfterDOMContentLoaded(
    script: String,
    resultCallback: ValueCallback<String>? = null,
) {
    evaluateJavascript(
        """
        var codeToRun = function() { 
            $script
        }
        
        if (document.readyState === "loading") {
          document.addEventListener("DOMContentLoaded", codeToRun);
        } else {
          codeToRun();
        }
        """.trimIndent(),
        resultCallback,
    )
}

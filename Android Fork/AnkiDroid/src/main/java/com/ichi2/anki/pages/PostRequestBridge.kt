// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.webkit.WebView
import androidx.annotation.MainThread
import androidx.annotation.VisibleForTesting
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.ichi2.anki.workarounds.SafeWebViewLayout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.HttpURLConnection.HTTP_ENTITY_TOO_LARGE
import java.net.HttpURLConnection.HTTP_INTERNAL_ERROR
import java.net.HttpURLConnection.HTTP_OK
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer

/**
 * Sends the POST requests of a [WebView] to a [PostRequestHandler].
 *
 * A [WebView] can intercept requests, but can't read their bodies. Instead,
 * `post-request-bridge.js` replaces `fetch()` before the page scripts run, and sends same-origin
 * POST requests through an object injected by [WebViewCompat.addWebMessageListener].
 * Only frames from [ORIGIN] get the object.
 *
 * Requests made with `XMLHttpRequest`, forms or from web workers aren't sent, and fail: the script
 * only replaces `fetch()`, and only in frames. The Anki pages and AnkiDroid's scripts only use
 * `fetch()`.
 *
 * Messages are binary, with big-endian numbers:
 * * request: request ID (4 bytes), URI length (2 bytes), UTF-8 URI, body
 * * reply: request ID (4 bytes), HTTP status (2 bytes), body
 *
 * Pages are loaded from [BASE_URL], which nothing else can serve: the WebViewClient serves the
 * page assets, and a request that escapes both fails, as the host doesn't resolve and nobody
 * else can get a certificate for it.
 *
 * @param scope main thread scope of the page. Requests outlive its cancellation, replies don't
 */
class PostRequestBridge(
    private val handler: PostRequestHandler,
    private val scope: CoroutineScope,
) : WebViewCompat.WebMessageListener {
    /** Must be called before loading the page. POST requests fail if the WebView isn't [supported][isSupported] */
    @MainThread
    fun attachTo(webViewLayout: SafeWebViewLayout) {
        if (!isSupported()) return
        webViewLayout.addWebMessageListener(JS_OBJECT_NAME, setOf(ORIGIN), this)
        webViewLayout.addDocumentStartJavaScript(readScript(webViewLayout.context), setOf(ORIGIN))
    }

    /** Must be called before loading the page. POST requests fail if the WebView isn't [supported][isSupported] */
    @SuppressLint("RequiresFeature") // checked by isSupported()
    @MainThread
    fun attachTo(webView: WebView) {
        if (!isSupported()) return
        WebViewCompat.addWebMessageListener(webView, JS_OBJECT_NAME, setOf(ORIGIN), this)
        WebViewCompat.addDocumentStartJavaScript(webView, readScript(webView.context), setOf(ORIGIN))
    }

    @SuppressLint("RequiresFeature") // the listener is only added if the WebView is supported
    override fun onPostMessage(
        view: WebView,
        message: WebMessageCompat,
        sourceOrigin: Uri,
        isMainFrame: Boolean,
        replyProxy: JavaScriptReplyProxy,
    ) {
        if (message.type != WebMessageCompat.TYPE_ARRAY_BUFFER) return
        val request = message.arrayBuffer
        scope.launch {
            // finish requests even if the page is closed, as they may change the collection
            val reply = withContext(NonCancellable) { handleMessage(request) } ?: return@launch
            replyProxy.postMessage(reply)
        }
    }

    /**
     * @param message a request, as described in [PostRequestBridge]
     * @return the reply to [message], or `null` if [message] isn't a valid request
     */
    @VisibleForTesting
    suspend fun handleMessage(message: ByteArray): ByteArray? {
        if (message.size > MAX_MESSAGE_SIZE) {
            Timber.w("POST request too large: %d bytes", message.size)
            return reply(ByteBuffer.wrap(message).int, HTTP_ENTITY_TOO_LARGE, "Request too large".encodeToByteArray())
        }
        val request = Request.parse(message) ?: return null
        Timber.d("POST: Requested %s", request.uri)
        val (status, body) =
            try {
                HTTP_OK to handler.handlePostRequest(request.uri, request.body)
            } catch (e: Exception) {
                // a handler may be canceled while the request isn't, e.g. by a timeout
                if (e is CancellationException) currentCoroutineContext().ensureActive()
                Timber.w(e, "POST request failed: %s", request.uri)
                HTTP_INTERNAL_ERROR to (e.localizedMessage ?: e.javaClass.name).encodeToByteArray()
            }
        return reply(request.id, status, body)
    }

    private fun reply(
        requestId: Int,
        status: Int,
        body: ByteArray,
    ): ByteArray =
        ByteBuffer
            .allocate(HEADER_SIZE + body.size)
            .putInt(requestId)
            .putShort(status.toShort())
            .put(body)
            .array()

    /**
     * A POST request sent by `post-request-bridge.js`
     *
     * @param id identifies the `fetch()` call to reply to, as requests may finish in any order
     * @param uri path of the request URL, e.g. `/_anki/getGraphPreferences`
     * @param body body of the request, which may be empty
     */
    private class Request(
        val id: Int,
        val uri: PostRequestUri,
        val body: ByteArray,
    ) {
        companion object {
            /** @return the request in [message], or `null` if [message] is truncated */
            fun parse(message: ByteArray): Request? =
                try {
                    val buffer = ByteBuffer.wrap(message)
                    val id = buffer.int
                    val uri = ByteArray(buffer.short.toUShort().toInt()).also { buffer.get(it) }
                    val body = ByteArray(buffer.remaining()).also { buffer.get(it) }
                    Request(id, PostRequestUri(uri.decodeToString()), body)
                } catch (e: BufferUnderflowException) {
                    Timber.w(e, "Invalid POST request message")
                    null
                }
        }
    }

    companion object {
        /**
         * Host of the pages: the one [androidx.webkit.WebViewAssetLoader] uses for app content.
         * Google reserves it, so it can't be impersonated.
         */
        const val HOST = "appassets.androidplatform.net"

        /** Origin of the pages */
        private const val ORIGIN = "https://$HOST"
        const val BASE_URL = "$ORIGIN/"

        /** Keep in sync with `post-request-bridge.js` */
        private const val JS_OBJECT_NAME = "ankidroidPostRequestBridge"
        private const val SCRIPT_PATH = "scripts/post-request-bridge.js"

        /** Size of the request ID, plus the URI length or the HTTP status */
        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        const val HEADER_SIZE = 6

        /** Larger messages are rejected, as copying their body could run the app out of memory */
        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        const val MAX_MESSAGE_SIZE = 64 shl 20

        /** Whether the WebView supports the APIs used by the bridge. Logs a warning if it doesn't */
        fun isSupported(): Boolean =
            (
                WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) &&
                    WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) &&
                    WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_ARRAY_BUFFER)
            ).also { isSupported ->
                if (!isSupported) Timber.w("WebView doesn't support PostRequestBridge: POST requests will fail")
            }

        /** Reads `post-request-bridge.js`, which replaces `fetch()` in the pages */
        private fun readScript(context: Context): String =
            context.assets
                .open(SCRIPT_PATH)
                .bufferedReader()
                .use { it.readText() }
    }
}

// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ichi2.anki.testutil.ensureWebViewIsSupported
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

/** Runs `post-request-bridge.js` in a WebView */
@RunWith(AndroidJUnit4::class)
class PostRequestBridgeWebViewTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** URIs received by the handler */
    private val requests = LinkedBlockingQueue<String>()

    /** The handler replies once it's completed */
    @Volatile
    private var canReply = CompletableDeferred(Unit)
    private lateinit var webView: WebView
    private var isWebViewDestroyed = false

    @Before
    fun setUp() {
        ensureWebViewIsSupported()
        val handler =
            object : PostRequestHandler {
                override suspend fun handlePostRequest(
                    uri: PostRequestUri,
                    bytes: ByteArray,
                ): ByteArray {
                    requests.add(uri.uri)
                    canReply.await()
                    return byteArrayOf()
                }
            }
        instrumentation.runOnMainSync {
            webView = WebView(instrumentation.targetContext).apply { settings.javaScriptEnabled = true }
            PostRequestBridge(handler, scope).attachTo(webView)
        }
    }

    @After
    fun tearDown() {
        if (!isWebViewDestroyed) destroyWebView()
        scope.cancel()
    }

    @Test
    fun requestsOfThePageAreSent() {
        load("<script>fetch('/_anki/page', { method: 'POST' })</script>")

        assertEquals("/_anki/page", requests.poll(10, TimeUnit.SECONDS))
    }

    @Test
    fun requestsOfSrcdocIframesAreSent() {
        // their `location.origin` is "null"
        load("""<iframe srcdoc="<script>fetch('/_anki/srcdoc', { method: 'POST' })</script>"></iframe>""")

        assertEquals("/_anki/srcdoc", requests.poll(10, TimeUnit.SECONDS))
    }

    @Test
    fun aRequestWithATooLongUrlIsRejected() {
        // its length doesn't fit in the 2 bytes of the message
        load(
            """
            <script>
            fetch('/' + 'a'.repeat(0x10000), { method: 'POST' })
                .then(() => 'resolved', error => error.name)
                .then(result => fetch('/_anki/' + result, { method: 'POST' }));
            </script>
            """,
        )

        assertEquals("/_anki/TypeError", requests.poll(10, TimeUnit.SECONDS))
    }

    @Test
    fun aReplyAfterTheWebViewIsDestroyedIsDropped() {
        canReply = CompletableDeferred()
        load("<script>fetch('/_anki/slow', { method: 'POST' })</script>")
        assertEquals("/_anki/slow", requests.poll(10, TimeUnit.SECONDS))

        destroyWebView()
        canReply.complete(Unit)

        // the reply is posted on the main thread, where an exception would crash the app
        instrumentation.waitForIdleSync()
    }

    private fun destroyWebView() {
        instrumentation.runOnMainSync { webView.destroy() }
        isWebViewDestroyed = true
    }

    private fun load(html: String) {
        instrumentation.runOnMainSync {
            webView.loadDataWithBaseURL(PostRequestBridge.BASE_URL, html, "text/html", null, null)
        }
    }
}

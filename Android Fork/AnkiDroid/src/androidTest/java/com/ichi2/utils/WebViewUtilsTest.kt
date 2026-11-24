// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.utils

import android.webkit.WebView
import androidx.test.platform.app.InstrumentationRegistry
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.anki.testutil.waitUntil
import com.ichi2.anki.workarounds.SafeWebViewClient
import org.junit.Test
import kotlin.test.assertNotNull

class WebViewUtilsTest : InstrumentedTest() {
    /**
     * WebView aborts the process if a renderer crash is not handled by every WebView.
     *
     * Looking up the user agent must not leave a WebView behind which cannot handle one
     */
    @Test
    fun rendererCrashAfterUserAgentLookupIsHandled() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var webView: WebView
        var crashHandled = false
        instrumentation.runOnMainSync {
            assertNotNull(getWebviewUserAgent(testContext))
            webView =
                WebView(testContext).apply {
                    webViewClient = SafeWebViewClient().apply { setOnRenderProcessGoneListener { crashHandled = true } }
                    loadUrl("chrome://crash")
                }
        }
        // the process is aborted here if a WebView could not handle the crash
        waitUntil(message = { "renderer crash was not handled" }) { crashHandled }
        instrumentation.runOnMainSync { webView.destroy() }
    }
}

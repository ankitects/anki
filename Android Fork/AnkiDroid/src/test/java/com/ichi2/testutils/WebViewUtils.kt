// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.core.net.toUri
import androidx.core.view.children
import com.ichi2.anki.workarounds.SafeWebViewLayout
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

val SafeWebViewLayout.webView: WebView
    get() = children.filterIsInstance<WebView>().single()

fun mockWebResourceRequest(
    address: String,
    mainFrame: Boolean = true,
): WebResourceRequest =
    mock {
        on { url } doReturn address.toUri()
        on { method } doReturn "GET"
        on { isForMainFrame } doReturn mainFrame
    }

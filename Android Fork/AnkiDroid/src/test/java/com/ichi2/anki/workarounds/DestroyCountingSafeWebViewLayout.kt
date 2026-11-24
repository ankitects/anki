// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.workarounds

import android.content.Context
import android.util.AttributeSet
import android.webkit.WebView

/** Tracks [WebView.destroy] invocations on the inner instance for regression tests. */
internal class DestroyCountingSafeWebViewLayout
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : SafeWebViewLayout(context, attrs) {
        var webViewDestroyCallCount = 0
            private set

        override fun createWebView(): WebView =
            object : WebView(context) {
                override fun destroy() {
                    webViewDestroyCallCount++
                    super.destroy()
                }
            }
    }

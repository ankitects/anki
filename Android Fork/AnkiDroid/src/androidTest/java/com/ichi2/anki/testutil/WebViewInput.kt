// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.testutil

import android.webkit.WebView
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

fun WebView.evaluate(script: String): String {
    val result = CompletableFuture<String>()
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
        evaluateJavascript(script) { result.complete(it) }
    }
    return result.get(5, TimeUnit.SECONDS)
}

fun WebView.awaitJavascript(condition: String) {
    waitUntil(message = { "JavaScript condition did not become true: $condition" }) {
        evaluate(condition) == "true"
    }
}

fun WebView.focusInput(id: String) {
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
        requestFocus()
        // loadUrl also permits keyboard activation before the WebView's first touch.
        loadUrl("javascript:document.getElementById('$id').focus();")
    }
    awaitJavascript("document.activeElement?.id === '$id'")
}

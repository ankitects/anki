// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.cardviewer

import android.content.Context
import android.util.AttributeSet
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.core.content.getSystemService
import com.ichi2.anki.workarounds.SafeWebViewLayout
import com.ichi2.ui.applyNoSuggest

/** Applies `nosuggest` to the focused HTML answer field's keyboard connection. */
open class TypeAnswerWebView(
    context: Context,
) : WebView(context) {
    // All keyboard state is owned by the UI thread.
    private var noSuggest = false

    private var destroyed = false

    init {
        addJavascriptInterface(Keyboard(), "AnkiDroidKeyboard")
    }

    private inner class Keyboard {
        @JavascriptInterface
        fun setNoSuggest(enabled: Boolean) {
            // JavaScript interfaces run on WebView's bridge thread.
            post {
                if (destroyed || noSuggest == enabled) return@post
                noSuggest = enabled
                // Chromium may reuse an input connection between HTML text fields.
                if (isAttachedToWindow) {
                    context.getSystemService<InputMethodManager>()?.restartInput(this@TypeAnswerWebView)
                }
            }
        }
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? =
        super.onCreateInputConnection(outAttrs).applyNoSuggest(outAttrs, noSuggest)

    override fun destroy() {
        destroyed = true
        super.destroy()
    }
}

/** Uses the same keyboard support when the new reviewer recreates its WebView after a crash. */
class TypeAnswerWebViewLayout(
    context: Context,
    attrs: AttributeSet?,
) : SafeWebViewLayout(context, attrs) {
    override fun createWebView(): WebView = TypeAnswerWebView(context)
}

// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.workarounds

import android.os.Build
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.RequiresApi
import timber.log.Timber

fun interface OnRenderProcessGoneListener {
    fun onRenderProcessGone(webView: WebView)
}

open class SafeWebViewClient : WebViewClient() {
    private var onRenderProcessGoneListener: OnRenderProcessGoneListener? = null

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onRenderProcessGone(
        view: WebView,
        detail: RenderProcessGoneDetail,
    ): Boolean {
        Timber.w("onRenderProcessGone (didCrash %b)", detail.didCrash())
        onRenderProcessGoneListener?.onRenderProcessGone(view)
            ?: throw IllegalStateException("onRenderProcessGoneListener must not be null")
        return true
    }

    fun setOnRenderProcessGoneListener(onRenderProcessGoneListener: OnRenderProcessGoneListener) {
        this.onRenderProcessGoneListener = onRenderProcessGoneListener
    }
}

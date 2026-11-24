// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 Kshitij Patil <kshitijpatil98@gmail.com>

package com.ichi2.anki

import android.webkit.WebView

/**
 * Intended to be used with [android.webkit.WebViewClient.onPageFinished]
 */
fun interface OnPageFinishedCallback {
    /** @see android.webkit.WebViewClient.onPageFinished */
    fun onPageFinished(view: WebView)
}

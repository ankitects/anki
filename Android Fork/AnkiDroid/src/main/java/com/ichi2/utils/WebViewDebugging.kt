// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.utils

import android.os.Build
import android.webkit.WebView
import androidx.annotation.RequiresApi

object WebViewDebugging {
    private var sHasSetDataDirectory = false

    /** Throws IllegalStateException if a WebView has been initialized  */
    @RequiresApi(api = Build.VERSION_CODES.P)
    fun setDataDirectorySuffix(suffix: String) {
        WebView.setDataDirectorySuffix(suffix)
        sHasSetDataDirectory = true
    }

    fun hasSetDataDirectory(): Boolean {
        // Implicitly truth requires API >= P
        return sHasSetDataDirectory
    }
}

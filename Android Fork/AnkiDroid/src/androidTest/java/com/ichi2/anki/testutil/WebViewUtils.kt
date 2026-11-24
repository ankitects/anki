// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.testutil

import androidx.core.content.pm.PackageInfoCompat
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewCompat
import com.ichi2.utils.checkWebViewVersionComponents
import kotlin.test.fail

/**
 * Fails if the WebView is older than AnkiDroid supports: its scripts would not run.
 *
 * Launching a screen directly bypasses the DeckPicker's outdated WebView dialog.
 */
fun ensureWebViewIsSupported() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val webView = WebViewCompat.getCurrentWebViewPackage(context)!!
    val outdatedVersion =
        checkWebViewVersionComponents(
            packageName = webView.packageName,
            webviewVersion = webView.versionName!!,
            versionCode = PackageInfoCompat.getLongVersionCode(webView),
            userAgent = null,
        )
    if (outdatedVersion != null) {
        fail("Unsupported WebView $outdatedVersion: AnkiDroid's scripts cannot run. Use a newer emulator")
    }
}

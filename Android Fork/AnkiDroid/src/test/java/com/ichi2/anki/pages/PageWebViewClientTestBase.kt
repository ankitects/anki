// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import android.webkit.WebResourceRequest
import android.webkit.WebView
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.RobolectricTest.Companion.advanceRobolectricLooper
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.common.destinations.StatisticsDestination
import com.ichi2.testutils.mockWebResourceRequest
import com.ichi2.testutils.webView
import org.robolectric.Shadows.shadowOf

abstract class PageWebViewClientTestBase : RobolectricTest() {
    protected fun withStatistics(block: (WebView, PageWebViewClient) -> Unit) {
        val view = openStatistics().webViewLayout.webView
        block(view, shadowOf(view).webViewClient as PageWebViewClient)
    }

    protected fun request(
        address: String,
        mainFrame: Boolean = true,
    ): WebResourceRequest = mockWebResourceRequest(address, mainFrame)
}

private fun RobolectricTest.openStatistics(): Statistics {
    val activity = startRegularActivity<SingleFragmentActivity>(StatisticsDestination.toIntent(targetContext))
    advanceRobolectricLooper()
    return activity.fragment as Statistics
}

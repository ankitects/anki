// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Intent
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.workarounds.SafeWebViewLayout
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.sameInstance
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class InfoTest : RobolectricTest() {
    private fun launchInfo(): Info =
        startActivityNormallyOpenCollectionWithIntent(
            Info::class.java,
            Intent(targetContext, Info::class.java),
        )

    private fun Info.webViewLayout(): SafeWebViewLayout = findViewById(R.id.web_view)

    @Test
    fun `onRenderProcessGone recovers by recreating the inner WebView`() {
        val activity = launchInfo()
        val webViewLayout = activity.webViewLayout()
        val initialInnerWebView = webViewLayout.getChildAt(0) as WebView

        webViewLayout.onRenderProcessGone(initialInnerWebView)

        assertThat("crashed WebView is destroyed", shadowOf(initialInnerWebView).wasDestroyCalled(), equalTo(true))
        assertThat("a fresh WebView replaces it", webViewLayout.childCount, equalTo(1))
        val recoveredInnerWebView = webViewLayout.getChildAt(0) as WebView
        assertThat("recovered WebView is a new instance", recoveredInnerWebView, not(sameInstance(initialInnerWebView)))
        assertThat("recovered WebView is not destroyed", shadowOf(recoveredInnerWebView).wasDestroyCalled(), equalTo(false))
    }

    @Test
    fun `onRenderProcessGone does not finish the activity`() {
        val activity = launchInfo()
        val webViewLayout = activity.webViewLayout()
        val innerWebView = webViewLayout.getChildAt(0) as WebView

        webViewLayout.onRenderProcessGone(innerWebView)

        assertThat("activity stays alive after render-process crash", activity.isFinishing, equalTo(false))
    }

    @Test
    fun `destroy tears down the inner WebView`() {
        val controller =
            startActivityControllerNormallyOpenCollectionWithIntent(
                Info::class.java,
                Intent(targetContext, Info::class.java),
            )
        val innerWebView = controller.get().webViewLayout().getChildAt(0) as WebView
        controller.pause().stop().destroy()
        assertThat("WebView.destroy() called on teardown", shadowOf(innerWebView).wasDestroyCalled(), equalTo(true))
    }

    @Test
    fun `destroy after render-process crash does not call destroy twice`() {
        val activity = launchInfo()
        val webViewLayout = activity.webViewLayout()
        val crashedWebView = webViewLayout.getChildAt(0) as WebView

        webViewLayout.onRenderProcessGone(crashedWebView)
        assertThat(shadowOf(crashedWebView).wasDestroyCalled(), equalTo(true))

        val recoveredWebView = webViewLayout.getChildAt(0) as WebView
        webViewLayout.destroy()
        assertThat("recovered WebView is destroyed on teardown", shadowOf(recoveredWebView).wasDestroyCalled(), equalTo(true))
    }
}

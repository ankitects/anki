// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.security

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.webkit.WebView
import androidx.core.net.toUri
import androidx.test.core.app.ActivityScenario
import com.ichi2.anki.AbstractFlashcardViewerTest.NonAbstractFlashcardViewer
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.browser.IdsFile
import com.ichi2.anki.loadInitialCard
import com.ichi2.anki.previewer.CardViewerActivity
import com.ichi2.anki.previewer.PreviewerFragment
import com.ichi2.anki.ui.windows.reviewer.ReviewerFragment
import com.ichi2.anki.workarounds.SafeWebViewLayout
import com.ichi2.testutils.assertNoActivityStarted
import com.ichi2.testutils.assertOpenedUrl
import com.ichi2.testutils.createTransientDirectory
import com.ichi2.testutils.mockWebResourceRequest
import com.ichi2.testutils.registerWebBrowser
import com.ichi2.testutils.webView
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(ParameterizedRobolectricTestRunner::class)
class CardLinkNavigationTest(
    private val viewerType: ViewerType,
) : RobolectricTest() {
    override fun getCollectionStorageMode() = CollectionStorageMode.IN_MEMORY_WITH_MEDIA

    @Before
    fun disableExternalApps() {
        editPreferences { putBoolean(targetContext.getString(R.string.pref_allow_card_external_launch_key), false) }
    }

    @Test
    fun `web links resolve to browsers and browsable native apps without external app permission`() {
        targetContext.registerWebBrowser()
        val nativeHandler = registerNativeHandler(browsable = true)
        registerNativeHandler(browsable = false)
        withWebView { webView ->
            val requests = listOf("http" to true, "https" to true, "http" to false, "https" to false)
            for ((scheme, mainFrame) in requests) {
                val url = "$scheme://native.example.org/action?value=1#fragment"
                val request = mockWebResourceRequest(url, mainFrame = mainFrame)
                assertTrue(shadowOf(webView).webViewClient.shouldOverrideUrlLoading(webView, request))
                val intent = assertNotNull(shadowOf(targetContext.applicationContext as Application).nextStartedActivity)
                assertEquals(Intent.ACTION_VIEW, intent.action)
                assertEquals(url.toUri(), intent.data)
                assertNull(intent.selector)
                val handlers =
                    targetContext.packageManager.queryIntentActivities(
                        intent,
                        PackageManager.MATCH_DEFAULT_ONLY,
                    )
                assertEquals(
                    setOf(ComponentName("com.ichi2.testutils.browser", "BrowserActivity"), nativeHandler),
                    handlers.map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }.toSet(),
                )
                targetContext.assertNoActivityStarted()
            }
        }
    }

    @Test
    fun `web links can open a native app without a browser or external app permission`() {
        registerNativeHandler(browsable = true)
        withWebView { webView ->
            shadowOf(targetContext.applicationContext as Application).checkActivities(true)
            val url = "https://native.example.org/action?value=1#fragment"
            assertTrue(shadowOf(webView).webViewClient.shouldOverrideUrlLoading(webView, mockWebResourceRequest(url)))
            targetContext.assertOpenedUrl(url.toUri())
        }
    }

    @Test
    fun `missing browsable handler does not load an external page in the card`() {
        registerNativeHandler(browsable = false)
        withWebView { webView ->
            shadowOf(targetContext.applicationContext as Application).checkActivities(true)
            val request = mockWebResourceRequest("https://native.example.org/action")
            assertTrue(shadowOf(webView).webViewClient.shouldOverrideUrlLoading(webView, request))
            targetContext.assertNoActivityStarted()
        }
    }

    @Test
    fun `parsed intents with web data still require permission`() {
        targetContext.registerWebBrowser()
        withWebView { webView ->
            for (url in listOf(
                "intent://example.org/#Intent;scheme=https;end",
                "android-app://com.ichi2.testutils.browser/https/example.org",
            )) {
                val request = mockWebResourceRequest(url)
                assertTrue(shadowOf(webView).webViewClient.shouldOverrideUrlLoading(webView, request))
                targetContext.assertNoActivityStarted(url)
            }
        }
    }

    @Test
    fun `opted-in intents still reject dangerous data and strip URI grants`() {
        editPreferences { putBoolean(targetContext.getString(R.string.pref_allow_card_external_launch_key), true) }
        targetContext.registerWebBrowser()
        withWebView { webView ->
            val client = shadowOf(webView).webViewClient
            for (scheme in listOf("file", "content", "javascript", "data")) {
                val request = mockWebResourceRequest("intent://example.org/#Intent;scheme=$scheme;end")
                assertTrue(client.shouldOverrideUrlLoading(webView, request))
                targetContext.assertNoActivityStarted()
            }
            val request = mockWebResourceRequest("intent://example.org/#Intent;scheme=https;launchFlags=0xc3;end")
            assertTrue(client.shouldOverrideUrlLoading(webView, request))
            val intent = assertNotNull(shadowOf(targetContext.applicationContext as Application).nextStartedActivity)
            assertEquals("https://example.org/".toUri(), intent.data)
            val grantFlags =
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
            assertEquals(0, intent.flags and grantFlags)
            targetContext.assertNoActivityStarted()
        }
    }

    private fun registerNativeHandler(browsable: Boolean): ComponentName {
        val component = ComponentName("com.example.native", "Handler$browsable")
        shadowOf(targetContext.packageManager).apply {
            addActivityIfNotPresent(component).exported = true
            addIntentFilterForActivity(
                component,
                IntentFilter(Intent.ACTION_VIEW).apply {
                    addCategory(Intent.CATEGORY_DEFAULT)
                    if (browsable) addCategory(Intent.CATEGORY_BROWSABLE)
                    addDataScheme("http")
                    addDataScheme("https")
                    if (browsable) addDataAuthority("native.example.org", null)
                },
            )
        }
        return component
    }

    private fun withWebView(block: (WebView) -> Unit) {
        val note = addBasicAndReversedNote()
        if (viewerType != ViewerType.LEGACY) {
            val intent =
                if (viewerType == ViewerType.PREVIEWER) {
                    PreviewerFragment.getIntent(
                        targetContext,
                        idsFile = IdsFile(createTransientDirectory(), note.cardIds(col), IdsFile.Purpose.PREVIEW),
                        currentIndex = 0,
                    )
                } else {
                    ReviewerFragment.getIntent(targetContext)
                }
            ActivityScenario.launch<CardViewerActivity>(intent).use { scenario ->
                scenario.onActivity { activity ->
                    block(activity.findViewById<SafeWebViewLayout>(R.id.web_view_layout).webView)
                }
            }
        } else {
            val controller = Robolectric.buildActivity(NonAbstractFlashcardViewer::class.java).setup()
            saveControllerForCleanup(controller)
            val viewer = controller.get()
            viewer.onCollectionLoaded(col)
            viewer.loadInitialCard()
            advanceRobolectricLooper()
            advanceRobolectricLooper()
            block(assertNotNull(viewer.webView))
        }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun viewers() = ViewerType.entries.map { arrayOf(it) }
    }

    enum class ViewerType { LEGACY, PREVIEWER, STUDY }
}

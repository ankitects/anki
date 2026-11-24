// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.deck_config.UpdateDeckConfigsMode
import anki.deck_config.UpdateDeckConfigsRequest
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.libanki.Consts
import com.ichi2.anki.settings.Prefs
import com.ichi2.testutils.assertNoActivityStarted
import com.ichi2.testutils.assertOpenedUrl
import com.ichi2.testutils.ext.clear
import com.ichi2.testutils.ext.defaultDeckNewCardsPerDay
import com.ichi2.testutils.mockWebResourceRequest
import com.ichi2.testutils.registerWebBrowser
import com.ichi2.testutils.webView
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Test for [DeckOptions] */
@RunWith(AndroidJUnit4::class)
class DeckOptionsTest : RobolectricTest() {
    private val manualUrl = "https://docs.ankiweb.net/deck-options.html#daily-limits".toUri()

    @Before
    fun registerBrowser() = targetContext.registerWebBrowser()

    @After
    override fun tearDown() {
        super.tearDown()
        Prefs.clear()
    }

    @Test
    fun `double tap interval is sent to the page when it is ready`() {
        Prefs.doubleTapInterval = 800

        withDeckOptions {
            onWebViewReady()

            assertThat(lastEvaluatedJavascript, containsString("setParameterUnlockClickTimeoutMs"))
            assertThat(lastEvaluatedJavascript, containsString("800"))
        }
    }

    @Test
    fun `javascript can reload the current deck options page`() {
        withDeckOptions {
            val webView = webViewLayout.webView
            val pageUrl =
                assertNotNull(webView.url)
                    .toUri()
                    .buildUpon()
                    .fragment(null)
                    .build()
            val request = mockWebResourceRequest(pageUrl.toString())

            for (fragment in listOf(null, "night")) {
                webView.loadUrl(
                    pageUrl
                        .buildUpon()
                        .fragment(fragment)
                        .build()
                        .toString(),
                )
                assertFalse(shadowOf(webView).webViewClient.shouldOverrideUrlLoading(webView, request), "fragment: $fragment")
                targetContext.assertNoActivityStarted("fragment: $fragment")
            }
        }
    }

    @Test
    fun `reloading the current page waits for the save response`() {
        withDeckOptions {
            onWebViewReady()
            val webView = webViewLayout.webView
            val shadow = shadowOf(webView)
            val save = assertIs<DeckOptions.SaveAndOptimizeReload>(shadow.getJavascriptInterface("ankidroidSave"))
            val pageUrl = assertNotNull(webView.url).substringBefore('#')
            webView.loadUrl("$pageUrl#night")
            val request = mockWebResourceRequest(pageUrl)

            save.started()
            assertTrue(shadow.webViewClient.shouldOverrideUrlLoading(webView, request))
            assertEquals(0, shadow.reloadInvocations)
            targetContext.assertNoActivityStarted()

            save.finished(true)
            advanceRobolectricLooper()

            assertEquals(1, shadow.reloadInvocations)
            assertFalse(shadow.webViewClient.shouldOverrideUrlLoading(webView, request))
            targetContext.assertNoActivityStarted()
        }
    }

    @Test
    fun `links to other bundled pages remain internal`() {
        withDeckOptions {
            val webView = webViewLayout.webView
            val otherPage =
                assertNotNull(webView.url)
                    .toUri()
                    .buildUpon()
                    .path("/graphs")
                    .build()
            val request = mockWebResourceRequest(otherPage.toString())

            assertFalse(shadowOf(webView).webViewClient.shouldOverrideUrlLoading(webView, request))
            targetContext.assertNoActivityStarted()
        }
    }

    @Test
    fun `HTTPS manual links remain internal`() {
        withDeckOptions {
            val webView = webViewLayout.webView
            val request = mockWebResourceRequest(manualUrl.toString())

            assertFalse(shadowOf(webView).webViewClient.shouldOverrideUrlLoading(webView, request))
            targetContext.assertNoActivityStarted()
        }
    }

    @Test
    fun `links outside the HTTPS manual origin open externally`() {
        withDeckOptions {
            val webView = webViewLayout.webView
            val otherOrigins =
                listOf(
                    manualUrl.buildUpon().scheme("http").build(),
                    manualUrl.buildUpon().encodedAuthority("docs.ankiweb.net:444").build(),
                    manualUrl.buildUpon().encodedAuthority("docs.ankiweb.net.example.org").build(),
                    manualUrl.buildUpon().encodedAuthority("user@docs.ankiweb.net").build(),
                    manualUrl.buildUpon().encodedAuthority("docs.ankiweb.net@example.org").build(),
                )
            for (url in otherOrigins) {
                val request = mockWebResourceRequest(url.toString())
                assertTrue(shadowOf(webView).webViewClient.shouldOverrideUrlLoading(webView, request), url.toString())
                targetContext.assertOpenedUrl(url)

                webView.loadUrl(url.toString())
                assertTrue(shadowOf(webView).webViewClient.shouldOverrideUrlLoading(webView, request), "reload: $url")
                targetContext.assertOpenedUrl(url)
            }
        }
    }

    @Test
    fun `manual subframes are blocked without launching an activity`() {
        withDeckOptions {
            val webView = webViewLayout.webView
            val request = mockWebResourceRequest(manualUrl.toString(), mainFrame = false)

            assertTrue(shadowOf(webView).webViewClient.shouldOverrideUrlLoading(webView, request))
            targetContext.assertNoActivityStarted()
        }
    }

    @Test
    fun `optimizing all presets saves without closing options`() =
        runTest {
            withDeckOptions {
                updateDeckConfigs(UpdateDeckConfigsMode.UPDATE_DECK_CONFIGS_MODE_COMPUTE_ALL_PARAMS, newPerDay = 42)

                assertFalse(requireActivity().isFinishing)
                assertEquals(42, col.defaultDeckNewCardsPerDay)
            }
        }

    @Test
    fun `normal save closes options`() =
        runTest {
            withDeckOptions {
                updateDeckConfigs(UpdateDeckConfigsMode.UPDATE_DECK_CONFIGS_MODE_NORMAL, newPerDay = 42)

                assertTrue(requireActivity().isFinishing)
                assertEquals(42, col.defaultDeckNewCardsPerDay)
            }
        }

    private suspend fun DeckOptions.updateDeckConfigs(
        mode: UpdateDeckConfigsMode,
        newPerDay: Int,
    ) {
        val data = col.backend.getDeckConfigsForUpdate(Consts.DEFAULT_DECK_ID)
        val config =
            data.allConfigList
                .single()
                .config
                .toBuilder()
        config.setConfig(config.config.toBuilder().setNewPerDay(newPerDay))
        val request =
            UpdateDeckConfigsRequest
                .newBuilder()
                .setTargetDeckId(Consts.DEFAULT_DECK_ID)
                .setMode(mode)
                .setFsrs(true)
                .addConfigs(config)
                .build()
                .toByteArray()
        requireActivity().updateDeckConfigsRaw(request)
    }

    private inline fun withDeckOptions(block: DeckOptions.() -> Unit) {
        val activity = startDeckOptionsController().get()
        advanceRobolectricLooper()
        block(activity.fragment as DeckOptions)
    }

    /** The last JavaScript evaluated by the WebView of [DeckOptions] */
    private val DeckOptions.lastEvaluatedJavascript: String?
        get() = shadowOf(webViewLayout.webView).lastEvaluatedJavascript
}

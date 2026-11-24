// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.testutils.assertNoActivityStarted
import com.ichi2.testutils.assertOpenedUrl
import com.ichi2.testutils.registerWebBrowser
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class PageWebViewClientNavigationTest : PageWebViewClientTestBase() {
    @Before
    fun registerBrowser() = targetContext.registerWebBrowser()

    @Test
    fun `external HTTP and HTTPS navigation opens the requested URL`() =
        withStatistics { view, client ->
            val addresses =
                listOf(
                    "http://example.org/graphs?search=hello#anchor",
                    "https://example.org/graphs?search=hello#anchor",
                    "https://docs.ankiweb.net/deck-options.html#daily-limits",
                )
            for (address in addresses) {
                assertTrue(client.shouldOverrideUrlLoading(view, request(address)), address)
                targetContext.assertOpenedUrl(address.toUri())
            }
        }

    @Test
    fun `external subframes are blocked`() =
        withStatistics { view, client ->
            for (scheme in listOf("http", "https")) {
                val address = "$scheme://example.org/graphs"
                assertTrue(client.shouldOverrideUrlLoading(view, request(address, mainFrame = false)), address)
                targetContext.assertNoActivityStarted(address)
            }
        }

    @Test
    fun `bundled pages and nested routes stay internal with queries and fragments`() =
        withStatistics { view, client ->
            val pageUrl = assertNotNull(view.url).toUri()
            val paths =
                listOf(
                    "/graphs",
                    "/congrats",
                    "/card-info",
                    "/change-notetype",
                    "/deck-options",
                    "/import-anki-package",
                    "/import-csv",
                    "/import-page",
                    "/image-occlusion",
                    "/deck-options/1",
                    "/card-info/1/2",
                )
            for (path in paths) {
                val url =
                    pageUrl
                        .buildUpon()
                        .path(path)
                        .query("test=1")
                        .fragment("night")
                        .build()
                assertFalse(client.shouldOverrideUrlLoading(view, request(url.toString())), url.toString())
                targetContext.assertNoActivityStarted(url.toString())
            }
        }

    @Test
    fun `bundled page paths on other origins open externally`() =
        withStatistics { view, client ->
            val url = assertNotNull(view.url).toUri()
            val otherOrigins =
                listOf(
                    url.buildUpon().scheme("http").build(),
                    url.buildUpon().encodedAuthority("${url.host}:8443").build(),
                    url.buildUpon().encodedAuthority("example.org").build(),
                    url.buildUpon().encodedAuthority("user@${url.encodedAuthority}").build(),
                )
            for (other in otherOrigins) {
                assertTrue(client.shouldOverrideUrlLoading(view, request(other.toString())), other.toString())
                targetContext.assertOpenedUrl(other)
            }
        }

    @Test
    fun `unbundled local paths are blocked`() =
        withStatistics { view, client ->
            val pageUrl = assertNotNull(view.url).toUri()
            for (path in listOf("/", "/unknown", "/graphs-other", "/editor", "/editor/1", "/preferences", "/_anki/test", "/_app/env.js")) {
                val address =
                    pageUrl
                        .buildUpon()
                        .path(path)
                        .build()
                        .toString()
                assertTrue(client.shouldOverrideUrlLoading(view, request(address)), address)
                targetContext.assertNoActivityStarted(address)
            }
        }

    @Test
    fun `callback rejects non-HTTP schemes without launching an activity`() =
        withStatistics { view, client ->
            val addresses =
                listOf(
                    "intent://example.org/#Intent;scheme=https;end",
                    "file:///tmp/test.html",
                    "content://example.org/test.html",
                    "data:text/html,test",
                    "javascript:void(0)",
                )
            for (address in addresses) {
                assertTrue(client.shouldOverrideUrlLoading(view, request(address)), address)
                targetContext.assertNoActivityStarted(address)
            }
        }
}

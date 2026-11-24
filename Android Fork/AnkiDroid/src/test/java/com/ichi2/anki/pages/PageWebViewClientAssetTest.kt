// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.jsoup.Jsoup
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class PageWebViewClientAssetTest : PageWebViewClientTestBase() {
    @Test
    fun `bundled SvelteKit CSP matches the reviewed declaration`() {
        val bundledHtml =
            targetContext.assets
                .open("backend/sveltekit/index.html")
                .bufferedReader()
                .use { it.readText() }
        val policies = Jsoup.parse(bundledHtml).select("meta[http-equiv=content-security-policy]").map { it.attr("content") }

        // Pin the entire declaration, including the bootstrap script hash, to require review on backend updates.
        // Backend updates are expected to change this hash and fail this test until the new declaration is reviewed.
        val expectedPolicy = "script-src 'self' 'sha256-NQxTRfw44PoCebiYF3iWjCm7D5GHMeJtVfcdODZjiH8='"
        assertEquals(
            listOf(expectedPolicy),
            policies,
            "Bundled SvelteKit CSP changed. This is expected after a backend update changes the bootstrap script hash. " +
                "Review the policy and the congrats exemption before updating this expectation.",
        )
    }

    @Test
    fun `congrats omits CSP so custom study and unbury bridge links can run`() =
        withStatistics { view, client ->
            val bundledHtml =
                view.context.assets
                    .open("backend/sveltekit/index.html")
                    .bufferedReader()
                    .use { it.readText() }
            val expected = Jsoup.parse(bundledHtml)
            assertNotNull(expected.selectFirst("meta[http-equiv=content-security-policy]"))
            expected.select("meta[http-equiv=content-security-policy]").remove()

            val url =
                assertNotNull(view.url)
                    .toUri()
                    .buildUpon()
                    .path("/congrats")
                    .build()
            val response = assertNotNull(client.shouldInterceptRequest(view, request(url.toString())))
            val html = response.data.bufferedReader().use { it.readText() }
            assertEquals(expected.outerHtml(), Jsoup.parse(html).outerHtml())
        }

    @Test
    fun `other supported pages retain the bundled HTML and CSP unchanged`() =
        withStatistics { view, client ->
            val expected =
                view.context.assets
                    .open("backend/sveltekit/index.html")
                    .use { it.readBytes() }
            assertNotNull(Jsoup.parse(expected.decodeToString()).selectFirst("meta[http-equiv=content-security-policy]"))

            val pageUrl = assertNotNull(view.url).toUri()
            val paths =
                listOf(
                    "/graphs",
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
                val url = pageUrl.buildUpon().path(path).build()
                val response = assertNotNull(client.shouldInterceptRequest(view, request(url.toString())))
                assertContentEquals(expected, response.data.use { it.readBytes() }, path)
            }
        }

    @Test
    fun `unsupported routes do not serve bundled HTML`() =
        withStatistics { view, client ->
            val pageUrl = assertNotNull(view.url).toUri()
            for (path in listOf("/", "/unknown", "/graphs-other", "/editor", "/editor/1", "/preferences")) {
                val url = pageUrl.buildUpon().path(path).build()
                assertNull(client.shouldInterceptRequest(view, request(url.toString())), path)
            }
        }

    @Test
    fun `bundled assets are served unchanged`() =
        withStatistics { view, client ->
            val url =
                assertNotNull(view.url)
                    .toUri()
                    .buildUpon()
                    .path("/_app/version.json")
                    .build()
            val expected =
                view.context.assets
                    .open("backend/sveltekit/app/version.json")
                    .use { it.readBytes() }
            val response = assertNotNull(client.shouldInterceptRequest(view, request(url.toString())))
            assertContentEquals(expected, response.data.use { it.readBytes() })
        }

    @Test
    fun `only the page server can serve bundled pages and assets`() =
        withStatistics { view, client ->
            val pageUrl = assertNotNull(view.url).toUri()
            // Use a real file in the backend.
            val assetUrl = pageUrl.buildUpon().path("/_app/version.json").build()
            for (url in listOf(pageUrl, assetUrl)) {
                assertNotNull(client.shouldInterceptRequest(view, request(url.toString())), url.toString()).data.close()

                val otherOrigins =
                    listOf(
                        url.buildUpon().scheme("http").build(),
                        url.buildUpon().encodedAuthority("${url.host}:8443").build(),
                        url.buildUpon().encodedAuthority("example.org").build(),
                        url.buildUpon().encodedAuthority("user@${url.encodedAuthority}").build(),
                    )
                for (other in otherOrigins) {
                    assertNull(client.shouldInterceptRequest(view, request(other.toString())), other.toString())
                }
            }
        }
}

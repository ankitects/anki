// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 YongWoo Shin <onlym6659@gmail.com>

package com.ichi2.anki

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.SharedDecksActivity.Companion.HTTP_STATUS_TOO_MANY_REQUESTS
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric
import org.robolectric.Shadows
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class SharedDecksActivityTest : RobolectricTest() {
    @Test
    fun redirectWhenUserIsNotLogin() {
        val controller = Robolectric.buildActivity(SharedDecksActivity::class.java).create()
        val activity = controller.get()
        saveControllerForCleanup(controller)

        val webView = activity.findViewById<WebView>(R.id.web_view)
        val spyWebClient = spy(activity.webViewClient)
        doReturn(false).whenever(spyWebClient).isLoggedInToAnkiWeb
        webView.webViewClient = spyWebClient

        controller.start().resume().visible()

        val mockRequest = mock<WebResourceRequest>()
        val mockErrorResponse = mock<WebResourceResponse>()
        whenever(mockErrorResponse.statusCode).thenReturn(HTTP_STATUS_TOO_MANY_REQUESTS)

        spyWebClient.onReceivedHttpError(webView, mockRequest, mockErrorResponse)

        val expectedUrl = getResourceString(R.string.shared_decks_login_url)
        val actualUrl = Shadows.shadowOf(webView).lastLoadedUrl
        assertEquals(expectedUrl, actualUrl)
    }

    @Test
    fun isNotRedirectWhenUserLogin() {
        val controller = Robolectric.buildActivity(SharedDecksActivity::class.java).create()
        val activity = controller.get()
        saveControllerForCleanup(controller)

        val webView = activity.findViewById<WebView>(R.id.web_view)
        val spyWebClient = spy(activity.webViewClient)
        doReturn(true).whenever(spyWebClient).isLoggedInToAnkiWeb
        webView.webViewClient = spyWebClient

        controller.start().resume().visible()

        val mockRequest = mock<WebResourceRequest>()
        val mockErrorResponse = mock<WebResourceResponse>()
        whenever(mockErrorResponse.statusCode).thenReturn(HTTP_STATUS_TOO_MANY_REQUESTS)

        spyWebClient.onReceivedHttpError(webView, mockRequest, mockErrorResponse)

        val expectedUrl = getResourceString(R.string.shared_decks_url)
        val lastUrl = Shadows.shadowOf(webView).lastLoadedUrl
        assertEquals(expectedUrl, lastUrl)
    }
}

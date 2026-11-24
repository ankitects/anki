// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.webkit.WebResourceRequest
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.anki.pages.PostRequestBridge
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class ViewerResourceHandlerTest : RobolectricTest() {
    private val mediaDir: File
        get() = CollectionHelper.getMediaDirectory(targetContext).apply { mkdirs() }

    @Test
    fun `file in the media directory is served`() {
        File(mediaDir, "hello.txt").writeText("hello")

        val response = ViewerResourceHandler(targetContext).shouldInterceptRequest(request("/hello.txt"))

        assertEquals("hello", assertNotNull(response).data.reader().readText())
    }

    @Test
    fun `path traversal outside the media directory is blocked`() {
        File(mediaDir.parentFile, "outside.txt").writeText("secret")

        val response = ViewerResourceHandler(targetContext).shouldInterceptRequest(request("/../outside.txt"))

        assertNull(response, "a request escaping the media directory must not be served")
    }

    @Test
    fun `MathJax configuration is available at the backend URL`() {
        val response = ViewerResourceHandler(targetContext).shouldInterceptRequest(request("/_anki/js/mathjax.js"))

        val expected = readAssetText("backend/js/mathjax.js")
        assertEquals(expected, assertNotNull(response).data.bufferedReader().use { it.readText() })
    }

    @Test
    fun `MathJax implementation is available at the backend URL`() {
        val response = ViewerResourceHandler(targetContext).shouldInterceptRequest(request("/_anki/js/vendor/mathjax/tex-chtml-full.js"))

        val expected = readAssetText("backend/js/vendor/mathjax/tex-chtml-full.js")
        assertEquals(expected, assertNotNull(response).data.bufferedReader().use { it.readText() })
    }

    private fun readAssetText(path: String): String =
        targetContext.assets
            .open(path)
            .bufferedReader()
            .use { it.readText() }

    private fun request(path: String): WebResourceRequest =
        mock {
            on { url } doReturn "${PostRequestBridge.BASE_URL.removeSuffix("/")}$path".toUri()
            on { method } doReturn "GET"
            on { requestHeaders } doReturn emptyMap()
        }
}

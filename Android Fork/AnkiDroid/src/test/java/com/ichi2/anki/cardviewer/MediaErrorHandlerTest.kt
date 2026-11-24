// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Kael Madar <itsybitsyspider@madarhome.com>

package com.ichi2.anki.cardviewer

import android.net.Uri
import android.webkit.WebResourceRequest
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.EmptyApplicationCategory
import com.ichi2.anki.pages.PostRequestBridge
import com.ichi2.testutils.EmptyApplication
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.equalTo
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

// PERF:
// Theoretically should be able to get away with not using this, but it requires WebResourceRequest (easy to mock)
// and URLUtil.guessFileName (static - likely harder)
@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class MediaErrorHandlerTest {
    private lateinit var sut: MediaErrorHandler
    private var timesCalled = 0
    private lateinit var fileNames: MutableList<String?>

    @Before
    fun before() {
        fileNames = ArrayList()
        sut = MediaErrorHandler()
    }

    private fun defaultHandler(): (String) -> Unit =
        { f: String? ->
            timesCalled++
            fileNames.add(f)
        }

    @Test
    fun firstTimeOnNewCardSends() {
        processFailure(getValidRequest("example.jpg"))
        assertThat(timesCalled, equalTo(1))
        assertThat(fileNames, contains("example.jpg"))
    }

    @Test
    fun twoCallsOnSameSideCallsOnce() {
        processFailure(getValidRequest("example.jpg"))
        processFailure(getValidRequest("example2.jpg"))
        assertThat(timesCalled, equalTo(1))
        assertThat(fileNames, contains("example.jpg"))
    }

    @Test
    fun callAfterFlipIsShown() {
        processFailure(getValidRequest("example.jpg"))
        sut.onCardSideChange()
        processFailure(getValidRequest("example2.jpg"))
        assertThat(timesCalled, equalTo(2))
        assertThat(fileNames, contains("example.jpg", "example2.jpg"))
    }

    @Test
    fun callAfterFlipSameImageIsIgnored() {
        processFailure(getValidRequest("example.jpg"))
        sut.onCardSideChange()
        processFailure(getValidRequest("example.jpg"))
        assertThat(timesCalled, equalTo(1))
        assertThat(fileNames, contains("example.jpg"))
    }

    @Test
    fun callAfterFlipDuplicateDoesNotHideDifferentImage() {
        processFailure(getValidRequest("example.jpg"))
        sut.onCardSideChange()
        processFailure(getValidRequest("example.jpg"))
        processFailure(getValidRequest("example2.jpg"))
        assertThat(fileNames, contains("example.jpg", "example2.jpg"))
    }

    @Test
    fun thirdCallIsIgnored() {
        processFailure(getValidRequest("example.jpg"))
        sut.onCardSideChange()
        processFailure(getValidRequest("example2.jpg"))
        sut.onCardSideChange()
        processFailure(getValidRequest("example3.jpg"))
        assertThat(timesCalled, equalTo(2))
        assertThat(fileNames, contains("example.jpg", "example2.jpg"))
    }

    @Test
    fun invalidRequestIsIgnored() {
        val invalidRequest = getInvalidRequest("example.jpg")
        processFailure(invalidRequest)
        assertThat(timesCalled, equalTo(0))
    }

    @Test
    fun jsApiFailuresAreNotReportedAsMissingMedia() {
        for (method in listOf("POST", "GET", "OPTIONS")) {
            for (endpoint in listOf("cardDue", "cardMod", "ttsSetLanguage", "showAnswer?test=1")) {
                processFailure(getWebResourceRequest("${PostRequestBridge.BASE_URL}jsapi/$endpoint", method))
                assertThat("$method $endpoint", timesCalled, equalTo(0))
            }
        }
    }

    @Test
    fun jsApiFailureDoesNotHideMissingMediaOnSameSide() {
        processFailure(getWebResourceRequest("${PostRequestBridge.BASE_URL}jsapi/cardDue", "POST"))
        processFailure(getValidRequest("example.jpg"))
        assertThat(fileNames, contains("example.jpg"))
    }

    @Test
    fun jsApiFailuresDoNotConsumeMissingMediaLimit() {
        processFailure(getWebResourceRequest("${PostRequestBridge.BASE_URL}jsapi/cardDue", "POST"))
        sut.onCardSideChange()
        processFailure(getWebResourceRequest("${PostRequestBridge.BASE_URL}jsapi/cardMod", "POST"))
        sut.onCardSideChange()
        processFailure(getValidRequest("example.jpg"))
        sut.onCardSideChange()
        processFailure(getValidRequest("example2.jpg"))
        sut.onCardSideChange()
        processFailure(getValidRequest("example3.jpg"))
        assertThat(fileNames, contains("example.jpg", "example2.jpg"))
    }

    @Test
    fun mediaWithApiLikeNamesIsStillReported() {
        processFailure(getWebResourceRequest("${PostRequestBridge.BASE_URL}jsapi/cardDue", "POST"))
        processFailure(getValidRequest("cardDue.bin"))
        sut.onCardSideChange()
        processFailure(getValidRequest("jsapi.jpg"))
        assertThat(fileNames, contains("cardDue.bin", "jsapi.jpg"))
    }

    private fun processFailure(
        invalidRequest: WebResourceRequest,
        consumer: (String) -> Unit = defaultHandler(),
    ) {
        sut.processFailure(invalidRequest, consumer)
    }

    private fun processMissingMedia(
        file: File,
        onFailure: (String) -> Unit,
    ) {
        sut.processMissingMedia(file, onFailure)
    }

    @Test
    fun uiFailureDoesNotCrash() {
        processFailure(getValidRequest("example.jpg")) { throw RuntimeException("expected") }
        assertThat("Irrelevant assert to stop lint warnings", timesCalled, equalTo(0))
    }

    @Test
    fun imageIsNotRememberedWhenUiFails() {
        processFailure(getValidRequest("example.jpg")) { throw RuntimeException("expected") }
        sut.onCardSideChange()
        processFailure(getValidRequest("example.jpg"))
        assertThat(fileNames, contains("example.jpg"))
    }

    @Test
    fun repeatedMissingSoundOnSameSideIsShown() {
        val handler = defaultHandler()
        repeat(3) {
            processMissingMedia(File("example.wav"), handler)
        }
        assertThat(fileNames, contains("example.wav", "example.wav", "example.wav"))
    }

    @Test
    fun testThirdSoundIsIgnored() {
        // Tests that the third call to processMissingSound is ignored
        val handler = defaultHandler()
        processMissingMedia(File("example.wav"), handler)
        sut.onCardSideChange()
        processMissingMedia(File("example2.wav"), handler)
        sut.onCardSideChange()
        processMissingMedia(File("example3.wav"), handler)
        assertThat(timesCalled, equalTo(2))
        assertThat(fileNames, contains("example.wav", "example2.wav"))
    }

    @Test
    fun testMissingSound_ExceptionCaught() {
        assertDoesNotThrow { processMissingMedia(File("example.wav")) { throw RuntimeException("expected") } }
    }

    private fun getValidRequest(fileName: String): WebResourceRequest {
        val url = "${PostRequestBridge.BASE_URL}$fileName"
        return getWebResourceRequest(url)
    }

    private fun getInvalidRequest(fileName: String): WebResourceRequest {
        // no collection.media in the URL
        val url = "file:///storage/emulated/0/AnkiDroid/$fileName"
        return getWebResourceRequest(url)
    }

    private fun getWebResourceRequest(
        url: String,
        method: String = "GET",
    ): WebResourceRequest =
        object : WebResourceRequest {
            override fun getUrl(): Uri = url.toUri()

            override fun isForMainFrame(): Boolean = false

            override fun isRedirect(): Boolean = false

            override fun hasGesture(): Boolean = false

            override fun getMethod(): String = method

            override fun getRequestHeaders(): Map<String, String>? = null
        }
}

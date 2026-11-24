// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import android.view.WindowManager
import android.webkit.JsResult
import android.webkit.WebView
import androidx.appcompat.app.AlertDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.EmptyApplicationCategory
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.common.crashreporting.CrashReporter
import com.ichi2.testutils.AndroidTest
import com.ichi2.testutils.EmptyApplication
import com.ichi2.testutils.targetContext
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.mockito.Answers.RETURNS_SELF
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.withSettings
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class PageChromeClientTest : AndroidTest {
    private lateinit var originalCrashReporter: CrashReporter
    private val crashReporter = mock<CrashReporter>()
    private val client = PageChromeClient()
    private val view = mock<WebView>()
    private val result = mock<JsResult>()

    @Before
    fun replaceCrashReporter() {
        originalCrashReporter = CrashReportService.getReporter()
        CrashReportService.setReporter(crashReporter)
        whenever(view.context).thenReturn(targetContext)
    }

    @After
    fun restoreCrashReporter() {
        CrashReportService.setReporter(originalCrashReporter)
    }

    @Test
    fun `alert at window limit is canceled and reported silently`() {
        val failure = IllegalStateException("window count is over max!!")
        assertFailureHandled(failure) { client.onJsAlert(view, "http://localhost/", "message", result) }
        verify(crashReporter).sendExceptionReport(failure, "onJsAlert:windowCount", "http://localhost/: message", onlyIfSilent = true)
        verifyNoMoreInteractions(crashReporter)
    }

    @Test
    fun `confirm at window limit is canceled and reported silently`() {
        val failure = IllegalStateException("window count is over max!!")
        assertFailureHandled(failure) { client.onJsConfirm(view, "http://localhost/", "message", result) }
        verify(crashReporter).sendExceptionReport(failure, "onJsConfirm:windowCount", "http://localhost/: message", onlyIfSilent = true)
        verifyNoMoreInteractions(crashReporter)
    }

    @Test
    fun `alert with invalid window token is canceled`() {
        assertFailureHandled(WindowManager.BadTokenException()) { client.onJsAlert(view, "http://localhost/", "message", result) }
        verifyNoInteractions(crashReporter)
    }

    @Test
    fun `confirm with invalid window token is canceled`() {
        assertFailureHandled(WindowManager.BadTokenException()) { client.onJsConfirm(view, "http://localhost/", "message", result) }
        verifyNoInteractions(crashReporter)
    }

    private fun assertFailureHandled(
        failure: RuntimeException,
        showDialog: () -> Boolean,
    ) {
        mockConstruction(AlertDialog.Builder::class.java, withSettings().defaultAnswer(RETURNS_SELF)) { builder, _ ->
            whenever(builder.show()).thenThrow(failure)
        }.use {
            assertTrue(showDialog(), "WebView must not attempt to display a fallback dialog")
        }
        verify(result).cancel()
        verifyNoMoreInteractions(result)
    }
}

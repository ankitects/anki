// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.webkit.CookieManager
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.dialogs.utils.message
import com.ichi2.anki.dialogs.utils.title
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

@RunWith(AndroidJUnit4::class)
@Config(application = DeckPickerWebViewErrorTest.MissingWebViewApplication::class, qualifiers = "normal")
class DeckPickerWebViewErrorTest : RobolectricTest() {
    @After
    fun resetWebViewMock() {
        unmockkStatic(CookieManager::class)
    }

    @Test
    fun `app opens DeckPicker with an error dialog when WebView is unavailable`() {
        ActivityScenario.launch(DeckPicker::class.java).use { scenario ->
            advanceRobolectricLooperUntil {
                ShadowDialog.getLatestDialog()?.isShowing == true
            }
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
                val dialog = assertIs<AlertDialog>(ShadowDialog.getLatestDialog())
                assertEquals(activity.getString(CommonString.ankidroid_init_failed_webview_title), dialog.title)
                assertContains(dialog.message, "WebView unavailable")
            }
        }
    }

    class MissingWebViewApplication : AnkiDroidApp() {
        override fun onCreate() {
            mockkStatic(CookieManager::class)
            every { CookieManager.getInstance() } throws IllegalStateException("WebView unavailable")
            super.onCreate()
        }
    }
}

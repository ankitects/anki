// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.cardviewer

import android.app.Activity
import android.os.Looper
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import androidx.core.content.getSystemService
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.testutils.RecordingInputMethodManager
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.util.ReflectionHelpers
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(shadows = [RecordingInputMethodManager::class])
class TypeAnswerWebViewLifecycleTest : RobolectricTest() {
    @Test
    fun `repeated keyboard state does not restart input again`() =
        withWebView { webView, manager ->
            webView.notifyFromBridge(true)
            webView.notifyFromBridge(true)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(listOf<View>(webView), manager.restartedViews)

            webView.notifyFromBridge(false)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(listOf<View>(webView, webView), manager.restartedViews)
        }

    @Test
    fun `queued keyboard change is ignored after destruction`() =
        withWebView { webView, manager ->
            webView.notifyFromBridge(true)
            (webView.parent as FrameLayout).removeView(webView)
            webView.destroy()
            shadowOf(Looper.getMainLooper()).idle()
            // Detachment alone prevents restartInput; also check that the queued update was ignored.
            assertFalse(ReflectionHelpers.getField(webView, "noSuggest"))
            assertTrue(manager.restartedViews.isEmpty())
        }

    private fun TypeAnswerWebView.notifyFromBridge(enabled: Boolean) {
        val bridge = shadowOf(this).getJavascriptInterface("AnkiDroidKeyboard")
        CompletableFuture
            .runAsync {
                bridge.javaClass
                    .getDeclaredMethod("setNoSuggest", Boolean::class.javaPrimitiveType)
                    .apply {
                        isAccessible = true
                    }.invoke(bridge, enabled)
            }.get(5, TimeUnit.SECONDS)
    }

    private fun withWebView(block: (TypeAnswerWebView, RecordingInputMethodManager) -> Unit) {
        Robolectric.buildActivity(Activity::class.java).use { controller ->
            val activity = controller.setup().get()
            val webView = TypeAnswerWebView(activity)
            val parent = FrameLayout(activity).apply { addView(webView) }
            activity.setContentView(parent)
            val manager = Shadow.extract<RecordingInputMethodManager>(activity.getSystemService<InputMethodManager>())
            manager.restartedViews.clear()
            try {
                block(webView, manager)
            } finally {
                if (!shadowOf(webView).wasDestroyCalled()) {
                    parent.removeView(webView)
                    webView.destroy()
                }
            }
        }
    }
}

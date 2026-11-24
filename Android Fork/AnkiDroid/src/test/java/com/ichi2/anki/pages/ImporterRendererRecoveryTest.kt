// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import android.content.Intent
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.pages.PostRequestBridge.Companion.HEADER_SIZE
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import java.nio.ByteBuffer
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

/** Import cancellation must follow the newly loaded page after renderer recovery. */
@RunWith(AndroidJUnit4::class)
class ImporterRendererRecoveryTest : RobolectricTest() {
    @Test
    fun `package import can be cancelled after completed page is recreated`() =
        runTest {
            assertCancellationAfterRecovery(AnkiPackageImporterFragment.getIntent(targetContext, "/unused.apkg"), cancel = false)
        }

    @Test
    fun `CSV import can be cancelled after completed page is recreated`() =
        runTest {
            assertCancellationAfterRecovery(CsvImporter.getIntent(targetContext, "/unused.csv"), cancel = false)
        }

    @Test
    fun `package import can be cancelled after cancelled page is recreated`() =
        runTest {
            assertCancellationAfterRecovery(AnkiPackageImporterFragment.getIntent(targetContext, "/unused.apkg"), cancel = true)
        }

    @Test
    fun `CSV import can be cancelled after cancelled page is recreated`() =
        runTest {
            assertCancellationAfterRecovery(CsvImporter.getIntent(targetContext, "/unused.csv"), cancel = true)
        }

    private suspend fun TestScope.assertCancellationAfterRecovery(
        intent: Intent,
        cancel: Boolean,
    ) {
        Robolectric.buildActivity(SingleFragmentActivity::class.java, intent).use { controller ->
            controller.setup().visible()
            advanceUntilIdle()
            advanceRobolectricLooper()
            val activity = controller.get()
            val page = activity.fragment as PageFragment
            val bridge = PostRequestBridge(page, this)
            val dispatcher = activity.onBackPressedDispatcher

            assertSuccessful(bridge.handleMessage(request("latestProgress")))
            assertTrue(dispatcher.hasEnabledCallbacks())
            if (cancel) {
                dispatcher.onBackPressed()
                assertFalse(activity.isFinishing)
            } else {
                assertSuccessful(bridge.handleMessage(request("importDone")))
            }
            assertFalse(dispatcher.hasEnabledCallbacks())

            // Simulate renderer loss and exercise the production WebView recovery path.
            val layout = page.webViewLayout
            val previous = layout.getChildAt(0) as WebView
            assertTrue(layout.isAttachedToWindow)
            layout.onRenderProcessGone(previous)
            advanceUntilIdle()
            advanceRobolectricLooper()
            assertNotSame(previous, layout.getChildAt(0), "Recovery must replace and configure the WebView")

            // Recovery reloads the initial importer route. Its next import must be cancellable.
            val recoveredBridge = PostRequestBridge(page, this)
            assertSuccessful(recoveredBridge.handleMessage(request("latestProgress")))
            assertTrue(dispatcher.hasEnabledCallbacks(), "The new import page must have a fresh, registered cancellation callback")
            dispatcher.onBackPressed()
            assertFalse(activity.isFinishing, "Back must cancel the new import rather than leave")
        }
    }

    private fun assertSuccessful(reply: ByteArray?) {
        assertEquals(200, ByteBuffer.wrap(assertNotNull(reply)).getShort(Int.SIZE_BYTES).toInt())
    }

    private fun request(method: String): ByteArray {
        val path = "/_anki/$method".encodeToByteArray()
        return ByteBuffer
            .allocate(HEADER_SIZE + path.size)
            .putInt(1)
            .putShort(path.size.toShort())
            .put(path)
            .array()
    }
}

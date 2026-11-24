// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import android.content.Intent
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
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class ImporterPostRequestBridgeTest : RobolectricTest() {
    @Test
    fun `package import progress enables back cancellation through the bridge`() =
        runTest {
            assertBackCancelsImport(AnkiPackageImporterFragment.getIntent(targetContext, "/unused.apkg"))
        }

    @Test
    fun `CSV import progress enables back cancellation through the bridge`() =
        runTest {
            assertBackCancelsImport(CsvImporter.getIntent(targetContext, "/unused.csv"))
        }

    private suspend fun TestScope.assertBackCancelsImport(intent: Intent) {
        Robolectric.buildActivity(SingleFragmentActivity::class.java, intent).use { controller ->
            controller.setup()
            advanceUntilIdle()
            advanceRobolectricLooper()
            val activity = controller.get()
            val bridge = PostRequestBridge(activity.fragment as PageFragment, this)
            val backDispatcher = activity.onBackPressedDispatcher
            assertFalse(backDispatcher.hasEnabledCallbacks(), "Back should leave before an import starts")

            // The frontend now sends progress polling through the bridge. Do not synthesize
            // onLoadResource(): a WebMessage does not cause that WebView callback.
            val response = assertNotNull(bridge.handleMessage(request("/_anki/latestProgress")))
            assertEquals(200, ByteBuffer.wrap(response).getShort(Int.SIZE_BYTES).toInt(), "The real progress handler must succeed")
            advanceUntilIdle()
            advanceRobolectricLooper()

            assertTrue(backDispatcher.hasEnabledCallbacks(), "Progress polling must enable import cancellation")
            backDispatcher.onBackPressed()
            assertFalse(activity.isFinishing, "The first Back should cancel the import, not close its screen")

            bridge.handleMessage(request("/_anki/importDone"))
            bridge.handleMessage(request("/_anki/latestProgress"))
            advanceUntilIdle()
            assertFalse(backDispatcher.hasEnabledCallbacks(), "Late polling must not re-enable cancellation after importDone")
            backDispatcher.onBackPressed()
            assertTrue(activity.isFinishing, "Back should leave after the import finishes")
        }
    }

    /** Empty protobuf request, encoded with the bridge's documented wire format. */
    private fun request(uri: String): ByteArray {
        val path = uri.encodeToByteArray()
        return ByteBuffer
            .allocate(HEADER_SIZE + path.size)
            .putInt(1)
            .putShort(path.size.toShort())
            .put(path)
            .array()
    }
}

// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>
package com.ichi2.anki.mediacheck

import android.webkit.WebView
import android.widget.Button
import androidx.core.view.isVisible
import androidx.fragment.app.commitNow
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.BackendError
import anki.backend.backendError
import anki.media.CheckMediaResponse
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CommonPlurals
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.workarounds.SafeWebViewLayout
import com.ichi2.testutils.rules.BackendOverrideRule
import com.ichi2.utils.positiveButton
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.exceptions.BackendIoException
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowDialog
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class MediaCheckFragmentTest : RobolectricTest() {
    private val failure =
        BackendIoException(
            backendError {
                kind = BackendError.Kind.IO_ERROR
                message = "Operation not permitted (os error 1)"
            },
        )

    @get:Rule
    val backend = BackendOverrideRule()

    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    /** Issue 22075 */
    @Test
    fun `failed media check shows an error`() =
        runTest {
            backend.replaceWith {
                object : Backend() {
                    override fun checkMedia(): CheckMediaResponse = throw failure
                }
            }

            startMediaCheck()

            assertEquals(failure.localizedMessage, getAlertDialogText(checkDismissed = true))
        }

    @Test
    fun `onRenderProcessGone recovers webview and reloads content`() =
        runTest {
            val mediaCheck = startMediaCheck()
            advanceUntilIdle()
            advanceRobolectricLooper()

            val layout = mediaCheck.get().findViewById<SafeWebViewLayout>(R.id.web_view)
            val initialWebView = assertNotNull(layout.getChildAt(0) as? WebView)

            layout.onRenderProcessGone(initialWebView)
            advanceRobolectricLooper()

            assertTrue(shadowOf(initialWebView).wasDestroyCalled(), "Dead WebView should be destroyed")
            assertNull(initialWebView.parent, "Dead WebView should be removed from parent")

            val recoveredWebView = assertNotNull(layout.getChildAt(0) as? WebView)
            assertNotSame(initialWebView, recoveredWebView, "A new WebView instance should be created")
            assertFalse(shadowOf(recoveredWebView).wasDestroyCalled(), "Recovered WebView should not be destroyed")
        }

    @Test
    fun `onRenderProcessGone after fragment view is destroyed cleans up dead webview without recreating`() =
        runTest {
            val mediaCheck = startMediaCheck()
            advanceUntilIdle()
            advanceRobolectricLooper()

            val layout = mediaCheck.get().findViewById<SafeWebViewLayout>(R.id.web_view)
            val initialWebView = assertNotNull(layout.getChildAt(0) as? WebView)

            val fragment = mediaCheck.get().supportFragmentManager.findFragmentById(R.id.fragment_container) as MediaCheckFragment
            mediaCheck.get().supportFragmentManager.commitNow { detach(fragment) }
            assertNull(fragment.view, "Fragment view should be null after detach")

            layout.onRenderProcessGone(initialWebView)

            assertTrue(shadowOf(initialWebView).wasDestroyCalled(), "Dead WebView should be destroyed")
            assertNull(initialWebView.parent, "Dead WebView should be removed from parent")
            assertEquals(0, layout.childCount, "No new WebView should be created when fragment view is gone")
        }

    @Test
    fun `media check result after view destruction is displayed after reattachment`() =
        runTest {
            val mediaCheck = startMediaCheck()
            advanceUntilIdle()
            advanceRobolectricLooper()
            val manager = mediaCheck.get().supportFragmentManager
            val fragment = manager.findFragmentById(R.id.fragment_container) as MediaCheckFragment
            val viewModel = ViewModelProvider(fragment)[MediaCheckViewModel::class.java]
            val oldView = fragment.requireView()

            manager.commitNow { detach(fragment) }
            addUnusedMedia()
            viewModel.checkMedia().join()
            advanceUntilIdle()
            advanceRobolectricLooper()
            assertNull(fragment.view)

            manager.commitNow { attach(fragment) }
            advanceUntilIdle()
            advanceRobolectricLooper()

            val newView = fragment.requireView()
            assertNotSame(oldView, newView)
            assertTrue(newView.findViewById<Button>(R.id.delete_used_media_button).isVisible)
            val layout = newView.findViewById<SafeWebViewLayout>(R.id.web_view)
            val webView = assertNotNull(layout.getChildAt(0) as? WebView)
            assertContains(shadowOf(webView).lastLoadDataWithBaseURL.data, "unused.png")
        }

    @Test
    fun `deleting unused media shows the result`() =
        runTest {
            addUnusedMedia()
            val mediaCheck = startMediaCheck()

            mediaCheck.confirmDeletion()

            assertEquals(oneFileDeleted, getAlertDialogText(checkDismissed = true))
            clickAlertDialogButton { positiveButton }
            assertTrue(mediaCheck.get().isFinishing)
        }

    @Test
    fun `result is shown again after recreation`() =
        runTest {
            addUnusedMedia()
            val mediaCheck = startMediaCheck()
            mediaCheck.confirmDeletion()
            val shownBeforeRecreation = ShadowDialog.getLatestDialog()

            mediaCheck.recreate()
            advanceRobolectricLooper()

            assertNotSame(shownBeforeRecreation, ShadowDialog.getLatestDialog())
            assertEquals(oneFileDeleted, getAlertDialogText(checkDismissed = true))
        }

    @Test
    fun `result dialog is not duplicated on return`() =
        runTest {
            addUnusedMedia()
            val mediaCheck = startMediaCheck()
            mediaCheck.confirmDeletion()
            val result = ShadowDialog.getLatestDialog()

            mediaCheck.pause().stop()
            mediaCheck.start().resume()
            advanceRobolectricLooper()

            assertSame(result, ShadowDialog.getLatestDialog())
        }

    @Test
    fun `failed deletion shows an error instead of the result`() =
        runTest {
            failDeletion()

            startMediaCheck().confirmDeletion()

            assertEquals(failure.localizedMessage, getAlertDialogText(checkDismissed = true))
        }

    @Test
    fun `error is shown again after recreation`() =
        runTest {
            failDeletion()
            val mediaCheck = startMediaCheck()
            mediaCheck.confirmDeletion()
            val shownBeforeRecreation = ShadowDialog.getLatestDialog()

            mediaCheck.recreate()
            advanceRobolectricLooper()

            assertNotSame(shownBeforeRecreation, ShadowDialog.getLatestDialog())
            assertEquals(failure.localizedMessage, getAlertDialogText(checkDismissed = true))
        }

    @Test
    fun `dismissed error is not shown again`() =
        runTest {
            failDeletion()
            val mediaCheck = startMediaCheck()
            mediaCheck.confirmDeletion()
            clickAlertDialogButton { positiveButton }
            val dismissed = ShadowDialog.getLatestDialog()

            mediaCheck.recreate()
            advanceRobolectricLooper()

            assertSame(dismissed, ShadowDialog.getLatestDialog())
        }

    @Test
    fun `deletion failing during recreation shows an error`() =
        runTest {
            failDeletion()
            val mediaCheck = startMediaCheck()

            withQueuedCollectionAccess {
                mediaCheck.confirmDeletion()
                mediaCheck.recreate()
                advanceUntilIdle()
            }
            advanceRobolectricLooper()

            assertEquals(failure.localizedMessage, getAlertDialogText(checkDismissed = true))
        }

    @Test
    fun `failure in the background is shown on return`() =
        runTest {
            failDeletion()
            val mediaCheck = startMediaCheck()

            withQueuedCollectionAccess {
                mediaCheck.confirmDeletion()
                mediaCheck.pause().stop()
                advanceUntilIdle()
            }
            advanceRobolectricLooper()
            assertNull(getAlertDialogText(checkDismissed = true))

            mediaCheck.start().resume()
            advanceRobolectricLooper()

            assertEquals(failure.localizedMessage, getAlertDialogText(checkDismissed = true))
        }

    private suspend fun failDeletion() {
        backend.replaceWith {
            object : Backend() {
                override fun trashMediaFiles(fnames: Iterable<String>): Unit = throw failure
            }
        }
        addUnusedMedia()
    }

    private fun addUnusedMedia() = File(col.media.dir, "unused.png").writeText("not an image")

    private val oneFileDeleted: String
        get() = targetContext.resources.getQuantityString(CommonPlurals.delete_media_result_message, 1, 1)

    private fun ActivityController<SingleFragmentActivity>.confirmDeletion() {
        get().findViewById<Button>(R.id.delete_used_media_button).performClick()
        advanceRobolectricLooper()
        clickAlertDialogButton { positiveButton }
    }

    private suspend fun <T> TestScope.withQueuedCollectionAccess(block: suspend () -> T): T {
        val previousQueue = CollectionManager.setTestDispatcher(StandardTestDispatcher(testScheduler), useReentrantLock = false)
        try {
            return block()
        } finally {
            CollectionManager.setTestDispatcher(previousQueue)
        }
    }

    private fun startMediaCheck(): ActivityController<SingleFragmentActivity> =
        startActivityControllerNormallyOpenCollectionWithIntent(
            SingleFragmentActivity::class.java,
            MediaCheckFragment.getIntent(targetContext),
        )
}

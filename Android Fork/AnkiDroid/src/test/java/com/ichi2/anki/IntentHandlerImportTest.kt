// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Intent
import android.os.Looper
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.dialogs.DialogHandler
import com.ichi2.anki.dialogs.utils.message
import com.ichi2.utils.ImportResolution
import com.ichi2.utils.ImportResult
import com.ichi2.utils.ImportUtils
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Import handling tests for [IntentHandler]. */
@RunWith(AndroidJUnit4::class)
class IntentHandlerImportTest : RobolectricTest() {
    @Before
    fun prepareImport() {
        setIntroductionSlidesShown(true)
        mockkObject(ImportUtils)
    }

    @After
    fun resetImport() {
        unmockkObject(ImportUtils)
    }

    @Test
    fun `import failure stays visible until dismissed`() =
        runTest {
            val failure = ImportResult.Failure("The selected file is no longer available")
            every { ImportUtils.prepareImport(any(), any()) } returns failure

            val activity = launchImport()

            val dialog = assertIs<AlertDialog>(ShadowDialog.getLatestDialog())
            assertEquals(failure.humanReadableMessage, dialog.message)
            assertTrue(dialog.isShowing)
            assertFalse(activity.isFinishing)

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(activity.isFinishing)
        }

    @Test
    @Config(sdk = [24, Config.TARGET_SDK])
    fun `intent handler preserves the system font size`() =
        runTest {
            RuntimeEnvironment.setFontScale(1.5f)
            every { ImportUtils.prepareImport(any(), any()) } returns ImportResult.Failure("Unavailable file")

            val activity = launchImport()

            assertEquals(1.5f, activity.resources.configuration.fontScale)
        }

    @Test
    fun `successful import finishes the intent handler`() =
        runTest {
            every { ImportUtils.prepareImport(any(), any()) } returns
                ImportResult.Success(
                    ImportResolution.DeckPackage(ImportResolution.Source("content://import/deck.apkg".toUri(), "deck.apkg")),
                    "/cache/deck.apkg",
                )

            assertTrue(launchImport().isFinishing)
        }

    @Test
    fun `finishing before preparation completes suppresses import results`() =
        runTest {
            val source = ImportResolution.Source("content://import/deck.apkg".toUri(), "deck.apkg")
            val results =
                listOf(
                    ImportResult.Success(ImportResolution.DeckPackage(source), "/cache/deck.apkg"),
                    ImportResult.Success(ImportResolution.Text(source), "/cache/notes.csv"),
                    ImportResult.Failure("The selected file is no longer available"),
                )
            val dialogHandler = DialogHandler(mockk<AnkiActivity>())
            for (result in results) {
                val ioScheduler = TestCoroutineScheduler()
                ioDispatcher = StandardTestDispatcher(ioScheduler)
                every { ImportUtils.prepareImport(any(), any()) } returns result
                val activity = launchImport()
                verify(exactly = 0) { ImportUtils.prepareImport(activity, any()) }

                activity.finish()
                ioScheduler.runCurrent()

                verify(exactly = 1) { ImportUtils.prepareImport(activity, any()) }
                assertFalse(activity.isDestroyed, "Exercise the window before onDestroy cancels the import scope")
                assertNull(shadowOf(activity).nextStartedActivity, "A dismissed import must not launch an activity")
                assertNull(dialogHandler.popMessage(), "A dismissed import must not leave a pending confirmation")
                assertNull(ShadowDialog.getLatestDialog(), "A dismissed import must not show an error")
            }
        }

    @Test
    fun `package import failure from a text intent stays visible until dismissed`() =
        runTest {
            val failure = ImportResult.Failure("The selected file is no longer available")
            every { ImportUtils.prepareImport(any(), any()) } returns failure

            val activity = launchImport("text/plain")

            val dialog = assertIs<AlertDialog>(ShadowDialog.getLatestDialog())
            assertEquals(failure.humanReadableMessage, dialog.message)
            assertTrue(dialog.isShowing)
            assertFalse(activity.isFinishing)

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(activity.isFinishing)
        }

    @Test
    fun `import before introduction finishes the intent handler`() =
        runTest {
            setIntroductionSlidesShown(false)

            assertTrue(launchImport().isFinishing)
        }

    private fun launchImport(mimeType: String? = null): IntentHandler {
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType("content://import/deck.apkg".toUri(), mimeType)
        val controller = Robolectric.buildActivity(IntentHandler::class.java, intent)
        saveControllerForCleanup(controller)
        return controller.create().get()
    }
}

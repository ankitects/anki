// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.DialogInterface
import android.provider.Settings
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.backendError
import anki.sync.SyncAuth
import anki.sync.SyncCollectionResponse
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.common.time.MockTime
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.dialogs.SyncErrorDialog
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import kotlinx.coroutines.runBlocking
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendFactory
import net.ankiweb.rsdroid.exceptions.BackendSyncException
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog
import java.text.DateFormat
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class SyncTest : RobolectricTest() {
    private lateinit var syncFailure: BackendSyncException

    @Before
    fun setUpFailingSync() {
        runBlocking { CollectionManager.discardBackend() }
        BackendFactory.setOverride {
            object : Backend() {
                override fun syncCollection(
                    auth: SyncAuth,
                    syncMedia: Boolean,
                ): SyncCollectionResponse = throw syncFailure
            }
        }
    }

    @After
    fun resetBackendFactory() {
        BackendFactory.setOverride(null)
    }

    @Test
    fun `clock errors use sync recovery dialog and open date settings`() =
        deckPicker {
            TimeManager.resetWith(MockTime(1_600_000_000_000))
            val currentTime = DateFormat.getDateTimeInstance().format(TimeManager.time.currentDate)
            val exception = BackendSyncException(backendError { message = CollectionManager.TR.syncClockOff() })

            val dialog = failSync(exception)

            assertTrue(supportFragmentManager.fragments.any { it is SyncErrorDialog })
            assertEquals(
                getString(CommonString.sync_clock_off_with_current_time, exception.localizedMessage, currentTime),
                dialog.findViewById<TextView>(android.R.id.message)?.text?.toString(),
            )
            assertEquals(getString(CommonString.dialog_cancel), dialog.getButton(DialogInterface.BUTTON_NEGATIVE).text)
            assertEquals(getString(CommonString.open_settings), dialog.getButton(DialogInterface.BUTTON_POSITIVE).text)
            assertEquals(View.GONE, dialog.getButton(DialogInterface.BUTTON_NEUTRAL).visibility)
            assertEquals("test", Prefs.hkey)

            dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
            advanceRobolectricLooper()

            val intent = assertNotNull(shadowOf(this).nextStartedActivity)
            assertEquals(Settings.ACTION_DATE_SETTINGS, intent.action)
            assertFalse(dialog.isShowing)
        }

    @Test
    fun `cancelling a clock error does not open settings`() =
        deckPicker {
            val dialog = failSync(BackendSyncException(backendError { message = CollectionManager.TR.syncClockOff() }))

            dialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
            advanceRobolectricLooper()

            assertFalse(dialog.isShowing)
            assertNull(shadowOf(this).nextStartedActivity)
        }

    @Test
    fun `clock error remains visible when date settings are unavailable`() =
        deckPicker {
            val dialog = failSync(BackendSyncException(backendError { message = CollectionManager.TR.syncClockOff() }))
            val applicationShadow = shadowOf(application)
            applicationShadow.checkActivities(true)
            try {
                dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
                advanceRobolectricLooper()

                assertTrue(dialog.isShowing)
                assertNull(shadowOf(this).nextStartedActivity)

                dialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
                advanceRobolectricLooper()
                assertFalse(dialog.isShowing)
            } finally {
                applicationShadow.checkActivities(false)
            }
        }

    @Test
    fun `other sync errors keep their message and OK button`() =
        deckPicker {
            val message = "other sync error"
            val dialog = failSync(BackendSyncException(backendError { this.message = message }))

            assertEquals(message, dialog.findViewById<TextView>(android.R.id.message)?.text?.toString())
            assertEquals(getString(CommonString.dialog_ok), dialog.getButton(DialogInterface.BUTTON_POSITIVE).text)
            assertEquals(View.GONE, dialog.getButton(DialogInterface.BUTTON_NEGATIVE).visibility)
            assertEquals(View.GONE, dialog.getButton(DialogInterface.BUTTON_NEUTRAL).visibility)
            assertEquals("test", Prefs.hkey)

            dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
            advanceRobolectricLooper()

            assertFalse(dialog.isShowing)
            assertNull(shadowOf(this).nextStartedActivity)
        }

    @Test
    fun `collection too large uses database check dialog`() =
        deckPickerEx {
            val message = TR.syncUploadTooLarge("300.14 MB > 300.00 MB")

            val dialog =
                failSync(
                    BackendSyncException(
                        backendError {
                            this.message = message
                        },
                    ),
                )

            assertEquals(
                message,
                dialog.findViewById<TextView>(android.R.id.message)?.text?.toString(),
            )
            assertEquals(
                TR.sentenceCase.checkDatabase,
                dialog.positiveButton.text,
            )
            assertEquals(
                getString(CommonString.dialog_cancel),
                dialog.negativeButton.text,
            )

            dialog.positiveButton.performClick()
            advanceRobolectricLooper()

            assertFalse(dialog.isShowing)
            assertTrue(integrityCheckCalled)
        }

    @Test
    fun `collection too large error is detected from raw byte backend message`() =
        deckPicker {
            val message =
                "Some localized backend message: 123456789 > 314572800"

            val dialog =
                failSync(
                    BackendSyncException(
                        backendError {
                            this.message = message
                        },
                    ),
                )

            assertEquals(
                TR.sentenceCase.checkDatabase,
                dialog.positiveButton.text,
            )
        }

    @Test
    fun `collection too large error is detected from localized backend message`() =
        deckPicker {
            val message =
                "AnkiWebକୁ ପଠାଇବା ପାଇଁ ଆପଣଙ୍କ ସଂଗ୍ରହ ଫାଇଲ୍ ବହୁତ ବଡ଼ ଅଟେ। " +
                    "ଆପଣ ଯେକୌଣସି ଅବାଞ୍ଛିତ ଡେକ୍ ଅପସାରଣ କରି ଏହାର ଆକାର ହ୍ରାସ କରିପାରିବେ " +
                    "(ବୈକଳ୍ପିକ ଭାବରେ ସେଗୁଡ଼ିକୁ ପ୍ରଥମେ ରପ୍ତାନି କରନ୍ତୁ), " +
                    "ଏବଂ ତାପରେ ଫାଇଲ୍ ଆକାର ସଙ୍କୁଚିତ କରିବାକୁ ଡାଟାବେସ୍ ଯାଞ୍ଚ ବ୍ୟବହାର କରିପାରିବେ। " +
                    "(300.14 MB > 300.00 MB)"

            val dialog =
                failSync(
                    BackendSyncException(
                        backendError {
                            this.message = message
                        },
                    ),
                )

            assertEquals(
                message,
                dialog.findViewById<TextView>(android.R.id.message)?.text?.toString(),
            )
            assertEquals(
                TR.sentenceCase.checkDatabase,
                dialog.positiveButton.text,
            )
        }

    @Test
    fun `authentication errors still log out`() =
        deckPicker {
            Prefs.username = "test user"
            val message = "authentication failed"
            val dialog = failSync(BackendSyncException.BackendSyncAuthFailedException(backendError { this.message = message }))

            assertEquals("", Prefs.hkey)
            assertEquals("", Prefs.username)
            assertEquals(message, dialog.findViewById<TextView>(android.R.id.message)?.text?.toString())
        }

    private fun DeckPicker.failSync(exception: BackendSyncException): AlertDialog {
        syncFailure = exception
        Prefs.hkey = "test"
        Prefs.lastSyncTime = 0
        throwOnShowError = false

        handleNewSync(conflict = null, syncMedia = false)
        advanceRobolectricLooperUntil { Prefs.lastSyncTime != 0L }

        return assertNotNull(ShadowDialog.getLatestDialog() as? AlertDialog)
    }
}

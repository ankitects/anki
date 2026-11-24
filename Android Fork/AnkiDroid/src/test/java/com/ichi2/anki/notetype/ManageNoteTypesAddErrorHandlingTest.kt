// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.notetype

import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.backendError
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.backend.DatabaseCorruption
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.common.crashreporting.CrashReporter
import com.ichi2.anki.dialogs.DatabaseErrorDialog
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.addNotetypeLegacy
import com.ichi2.anki.throwOnShowError
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.launch
import net.ankiweb.rsdroid.BackendException.BackendDbException.BackendDbCorruptException
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class ManageNoteTypesAddErrorHandlingTest : RobolectricTest() {
    private lateinit var originalCrashReporter: CrashReporter
    private val crashReporter = mock<CrashReporter>()

    @Before
    fun captureCrashReports() {
        throwOnShowError = false
        originalCrashReporter = CrashReportService.getReporter()
        CrashReportService.setReporter(crashReporter)
    }

    @After
    fun restoreCrashReporter() {
        CrashReportService.setReporter(originalCrashReporter)
    }

    @Test
    fun `collection open failure while adding shows an error without escaping the coroutine`() =
        runTest {
            val (_, dialog) = openAddDialog()
            dialog.newNoteTypeBinding.notetypeNewName.setText("New note type")

            val previousFailure = CollectionManager.emulatedOpenFailure
            CollectionManager.emulatedOpenFailure = CollectionManager.CollectionOpenFailure.LOCKED
            try {
                submit(dialog)

                assertNotSame(dialog, latestDialog(), "Collection access failures must reach the error UI")
                assertTrue(getAlertDialogText(true).orEmpty().contains("Advanced settings"))
            } finally {
                CollectionManager.emulatedOpenFailure = previousFailure
            }
        }

    @Test
    fun `corruption during add opens database recovery`() =
        runTest {
            val (activity, dialog) = openAddDialog()
            dialog.newNoteTypeBinding.notetypeNewName.setText("New note type")

            val previousCorruption = DatabaseCorruption.isDetected
            DatabaseCorruption.isDetected = false
            mockkStatic(Collection::addNotetypeLegacy)
            try {
                every { any<Collection>().addNotetypeLegacy(any()) } throws BackendDbCorruptException(backendError {})

                submit(dialog)

                assertTrue(DatabaseCorruption.isDetected, "Corruption must be marked for recovery")
                assertTrue(
                    activity.supportFragmentManager.fragments.any { it is DatabaseErrorDialog },
                    "Corruption must open the database recovery dialog",
                )
            } finally {
                unmockkStatic(Collection::addNotetypeLegacy)
                DatabaseCorruption.isDetected = previousCorruption
            }
        }

    @Test
    fun `cloning an invalid legacy template shows validation without a crash report`() =
        runTest {
            val sourceName = "Invalid legacy note type"
            val cloneName = "Invalid template clone"
            // Legacy collections can contain templates that fail the current backend's validation.
            addStandardNoteType(sourceName, arrayOf("front", "back"), "", "")
            val (_, dialog) = openAddDialog()
            val selector = dialog.newNoteTypeBinding.notetypeNewType
            val sourcePosition =
                (0 until selector.count).single { selector.getItemAtPosition(it).toString().contains(sourceName) }
            selector.setSelection(sourcePosition)
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            dialog.newNoteTypeBinding.notetypeNewName.setText(cloneName)

            submit(dialog)

            val errorDialog = latestDialog()
            assertNotSame(dialog, errorDialog, "The invalid template must produce a validation dialog")
            assertNull(col.notetypes.byName(cloneName), "The invalid clone must not be created")
            errorDialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            verify(crashReporter, never()).sendExceptionReport(any<Throwable>(), anyOrNull(), anyOrNull(), any())
        }

    private fun openAddDialog(): Pair<ManageNotetypes, AlertDialog> {
        val activity = startRegularActivity<ManageNotetypes>()
        activity.lifecycleScope.launch { AddNewNotesType(activity).showAddNewNotetypeDialog() }
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        return activity to latestDialog()
    }

    private fun submit(dialog: AlertDialog) {
        val addButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        assertTrue(addButton.isEnabled, "The add action must be enabled before submitting")
        assertTrue(addButton.performClick())
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    }

    private fun latestDialog(): AlertDialog = ShadowDialog.getLatestDialog() as AlertDialog
}

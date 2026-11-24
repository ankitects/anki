// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.fragment.app.DialogFragment
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CrashReportData.HelpAction
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.common.crashreporting.CrashReporter
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.dialogs.DatabaseErrorDialog
import com.ichi2.anki.dialogs.DatabaseErrorDialog.DatabaseErrorDialogType
import com.ichi2.anki.dialogs.utils.message
import com.ichi2.anki.dialogs.utils.performPositiveClick
import com.ichi2.anki.dialogs.utils.title
import com.ichi2.anki.libanki.Note
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.anki.utils.ext.DIALOG_FRAGMENT_TAG
import com.ichi2.testutils.BackupManagerTestUtilities
import com.ichi2.utils.neutralButton
import com.ichi2.utils.positiveButton
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

@RunWith(AndroidJUnit4::class)
class StudyOptionsCorruptionRecoveryRegressionTest : RobolectricTest() {
    private lateinit var originalCrashReporter: CrashReporter
    private val crashReporter = mock<CrashReporter>()

    @Before
    fun allowTheDeckPickerToOpen() {
        originalCrashReporter = CrashReportService.getReporter()
        CrashReportService.setReporter(crashReporter)
        ensureCollectionLoadIsSynchronous()
        setIntroductionSlidesShown(true)
        BackupManagerTestUtilities.setupSpaceForBackup(targetContext)
        targetContext.sharedPrefs().edit { putBoolean("backupPromptDisabled", true) }
    }

    @After
    fun restoreCrashReporter() {
        CrashReportService.setReporter(originalCrashReporter)
    }

    @Test
    fun `Options on a load failure opens the recovery options in the Deck Picker`() {
        val studyOptions = startRegularActivity<StudyOptionsActivity>()
        studyOptions.showDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_LOAD_FAILED)

        studyOptions.awaitDialog<DatabaseErrorDialog>().performPositiveClick()

        val recoveryOptions = studyOptions.openedDeckPicker().awaitDialog<DatabaseErrorDialog>()
        assertEquals(getResourceString(CommonString.error_handling_title), recoveryOptions.title)
    }

    @Test
    fun `Check Database from an error runs in the Deck Picker`() {
        val studyOptions = startRegularActivity<StudyOptionsActivity>()
        runBlocking { HelpAction.OpenCheckDatabase.execute(studyOptions) }

        studyOptions.openedDeckPicker().awaitDialog<DatabaseErrorDialog>().performPositiveClick()

        assertEquals(TR.databaseCheckRebuilt(), (ShadowDialog.getLatestDialog() as AlertDialog).message)
    }

    @Test
    fun `missing notetype error offers a working database check without a crash report`() {
        val studyOptions = startRegularActivity<StudyOptionsActivity>()
        throwOnShowError = false

        runBlocking { studyOptions.runCatching { Note.fromNotetypeId(col, -1) } }
        advanceRobolectricLooper()

        val errorDialog = ShadowDialog.getLatestDialog() as AlertDialog
        assertEquals(with(studyOptions) { TR.sentenceCase.checkDatabase }, errorDialog.neutralButton?.text)
        errorDialog.neutralButton!!.performClick()
        advanceRobolectricLooper()

        assertFalse(errorDialog.isShowing)
        studyOptions.openedDeckPicker().awaitDialog<DatabaseErrorDialog>().performPositiveClick()
        assertEquals(TR.databaseCheckRebuilt(), (ShadowDialog.getLatestDialog() as AlertDialog).message)
        verify(crashReporter, never()).sendExceptionReport(any<Throwable>(), anyOrNull(), anyOrNull(), any())
    }

    @Test
    fun `dismissing a missing notetype error does not send a crash report`() {
        val studyOptions = startRegularActivity<StudyOptionsActivity>()
        throwOnShowError = false

        runBlocking { studyOptions.runCatching { Note.fromNotetypeId(col, -1) } }
        advanceRobolectricLooper()

        val errorDialog = ShadowDialog.getLatestDialog() as AlertDialog
        errorDialog.positiveButton.performClick()
        advanceRobolectricLooper()

        assertFalse(errorDialog.isShowing)
        verify(crashReporter, never()).sendExceptionReport(any<Throwable>(), anyOrNull(), anyOrNull(), any())
    }

    private fun AnkiActivity.openedDeckPicker(): DeckPicker {
        val intent = assertNotNull(shadowOf(this).nextStartedActivity, "the Deck Picker was not opened")
        assertEquals(DeckPicker::class.java.name, intent.component?.className)
        return startActivityNormallyOpenCollectionWithIntent(DeckPicker::class.java, intent)
    }

    private inline fun <reified T : DialogFragment> AnkiActivity.awaitDialog(): AlertDialog {
        advanceRobolectricLooperUntil { supportFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG) is T }
        return (supportFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG) as T).requireDialog() as AlertDialog
    }
}

// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.app.NotificationManager
import android.content.ContentProvider
import android.content.Intent
import android.database.MatrixCursor
import android.net.Uri
import android.os.Looper
import android.provider.OpenableColumns
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.lifecycle.ViewModelProvider
import com.ichi2.anki.deckpicker.DeckPickerViewModel
import com.ichi2.anki.deckpicker.DeckPickerViewModel.AnkiDroidEnvironment
import com.ichi2.anki.dialogs.AsyncDialogFragment
import com.ichi2.anki.dialogs.DatabaseErrorDialog
import com.ichi2.anki.dialogs.DialogHandler
import com.ichi2.anki.dialogs.ImportDialog
import com.ichi2.anki.dialogs.ImportViewModel
import com.ichi2.anki.testutils.SingleViewModelFactory
import com.ichi2.anki.utils.ext.DIALOG_FRAGMENT_TAG
import com.ichi2.testutils.BackupManagerTestUtilities
import com.ichi2.testutils.EmptyAnkiActivity
import com.ichi2.utils.ImportResolution
import com.ichi2.utils.ImportResult
import com.ichi2.utils.ImportUtils
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers
import org.junit.After
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doCallRealMethod
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowContentResolver
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(ParameterizedRobolectricTestRunner::class)
class DeckPickerImportTest : RobolectricTest() {
    @ParameterizedRobolectricTestRunner.Parameter
    @JvmField
    var qualifiers: String? = null

    companion object {
        @ParameterizedRobolectricTestRunner.Parameters
        @JvmStatic
        fun configurations(): Collection<String> = listOf("normal", "xlarge")
    }

    @Before
    fun configureScreen() {
        RuntimeEnvironment.setQualifiers(qualifiers)
        setIntroductionSlidesShown(true)
        InitialActivity.setUpgradedToLatestVersion(getPreferences())
        getPreferences().edit { putBoolean("backupPromptDisabled", true) }
        BackupManagerTestUtilities.setupSpaceForBackup(targetContext)
    }

    @After
    fun resetBackupSpace() {
        BackupManagerTestUtilities.reset()
    }

    @Test
    fun `selected package displays one confirmation with its cached path`() =
        runTest {
            val activity = startActivityNormallyOpenCollectionWithIntent(DeckPickerImport::class.java, Intent())
            val fileName = "deck name.apkg"
            val uri = "content://import/document/123".toUri()
            // FileProvider's Android path handling depends on the host filesystem.
            val provider = mock<ContentProvider>()
            whenever(provider.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)).thenAnswer {
                MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME)).apply { addRow(arrayOf(fileName)) }
            }
            ShadowContentResolver.registerProviderInternal("import", provider)
            shadowOf(activity.contentResolver).registerInputStreamSupplier(uri) { "package contents".byteInputStream() }
            val cachedPath = File(activity.cacheDir, Uri.encode(fileName)).absolutePath
            try {
                activity.onSelectedFileToImport(Intent(Intent.ACTION_VIEW, uri))
                advanceRobolectricLooper()
                activity.assertSingleImportConfirmation(ImportDialog.Type.DIALOG_IMPORT_ADD_CONFIRM, cachedPath)
            } finally {
                File(cachedPath).delete()
            }
        }

    @Test
    fun `confirmed import waits for resume without launching twice`() =
        runTest {
            val controller = startActivityControllerNormallyOpenCollectionWithIntent(DeckPickerImport::class.java, Intent())
            val activity = controller.get()
            controller.pause().stop()
            ViewModelProvider(activity)[ImportViewModel::class.java].triggerImportAdd("/cache/deck.apkg")
            advanceRobolectricLooper()
            assertNull(shadowOf(activity).nextStartedActivity)

            controller.start().resume().visible()
            advanceRobolectricLooper()
            val intent = assertNotNull(shadowOf(activity).nextStartedActivity)
            assertEquals(SingleFragmentActivity::class.java.name, intent.component?.className)
            controller
                .pause()
                .stop()
                .start()
                .resume()
                .visible()
            advanceRobolectricLooper()
            assertNull(shadowOf(activity).nextStartedActivity)
        }

    @Test
    fun `stopped picker retains Add confirmation for the next picker`() {
        assertRetainedConfirmation(ImportDialog.Type.DIALOG_IMPORT_ADD_CONFIRM, "/cache/deck%20name.apkg")
    }

    @Test
    fun `stopped picker retains Replace confirmation for the next picker`() {
        assertRetainedConfirmation(ImportDialog.Type.DIALOG_IMPORT_REPLACE_CONFIRM, "/cache/collection%20name.colpkg")
    }

    private fun assertRetainedConfirmation(
        type: ImportDialog.Type,
        path: String,
    ) {
        deferConfirmationFromStoppedPicker(type, path)

        val controller = startActivityControllerNormallyOpenCollectionWithIntent(DeckPickerImport::class.java, Intent())
        controller.get().assertSingleImportConfirmation(type, path)
        assertTrue(notifications.allNotifications.isEmpty())

        controller.assertDismissedConfirmationIsNotReplayed()
    }

    private val notifications
        get() = shadowOf(targetContext.getSystemService<NotificationManager>()!!)

    private fun deferConfirmationFromStoppedPicker(
        type: ImportDialog.Type,
        path: String,
    ) {
        val controller = startActivityControllerNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent())
        val activity = controller.get()
        controller.pause().stop()

        activity.showImportDialog(type, path)
        advanceRobolectricLooper()
        assertNull(activity.supportFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG))
        val notification = notifications.allNotifications.single()
        assertEquals(DeckPicker::class.java.name, shadowOf(notification.contentIntent).savedIntent.component?.className)
        controller.destroy()
    }

    private fun ActivityController<DeckPickerImport>.assertDismissedConfirmationIsNotReplayed() {
        val activity = get()
        (activity.supportFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG) as ImportDialog).dismiss()
        advanceRobolectricLooper()
        pause()
            .stop()
            .start()
            .resume()
            .visible()
        advanceRobolectricLooper()
        assertEquals(1, activity.importConfirmations.size, "Resuming again must not replay the confirmation")
        assertNull(activity.supportFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG))
    }

    @Test
    fun `pending Add confirmation waits for DeckPicker`() {
        assertConfirmationWaitsForDeckPicker(ImportDialog.Type.DIALOG_IMPORT_ADD_CONFIRM, "/cache/deck.apkg")
    }

    @Test
    fun `pending Replace confirmation waits for DeckPicker`() {
        assertConfirmationWaitsForDeckPicker(ImportDialog.Type.DIALOG_IMPORT_REPLACE_CONFIRM, "/cache/collection.colpkg")
    }

    private fun assertConfirmationWaitsForDeckPicker(
        type: ImportDialog.Type,
        path: String,
    ) {
        DialogHandler.storeMessage(ImportDialog.newInstance(type, path).dialogHandlerMessage.toMessage())
        val controller = startActivityControllerNormallyOpenCollectionWithIntent(EmptyAnkiActivity::class.java, Intent())
        val activity = controller.get()
        assertNull(activity.supportFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG))

        controller
            .pause()
            .stop()
            .start()
            .resume()
            .visible()
        advanceRobolectricLooper()
        assertNull(activity.supportFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG))
        controller.pause().stop()

        val picker = startActivityNormallyOpenCollectionWithIntent(DeckPickerImport::class.java, Intent())
        picker.assertSingleImportConfirmation(type, path)
    }

    @Test
    fun `other pending dialogs can still resume on another activity`() {
        DialogHandler.storeMessage(CollectionLoadingErrorDialog().toMessage())

        val activity = startActivityNormallyOpenCollectionWithIntent(EmptyAnkiActivity::class.java, Intent())

        val fragment = assertNotNull(activity.supportFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG) as? DatabaseErrorDialog)
        assertTrue(assertNotNull(fragment.dialog as? AlertDialog).isShowing)
        assertNull(activity.dialogHandler.popMessage())
    }

    @Test
    fun `finishing picker before text preparation completes suppresses navigation`() =
        runTest {
            val activity = startActivityNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent())
            advanceRobolectricLooperUntil { activity.viewModel.loadDeckCounts?.isCompleted == true }
            val fileName = "queued-notes.csv"
            val uri = "content://import/document/123".toUri()
            val provider = mock<ContentProvider>()
            whenever(provider.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)).thenAnswer {
                MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME)).apply { addRow(arrayOf(fileName)) }
            }
            ShadowContentResolver.registerProviderInternal("import", provider)
            val contents = "front,back\nhello,world\n"
            var sourceOpened = false
            shadowOf(activity.contentResolver).registerInputStreamSupplier(uri) {
                sourceOpened = true
                contents.byteInputStream()
            }
            val ioScheduler = TestCoroutineScheduler()
            ioDispatcher = StandardTestDispatcher(ioScheduler)
            val cachedFile = File(activity.cacheDir, fileName)
            try {
                val job = activity.onSelectedFileToImport(Intent(Intent.ACTION_VIEW, uri))
                assertFalse(sourceOpened, "Preparation must still be queued")

                activity.finish()
                ioScheduler.runCurrent()
                shadowOf(Looper.getMainLooper()).idle()

                assertTrue(activity.isFinishing)
                assertFalse(activity.isDestroyed, "Exercise the window before destruction cancels the lifecycle scope")
                assertTrue(job.isCompleted)
                assertTrue(sourceOpened)
                assertEquals(contents, cachedFile.readText())
                assertNull(shadowOf(activity).nextStartedActivity, "A dismissed picker must not reopen the import UI")
            } finally {
                cachedFile.delete()
            }
        }

    @Test
    fun `prepared deck waits for resume and selects Add from its resolved type`() {
        assertPreparedConfirmation(
            ImportResult.Success(
                ImportResolution.DeckPackage(ImportResolution.Source("content://unavailable/deck.apkg".toUri(), "deck.apkg")),
                "/cache/cached.colpkg",
            ),
            ImportDialog.Type.DIALOG_IMPORT_ADD_CONFIRM,
        )
    }

    @Test
    fun `prepared collection waits for resume and selects Replace from its resolved type`() {
        assertPreparedConfirmation(
            ImportResult.Success(
                ImportResolution.CollectionPackage(ImportResolution.Source("content://unavailable/backup.colpkg".toUri(), "backup.colpkg")),
                "/cache/cached.apkg",
            ),
            ImportDialog.Type.DIALOG_IMPORT_REPLACE_CONFIRM,
        )
    }

    private fun assertPreparedConfirmation(
        result: ImportResult.Success,
        type: ImportDialog.Type,
    ) {
        val controller = startActivityControllerNormallyOpenCollectionWithIntent(DeckPickerImport::class.java, Intent())
        val activity = controller.get()

        controller.pause().stop()
        activity.openImport(result)
        advanceRobolectricLooper()
        assertTrue(activity.importConfirmations.isEmpty(), "A stopped activity must wait for onResume to dispatch the stored confirmation")

        controller
            .start()
            .resume()
            .visible()
        advanceRobolectricLooper()

        activity.assertSingleImportConfirmation(type, result.path)
    }

    private fun DeckPickerImport.assertSingleImportConfirmation(
        type: ImportDialog.Type,
        path: String,
    ) {
        assertEquals(1, importConfirmations.size, "One import must display exactly one confirmation")
        val (thread, fragment) = importConfirmations.single()
        assertEquals(Looper.getMainLooper().thread, thread)
        assertEquals(type.code, fragment.requireArguments().getInt(ImportDialog.ARG_DIALOG_TYPE))
        assertEquals(path, fragment.requireArguments().getString(ImportDialog.ARG_PACKAGE_PATH))
        assertTrue(assertNotNull(fragment.dialog as? AlertDialog).isShowing)
    }

    @Test
    fun `import confirmation survives delayed startup`() {
        val viewModel = spy(DeckPickerViewModel())
        lateinit var startupEnvironment: AnkiDroidEnvironment
        doAnswer { invocation ->
            startupEnvironment = invocation.getArgument(0)
            null
        }.whenever(viewModel).handleStartup(any())

        DialogHandler.storeMessage(ImportUtils.CollectionImportAdd("deck.apkg").toMessage())
        val controller = Robolectric.buildActivity(DeckPicker::class.java)
        saveControllerForCleanup(controller)
        val activity = controller.get()
        ViewModelProvider(activity, SingleViewModelFactory.create(viewModel))[DeckPickerViewModel::class.java]
        controller
            .create()
            .start()
            .resume()
            .visible()
        advanceRobolectricLooper()
        activity.assertImportConfirmationVisible()

        // Opening the collection at startup can finish after the pending import dialog is shown.
        doCallRealMethod().whenever(viewModel).handleStartup(any())
        viewModel.handleStartup(startupEnvironment)
        viewModel.awaitStartup()
        advanceRobolectricLooper()

        activity.assertImportConfirmationVisible()
    }

    @Test
    fun `import confirmation survives startup`() {
        DialogHandler.storeMessage(ImportUtils.CollectionImportAdd("deck.apkg").toMessage())

        deckPicker {
            assertImportConfirmationVisible()
        }
    }

    @Test
    fun `import confirmation survives returning from file picker`() {
        val controller = startActivityControllerNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent())
        controller.pause().stop()
        DialogHandler.storeMessage(ImportUtils.CollectionImportAdd("deck.apkg").toMessage())

        controller.start().resume().visible()
        advanceRobolectricLooper()

        controller.get().assertImportConfirmationVisible()
    }

    private fun DeckPicker.assertImportConfirmationVisible() {
        val fragment = assertNotNull(supportFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG) as? ImportDialog)
        assertTrue(assertNotNull(fragment.dialog as? AlertDialog).isShowing)
    }

    @Test
    fun importAddShowsImportDialog() {
        val deckPicker = super.startActivityNormallyOpenCollectionWithIntent(DeckPickerImport::class.java, Intent())

        deckPicker.showImportDialog(ImportDialog.Type.DIALOG_IMPORT_ADD_CONFIRM, "")

        assertThat(deckPicker.getAsyncDialogFragmentClass(), Matchers.typeCompatibleWith(ImportDialog::class.java))
    }

    @Test
    fun replaceShowsImportDialog() {
        val deckPicker = super.startActivityNormallyOpenCollectionWithIntent(DeckPickerImport::class.java, Intent())

        deckPicker.showImportDialog(ImportDialog.Type.DIALOG_IMPORT_REPLACE_CONFIRM, "")

        assertThat(deckPicker.getAsyncDialogFragmentClass(), Matchers.typeCompatibleWith(ImportDialog::class.java))
    }

    private class DeckPickerImport : DeckPicker() {
        private var dialogFragment: AsyncDialogFragment? = null
        val importConfirmations = mutableListOf<Pair<Thread, ImportDialog>>()

        fun getAsyncDialogFragmentClass(): Class<*> {
            if (dialogFragment == null) {
                fail("No async fragment shown")
            }
            return dialogFragment!!.javaClass
        }

        override fun showAsyncDialogFragment(newFragment: AsyncDialogFragment) {
            dialogFragment = newFragment
            if (newFragment is ImportDialog) {
                importConfirmations.add(Thread.currentThread() to newFragment)
            }
            super.showAsyncDialogFragment(newFragment)
        }
    }
}

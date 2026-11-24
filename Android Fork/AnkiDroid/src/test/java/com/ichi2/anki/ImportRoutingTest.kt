// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.app.Activity
import android.content.ClipData
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ProviderInfo
import android.database.MatrixCursor
import android.net.Uri
import android.os.Build
import android.os.Looper
import android.os.Message
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.view.ContentInfo
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.ContentInfoCompat
import androidx.core.view.ViewCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import anki.generic.Empty
import anki.import_export.ImportAnkiPackageOptions
import anki.import_export.exportAnkiPackageOptions
import anki.import_export.exportLimit
import anki.import_export.importAnkiPackageRequest
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.anki.dialogs.DialogHandler
import com.ichi2.anki.dialogs.DialogHandlerMessage
import com.ichi2.anki.dialogs.ImportDialog
import com.ichi2.anki.pages.CsvImporter
import com.ichi2.utils.ImportResolution
import com.ichi2.utils.ImportResult
import com.ichi2.utils.ImportUtils
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import net.ankiweb.rsdroid.BackendException
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowContentResolver
import org.robolectric.shadows.ShadowDialog
import java.io.File
import java.lang.ref.WeakReference
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class ImportRoutingTest : RobolectricTest() {
    private var filenameQueries = 0

    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    @Before
    fun setCollectionPath() {
        // Robolectric includes the test name in this path; avoid an extra subdirectory for Windows SQLite.
        val collectionDirectory = assertNotNull(targetContext.getExternalFilesDir(null))
        getPreferences().edit {
            putString(CollectionHelper.PREF_COLLECTION_PATH, collectionDirectory.absolutePath)
        }
    }

    @Test
    fun `package preparation resolves the filename only once`() =
        runTest {
            val source = exportPackage()

            assertIs<ImportResult.Success>(ImportUtils.prepareImport(activity(), provideFile(source, "application/apkg")))

            assertEquals(1, filenameQueries)
        }

    @Test
    fun `text intent with missing filename shows an error`() =
        runTest {
            setIntroductionSlidesShown(true)
            val source = tempFolder.newFile("notes.csv")
            val controller = Robolectric.buildActivity(IntentHandler::class.java, provideFile(source, "text/plain", displayName = null))
            saveControllerForCleanup(controller)
            val activity = controller.create().get()

            val dialog = assertIs<AlertDialog>(ShadowDialog.getLatestDialog())
            assertTrue(dialog.isShowing)
            assertFalse(activity.isFinishing)
        }

    @Test
    fun `package import ignores incorrect text MIME metadata`() =
        runTest {
            val activity = activity()
            for (legacy in listOf(false, true)) {
                val source = exportPackage(legacy)
                for ((providerMime, intentMime) in listOf(
                    "text/plain" to "text/plain",
                    "text/csv" to "text/csv",
                    "application/octet-stream" to null,
                    "text/plain" to "application/apkg",
                    "application/apkg" to "text/plain",
                )) {
                    val intent = provideFile(source, providerMime, intentMimeType = intentMime)

                    activity.openImport(assertIs<ImportResult.Success>(ImportUtils.prepareImport(activity, intent)))

                    assertPackageImport(source)
                    assertNull(shadowOf(activity).nextStartedActivity, "Package import should not launch the CSV page")
                }
            }
        }

    @Test
    fun `specific package MIME imports a deck with a text or missing filename`() =
        runTest {
            val source = exportPackage()
            val activity = activity()
            for (name in listOf("notes.csv", "download", null)) {
                for ((providerMime, intentMime) in listOf("text/plain" to "application/apkg", "application/apkg" to "text/plain")) {
                    val intent = provideFile(source, providerMime, displayName = name, intentMimeType = intentMime)
                    val result = assertIs<ImportResult.Success>(ImportUtils.prepareImport(activity, intent))

                    assertIs<ImportResolution.DeckPackage>(result.file)
                    activity.openImport(result)

                    assertPackageImport(source)
                    assertNull(shadowOf(activity).nextStartedActivity)
                }
            }
        }

    @Test
    fun `specific collection MIME keeps replacement confirmation with a text or missing filename`() =
        runTest {
            addBasicNote()
            val backups = tempFolder.newFolder("backups")
            assertTrue(col.createBackup(backups.path, force = true, waitForCompletion = true))
            val source = assertNotNull(backups.listFiles()).single()
            for (name in listOf("notes.csv", "download", null)) {
                for ((providerMime, intentMime) in listOf("text/plain" to "application/colpkg", "application/colpkg" to "text/plain")) {
                    val activity = launchImport(provideFile(source, providerMime, displayName = name, intentMimeType = intentMime))

                    val message = packageConfirmation()
                    assertIs<ImportUtils.CollectionImportReplace>(DialogHandlerMessage.fromMessage(message))
                    assertContentEquals(source.readBytes(), File(assertNotNull(message.data.getString("importPath"))).readBytes())
                    assertEquals(DeckPicker::class.java.name, shadowOf(activity).nextStartedActivity.component?.className)
                }
            }
        }

    @Test
    fun `text mislabeled as a package is rejected by the package importer`() =
        runTest {
            val source = tempFolder.newFile("notes.apkg").apply { writeText("front,back\nhello,world\n") }
            val activity = activity()
            val result = assertIs<ImportResult.Success>(ImportUtils.prepareImport(activity, provideFile(source, "text/plain")))

            assertIs<ImportResolution.DeckPackage>(result.file)
            activity.openImport(result)
            val message = packageConfirmation()
            assertIs<ImportUtils.CollectionImportAdd>(DialogHandlerMessage.fromMessage(message))
            assertFailsWith<BackendException> {
                col.importAnkiPackage(assertNotNull(message.data.getString("importPath")), ImportAnkiPackageOptions.getDefaultInstance())
            }
            assertNull(shadowOf(activity).nextStartedActivity)
        }

    @Test
    fun `text picker still opens the CSV importer for text files`() =
        runTest {
            setIntroductionSlidesShown(true)
            val source = tempFolder.newFile("notes.csv").apply { writeText("front\tback") }
            val activity = startActivityNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent())

            selectTextFile(activity, source)

            val intent = assertNotNull(shadowOf(activity).nextStartedActivity)
            assertEquals(CsvImporter::class.java.name, intent.getStringExtra(SingleFragmentActivity.EXTRA_FRAGMENT_NAME))
            assertContentEquals(source.readBytes(), File(targetContext.cacheDir, source.name).readBytes())
        }

    @Test
    fun `package selected in the text picker keeps its confirmation on the current deck picker`() =
        runTest {
            setIntroductionSlidesShown(true)
            val source = exportPackage()
            val activity = startActivityNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent())

            selectTextFile(activity, source)
            shadowOf(Looper.getMainLooper()).idle()

            assertNull(shadowOf(activity).nextStartedActivity, "Recreating DeckPicker discards its pending import dialog")
            assertIs<ImportDialog>(activity.supportFragmentManager.fragments.last())
        }

    @Test
    fun `package picker ignores incorrect provider MIME`() =
        runTest {
            setIntroductionSlidesShown(true)
            val source = exportPackage().copyTo(File(tempFolder.root, "deck with spaces.apkg"))
            val activity = startActivityNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent())
            activity.getApkgFileImportResultLauncher().launch(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*"))
            val picker = assertNotNull(shadowOf(activity).nextStartedActivity)

            shadowOf(activity).receiveResult(picker, Activity.RESULT_OK, provideFile(source, "text/plain"))
            shadowOf(Looper.getMainLooper()).idle()

            assertNull(shadowOf(activity).nextStartedActivity)
            assertCachedPackage(activity, source)
        }

    @Test
    fun `dropped package ignores incorrect provider MIME`() =
        runTest {
            setIntroductionSlidesShown(true)
            val source = exportPackage()
            val activity = startActivityNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent())
            val uri = assertNotNull(provideFile(source, "text/plain").data)
            val payload =
                ContentInfoCompat
                    .Builder(
                        ClipData.newUri(activity.contentResolver, "deck", uri),
                        ContentInfoCompat.SOURCE_DRAG_AND_DROP,
                    ).build()

            ViewCompat.performReceiveContent(activity.findViewById(R.id.pull_to_sync_wrapper), payload)
            shadowOf(Looper.getMainLooper()).idle()

            assertNull(shadowOf(activity).nextStartedActivity)
            assertIs<ImportDialog>(activity.supportFragmentManager.fragments.last())
        }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    fun `dropped package retains its payload until preparation completes`() =
        runTest {
            setIntroductionSlidesShown(true)
            val source = exportPackage().copyTo(File(tempFolder.root, "deck with spaces.apkg"))
            val activity = startActivityNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent())
            val uri = assertNotNull(provideFile(source, "text/plain").data)
            val ioScheduler = TestCoroutineScheduler()
            ioDispatcher = StandardTestDispatcher(ioScheduler)

            val payload = dropFile(activity, uri)
            shadowOf(activity.contentResolver).registerInputStreamSupplier(uri) {
                repeat(3) { System.gc() }
                assertNotNull(payload.get(), "The payload must remain alive when the source is opened")
                source.inputStream()
            }
            repeat(3) { System.gc() }
            try {
                assertEquals(0, filenameQueries, "Preparation must still be queued")
                assertNotNull(payload.get(), "Keep the platform payload alive while its URI permissions are needed")
            } finally {
                ioScheduler.runCurrent()
            }
            shadowOf(Looper.getMainLooper()).idle()

            assertCachedPackage(activity, source)
            assertPayloadReleased(payload)
        }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    fun `cancelled dropped import releases its payload`() =
        runTest {
            setIntroductionSlidesShown(true)
            val source = exportPackage()
            val controller = startActivityControllerNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent())
            val activity = controller.get()
            val uri = assertNotNull(provideFile(source, "text/plain").data)
            val ioScheduler = TestCoroutineScheduler()
            ioDispatcher = StandardTestDispatcher(ioScheduler)
            val payload = dropFile(activity, uri)

            activity.finish()
            controller.pause().stop().destroy()
            ioScheduler.runCurrent()

            assertEquals(0, filenameQueries, "A cancelled import must not start preparation")
            assertPayloadReleased(payload)
        }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun dropFile(
        activity: DeckPicker,
        uri: Uri,
    ): WeakReference<ContentInfo> {
        val payload =
            ContentInfoCompat
                .Builder(ClipData.newRawUri("deck", uri), ContentInfoCompat.SOURCE_DRAG_AND_DROP)
                .build()
        ViewCompat.performReceiveContent(activity.findViewById(R.id.pull_to_sync_wrapper), payload)
        return WeakReference(assertNotNull(payload.toContentInfo()))
    }

    private fun assertPayloadReleased(payload: WeakReference<ContentInfo>) {
        repeat(10) {
            System.gc()
            if (payload.get() == null) return
        }
        assertNull(payload.get(), "Release the payload when its import finishes or is cancelled")
    }

    @Test
    fun `shared package resolves its clip URI despite a text MIME type`() =
        runTest {
            val source = exportPackage()
            val uri = assertNotNull(provideFile(source, "text/plain").data)
            val intent =
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newRawUri("deck", uri)
                }

            val activity = launchImport(intent)

            assertPackageImport(source)
            assertEquals(DeckPicker::class.java.name, shadowOf(activity).nextStartedActivity.component?.className)
        }

    @Test
    fun `text picker accepts CSV with generic MIME metadata`() =
        runTest {
            setIntroductionSlidesShown(true)
            val source = tempFolder.newFile("notes.csv").apply { writeText("front\tback") }
            val activity = startActivityNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent())

            selectTextFile(activity, source, "application/octet-stream")

            val intent = assertNotNull(shadowOf(activity).nextStartedActivity)
            assertEquals(CsvImporter::class.java.name, intent.getStringExtra(SingleFragmentActivity.EXTRA_FRAGMENT_NAME))
        }

    @Test
    fun `opening a package with a text intent uses package import`() =
        runTest {
            val source = exportPackage()
            val activity = launchTextImport(source)

            assertPackageImport(source)
            assertEquals(DeckPicker::class.java.name, shadowOf(activity).nextStartedActivity.component?.className)
        }

    @Test
    fun `package selected as text preserves encoded paths through the web importer`() =
        runTest {
            val source = exportPackage().copyTo(File(tempFolder.root, "名前 with spaces.apkg"))
            launchTextImport(source)
            val message = packageConfirmation()
            val cachedPath = assertNotNull(message.data.getString("importPath"))
            val request =
                importAnkiPackageRequest {
                    // The web page decodes its route before posting the import request.
                    packagePath = Uri.decode(cachedPath)
                    options = ImportAnkiPackageOptions.getDefaultInstance()
                }

            importAnkiPackageUndoable(request.toByteArray())

            assertEquals(1, col.noteCount())
        }

    @Test
    fun `collection packages selected as text still require replacement confirmation`() =
        runTest {
            addBasicNote()
            val backups = tempFolder.newFolder("backups")
            assertTrue(col.createBackup(backups.path, force = true, waitForCompletion = true))
            val backup = assertNotNull(backups.listFiles()).single()
            for (name in listOf("backup.colpkg", "collection.apkg")) {
                val source = backup.copyTo(File(tempFolder.root, name))

                val activity = launchTextImport(source)

                val message = packageConfirmation()
                assertIs<ImportUtils.CollectionImportReplace>(DialogHandlerMessage.fromMessage(message))
                assertContentEquals(source.readBytes(), File(assertNotNull(message.data.getString("importPath"))).readBytes())
                assertEquals(DeckPicker::class.java.name, shadowOf(activity).nextStartedActivity.component?.className)
            }
        }

    @Test
    fun `text intents still open the CSV importer for text files`() =
        runTest {
            val source = tempFolder.newFile("notes.csv").apply { writeText("front\tback") }
            val activity = launchTextImport(source)

            val intent = assertNotNull(shadowOf(activity).nextStartedActivity)
            assertEquals(CsvImporter::class.java.name, intent.getStringExtra(SingleFragmentActivity.EXTRA_FRAGMENT_NAME))
            assertContentEquals(source.readBytes(), File(targetContext.cacheDir, source.name).readBytes())
        }

    @Test
    fun `provider text MIME opens CSV without a trailing deck picker launch`() =
        runTest {
            val source = tempFolder.newFile("notes").apply { writeText("front\tback") }
            val intent = provideFile(source, "text/plain", intentMimeType = "application/octet-stream")

            val activity = launchImport(intent)

            val launched = assertNotNull(shadowOf(activity).nextStartedActivity)
            assertEquals(CsvImporter::class.java.name, launched.getStringExtra(SingleFragmentActivity.EXTRA_FRAGMENT_NAME))
            assertTrue(activity.isFinishing)
        }

    @Test
    fun `unavailable text file shows preparation failure`() =
        runTest {
            val source = tempFolder.newFile("notes.csv")
            val intent = provideFile(source, "text/plain")
            source.delete()

            val activity = launchImport(intent)

            val dialog = assertIs<AlertDialog>(ShadowDialog.getLatestDialog())
            assertTrue(dialog.isShowing)
            assertFalse(activity.isFinishing)
            assertNull(shadowOf(activity).nextStartedActivity)
        }

    private fun selectTextFile(
        activity: DeckPicker,
        source: File,
        mimeType: String = "text/plain",
    ) {
        activity.getCsvFileImportResultLauncher().launch(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("text/plain"))
        val picker = assertNotNull(shadowOf(activity).nextStartedActivity)
        shadowOf(activity).receiveResult(picker, Activity.RESULT_OK, provideFile(source, mimeType))
    }

    private fun launchTextImport(source: File): IntentHandler = launchImport(provideFile(source, "text/plain"))

    private fun launchImport(intent: Intent): IntentHandler {
        setIntroductionSlidesShown(true)
        val controller = Robolectric.buildActivity(IntentHandler::class.java, intent)
        saveControllerForCleanup(controller)
        return controller.create().get()
    }

    private fun assertPackageImport(source: File) {
        val message = packageConfirmation()
        assertIs<ImportUtils.CollectionImportAdd>(DialogHandlerMessage.fromMessage(message))
        val cached = File(assertNotNull(message.data.getString("importPath")))
        assertContentEquals(source.readBytes(), cached.readBytes())
        // Exercise the real backend with the exact bytes copied by the import flow.
        col.importAnkiPackage(cached.path, ImportAnkiPackageOptions.getDefaultInstance())
        assertEquals(1, col.noteCount())
    }

    private fun assertCachedPackage(
        activity: DeckPicker,
        source: File,
    ) {
        val dialog = assertIs<ImportDialog>(activity.supportFragmentManager.fragments.last())
        val cachedPath = assertNotNull(dialog.requireArguments().getString(ImportDialog.ARG_PACKAGE_PATH))
        assertContentEquals(source.readBytes(), File(cachedPath).readBytes())
    }

    private fun packageConfirmation(): Message {
        val controller = Robolectric.buildActivity(DeckPicker::class.java)
        saveControllerForCleanup(controller)
        return assertNotNull(DialogHandler(controller.get()).popMessage(), "Expected package confirmation")
    }

    private fun activity(): Activity {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        saveControllerForCleanup(controller)
        return controller.get()
    }

    private fun exportPackage(legacy: Boolean = false): File {
        if (col.noteCount() == 0) addBasicNote("Issue 21430", "round-trip")
        return tempFolder.newFile(if (legacy) "legacy.APKG" else "deck.apkg").also {
            col.exportAnkiPackage(
                it.path,
                exportAnkiPackageOptions {
                    withScheduling = true
                    withDeckConfigs = true
                    withMedia = true
                    this.legacy = legacy
                },
                exportLimit { wholeCollection = Empty.getDefaultInstance() },
            )
        }
    }

    private fun provideFile(
        source: File,
        mimeType: String,
        displayName: String? = source.name,
        intentMimeType: String? = mimeType,
    ): Intent {
        val authority = "com.ichi2.anki.importtest"
        val provider = ImportProvider(source, mimeType, displayName) { filenameQueries++ }
        provider.attachInfo(targetContext, ProviderInfo().apply { this.authority = authority })
        ShadowContentResolver.registerProviderInternal(authority, provider)
        // An opaque URI requires the import flow to use DISPLAY_NAME from the provider.
        return Intent(Intent.ACTION_VIEW).setDataAndType("content://$authority/document/123".toUri(), intentMimeType)
    }

    private class ImportProvider(
        private val source: File,
        private val mimeType: String,
        private val displayName: String?,
        private val onQuery: () -> Unit,
    ) : ContentProvider() {
        override fun onCreate() = true

        override fun getType(uri: Uri) = mimeType

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ) = MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME)).apply {
            onQuery()
            addRow(arrayOf(displayName))
        }

        override fun openFile(
            uri: Uri,
            mode: String,
        ) = ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY)

        override fun insert(
            uri: Uri,
            values: ContentValues?,
        ): Uri? = error("Not supported")

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int = error("Not supported")

        override fun delete(
            uri: Uri,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int = error("Not supported")
    }
}

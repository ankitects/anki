// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ContentProvider
import android.content.Intent
import android.database.MatrixCursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.common.crashreporting.CrashReporter
import com.ichi2.anki.pages.CsvImporter
import com.ichi2.utils.ImportResult
import com.ichi2.utils.ImportUtils
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowContentResolver
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Preparation reports copy failures; UI dispatch uses the prepared path. */
@RunWith(AndroidJUnit4::class)
class ImportPreparationTest : RobolectricTest() {
    private lateinit var originalCrashReporter: CrashReporter
    private val crashReporter = mock<CrashReporter>()

    @Before
    fun captureCrashReports() {
        originalCrashReporter = CrashReportService.getReporter()
        CrashReportService.setReporter(crashReporter)
    }

    @After
    fun restoreCrashReporter() {
        CrashReportService.setReporter(originalCrashReporter)
    }

    @Test
    fun `missing text source returns failure without opening the importer`() {
        val activity = importActivity()
        val missingFile = File(tempFolder.root, "deleted.csv")
        val uri = textSource("deleted.csv") { missingFile.inputStream() }

        val result = ImportUtils.prepareImport(activity, Intent(Intent.ACTION_VIEW, uri))

        val failure = assertIs<ImportResult.Failure>(result)
        assertIs<FileNotFoundException>(failure.exception)
        assertEquals(activity.getString(CommonString.import_error_copy_to_cache_title), failure.title)
        assertNull(shadowOf(activity).nextStartedActivity)
        verifyNoInteractions(crashReporter)
    }

    @Test
    fun `unreadable text source returns failure and reports the copy error`() {
        val activity = importActivity()
        val exception = IOException("Could not read selected text file")
        val uri =
            textSource("unreadable.tsv") {
                object : InputStream() {
                    override fun read(): Int = throw exception
                }
            }

        val result = ImportUtils.prepareImport(activity, Intent(Intent.ACTION_VIEW, uri))

        val failure = assertIs<ImportResult.Failure>(result)
        assertEquals(exception, failure.exception)
        assertEquals(activity.getString(CommonString.import_error_copy_to_cache_title), failure.title)
        assertNull(shadowOf(activity).nextStartedActivity)
        verify(crashReporter).sendExceptionReport(exception, "ImportUtils", null, false)
    }

    @Test
    fun `text import copies bytes and opens the raw cached path before returning`() {
        val activity = importActivity()
        val fileName = "notes 好 + %.csv"
        val contents = "front,back\nhello,world\n"
        val uri = textSource(fileName) { contents.byteInputStream() }

        val result = ImportUtils.prepareImport(activity, Intent(Intent.ACTION_VIEW, uri))

        activity.openImport(assertIs<ImportResult.Success>(result))
        val cachedFile = File(activity.cacheDir, fileName)
        assertEquals(contents, cachedFile.readText())
        val intent = assertNotNull(shadowOf(activity).nextStartedActivity)
        assertEquals(SingleFragmentActivity::class.java.name, intent.component?.className)
        assertEquals(CsvImporter::class.java.name, intent.getStringExtra(SingleFragmentActivity.EXTRA_FRAGMENT_NAME))
        assertEquals(cachedFile.absolutePath, intent.getBundleExtra(SingleFragmentActivity.EXTRA_FRAGMENT_ARGS)?.getString("filePath"))
        assertEquals(DeckPicker::class.java.name, shadowOf(activity).nextStartedActivity?.component?.className)
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun `preparation copies text without opening the importer`() {
        val activity = importActivity()
        val contents = "front,back"
        val uri = textSource("notes.csv") { contents.byteInputStream() }

        val result = assertIs<ImportResult.Success>(ImportUtils.prepareImport(activity, Intent(Intent.ACTION_VIEW, uri)))

        assertEquals(uri, result.file.source.uri)
        assertEquals(File(activity.cacheDir, "notes.csv").absolutePath, result.path)
        assertEquals(contents, File(result.path).readText())
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun `failure to launch text importer propagates to the caller`() {
        val activity = spy(importActivity())
        val exception = ActivityNotFoundException("Importer could not be opened")
        doThrow(exception).whenever(activity).startActivities(any(), anyOrNull())
        val uri = textSource("notes.csv") { "front,back".byteInputStream() }

        val result = ImportUtils.prepareImport(activity, Intent(Intent.ACTION_VIEW, uri))

        assertEquals(
            exception,
            assertFailsWith<ActivityNotFoundException> { activity.openImport(assertIs<ImportResult.Success>(result)) },
        )
        verifyNoInteractions(crashReporter)
    }

    private fun importActivity(): Activity {
        val controller = Robolectric.buildActivity(Activity::class.java).create()
        saveControllerForCleanup(controller)
        return controller.get()
    }

    private fun textSource(
        fileName: String,
        open: () -> InputStream,
    ): Uri {
        val uri = "content://import/document/123".toUri()
        val provider = mock<ContentProvider>()
        whenever(provider.query(any(), anyOrNull(), anyOrNull<String>(), anyOrNull(), anyOrNull())).thenAnswer {
            MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME)).apply { addRow(arrayOf(fileName)) }
        }
        whenever(provider.getType(uri)).thenReturn("text/csv")
        ShadowContentResolver.registerProviderInternal("import", provider)
        shadowOf(targetContext.contentResolver).registerInputStreamSupplier(uri) { open() }
        return uri
    }
}

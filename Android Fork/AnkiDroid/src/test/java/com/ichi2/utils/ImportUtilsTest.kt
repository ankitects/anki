// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.utils

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipDescription
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.annotation.CheckResult
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.CommonString
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.common.crashreporting.CrashReporter
import com.ichi2.anki.common.time.MockTime
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.dialogs.DialogHandler
import com.ichi2.utils.ImportUtils.FileImporter
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.endsWith
import org.hamcrest.Matchers.lessThanOrEqualTo
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.util.function.Supplier
import kotlin.test.assertIs
import kotlin.test.assertNull

/** Tests for [ImportUtils]. */
@RunWith(AndroidJUnit4::class)
class ImportUtilsTest : RobolectricTest() {
    private lateinit var originalCrashReporter: CrashReporter
    private val crashReporter = mock(CrashReporter::class.java)
    private val packageMimeTypes =
        mapOf(
            "application/apkg" to ImportResolution.DeckPackage::class,
            "application/x-apkg" to ImportResolution.DeckPackage::class,
            "application/vnd.anki" to ImportResolution.DeckPackage::class,
            "application/colpkg" to ImportResolution.CollectionPackage::class,
            "application/x-colpkg" to ImportResolution.CollectionPackage::class,
        )
    private val storageDocumentUri = "content://com.android.externalstorage.documents/document/denied.apkg".toUri()
    private val mediaStoreUri = "content://media/external_primary/file/52".toUri()
    private val storageProviderRefusal = SecurityException("com.android.externalstorage has no access to $mediaStoreUri")

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
    fun `missing source file returns an import failure without a crash report`() {
        val uri = "content://import/deleted.apkg".toUri()
        val missingFile = File(tempFolder.root, "deleted.apkg")
        shadowOf(targetContext.contentResolver).registerInputStreamSupplier(uri) { missingFile.inputStream() }

        val result = importerWithFileName("deleted.apkg").prepareImport(targetContext, Intent(Intent.ACTION_VIEW, uri))

        val failure = assertIs<ImportResult.Failure>(result)
        assertIs<FileNotFoundException>(failure.exception)
        assertEquals(targetContext.getString(CommonString.import_error_copy_to_cache_title), failure.title)
        verifyNoInteractions(crashReporter)
    }

    @Test
    fun `unexpected source errors are still reported`() {
        val uri = "content://import/broken.apkg".toUri()
        val exception = IOException("Provider failed")
        shadowOf(targetContext.contentResolver).registerInputStreamSupplier(uri) { throw exception }

        val result = importerWithFileName("broken.apkg").prepareImport(targetContext, Intent(Intent.ACTION_VIEW, uri))

        assertEquals(exception, assertIs<ImportResult.Failure>(result).exception)
        verify(crashReporter).sendExceptionReport(exception, "ImportUtils", null, false)
    }

    @Test
    fun `pending or trashed download returns an import failure without a crash report`() {
        val uri = "content://import/pending.apkg".toUri()
        val exception = IllegalStateException("Only owner is able to interact with pending/trashed item $uri")
        shadowOf(targetContext.contentResolver).registerInputStreamSupplier(uri) { throw exception }

        val result = importerWithFileName("pending.apkg").prepareImport(targetContext, Intent(Intent.ACTION_VIEW, uri))

        assertEquals(exception, assertIs<ImportResult.Failure>(result).exception)
        verifyNoInteractions(crashReporter)
    }

    @Test
    fun `other illegal state errors from the source are still reported`() {
        val uri = "content://import/illegal-state.apkg".toUri()
        val exception = IllegalStateException("Provider failed")
        shadowOf(targetContext.contentResolver).registerInputStreamSupplier(uri) { throw exception }

        val result = importerWithFileName("illegal-state.apkg").prepareImport(targetContext, Intent(Intent.ACTION_VIEW, uri))

        assertEquals(exception, assertIs<ImportResult.Failure>(result).exception)
        verify(crashReporter).sendExceptionReport(exception, "ImportUtils", null, false)
    }

    @Test
    fun `termux refusing access shows the error without a crash report`() {
        val uri = "content://com.termux.files/file/termux.apkg".toUri()
        val message =
            "TermuxContentProvider requires `allow-external-apps` property to be set to `true` in `~/.termux/termux.properties` file."
        val exception = IllegalArgumentException(message)
        shadowOf(targetContext.contentResolver).registerInputStreamSupplier(uri) { throw exception }

        val result = importerWithFileName("termux.apkg").prepareImport(targetContext, Intent(Intent.ACTION_VIEW, uri))

        val failure = assertIs<ImportResult.Failure>(result)
        assertEquals(exception, failure.exception)
        assertThat(failure.humanReadableMessage, containsString(message))
        verifyNoInteractions(crashReporter)
    }

    @Test
    fun `other illegal argument errors are still reported`() {
        val uri = "content://import/illegal-argument.apkg".toUri()
        val exception = IllegalArgumentException("Provider failed")
        shadowOf(targetContext.contentResolver).registerInputStreamSupplier(uri) { throw exception }

        val result = importerWithFileName("illegal-argument.apkg").prepareImport(targetContext, Intent(Intent.ACTION_VIEW, uri))

        assertEquals(exception, assertIs<ImportResult.Failure>(result).exception)
        verify(crashReporter).sendExceptionReport(exception, "ImportUtils", null, false)
    }

    @Test
    fun `storage provider refused by media store imports through media store`() {
        val result = importRefusedStorageDocument { "package contents".byteInputStream() }

        assertEquals("package contents", File(assertIs<ImportResult.Success>(result).path).readText())
        verifyNoInteractions(crashReporter)
    }

    @Test
    fun `storage provider refusal is still reported if media store cannot open the file`() {
        val result = importRefusedStorageDocument { throw SecurityException() }

        assertEquals(storageProviderRefusal, assertIs<ImportResult.Failure>(result).exception)
        verify(crashReporter).sendExceptionReport(storageProviderRefusal, "ImportUtils", null, false)
    }

    @SuppressLint("NewApi")
    private fun importRefusedStorageDocument(mediaStoreSource: Supplier<InputStream>): ImportResult {
        mockkStatic(MediaStore::class)
        try {
            every { MediaStore.getMediaUri(targetContext, storageDocumentUri) } returns mediaStoreUri
            shadowOf(targetContext.contentResolver).registerInputStreamSupplier(storageDocumentUri) { throw storageProviderRefusal }
            shadowOf(targetContext.contentResolver).registerInputStreamSupplier(mediaStoreUri, mediaStoreSource)
            return importerWithFileName("denied.apkg").prepareImport(targetContext, Intent(Intent.ACTION_VIEW, storageDocumentUri))
        } finally {
            unmockkStatic(MediaStore::class)
        }
    }

    @Test
    fun `file not found while reading an open source is still reported`() {
        val uri = "content://import/unreadable.apkg".toUri()
        val exception = FileNotFoundException("Read failed after opening")
        shadowOf(targetContext.contentResolver).registerInputStreamSupplier(uri) {
            object : InputStream() {
                override fun read(): Int = throw exception
            }
        }

        val result = importerWithFileName("unreadable.apkg").prepareImport(targetContext, Intent(Intent.ACTION_VIEW, uri))

        assertEquals(exception, assertIs<ImportResult.Failure>(result).exception)
        verify(crashReporter).sendExceptionReport(exception, "ImportUtils", null, false)
    }

    private fun importerWithFileName(fileName: String): FileImporter =
        object : FileImporter() {
            override fun getFileNameFromContentProvider(
                context: Context,
                data: Uri,
            ): String = fileName
        }

    @Test
    fun `preparation keeps the filename resolved before copying`() {
        var queries = 0
        val importer =
            object : FileImporter() {
                override fun getFileNameFromContentProvider(
                    context: Context,
                    data: Uri,
                ): String {
                    queries++
                    return if (queries == 1) "deck name.apkg" else "collection.colpkg"
                }

                override fun copyFileToCache(
                    context: Context,
                    data: Uri,
                    tempPath: String,
                ) = CacheFileResult.Success(tempPath)
            }
        val uri = "content://import/document/123".toUri()

        val result = assertIs<ImportResult.Success>(importer.prepareImport(targetContext, Intent(Intent.ACTION_VIEW, uri)))

        assertIs<ImportResolution.DeckPackage>(result.file)
        assertEquals(uri, result.file.source.uri)
        assertEquals("deck name.apkg", File(Uri.decode(result.path)).name)
        assertEquals(1, queries)
    }

    @Test
    fun `preparing packages does not store a confirmation`() {
        val handler = DialogHandler(mock(AnkiActivity::class.java))
        val uri = "content://import/document/123".toUri()
        val packages =
            listOf(
                "deck.apkg" to ImportResolution.DeckPackage::class,
                "backup.colpkg" to ImportResolution.CollectionPackage::class,
            )

        for ((fileName, expectedType) in packages) {
            val result =
                assertIs<ImportResult.Success>(
                    TestFileImporter(fileName).prepareImport(targetContext, Intent(Intent.ACTION_VIEW, uri)),
                )

            assertEquals(expectedType, result.file::class)
            assertNull(handler.popMessage(), "Preparation must not store a confirmation for $fileName")
        }
    }

    @Test
    fun `resolution reports missing filenames instead of assuming text`() {
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType("content://import/document/123".toUri(), "text/plain")

        assertIs<ImportResolution.Failure>(TestFileImporter(null).resolveImport(targetContext, intent))
    }

    @Test
    fun `resolution does not copy the source`() {
        val importer =
            object : FileImporter() {
                override fun getFileNameFromContentProvider(
                    context: Context,
                    data: Uri,
                ) = "collection.colpkg"

                override fun copyFileToCache(
                    context: Context,
                    data: Uri,
                    tempPath: String,
                ): CacheFileResult = error("Resolving an import must not copy its contents")
            }
        val intent = Intent(Intent.ACTION_VIEW, "content://import/document/123".toUri())

        assertIs<ImportResolution.CollectionPackage>(importer.resolveImport(targetContext, intent))
    }

    @Test
    fun `legacy collection name is classified after sanitization`() {
        val intent = Intent(Intent.ACTION_VIEW, "content://import/document/123".toUri())

        val result = TestFileImporter("../collection.apkg").resolveImport(targetContext, intent)

        assertIs<ImportResolution.CollectionPackage>(result)
        assertEquals("collection.apkg", result.source.fileName)
    }

    @Test
    fun `text MIME can come from the intent or the provider`() {
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType("content://import/document/123".toUri(), "text/plain")
        assertIs<ImportResolution.Text>(TestFileImporter("notes").resolveImport(targetContext, intent))

        intent.setDataAndType(intent.data, "application/octet-stream")
        assertIs<ImportResolution.Text>(TestFileImporter("notes").resolveImport(mockContextWithMime("text/csv"), intent))
    }

    @Test
    fun `text filenames remain importable without useful MIME metadata`() {
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType("content://import/document/123".toUri(), "application/octet-stream")
        for (filename in listOf("notes.csv", "notes.TSV", "notes.txt")) {
            assertIs<ImportResolution.Text>(TestFileImporter(filename).resolveImport(targetContext, intent))
        }
    }

    @Test
    fun `specific package MIME types take precedence over text metadata`() {
        for ((packageMime, expectedType) in packageMimeTypes) {
            for (filename in listOf("notes.csv", "notes.tsv", "notes.txt", "download", "download.zip", "")) {
                for ((intentMime, providerMime) in listOf(
                    packageMime to "text/plain",
                    "text/plain" to packageMime,
                    packageMime to "text/csv",
                    "text/csv" to packageMime,
                    null to packageMime,
                    "application/octet-stream" to packageMime,
                )) {
                    val resolution = resolveWithMetadata(filename, intentMime, providerMime)

                    assertEquals("$filename, intent=$intentMime, provider=$providerMime", expectedType, resolution::class)
                }
            }
        }
    }

    @Test
    fun `specific package MIME types provide a fallback filename`() {
        for ((packageMime, expectedType) in packageMimeTypes) {
            for ((intentMime, providerMime) in listOf(packageMime to "text/plain", "text/plain" to packageMime, null to packageMime)) {
                val resolution = assertIs<ImportResolution.ResolvedFile>(resolveWithMetadata(null, intentMime, providerMime))

                assertEquals(expectedType, resolution::class)
                val extension = if (expectedType == ImportResolution.CollectionPackage::class) "colpkg" else "apkg"
                assertEquals("unknown_filename.$extension", resolution.source.fileName)
            }
        }
    }

    @Test
    fun `package filenames determine the package kind before MIME hints`() {
        for ((filename, expectedType) in listOf(
            "deck.apkg" to ImportResolution.DeckPackage::class,
            "legacy.APKG" to ImportResolution.DeckPackage::class,
            "backup.colpkg" to ImportResolution.CollectionPackage::class,
            "collection.apkg" to ImportResolution.CollectionPackage::class,
        )) {
            for (packageMime in packageMimeTypes.keys) {
                assertEquals(expectedType, resolveWithMetadata(filename, packageMime, packageMime)::class)
            }
        }
    }

    @Test
    fun `intent package MIME wins when the provider supplies another package MIME`() {
        assertIs<ImportResolution.DeckPackage>(resolveWithMetadata("download", "application/apkg", "application/colpkg"))
        assertIs<ImportResolution.CollectionPackage>(resolveWithMetadata("download", "application/colpkg", "application/apkg"))
    }

    @Test
    fun `generic MIME types do not identify named files as Anki packages`() {
        for (mime in listOf("application/octet-stream", "application/zip", "text/plain")) {
            assertIs<ImportResolution.Text>(resolveWithMetadata("notes.csv", mime, mime))
        }
        for (mime in listOf("application/octet-stream", "application/zip")) {
            assertIs<ImportResolution.Failure>(resolveWithMetadata("download", mime, mime))
        }
        assertIs<ImportResolution.Text>(resolveWithMetadata("download", "text/plain", "text/plain"))
    }

    @Test
    fun `nameless ZIP fallback defers to a specific package MIME`() {
        assertIs<ImportResolution.DeckPackage>(resolveWithMetadata(null, "application/zip", null))
        val collection =
            assertIs<ImportResolution.CollectionPackage>(resolveWithMetadata(null, "application/zip", "application/colpkg"))
        assertEquals("unknown_filename.colpkg", collection.source.fileName)
    }

    @Test
    fun `unavailable provider MIME does not invalidate usable filenames or an intent package MIME`() {
        val context = mockContextWithMime(null)
        whenever(context.contentResolver.getType(any())).thenThrow(SecurityException("MIME metadata is unavailable"))
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType("content://import/document/123".toUri(), "text/plain")

        assertIs<ImportResolution.Text>(TestFileImporter("notes.csv").resolveImport(context, intent))
        assertIs<ImportResolution.DeckPackage>(TestFileImporter("deck.apkg").resolveImport(context, intent))

        intent.setDataAndType(intent.data, "application/apkg")
        assertIs<ImportResolution.DeckPackage>(TestFileImporter("download").resolveImport(context, intent))
        assertIs<ImportResolution.DeckPackage>(TestFileImporter(null).resolveImport(context, intent))
    }

    private fun resolveWithMetadata(
        filename: String?,
        intentMime: String?,
        providerMime: String?,
    ): ImportResolution {
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType("content://import/document/123".toUri(), intentMime)
        return TestFileImporter(filename).resolveImport(mockContextWithMime(providerMime), intent)
    }

    @Test
    fun cjkNamesAreConvertedToUnicode() {
        // NOTE: I don't know whether this still needs to exist, but it was added as this previously crashes
        // and I would have added a regression without checking the history.
        // https://github.com/ankidroid/Anki-Android/commit/ed06954c8c678024e2fce25c19bd6cdaf0120260#diff-8eefa7f7b20c936f007c934965238520R58

        val inputFileName = "好.apkg"

        val actualFilePath = importValidFile(inputFileName)

        assertThat("Unicode character should be stripped", actualFilePath, not(containsString("好")))
        assertThat("Unicode character should be urlencoded", actualFilePath, endsWith("%E5%A5%BD.apkg"))
    }

    @Test
    fun fileNamesAreLimitedTo100Chars() {
        // #6137 - We URLEncode due to the above. Therefore: 好 -> %E5%A5%BD
        // This caused filenames to be too long.
        val inputFileName = "好好好好好好好好好好好好好好好好好好好好好好好好好好好好好好好好好好好好好好好好好好好好.apkg"

        val actualFilePath = importValidFile(inputFileName)

        assertThat(actualFilePath, endsWith(".apkg"))
        assertThat(actualFilePath, containsString("..."))
        // Obtain the filename from the path
        assertThat(actualFilePath, containsString("%E5%A5%BD"))
        val fileName = actualFilePath.substringAfter("%E5%A5%BD")
        assertThat(fileName.length, lessThanOrEqualTo(100))
    }

    private fun importValidFile(fileName: String): String {
        val testFileImporter = TestFileImporter(fileName)
        val intent = getValidClipDataUri(fileName)
        testFileImporter.prepareImport(targetContext, intent)

        // COULD_BE_BETTER: Strip off the file path
        return testFileImporter.cacheFileName
    }

    @Test
    fun pathTraversalInFileNameIsRejected() {
        // GHSA-q29p-h3pp-mh3v — Path traversal via import DISPLAY_NAME should be blocked
        val cacheDir = targetContext.cacheDir.canonicalFile
        val maliciousFilenames =
            listOf(
                "../etc/passwd.apkg",
                "..\\windows\\system32\\config.sam.apkg",
                "../../../../../../../../../etc/passwd.apkg",
                "..\\..\\..\\passwd.apkg",
                "%2e%2e%2fetc%2fpasswd.apkg", // percent-encoded traversal: File does not decode it
                "....apkg",
                "normal.apkg",
            )

        for (maliciousFilename in maliciousFilenames) {
            val testFileImporter = TestFileImporter(maliciousFilename)
            val intent = getValidClipDataUri(maliciousFilename)
            val result = testFileImporter.prepareImport(targetContext, intent)
            assertTrue("Import should succeed after basename sanitization: $maliciousFilename", result is ImportResult.Success)

            // the cached path is Uri-encoded (see cacheImport): decode it before resolving it on disk
            val cachedFile = File(Uri.decode(testFileImporter.cacheFileName)).canonicalFile
            assertEquals(
                "Cached file must be a direct child of cacheDir: $maliciousFilename -> $cachedFile",
                cacheDir,
                cachedFile.parentFile,
            )
        }
    }

    @Test
    fun leadingDotFilenamesAreNotStripped() {
        for (fileName in listOf(".hidden.apkg", "..apkg")) {
            val actualFilePath = importValidFile(fileName)
            assertEquals(fileName, File(Uri.decode(actualFilePath)).name)
        }
    }

    @Test
    fun getFileCachedCopyUsesUnnamedFileForEmptyDotAndDotDot() {
        for (fileName in listOf("", ".", "..")) {
            val actualFilepath = TestFileImporter(fileName).getFileCachedCopy(targetContext, "dummy".toUri())
            assertEquals(File(targetContext.cacheDir, "unnamed_file").absolutePath, actualFilepath)
        }
    }

    @Test
    fun getFileCachedCopyReturnsAbsolutePath() {
        val filename = "spaced filename.apkg"
        val expectedFilepath = File(targetContext.cacheDir, filename).absolutePath
        val actualFilepath = TestFileImporter(filename).getFileCachedCopy(targetContext, "dummy".toUri())
        assertEquals(expectedFilepath, actualFilepath)
    }

    @Test
    fun getFileCachedCopyUsesFallbackFileNameWhenDecodingFails() {
        TimeManager.resetWith(MockTime(1_600_000_000_000))
        val prefix = "a".repeat(89)
        val invalidShortenedFileName = "${prefix}ääääääääää.apkg"
        val expectedFallbackFileName = "file_1600000000000.apkg"
        val expectedFilepath = File(targetContext.cacheDir, expectedFallbackFileName).absolutePath

        val actualFilepath = TestFileImporter(invalidShortenedFileName).getFileCachedCopy(targetContext, "dummy".toUri())

        assertEquals(expectedFilepath, actualFilepath)
    }

    @Test
    fun collectionApkgIsValid() {
        assertTrue(ImportUtils.isValidPackageName("collection.apkg"))
    }

    @Test
    fun collectionColPkgIsValid() {
        assertTrue(ImportUtils.isValidPackageName("collection.colpkg"))
    }

    @Test
    fun deckApkgIsValid() {
        assertTrue(ImportUtils.isValidPackageName("deckName.apkg"))
    }

    @Test
    fun deckColPkgIsValid() {
        assertTrue(ImportUtils.isValidPackageName("deckName.colpkg"))
    }

    @Test
    fun nullIsNotValidPackage() {
        assertFalse(ImportUtils.isValidPackageName(null))
    }

    @Test
    fun docxIsNotValidForImport() {
        assertFalse(ImportUtils.isValidPackageName("test.docx"))
    }

    @Test
    fun onlySupportedTextMimeTypesSelectTheTextImporter() {
        val uri = "content://com.example".toUri()
        val validMimeTypes =
            listOf(
                "text/plain",
                "text/comma-separated-values",
                "text/tab-separated-values",
                "text/csv",
                "text/tsv",
            )
        val invalidMimeTypes =
            listOf(
                null,
                "text/html",
                "application/pdf",
                "image/jpeg",
                "image/png",
            )

        for (mime in validMimeTypes) {
            val context = mockContextWithMime(mime)
            val resolution = TestFileImporter("notes").resolveImport(context, Intent(Intent.ACTION_VIEW, uri))
            assertIs<ImportResolution.Text>(resolution, "Expected text MIME to be accepted: $mime")
        }

        for (mime in invalidMimeTypes) {
            val context = mockContextWithMime(mime)
            val resolution = TestFileImporter("notes").resolveImport(context, Intent(Intent.ACTION_VIEW, uri))
            assertIs<ImportResolution.Failure>(resolution, "Expected unsupported MIME to be rejected: $mime")
        }
    }

    private fun mockContextWithMime(mimeType: String?): Context {
        val resolver = mock(ContentResolver::class.java)
        whenever(resolver.getType(any())).thenReturn(mimeType)
        val context = spy(targetContext)
        whenever(context.contentResolver).thenReturn(resolver)
        return context
    }

    @CheckResult
    private fun getValidClipDataUri(fileName: String): Intent {
        val i = Intent()
        i.clipData = clipDataUriFromFile(fileName)
        return i
    }

    private fun clipDataUriFromFile(fileName: String): ClipData {
        val item = ClipData.Item("content://$fileName".toUri())
        val description = ClipDescription("", arrayOf())
        return ClipData(description, item)
    }

    class TestFileImporter(
        private val fileName: String?,
    ) : FileImporter() {
        lateinit var cacheFileName: String
            private set

        override fun copyFileToCache(
            context: Context,
            data: Uri,
            tempPath: String,
        ) = run {
            cacheFileName = tempPath
            CacheFileResult.Success(tempPath)
        }

        override fun getFileNameFromContentProvider(
            context: Context,
            data: Uri,
        ): String? = fileName
    }
}

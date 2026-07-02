/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.utils

import android.content.ClipData
import android.content.ClipDescription
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.CheckResult
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.utils.ImportUtils.FileImporter
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.endsWith
import org.hamcrest.Matchers.lessThanOrEqualTo
import org.hamcrest.Matchers.not
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import java.io.File

@RunWith(AndroidJUnit4::class)
class ImportUtilsTest : RobolectricTest() {
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
        testFileImporter.handleFileImport(targetContext, intent)

        // COULD_BE_BETTER: Strip off the file path
        return testFileImporter.cacheFileName
    }

    @Test
    fun getFileCachedCopyReturnsAbsolutePath() {
        val filename = "spaced filename.apkg"
        val expectedFilepath = File(targetContext.cacheDir, filename).absolutePath
        val actualFilepath = TestFileImporter(filename).getFileCachedCopy(targetContext, "dummy".toUri())
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
    fun onlyValidTextOrDataMimeTypesReturnTrue() {
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
            val isValid = ImportUtils.isValidTextOrDataFile(context, uri)
            assertTrue("Expected MIME to be accepted: $mime", isValid)
        }

        for (mime in invalidMimeTypes) {
            val context = mockContextWithMime(mime)
            val isValid = ImportUtils.isValidTextOrDataFile(context, uri)
            assertFalse("Expected MIME to be rejected: $mime", isValid)
        }
    }

    private fun mockContextWithMime(mimeType: String?): Context {
        val resolver = mock(ContentResolver::class.java)
        whenever(resolver.getType(any())).thenReturn(mimeType)
        val context = mock(Context::class.java)
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

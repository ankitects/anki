// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.multimedia

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Looper
import androidx.activity.result.ActivityResult
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.testutils.ShadowFileProviderPathStrategy
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.IOException
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

@RunWith(AndroidJUnit4::class)
@Config(shadows = [ShadowFileProviderPathStrategy::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExternalImageEditorResultTest : RobolectricTest(loadBackend = false) {
    private val editedImage = editorResultImageBytes()
    private lateinit var editor: ExternalImageEditor
    private lateinit var resultProvider: EditorResultProvider
    private lateinit var cacheDirectory: File
    private lateinit var original: File
    private lateinit var copy: File

    @Before
    fun setUpEditor() {
        clearFileProviderRoots()
        cacheDirectory = File(targetContext.cacheDir, "external-editor").apply { mkdirs() }
        original = File(cacheDirectory, "original.png").apply { writeText("original") }
        copy = File(cacheDirectory, "working.png").apply { writeText("original") }
        editor = ExternalImageEditor(targetContext.applicationContext, cacheDirectory)
        resultProvider = EditorResultProvider.register()
    }

    @Test
    fun `a returned file uri is rejected`() =
        runBlocking {
            assertRejectedResult(Uri.fromFile(original))
        }

    @Test
    fun `an empty working copy is rejected`() =
        runBlocking {
            copy.writeText("")
            assertRejectedResult()
        }

    @Test
    fun `rejecting a same-name empty result preserves original bytes`() =
        runBlocking {
            val result = File(targetContext.cacheDir, "empty.png").apply { writeText("") }
            assertRejectedResult(resultProvider.result(result, displayName = original.name))
        }

    @Test
    fun `returned display name cannot escape image cache`() =
        runBlocking {
            val existing = File(targetContext.filesDir, "existing.png").apply { writeText("untouched") }
            val result = File(targetContext.cacheDir, "edited.png").apply { writeBytes(editedImage) }
            val accepted = assertNotNull(readResult(resultProvider.result(result, "../../files/existing.png")))
            assertEquals("untouched", existing.readText())
            assertContentEquals(editedImage, accepted.readBytes())
            assertEquals(cacheDirectory, accepted.parentFile)
        }

    @Test
    fun `returned image keeps its mime type when the editor changes formats`() =
        runBlocking {
            val result = File(targetContext.cacheDir, "edited.jpg").apply { writeBytes(editorResultImageBytes("jpeg")) }
            val accepted = assertNotNull(readResult(resultProvider.result(result)))
            assertEquals("image/jpeg", targetContext.contentResolver.getType(uriForFile(accepted)))
            assertContentEquals(result.readBytes(), accepted.readBytes())
        }

    @Test
    fun `a non-image result is rejected`() =
        runBlocking {
            val result = File(targetContext.cacheDir, "edited.html").apply { writeText("<html>not an image</html>") }
            assertRejectedResult(resultProvider.result(result, mimeType = "text/html"))
        }

    @Test
    fun `an invalid result claiming an image mime type is rejected`() =
        runBlocking {
            val result = File(targetContext.cacheDir, "edited.png").apply { writeText("not an image") }
            assertRejectedResult(resultProvider.result(result))
        }

    @Test
    fun `an invalid in-place result is rejected`() =
        runBlocking {
            copy.writeText("not an image")
            assertRejectedResult()
        }

    @Test
    fun `image headers override a non-image provider mime type`() =
        runBlocking {
            val result = File(targetContext.cacheDir, "edited.html").apply { writeBytes(editedImage) }
            val accepted = assertNotNull(readResult(resultProvider.result(result, mimeType = "text/html")))
            assertEquals("image/png", targetContext.contentResolver.getType(uriForFile(accepted)))
            assertContentEquals(editedImage, accepted.readBytes())
        }

    @Test
    fun `image headers supply a missing provider mime type`() =
        runBlocking {
            val result = File(targetContext.cacheDir, "edited.bin").apply { writeBytes(editorResultImageBytes("jpeg")) }
            val accepted = assertNotNull(readResult(resultProvider.result(result, mimeType = null)))
            assertEquals("image/jpeg", targetContext.contentResolver.getType(uriForFile(accepted)))
            assertContentEquals(result.readBytes(), accepted.readBytes())
        }

    @Test
    fun `image headers override an incorrect image provider mime type`() =
        runBlocking {
            val result = File(targetContext.cacheDir, "edited.jpg").apply { writeBytes(editedImage) }
            val accepted = assertNotNull(readResult(resultProvider.result(result)))
            assertEquals("image/png", targetContext.contentResolver.getType(uriForFile(accepted)))
            assertContentEquals(editedImage, accepted.readBytes())
        }

    @Test
    fun `an in-place result can change image formats`() =
        runBlocking {
            val jpeg = editorResultImageBytes("jpeg")
            copy.writeBytes(jpeg)
            val accepted = assertNotNull(readResult())
            assertEquals("image/jpeg", targetContext.contentResolver.getType(uriForFile(accepted)))
            assertContentEquals(jpeg, accepted.readBytes())
        }

    @Test
    fun `an unshared private cache file is rejected`() =
        runBlocking {
            val secret = File(targetContext.cacheDir, "private.png").apply { writeText("private data") }
            assertRejectedResult(uriForFile(secret))
            assertEquals("private data", secret.readText())
        }

    @Test
    fun `the supplied working uri is accepted`() =
        runBlocking {
            copy.writeBytes(editedImage)
            val accepted = assertNotNull(readResult(uriForFile(copy)))
            assertContentEquals(editedImage, accepted.readBytes())
            assertNotEquals(copy, accepted)
            assertEquals("original", original.readText())
        }

    @Test
    fun `external result is imported off the main thread`() =
        runBlocking {
            val result = File(targetContext.cacheDir, "edited.png").apply { writeBytes(editedImage) }
            val accepted = assertNotNull(readResult(resultProvider.result(result)))
            assertContentEquals(editedImage, accepted.readBytes())
            assertNotEquals(Looper.getMainLooper().thread, assertNotNull(resultProvider.openThread))
        }

    private suspend fun readResult(uri: Uri? = null): File? =
        editor.readResult(original, copy, ActivityResult(Activity.RESULT_OK, uri?.let { Intent().setData(it) }))

    private suspend fun assertRejectedResult(uri: Uri? = null) {
        assertFailsWith<IOException> { readResult(uri) }
        assertEquals("original", original.readText())
        assertEquals(setOf(original, copy), cacheDirectory.listFiles()!!.toSet())
    }

    private fun uriForFile(file: File): Uri =
        FileProvider.getUriForFile(targetContext, "${targetContext.packageName}.apkgfileprovider", file)
}

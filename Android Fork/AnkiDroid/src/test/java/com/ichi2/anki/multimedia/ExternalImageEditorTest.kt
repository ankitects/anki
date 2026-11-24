// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.multimedia

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.utils.ext.getParcelableExtraCompat
import com.ichi2.testutils.ShadowFileProviderPathStrategy
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.runBlocking
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(shadows = [ShadowFileProviderPathStrategy::class])
class ExternalImageEditorTest : RobolectricTest(loadBackend = false) {
    private lateinit var editor: ExternalImageEditor

    @Before
    fun setUpEditor() {
        clearFileProviderRoots()
        editor = ExternalImageEditor(targetContext.applicationContext, targetContext.cacheDir)
        targetContext.registerExternalImageEditor()
    }

    @Test
    fun `preparation shares a writable copy through a chooser`() =
        runBlocking {
            val original = File(targetContext.cacheDir, "original.png").apply { writeText("original") }
            val prepared = editor.prepare(original)
            try {
                assertThat(prepared.intent.action, equalTo(Intent.ACTION_CHOOSER))
                val edit = requireNotNull(prepared.intent.getParcelableExtraCompat<Intent>(Intent.EXTRA_INTENT))
                assertThat(edit.action, equalTo(Intent.ACTION_EDIT))
                assertThat(edit.type, equalTo("image/png"))
                assertThat(edit.data?.scheme, equalTo("content"))
                assertThat(edit.clipData!!.getItemAt(0).uri, equalTo(edit.data))
                val grants = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                assertThat(edit.flags and grants, equalTo(grants))
                targetContext.contentResolver.openOutputStream(edit.data!!, "wt")!!.use { it.write("edited".toByteArray()) }
                assertThat(original.readText(), equalTo("original"))
            } finally {
                editor.clear(prepared.file)
            }
        }

    @Test
    fun `preparation provides Google Photos with a writable output URI`() =
        runBlocking {
            val original = File(targetContext.cacheDir, "original.png").apply { writeText("original") }
            val prepared = editor.prepare(original)
            try {
                val edit = requireNotNull(prepared.intent.getParcelableExtraCompat<Intent>(Intent.EXTRA_INTENT))
                val output = requireNotNull(edit.getParcelableExtraCompat<Uri>(MediaStore.EXTRA_OUTPUT))
                assertThat(output, equalTo(edit.data))
                targetContext.contentResolver.openOutputStream(output, "wt")!!.use { it.write("edited".toByteArray()) }
                assertThat(prepared.file.readText(), equalTo("edited"))
                assertThat(original.readText(), equalTo("original"))
            } finally {
                editor.clear(prepared.file)
            }
        }

    @Test
    fun `an unshareable source does not report an available editor`() =
        runBlocking {
            val source = File(targetContext.filesDir, "private.png").apply { writeText("original") }
            assertThat(editor.canEdit(source), equalTo(false))
        }

    @Test
    fun `preparation detects the extension before sharing the copy`() =
        runBlocking {
            val source = File(targetContext.filesDir, "private.png").apply { writeText("original") }
            assertPreparedType(source, "image/png")
        }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `preparation detects image headers before sharing the copy`() =
        runBlocking {
            val source = File(targetContext.filesDir, "private.bin")
            source.outputStream().use { createBitmap(1, 1).compress(Bitmap.CompressFormat.PNG, 100, it) }
            assertPreparedType(source, "image/png")
        }

    @Test
    fun `preparation falls back when the image type is unknown`() =
        runBlocking {
            val source = File(targetContext.filesDir, "private").apply { writeText("unknown format") }
            assertPreparedType(source, "image/*")
        }

    @Test
    fun `preparation falls back when decoding image headers runs out of memory`() =
        runBlocking {
            val source = File(targetContext.filesDir, "private.bin").apply { writeText("original") }
            mockkStatic(BitmapFactory::class)
            try {
                every { BitmapFactory.decodeStream(any(), null, any()) } throws OutOfMemoryError("Image header decoding failed")

                assertPreparedType(source, "image/*")
            } finally {
                unmockkStatic(BitmapFactory::class)
            }
        }

    @Test
    fun `editor discovery falls back when decoding image headers runs out of memory`() =
        runBlocking {
            val source = File(targetContext.cacheDir, "image.bin").apply { writeText("original") }
            mockkStatic(BitmapFactory::class)
            try {
                every { BitmapFactory.decodeStream(any(), null, any()) } throws OutOfMemoryError("Image header decoding failed")

                assertThat(editor.canEdit(source), equalTo(true))
            } finally {
                unmockkStatic(BitmapFactory::class)
            }
        }

    private suspend fun assertPreparedType(
        source: File,
        mimeType: String,
    ) {
        val prepared = editor.prepare(source)
        try {
            val intent = requireNotNull(prepared.intent.getParcelableExtraCompat<Intent>(Intent.EXTRA_INTENT))
            assertThat(intent.type, equalTo(mimeType))
            assertThat(prepared.file.parentFile, equalTo(targetContext.cacheDir))
            assertThat(prepared.file.readBytes().toList(), equalTo(source.readBytes().toList()))
        } finally {
            editor.clear(prepared.file)
        }
    }
}

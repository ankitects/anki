// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor

import android.content.ClipData
import android.content.ContentProvider
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import androidx.core.view.ContentInfoCompat
import androidx.core.view.ViewCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.NoteEditorActivity
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.destinations.NoteEditorDestination
import com.ichi2.anki.common.utils.ext.pasteImagesAsPng
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowContentResolver
import java.io.ByteArrayOutputStream
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NoteEditorXmlSvgDropTest : RobolectricTest() {
    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    @Test
    fun `XML typed SVG dropped with a photo is inserted with PNG conversion`() = assertBothImages(true)

    @Test
    fun `XML typed SVG dropped with a photo is inserted without PNG conversion`() = assertBothImages(false)

    @Test
    @Config(sdk = [30])
    fun `a mixed AVIF drop on Android 11 preserves the image`() = assertBothAvifImages(pasteAsPng = false)

    @Test
    @Config(sdk = [30])
    fun `a mixed AVIF drop on Android 11 preserves the image with conversion`() = assertBothAvifImages(pasteAsPng = true)

    private fun assertBothAvifImages(
        pasteAsPng: Boolean,
        displayName: String = "drawing.avif",
    ) = requireNotNull(javaClass.getResourceAsStream("/mixed-drop-photo.avif")).use { stream ->
        val avif = stream.readBytes()
        assertNull(MimeTypeMap.getSingleton().getMimeTypeFromExtension("avif"))
        assertNull(BitmapFactory.decodeByteArray(avif, 0, avif.size), "Android 11 has no AVIF bitmap decoder")
        assertBothImages(pasteAsPng, displayName, "application/octet-stream", avif)
    }

    private fun assertBothImages(
        pasteAsPng: Boolean,
        secondName: String = "drawing.svg",
        secondMimeType: String = "application/xml",
        secondBytes: ByteArray =
            """<svg xmlns="http://www.w3.org/2000/svg" width="1" height="1"><rect width="1" height="1"/></svg>""".toByteArray(),
    ) = runTest {
        col.config.pasteImagesAsPng = pasteAsPng
        val note = addBasicNote("Front", "Back")
        val editor =
            startRegularActivity<NoteEditorActivity>(
                NoteEditorDestination.EditNoteFromPreviewer(note.firstCard().id).toIntent(targetContext),
            ).getNoteEditorFragment()
        val provider = mock<ContentProvider>()
        ShadowContentResolver.registerProviderInternal("files", provider)

        fun media(
            name: String,
            type: String,
            bytes: ByteArray,
        ) = "content://files/$name".toUri().also { uri ->
            whenever(provider.getType(uri)).thenReturn(type)
            whenever(provider.query(uri, null, null, null, null)).thenAnswer {
                MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME)).apply { addRow(arrayOf(name)) }
            }
            shadowOf(targetContext.contentResolver).registerInputStreamSupplier(uri) { bytes.inputStream() }
        }
        val jpeg = ByteArrayOutputStream().also { createBitmap(1, 1).compress(Bitmap.CompressFormat.JPEG, 90, it) }
        val photo = media("photo.jpg", "image/jpeg", jpeg.toByteArray())
        val drawing = media(secondName, secondMimeType, secondBytes)
        val clip = ClipData("files", arrayOf("image/jpeg", secondMimeType), ClipData.Item(photo))
        clip.addItem(ClipData.Item(drawing))
        val payload = ContentInfoCompat.Builder(clip, ContentInfoCompat.SOURCE_DRAG_AND_DROP).build()
        val field = editor.editFieldAt(1)!!
        ViewCompat.performReceiveContent(field, payload)
        advanceRobolectricLooper()
        assertContains(field.text.toString(), "<img src=\"photo")
        assertContains(field.text.toString(), "<img src=\"drawing")
        assertEquals(
            listOf(if (pasteAsPng) "png" else "jpg", secondName.substringAfterLast('.')).sorted(),
            col.media.dir
                .listFiles()!!
                .map { it.extension }
                .sorted(),
        )
    }
}

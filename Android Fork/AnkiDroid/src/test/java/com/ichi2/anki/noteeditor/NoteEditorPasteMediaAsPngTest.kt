// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor

import android.content.ClipData
import android.content.ClipDescription
import android.content.ContentProvider
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
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
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowContentResolver
import java.io.ByteArrayOutputStream
import kotlin.test.assertContains
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NoteEditorPasteMediaAsPngTest : RobolectricTest() {
    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    @Test
    fun `dropped audio is inserted when images are pasted as PNG`() =
        assertMediaIsInserted("sound.mp3", "audio/mpeg", ByteArray(64) { it.toByte() }, "Back[sound:")

    @Test
    fun `dropped video is inserted when images are pasted as PNG`() =
        assertMediaIsInserted("clip.mp4", "video/mp4", ByteArray(64) { it.toByte() }, "Back[sound:")

    @Test
    fun `dropped SVG is inserted when images are pasted as PNG`() =
        assertMediaIsInserted(
            "drawing.svg",
            "image/svg+xml",
            """<svg xmlns="http://www.w3.org/2000/svg" width="1" height="1"><rect width="1" height="1"/></svg>""".toByteArray(),
            "Back<img src=\"",
        )

    @Test
    fun `a text document dropped with a photo is not attached as an image`() = assertOnlyPhotoIsImported("text/plain")

    @Test
    fun `a TypeScript document dropped with a photo is skipped`() = assertOnlyPhotoIsImported("text/plain", "notes.ts")

    @Test
    fun `a dropped document with no provider MIME type is skipped`() = assertOnlyPhotoIsImported(null)

    @Test
    fun `a dropped document whose name contains an apostrophe is skipped`() = assertOnlyPhotoIsImported(null, "John's notes.txt")

    @Test
    fun `a document with no type or known extension is skipped`() = assertOnlyPhotoIsImported(null, "notes.withoutamapping")

    @Test
    fun `a document dropped with a recording is not attached as audio`() = assertOnlyRecordingIsImported("notes.txt")

    @Test
    fun `an unknown file dropped with a recording is not attached as audio`() = assertOnlyRecordingIsImported("notes.withoutamapping")

    private fun assertOnlyRecordingIsImported(documentName: String) =
        runTest {
            col.config.pasteImagesAsPng = true
            val note = addBasicNote("Front", "Back")
            val editor =
                startRegularActivity<NoteEditorActivity>(
                    NoteEditorDestination.EditNoteFromPreviewer(note.firstCard().id).toIntent(targetContext),
                ).getNoteEditorFragment()
            val provider = mock<ContentProvider>()
            ShadowContentResolver.registerProviderInternal("files", provider)

            fun file(
                name: String,
                type: String?,
                bytes: ByteArray,
            ) = "content://files/$name".toUri().also { uri ->
                whenever(provider.getType(uri)).thenReturn(type)
                whenever(provider.query(uri, null, null, null, null)).thenAnswer {
                    MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME)).apply { addRow(arrayOf(name)) }
                }
                shadowOf(targetContext.contentResolver).registerInputStreamSupplier(uri) { bytes.inputStream() }
            }
            val audio = file("recording.mp3", "audio/mpeg", byteArrayOf(1, 2, 3))
            val document = file(documentName, null, "Shopping list".toByteArray())
            val clip = ClipData("files", arrayOf("audio/mpeg", "text/plain"), ClipData.Item(audio))
            clip.addItem(ClipData.Item(document))
            val payload = ContentInfoCompat.Builder(clip, ContentInfoCompat.SOURCE_DRAG_AND_DROP).build()
            val field = editor.editFieldAt(1)!!
            ViewCompat.performReceiveContent(field, payload)
            advanceRobolectricLooper()

            assertContains(field.text.toString(), "[sound:recording")
            assertEquals(
                listOf("mp3"),
                col.media.dir
                    .listFiles()!!
                    .map { it.extension }
                    .sorted(),
            )
        }

    @Test
    fun `a document with a single image clip type is not attached as an image`() =
        assertOnlyPhotoIsImported("text/plain", singleClipType = true)

    @Test
    fun `an untyped document in a single image type clip is skipped`() = assertOnlyPhotoIsImported(null, singleClipType = true)

    private fun assertOnlyPhotoIsImported(
        documentMimeType: String?,
        documentFilename: String = "notes.txt",
        singleClipType: Boolean = false,
    ) = runTest {
        col.config.pasteImagesAsPng = true
        val note = addBasicNote("Front", "Back")
        val editor =
            startRegularActivity<NoteEditorActivity>(
                NoteEditorDestination.EditNoteFromPreviewer(note.firstCard().id).toIntent(targetContext),
            ).getNoteEditorFragment()
        val provider = mock<ContentProvider>()
        ShadowContentResolver.registerProviderInternal("files", provider)

        fun file(
            name: String,
            type: String?,
            data: ByteArray,
        ) = "content://files/${Uri.encode(name)}".toUri().also { uri ->
            whenever(provider.getType(uri)).thenReturn(type)
            whenever(provider.query(uri, null, null, null, null)).thenAnswer {
                MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME)).apply { addRow(arrayOf(name)) }
            }
            shadowOf(targetContext.contentResolver).registerInputStreamSupplier(uri) { data.inputStream() }
        }

        val jpeg = ByteArrayOutputStream()
        createBitmap(1, 1).compress(Bitmap.CompressFormat.JPEG, 90, jpeg)
        val photo = file("photo.jpg", "image/jpeg", jpeg.toByteArray())
        val document = file(documentFilename, documentMimeType, "Shopping list".toByteArray())
        val clip =
            ClipData(
                "files",
                if (singleClipType) {
                    arrayOf("image/jpeg")
                } else {
                    arrayOf(
                        "image/jpeg",
                        documentMimeType ?: ClipDescription.MIMETYPE_TEXT_URILIST,
                    )
                },
                ClipData.Item(photo),
            )
        clip.addItem(ClipData.Item(document))
        val payload = ContentInfoCompat.Builder(clip, ContentInfoCompat.SOURCE_DRAG_AND_DROP).build()

        val field = editor.editFieldAt(1)!!
        ViewCompat.performReceiveContent(field, payload)
        advanceRobolectricLooper()

        assertContains(field.text.toString(), "<img src=\"photo")
        assertEquals(
            listOf("png"),
            col.media.dir
                .listFiles()!!
                .map { it.extension }
                .sorted(),
        )
    }

    private fun assertMediaIsInserted(
        fileName: String,
        mimeType: String,
        mediaData: ByteArray,
        expectedField: String,
    ) = runTest {
        col.config.pasteImagesAsPng = true
        val note = addBasicNote("Front", "Back")
        val editor =
            startRegularActivity<NoteEditorActivity>(
                NoteEditorDestination.EditNoteFromPreviewer(note.firstCard().id).toIntent(targetContext),
            ).getNoteEditorFragment()
        val media = "content://clipboard/$fileName".toUri()
        val cursor = MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME)).apply { addRow(arrayOf(fileName)) }
        val provider = mock<ContentProvider>()
        whenever(provider.query(media, null, null, null, null)).thenReturn(cursor)
        ShadowContentResolver.registerProviderInternal("clipboard", provider)
        shadowOf(targetContext.contentResolver).registerInputStreamSupplier(media) { mediaData.inputStream() }
        val payload =
            ContentInfoCompat
                .Builder(
                    ClipData("media", arrayOf(mimeType), ClipData.Item(media)),
                    ContentInfoCompat.SOURCE_DRAG_AND_DROP,
                ).build()

        val field = editor.editFieldAt(1)!!
        ViewCompat.performReceiveContent(field, payload)
        advanceRobolectricLooper()

        assertContains(field.text.toString(), expectedField, message = "media PNG conversion cannot handle must still be inserted")
        val importedMedia =
            col.media.dir
                .listFiles()!!
                .single()
        assertEquals(fileName.substringAfterLast('.'), importedMedia.extension)
    }
}

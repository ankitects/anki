// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor

import android.content.ClipData
import android.content.ContentProvider
import android.content.ContentValues
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.NoteEditorActivity
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.destinations.NoteEditorDestination
import com.ichi2.anki.common.utils.ext.pasteImagesAsPng
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowContentResolver
import java.io.ByteArrayOutputStream
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NoteEditorClipboardRepresentationTest : RobolectricTest() {
    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    @Test
    fun `pasting a generic JPEG advertised by the clipboard`() = assertPastedPhoto("photo.jpg", useTypedProvider = false)

    @Test
    fun `pasting a JPEG representation with a generic filename`() = assertPastedPhoto("photo.bin", useTypedProvider = true)

    @Test
    fun `a JPEG from a provider with a structured MIME type`() =
        assertPastedPhoto("photo.jpg", useTypedProvider = true, providerMimeType = "vnd.android.cursor.item/vnd.test.photo")

    @Test
    fun `pasting an image with several image representations`() =
        assertPastedPhoto("photo.bin", useTypedProvider = true, streamTypes = arrayOf("image/jpeg", "image/png"))

    @Test
    fun `pasting an image from a provider with a text representation`() =
        assertPastedPhoto("photo.bin", useTypedProvider = true, streamTypes = arrayOf("text/plain", "image/jpeg"))

    @Test
    fun `a JPEG URI with an HTML alternative and a generic type`() =
        assertPastedPhoto("photo.bin", useTypedProvider = false, htmlAlternative = true)

    @Test
    fun `pasting a JPEG when the provider reports binary octet stream`() =
        assertPastedPhoto("photo.jpg", useTypedProvider = false, providerMimeType = "binary/octet-stream")

    @Test
    fun `pasting a readable JPEG representation of a PDF provider`() =
        assertPastedPhoto("photo.jpg", useTypedProvider = true, providerMimeType = "application/pdf")

    @Test
    fun `an SVG from an XML provider with PNG conversion`() = assertPastedSvg(pasteAsPng = true)

    @Test
    fun `an SVG from an XML provider without PNG conversion`() = assertPastedSvg(pasteAsPng = false)

    @Test
    fun `octet stream audio is kept`() = assertPastedRecording(pasteAsPng = false)

    @Test
    fun `octet stream audio is kept with PNG conversion`() = assertPastedRecording(pasteAsPng = true)

    @Test
    fun `audio typed as a download is kept`() = assertPastedRecording(pasteAsPng = false, providerMimeType = "application/x-download")

    private fun assertPastedRecording(
        pasteAsPng: Boolean,
        providerMimeType: String = "application/octet-stream",
    ) = runTest {
        col.config.pasteImagesAsPng = pasteAsPng
        val note = addBasicNote("Front", "Back")
        val editor =
            startRegularActivity<NoteEditorActivity>(
                NoteEditorDestination.EditNoteFromPreviewer(note.firstCard().id).toIntent(targetContext),
            ).getNoteEditorFragment()
        val uri = "content://clipboard/recording.bin".toUri()
        val provider = Robolectric.buildContentProvider(JpegRepresentationProvider::class.java).create("clipboard").get()
        provider.mimeType = providerMimeType
        provider.streamMimeTypes = arrayOf("audio/ogg")
        val resolver = targetContext.contentResolver
        val clip = ClipData.newUri(resolver, "recording", uri)
        val recording = requireNotNull(javaClass.getResourceAsStream("/pasted-recording.ogg")).use { it.readBytes() }
        shadowOf(resolver).registerInputStreamSupplier(uri) { recording.inputStream() }

        val field = editor.editFieldAt(1)!!
        field.clipboard!!.setPrimaryClip(clip)
        field.onTextContextMenuItem(android.R.id.paste)
        advanceRobolectricLooper()

        assertContains(field.text.toString(), "[sound:recording")
        assertContentEquals(
            recording,
            col.media.dir
                .listFiles()!!
                .single()
                .readBytes(),
        )
    }

    private fun assertPastedSvg(pasteAsPng: Boolean) =
        runTest {
            col.config.pasteImagesAsPng = pasteAsPng
            val note = addBasicNote("Front", "Back")
            val editor =
                startRegularActivity<NoteEditorActivity>(
                    NoteEditorDestination.EditNoteFromPreviewer(note.firstCard().id).toIntent(targetContext),
                ).getNoteEditorFragment()
            val uri = "content://clipboard/drawing.svg".toUri()
            val provider = Robolectric.buildContentProvider(JpegRepresentationProvider::class.java).create("clipboard").get()
            provider.mimeType = "application/xml"
            provider.streamMimeTypes = arrayOf("image/svg+xml")
            val clip = ClipData.newUri(targetContext.contentResolver, "drawing", uri)
            val svg = """<svg xmlns="http://www.w3.org/2000/svg" width="1" height="1"><rect width="1" height="1"/></svg>""".toByteArray()
            shadowOf(targetContext.contentResolver).registerInputStreamSupplier(uri) { svg.inputStream() }
            val field = editor.editFieldAt(1)!!
            field.clipboard!!.setPrimaryClip(clip)
            field.onTextContextMenuItem(android.R.id.paste)
            advanceRobolectricLooper()
            assertContains(field.text.toString(), "<img src=\"drawing")
            assertEquals(
                "svg",
                col.media.dir
                    .listFiles()!!
                    .single()
                    .extension,
            )
        }

    private fun assertPastedPhoto(
        filename: String,
        useTypedProvider: Boolean,
        providerMimeType: String = "application/octet-stream",
        streamTypes: Array<String> = arrayOf("image/jpeg"),
        htmlAlternative: Boolean = false,
    ) = runTest {
        col.config.pasteImagesAsPng = true
        val note = addBasicNote("Front", "Back")
        val editor =
            startRegularActivity<NoteEditorActivity>(
                NoteEditorDestination.EditNoteFromPreviewer(note.firstCard().id).toIntent(targetContext),
            ).getNoteEditorFragment()
        val uri = "content://clipboard/$filename".toUri()
        val clip =
            if (useTypedProvider) {
                val provider = Robolectric.buildContentProvider(JpegRepresentationProvider::class.java).create("clipboard").get()
                provider.mimeType = providerMimeType
                provider.streamMimeTypes = streamTypes
                ClipData.newUri(targetContext.contentResolver, "photo", uri)
            } else {
                val provider = mock<ContentProvider>()
                whenever(provider.getType(uri)).thenReturn(providerMimeType)
                whenever(provider.query(uri, null, null, null, null)).thenReturn(displayNameCursor(filename))
                ShadowContentResolver.registerProviderInternal("clipboard", provider)
                if (htmlAlternative) {
                    val item = ClipData.Item("photo", "<b>photo</b>", null, uri)
                    ClipData("photo", arrayOf("image/jpeg", "text/html"), item)
                } else {
                    ClipData("photo", arrayOf(providerMimeType, *streamTypes), ClipData.Item(uri))
                }
            }
        assertEquals(streamTypes.size + 1, clip.description.mimeTypeCount)
        val jpeg = ByteArrayOutputStream()
        createBitmap(2, 2).compress(Bitmap.CompressFormat.JPEG, 90, jpeg)
        val resolver = targetContext.contentResolver
        shadowOf(resolver).registerInputStreamSupplier(uri) { jpeg.toByteArray().inputStream() }

        val field = editor.editFieldAt(1)!!
        field.clipboard!!.setPrimaryClip(clip)
        field.onTextContextMenuItem(android.R.id.paste)
        advanceRobolectricLooper()

        assertContains(field.text.toString(), "<img src=\"photo")
        assertEquals(
            "png",
            col.media.dir
                .listFiles()!!
                .single()
                .extension,
        )
    }

    class JpegRepresentationProvider : ContentProvider() {
        var mimeType = "application/octet-stream"
        var streamMimeTypes = arrayOf("image/jpeg")

        override fun onCreate() = true

        override fun getType(uri: Uri) = mimeType

        override fun getStreamTypes(
            uri: Uri,
            mimeTypeFilter: String,
        ) = streamMimeTypes

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ) = displayNameCursor(requireNotNull(uri.lastPathSegment))

        override fun insert(
            uri: Uri,
            values: ContentValues?,
        ) = null

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ) = 0

        override fun delete(
            uri: Uri,
            selection: String?,
            selectionArgs: Array<out String>?,
        ) = 0
    }

    companion object {
        private fun displayNameCursor(filename: String) =
            MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME)).apply { addRow(arrayOf(filename)) }
    }
}

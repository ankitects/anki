// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.ClipDescription
import android.content.ContentProvider
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowContentResolver
import java.io.ByteArrayOutputStream
import java.util.Base64
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class MediaRegistrationTest : RobolectricTest() {
    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    @Test
    fun `pasted image references the normalized media filename`() {
        assertPastedMedia("image.png", "image/png", """<img src="%s">""")
    }

    @Test
    fun `an Ogg recording in a mixed audio drop is kept`() {
        val data = requireNotNull(javaClass.getResourceAsStream("/pasted-recording.ogg")).use { it.readBytes() }
        val uri = stubClipboardMedia("recording.ogg", data, mimeType = "application/ogg")
        val description = ClipDescription("recordings", arrayOf("audio/mpeg", "application/ogg"))

        val tag = MediaRegistration.onPaste(targetContext, uri, description, pasteAsPng = false, showError = {})
        val recording =
            col.media.dir
                .listFiles()!!
                .singleOrNull()

        assertTrue(recording != null, "The Ogg recording must be imported")
        assertContentEquals(data, recording.readBytes())
        assertEquals("[sound:${recording.name}]", tag)
    }

    @Test
    fun `FLAC with an application MIME type is retained in a mixed audio drop`() {
        val data = requireNotNull(javaClass.getResourceAsStream("/mixed-drop-tone.flac")).use { it.readBytes() }
        val uri = stubClipboardMedia("tone.flac", data, mimeType = "application/x-flac")
        val description = ClipDescription("recordings", arrayOf("audio/mpeg", "application/x-flac"))

        val tag = MediaRegistration.onPaste(targetContext, uri, description, pasteAsPng = false, showError = {})
        val recording =
            col.media.dir
                .listFiles()!!
                .singleOrNull()
        assertTrue(recording != null, "The FLAC recording must be imported")
        assertContentEquals(data, recording.readBytes())
        assertEquals("[sound:${recording.name}]", tag)
    }

    @Test
    fun `pasted audio references the normalized media filename`() {
        assertPastedMedia("audio.mp3", "audio/mpeg", "[sound:%s]")
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `pasted image is converted when images are pasted as PNG`() {
        val jpeg = ByteArrayOutputStream()
        createBitmap(1, 1).compress(Bitmap.CompressFormat.JPEG, 100, jpeg)
        val uri = stubClipboardMedia("photo.jpg", jpeg.toByteArray())

        val html =
            MediaRegistration.loadMediaIntoCollection(
                targetContext,
                uri,
                ClipDescription("media", arrayOf("image/jpeg")),
                pasteAsPng = true,
                showError = { error("Unexpected paste error: $it") },
            )

        val importedFile =
            col.media.dir
                .listFiles()!!
                .single()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(importedFile.absolutePath, bounds)
        assertEquals("image/png", bounds.outMimeType)
        assertEquals("png", importedFile.extension)
        assertEquals("""<img src="${importedFile.name}">""", html)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `an animated GIF keeps its frames when pasted as PNG`() =
        assertKeptWhenPastedAsPng(
            "animation.gif",
            "image/gif",
            "R0lGODlhAQABAIAAAP8AAAAA/yH/C05FVFNDQVBFMi4wAwEAAAAh+QQACgAAACwAAAAAAQABAAACAkQBACH5BAAKAAAALAAAAAABAAEAAAICTAEAOw==",
        )

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `an animated PNG keeps its frames when pasted as PNG`() =
        assertKeptWhenPastedAsPng(
            "animation.png",
            "image/png",
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAACGFjVEwAAAACAAAAAPONk3AAAAAaZmNUTAAAAAAAAAABAAAAAQAAAAAAAAAAAAEACgAAWn8w0AAAAAxJREFUeJxj+M/AAAADAQEAyf6S7wAAABpmY1RMAAAAAQAAAAEAAAABAAAAAAAAAAAAAQAKAADBDNoEAAAAEGZkQVQAAAACeJxjYGD4DwABAwEAaL/t9AAAAABJRU5ErkJggg==",
        )

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `an animated WebP keeps its frames when pasted as PNG`() =
        assertKeptWhenPastedAsPng(
            "animation.webp",
            "image/webp",
            "UklGRngAAABXRUJQVlA4WAoAAAACAAAAAAAAAAAAQU5JTQYAAAAAAAAAAABBTk1GIgAAAAAAAAAAAAAAAAAAAGQAAAJWUDhMCgAAAC8AAAAAiP5H/wNBTk1GIgAAAAAAAAAAAAAAAAAAAGQAAAJWUDhMCgAAAC8AAAAAiOh//wM=",
        )

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `audio in a mixed drop is inserted as sound`() {
        val uri = stubClipboardMedia("recording.mp3", byteArrayOf(1, 2, 3), mimeType = "audio/mpeg")
        val clip = ClipDescription("photo and recording", arrayOf("image/jpeg", "audio/mpeg"))

        val tag = MediaRegistration.onPaste(targetContext, uri, clip, pasteAsPng = true, showError = {})
        val recording =
            col.media.dir
                .listFiles()!!
                .singleOrNull()

        assertTrue(recording != null, "The recording must be imported")
        assertEquals("[sound:${recording.name}]", tag)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `audio in a drop with only an image clip type is inserted as sound`() = assertSingleClipAudio("audio/mpeg")

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `untyped audio in an image only clip is inserted as sound`() = assertSingleClipAudio(null)

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `audio without provider type in an image and URI list clip is inserted as sound`() = assertSingleClipAudio(null, uriList = true)

    private fun assertSingleClipAudio(
        providerType: String?,
        uriList: Boolean = false,
    ) {
        val data = byteArrayOf(1, 2, 3)
        val uri = stubClipboardMedia("recording.mp3", data, mimeType = providerType)
        val types = if (uriList) arrayOf("image/jpeg", "text/uri-list") else arrayOf("image/jpeg")
        val tag = MediaRegistration.onPaste(targetContext, uri, ClipDescription("files", types), true, {})
        val recording =
            col.media.dir
                .listFiles()!!
                .singleOrNull()
        assertTrue(recording != null, "The recording must be imported")
        assertContentEquals(data, recording.readBytes())
        assertEquals("[sound:${recording.name}]", tag)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `video in a mixed drop is inserted as sound`() {
        val uri = stubClipboardMedia("clip.mp4", byteArrayOf(1, 2, 3), mimeType = "video/mp4")

        val tag =
            MediaRegistration.onPaste(
                targetContext,
                uri,
                ClipDescription("photo and video", arrayOf("image/jpeg", "video/mp4")),
                pasteAsPng = true,
                showError = { error("Unexpected paste error: $it") },
            )

        val video =
            col.media.dir
                .listFiles()!!
                .single()
        assertEquals("[sound:${video.name}]", tag)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `a recording without a provider type in a mixed drop is inserted as sound`() {
        val uri = stubClipboardMedia("recording.mp3", byteArrayOf(1, 2, 3))

        val tag =
            MediaRegistration.onPaste(
                targetContext,
                uri,
                ClipDescription("photo and recording", arrayOf("image/jpeg", "audio/mpeg")),
                pasteAsPng = true,
                showError = { error("Unexpected paste error: $it") },
            )

        val recording =
            col.media.dir
                .listFiles()!!
                .single()
        assertEquals("[sound:${recording.name}]", tag)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `an SVG with a generic provider type in a mixed drop is inserted as an image`() {
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" width="1" height="1"><rect width="1" height="1"/></svg>"""
        val uri = stubClipboardMedia("drawing.svg", svg.toByteArray(), mimeType = "application/octet-stream")

        val tag =
            MediaRegistration.onPaste(
                targetContext,
                uri,
                ClipDescription("drawings", arrayOf("image/svg+xml", "image/png")),
                pasteAsPng = false,
                showError = { error("Unexpected paste error: $it") },
            )

        val image =
            col.media.dir
                .listFiles()!!
                .single()
        assertEquals("""<img src="${image.name}">""", tag)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `an SVG typed as text XML in a mixed drop is inserted as an image`() {
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" width="1" height="1"><rect width="1" height="1"/></svg>"""
        val uri = stubClipboardMedia("drawing.svg", svg.toByteArray(), mimeType = "text/xml")

        val tag =
            MediaRegistration.onPaste(
                targetContext,
                uri,
                ClipDescription("drawings", arrayOf("image/jpeg", "text/xml")),
                pasteAsPng = false,
                showError = { error("Unexpected paste error: $it") },
            )

        val image =
            col.media.dir
                .listFiles()!!
                .single()
        assertEquals("""<img src="${image.name}">""", tag)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `an image is pasted when its provider reports a generic type`() {
        val jpeg = ByteArrayOutputStream()
        createBitmap(2, 2).compress(Bitmap.CompressFormat.JPEG, 90, jpeg)
        val uri = stubClipboardMedia("photo.jpg", jpeg.toByteArray(), mimeType = "application/octet-stream")

        val tag =
            MediaRegistration.onPaste(
                targetContext,
                uri,
                ClipDescription("photo", arrayOf("image/jpeg")),
                pasteAsPng = true,
                showError = { error("Unexpected paste error: $it") },
            )

        val image =
            col.media.dir
                .listFiles()!!
                .single()
        assertEquals("png", image.extension)
        assertEquals("""<img src="${image.name}">""", tag)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `a recording with an unknown extension in an audio clip is inserted as sound`() {
        val uri = stubClipboardMedia("recording.unknownextension", byteArrayOf(1, 2, 3))

        val tag =
            MediaRegistration.onPaste(
                targetContext,
                uri,
                ClipDescription("recording", arrayOf("audio/mpeg")),
                pasteAsPng = false,
                showError = { error("Unexpected paste error: $it") },
            )

        val recording =
            col.media.dir
                .listFiles()!!
                .single()
        assertEquals("[sound:${recording.name}]", tag)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `a text file without a provider type in an audio clip is skipped`() {
        val uri = stubClipboardMedia("notes.txt", "Shopping list".toByteArray())

        val tag =
            MediaRegistration.onPaste(
                targetContext,
                uri,
                ClipDescription("recording", arrayOf("audio/mpeg")),
                pasteAsPng = false,
                showError = { error("Unexpected paste error: $it") },
            )

        assertEquals(null, tag)
        assertEquals(
            0,
            col.media.dir
                .listFiles()!!
                .size,
        )
    }

    private fun assertPastedMedia(
        filename: String,
        mimeType: String,
        expectedTag: String,
    ) {
        val data = "pasted media".toByteArray()
        // Exceed the backend's filename length limit using characters valid on every OS.
        val originalFilename = "a".repeat(120) + filename
        val uri = stubClipboardMedia(originalFilename, data)

        val html =
            MediaRegistration.loadMediaIntoCollection(
                targetContext,
                uri,
                ClipDescription("media", arrayOf(mimeType)),
                pasteAsPng = false,
                showError = { error("Unexpected paste error: $it") },
            )

        val importedFile =
            col.media.dir
                .listFiles()!!
                .single()
        assertTrue(importedFile.name.length < originalFilename.length, "Expected the backend to shorten the filename")
        assertContentEquals(data, importedFile.readBytes())
        assertEquals(expectedTag.format(importedFile.name), html)
    }

    private fun assertKeptWhenPastedAsPng(
        filename: String,
        mimeType: String,
        base64: String,
    ) {
        val image = Base64.getDecoder().decode(base64)
        val uri = stubClipboardMedia(filename, image, mimeType = mimeType)

        val html =
            MediaRegistration.loadMediaIntoCollection(
                targetContext,
                uri,
                ClipDescription("media", arrayOf(mimeType)),
                pasteAsPng = true,
                showError = { error("Unexpected paste error: $it") },
            )

        val importedFile =
            col.media.dir
                .listFiles()!!
                .single()
        assertEquals(filename.substringAfterLast('.'), importedFile.extension)
        assertContentEquals(image, importedFile.readBytes(), "Converting to PNG keeps only the first frame")
        assertEquals("""<img src="${importedFile.name}">""", html)
    }

    private fun stubClipboardMedia(
        filename: String,
        data: ByteArray,
        mimeType: String? = null,
    ): Uri {
        val uri = "content://clipboard/media".toUri()
        val cursor = MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME)).apply { addRow(arrayOf(filename)) }
        val provider = mock<ContentProvider>()
        whenever(provider.getType(uri)).thenReturn(mimeType)
        whenever(provider.query(uri, null, null, null, null)).thenReturn(cursor)
        ShadowContentResolver.registerProviderInternal("clipboard", provider)
        shadowOf(targetContext.contentResolver).registerInputStreamSupplier(uri) { data.inputStream() }
        return uri
    }
}

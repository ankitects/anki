// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki

import android.content.ClipDescription
import android.content.ContentProvider
import android.database.MatrixCursor
import android.graphics.Bitmap
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
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MediaRegistrationMixedUnknownImageTest : RobolectricTest() {
    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    @Test
    fun `an untyped JPEG in an audio clip becomes a PNG image`() {
        val uri = "content://files/photo.jpg".toUri()
        val provider = mock<ContentProvider>()
        whenever(provider.query(uri, null, null, null, null)).thenAnswer {
            MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME)).apply { addRow(arrayOf("photo.jpg")) }
        }
        ShadowContentResolver.registerProviderInternal("files", provider)
        val jpeg = ByteArrayOutputStream().also { createBitmap(2, 2).compress(Bitmap.CompressFormat.JPEG, 90, it) }
        shadowOf(targetContext.contentResolver).registerInputStreamSupplier(uri) { jpeg.toByteArray().inputStream() }
        val clip = ClipDescription("files", arrayOf("audio/mpeg", "text/uri-list"))
        val tag = MediaRegistration.onPaste(targetContext, uri, clip, pasteAsPng = true, showError = {})
        val image =
            col.media.dir
                .listFiles()!!
                .single()
        assertEquals("png", image.extension)
        assertEquals("""<img src="${image.name}">""", tag)
    }
}

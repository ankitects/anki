// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.net.Uri
import android.os.Process
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.BuildConfig
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.FlashCardsContract.AnkiMedia
import com.ichi2.anki.RobolectricTest
import com.ichi2.testutils.grantPermissions
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowBinder
import org.robolectric.shadows.ShadowContentResolver
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class CardContentProviderMediaTest : RobolectricTest() {
    private lateinit var provider: CardContentProvider
    private val authority = "${BuildConfig.APPLICATION_ID}.flashcards"
    private val sourceUri = "content://media-source/cover.png".toUri()

    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    @Before
    fun setUpProvider() {
        CollectionManager.setColForTests(col)
        grantPermissions("${BuildConfig.APPLICATION_ID}.permission.READ_WRITE_DATABASE")
        provider = Robolectric.buildContentProvider(CardContentProvider::class.java).create(authority).get()
        ShadowBinder.setCallingPid(Process.myPid() + 1)

        val sourceProvider = mock<ContentProvider>()
        whenever(sourceProvider.getType(sourceUri)).thenReturn("image/png")
        ShadowContentResolver.registerProviderInternal(sourceUri.authority!!, sourceProvider)
    }

    @Test
    fun `repeated media imports reuse the existing file`() {
        val first = assertNotNull(insertMedia(preferredName = "cover", contents = "cover contents"))
        val second = assertNotNull(insertMedia(preferredName = "cover", contents = "cover contents"))

        assertEquals(first, second)
        assertEquals("cover.png", mediaFile(first).name)
        assertEquals("cover contents", mediaFile(first).readText())
        assertEquals(
            1,
            col.media.dir
                .listFiles()!!
                .size,
        )
    }

    @Test
    fun `media imports preserve different contents with the same preferred name`() {
        val first = assertNotNull(insertMedia(preferredName = "cover", contents = "original cover"))
        val second = assertNotNull(insertMedia(preferredName = "cover", contents = "changed cover"))

        assertNotEquals(first, second)
        assertEquals("original cover", mediaFile(first).readText())
        assertEquals("changed cover", mediaFile(second).readText())
        assertEquals(
            2,
            col.media.dir
                .listFiles()!!
                .size,
        )
    }

    @Test
    fun `repeated conflicting media imports reuse the renamed file`() {
        // 4 cover.png imports:
        // success
        val original = assertNotNull(insertMedia(preferredName = "cover", contents = "original cover"))
        // Different contents - another name is selected
        val conflicting = assertNotNull(insertMedia(preferredName = "cover", contents = "changed cover"))
        // Duplicate of the second - reuses the renamed file
        val repeated = assertNotNull(insertMedia(preferredName = "cover", contents = "changed cover"))
        // Duplicate of the first - reuses the original file
        val repeatedOriginal = assertNotNull(insertMedia(preferredName = "cover", contents = "original cover"))

        // Both contents are preserved, with only two media files.
        assertEquals(conflicting, repeated)
        assertEquals(original, repeatedOriginal)
        assertEquals("changed cover", mediaFile(repeated).readText())
        assertEquals("original cover", mediaFile(repeatedOriginal).readText())
        assertEquals(
            2,
            col.media.dir
                .listFiles()!!
                .size,
        )
    }

    @Test
    fun `empty media imports fail without creating media`() {
        assertNull(insertMedia(contents = "", preferredName = "cover"))
        assertEquals(
            0,
            col.media.dir
                .listFiles()!!
                .size,
        )
    }

    /** Imports [contents] from the PNG source URI with [preferredName] and returns the provider's URI. */
    private fun insertMedia(
        contents: String,
        @Suppress("SameParameterValue") preferredName: String,
    ): Uri? {
        shadowOf(targetContext.contentResolver).registerInputStreamSupplier(sourceUri) { contents.byteInputStream() }
        val values =
            ContentValues().apply {
                put(AnkiMedia.FILE_URI, sourceUri.toString())
                put(AnkiMedia.PREFERRED_NAME, preferredName)
            }
        return provider.insert("content://$authority/media".toUri(), values)
    }

    private fun mediaFile(uri: Uri): File {
        val filename = File(assertNotNull(uri.path)).name
        return File(col.media.dir, filename)
    }
}

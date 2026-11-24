// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import android.content.Context
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.EmptyApplicationCategory
import com.ichi2.utils.openInputStreamSafe
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.util.ReflectionHelpers
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class, shadows = [ShadowFileProviderPathStrategy::class])
@Category(EmptyApplicationCategory::class)
class FileProviderHostPathsTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val authority: String get() = "${context.packageName}.apkgfileprovider"

    @Before
    fun setUp() {
        // Robolectric gives each test a new cache directory, but AndroidX retains the old roots.
        ReflectionHelpers.getStaticField<MutableMap<String, Any>>(FileProvider::class.java, "sCache").clear()
    }

    @Test
    fun `nested cache files can be shared and opened`() {
        val directory = File(context.cacheDir, "nested images").apply { mkdirs() }
        val file = File(directory, "image #1.png").apply { writeText("original") }
        val uri = FileProvider.getUriForFile(context, authority, file)

        val strategies = ReflectionHelpers.getStaticField<Map<String, Any>>(FileProvider::class.java, "sCache")
        assertIs<ShadowFileProviderPathStrategy>(Shadow.extract<Any>(strategies.getValue(authority)))
        val contents =
            context.contentResolver
                .openInputStreamSafe(uri)!!
                .bufferedReader()
                .use { it.readText() }
        assertEquals("original", contents)
        context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write("edited".toByteArray()) }
        assertEquals("edited", file.readText())
    }

    @Test
    fun `the root itself and a directory with the same prefix cannot be shared`() {
        val root = context.cacheDir
        val sibling = File(root.parentFile, "${root.name}-private/image.png")
        for (file in listOf(root, sibling)) {
            assertFailsWith<IllegalArgumentException> { FileProvider.getUriForFile(context, authority, file) }
        }
    }

    @Test
    fun `a URI cannot traverse outside its configured root`() {
        val file = File(context.cacheDir, "image.png").apply { writeText("original") }
        val uri = FileProvider.getUriForFile(context, authority, file)
        val escaped =
            uri
                .buildUpon()
                .encodedPath("/${uri.pathSegments.first()}/%2E%2E/${context.cacheDir.name}-private/image.png")
                .build()

        assertFailsWith<SecurityException> { context.contentResolver.openInputStreamSafe(escaped) }
    }
}

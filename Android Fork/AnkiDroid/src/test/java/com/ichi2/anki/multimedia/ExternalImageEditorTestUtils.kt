// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.multimedia

import android.content.ComponentName
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ProviderInfo
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.util.ReflectionHelpers
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO

internal fun editorResultImageBytes(format: String = "png"): ByteArray =
    ByteArrayOutputStream().use { output ->
        check(ImageIO.write(BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), format, output))
        output.toByteArray()
    }

internal fun clearFileProviderRoots() {
    // Robolectric gives each test a new cache directory, but AndroidX retains the old roots.
    ReflectionHelpers.getStaticField<MutableMap<String, Any>>(FileProvider::class.java, "sCache").clear()
}

internal fun Context.registerExternalImageEditor(): ComponentName {
    val editor = ComponentName("com.ichi2.testutils.editor", "ImageEditorActivity")
    shadowOf(packageManager).apply {
        addActivityIfNotPresent(editor).exported = true
        addIntentFilterForActivity(
            editor,
            IntentFilter(Intent.ACTION_EDIT).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addDataScheme("content")
                addDataType("image/png")
            },
        )
    }
    return editor
}

/** Models an editor's output using a separate authority from AnkiDroid's FileProvider. */
class EditorResultProvider : ContentProvider() {
    private lateinit var file: File
    private lateinit var displayName: String
    private var mimeType: String? = null
    var openThread: Thread? = null
    var beforeOpen: () -> Unit = {}

    fun result(
        file: File,
        displayName: String = file.name,
        mimeType: String? = if (file.extension == "jpg") "image/jpeg" else "image/png",
    ): Uri {
        this.file = file
        this.displayName = displayName
        this.mimeType = mimeType
        return "content://com.ichi2.testutils.editor.results/result".toUri()
    }

    override fun onCreate() = true

    override fun getType(uri: Uri): String? = mimeType

    override fun openFile(
        uri: Uri,
        mode: String,
    ): ParcelFileDescriptor {
        openThread = Thread.currentThread()
        beforeOpen()
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ) = MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME)).apply { addRow(arrayOf(displayName)) }

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = throw UnsupportedOperationException()

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ) = throw UnsupportedOperationException()

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ) = throw UnsupportedOperationException()

    companion object {
        fun register(): EditorResultProvider =
            Robolectric
                .buildContentProvider(EditorResultProvider::class.java)
                .create(
                    ProviderInfo().apply {
                        authority = "com.ichi2.testutils.editor.results"
                        packageName = "com.ichi2.testutils.editor"
                        exported = true
                    },
                ).get()
    }
}

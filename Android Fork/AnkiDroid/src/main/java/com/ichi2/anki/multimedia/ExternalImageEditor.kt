// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.multimedia

import android.app.Activity
import android.content.ClipData
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.activity.result.ActivityResult
import androidx.core.content.FileProvider
import com.ichi2.anki.utils.runWithOOMCheck
import com.ichi2.utils.openInputStreamSafe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.apache.commons.io.FileUtils
import timber.log.Timber
import java.io.File
import java.io.IOException

/** File operations for external editing. Only the working copy is writable by the editor. */
internal class ExternalImageEditor(
    private val context: Context,
    private val cacheDirectory: File,
) {
    /** A writable image copy and the chooser intent used to launch its external editor. */
    data class Prepared(
        val file: File,
        val intent: Intent,
    )

    /** Return whether the source can be shared with an available external image editor. */
    suspend fun canEdit(source: File): Boolean =
        withContext(Dispatchers.IO) {
            try {
                createEditIntent(source, imageMimeType(source)).resolveActivity(context.packageManager) != null
            } catch (e: IllegalArgumentException) {
                Timber.w(e, "Unable to share image with an external editor")
                false
            }
        }

    /** Create a writable copy of the source and a chooser intent for editing it externally. */
    suspend fun prepare(source: File): Prepared {
        var copy: File? = null
        try {
            return withContext(Dispatchers.IO) {
                val mimeType = imageMimeType(source)
                val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: source.extension
                val file = File.createTempFile("img", ".$extension", cacheDirectory)
                copy = file
                source.copyTo(file, overwrite = true)
                Prepared(
                    file,
                    Intent.createChooser(createEditIntent(file, mimeType), null),
                )
            }
        } catch (e: Exception) {
            withContext(NonCancellable + Dispatchers.IO) { copy?.delete() }
            if (e is CancellationException) throw e
            throw IOException("Unable to prepare image for external editing", e)
        }
    }

    private fun uriForFile(file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.apkgfileprovider", file)

    private fun imageMimeType(file: File): String {
        MimeTypeMap
            .getSingleton()
            .getMimeTypeFromExtension(file.extension.lowercase())
            ?.takeIf { it.startsWith("image/") }
            ?.let { return it }
        return imageMimeTypeFromHeader(file) ?: "image/*"
    }

    /** Read the MIME type from image headers, or return null if reading or decoding fails or runs out of memory. */
    private fun imageMimeTypeFromHeader(file: File): String? =
        runWithOOMCheck(
            action = {
                try {
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    file.inputStream().use { BitmapFactory.decodeStream(it, null, options) }
                    options.outMimeType?.takeIf { options.outWidth > 0 && options.outHeight > 0 }
                } catch (e: IOException) {
                    Timber.w(e, "Unable to read image headers")
                    null
                }
            },
            onError = { Timber.w(it, "Unable to read image headers") },
        )

    private fun createEditIntent(
        file: File,
        mimeType: String,
    ): Intent =
        Intent(Intent.ACTION_EDIT).apply {
            val uri = uriForFile(file)
            setDataAndType(uri, mimeType)
            // Google Photos requires an explicit output URI for FileProvider images.
            putExtra(MediaStore.EXTRA_OUTPUT, uri)
            clipData = ClipData.newRawUri("image", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }

    /**
     * Import into a unique file, or return null on cancellation or unchanged contents.
     *
     * @param original the unmodified source image used to detect unchanged results.
     * @param copy the writable copy shared with the editor.
     * @param result the activity result from the editor, optionally containing an output URI.
     */
    suspend fun readResult(
        original: File,
        copy: File,
        result: ActivityResult,
    ): File? {
        // An editor may write before the user cancels; only a successful result applies those changes.
        if (result.resultCode != Activity.RESULT_OK) return null
        var accepted: File? = null
        try {
            return withContext(Dispatchers.IO) {
                // Some editors modify the supplied URI; others return a new content URI.
                val returnedUri = result.data?.data?.takeUnless { it == uriForFile(copy) }
                returnedUri?.let(::validateReturnedUri)
                // Never trust provider display names as paths or allow them to overwrite an existing image.
                val destination = File.createTempFile("img", null, cacheDirectory)
                accepted = destination
                if (returnedUri == null) {
                    // Detach the accepted image from the file the external app could still write to.
                    copy.copyTo(destination, overwrite = true)
                } else {
                    val input =
                        context.contentResolver.openInputStreamSafe(returnedUri)
                            ?: throw IOException("Unable to open externally edited image")
                    input.use { source -> destination.outputStream().use { source.copyTo(it) } }
                }
                if (destination.length() == 0L) throw IOException("External editor returned an empty image")
                if (FileUtils.contentEquals(original, destination)) {
                    destination.delete()
                    null
                } else {
                    // Validate the copied bytes; provider MIME types and the working extension can be wrong.
                    val mimeType =
                        imageMimeTypeFromHeader(destination)?.takeIf { it.startsWith("image/") }
                            ?: throw IOException("External editor did not return a decodable image")
                    val extension =
                        MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
                            ?: throw IOException("External editor returned an unsupported image type: $mimeType")
                    val image = File.createTempFile("img", ".$extension", cacheDirectory)
                    if (!destination.renameTo(image)) {
                        image.delete()
                        throw IOException("Unable to rename externally edited image")
                    }
                    accepted = image
                    image
                }
            }
        } catch (e: Exception) {
            withContext(NonCancellable + Dispatchers.IO) { accepted?.delete() }
            if (e is CancellationException) throw e
            throw IOException("Unable to read externally edited image", e)
        }
    }

    private fun validateReturnedUri(uri: Uri) {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) {
            throw IOException("External editor returned an unsupported URI")
        }
        // Do not use our privileges to read an unshared file from our own providers.
        val authority = uri.authority?.substringAfterLast('@')
        if (authority != null && context.packageManager.resolveContentProvider(authority, 0)?.packageName == context.packageName) {
            throw IOException("External editor returned an unshared internal URI")
        }
    }

    /** Revoke access even when the owning screen was closed during an operation. */
    suspend fun clear(file: File?) =
        withContext(NonCancellable + Dispatchers.IO) {
            file?.let {
                context.revokeUriPermission(uriForFile(it), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                it.delete()
            }
            Unit
        }
}

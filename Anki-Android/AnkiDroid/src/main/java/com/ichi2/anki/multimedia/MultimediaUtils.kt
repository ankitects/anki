/*
 * Copyright (c) 2024 Ashish Yadav <mailtoashish693@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.multimedia

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.CancellationSignal
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.content.ContentResolverCompat
import androidx.core.net.toUri
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.common.time.getTimestamp
import timber.log.Timber
import java.io.File
import java.io.IOException

object MultimediaUtils {
    /**
     * Creates a new temporary image file in the specified cache directory.
     *
     * @param extension The desired file extension (default: "jpg").
     * @return The newly created image file.
     * @throws IOException If an error occurs while creating the file.
     */
    @Throws(IOException::class)
    fun createNewCacheImageFile(
        extension: String = "jpg",
        directory: String?,
    ): File {
        val storageDir = File(directory!!)
        return File.createTempFile("img", ".$extension", storageDir)
    }

    const val IMAGE_SAVE_MAX_WIDTH = 1920

    /**
     * https://cs.android.com/android/platform/superproject/+/master:packages/providers/DownloadProvider/src/com/android/providers/downloads/MediaStoreDownloadsHelper.java;l=24
     */
    private const val MEDIASTORE_DOWNLOAD_FILE_PREFIX = "msf:"

    /**
     * The default prefix to raw file documentIds
     *
     * https://cs.android.com/android/platform/superproject/+/master:packages/providers/DownloadProvider/src/com/android/providers/downloads/RawDocumentsHelper.java;l=35?q=%5C%22raw:%5C%22&ss=android%2Fplatform%2Fsuperproject
     */
    private const val RAW_DOCUMENTS_FILE_PREFIX = "raw:"

    /** 100MB in bytes upstream limit https://faqs.ankiweb.net/are-there-limits-on-file-sizes-on-ankiweb.html **/
    const val IMAGE_LIMIT = 1024 * 1024 * 100

    /**
     * Get image name based on uri and selection args
     *
     * @return Display name of file identified by uri (null if does not exist)
     */
    fun getImageNameFromUri(
        context: Context,
        uri: Uri,
    ): String? =
        try {
            Timber.d("getImageNameFromUri() URI: %s", uri)
            var imageName: String? = null
            if (DocumentsContract.isDocumentUri(context, uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                if ("com.android.providers.media.documents" == uri.authority) {
                    val id = docId.split(":").toTypedArray()[1]
                    val selection = MediaStore.Images.Media._ID + "=" + id
                    imageName =
                        getImageNameFromContentResolver(
                            context,
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            selection,
                        )
                } else if ("com.android.providers.downloads.documents" == uri.authority) {
                    imageName =
                        when {
                            // `msf:1000034860` can be handled by using the URI
                            docId.startsWith(MEDIASTORE_DOWNLOAD_FILE_PREFIX) -> {
                                getImageNameFromContentResolver(context, uri, null)
                            }

                            // raw:/storage/emulated/0/Download/pexels-pixabay-36717.jpg
                            docId.startsWith(RAW_DOCUMENTS_FILE_PREFIX) -> {
                                docId
                                    .substring(RAW_DOCUMENTS_FILE_PREFIX.length)
                                    .split("/")
                                    .toTypedArray()
                                    .last()
                            }

                            docId.toLongOrNull() != null -> {
                                val contentUri =
                                    ContentUris.withAppendedId(
                                        "content://downloads/public_downloads".toUri(),
                                        docId.toLong(),
                                    )
                                getImageNameFromContentResolver(context, contentUri, null)
                            }

                            else -> {
                                CrashReportService.sendExceptionReport(
                                    message = "Failed to get fileName from providers.downloads.documents",
                                    origin = "getImageNameFromUri",
                                )
                                null
                            }
                        }
                }
            } else if ("content".equals(uri.scheme, ignoreCase = true)) {
                imageName = getImageNameFromContentResolver(context, uri, null)
            } else if ("file".equals(uri.scheme, ignoreCase = true)) {
                if (uri.path != null) {
                    imageName = uri.path!!.split("/").last()
                }
            }
            Timber.d("getImageNameFromUri() returning name %s", imageName)
            imageName
        } catch (e: Exception) {
            Timber.w(e)
            CrashReportService.sendExceptionReport(e, "getImageNameFromUri")
            null
        }

    /**
     * Get image name based on uri and selection args
     *
     * @return Display name of file identified by uri (null if does not exist)
     */
    private fun getImageNameFromContentResolver(
        context: Context,
        uri: Uri,
        selection: String?,
    ): String? {
        Timber.d("getImageNameFromContentResolver() %s", uri)
        val filePathColumns = arrayOf(MediaStore.MediaColumns.DISPLAY_NAME)
        val signal: CancellationSignal? = null // needed to fix the type to non-deprecated android.os.CancellationSignal for use below
        ContentResolverCompat.query(context.contentResolver, uri, filePathColumns, selection, null, null, signal).use { cursor ->

            if (cursor == null) {
                Timber.w("getImageNameFromContentResolver() cursor was null")
                return null
            }

            if (!cursor.moveToFirst()) {
                // TODO: #5909, it would be best to instrument this to see if we can fix the failure
                Timber.w("getImageNameFromContentResolver() cursor had no data")
                return null
            }

            val imageName = cursor.getString(cursor.getColumnIndex(filePathColumns[0]))
            Timber.d("getImageNameFromContentResolver() decoded image name %s", imageName)
            return imageName
        }
    }

    /**
     * Creates a temporary image file in the external files directory.
     *
     * This function generates a temporary image file with a unique name based on the current timestamp.
     * The file is created in the external files directory specific to the pictures.
     *
     * @receiver Context The context used to access the external files directory.
     * @return File The created temporary image file.
     * @throws IOException If an error occurs while creating the file.
     */
    fun Context.createImageFile(): File {
        val currentDateTime = getTimestamp(TimeManager.time)
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "ANKIDROID_$currentDateTime",
            ".jpg",
            storageDir,
        )
    }

    /**
     * Creates a cached file with the specified filename and directory.
     *
     * This function creates a file with the given filename in the specified directory.
     * The file is marked to be deleted when the virtual machine terminates.
     *
     * @param filename The name of the file to be created.
     * @param directory The directory where the file will be created. If null, the default directory is used.
     * @return File The created file.
     * @throws IOException If an error occurs while creating the file.
     */
    @Throws(IOException::class)
    fun createCachedFile(
        filename: String,
        directory: String?,
    ) = File(directory, filename).apply {
        deleteOnExit()
    }
}

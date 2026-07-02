/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.utils

import android.content.ContentResolver
import android.database.sqlite.SQLiteException
import android.net.Uri
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.annotation.CheckResult
import androidx.core.net.toUri
import timber.log.Timber

object ContentResolverUtil {
    /** Obtains the filename from the url. Throws if all methods return exception  */
    @CheckResult
    fun getFileName(
        contentResolver: ContentResolver,
        uri: Uri,
    ): String {
        try {
            val filename = getFilenameViaDisplayName(contentResolver, uri)
            if (filename != null) {
                return filename
            }
        } catch (e: Exception) {
            Timber.w(e, "getFilenameViaDisplayName")
        }

        // let this one throw
        val filename = getFilenameViaMimeType(contentResolver, uri)
        if (filename != null) {
            return filename
        }
        throw IllegalStateException("Unable to obtain valid filename from uri: $uri")
    }

    @CheckResult
    private fun getFilenameViaMimeType(
        contentResolver: ContentResolver,
        uri: Uri,
    ): String? {
        // value: "png" when testing
        var extension: String? = null

        // Check uri format to avoid null
        if (uri.scheme != null && uri.scheme == ContentResolver.SCHEME_CONTENT) {
            // If scheme is a content
            val mime = MimeTypeMap.getSingleton()
            extension = mime.getExtensionFromMimeType(contentResolver.getType(uri))
        } else {
            // If scheme is a File
            // This will replace white spaces with %20 and also other special characters. This will avoid returning null values on file name with spaces and special characters.
            if (uri.path != null) {
                extension = AssetHelper.getFileExtensionFromFilePath(uri.path as String)
            }
        }
        return if (extension == null) {
            null
        } else {
            "image.$extension"
        }
    }

    @CheckResult
    private fun getFilenameViaDisplayName(
        contentResolver: ContentResolver,
        uri: Uri,
    ): String? {
        // 7748: android.database.sqlite.SQLiteException: no such column: _display_name (code 1 SQLITE_ERROR[1]): ...
        try {
            contentResolver.query(uri, null, null, null, null).use { c ->
                if (c == null) {
                    return null
                }
                c.moveToNext()
                val mediaIndex = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                if (mediaIndex != -1) {
                    return c.getString(mediaIndex)
                }

                // use `_data` column label directly, MediaStore.MediaColumns.DATA is deprecated in API29+ but
                // the recommended MediaStore.MediaColumns.RELATIVE_PATH does not appear to be available
                // content uri contains only id and _data columns in samsung clipboard and not media columns
                // gboard contains media columns and works with MediaStore.MediaColumns.DISPLAY_NAME
                val dataIndex = c.getColumnIndexOrThrow("_data")
                val fileUri = c.getString(dataIndex).toUri()
                return fileUri.lastPathSegment
            }
        } catch (e: SQLiteException) {
            Timber.w(e, "getFilenameViaDisplayName ContentResolver query failed.")
        }
        return null
    }
}

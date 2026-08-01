// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.compat

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.Paint
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.util.Size
import androidx.annotation.RequiresApi
import com.ichi2.anki.common.time.TimeManager
import java.io.File
import java.io.IOException

/** Implementation of [Compat] for SDK level 29  */
@RequiresApi(29)
open class CompatV29 : CompatV26() {
    override fun hasVideoThumbnail(path: String): Boolean? {
        return try {
            ThumbnailUtils.createVideoThumbnail(File(path), THUMBNAIL_MINI_KIND, null)
            // createVideoThumbnail throws an exception if it's null
            true
        } catch (e: IOException) {
            // The default for audio is an IOException, so don't log it
            // A log line is still produced:
            // E/MediaMetadataRetrieverJNI: getEmbeddedPicture: Call to getEmbeddedPicture failed
            if (e.message == "Failed to create thumbnail") return false
            null
        } catch (e: Exception) {
            // unexpected exception
            null
        }
    }

    override fun saveImage(
        context: Context,
        bitmap: Bitmap,
        baseFileName: String,
        extension: String,
        format: Bitmap.CompressFormat,
        quality: Int,
    ): Uri {
        val imagesCollection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val destDir = File(Environment.DIRECTORY_PICTURES, "AnkiDroid")
        val date = TimeManager.time.intTimeMS()

        val newImage =
            ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "$date.$extension")
                put(MediaStore.MediaColumns.MIME_TYPE, "image/$extension")
                put(MediaStore.MediaColumns.DATE_ADDED, date)
                put(MediaStore.MediaColumns.DATE_MODIFIED, date)
                put(MediaStore.MediaColumns.SIZE, bitmap.byteCount)
                put(MediaStore.MediaColumns.WIDTH, bitmap.width)
                put(MediaStore.MediaColumns.HEIGHT, bitmap.height)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$destDir${File.separator}")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        val newImageUri = context.contentResolver.insert(imagesCollection, newImage)
        context.contentResolver.openOutputStream(newImageUri!!).use {
            if (it != null) {
                bitmap.compress(format, quality, it)
            }
        }
        newImage.clear()
        newImage.put(MediaStore.Images.Media.IS_PENDING, 0)
        context.contentResolver.update(newImageUri, newImage, null, null)
        return newImageUri
    }

    override fun isUsingSystemGestureNavigation(
        context: Context,
        defaultValue: Boolean,
    ): Boolean {
        val defaultMode = if (defaultValue) 2 else 0
        return Settings.Secure.getInt(context.contentResolver, "navigation_mode", defaultMode) == 2
    }

    override fun setDstOutBlend(paint: Paint) {
        paint.blendMode = BlendMode.DST_OUT
    }

    companion object {
        // obtained from AOSP source
        private val THUMBNAIL_MINI_KIND = Size(512, 384)
    }
}

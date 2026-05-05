// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2017 Profpatsch <mail@profpatsch.de>

package com.ichi2.anki.compat

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.media.MediaRecorder
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Vibrator
import android.provider.MediaStore
import android.view.View
import androidx.annotation.AnimRes
import androidx.appcompat.widget.TooltipCompat
import com.ichi2.anki.common.utils.annotation.KotlinCleanup
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.Serializable
import kotlin.time.Duration

/** Baseline implementation of [Compat], for API V23. Check [Compat] for more detail.  */
@KotlinCleanup("add extension method logging file.delete() failure" + "Fix Deprecation")
@Suppress("Deprecation")
open class BaseCompat : Compat {
    // Until API26, tooltips cannot be defined declaratively in layouts
    override fun setTooltipTextByContentDescription(view: View) {
        TooltipCompat.setTooltipText(view, view.contentDescription)
    }

    override fun overrideTransition(
        activity: Activity,
        @AnimRes enter: Int,
        @AnimRes exit: Int,
        open: Boolean,
    ) {
        activity.overridePendingTransition(
            enter,
            exit,
        )
    }

    // Until API 26 just specify time, after that specify effect also
    override fun vibrate(
        context: Context,
        duration: Duration,
        @VibrationUsage usage: Int,
    ) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator?
        vibratorManager?.vibrate(duration.inWholeMilliseconds)
    }

    // Until API 26 do the copy using streams
    @Throws(IOException::class)
    override fun copyFile(
        source: String,
        target: String,
    ) {
        try {
            FileInputStream(source).use { fileInputStream -> copyFile(fileInputStream, target) }
        } catch (e: IOException) {
            Timber.e(e, "copyFile() error copying source %s", source)
            throw e
        }
    }

    // Until API 26 do the copy using streams
    @Throws(IOException::class)
    override fun copyFile(
        source: String,
        target: OutputStream,
    ): Long {
        var count: Long
        try {
            FileInputStream(source).use { fileInputStream -> count = copyFile(fileInputStream, target) }
        } catch (e: IOException) {
            Timber.e(e, "copyFile() error copying source %s", source)
            throw e
        }
        return count
    }

    // Until API 26 do the copy using streams
    @Throws(IOException::class)
    override fun copyFile(
        source: InputStream,
        target: String,
    ): Long {
        var bytesCopied: Long
        try {
            FileOutputStream(target).use { targetStream -> bytesCopied = copyFile(source, targetStream) }
        } catch (ioe: IOException) {
            Timber.e(ioe, "Error while copying to file %s", target)
            throw ioe
        }
        return bytesCopied
    }

    // Internal implementation under the API26 copyFile APIs
    @Throws(IOException::class)
    private fun copyFile(
        source: InputStream,
        target: OutputStream,
    ): Long {
        // balance memory and performance, it appears 32k is the best trade-off
        // https://stackoverflow.com/questions/10143731/android-optimal-buffer-size
        val buffer = ByteArray(1024 * 32)
        var count: Long = 0
        var n: Int
        @KotlinCleanup("This code feels hard to read, Improve readability")
        while (source.read(buffer).also { n = it } != -1) {
            target.write(buffer, 0, n)
            count += n.toLong()
        }
        target.flush()
        return count
    }

    // Until API 26
    /* This method actually read the full content of the directory.
     * It is linear in time and space in the number of file and directory in the directory.
     * However, hasNext and next should be constant in time and space. */
    @Throws(IOException::class)
    override fun contentOfDirectory(directory: File): FileStream {
        val paths = directory.listFiles()
        if (paths == null) {
            if (!directory.exists()) {
                throw FileNotFoundException(directory.path)
            }
            throw IOException(
                "Directory " + directory.path + "'s file can not be listed. " +
                    "Probable cause are that it's not a directory " +
                    "(which violates the method's assumption) or a permission issue.",
            )
        }
        val length = paths.size
        return object : FileStream {
            override fun close() {
                // No op. Nothing to close here.
            }

            private var mOrd = 0

            override operator fun hasNext(): Boolean = mOrd < length

            override operator fun next(): File = paths[mOrd++]
        }
    }

    // Until API 26
    @Throws(IOException::class)
    override fun deleteFile(file: File) {
        if (!file.delete()) {
            if (!file.exists()) {
                throw FileNotFoundException(file.canonicalPath)
            }
            throw IOException("Unable to delete: " + file.canonicalPath)
        }
    }

    // Until API 26
    @Throws(IOException::class)
    override fun createDirectories(directory: File) {
        if (directory.exists()) {
            if (!directory.isDirectory) {
                throw IOException("$directory is not a directory")
            }
            return
        }
        if (!directory.mkdirs()) {
            throw IOException("Failed to create $directory")
        }
    }

    override val webpLossyFormat: Bitmap.CompressFormat = Bitmap.CompressFormat.WEBP

    // Until API 29
    @Throws(FileNotFoundException::class)
    override fun saveImage(
        context: Context,
        bitmap: Bitmap,
        baseFileName: String,
        extension: String,
        format: Bitmap.CompressFormat,
        quality: Int,
    ): Uri {
        val pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val ankiDroidDirectory = File(pictures, "AnkiDroid")
        if (!ankiDroidDirectory.exists()) {
            ankiDroidDirectory.mkdirs()
        }
        val imageFile = File(ankiDroidDirectory, "$baseFileName.$extension")
        bitmap.compress(format, quality, FileOutputStream(imageFile))
        return Uri.fromFile(imageFile)
    }

    // Until API 29
    override fun hasVideoThumbnail(path: String): Boolean? =
        try {
            ThumbnailUtils.createVideoThumbnail(path, MediaStore.Images.Thumbnails.MINI_KIND) != null
        } catch (e: Exception) {
            null
        }

    // Until API31 the MediaRecorder constructor was default, ignoring the Context
    override fun getMediaRecorder(context: Context): MediaRecorder = MediaRecorder()

    // Until API 33
    override fun resolveActivity(
        packageManager: PackageManager,
        intent: Intent,
        flags: ResolveInfoFlagsCompat,
    ): ResolveInfo? = packageManager.resolveActivity(intent, flags.value.toInt())

    // Until API 33
    override fun resolveService(
        packageManager: PackageManager,
        intent: Intent,
        flags: ResolveInfoFlagsCompat,
    ): ResolveInfo? = packageManager.resolveService(intent, flags.value.toInt())

    // Until API 33
    @Suppress("QueryPermissionsNeeded") // queries declaration is available in the main module manifest
    override fun queryIntentActivities(
        packageManager: PackageManager,
        intent: Intent,
        flags: ResolveInfoFlagsCompat,
    ): List<ResolveInfo> = packageManager.queryIntentActivities(intent, flags.value.toInt())

    // Until API 33
    override fun <T : Serializable?> getSerializableExtra(
        intent: Intent,
        name: String,
        className: Class<T>,
    ): T? {
        return try {
            @Suppress("UNCHECKED_CAST")
            intent.getSerializableExtra(name) as? T?
        } catch (e: Exception) {
            return null
        }
    }

    // Until API 33
    override fun getPackageInfo(
        packageManager: PackageManager,
        packageName: String,
        flags: PackageInfoFlagsCompat,
    ): PackageInfo? = packageManager.getPackageInfo(packageName, flags.value.toInt())

    // Until API 33
    @Suppress("UNCHECKED_CAST")
    override fun <T : Serializable?> getSerializable(
        bundle: Bundle,
        key: String,
        clazz: Class<T>,
    ): T? = bundle.getSerializable(key) as? T?

    @Suppress("ktlint:standard:property-naming")
    override val AXIS_GESTURE_X_OFFSET: Int = 48

    @Suppress("ktlint:standard:property-naming")
    override val AXIS_GESTURE_Y_OFFSET: Int = 49

    @Suppress("ktlint:standard:property-naming")
    override val AXIS_GESTURE_SCROLL_X_DISTANCE: Int = 50

    @Suppress("ktlint:standard:property-naming")
    override val AXIS_GESTURE_SCROLL_Y_DISTANCE: Int = 51

    @Suppress("ktlint:standard:property-naming")
    override val AXIS_GESTURE_PINCH_SCALE_FACTOR: Int = 52

    // Until API 26, use the provided fallback
    override fun getListSeparator(
        context: Context,
        fallback: String,
    ): String = fallback

    // Until API 29, gesture navigation does not exist
    override fun isUsingSystemGestureNavigation(
        context: Context,
        defaultValue: Boolean,
    ): Boolean = false

    override fun setDstOutBlend(paint: Paint) {
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    }
}

typealias CompatV24 = BaseCompat

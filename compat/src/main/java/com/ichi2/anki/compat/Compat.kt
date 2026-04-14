// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2011 Flavio Lerda <flerda@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2022 Arthur Milchior <arthur@milchior.fr>

package com.ichi2.anki.compat

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import androidx.annotation.AnimRes
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.Serializable
import kotlin.time.Duration

/**
 * This interface defines a set of functions that are not available on all platforms.
 *
 *
 * A set of implementations for the supported platforms are available.
 *
 * BaseCompat is the implementation for our current [minSdk]. It is overridden by `CompatV<n>`,
 * identifying the minimum API version on which this implementation
 * can be used. For example, see [CompatV33].
 *
 *
 * Each implementation `CompatVn` should extend the implementation `CompatVm` for the greatest m<n such that `CompatVm`
 * exists, or BaseCompat if no such `m` exists.
 * E.g. as of November 24 `CompatV29` extends `CompatV26` because there is no `CompatV27` or `CompatV28`.
 * If `CompatV27` were to be created one day, it will extends `CompatV26` and be extended by `CompatV29`.
 *
 *
 * Each method `method` must be implemented in [BaseCompat] .
 * It must also be implemented in `CompatVn` if, in version `n` and higher,
 * a different implementation must be used. This can be done either because some method used in the API `n` got
 * deprecated, changed its behavior, or because the implementation of `method` can be more efficient.
 *
 *
 * When you call method `method` from some device with API `n`, it will uses the implementation in `CompatVm`,
 * for `m < n` as great as possible. The value of `m` being at least the current min SDK. The method may be empty,
 * for example `setupNotificationChannel`'s implementation in `CompatV21` is empty since
 * notification channels were introduced in API 26.
 *
 *
 * Example: [CompatV26] extends [CompatV24] which extends [BaseCompat]. The method `vibrate` is
 * defined in [BaseCompat] where only the number of seconds of vibration is taken into consideration, and is
 * redefined in [CompatV26] - using `@Override` - where the style of vibration is also taken into
 * consideration. It means that  on devices using APIs 23 to 25 included, the implementation of [BaseCompat] is
 * used, and on devices using API 26 and higher, the implementation of [CompatV26] is used.
 * On the other hand a method like [Compat.saveImage] that got defined in [BaseCompat] and redefined in
 * [CompatV29] in order to use [Environment.DIRECTORY_PICTURES], need not be implemented again in [CompatV26].
 */
interface Compat {
    fun setTooltipTextByContentDescription(view: View)

    fun vibrate(
        context: Context,
        duration: Duration,
        @VibrationUsage usage: Int,
    )

    fun getMediaRecorder(context: Context): MediaRecorder

    fun overrideTransition(
        activity: Activity,
        @AnimRes enter: Int,
        @AnimRes exit: Int,
        open: Boolean,
    )

    fun resolveActivity(
        packageManager: PackageManager,
        intent: Intent,
        flags: ResolveInfoFlagsCompat,
    ): ResolveInfo?

    fun resolveService(
        packageManager: PackageManager,
        intent: Intent,
        flags: ResolveInfoFlagsCompat,
    ): ResolveInfo?

    fun queryIntentActivities(
        packageManager: PackageManager,
        intent: Intent,
        flags: ResolveInfoFlagsCompat,
    ): List<ResolveInfo>

    /**
     * Retrieve extended data from the intent.
     * @param name – The name of the desired item.
     * @param className – The type of the object expected.
     * @return the value of an item previously added with putExtra(), or null if no [Serializable] value was found.
     */
    fun <T : Serializable?> getSerializableExtra(
        intent: Intent,
        name: String,
        className: Class<T>,
    ): T?

    /**
     * Returns the value associated with the given key, or `null` if:
     * * No mapping of the desired type exists for the given key.
     * * A `null` value is explicitly associated with the key.
     * * The object is not of type `clazz`.
     *
     * @param key a String, or `null`
     * @param clazz The expected class of the returned type
     * @return a Serializable value, or `null`
     */
    fun <T : Serializable?> getSerializable(
        bundle: Bundle,
        key: String,
        clazz: Class<T>,
    ): T?

    /**
     * Retrieve overall information about an application package that is
     * installed on the system.
     *
     * @see PackageManager.getPackageInfo
     * @throws NameNotFoundException if no such package is available to the caller.
     * * Can be null: https://cs.android.com/android/platform/superproject/+/master:frameworks/base/services/core/java/com/android/server/pm/ComputerEngine.java;drc=c4ad8bc669e66262a00798b57132347a0d0aa2ac;bpv=1;bpt=1;l=1705?q=getPackageInfoInternal&ss=android&gsn=getPackageInfoInternalBody&gs=kythe%3A%2F%2Fandroid.googlesource.com%2Fplatform%2Fsuperproject%3Flang%3Djava%3Fpath%3Dcom.android.server.pm.ComputerEngine%23977e4a94695fef516f4b2d9fa73dea77cfaf06eff40c6fb3ec9bd80c6e18a08f
     */
    @Throws(NameNotFoundException::class)
    fun getPackageInfo(
        packageManager: PackageManager,
        packageName: String,
        flags: PackageInfoFlagsCompat,
    ): PackageInfo?

    /**
     * Copy file at path [source] to path [target]
     */
    @Throws(IOException::class)
    fun copyFile(
        source: String,
        target: String,
    )

    /**
     * Copy file at path [source] to [target]
     * @return the number of bytes read or written
     */
    @Throws(IOException::class)
    fun copyFile(
        source: String,
        target: OutputStream,
    ): Long

    /**
     * Copy file at path [source] to path [target]
     * @return the number of bytes read or written
     */
    @Throws(IOException::class)
    fun copyFile(
        source: InputStream,
        target: String,
    ): Long

    /**
     * Deletes a provided file/directory. If the file is a directory then the directory must be empty
     * @see File.delete
     * @see java.nio.file.Files.delete
     * @throws FileNotFoundException If the file does not exist
     * @throws IOException If the file failed to be deleted
     */
    @Throws(IOException::class)
    fun deleteFile(file: File)

    /**
     * Whether a directory has at least one files
     * @return Whether the directory has file.
     * @throws SecurityException If a security manager exists and its SecurityManager.checkRead(String)
     * method denies read access to the directory
     * @throws FileNotFoundException if the file do not exists
     * @throws java.nio.file.NotDirectoryException if the file could not otherwise be opened because it is not
     * a directory (optional specific exception), (starting at API 26)
     * @throws IOException – if an I/O error occurs
     */
    @Throws(IOException::class)
    fun hasFiles(directory: File): Boolean {
        contentOfDirectory(directory).use { stream -> return stream.hasNext() }
    }

    /**
     * Same as [File::createDirectories]. Does not throw if directory already exists
     * @param directory a directory to create. Create parents if necessary
     * @throws IOException
     */
    @Throws(IOException::class)
    fun createDirectories(directory: File)

    fun hasVideoThumbnail(path: String): Boolean?

    /**
     * Writes an image represented by bitmap to the Pictures/AnkiDroid directory under the primary
     * external storage directory. Requires the WRITE_EXTERNAL_STORAGE permission to be obtained on devices running
     * API <= 28. If this condition isn't satisfied, this method will throw a [FileNotFoundException].
     *
     * @param context Used to insert the image into the appropriate MediaStore collection for API >= 29
     * @param bitmap Bitmap to be saved
     * @param baseFileName the filename of the image to be saved excluding the file extension
     * @param extension File extension of the image to be saved
     * @param format The format bitmap should be compressed to
     * @param quality Hint to the compressor specifying the quality of the compressed image
     * @return The path of the saved image
     * @throws FileNotFoundException if the device's API is <= 28 and has not obtained the
     * WRITE_EXTERNAL_STORAGE permission
     */
    @Throws(FileNotFoundException::class)
    fun saveImage(
        context: Context,
        bitmap: Bitmap,
        baseFileName: String,
        extension: String,
        format: CompressFormat,
        quality: Int,
    ): Uri

    /**
     *
     * @param directory A directory.
     * @return a FileStream over file and directory of this directory.
     * null in case of trouble. This stream must be closed explicitly when done with it.
     * @throws java.nio.file.NotDirectoryException if the file exists and is not a directory (starting at API 26)
     * @throws FileNotFoundException if the file do not exists
     * @throws IOException if files can not be listed. On non existing or non-directory file up to API 25. This also occurred on an existing directory because of permission issue
     * that we could not reproduce. See https://github.com/ankidroid/Anki-Android/issues/10358
     * @throws SecurityException – If a security manager exists and its SecurityManager.checkRead(String) method denies read access to the directory
     */
    @Throws(IOException::class)
    fun contentOfDirectory(directory: File): FileStream

    @Suppress("PropertyName")
    val AXIS_GESTURE_X_OFFSET: Int

    @Suppress("PropertyName")
    val AXIS_GESTURE_Y_OFFSET: Int

    @Suppress("PropertyName")
    val AXIS_GESTURE_PINCH_SCALE_FACTOR: Int

    @Suppress("PropertyName")
    val AXIS_GESTURE_SCROLL_X_DISTANCE: Int

    @Suppress("PropertyName")
    val AXIS_GESTURE_SCROLL_Y_DISTANCE: Int

    /**
     * Returns the character to use when separating a list; `, ` in English
     * @param fallback The fallback separator to use on API levels below 26
     */
    fun getListSeparator(
        context: Context,
        fallback: String,
    ): String

    /**
     * Returns `true` if the device is using gesture navigation (swipe from edges).
     * Gesture navigation was introduced in Android 10 (API 29).
     * On older versions, this always returns `false`.
     *
     * Note: this reads a `Settings.Secure` key that is not part of the public API.
     * It will not throw, but the result is manufacturer dependent.
     *
     * @param defaultValue value returned when the `navigation_mode` key is unset
     */
    fun isUsingSystemGestureNavigation(
        context: Context,
        defaultValue: Boolean = false,
    ): Boolean
}

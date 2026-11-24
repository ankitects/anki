// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Akshay Jadhav <jadhavakshay0701@gmail.com>

package com.ichi2.anki

import android.content.ClipDescription
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.annotation.CheckResult
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.compat.CompatHelper
import com.ichi2.anki.libanki.exception.EmptyMediaException
import com.ichi2.anki.multimediacard.fields.ImageField
import com.ichi2.anki.multimediacard.fields.MediaClipField
import com.ichi2.utils.ClipboardUtil
import com.ichi2.utils.ContentResolverUtil.getFileName
import com.ichi2.utils.FileNameAndExtension
import com.ichi2.utils.openInputStreamSafe
import timber.log.Timber
import java.io.DataInputStream
import java.io.EOFException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

private typealias DisplayMediaError = (MediaRegistration.MediaError) -> Unit

/**
 * Utility class for media registration and handling errors during media paste actions.
 */
object MediaRegistration {
    /**
     * Represents different types of media errors.
     */
    sealed class MediaError {
        /** [Something wrong wrong][CommonString.multimedia_editor_something_wrong] */
        data object GenericError : MediaError()

        /** [Something went wrong, please try again[CommonString.something_wrong] */
        data class GenericErrorTryAgain(
            val details: String?,
        ) : MediaError()

        /** [Error converting clipboard image to png][CommonString.multimedia_editor_png_paste_error] */
        class ConversionError(
            val message: String,
        ) : MediaError()

        /** [The image is too large, please insert the image manually][CommonString.note_editor_image_too_large] */
        data object ImageTooLarge : MediaError()

        /** [The video file is too large, please insert the video manually][CommonString.note_editor_video_too_large] */
        data object VideoTooLarge : MediaError()

        /** [The audio file is too large, please insert the audio manually][CommonString.note_editor_audio_too_large] */
        data object AudioTooLarge : MediaError()

        fun toHumanReadableString(context: Context): String =
            when (this) {
                is GenericError -> context.getString(CommonString.multimedia_editor_something_wrong)
                is GenericErrorTryAgain -> context.getString(CommonString.something_wrong) + details?.let { "\n\n$it" }.orEmpty()
                is ConversionError -> context.getString(CommonString.multimedia_editor_png_paste_error, message)
                is ImageTooLarge -> context.getString(CommonString.note_editor_image_too_large)
                is VideoTooLarge -> context.getString(CommonString.note_editor_video_too_large)
                is AudioTooLarge -> context.getString(CommonString.note_editor_audio_too_large)
            }
    }

    /**
     * Maximum allowed media file size in bytes.
     * The limit is set to 5 MB.
     */
    private const val MEDIA_MAX_SIZE_BYTES = 5 * 1000 * 1000

    /**
     * Handles the paste action for media.
     *
     * @param context The application context.
     * @param uri The URI of the media to be pasted.
     * @param description The description of the clipboard content.
     * @param pasteAsPng A flag indicating whether to convert the media to PNG format.
     * @param showError A callback function for displaying error messages based on media error type.
     * @return A string reference to the media if successfully processed, or null if an error occurred.
     */
    fun onPaste(
        context: Context,
        uri: Uri,
        description: ClipDescription,
        pasteAsPng: Boolean,
        showError: DisplayMediaError,
    ): String? =
        try {
            loadMediaIntoCollection(context, uri, description, pasteAsPng, showError)
        } catch (ex: NullPointerException) {
            // Tested under FB Messenger and GMail, both apps do nothing if this occurs.
            // This typically works if the user copies again - don't know the exact cause

            //  java.lang.SecurityException: Permission Denial: opening provider
            //  org.chromium.chrome.browser.util.ChromeFileProvider from ProcessRecord{80125c 11262:com.ichi2.anki/u0a455}
            //  (pid=11262, uid=10455) that is not exported from UID 10057
            Timber.w(ex, "Failed to paste media")
            showError(MediaError.GenericError)
            null
        } catch (ex: SecurityException) {
            Timber.w(ex, "Failed to paste media")
            showError(MediaError.GenericError)
            null
        } catch (e: Exception) {
            // NOTE: This is happy path coding which works on Android 9.
            CrashReportService.sendExceptionReport("File is invalid issue:8880", "RegisterMediaForWebView:onImagePaste URI of file:$uri")
            Timber.w(e, "Failed to paste media")
            showError(MediaError.GenericError)
            null
        } catch (ex: OutOfMemoryError) {
            CrashReportService.sendExceptionReport(ex, "onPaste", onlyIfSilent = true)
            Timber.w(ex, "Failed to paste media")
            showError(MediaError.GenericErrorTryAgain(details = ex.toString()))
            null
        }

    /**
     * Checks if the media file size exceeds the allowed limit.
     *
     * @param bytesWritten The size of the media file in bytes.
     * @param isImage `true` if the media is an image, otherwise `false`.
     * @param isVideo `true` if the media is a video, otherwise `false`.
     * @param showError A callback function to display an error message if the file exceeds the size limit.
     *                  It receives a [MediaError] type and an optional error message.
     * @return `true` if the file size is within the allowed limit, otherwise `false`.
     */
    @NeedsTest("all cases should work as expected")
    private fun checkMediaSize(
        bytesWritten: Long,
        isImage: Boolean,
        isVideo: Boolean,
        showError: DisplayMediaError,
    ): Boolean {
        if (bytesWritten <= MEDIA_MAX_SIZE_BYTES) return true

        when {
            isImage -> showError(MediaError.ImageTooLarge)
            isVideo -> showError(MediaError.VideoTooLarge)
            else -> showError(MediaError.AudioTooLarge)
        }
        return false
    }

    /**
     * Loads media into the collection.media directory and returns a HTML reference
     * @param uri The uri of the image to load
     * @return HTML referring to the loaded image
     *
     * @throws OutOfMemoryError if the file could not be copied to a contiguous block of memory (or is >= 2GB)
     */
    // TODO: remove the Android dependencies and handle them outside the method
    @Throws(IOException::class)
    fun loadMediaIntoCollection(
        context: Context,
        uri: Uri,
        description: ClipDescription,
        pasteAsPng: Boolean,
        showError: DisplayMediaError,
    ): String? {
        val filename = getFileName(context.contentResolver, uri)
        val (fileName, fileExtensionWithDot) =
            FileNameAndExtension
                .fromString(filename)
                ?.renameForCreateTempFile()
                ?: throw IllegalStateException("Unable to determine valid filename")
        var clipCopy: File = File.createTempFile(fileName, fileExtensionWithDot)
        var bytesWritten: Long = 0

        context.contentResolver.openInputStreamSafe(uri).use { fd ->
            if (fd == null) return@use
            bytesWritten = CompatHelper.compat.copyFile(fd, clipCopy.absolutePath)
        }
        val mediaType = mediaType(context, uri, description, clipCopy)
        if (mediaType == null) {
            Timber.i("Not pasting an item which is not media")
            clipCopy.delete()
            return null
        }
        val isImage = ClipDescription.compareMimeTypes(mediaType, "image/*")
        val isVideo = ClipDescription.compareMimeTypes(mediaType, "video/*")

        // Converts to PNG if needed. Returns null on failure.
        val bitmap = if (pasteAsPng && isImage && !isAnimated(clipCopy)) BitmapFactory.decodeFile(clipCopy.absolutePath) else null
        if (bitmap != null) {
            clipCopy.delete()
            clipCopy = File.createTempFile(fileName, ".png")
            if (!convertToPNG(bitmap, clipCopy, showError)) {
                return null
            }
        }
        val tempFilePath = clipCopy

        val checkMediaSize = checkMediaSize(bytesWritten, isImage, isVideo, showError)

        if (!checkMediaSize) {
            tempFilePath.delete()
            clipCopy.delete()
            return null
        }

        // register media for webView
        val importedFile = registerMediaForWebView(tempFilePath)
        if (importedFile == null) {
            clipCopy.delete()
            return null
        }
        Timber.d("File was %d bytes", bytesWritten)

        val field = if (isImage) ImageField() else MediaClipField()

        field.mediaFile = importedFile
        return field.formattedValue
    }

    private val AUDIO_APPLICATION_TYPES = mapOf("application/ogg" to "audio/ogg", "application/x-flac" to "audio/flac")

    private val EXTENSION_TYPES = mapOf("avif" to "image/avif")

    private fun mediaType(
        context: Context,
        uri: Uri,
        description: ClipDescription,
        file: File,
    ): String? {
        val resolver = context.contentResolver
        val providerType = resolver.getType(uri)?.let { AUDIO_APPLICATION_TYPES[it] ?: it }
        if (isMediaType(providerType)) return providerType
        resolver
            .getStreamTypes(uri, "*/*")
            ?.map { AUDIO_APPLICATION_TYPES[it] ?: it }
            ?.firstOrNull { isMediaType(it) }
            ?.let { return it }
        val extensionType =
            (MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension) ?: EXTENSION_TYPES[file.extension.lowercase()])
                ?.let { AUDIO_APPLICATION_TYPES[it] ?: it }
        val providerIsText = providerType != null && ClipDescription.compareMimeTypes(providerType, "text/*")
        if (isMediaType(extensionType) && (!providerIsText || extensionType == "image/svg+xml")) return extensionType
        decodedImageType(file)?.let { return it }
        val clipTypes = List(description.mimeTypeCount) { description.getMimeType(it) }
        val clipIsAudioOrVideo =
            clipTypes.isNotEmpty() &&
                clipTypes.all { type -> listOf("audio/*", "video/*").any { ClipDescription.compareMimeTypes(type, it) } }
        return if (isUnknownType(providerType) && isUnknownType(extensionType) && clipIsAudioOrVideo) clipTypes.first() else null
    }

    private fun isUnknownType(type: String?): Boolean =
        type == null || type.endsWith("/octet-stream") || type.startsWith("vnd.android.cursor.")

    private fun isMediaType(type: String?): Boolean =
        type != null && ClipboardUtil.MEDIA_MIME_TYPES.any { ClipDescription.compareMimeTypes(type, it) }

    private fun decodedImageType(file: File): String? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        return if (bounds.outWidth > 0) bounds.outMimeType ?: "image/*" else null
    }

    private const val GIF_EXTENSION_INTRODUCER = 0x21

    private const val GIF_IMAGE_SEPARATOR = 0x2C

    private fun isAnimated(file: File): Boolean =
        try {
            DataInputStream(file.inputStream().buffered()).use { input ->
                val header = ByteArray(12)
                input.mark(header.size)
                input.readFully(header)
                input.reset()
                val signature = header.toString(Charsets.ISO_8859_1)
                when {
                    signature.startsWith("GIF8") -> input.hasSecondGifFrame()
                    signature.startsWith("\u0089PNG") -> input.hasPngAnimationControl()
                    signature.startsWith("RIFF") && signature.endsWith("WEBP") -> input.hasWebpAnimationFlag()
                    else -> false
                }
            }
        } catch (e: IOException) {
            Timber.w(e, "Unable to check whether an image is animated")
            false
        }

    private fun DataInputStream.hasSecondGifFrame(): Boolean {
        skipFully(10)
        val screenFlags = readUnsignedByte()
        skipFully(2)
        skipGifColorTable(screenFlags)
        var frames = 0
        while (true) {
            when (readUnsignedByte()) {
                GIF_EXTENSION_INTRODUCER -> skipFully(1)
                GIF_IMAGE_SEPARATOR -> {
                    if (++frames == 2) return true
                    skipFully(8)
                    skipGifColorTable(readUnsignedByte())
                    skipFully(1)
                }
                else -> return false
            }
            skipGifSubBlocks()
        }
    }

    private fun DataInputStream.skipGifColorTable(flags: Int) {
        if (flags and 0x80 != 0) skipFully(3 shl ((flags and 0x07) + 1))
    }

    private fun DataInputStream.skipGifSubBlocks() {
        while (true) {
            val size = readUnsignedByte()
            if (size == 0) return
            skipFully(size)
        }
    }

    private fun DataInputStream.hasPngAnimationControl(): Boolean {
        skipFully(8)
        while (true) {
            val length = readInt()
            when (readChunkType()) {
                "acTL" -> return true
                "IDAT" -> return false
            }
            skipFully(length)
            skipFully(4)
        }
    }

    private fun DataInputStream.hasWebpAnimationFlag(): Boolean {
        skipFully(12)
        if (readChunkType() != "VP8X") return false
        skipFully(4)
        return readUnsignedByte() and 0x02 != 0
    }

    private fun DataInputStream.readChunkType(): String = ByteArray(4).also { readFully(it) }.toString(Charsets.ISO_8859_1)

    private fun DataInputStream.skipFully(count: Int) {
        if (skipBytes(count) < count) throw EOFException()
    }

    private fun convertToPNG(
        bitmap: Bitmap,
        file: File,
        showError: DisplayMediaError,
    ): Boolean {
        try {
            FileOutputStream(file.absolutePath).use { outStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream)
                outStream.flush()
            }
        } catch (e: IOException) {
            Timber.w("MediaRegistration : Unable to convert file to png format")
            CrashReportService.sendExceptionReport(e, "Unable to convert file to png format")
            showError(MediaError.ConversionError(e.message ?: ""))
            return false
        }
        return true
    }

    /**
     * @return the imported file, including any filename changes made by the backend, or null on failure
     *
     * @throws OutOfMemoryError if the file could not be copied to a contiguous block of memory (or is >= 2GB)
     */
    @CheckResult
    private fun registerMediaForWebView(mediaPath: File): File? {
        Timber.i("Adding media to collection: %s", mediaPath)
        return try {
            val media = CollectionManager.getColUnsafe().media
            File(media.dir, media.addFile(mediaPath))
        } catch (e: IOException) {
            Timber.w(e, "Failed to add file")
            null
        } catch (e: EmptyMediaException) {
            Timber.w(e, "Failed to add file")
            null
        }
    }
}

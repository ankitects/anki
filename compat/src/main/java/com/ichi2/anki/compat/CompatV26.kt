// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2018 Mike Hardy <github@mikehardy.net>
// SPDX-FileCopyrightText: Copyright (c) 2022 Arthur Milchior <arthur@milchior.fr>

package com.ichi2.anki.compat

import android.content.Context
import android.icu.text.ListFormatter
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.DirectoryIteratorException
import java.nio.file.DirectoryStream
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import kotlin.time.Duration

/** Implementation of [Compat] for SDK level 26 and higher. Check  [Compat]'s for more detail.  */
@RequiresApi(26)
open class CompatV26 : CompatV24() {
    override fun setTooltipTextByContentDescription(view: View) { // Nothing to do API26+
    }

    @Suppress("DEPRECATION") // VIBRATOR_SERVICE => VIBRATOR_MANAGER_SERVICE handled in CompatV31
    override fun vibrate(
        context: Context,
        duration: Duration,
        @VibrationUsage usage: Int,
    ) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (vibratorManager != null) {
            val effect = VibrationEffect.createOneShot(duration.inWholeMilliseconds, VibrationEffect.DEFAULT_AMPLITUDE)
            vibratorManager.vibrate(effect)
        }
    }

    @Throws(IOException::class)
    override fun copyFile(
        source: String,
        target: String,
    ) {
        Files.copy(Paths.get(source), Paths.get(target), StandardCopyOption.REPLACE_EXISTING)
    }

    @Throws(IOException::class)
    override fun copyFile(
        source: String,
        target: OutputStream,
    ): Long = Files.copy(Paths.get(source), target)

    @Throws(IOException::class)
    override fun copyFile(
        source: InputStream,
        target: String,
    ): Long = Files.copy(source, Paths.get(target), StandardCopyOption.REPLACE_EXISTING)

    @Throws(IOException::class)
    override fun deleteFile(file: File) {
        try {
            Files.delete(file.toPath())
        } catch (ex: NoSuchFileException) {
            throw FileNotFoundException(file.canonicalPath)
        }
    }

    @Throws(IOException::class)
    override fun createDirectories(directory: File) {
        Files.createDirectories(directory.toPath())
    }

    @VisibleForTesting
    @Throws(IOException::class)
    fun newDirectoryStream(dir: Path?): DirectoryStream<Path> = Files.newDirectoryStream(dir)

    /*
     * This method uses [Files.newDirectoryStream].
     * Hence this method, hasNext and next should be constant in time and space.
     */
    @Throws(IOException::class)
    override fun contentOfDirectory(directory: File): FileStream {
        val pathsStream: DirectoryStream<Path> =
            try {
                newDirectoryStream(directory.toPath())
            } catch (noSuchFileException: NoSuchFileException) {
                throw FileNotFoundException(
                    """
                    ${noSuchFileException.file}
                    ${noSuchFileException.cause}
                    ${noSuchFileException.stackTrace}
                    """.trimIndent(),
                )
            }
        val paths: Iterator<Path> = pathsStream.iterator()
        return object : FileStream {
            @Throws(IOException::class)
            override fun close() {
                pathsStream.close()
            }

            @Throws(IOException::class)
            override operator fun hasNext(): Boolean =
                try {
                    paths.hasNext()
                } catch (e: DirectoryIteratorException) {
                    // According to the documentation, it's the only exception it can throws.
                    throw e.cause!!
                }

            @Throws(IOException::class)
            override operator fun next(): File {
                // According to the documentation, if [hasNext] returned true, [next] is guaranteed to succeed.
                return try {
                    paths.next().toFile()
                } catch (e: DirectoryIteratorException) {
                    throw e.cause!!
                }
            }
        }
    }

    // API 26+: Use ListFormatter to dynamically get the locale-specific separator
    override fun getListSeparator(
        context: Context,
        fallback: String,
    ): String {
        val formatter = ListFormatter.getInstance()
        // Format a list with 3 dummy items
        val formatted = formatter.format("A", "B", "C")
        // Parse the separator from the first two items
        // The format should be something like "A, B, and C" or "A، B، و C" (Arabic)
        // We extract the part between "A" and "B"
        val separatorStart = formatted.indexOf("A") + 1
        val separatorEnd = formatted.indexOf("B")
        return formatted.substring(separatorStart, separatorEnd)
    }
}

/*
 * Copyright (c) 2021 Mani <infinyte01@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * This file incorporates work covered by the following copyright and permission
 * notice:
 *
 *      Copyright (C) 2016 The Android Open Source Project
 *      <p>
 *      Licensed under the Apache License, Version 2.0 (the "License");
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *      <p>
 *      http://www.apache.org/licenses/LICENSE-2.0
 *      <p>
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an "AS IS" BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 */

package com.ichi2.anki.jsaddons

import android.content.Context
import android.text.format.Formatter
import com.ichi2.anki.R
import com.ichi2.anki.common.utils.android.showThemedToast
import com.ichi2.anki.compat.CompatHelper.Companion.compat
import com.ichi2.utils.FileUtil
import org.apache.commons.compress.archivers.ArchiveException
import org.apache.commons.compress.archivers.ArchiveStreamFactory
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import timber.log.Timber
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.GZIPInputStream

/*
 * In JS Addons the addon packages are downloaded from npm registry, https://registry.npmjs.org
 * The file format of downloaded file is tgz, so this class used to extract tgz file to addon directory.
 * To extract the gzip file considering security the following checks are implemented
 *
 * 1. File size of package should be less than 100 MB
 * 2. During extract the space consumed should be less than half of original availableSpace
 * 3. The file should not write outside of specified directory
 * 4. Clean up of temp files
 *
 * The following TarUtil.java file used to implement this
 * https://android.googlesource.com/platform/tools/tradefederation/+/master/src/com/android/tradefed/util/TarUtil.java
 *
 * zip slip safety
 * https://snyk.io/research/zip-slip-vulnerability
 *
 * Safely extract files
 * https://wiki.sei.cmu.edu/confluence/display/java/IDS04-J.+Safely+extract+files+from+ZipInputStream
 */

/**
 * addons path typealias, the path is some-addon path inside addons directory
 * The path structure of some-addon
 *
 * AnkiDroid
 *   - addons
 *       - some-addon
 *           - package
 *               - index.js
 *               - README.md
 *       - some-another-addon
 */
typealias AddonsPackageDir = File

class TgzPackageExtract(
    private val context: Context,
) {
    private val gzipSignature = byteArrayOf(0x1f, 0x8b.toByte())
    private var requiredMinSpace: Long = 0
    private var availableSpace: Long = 0

    private var count = 0
    private var total: Long = 0
    private var data = ByteArray(BUFFER)

    /**
     * Determine whether a file is a gzip.
     *
     * @param file the file to check.
     * @return whether the file is a gzip.
     * @throws IOException if the file could not be read.
     */
    @Throws(IOException::class)
    fun isGzip(file: File?): Boolean {
        val signature = ByteArray(gzipSignature.size)
        FileInputStream(file).use { stream ->
            if (stream.read(signature) != signature.size) {
                return false
            }
        }
        return gzipSignature.contentEquals(signature)
    }

    /**
     * Untar and ungzip a tar.gz file to a AnkiDroid/addons directory.
     *
     * @param tarballFile the .tgz file to extract
     * @param addonsPackageDir the addons package directory, the path is addon path inside addons directory
     *                         e.g. AnkiDroid/addons/some-addon/
     * @return the temp directory.
     * @throws FileNotFoundException if .tgz file or ungzipped file i.e. .tar file not found
     * @throws IOException
     */
    @Throws(Exception::class)
    fun extractTarGzipToAddonFolder(
        tarballFile: File,
        addonsPackageDir: AddonsPackageDir,
    ) {
        require(isGzip(tarballFile)) { context.getString(R.string.not_valid_js_addon_package, tarballFile.absolutePath) }

        try {
            compat.createDirectories(addonsPackageDir)
        } catch (e: IOException) {
            showThemedToast(context, context.getString(R.string.could_not_create_dir, addonsPackageDir.absolutePath), false)
            Timber.w(e)
            return
        }

        // Make sure we have 2x the tar file size in free space (1x for tar file, 1x for unarchived tar file contents
        requiredMinSpace = tarballFile.length() * 2
        availableSpace = FileUtil.determineBytesAvailable(addonsPackageDir.canonicalPath)
        InsufficientSpaceException.throwIfInsufficientSpace(context, requiredMinSpace, availableSpace)

        // If space available then unGZip it
        val tarTempFile = unGzip(tarballFile, addonsPackageDir)
        tarTempFile.deleteOnExit()

        // Make sure we have sufficient free space
        val unTarSize = calculateUnTarSize(tarTempFile)
        InsufficientSpaceException.throwIfInsufficientSpace(context, unTarSize, availableSpace)

        try {
            // If space available then unTar it
            unTar(tarTempFile, addonsPackageDir)
        } catch (e: IOException) {
            Timber.w("Failed to unTar file")
            safeDeleteAddonsPackageDir(addonsPackageDir)
        } finally {
            tarTempFile.delete()
        }
    }

    /**
     * UnGZip a file: a .tgz file will become a .tar file.
     *
     * @param inputFile The [File] to ungzip
     * @param outputDir The directory where to put the ungzipped file.
     * @return a [File] pointing to the ungzipped file.
     * @throws FileNotFoundException
     * @throws IOException
     */
    @Throws(FileNotFoundException::class, IOException::class)
    fun unGzip(
        inputFile: File,
        outputDir: File,
    ): File {
        Timber.i("Ungzipping %s to dir %s.", inputFile.absolutePath, outputDir.absolutePath)

        // remove the '.tgz' extension and add .tar extension
        val outputFile = File(outputDir, inputFile.nameWithoutExtension + ".tar")

        count = 0
        total = 0
        data = ByteArray(BUFFER)

        try {
            GZIPInputStream(FileInputStream(inputFile)).use { inputStream ->
                FileOutputStream(outputFile).use { outputStream ->
                    BufferedOutputStream(outputStream, BUFFER).use { bufferOutput ->

                        // Gzip file size should not not be greater than TOO_BIG_SIZE
                        while (total + BUFFER <= TOO_BIG_SIZE &&
                            inputStream.read(data, 0, BUFFER).also { count = it } != -1
                        ) {
                            bufferOutput.write(data, 0, count)
                            total += count

                            // If space consumed is more than half of original availableSpace, delete file recursively and throw
                            enforceSpaceUsedLessThanHalfAvailable(outputFile)
                        }

                        if (total + BUFFER > TOO_BIG_SIZE) {
                            outputFile.delete()
                            throw IllegalStateException("Gzip file is too big to unGzip")
                        }
                    }
                }
            }
        } catch (e: IOException) {
            outputFile.delete()
            throw IllegalStateException("Gzip file is too big to unGzip")
        }

        return outputFile
    }

    /**
     * Untar a tar file into a directory. tar.gz file needs to be [.unGzip] first.
     *
     * @param inputFile The tar file to extract
     * @param outputDir the directory where to put the extracted files.
     * @throws ArchiveException
     * @throws IOException
     */
    @Throws(Exception::class)
    fun unTar(
        inputFile: File,
        outputDir: File,
    ) {
        Timber.i("Untaring %s to dir %s.", inputFile.absolutePath, outputDir.absolutePath)

        count = 0
        total = 0
        data = ByteArray(BUFFER)

        try {
            FileInputStream(inputFile).use { inputStream ->
                ArchiveStreamFactory().createArchiveInputStream<TarArchiveInputStream>("tar", inputStream).use { tarInputStream ->
                    tarInputStream.forEach { entry ->
                        val outputFile = File(outputDir, entry.name)

                        // Zip Slip Vulnerability https://snyk.io/research/zip-slip-vulnerability
                        zipPathSafety(outputFile, outputDir)
                        if (entry.isDirectory) {
                            unTarDir(inputFile, outputDir, outputFile)
                        } else {
                            unTarFile(tarInputStream, entry, outputDir, outputFile)
                        }
                    }
                }
            }
        } catch (e: IOException) {
            outputDir.deleteRecursively()
            throw ArchiveException(
                context.getString(
                    R.string.malicious_archive_exceeds_limit,
                    Formatter.formatFileSize(context, TOO_BIG_SIZE),
                    TOO_MANY_FILES,
                ),
            )
        }
    }

    /**
     * UnTar file from archive using input stream, entry to output dir
     * @param tarInputStream TarArchiveInputStream
     * @param entry TarArchiveEntry
     * @param outputDir Output directory
     * @param outputFile Output file
     * @throws IOException
     */
    @Throws(IOException::class)
    private fun unTarFile(
        tarInputStream: TarArchiveInputStream,
        entry: TarArchiveEntry,
        outputDir: File,
        outputFile: File,
    ) {
        Timber.i("Creating output file %s.", outputFile.absolutePath)
        val currentFile = File(outputDir, entry.name)

        // this line important otherwise FileNotFoundException
        val parent = currentFile.parentFile ?: return

        try {
            compat.createDirectories(parent)
        } catch (e: IOException) {
            // clean up
            Timber.w(e)
            throw IOException(context.getString(R.string.could_not_create_dir, parent.absolutePath))
        }

        FileOutputStream(outputFile).use { outputFileStream ->
            BufferedOutputStream(outputFileStream, BUFFER).use { bufferOutput ->

                // Tar file should not be greater than TOO_BIG_SIZE
                while (total + BUFFER <= TOO_BIG_SIZE &&
                    tarInputStream.read(data, 0, BUFFER).also { count = it } != -1
                ) {
                    bufferOutput.write(data, 0, count)
                    total += count

                    // If space consumed is more than half of original availableSpace, delete file recursively and throw
                    enforceSpaceUsedLessThanHalfAvailable(outputDir)
                }

                if (total + BUFFER > TOO_BIG_SIZE) {
                    // remove unused file
                    outputFile.delete()
                    throw IllegalStateException("Tar file is too big to untar")
                }
            }
        }
    }

    /**
     * UnTar directory to output dir
     * @param inputFile archive input file
     * @param outputDir Output directory
     * @param outputFile Output file
     * @throws IOException
     */
    @Throws(IOException::class)
    private fun unTarDir(
        inputFile: File,
        outputDir: File,
        outputFile: File,
    ) {
        Timber.i("Untaring %s to dir %s.", inputFile.absolutePath, outputDir.absolutePath)
        try {
            Timber.i("Attempting to create output directory %s.", outputFile.absolutePath)
            compat.createDirectories(outputFile)
        } catch (e: IOException) {
            Timber.w(e)
            throw IOException(context.getString(R.string.could_not_create_dir, outputFile.absolutePath))
        }
    }

    /**
     * Ensure that the canonical path of destination directory should be equal to canonical path of output file
     * Zip Slip Vulnerability https://snyk.io/research/zip-slip-vulnerability
     *
     * @param outputFile output file
     * @param destDirectory destination directory
     */
    @Throws(ArchiveException::class, IOException::class)
    private fun zipPathSafety(
        outputFile: File,
        destDirectory: File,
    ) {
        val destDirCanonicalPath = destDirectory.canonicalPath
        val outputFileCanonicalPath = outputFile.canonicalPath

        if (!outputFileCanonicalPath.startsWith(destDirCanonicalPath)) {
            throw ArchiveException(context.getString(R.string.malicious_archive_entry_outside, outputFileCanonicalPath))
        }
    }

    /**
     * Given a tar file, iterate through the entries to determine the total untar size
     * TODO warning: vulnerable to resource exhaustion attack if entries contain spoofed sizes
     *
     * @param tarFile File of unknown total uncompressed size
     * @return total untar size of tar file
     */
    private fun calculateUnTarSize(tarFile: File): Long {
        var unTarSize: Long = 0

        FileInputStream(tarFile).use { inputStream ->
            ArchiveStreamFactory().createArchiveInputStream<TarArchiveInputStream>("tar", inputStream).use { tarInputStream ->
                var numOfEntries = 0

                tarInputStream.forEach { entry ->
                    numOfEntries++
                    if (numOfEntries > TOO_MANY_FILES) {
                        throw IllegalStateException("Too many files to untar")
                    }
                    unTarSize += entry.size
                }
            }
        }
        return unTarSize
    }

    /**
     * If space consumed is more than half of original availableSpace, delete file recursively and throw
     *
     * @param outputDir output directory
     */
    private fun enforceSpaceUsedLessThanHalfAvailable(outputDir: File) {
        val newAvailableSpace: Long = FileUtil.determineBytesAvailable(outputDir.canonicalPath)
        if (newAvailableSpace <= availableSpace / 2) {
            throw ArchiveException(context.getString(R.string.file_extract_exceeds_storage_space))
        }
    }

    class InsufficientSpaceException(
        val required: Long,
        val available: Long,
        val context: Context,
    ) : IOException() {
        companion object {
            fun throwIfInsufficientSpace(
                context: Context,
                requiredMinSpace: Long,
                availableSpace: Long,
            ) {
                if (requiredMinSpace > availableSpace) {
                    Timber.w(
                        "Not enough space, need %s, available %s",
                        Formatter.formatFileSize(context, requiredMinSpace),
                        Formatter.formatFileSize(context, availableSpace),
                    )
                    throw InsufficientSpaceException(requiredMinSpace, availableSpace, context)
                }
            }
        }
    }

    private fun safeDeleteAddonsPackageDir(addonsPackageDir: AddonsPackageDir) {
        if (addonsPackageDir.parent != "addons") {
            return
        }
        addonsPackageDir.deleteRecursively()
    }

    companion object {
        private const val BUFFER = 512
        private const val TOO_BIG_SIZE: Long = 0x6400000 // max size of unzipped data, 100MB
        private const val TOO_MANY_FILES = 1024 // max number of files
    }
}

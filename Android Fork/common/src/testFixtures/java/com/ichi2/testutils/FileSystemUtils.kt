// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import androidx.annotation.CheckResult
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.io.path.pathString

/** Utilities which assist testing changes to files/directories */
private val logger = LoggerFactory.getLogger(FileSystemUtils::class.java)

@Suppress("unused")
object FileSystemUtils {
    /**
     * Prints a directory structure
     * @param description The prefix to print before the tree is listed
     * @param file the root of the tree to print
     *
     * ```
     * D/FileSystemUtils: destination: C:\Users\User\AppData\Local\Temp\robolectric-Method_successfulMigration11404528269084729867\external-files\AnkiDroid-1
     * +--AnkiDroid-1/
     * |  +--backup/
     * |  |  +--collection-2020-08-07-08-00.colpkg
     * |  +--collection.media/
     * |  |  +--directory/
     * |  |  |  +--test.txt
     * |  |  +--test.txt
     * ```
     */
    fun printDirectoryTree(
        description: String,
        file: File,
    ) {
        logger.debug("$description: $file\n${printDirectoryTree(file)}")
    }

    /** from https://stackoverflow.com/a/13130974/ */
    @CheckResult
    private fun printDirectoryTree(directory: File): String {
        require(directory.isDirectory) { "directory is not a Directory" }
        val indent = 0
        val sb = StringBuilder()
        printDirectoryTree(directory, indent, sb)
        return sb.toString()
    }

    /** from https://stackoverflow.com/a/13130974/ */
    private fun printDirectoryTree(
        directory: File,
        indent: Int,
        sb: StringBuilder,
    ) {
        require(directory.isDirectory) { "directory is not a Directory" }
        sb.append(getIndentString(indent))
        sb.append("+--")
        sb.append(directory.name)
        sb.append("/")
        sb.append("\n")
        for (file in directory.listFiles() ?: emptyArray()) {
            if (file.isDirectory) {
                printDirectoryTree(file, indent + 1, sb)
            } else {
                printFile(file, indent + 1, sb)
            }
        }
    }

    /** from https://stackoverflow.com/a/13130974/ */
    private fun printFile(
        file: File,
        indent: Int,
        sb: StringBuilder,
    ) {
        sb.append(getIndentString(indent))
        sb.append("+--")
        sb.append(file.name)
        sb.append("\n")
    }

    /** from https://stackoverflow.com/a/13130974/ */
    private fun getIndentString(indent: Int): String {
        val sb = StringBuilder()
        for (i in 0 until indent) {
            sb.append("|  ")
        }
        return sb.toString()
    }
}

/**
 * Returns a new directory in the OS's default temp directory, using the given [prefix] to generate its name.
 * This directory is deleted on exit
 */
fun createTransientDirectory(prefix: String? = null): File =
    createTempDirectory(prefix = prefix).let {
        val file = File(it.pathString)
        file.deleteOnExit()
        return@let file
    }

/**
 * Returns a temp file with [content]. The file is deleted on exit.
 * @param extension The file extension. Do not include a "."
 */
fun createTransientFile(
    content: String = "",
    extension: String? = null,
): File =
    File(
        kotlin.io.path
            .createTempFile(suffix = if (extension == null) null else ".$extension")
            .pathString,
    ).also {
        it.deleteOnExit()
        it.writeText(content)
    }

/** Creates a sub-directory with the given name which is deleted on exit */
fun File.createTransientDirectory(name: String): File {
    File(this, name).also { directory ->
        directory.deleteOnExit()
        logger.debug("test: creating $directory")
        MatcherAssert.assertThat("directory should have been created", directory.mkdirs(), CoreMatchers.equalTo(true))
        return directory
    }
}

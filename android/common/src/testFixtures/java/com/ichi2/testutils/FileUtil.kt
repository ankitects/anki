// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import com.ichi2.anki.model.Directory
import java.io.File
import java.util.Scanner

object FileUtil {
    /**
     * Reads the single line of content in a file
     * @return The single line of the file, as a string
     * @throws IllegalArgumentException if file has more than one line
     * @throws IllegalArgumentException if file does not exist
     * @throws NoSuchElementException file has no lines
     * */
    fun readSingleLine(
        base: File,
        vararg path: String,
    ): String {
        var file = base
        for (pathSegment in path) {
            file = File(file, pathSegment)
        }
        if (!file.exists()) {
            throw IllegalArgumentException("path: $file does not exist")
        }

        return readAllLines(file).single()
    }

    /**
     * Sequence of the lines of file
     */
    private fun readAllLines(file: File) =
        sequence {
            Scanner(file).use { scanner ->
                while (scanner.hasNextLine()) {
                    yield(scanner.nextLine().toString())
                }
            }
        }
}

fun Directory.exists(): Boolean = this.directory.exists()

/** Adds a file to the directory with the provided name and content */
fun File.withTempFile(
    fileName: String,
    content: String = "default content",
): File {
    this.addTempFile(fileName, content)
    return this
}

/** Adds a file to the directory with the provided name and content. Return the new file. */
fun File.addTempFile(
    fileName: String,
    content: String = "default content",
): File =
    File(this, fileName).also {
        it.writeText(content)
        it.deleteOnExit()
    }

/** Adds a directory to the directory with the provided name and content. Return the new directory. */
fun File.addTempDirectory(directoryName: String): Directory {
    val dir =
        File(this, directoryName).also {
            it.mkdir()
            it.deleteOnExit()
        }
    return Directory.createInstance(dir)!!
}

/** Adds a file to the directory with the provided name and content */
fun Directory.withTempFile(
    fileName: String,
    content: String = "default content",
): Directory {
    this.directory.withTempFile(fileName, content)
    return this
}

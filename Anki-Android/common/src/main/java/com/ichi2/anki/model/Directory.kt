// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.model

import java.io.File

/**
 * A directory which is assumed to exist (existed when class was instantiated)
 */
class Directory private constructor(
    val directory: File,
) {
    /** List of files in this directory. If this is not a directory or no longer exists, then an empty array. */
    fun listFiles(): Array<out File> = directory.listFiles() ?: emptyArray()

    /** The [canonical path][java.io.File.getCanonicalPath] for the file */
    override fun toString(): String = directory.canonicalPath

    companion object {
        /**
         * Returns a [Directory] from [path] if `Directory` precondition holds; i.e. [path] is an existing directory.
         * Otherwise returns `null`.
         */
        fun createInstance(path: String): Directory? = createInstance(File(path))

        /**
         * Returns a [Directory] from [file] if `Directory` precondition holds; i.e. [file] is an existing directory.
         * Otherwise returns `null`.
         */
        fun createInstance(file: File): Directory? {
            if (!file.exists() || !file.isDirectory) {
                return null
            }
            return Directory(file)
        }
    }
}

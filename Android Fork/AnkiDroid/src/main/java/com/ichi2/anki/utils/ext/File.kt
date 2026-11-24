// SPDX-FileCopyrightText: 2026 Shaan Narendran <shaannaren06@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.utils.ext

import java.io.File

/**
 * @param name Relative file name or path inside this directory.
 * @return `true` if the named file exists within this directory
 *
 * @throws SecurityException If [SecurityManager.checkRead] is used and access is denied.
 */
fun File.containsFile(name: String): Boolean {
    if (!isDirectory) return false

    val maybeFile = File(this, name).canonicalFile

    // guard against path traversal
    if (!maybeFile.path.startsWith(this.canonicalPath + File.separator)) return false

    // ensure the file exists, and is not a directory
    return maybeFile.isFile
}

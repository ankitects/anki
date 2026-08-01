// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.compat

import com.ichi2.anki.model.Directory
import java.io.IOException
import java.nio.file.NotDirectoryException

/**
 * Whether this directory has at least one file
 * @return Whether the directory has a file.
 * @throws [SecurityException] If a security manager exists and its SecurityManager.checkRead(String)
 * method denies read access to the directory
 * @throws [java.io.FileNotFoundException] if the file does not exist
 * @throws [NotDirectoryException] if the file could not otherwise be opened because it is not
 * a directory (optional specific exception), (starting at API 26)
 * @throws [IOException] if an I/O error occurs.
 * This also occurred on an existing directory because of permission issue
 * that we could not reproduce. See https://github.com/ankidroid/Anki-Android/issues/10358
 */
@Throws(IOException::class, SecurityException::class, NotDirectoryException::class)
fun Directory.hasFiles(): Boolean = CompatHelper.compat.hasFiles(directory)

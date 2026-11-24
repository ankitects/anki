// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.startup

import android.content.Context
import android.system.Os
import timber.log.Timber
import java.io.File
import java.io.IOException

/** Ensures SQLite and Rust have a usable TMPDIR, falling back to the app cache (#19545). */
internal fun configureBackendTemporaryDirectory(context: Context) {
    val inheritedTmpDir = Os.getenv("TMPDIR")

    // handle a TMPDIR which is unusable
    val overwriteExistingValue =
        inheritedTmpDir.isNullOrEmpty() || !isUsableTemporaryDirectory(inheritedTmpDir)

    Os.setenv("TMPDIR", context.cacheDir.path, overwriteExistingValue)
}

/** Checks that temporary files can be created and removed in [path]. */
private fun isUsableTemporaryDirectory(path: String): Boolean =
    try {
        File.createTempFile("ankidroid-tmpdir-", null, File(path)).delete()
    } catch (e: IOException) {
        Timber.w(e, "Unable to use inherited TMPDIR: %s", path)
        false
    } catch (e: SecurityException) {
        Timber.w(e, "Unable to access inherited TMPDIR: %s", path)
        false
    }

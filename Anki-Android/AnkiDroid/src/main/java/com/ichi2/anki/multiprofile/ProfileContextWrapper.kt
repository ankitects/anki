/*
 * Copyright (c) 2025 Ashish Yadav <mailtoashish693@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.multiprofile

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.ichi2.utils.withFileNameSafe
import timber.log.Timber
import java.io.File
import java.io.IOException

/**
 * A ContextWrapper that redirects private file access to a specific profile's directory
 * and transparently namespaces SharedPreferences.
 *
 * Use [ProfileContextWrapper.create] to instantiate.
 */
class ProfileContextWrapper private constructor(
    base: Context,
    val profileId: ProfileId,
    private val profileBaseDir: File,
) : ContextWrapper(base) {
    override fun getFilesDir(): File =
        if (profileId.isDefault()) {
            super.getFilesDir()
        } else {
            File(profileBaseDir, "files").apply { mkdirs() }
        }

    override fun getCacheDir(): File =
        if (profileId.isDefault()) {
            super.getCacheDir()
        } else {
            File(profileBaseDir, "cache").apply { mkdirs() }
        }

    override fun getCodeCacheDir(): File =
        if (profileId.isDefault()) {
            super.getCodeCacheDir()
        } else {
            File(profileBaseDir, "code_cache").apply { mkdirs() }
        }

    override fun getNoBackupFilesDir(): File =
        if (profileId.isDefault()) {
            super.getNoBackupFilesDir()
        } else {
            File(profileBaseDir, "no_backup").apply { mkdirs() }
        }

    override fun getDatabasePath(name: String): File {
        if (profileId.isDefault()) {
            return super.getDatabasePath(name)
        }
        val dbDir = profileBaseDir.withFileNameSafe("databases").apply { mkdirs() }

        if (name == "init") return dbDir

        return dbDir.withFileNameSafe(name)
    }

    /**
     * Used for things like ACRA, textures, etc.
     */
    override fun getDir(
        name: String,
        mode: Int,
    ): File {
        if (profileId.isDefault()) {
            return super.getDir(name, mode)
        }

        // Prevent directory traversal
        return profileBaseDir.withFileNameSafe(name).apply { mkdirs() }
    }

    override fun getSharedPreferences(
        name: String,
        mode: Int,
    ): SharedPreferences {
        if (profileId.isDefault()) {
            return super.getSharedPreferences(name, mode)
        }

        val prefix = "profile_${profileId.value}_"
        if (name.startsWith(prefix)) {
            return super.getSharedPreferences(name, mode)
        }

        return super.getSharedPreferences("$prefix$name", mode)
    }

    /**
     * Creates the directories required for the Context Wrapper.
     *
     * @throws SecurityException If the resolved path escapes the parent directory.
     * @throws IOException If the directory could not be created
     */
    private fun requireCustomDirectories() {
        // Default uses the base context wrapper validation
        if (profileId.isDefault()) return

        // create/validate our custom baseDir
        if (profileBaseDir.exists()) {
            if (!profileBaseDir.isDirectory) {
                throw IOException("Profile root exists but is not a directory: $profileBaseDir")
            }
        } else {
            if (!profileBaseDir.mkdirs()) {
                throw IOException("Failed to create profile root directory: $profileBaseDir")
            }
        }

        fun File.mkdirsOrFail() {
            if (mkdirs() || exists()) return
            throw IOException("Failed to create directory: $absolutePath")
        }

        // create the subfolders
        this.filesDir.mkdirsOrFail()
        this.cacheDir.mkdirsOrFail()
        this.codeCacheDir.mkdirsOrFail()
        this.noBackupFilesDir.mkdirsOrFail()
        this.getDatabasePath("init").mkdirsOrFail()
    }

    companion object {
        /**
         * Factory method to safely create a ProfileContextWrapper.
         *
         * Side Effect: This method creates the physical directory structure on disk.
         *
         * @throws IllegalArgumentException If [profileBaseDir] is not inside the application's private storage.
         * @throws IOException If the profile directory structure cannot be created.
         * @throws SecurityException If a path traversal attack occurs
         */
        @JvmStatic
        fun create(
            context: Context,
            profileId: ProfileId,
            profileBaseDir: File,
        ): ProfileContextWrapper {
            requirePathInFilesDir(profileBaseDir, context)

            return ProfileContextWrapper(context, profileId, profileBaseDir).apply {
                requireCustomDirectories()
            }
        }

        /**
         * @throws IllegalArgumentException if `profileBaseDir` is not in [Context.getFilesDir]
         */
        private fun requirePathInFilesDir(
            profileBaseDir: File,
            context: Context,
        ) {
            val appDataRoot = context.filesDir.parentFile ?: return

            val validLocation =
                try {
                    profileBaseDir.canonicalPath.startsWith(appDataRoot.canonicalPath)
                } catch (e: IOException) {
                    Timber.e(e, "Failed to canonicalize path: %s", profileBaseDir)
                    false
                }

            if (!validLocation) {
                throw IllegalArgumentException("Profile path must be inside internal storage: $profileBaseDir")
            }
        }
    }
}

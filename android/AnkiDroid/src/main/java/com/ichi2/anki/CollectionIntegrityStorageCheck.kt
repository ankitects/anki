/*
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki

import android.content.Context
import android.text.format.Formatter
import com.ichi2.utils.FileUtil
import timber.log.Timber

/**
 * This currently stores either:
 * An error message stating the reason that a storage check must be performed
 * OR
 * The current storage requirements, and the current available storage.
 */
class CollectionIntegrityStorageCheck {
    private val errorMessage: String?

    // OR:
    private val requiredSpace: Long?
    private val freeSpace: Long?

    private constructor(requiredSpace: Long, freeSpace: Long) {
        this.freeSpace = freeSpace
        this.requiredSpace = requiredSpace
        errorMessage = null
    }

    private constructor(errorMessage: String) {
        requiredSpace = null
        freeSpace = null
        this.errorMessage = errorMessage
    }

    fun shouldWarnOnIntegrityCheck(): Boolean = errorMessage != null || fileSystemDoesNotHaveSpaceForBackup()

    private fun fileSystemDoesNotHaveSpaceForBackup(): Boolean {
        // only to be called when mErrorMessage == null
        if (freeSpace == null || requiredSpace == null) {
            Timber.e("fileSystemDoesNotHaveSpaceForBackup called in invalid state.")
            return true
        }
        Timber.d("Required Free Space: %d. Current: %d", requiredSpace, freeSpace)
        return requiredSpace > freeSpace
    }

    fun getWarningDetails(context: Context): String {
        if (errorMessage != null) {
            return errorMessage
        }
        if (freeSpace == null || requiredSpace == null) {
            Timber.e("CollectionIntegrityCheckStatus in an invalid state")
            val defaultRequiredFreeSpace = defaultRequiredFreeSpace(context)
            return context.resources.getString(
                R.string.integrity_check_insufficient_space,
                defaultRequiredFreeSpace,
            )
        }
        val required = Formatter.formatShortFileSize(context, requiredSpace)
        val insufficientSpace =
            context.resources.getString(
                R.string.integrity_check_insufficient_space,
                required,
            )

        // Also concat in the extra content showing the current free space.
        val currentFree = Formatter.formatShortFileSize(context, freeSpace)
        val insufficientSpaceCurrentFree =
            context.resources.getString(
                R.string.integrity_check_insufficient_space_extra_content,
                currentFree,
            )
        return insufficientSpace + insufficientSpaceCurrentFree
    }

    companion object {
        private fun fromError(errorMessage: String): CollectionIntegrityStorageCheck = CollectionIntegrityStorageCheck(errorMessage)

        private fun defaultRequiredFreeSpace(context: Context): String {
            val oneHundredFiftyMB =
                (150 * 1000 * 1000).toLong() // tested, 1024 displays 157MB. 1000 displays 150
            return Formatter.formatShortFileSize(context, oneHundredFiftyMB)
        }

        fun createInstance(context: Context): CollectionIntegrityStorageCheck {
            val maybeCurrentCollectionSizeInBytes = CollectionHelper.getCollectionSize(context)
            if (maybeCurrentCollectionSizeInBytes == null) {
                Timber.w("Error obtaining collection file size.")
                val requiredFreeSpace = defaultRequiredFreeSpace(context)
                return fromError(
                    context.resources.getString(
                        R.string.integrity_check_insufficient_space,
                        requiredFreeSpace,
                    ),
                )
            }

            // This means that when VACUUMing a database, as much as twice the size of the original database file is
            // required in free disk space. - https://www.sqlite.org/lang_vacuum.html
            val requiredSpaceInBytes = maybeCurrentCollectionSizeInBytes * 2

            // We currently use the same directory as the collection for VACUUM/ANALYZE due to the SQLite APIs
            val collectionFile = CollectionHelper.getCollectionPath(context)
            val freeSpace = FileUtil.getFreeDiskSpace(collectionFile, -1)
            if (freeSpace == -1L) {
                Timber.w("Error obtaining free space for '%s'", collectionFile.path)
                val readableFileSize = Formatter.formatFileSize(context, requiredSpaceInBytes)
                return fromError(
                    context.resources.getString(
                        R.string.integrity_check_insufficient_space,
                        readableFileSize,
                    ),
                )
            }
            return CollectionIntegrityStorageCheck(requiredSpaceInBytes, freeSpace)
        }
    }
}

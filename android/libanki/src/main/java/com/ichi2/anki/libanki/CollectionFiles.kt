/*
 *  Copyright (c) 2025 Arthur Milchior <arthur@milchior.fr>
 *  Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>
 *
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

package com.ichi2.anki.libanki

import androidx.annotation.VisibleForTesting
import com.ichi2.anki.libanki.utils.NotInPyLib
import java.io.File

/**
 * Accessors for `collection.anki2`, `collection.media` and `collection.media.db`
 *
 * When testing, some of these are unavailable (collection uses `:memory:`)
 */
@NotInPyLib
sealed class CollectionFiles {
    /** The 'standard' collection which AnkiDroid uses */
    class FolderBasedCollection(
        folderPath: File,
        collectionName: String = "collection",
    ) : CollectionFiles() {
        val colDb = File(folderPath, "$collectionName.anki2")
        override val mediaFolder = File(folderPath, "$collectionName.media")
        val mediaDb = File(folderPath, "$collectionName.media.db")
    }

    /** An in-memory database with no media files */
    @VisibleForTesting
    data object InMemory : CollectionFiles() {
        override val mediaFolder = null
    }

    /** An in-memory collection supporting media files */
    @VisibleForTesting
    class InMemoryWithMedia(
        override val mediaFolder: File,
    ) : CollectionFiles()

    /**
     * Returns the paths for a disk-based collection
     *
     * @throws UnsupportedOperationException if the collection is in-memory
     */
    fun requireDiskBasedCollection(): FolderBasedCollection =
        when (this) {
            is InMemory, is InMemoryWithMedia -> throw UnsupportedOperationException("collection is in-memory")
            is FolderBasedCollection -> this
        }

    /**
     * @return Path to the media folder (`collection.media`)
     *
     * @throws UnsupportedOperationException if the collection is in-memory
     */
    fun requireMediaFolder() =
        when (this) {
            is InMemory -> throw UnsupportedOperationException("collection is in-memory")
            is FolderBasedCollection -> this.mediaFolder
            is InMemoryWithMedia -> this.mediaFolder
        }

    abstract val mediaFolder: File?
}

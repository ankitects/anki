// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.startup

import com.ichi2.anki.StoragePermissionSet
import com.ichi2.anki.common.storage.AnkiDroidFolder
import com.ichi2.anki.common.storage.StorageDecision

/**
 * Who chooses the [AnkiDroidFolder] on a fresh install.
 *
 * Declared per [StoragePermissionSet] ([StoragePermissionSet.storagePolicy]) and applied by
 * [maybeSetDefaultCollectionPath].
 */
sealed interface StoragePolicy {
    /** Only one folder is possible, so it is persisted on startup. */
    data class Fixed(
        val folder: AnkiDroidFolder,
    ) : StoragePolicy

    /**
     * The user chooses on the permission screen: granting the permissions selects
     * [AnkiDroidFolder.PUBLIC], skipping them selects [AnkiDroidFolder.APP_PRIVATE].
     *
     * The [StorageDecision] is [StorageDecision.Undecided] until the screen is completed.
     */
    data object UserChoosesOnPermissionScreen : StoragePolicy
}

/**
 * The folder to persist on startup, or `null` if the user has yet to choose.
 *
 * @param hasRequiredPermissions [StoragePermissionSet.hasRequiredPermissions]
 */
fun StoragePolicy.folderToPersist(hasRequiredPermissions: Boolean): AnkiDroidFolder? =
    when (this) {
        is StoragePolicy.Fixed -> folder
        StoragePolicy.UserChoosesOnPermissionScreen -> if (hasRequiredPermissions) AnkiDroidFolder.PUBLIC else null
    }

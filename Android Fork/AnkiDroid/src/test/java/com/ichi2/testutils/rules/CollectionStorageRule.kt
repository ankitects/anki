// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils.rules

import com.ichi2.anki.CollectionManager
import com.ichi2.anki.libanki.CollectionFiles
import org.junit.rules.ExternalResource

/**
 * Use [collectionFiles] to configure the test's storage, typically using an in-memory collection.
 *
 * Use `null` for the default on-disk configuration.
 *
 * [collectionFiles] must be run after rules generating its temporary folders (if applicable).
 */
class CollectionStorageRule(
    private val collectionFiles: () -> CollectionFiles?,
) : ExternalResource() {
    private var previous: CollectionFiles? = null

    override fun before() {
        previous = CollectionManager.collectionFilesTestOverride
        CollectionManager.collectionFilesTestOverride = collectionFiles()
    }

    override fun after() {
        CollectionManager.collectionFilesTestOverride = previous
    }
}

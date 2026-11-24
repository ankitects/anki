// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import com.ichi2.anki.libanki.CollectionFiles
import org.junit.Rule
import org.junit.rules.TemporaryFolder

/** Native collection and media files in a fresh temporary directory, without Android services. */
open class OnDiskJvmTest : JvmTest() {
    @get:Rule(order = 0)
    val collectionDirectory = TemporaryFolder()

    override val collectionFiles: CollectionFiles
        get() = CollectionFiles.FolderBasedCollection(collectionDirectory.root)
}

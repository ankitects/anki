// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.libanki

import com.ichi2.anki.libanki.testutils.InMemoryAnkiTest
import com.ichi2.anki.libanki.testutils.InMemoryCollectionManager
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CollectionOnDiskTest : InMemoryAnkiTest() {
    @get:Rule
    var directory = TemporaryFolder()

    override val collectionManager =
        object : InMemoryCollectionManager() {
            override val collectionFiles: CollectionFiles
                get() =
                    CollectionFiles.InMemoryWithMedia(
                        directory.newFolder().apply {
                            delete()
                        },
                    )
        }

    @Test
    fun `media folder exists after collection created`() {
        assertThat("media ffolder exists", col.mediaFolder?.exists(), equalTo(true))
    }
}

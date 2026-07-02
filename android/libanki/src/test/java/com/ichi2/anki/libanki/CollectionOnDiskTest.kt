/*
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

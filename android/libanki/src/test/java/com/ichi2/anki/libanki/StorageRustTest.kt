/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.Test

class StorageRustTest : InMemoryAnkiTest() {
    @Test
    fun testModelCount() {
        val noteTypeNames = col.notetypes.all().map { x -> x.name }
        MatcherAssert.assertThat(
            noteTypeNames,
            Matchers.containsInAnyOrder(
                "Basic",
                "Basic (and reversed card)",
                "Cloze",
                "Basic (type in the answer)",
                "Basic (optional reversed card)",
                "Image Occlusion",
            ),
        )
    }
}

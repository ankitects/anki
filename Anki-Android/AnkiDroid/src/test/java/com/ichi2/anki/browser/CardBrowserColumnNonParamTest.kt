/*
 *  Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.browser

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.testutils.JvmTest
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertContentEquals

/** @see CardBrowserColumn */
@RunWith(AndroidJUnit4::class)
class CardBrowserColumnNonParamTest : JvmTest() {
    @Test
    fun `all keys are documented`() {
        // meta test - the column keys aren't documented well
        // this ensures the columns are greppable in the code
        val ankiColumnKeys =
            col.backend
                .allBrowserColumns()
                .map { it.key }
                .sorted()
        val ourKeys = CardBrowserColumn.entries.map { it.ankiColumnKey }.sorted()

        assertContentEquals(ankiColumnKeys, ourKeys)
    }
}

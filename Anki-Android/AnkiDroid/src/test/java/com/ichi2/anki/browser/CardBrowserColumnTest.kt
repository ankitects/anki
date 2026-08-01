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

import com.ichi2.testutils.JvmTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import timber.log.Timber
import kotlin.test.assertNotNull

/** @see CardBrowserColumn */
@RunWith(ParameterizedRobolectricTestRunner::class)
class CardBrowserColumnTest : JvmTest() {
    companion object {
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        @JvmStatic // required for initParameters
        fun initParameters(): Collection<Array<Any>> =
            CardBrowserColumn.entries
                .map { arrayOf(it) }
    }

    @ParameterizedRobolectricTestRunner.Parameter
    @JvmField // required for Parameter
    var columnParam: CardBrowserColumn? = null
    private val column get() = columnParam!!

    @Test
    fun ensureAllColumnsMapped() {
        val collectionColumns = col.backend.allBrowserColumns()
        Timber.w("%s", collectionColumns.joinToString { it.key })
        assertNotNull(collectionColumns.find(column))
    }
}

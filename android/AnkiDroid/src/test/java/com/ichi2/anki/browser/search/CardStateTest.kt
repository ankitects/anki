/*
 *  Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.browser.search

import com.ichi2.anki.EmptyApplicationCategory
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.libanki.testutils.AnkiTest
import com.ichi2.testutils.EmptyApplication
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/** Test for [CardState] */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class CardStateTest : RobolectricTest() {
    @ParameterizedRobolectricTestRunner.Parameter(0)
    @JvmField
    var stateParam: CardState? = null

    val state get() = stateParam!!

    @Test
    fun `search strings are valid`() {
        fun CardState.toExpectedSearchString() =
            when (this) {
                CardState.New -> "is:new"
                CardState.Learning -> "is:learn"
                CardState.Review -> "is:review"
                CardState.Buried -> "is:buried"
                CardState.Suspended -> "is:suspended"
            }

        assertEquals(state.toSearchString(), state.toExpectedSearchString())
    }

    @Test
    fun `backend label is unchanged`() {
        fun CardState.toExpectedLabel() =
            when (this) {
                CardState.New -> "New"
                CardState.Learning -> "Learning"
                CardState.Review -> "Review"
                CardState.Buried -> "Buried"
                CardState.Suspended -> "Suspended"
            }
        assertEquals(state.label, state.toExpectedLabel())
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun parameters(): Collection<Array<Any>> = CardState.entries.map { arrayOf(it) }
    }
}

context(test: AnkiTest)
fun CardState.toSearchString() = test.col.buildSearchString(listOf(this.toSearchNode()))

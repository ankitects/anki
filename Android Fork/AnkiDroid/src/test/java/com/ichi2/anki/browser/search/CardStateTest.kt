// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser.search

import com.ichi2.anki.libanki.testutils.AnkiTest
import com.ichi2.testutils.JvmTest
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.test.assertEquals

/** Test for [CardState] */
@RunWith(Parameterized::class)
class CardStateTest : JvmTest() {
    @Parameterized.Parameter(0)
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
        @Parameterized.Parameters(name = "{0}")
        fun parameters(): Collection<Array<Any>> = CardState.entries.map { arrayOf(it) }
    }
}

context(test: AnkiTest)
fun CardState.toSearchString() = test.col.buildSearchString(listOf(this.toSearchNode()))

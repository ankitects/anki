// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser.search

import kotlinx.serialization.json.Json
import org.junit.Test
import kotlin.test.assertEquals

/** Test for [CardStateSerializer] */
class CardStateSerializerTest {
    @Test
    fun `ensure mappings are unchanged`() {
        // CardState is serialized, so codes may not change
        val allKnownStates =
            listOf(
                CardState.New,
                CardState.Learning,
                CardState.Review,
                CardState.Buried,
                CardState.Suspended,
            )
        val encoded = Json.encodeToString(allKnownStates)
        assertEquals("[0,1,2,3,4]", encoded)

        // ensure no additional states were added
        assertEquals(allKnownStates, CardState.entries)
    }
}

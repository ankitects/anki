/*
 *  Copyright (c) 2025 Sanjay Sargam <sargamsanjaykumar@gmail.com>
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

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class PythonTypesTest {
    @Test
    fun test_deckId_toString_default_deck() {
        val deckId: DeckId = 1L
        assertThat(deckId.toString(), equalTo("1"))
    }

    @Test
    fun test_deckId_toString_user_created_deck() {
        val deckId: DeckId = 1428219222352L
        assertThat(deckId.toString(), equalTo("1428219222352"))
    }

    @Test
    fun test_deckId_toString_large_number() {
        val deckId: DeckId = Long.MAX_VALUE
        assertThat(deckId.toString(), equalTo("9223372036854775807"))
    }

    @Test
    fun test_deckId_toString_minimum_value() {
        val deckId: DeckId = Long.MIN_VALUE
        assertThat(deckId.toString(), equalTo("-9223372036854775808"))
    }
}

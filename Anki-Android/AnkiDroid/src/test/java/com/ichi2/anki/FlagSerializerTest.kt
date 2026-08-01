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

package com.ichi2.anki

import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.assertEquals

/** Tests [FlagSerializer] */
class FlagSerializerTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("flags")
    fun `round trip serialization`(flag: Flag) {
        val encoded = Json.encodeToString(flag)
        val decoded = Json.decodeFromString<Flag>(encoded)

        assertEquals(flag.code.toString(), encoded)
        assertEquals(flag, decoded)
    }

    @Test
    fun `flag list serialization round trip`() {
        val flags: List<Flag> = flags()

        val encoded = Json.encodeToString(flags)
        val decoded = Json.decodeFromString<List<Flag>>(encoded)

        // Assert that codes have not changed, or flags have not been removed or added
        assertEquals(encoded, "[0,1,2,3,4,5,6,7]")
        assertEquals(
            decoded,
            listOf(
                Flag.NONE,
                Flag.RED,
                Flag.ORANGE,
                Flag.GREEN,
                Flag.BLUE,
                Flag.PINK,
                Flag.TURQUOISE,
                Flag.PURPLE,
            ),
        )
    }

    companion object {
        @JvmStatic
        fun flags() = Flag.entries
    }
}

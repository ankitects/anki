/*
 * Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.multiprofile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileIdTest {
    @Test
    fun `DEFAULT is correctly identified`() {
        assertTrue(ProfileId.DEFAULT.isDefault())
        assertTrue(ProfileId("default").isDefault())
        assertTrue(!ProfileId("p_12345678").isDefault())
    }

    @Test
    fun `generate creates unique IDs using the purely random portion of a UUID`() {
        val iterations = 1000
        val generatedIds = List(iterations) { ProfileId.generate().value }

        for (id in generatedIds) {
            assertTrue("ID should start with p_", id.startsWith("p_"))
            assertEquals("ID length should be 10 (p_ + 8 hex chars)", 10, id.length)
        }

        val uniqueIds = generatedIds.toSet()
        assertEquals("All $iterations generated IDs should be unique", iterations, uniqueIds.size)

        val prefixLength = 2
        val hexLength = 8

        for (charIndex in prefixLength until prefixLength + hexLength) {
            val uniqueCharsAtThisPosition = generatedIds.map { it[charIndex] }.toSet()

            // If a position only ever has 1 unique character across 1000 generations,
            // it means it's a fixed bit (like the '4' in UUIDv4) or a sequential
            // timestamp prefix (like UUIDv7), which dramatically increases collision risk.
            assertTrue(
                "Position index $charIndex should be random, but was constantly '${uniqueCharsAtThisPosition.first()}'",
                uniqueCharsAtThisPosition.size > 1,
            )
        }
    }
}

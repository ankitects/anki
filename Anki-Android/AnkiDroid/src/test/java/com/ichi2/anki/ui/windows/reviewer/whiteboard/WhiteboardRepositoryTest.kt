/*
 * Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.ui.windows.reviewer.whiteboard

import org.junit.Test
import kotlin.test.assertEquals

class WhiteboardRepositoryTest {
    @Test
    fun `toolbar alignment enum values`() {
        // changing a value require a preference upgrade or using constants in the enum
        val values =
            listOf(
                "LEFT",
                "RIGHT",
                "BOTTOM",
            )
        val enumNames = ToolbarAlignment.entries.map { it.name }
        assertEquals(values, enumNames)
    }

    @Test
    fun `eraser enum values`() {
        // changing a value require a preference upgrade or using constants in the enum
        val values =
            listOf(
                "INK",
                "STROKE",
            )
        val enumNames = EraserMode.entries.map { it.name }
        assertEquals(values, enumNames)
    }
}

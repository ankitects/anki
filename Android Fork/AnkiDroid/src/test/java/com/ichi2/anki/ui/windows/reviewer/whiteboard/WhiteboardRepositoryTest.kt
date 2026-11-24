// SPDX-License-Identifier: GPL-3.0-or-later

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

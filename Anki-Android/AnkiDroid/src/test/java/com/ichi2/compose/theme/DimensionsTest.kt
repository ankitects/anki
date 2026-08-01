// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.compose.theme

import androidx.compose.ui.unit.dp
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the [Dimensions] spacing scale: the `spaceNNN` numeric tokens and the
 * semantic aliases that point at specific steps.
 */
class DimensionsTest {
    private val dimensions = Dimensions()

    @Test
    fun `numeric scale follows the 8dp-base convention`() {
        // spaceNNN == (NNN / 100) * 8dp
        assertEquals(2.dp, dimensions.space25)
        assertEquals(4.dp, dimensions.space50)
        assertEquals(8.dp, dimensions.space100)
        assertEquals(12.dp, dimensions.space150)
        assertEquals(16.dp, dimensions.space200)
        assertEquals(24.dp, dimensions.space300)
        assertEquals(32.dp, dimensions.space400)
        assertEquals(64.dp, dimensions.space800)
    }

    @Test
    fun `scale increases monotonically`() {
        val scale =
            listOf(
                dimensions.space25,
                dimensions.space50,
                dimensions.space100,
                dimensions.space150,
                dimensions.space200,
                dimensions.space300,
                dimensions.space400,
                dimensions.space800,
            )
        scale.zipWithNext { smaller, larger ->
            assertTrue(smaller < larger, "$smaller should be < $larger")
        }
    }

    @Test
    fun `semantic aliases map to their scale step`() {
        // Documented intent: screenEdge is the space300 step, sectionGap is space200.
        assertEquals(dimensions.space300, dimensions.screenEdge)
        assertEquals(dimensions.space200, dimensions.sectionGap)
    }

    @Test
    fun `copy overrides a single token and leaves the rest`() {
        // Used by previews / tests to provide a custom scale.
        val custom = dimensions.copy(screenEdge = 8.dp)
        assertEquals(8.dp, custom.screenEdge)
        assertEquals(16.dp, custom.sectionGap, "non-overridden tokens are unchanged")
    }
}

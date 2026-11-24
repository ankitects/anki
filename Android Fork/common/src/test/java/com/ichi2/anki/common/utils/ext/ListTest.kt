// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.utils.ext

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ListTest {
    @Test
    fun `indexOfOrNull returns first entry`() = assertEquals(0, listOf(1, 2, 1).indexOfOrNull(1))

    @Test
    fun `indexOfOrNull returns null if not found`() = assertNull(listOf(1, 2, 1).indexOfOrNull(3))

    @Test
    fun `indexOfOrNull returns null if list is empty`() = assertNull(listOf(1, 2, 1).indexOfOrNull(3))

    @Test
    fun `indexOfOrNull using block`() = assertEquals(1, listOf(1, 2, 1, 2).indexOfOrNull { it % 2 == 0 })

    @Test
    fun `indexOfOrNull using block returning null`() = assertNull(listOf(1, 2, 1).indexOfOrNull { it == 3 })
}

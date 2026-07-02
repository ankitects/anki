/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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
package com.ichi2.anki.reviewer

import android.view.ViewConfiguration
import com.ichi2.anki.cardviewer.Gesture
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test
import org.mockito.Mockito.mock
import kotlin.test.assertNull

class GestureMapperTest {
    private val sut = GestureMapper()

    @Test
    fun zeroWidthReturnsNothing() {
        assertNull(sut.gesture(0, 10, 10f, 10f))
    }

    @Test
    fun zeroHeightReturnsNothing() {
        assertNull(sut.gesture(10, 0, 10f, 10f))
    }

    @Test
    fun testOobTop() {
        assertEquals(Gesture.TAP_TOP, sut.gesture(100, 100, 50f, -5f))
    }

    @Test
    fun testOobLeft() {
        assertEquals(Gesture.TAP_LEFT, sut.gesture(100, 100, -10f, 50f))
    }

    @Test
    fun testOobRight() {
        assertEquals(Gesture.TAP_RIGHT, sut.gesture(100, 100, 200f, 50f))
    }

    @Test
    fun testOobBottom() {
        assertEquals(Gesture.TAP_BOTTOM, sut.gesture(100, 100, 50f, 200f))
    }

    @Test
    fun testCenter() {
        assertEquals(Gesture.TAP_CENTER, sut.gesture(100, 100, 50f, 50f))
    }

    companion object {
        @BeforeClass
        @JvmStatic // required for @BeforeClass
        fun before() {
            mockkStatic(ViewConfiguration::class)
            every { ViewConfiguration.get(any()) } answers { mock(ViewConfiguration::class.java) }
        }

        @JvmStatic // required for @AfterClass
        @AfterClass
        fun after() {
            unmockkStatic(ViewConfiguration::class)
        }
    }
}

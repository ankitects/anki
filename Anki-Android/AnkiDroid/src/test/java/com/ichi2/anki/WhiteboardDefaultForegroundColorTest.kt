/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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

import android.content.Intent
import android.graphics.Color
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

@RunWith(ParameterizedRobolectricTestRunner::class)
class WhiteboardDefaultForegroundColorTest : RobolectricTest() {
    @ParameterizedRobolectricTestRunner.Parameter
    @JvmField // required for Parameter
    var mIsInverted = false

    @ParameterizedRobolectricTestRunner.Parameter(1)
    @JvmField // required for Parameter
    var mExpectedResult = 0

    @Test
    fun testDefaultForegroundColor() {
        assertThat(foregroundColor, equalTo(mExpectedResult))
    }

    private val foregroundColor: Int
        get() {
            val mock: AbstractFlashcardViewer = super.startActivityNormallyOpenCollectionWithIntent(Reviewer::class.java, Intent())
            return Whiteboard(mock, true, mIsInverted).foregroundColor
        }

    companion object {
        @ParameterizedRobolectricTestRunner.Parameters
        @JvmStatic // required for initParameters
        fun initParameters(): Collection<Array<Any>> = mutableListOf((arrayOf(true, Color.WHITE)), arrayOf(false, Color.BLACK))
    }
}

// SPDX-License-Identifier: GPL-3.0-or-later

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

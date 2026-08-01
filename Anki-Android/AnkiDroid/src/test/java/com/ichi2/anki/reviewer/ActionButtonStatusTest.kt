/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.reviewer

import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.utils.annotation.KotlinCleanup
import com.ichi2.anki.preferences.PreferenceTestUtils
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsInAnyOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.any
import org.mockito.Mockito.mock
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class ActionButtonStatusTest : RobolectricTest() {
    @Test
    fun allCustomButtonsCanBeDisabled() {
        val reviewerExpectedKeys = customButtonsExpectedKeys
        val actualPreferenceKeys = PreferenceTestUtils.getAllCustomButtonKeys(targetContext)
        assertThat(
            "Each button in the Action Bar must be modifiable in Preferences - Reviewer - App Bar Buttons",
            reviewerExpectedKeys,
            containsInAnyOrder(*actualPreferenceKeys.toTypedArray()),
        )
    }

    @KotlinCleanup("Use SPMockBuilder")
    private val customButtonsExpectedKeys: Set<String>
        get() {
            val preferences = mock(SharedPreferences::class.java)
            val ret: MutableSet<String> = HashSet()
            whenever(preferences.getString(any(), any())).then { a: InvocationOnMock ->
                val key = a.getArgument<String>(0)
                ret.add(key)
                "0"
            }
            val status = ActionButtonStatus()
            status.setup(preferences)
            return ret
        }
}

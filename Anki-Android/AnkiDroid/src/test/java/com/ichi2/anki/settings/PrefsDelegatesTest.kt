/*
 * Copyright (c) 2025 Brayan Oliveira <brayandso.dev@gmail.com>
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
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.settings

import android.content.res.Resources
import com.github.ivanshafran.sharedpreferencesmock.SPMockBuilder
import com.ichi2.anki.settings.enums.PrefEnum
import io.mockk.every
import io.mockk.mockk
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test

class PrefsDelegatesTest {
    val prefs: PrefsRepository

    init {
        val sharedPrefs = SPMockBuilder().createSharedPreferences()
        val mockResources = mockk<Resources>()
        every { mockResources.getString(any()) } answers {
            val resId = invocation.args[0] as Int
            resId.toString()
        }
        prefs = PrefsRepository(sharedPrefs, mockResources)
    }

    @Test
    fun `booleanPref getter and setter work`() {
        var pref by prefs.booleanPref(123, false)
        assertThat(pref, equalTo(false))

        pref = true
        assertThat(pref, equalTo(true))
    }

    @Test
    fun `stringPref getter and setter work`() {
        var pref by prefs.stringPref(456, "defaultValue")
        assertThat(pref, equalTo("defaultValue"))

        pref = "newValue"
        assertThat(pref, equalTo("newValue"))
    }

    @Test
    fun `intPref getter and setter work`() {
        var pref by prefs.intPref(101, 42)
        assertThat(pref, equalTo(42))

        pref = 99
        assertThat(pref, equalTo(99))
    }

    @Test
    fun `longPref getter and setter work`() {
        var pref by prefs.longPref(202, 12345L)
        assertThat(pref, equalTo(12345L))

        pref = 9876543210L
        assertThat(pref, equalTo(9876543210L))
    }

    @Test
    fun `enumPref getter and setter work`() {
        var pref by prefs.enumPref(789, TestEnum.SECOND)
        assertThat(pref, equalTo(TestEnum.SECOND))

        pref = TestEnum.FIRST
        assertThat(pref, equalTo(TestEnum.FIRST))
    }

    private enum class TestEnum(
        override val entryResId: Int,
    ) : PrefEnum {
        FIRST(0),
        SECOND(1),
    }
}

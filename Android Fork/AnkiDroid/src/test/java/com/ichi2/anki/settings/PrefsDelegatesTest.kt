// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.settings

import com.ichi2.anki.settings.enums.PrefEnum
import com.ichi2.testutils.inMemoryPrefs
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test

class PrefsDelegatesTest {
    val prefs = inMemoryPrefs()

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

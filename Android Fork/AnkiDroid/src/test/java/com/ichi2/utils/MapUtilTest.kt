// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Mrudul Tora <mrudultora@gmail.com>

package com.ichi2.utils

import com.ichi2.utils.MapUtil.getKeyByValue
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.junit.Test

class MapUtilTest {
    private var map =
        hashMapOf(
            12 to "Anki",
            5 to "AnkiMobile",
            20 to "AnkiDroid",
            30 to "AnkiDesktop",
        )

    @Test
    fun getKeyByValueIsEqualTest() {
        assertThat(getKeyByValue(map, "AnkiDroid"), equalTo(20))
    }

    @Test
    fun getKeyByValueIsNotEqualTest() {
        assertThat(getKeyByValue(map, "AnkiDesktop"), not(5))
    }
}

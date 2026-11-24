// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.utils

import androidx.test.ext.junit.runners.AndroidJUnit4
import net.ankiweb.rsdroid.BackendFactory
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class LanguageUtilRobolectricTest {
    @Test
    @Config(qualifiers = "zn")
    fun `Language without region is set`() {
        assertEquals(BackendFactory.defaultLanguages, listOf("zn"))
    }

    @Test
    @Config(qualifiers = "zn-rTW")
    fun `Language with region is set`() {
        assertEquals(BackendFactory.defaultLanguages, listOf("zn-TW"))
    }
}

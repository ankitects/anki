// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Tarek Mohamed Abdalla <tarekkma@gmail.com>

package com.ichi2.ui

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.view.ContextThemeWrapper
import org.junit.Test
import org.junit.jupiter.api.assertThrows
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class RtlCompliantActionProviderTest {
    @Test
    fun test_unwrapContext_will_get_activity() {
        val a = Activity()
        val c: Context =
            ContextWrapper(
                ContextThemeWrapper(
                    ContextWrapper(
                        a,
                    ),
                    0,
                ),
            )
        val provider = RtlCompliantActionProvider(c)
        assertEquals(provider.activity, a)
    }

    @Test
    fun test_unwrapContext_will_throw_on_no_activity() {
        val a = Application()
        val c: Context =
            ContextWrapper(
                ContextThemeWrapper(
                    ContextWrapper(
                        a,
                    ),
                    0,
                ),
            )
        assertThrows<ClassCastException> { RtlCompliantActionProvider(c) }
    }
}

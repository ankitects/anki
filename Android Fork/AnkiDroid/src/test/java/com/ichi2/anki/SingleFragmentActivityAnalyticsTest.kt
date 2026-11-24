// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.common.analytics.Analytics
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [SingleFragmentActivity] hosts a dozen unrelated screens, so reporting the host
 * would file them all under one name.
 */
@RunWith(AndroidJUnit4::class)
class SingleFragmentActivityAnalyticsTest : RobolectricTest() {
    @Before
    override fun setUp() {
        super.setUp()
        mockkObject(Analytics)
        every { Analytics.sendAnalyticsScreenView(any<String>()) } returns Unit
    }

    @After
    override fun tearDown() {
        super.tearDown()
        unmockkObject(Analytics)
    }

    @Test
    fun `the hosted fragment is reported, not the host`() {
        startRegularActivity<SingleFragmentActivity>(DrawingFragment.getIntent(targetContext))

        verify { Analytics.sendAnalyticsScreenView("DrawingFragment") }
    }
}

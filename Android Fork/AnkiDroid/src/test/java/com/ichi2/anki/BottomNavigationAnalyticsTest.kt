// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.BottomNavController.NavigationItem
import com.ichi2.anki.common.analytics.Analytics
import com.ichi2.anki.settings.Prefs
import com.ichi2.testutils.withPref
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkObject
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.equalTo
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.android.controller.ActivityController

@RunWith(AndroidJUnit4::class)
class BottomNavigationAnalyticsTest : RobolectricTest() {
    private val screenViews = mutableListOf<String>()

    @Before
    override fun setUp() {
        super.setUp()
        mockkObject(Analytics)
        every { Analytics.sendAnalyticsScreenView(capture(screenViews)) } just runs
    }

    @After
    override fun tearDown() {
        super.tearDown()
        unmockkObject(Analytics)
    }

    @Test
    fun `each tab is reported under its screen name`() =
        withBottomNavigation {
            mapOf(
                NavigationItem.BROWSER to "CardBrowser",
                NavigationItem.STATS to "Statistics",
                NavigationItem.MORE to "MoreFragment",
                NavigationItem.HOME to "DeckPicker",
            ).forEach { (tab, screenName) ->
                assertThat(screenViewsDuring { select(tab) }, equalTo(listOf(screenName)))
            }
        }

    @Test
    fun `selecting the open tab again is not reported`() =
        withBottomNavigation {
            select(NavigationItem.BROWSER)

            assertThat(screenViewsDuring { select(NavigationItem.BROWSER) }, empty())
        }

    @Test
    fun `resuming reports the open tab, not the deck picker`() =
        withBottomNavigation {
            select(NavigationItem.BROWSER)

            assertThat(screenViewsDuring { pause().resume() }, equalTo(listOf("CardBrowser")))
        }

    @Test
    fun `recreating on a tab reports it once`() =
        withBottomNavigation {
            select(NavigationItem.BROWSER)

            assertThat(screenViewsDuring { recreate() }, equalTo(listOf("CardBrowser")))
        }

    private fun withBottomNavigation(test: ActivityController<DeckPicker>.() -> Unit) =
        withPref(Prefs::devBottomNavEnabled, true) {
            setIntroductionSlidesShown(true)
            startActivityControllerNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent()).test()
        }

    private fun screenViewsDuring(action: () -> Unit): List<String> {
        screenViews.clear()
        action()
        return screenViews.toList()
    }

    private fun ActivityController<DeckPicker>.select(tab: NavigationItem) {
        get().binding.bottomNavigation!!.selectedItemId = tab.id
        advanceRobolectricLooper()
    }
}

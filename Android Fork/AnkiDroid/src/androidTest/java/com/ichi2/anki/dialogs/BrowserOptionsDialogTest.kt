// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CardBrowser
import com.ichi2.anki.R
import com.ichi2.anki.model.CardsOrNotes
import com.ichi2.anki.tests.InstrumentedTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Tests [BrowserOptionsDialog] */
@RunWith(AndroidJUnit4::class)
class BrowserOptionsDialogTest : InstrumentedTest() {
    @get:Rule
    val activityRule = ActivityScenarioRule(CardBrowser::class.java)

    @Test
    fun dialogLoads() {
        activityRule.scenario.onActivity { activity ->
            BrowserOptionsDialog
                .newInstance(
                    CardsOrNotes.CARDS,
                    isTruncated = true,
                ).show(activity.supportFragmentManager, "BrowserOptionsDialog")
        }
        onView(withId(R.id.toggle_cards_notes_title))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
    }
}

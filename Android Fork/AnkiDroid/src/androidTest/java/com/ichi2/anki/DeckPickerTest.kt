// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Shridhar Goel <shridhar.goel@gmail.com>

package com.ichi2.anki

import android.view.View
import androidx.core.content.edit
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.ichi2.anki.TestUtils.isTablet
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.anki.testutil.GrantStoragePermission.storagePermission
import com.ichi2.anki.testutil.disableIntroductionSlide
import com.ichi2.anki.testutil.discardPreliminaryViews
import com.ichi2.anki.testutil.grantPermissions
import com.ichi2.anki.testutil.notificationPermission
import com.ichi2.anki.testutil.tapOnCountLayouts
import com.ichi2.anki.testutil.useResumedActivity
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class DeckPickerTest : InstrumentedTest() {
    @get:Rule
    val activityRule = ActivityScenarioRule(DeckPicker::class.java)

    @get:Rule
    val runtimePermissionRule = grantPermissions(storagePermission, notificationPermission)

    @Before
    fun before() {
        addNoteUsingBasicNoteType()
        disableIntroductionSlide()
        discardPreliminaryViews()
    }

    @Test
    fun checkIfClickOnCountsLayoutOpensStudyOptionsOnMobile() {
        // Run the test only on emulator.
        assumeTrue(isEmulator())

        // For mobile. If it is not a mobile, then test will be ignored.
        assumeTrue(!isTablet)

        // Go to RecyclerView item having "Test Deck" and click on the counts layout
        tapOnCountLayouts("Default")

        // ActivityScenarioRule only owns DeckPicker. Close Study Options before collection cleanup.
        useResumedActivity<StudyOptionsActivity> {
            onView(withId(R.id.studyoptions_frame)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun bottomNavigationFollowsDeckPickerLayoutWhenPreferenceIsSaved() {
        val key = testContext.getString(R.string.dev_bottom_nav_key)
        val preferences = testContext.sharedPrefs()
        val wasSaved = preferences.contains(key)
        val oldValue = preferences.getBoolean(key, false)
        try {
            preferences.edit(commit = true) { putBoolean(key, true) }
            activityRule.scenario.recreate()
            activityRule.scenario.onActivity { deckPicker ->
                val hasBottomNavLayout = deckPicker.resources.getBoolean(R.bool.bottom_navigation_available)
                assertEquals(!deckPicker.fragmented, hasBottomNavLayout)
                assertEquals(true, Prefs.devBottomNavEnabled)
                assertEquals(hasBottomNavLayout, deckPicker.bottomNavigationEnabled)
                assertEquals(hasBottomNavLayout, deckPicker.findViewById<View>(R.id.bottom_navigation) != null)
                assertEquals(if (hasBottomNavLayout) 1 else 0, deckPicker.deckPickerBinding.decks.itemDecorationCount)
            }
        } finally {
            preferences.edit(commit = true) {
                if (wasSaved) putBoolean(key, oldValue) else remove(key)
            }
        }
    }

    @Test
    fun checkIfStudyOptionsIsDisplayedOnTablet() {
        // Run the test only on emulator.
        assumeTrue(isEmulator())

        // For tablet. If it is not a tablet, then test will be ignored.
        assumeTrue(isTablet)

        // Check if currently open Fragment is StudyOptionsFragment
        onView(withId(R.id.studyoptions_fragment))
            .check(ViewAssertions.matches(isDisplayed()))
    }
}

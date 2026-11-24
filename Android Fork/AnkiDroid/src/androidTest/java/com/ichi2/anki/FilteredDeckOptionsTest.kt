// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.anki.tests.checkWithTimeout
import com.ichi2.anki.testutil.GrantStoragePermission
import com.ichi2.anki.testutil.disableIntroductionSlide
import com.ichi2.anki.testutil.grantPermissions
import com.ichi2.anki.testutil.useResumedActivity
import com.ichi2.anki.utils.ConfigAwareSingleFragmentActivity
import org.hamcrest.Matchers.allOf
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class FilteredDeckOptionsTest : InstrumentedTest() {
    @get:Rule
    val runtimePermissionRule = grantPermissions(GrantStoragePermission.storagePermission)

    @Before
    fun before() {
        disableIntroductionSlide()
    }

    @Test
    fun canOpenDeckOptions() {
        ActivityScenario.launch(DeckPicker::class.java).use { scenario ->
            scenario.onActivity { deckPicker ->
                val deckId = col.decks.newFiltered("Filtered Testing")
                deckPicker.viewModel.openDeckOptions(deckId)
            }
            useResumedActivity<ConfigAwareSingleFragmentActivity> {
                onView(withId(R.id.deck_name_input))
                    .checkWithTimeout(matches(allOf(isDisplayed(), withText("Filtered Testing"))))
            }
        }
    }
}

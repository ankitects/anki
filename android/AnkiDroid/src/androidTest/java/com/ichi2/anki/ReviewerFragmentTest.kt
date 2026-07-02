// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.core.content.edit
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.anki.tests.checkWithTimeout
import com.ichi2.anki.tests.libanki.RetryRule
import com.ichi2.anki.testutil.GrantStoragePermission.storagePermission
import com.ichi2.anki.testutil.closeBackupCollectionDialogIfExists
import com.ichi2.anki.testutil.closeGetStartedScreenIfExists
import com.ichi2.anki.testutil.grantPermissions
import com.ichi2.anki.testutil.notificationPermission
import com.ichi2.anki.testutil.reviewDeckWithName
import com.ichi2.anki.utils.ext.cardStateCustomizer
import com.ichi2.testutils.common.Flaky
import com.ichi2.testutils.common.OS
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ReviewerFragmentTest : InstrumentedTest() {
    // Launch IntroductionActivity instead of DeckPicker activity because in CI
    // builds, it seems to create IntroductionActivity after the DeckPicker,
    // causing the DeckPicker activity to be destroyed. As a consequence, this
    // will throw RootViewWithoutFocusException when Espresso tries to interact
    // with an already destroyed activity. By launching IntroductionActivity, we
    // ensure that IntroductionActivity is launched first and navigate to the
    // DeckPicker -> Reviewer activities
    @get:Rule
    val activityScenarioRule = ActivityScenarioRule(IntroductionActivity::class.java)

    @get:Rule
    val runtimePermissionRule = grantPermissions(storagePermission, notificationPermission)

    @get:Rule
    val retry = RetryRule(10)

    @Test
    @Flaky(os = OS.ALL, "Fails on CI with timing issues frequently")
    fun testCustomSchedulerWithCustomData() {
        setNewReviewer()
        col.cardStateCustomizer =
            """
            states.good.normal.review.easeFactor = 3.0;
            states.good.normal.review.scheduledDays = 123;
            customData.good.c += 1;
            """
        val note = addNoteUsingBasicNoteType("foo", "bar")
        val card = note.firstCard(col)
        val deck = col.decks.getLegacy(note.notetype.did)!!
        card.moveToReviewQueue()
        col.backend.updateCards(
            listOf(
                card
                    .toBackendCard()
                    .toBuilder()
                    .setCustomData("""{"c":1}""")
                    .build(),
            ),
            true,
        )

        closeGetStartedScreenIfExists()
        closeBackupCollectionDialogIfExists()
        reviewDeckWithName(deck.name)

        var cardFromDb = col.getCard(card.id).toBackendCard()
        assertThat(cardFromDb.easeFactor, equalTo(card.factor))
        assertThat(cardFromDb.interval, equalTo(card.ivl))
        assertThat(cardFromDb.customData, equalTo("""{"c":1}"""))

        clickShowAnswerAndAnswerGood()

        cardFromDb = col.getCard(card.id).toBackendCard()
        assertThat(cardFromDb.easeFactor, equalTo(3000))
        assertThat(cardFromDb.interval, equalTo(123))
        assertThat(cardFromDb.customData, equalTo("""{"c":2}"""))
    }

    @Test
    @Flaky(os = OS.ALL, "Fails on CI with timing issues frequently")
    fun testCustomSchedulerWithRuntimeError() {
        setNewReviewer()
        // Issue 15035 - runtime errors weren't handled
        col.cardStateCustomizer = "states.this_is_not_defined.normal.review = 12;"
        addNoteUsingBasicNoteType()

        closeGetStartedScreenIfExists()
        closeBackupCollectionDialogIfExists()
        reviewDeckWithName("Default")

        clickShowAnswer()

        ensureAnswerButtonsAreDisplayed()
    }

    private fun clickShowAnswerAndAnswerGood() {
        clickShowAnswer()
        ensureAnswerButtonsAreDisplayed()
        onView(withId(R.id.good_button)).perform(click())
    }

    private fun clickShowAnswer() {
        onView(withId(R.id.show_answer_button)).perform(click())
    }

    private fun ensureAnswerButtonsAreDisplayed() {
        // We need to wait for the card to fully load to allow enough time for
        // the messages to be passed in and out of the WebView when evaluating
        // the custom JS scheduler code. The ease buttons are hidden until the
        // custom scheduler has finished running
        onView(withId(R.id.good_button)).checkWithTimeout(
            matches(isDisplayed()),
            100,
            // Increase to a max of 30 seconds because CI builds can be very
            // slow
            TimeUnit.SECONDS.toMillis(30),
        )
    }

    private fun setNewReviewer() {
        testContext.sharedPrefs().edit {
            putBoolean("newReviewer", true)
            putBoolean("newReviewerOptions", true)
        }
    }
}

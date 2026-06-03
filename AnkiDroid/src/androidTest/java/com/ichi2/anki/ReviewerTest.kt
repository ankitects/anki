// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2023

package com.ichi2.anki

import androidx.core.content.edit
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withResourceName
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.anki.tests.checkWithTimeout
import com.ichi2.anki.tests.libanki.RetryRule
import com.ichi2.anki.testutil.GrantStoragePermission.storagePermission
import com.ichi2.anki.testutil.ThreadUtils
import com.ichi2.anki.testutil.closeBackupCollectionDialogIfExists
import com.ichi2.anki.testutil.closeGetStartedScreenIfExists
import com.ichi2.anki.testutil.grantPermissions
import com.ichi2.anki.testutil.notificationPermission
import com.ichi2.anki.utils.ext.cardStateCustomizer
import com.ichi2.testutils.common.Flaky
import com.ichi2.testutils.common.OS
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber
import java.lang.AssertionError

@RunWith(AndroidJUnit4::class)
class ReviewerTest : InstrumentedTest() {
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

    override fun runBeforeEachTest() {
        super.runBeforeEachTest()

        // 17298: for an unknown reason, we were using the beta Reviewer
        // This works on my MacBook, fails in CI
        // failure is due to the card not being flipped
        // since the feature is currently in beta and unexpectedly enabled, disable it
        // TODO: remove this
        disableNewReviewer()
    }

    @Test
    @Flaky(os = OS.ALL, "Fails on CI with timing issues frequently")
    fun testCustomSchedulerWithCustomData() {
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

        fun runAssertion() {
            cardFromDb = col.getCard(card.id).toBackendCard()
            assertThat(cardFromDb.easeFactor, equalTo(3000))
            assertThat(cardFromDb.interval, equalTo(123))
            assertThat(cardFromDb.customData, equalTo("""{"c":2}"""))
        }

        try {
            runAssertion()
        } catch (e: Exception) {
            // Give separate threads a greater chance of doing the custom scheduling
            // if the card scheduling values aren't updated immediately
            ThreadUtils.sleep(2000)
            runAssertion()
        }
    }

    @Test
    @Flaky(os = OS.ALL, "Fails on CI with timing issues frequently")
    fun testCustomSchedulerWithRuntimeError() {
        // Issue 15035 - runtime errors weren't handled
        col.cardStateCustomizer = "states.this_is_not_defined.normal.review = 12;"
        addNoteUsingBasicNoteType()

        closeGetStartedScreenIfExists()
        closeBackupCollectionDialogIfExists()
        reviewDeckWithName("Default")

        clickShowAnswer()

        ensureAnswerButtonsAreDisplayed()
    }

    private fun clickOnDeckWithName(deckName: String) {
        onView(withId(R.id.decks)).checkWithTimeout(matches(hasDescendant(withText(deckName))))
        onView(withId(R.id.decks)).perform(
            RecyclerViewActions.actionOnItem<RecyclerView.ViewHolder>(
                hasDescendant(withText(deckName)),
                click(),
            ),
        )
    }

    private fun clickOnStudyButtonIfExists() {
        onView(withId(R.id.studyoptions_start))
            .withFailureHandler { _, _ -> }
            .perform(click())
    }

    private fun reviewDeckWithName(deckName: String) {
        clickOnDeckWithName(deckName)
        // Adding cards directly to the database while in the Deck Picker screen
        // will not update the page with correct card counts. Hence, clicking
        // on the deck will bring us to the study options page where we need to
        // click on the Study button. If we have added cards to the database
        // before the Deck Picker screen has fully loaded, then we skip clicking
        // the Study button
        clickOnStudyButtonIfExists()
    }

    private fun clickShowAnswerAndAnswerGood() {
        clickShowAnswer()
        ensureAnswerButtonsAreDisplayed()
        try {
            // ...on the command line it has resource name "good_button"...
            onView(withResourceName("good_button")).perform(click())
        } catch (e: NoMatchingViewException) {
            // ...but in Android Studio it has resource name "flashcard_layout_ease3" !?
            onView(withResourceName("flashcard_layout_ease3")).perform(click())
        }
    }

    private fun clickShowAnswer() {
        try {
            // ... on the command line, it has resource name "show_answer"...
            onView(withResourceName("show_answer")).perform(click())
        } catch (e: NoMatchingViewException) {
            // ... but in Android Studio it has resource name "flashcard_layout_flip" !?
            onView(withResourceName("flashcard_layout_flip")).perform(click())
        }
    }

    private fun ensureAnswerButtonsAreDisplayed() {
        // We need to wait for the card to fully load to allow enough time for
        // the messages to be passed in and out of the WebView when evaluating
        // the custom JS scheduler code. The ease buttons are hidden until the
        // custom scheduler has finished running
        try {
            // ...on the command line it has resource name "good_button"...
            onView(withResourceName("good_button")).checkWithTimeout(
                matches(isDisplayed()),
                100,
            )
        } catch (e: AssertionError) {
            // ...but in Android Studio it has resource name "flashcard_layout_ease3" !?
            onView(withResourceName("flashcard_layout_ease3")).checkWithTimeout(
                matches(isDisplayed()),
                100,
            )
        }
    }

    private fun disableNewReviewer() {
        val newReviewerPrefKey = testContext.getString(R.string.new_reviewer_options_key)
        val prefs = testContext.sharedPrefs()
        val isUsingNewReviewer = prefs.getBoolean(newReviewerPrefKey, false)
        if (!isUsingNewReviewer) return

        Timber.w("unexpectedly using new reviewer: disabling it")
        prefs.edit {
            putBoolean(newReviewerPrefKey, false)
        }
    }
}

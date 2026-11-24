// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2023

package com.ichi2.anki

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.common.utils.ext.cardStateCustomizer
import com.ichi2.anki.libanki.Consts
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.anki.tests.checkWithTimeout
import com.ichi2.anki.testutil.AvoidDayRolloverRule
import com.ichi2.anki.testutil.GrantStoragePermission.storagePermission
import com.ichi2.anki.testutil.ensureWebViewIsSupported
import com.ichi2.anki.testutil.grantPermissions
import com.ichi2.anki.testutil.notificationPermission
import com.ichi2.anki.testutil.waitUntil
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.After
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ReviewerTest : InstrumentedTest() {
    @get:Rule(order = 0)
    val avoidDayRollover = AvoidDayRolloverRule()

    @get:Rule
    val runtimePermissionRule = grantPermissions(storagePermission, notificationPermission)

    /** The collection is shared between tests: review a deck which only contains this test's cards */
    private var testDeckId: DeckId = 0

    @After
    fun tearDown() {
        if (testDeckId != 0L) {
            col.decks.remove(listOf(testDeckId))
            col.decks.select(Consts.DEFAULT_DECK_ID)
            testDeckId = 0
        }
        col.config.cardStateCustomizer = ""
    }

    @Test
    fun testCustomSchedulerWithCustomData() {
        val testDeckName = "ReviewerTest-${UUID.randomUUID()}"
        testDeckId = col.decks.addNormalDeckWithName(testDeckName).id
        col.decks.select(testDeckId)

        col.config.cardStateCustomizer =
            """
            states.good.normal.review.easeFactor = 3.0;
            states.good.normal.review.scheduledDays = 123;
            customData.good.c += 1;
            """
        val note = addNoteUsingBasicNoteType("foo", "bar")
        val card = note.firstCard(col).update { did = testDeckId }
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

        withReviewer {
            var cardFromDb = col.getCard(card.id).toBackendCard()
            assertThat(cardFromDb.easeFactor, equalTo(card.factor))
            assertThat(cardFromDb.interval, equalTo(card.ivl))
            assertThat(cardFromDb.customData, equalTo("""{"c":1}"""))

            clickShowAnswerAndAnswerGood()
            // Answering runs on the IO dispatcher, which Espresso does not wait for.
            waitUntil(message = { "The review of card ${card.id} was not saved" }) {
                col.getCard(card.id).reps == card.reps + 1
            }

            cardFromDb = col.getCard(card.id).toBackendCard()
            assertThat(cardFromDb.easeFactor, equalTo(3000))
            assertThat(cardFromDb.interval, equalTo(123))
            assertThat(cardFromDb.customData, equalTo("""{"c":2}"""))
        }
    }

    @Test
    fun testCustomSchedulerWithRuntimeError() {
        val testDeckName = "ReviewerTest-${UUID.randomUUID()}"
        testDeckId = col.decks.addNormalDeckWithName(testDeckName).id
        col.decks.select(testDeckId)

        // Issue 15035 - runtime errors weren't handled
        col.config.cardStateCustomizer = "states.this_is_not_defined.normal.review = 12;"
        addNoteUsingBasicNoteType().firstCard(col).update { did = testDeckId }

        withReviewer {
            clickShowAnswer()

            ensureAnswerButtonsAreDisplayed()
        }
    }

    private fun withReviewer(block: () -> Unit) {
        // Issue 22912: a deck tap can become a long press on a slow emulator, opening its menu.
        // Launch the legacy reviewer explicitly and close it before collection/rollover cleanup.
        ActivityScenario.launch<Reviewer>(Intent(testContext, Reviewer::class.java)).use { block() }
    }

    private fun clickShowAnswerAndAnswerGood() {
        clickShowAnswer()
        ensureAnswerButtonsAreDisplayed()
        onView(withId(R.id.flashcard_layout_ease3)).perform(click())
    }

    private fun clickShowAnswer() {
        onView(withId(R.id.flashcard_layout_flip)).perform(click())
    }

    private fun ensureAnswerButtonsAreDisplayed() {
        // We need to wait for the card to fully load to allow enough time for
        // the messages to be passed in and out of the WebView when evaluating
        // the custom JS scheduler code. The ease buttons are hidden until the
        // custom scheduler has finished running
        onView(withId(R.id.flashcard_layout_ease3)).checkWithTimeout(
            matches(isDisplayed()),
            100,
        )
    }

    companion object {
        @JvmStatic
        @BeforeClass
        fun checkWebView() = ensureWebViewIsSupported()
    }
}

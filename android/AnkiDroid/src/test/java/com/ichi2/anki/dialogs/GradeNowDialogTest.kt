// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Bhaskar Patel <patel.bhaskar09@gmail.com>

package com.ichi2.anki.dialogs

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.ichi2.anki.CardBrowser
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.ui.internationalization.sentenceCase
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Tests [GradeNowDialog] */
@RunWith(RobolectricTestRunner::class)
class GradeNowDialogTest : RobolectricTest() {
    @Test
    fun dialogLoads() {
        val cardId = addBasicNote().firstCard().id
        val cardBrowser = super.startRegularActivity<CardBrowser>()

        val translatedTitle = with(cardBrowser) { TR.sentenceCase.gradeNow }

        GradeNowDialog.showDialog(cardBrowser, listOf(cardId))

        onView(withText(translatedTitle))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
    }

    @Test
    fun showsAllGradeOptions() {
        val cardId = addBasicNote().firstCard().id
        val cardBrowser = super.startRegularActivity<CardBrowser>()

        GradeNowDialog.showDialog(cardBrowser, listOf(cardId))

        onView(withText(TR.studyingAgain()))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))

        onView(withText(TR.studyingHard()))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))

        onView(withText(TR.studyingGood()))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))

        onView(withText(TR.studyingEasy()))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
    }

    @Test
    fun clickingGradeDismissesDialog() {
        val cardId = addBasicNote().firstCard().id
        val cardBrowser = super.startRegularActivity<CardBrowser>()

        val translatedTitle = with(cardBrowser) { TR.sentenceCase.gradeNow }

        GradeNowDialog.showDialog(cardBrowser, listOf(cardId))

        onView(withText(TR.studyingGood()))
            .inRoot(isDialog())
            .perform(click())

        onView(withText(translatedTitle))
            .check(doesNotExist())
    }

    @Test
    fun dialogNotShownIfNoCardsSelected() {
        val cardBrowser = super.startRegularActivity<CardBrowser>()
        val translatedTitle = with(cardBrowser) { TR.sentenceCase.gradeNow }

        GradeNowDialog.showDialog(cardBrowser, emptyList())

        onView(withText(translatedTitle))
            .check(doesNotExist())
    }
}

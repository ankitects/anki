// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 Arthur Milchior <arthur@milchior.fr>

package com.ichi2.anki.testutil

import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.TestUtils.clickChildViewWithId
import com.ichi2.anki.tests.checkWithTimeout
import com.ichi2.anki.testutil.ThreadUtils.sleep
import com.ichi2.anki.common.android.R as CommonR

/**
 * This file contains utility methods to interact with the DeckPicker.
 */

fun closeGetStartedScreenIfExists() {
    onView(withId(R.id.get_started)).withFailureHandler { _, _ -> }.perform(click())
}

fun closeBackupCollectionDialogIfExists() {
    onView(withText(CommonString.button_backup_later))
        .withFailureHandler { _, _ -> }
        .perform(click())
}

/**
 * Discard the Get Started and the Backup Collection dialog if they exists
 */
fun discardPreliminaryViews() {
    closeGetStartedScreenIfExists()
    closeBackupCollectionDialogIfExists()
}

/**
 * Create a deck with a unique name (based on the timestamp) return the name.
 * Must be called from a clean deck picker.
 */
fun createDeckWithUniqueName(): String {
    val testString = System.currentTimeMillis().toString()
    val deckName = "TestDeck$testString"
    onView(withId(R.id.fab_main)).perform(click())
    onView(withId(R.id.add_deck_button)).perform(click())
    onView(withId(CommonR.id.dialog_text_input)).perform(typeText(deckName))
    onView(withText(CommonString.dialog_ok)).perform(click())
    return deckName
}

fun tapOnCountLayouts(deckName: String) {
    onView(withId(R.id.decks)).perform(
        RecyclerViewActions.actionOnItem<RecyclerView.ViewHolder>(
            hasDescendant(withText(deckName)),
            clickChildViewWithId(R.id.counts_layout),
        ),
    )

    // without this sleep, the study options fragment sometimes loses the "load and become active" race vs the assertion below.
    // It actually won the race sometimes so sleeping a full second is generous. This should be quite stable
    sleep(1000)
}

/**
 * This must be called on a clean deck picker.
 */
fun clickOnDeckWithName(deckName: String) {
    onView(withId(R.id.decks)).checkWithTimeout(matches(hasDescendant(withText(deckName))))
    onView(withId(R.id.decks)).perform(
        RecyclerViewActions.actionOnItem<RecyclerView.ViewHolder>(
            hasDescendant(withText(deckName)),
            click(),
        ),
    )
}

/**
 * This must be called on a clean deck picker. It taps on the deck, and if needed on the "study" button.
 */
fun reviewDeckWithName(deckName: String) {
    clickOnDeckWithName(deckName)
    // Adding cards directly to the database while in the Deck Picker screen
    // will not update the page with correct card counts. Hence, clicking
    // on the deck will bring us to the study options page where we need to
    // click on the Study button. If we have added cards to the database
    // before the Deck Picker screen has fully loaded, then we skip clicking
    // the Study button
    clickOnStudyButtonIfExists()
}

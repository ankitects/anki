// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 Arthur Milchior <arthur@milchior.fr>

package com.ichi2.anki.testutil

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import com.ichi2.anki.R

/**
 * If the study options is displayed, tap on the "study" button. Otherwise do nothing.
 */
fun clickOnStudyButtonIfExists() {
    onView(withId(R.id.studyoptions_start))
        .withFailureHandler { _, _ -> }
        .perform(click())
}

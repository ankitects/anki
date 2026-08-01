// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 Eric Li <ericli3690@gmail.com>

package com.ichi2.anki.reviewreminders

import android.view.View
import androidx.fragment.app.FragmentActivity
import com.ichi2.anki.R
import com.ichi2.anki.ScreenshotTest
import com.ichi2.anki.reviewreminders.AddEditReminderDialog.DialogMode
import org.junit.Test
import org.robolectric.Robolectric.buildActivity

class AddEditReminderDialogScreenshotTest : ScreenshotTest() {
    @Test
    fun `add mode`() {
        val activity = buildActivity(FragmentActivity::class.java).setup().get()
        AddEditReminderDialog
            .getInstance(DialogMode.Add(ReviewReminderScope.Global))
            .show(activity.supportFragmentManager, "dialog")
        advanceRobolectricLooper()
        captureScreen("add_mode")

        val dialogFragment =
            activity.supportFragmentManager.findFragmentByTag("dialog") as AddEditReminderDialog
        dialogFragment.dialog?.findViewById<View>(R.id.add_edit_reminder_advanced_dropdown)?.performClick()
        advanceRobolectricLooper()
        captureScreen("add_mode_advanced_open")
    }

    @Test
    fun `edit mode`() {
        val deckId = addDeck("Test Deck")
        val reminder =
            ReviewReminder.createReviewReminder(
                time = ReviewReminderTime(9, 0),
                scope = ReviewReminderScope.DeckSpecific(deckId),
                onlyNotifyIfNoReviews = true,
            )
        val activity = buildActivity(FragmentActivity::class.java).setup().get()
        AddEditReminderDialog
            .getInstance(DialogMode.Edit(reminder))
            .show(activity.supportFragmentManager, "dialog")
        advanceRobolectricLooper()
        captureScreen("edit_mode")

        val dialogFragment =
            activity.supportFragmentManager.findFragmentByTag("dialog") as AddEditReminderDialog
        dialogFragment.dialog?.findViewById<View>(R.id.add_edit_reminder_advanced_dropdown)?.performClick()
        advanceRobolectricLooper()
        captureScreen("edit_mode_advanced_open")
    }
}

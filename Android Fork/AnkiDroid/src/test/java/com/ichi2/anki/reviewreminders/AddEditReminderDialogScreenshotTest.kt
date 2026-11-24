// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 Eric Li <ericli3690@gmail.com>

package com.ichi2.anki.reviewreminders

import android.provider.Settings
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.testing.junit.testparameterinjector.TestParameter
import com.ichi2.anki.ScreenshotTest
import com.ichi2.anki.reviewreminders.AddEditReminderDialog.DialogMode
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import com.google.android.material.R as MaterialR

class AddEditReminderDialogScreenshotTest : ScreenshotTest() {
    @Test
    fun `add mode`() {
        withReminderDialog(DialogMode.Add(ReviewReminderScope.Global)) {
            captureScreen("add_mode")

            binding.addEditReminderAdvancedDropdown.performClick()
            advanceRobolectricLooper()
            captureScreen("add_mode_advanced_open")
        }
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
        withReminderDialog(DialogMode.Edit(reminder)) {
            captureScreen("edit_mode")

            binding.addEditReminderAdvancedDropdown.performClick()
            advanceRobolectricLooper()
            captureScreen("edit_mode_advanced_open")
        }
    }

    @Test
    fun `card threshold entry`(
        @TestParameter landscape: Boolean,
    ) {
        val orientation = setOrientation(landscape)
        withReminderDialog(DialogMode.Add(ReviewReminderScope.Global)) {
            binding.addEditReminderAdvancedDropdown.performClick()
            binding.addEditReminderCardThresholdInput.apply {
                requestFocus()
                setText("25")
                setSelection(text!!.length)
            }
            advanceRobolectricLooper()
            binding.addEditReminderScrollview.fullScroll(View.FOCUS_DOWN)
            advanceRobolectricLooper()
            captureScreen("${orientation}_card_threshold_entry")
        }
    }

    @Test
    fun `delete confirmation dialog`() {
        val reminder = ReviewReminder.createReviewReminder(time = ReviewReminderTime(9, 0))
        withReminderDialog(DialogMode.Edit(reminder)) {
            (requireDialog() as AlertDialog).getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            advanceRobolectricLooper()
            captureScreen("delete_confirmation_dialog")
        }
    }

    @Test
    fun `time picker`(
        @TestParameter use24HourClock: Boolean,
    ) {
        captureTimePicker("timePicker", use24HourClock)
    }

    @Test
    fun `time picker in landscape`(
        @TestParameter use24HourClock: Boolean,
    ) {
        RuntimeEnvironment.setQualifiers("+land")
        captureTimePicker("timePicker_landscape", use24HourClock)
    }

    /**
     * The mode the picker opens in on a 24-hour clock, which [captureTimePicker] switches away from
     * to reach the dial. The layout is a fixed-size box, so one orientation covers it.
     */
    @Test
    fun `time picker text entry`() {
        Settings.System.putString(targetContext.contentResolver, Settings.System.TIME_12_24, "24")
        withTimePicker { captureScreen("timePicker_textEntry") }
    }

    private fun captureTimePicker(
        name: String,
        use24HourClock: Boolean,
    ) {
        val clock = if (use24HourClock) "24" else "12"
        Settings.System.putString(targetContext.contentResolver, Settings.System.TIME_12_24, clock)
        withTimePicker { timePicker ->
            // MaterialTimePicker opens in text entry on a 24-hour clock, so switch to the dial:
            // its inner 13-00 ring is the layout most exposed to `TimePickerStyle`'s colors
            if (use24HourClock) timePicker.switchInputMode()
            captureScreen("${name}_${clock}h")
        }
    }

    /** Opens the time picker of a reminder set to 17:45 */
    private fun withTimePicker(block: (MaterialTimePicker) -> Unit) {
        val reminder = ReviewReminder.createReviewReminder(time = ReviewReminderTime(17, 45))
        withReminderDialog(DialogMode.Edit(reminder)) {
            binding.addEditReminderTimeButton.performClick()
            advanceRobolectricLooper()
            val timePicker =
                parentFragmentManager.fragments
                    .filterIsInstance<MaterialTimePicker>()
                    .single()
            block(timePicker)
        }
    }

    /** Switches between the clock dial and text entry, as the mode button does */
    private fun MaterialTimePicker.switchInputMode() {
        requireDialog().findViewById<View>(MaterialR.id.material_timepicker_mode_button).performClick()
        advanceRobolectricLooper()
    }

    private fun withReminderDialog(
        mode: DialogMode,
        block: AddEditReminderDialog.() -> Unit,
    ) = withScheduleRemindersFragment { fragment ->
        val dialog = AddEditReminderDialog.getInstance(mode)
        dialog.show(fragment.childFragmentManager, "dialog")
        advanceRobolectricLooper()
        dialog.block()
    }
}

// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.fragment.app.Fragment
import com.ichi2.anki.browser.BrowserColumnSelectionFragment
import com.ichi2.anki.cardviewer.SingleCardSide
import com.ichi2.anki.dialogs.ChangeNoteTypeDialog
import com.ichi2.anki.dialogs.InsertFieldDialog
import com.ichi2.anki.dialogs.InsertFieldMetadata
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog
import com.ichi2.anki.filtered.FilteredDeckOptionsFragment
import com.ichi2.anki.mediacheck.MediaCheckFragment
import com.ichi2.anki.model.CardsOrNotes
import com.ichi2.anki.preferences.AboutFragment
import com.ichi2.anki.preferences.AdvancedSettingsFragment
import com.ichi2.anki.preferences.AppearanceSettingsFragment
import com.ichi2.anki.preferences.DeveloperOptionsFragment
import com.ichi2.anki.preferences.GeneralSettingsFragment
import com.ichi2.anki.preferences.ReviewerOptionsFragment
import com.ichi2.anki.preferences.ReviewingSettingsFragment
import com.ichi2.anki.preferences.SyncSettingsFragment
import com.ichi2.anki.reviewreminders.AddEditReminderDialog
import com.ichi2.anki.reviewreminders.ReviewReminderScope
import com.ichi2.anki.reviewreminders.ScheduleRemindersFragment
import com.ichi2.anki.ui.windows.managespace.ManageSpaceFragment

/** A screen and its required setup for [FragmentCoroutineLifecycleTest]. */
class FragmentLifecycleFixture(
    private val name: String,
    val host: Class<out AnkiActivity> = AnkiActivity::class.java,
    val hasFragmentView: Boolean = true,
    val create: RobolectricTest.() -> Fragment,
) {
    enum class Transition { REMOVE, DESTROY_VIEW, RECREATE_VIEW }

    override fun toString(): String = name
}

fun fragmentLifecycleFixtures(): List<FragmentLifecycleFixture> =
    listOf(
        FragmentLifecycleFixture("StudyOptions") { StudyOptionsFragment() },
        FragmentLifecycleFixture("AppearanceSettings") { AppearanceSettingsFragment() },
        FragmentLifecycleFixture("GeneralSettings") { GeneralSettingsFragment() },
        FragmentLifecycleFixture("ReviewerOptions") { ReviewerOptionsFragment() },
        FragmentLifecycleFixture("ReviewingSettings") { ReviewingSettingsFragment() },
        FragmentLifecycleFixture("AdvancedSettings") { AdvancedSettingsFragment() },
        FragmentLifecycleFixture("DeveloperOptions") { DeveloperOptionsFragment() },
        FragmentLifecycleFixture("SyncSettings") { SyncSettingsFragment() },
        FragmentLifecycleFixture("About") { AboutFragment() },
        FragmentLifecycleFixture("ScheduleReminders") { ScheduleRemindersFragment() },
        FragmentLifecycleFixture("FilteredDeckOptions") {
            FilteredDeckOptionsFragment()
        },
        FragmentLifecycleFixture("MediaCheck") { MediaCheckFragment() },
        FragmentLifecycleFixture("ManageSpace") { ManageSpaceFragment() },
        FragmentLifecycleFixture("ChangeNoteType") {
            ChangeNoteTypeDialog.newInstance(listOf(addBasicNote().id))
        },
        FragmentLifecycleFixture("CustomStudy", hasFragmentView = false) {
            // Empty decks dismiss the dialog instead of remaining resumed.
            addBasicNote()
            CustomStudyDialog.createInstance(col.decks.selected())
        },
        FragmentLifecycleFixture("AddReminder", hasFragmentView = false) {
            AddEditReminderDialog.getInstance(AddEditReminderDialog.DialogMode.Add(ReviewReminderScope.Global))
        },
        FragmentLifecycleFixture("InsertField") {
            InsertFieldDialog.newInstance(
                fieldItems = listOf("Front", "Back"),
                metadata = InsertFieldMetadata(SingleCardSide.FRONT, "Card 1", "Basic", null, null, null, "Default"),
                requestKey = "insert-field-test",
            )
        },
        FragmentLifecycleFixture("BrowserColumns", host = CardBrowser::class.java) {
            BrowserColumnSelectionFragment.createInstance(CardsOrNotes.CARDS)
        },
    )

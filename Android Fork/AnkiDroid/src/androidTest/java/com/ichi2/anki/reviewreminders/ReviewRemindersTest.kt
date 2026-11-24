// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.reviewreminders

import android.view.View
import androidx.core.content.edit
import androidx.fragment.app.findFragment
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions.actionOnItem
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.timepicker.MaterialTimePicker
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.TestUtils.clickChildViewWithId
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.anki.tests.checkWithTimeout
import com.ichi2.anki.testutil.GrantStoragePermission
import com.ichi2.anki.testutil.waitUntil
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.anki.utils.ConfigAwareSingleFragmentActivity
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matcher
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.hamcrest.Matchers.lessThanOrEqualTo
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.google.android.material.R as MaterialR
import com.ichi2.anki.ankicommon.R as AnkiCommonR

/** Exercises the reminder dialogs through the UI and checks their persisted results. */
@RunWith(AndroidJUnit4::class)
class ReviewRemindersTest : InstrumentedTest() {
    @get:Rule
    val storagePermissionRule = GrantStoragePermission.instance

    private var originalReminderNotifsRequestShown = false

    private val allDecksName: String
        get() = with(testContext) { TR.sentenceCase.allDecks }

    @Before
    fun setUpReminders() {
        CollectionManager.setColForTests(emptyCol)
        ReviewRemindersDatabase.remindersSharedPrefs.edit { clear() }
        originalReminderNotifsRequestShown = Prefs.reminderNotifsRequestShown
        Prefs.reminderNotifsRequestShown = true
    }

    @After
    fun tearDownReminders() {
        storedReminders().forEach {
            ReviewReminderAlarmManager.unscheduleReviewReminderNotifications(testContext, it)
        }
        ReviewRemindersDatabase.remindersSharedPrefs.edit { clear() }
        Prefs.reminderNotifsRequestShown = originalReminderNotifsRequestShown
    }

    @Test
    fun createReminderWithDefaults() {
        withReminders {
            openAddDialog()
            onView(withId(android.R.id.button1)).perform(click())

            val reminder = awaitSingleReminder()
            assertThat(reminder.scope, equalTo(ReviewReminderScope.Global))
            assertThat(reminder.cardTriggerThreshold, equalTo(ReviewReminderCardTriggerThreshold(1)))
            assertThat(reminder.enabled, equalTo(true))
            assertThat(reminder.onlyNotifyIfNoReviews, equalTo(false))
            assertReminderRow(reminder, allDecksName)
        }
        withReminders(reminderCount = 1) {
            assertReminderRow(storedReminders().single(), allDecksName)
        }
    }

    @Test
    fun customizeReminderBeforeCreatingIt() {
        val deckName = "Reminder deck"
        val deckId = col.decks.id(deckName)
        val time = ReviewReminderTime(9, 15)
        withReminders {
            openAddDialog()
            selectDeck(deckName)
            selectTime(time)
            openAdvancedSettings()
            onView(withId(R.id.add_edit_reminder_card_threshold_input)).perform(scrollTo(), replaceText("5"), closeSoftKeyboard())
            onView(withId(R.id.add_edit_reminder_only_notify_if_no_reviews_checkbox)).perform(scrollTo(), click())
            onView(withId(android.R.id.button1)).perform(click())

            val reminder = awaitSingleReminder()
            assertThat(reminder.scope, equalTo(ReviewReminderScope.DeckSpecific(deckId)))
            assertThat(reminder.time, equalTo(time))
            assertThat(reminder.cardTriggerThreshold, equalTo(ReviewReminderCardTriggerThreshold(5)))
            assertThat(reminder.onlyNotifyIfNoReviews, equalTo(true))
            assertThat(reminder.enabled, equalTo(true))
            assertReminderRow(reminder, deckName)
        }
        withReminders(scope = ReviewReminderScope.DeckSpecific(deckId), reminderCount = 1) {
            assertReminderRow(storedReminders().single(), deckName)
            onView(withId(R.id.reminders_list_time_text)).perform(click())
            openAdvancedSettings()
            onView(withId(R.id.add_edit_reminder_card_threshold_input)).check(matches(withText("5")))
            onView(withId(R.id.add_edit_reminder_only_notify_if_no_reviews_checkbox)).check(matches(isChecked()))
        }
    }

    @Test
    fun cancelCreatingReminder() {
        withReminders {
            openAddDialog()
            onView(withId(android.R.id.button3)).perform(click())

            onView(withId(R.id.add_edit_reminder_toolbar)).check(doesNotExist())
            onView(withId(R.id.no_reminders_placeholder)).check(matches(isDisplayed()))
            assertThat(storedReminders(), empty())
        }
    }

    @Test
    fun editReminderChangingDeckAndSettings() {
        val originalScope = ReviewReminderScope.DeckSpecific(col.decks.id("Original deck"))
        val destinationDeckName = "Destination deck"
        val destinationScope = ReviewReminderScope.DeckSpecific(col.decks.id(destinationDeckName))
        val original =
            ReviewReminder
                .createReviewReminder(
                    time = ReviewReminderTime(7, 30),
                    cardTriggerThreshold = ReviewReminderCardTriggerThreshold(2),
                    scope = originalScope,
                    enabled = false,
                ).apply { latestNotifTime = 1L }
        insertReminder(original)

        val updatedTime = ReviewReminderTime(9, 45)
        withReminders(reminderCount = 1) {
            assertReminderRow(original, "Original deck")
            onView(withId(R.id.reminders_list_time_text)).perform(click())
            onView(withId(R.id.add_edit_reminder_deck_name)).checkWithTimeout(matches(withText("Original deck")))
            onView(
                withId(R.id.add_edit_reminder_time_button),
            ).checkWithTimeout(matches(withText(original.time.toFormattedString(testContext))))
            openAdvancedSettings()
            onView(withId(R.id.add_edit_reminder_card_threshold_input)).check(matches(withText("2")))
            onView(withId(R.id.add_edit_reminder_only_notify_if_no_reviews_checkbox)).check(matches(not(isChecked())))

            selectDeck(destinationDeckName)
            selectTime(updatedTime)
            onView(withId(R.id.add_edit_reminder_card_threshold_input)).perform(scrollTo(), replaceText("7"), closeSoftKeyboard())
            onView(withId(R.id.add_edit_reminder_only_notify_if_no_reviews_checkbox)).perform(scrollTo(), click())
            val beforeSave = TimeManager.time.intTimeMS()
            onView(withId(android.R.id.button1)).perform(click())

            val updated = awaitSingleReminder { it.scope == destinationScope }
            val afterSave = TimeManager.time.intTimeMS()
            assertThat(updated.time, equalTo(updatedTime))
            assertThat(updated.cardTriggerThreshold, equalTo(ReviewReminderCardTriggerThreshold(7)))
            assertThat(updated.onlyNotifyIfNoReviews, equalTo(true))
            assertThat(updated.enabled, equalTo(false))
            assertThat(
                "Editing must reset the last notification time to now",
                updated.latestNotifTime,
                allOf(greaterThanOrEqualTo(beforeSave), lessThanOrEqualTo(afterSave)),
            )
            assertThat(runBlocking { ReviewRemindersDatabase.getRemindersForScope(originalScope).isEmpty() }, equalTo(true))
            assertReminderRow(updated, destinationDeckName)
        }
        withReminders(scope = destinationScope, reminderCount = 1) {
            assertReminderRow(storedReminders().single(), destinationDeckName)
            onView(withId(R.id.reminders_list_time_text)).perform(click())
            openAdvancedSettings()
            onView(withId(R.id.add_edit_reminder_card_threshold_input)).check(matches(withText("7")))
            onView(withId(R.id.add_edit_reminder_only_notify_if_no_reviews_checkbox)).check(matches(isChecked()))
        }
        withReminders(scope = originalScope) {
            onView(withId(R.id.no_reminders_placeholder)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun cancelEditingReminder() {
        val original = ReviewReminder.createReviewReminder(time = ReviewReminderTime(7, 30))
        insertReminder(original)
        withReminders(reminderCount = 1) {
            onView(withId(R.id.reminders_list_time_text)).perform(click())
            selectTime(ReviewReminderTime(9, 45))
            onView(withId(android.R.id.button3)).perform(click())

            onView(withId(R.id.add_edit_reminder_toolbar)).check(doesNotExist())
            assertThat(storedReminders(), equalTo(listOf(original)))
            assertReminderRow(original, allDecksName)
        }
        withReminders(reminderCount = 1) {
            assertReminderRow(original, allDecksName)
        }
    }

    @Test
    fun deleteLastReminder() {
        val reminder = ReviewReminder.createReviewReminder(time = ReviewReminderTime(9, 15))
        insertReminder(reminder)
        withReminders(reminderCount = 1) {
            onView(withId(R.id.reminders_list_time_text)).perform(click())
            openDeleteConfirmation()
            assertThat("Deletion must wait for confirmation", storedReminders(), equalTo(listOf(reminder)))
            onView(withId(android.R.id.button1)).perform(click())

            waitUntil(message = { "The deleted reminder is still stored: ${storedReminders()}" }) { storedReminders().isEmpty() }
            assertReminderCount(0)
        }
        withReminders {
            onView(withId(R.id.no_reminders_placeholder)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun cancelDeletingReminder() {
        val reminder = ReviewReminder.createReviewReminder(time = ReviewReminderTime(9, 15))
        insertReminder(reminder)
        withReminders(reminderCount = 1) {
            onView(withId(R.id.reminders_list_time_text)).perform(click())
            openDeleteConfirmation()
            onView(withId(android.R.id.button2)).perform(click())

            onView(withText("Delete this reminder?")).check(doesNotExist())
            assertThat(storedReminders(), equalTo(listOf(reminder)))
            onView(withId(R.id.add_edit_reminder_toolbar)).check(matches(isDisplayed()))
            onView(withId(android.R.id.button3)).perform(click())
            assertReminderRow(reminder, allDecksName)
        }
        withReminders(reminderCount = 1) {
            assertReminderRow(reminder, allDecksName)
        }
    }

    @Test
    fun deleteReminderForDeletedDeck() {
        val deckId = col.decks.id("Deck to delete")
        val orphan =
            ReviewReminder.createReviewReminder(
                time = ReviewReminderTime(9, 15),
                scope = ReviewReminderScope.DeckSpecific(deckId),
            )
        val remaining = ReviewReminder.createReviewReminder(time = ReviewReminderTime(10, 30))
        insertReminder(orphan)
        insertReminder(remaining)
        col.decks.remove(listOf(deckId))

        withReminders(reminderCount = 2) {
            onView(withText("Deck not found")).checkWithTimeout(matches(isDisplayed()))
            onView(withText("Deck not found")).perform(click())
            openDeleteConfirmation()
            onView(withId(android.R.id.button1)).perform(click())

            assertThat(awaitSingleReminder { it.id == remaining.id }, equalTo(remaining))
            assertThat(runBlocking { ReviewRemindersDatabase.getRemindersForScope(orphan.scope).isEmpty() }, equalTo(true))
            assertReminderRow(remaining, allDecksName)
        }
        withReminders(reminderCount = 1) {
            assertReminderRow(remaining, allDecksName)
        }
    }

    @Test
    fun disableAndEnableReminder() {
        val deckName = "Reminder deck"
        val reminder =
            ReviewReminder.createReviewReminder(
                time = ReviewReminderTime(9, 15),
                scope = ReviewReminderScope.DeckSpecific(col.decks.id(deckName)),
                cardTriggerThreshold = ReviewReminderCardTriggerThreshold(5),
                onlyNotifyIfNoReviews = true,
            )
        insertReminder(reminder)

        withReminders(reminderCount = 1) {
            assertReminderRow(reminder, deckName)
            onView(withId(R.id.reminders_list_switch)).perform(click())

            val disabled = awaitSingleReminder { !it.enabled }
            reminder.enabled = false
            assertThat("Disabling must preserve the reminder's other settings", disabled, equalTo(reminder))
            assertReminderRow(disabled, deckName)
            onView(withId(R.id.add_edit_reminder_toolbar)).check(doesNotExist())
        }
        withReminders(reminderCount = 1) {
            assertReminderRow(reminder, deckName)
            onView(withId(R.id.reminders_list_switch)).perform(click())

            val enabled = awaitSingleReminder { it.enabled }
            reminder.enabled = true
            assertThat("Enabling must preserve the reminder's other settings", enabled, equalTo(reminder))
            assertReminderRow(enabled, deckName)
            onView(withId(R.id.add_edit_reminder_toolbar)).check(doesNotExist())
        }
        withReminders(reminderCount = 1) {
            assertReminderRow(reminder, deckName)
        }
    }

    private fun withReminders(
        scope: ReviewReminderScope = ReviewReminderScope.Global,
        reminderCount: Int = 0,
        action: () -> Unit,
    ) {
        ActivityScenario
            .launch<ConfigAwareSingleFragmentActivity>(ScheduleRemindersFragment.getIntent(testContext, scope))
            .use {
                assertReminderCount(reminderCount)
                action()
            }
    }

    private fun openAddDialog() {
        onView(withId(R.id.floating_action_button_add)).perform(click())
        onView(withId(R.id.add_edit_reminder_deck_name)).checkWithTimeout(matches(withText(allDecksName)))
    }

    private fun openDeleteConfirmation() {
        onView(withId(android.R.id.button2)).perform(click())
        onView(withText("Delete this reminder?")).check(matches(isDisplayed()))
    }

    /** Expanding the advanced section travels through the view model, so wait for its content. */
    private fun openAdvancedSettings() {
        onView(withId(R.id.add_edit_reminder_advanced_dropdown)).perform(click())
        onView(withId(R.id.add_edit_reminder_card_threshold_input)).checkWithTimeout(matches(isDisplayed()))
    }

    private fun selectDeck(deckName: String) {
        onView(withId(R.id.add_edit_reminder_deck_name)).perform(click())
        onView(withText(deckName)).checkWithTimeout(matches(isDisplayed()))
        // .click() can open "Create subdeck", so invoke the handler directly
        onView(withId(AnkiCommonR.id.decks)).perform(
            actionOnItem<RecyclerView.ViewHolder>(
                hasDescendant(withText(deckName)),
                clickChildViewWithId(AnkiCommonR.id.DeckPickerHoriz),
            ),
        )
        onView(withId(R.id.add_edit_reminder_deck_name)).inRoot(isDialog()).checkWithTimeout(matches(withText(deckName)))
    }

    /** Sets the time on the picker directly, as a tap on its mode button can become a long press that only shows a tooltip. */
    private fun selectTime(time: ReviewReminderTime) {
        onView(withId(R.id.add_edit_reminder_time_button)).inRoot(isDialog()).perform(click())
        onView(withId(MaterialR.id.material_timepicker_ok_button)).inRoot(isDialog()).perform(confirmTime(time))
        onView(withId(R.id.add_edit_reminder_time_button))
            .inRoot(isDialog())
            .checkWithTimeout(matches(withText(time.toFormattedString(testContext))))
    }

    private fun confirmTime(time: ReviewReminderTime) =
        object : ViewAction {
            override fun getConstraints(): Matcher<View> = isDisplayed()

            override fun getDescription(): String = "confirm $time in the time picker"

            override fun perform(
                uiController: UiController,
                view: View,
            ) {
                view.findFragment<MaterialTimePicker>().apply {
                    hour = time.hour
                    minute = time.minute
                }
                view.performClick()
            }
        }

    private fun storedReminders(): List<ReviewReminder> = runBlocking { ReviewRemindersDatabase.getAllReminders().getRemindersList() }

    private fun insertReminder(reminder: ReviewReminder) = runBlocking { ReviewRemindersDatabase.insertReminder(reminder) }

    private fun awaitSingleReminder(predicate: (ReviewReminder) -> Boolean = { true }): ReviewReminder {
        waitUntil(message = { "Expected one saved reminder matching the changes, found ${storedReminders()}" }) {
            storedReminders().singleOrNull()?.let(predicate) == true
        }
        return storedReminders().single()
    }

    private fun assertReminderCount(count: Int) {
        onView(withId(R.id.recycler_view)).checkWithTimeout(
            { view, exception ->
                if (exception != null) throw exception
                assertThat((view as RecyclerView).adapter!!.itemCount, equalTo(count))
            },
        )
        onView(withId(R.id.no_reminders_placeholder)).checkWithTimeout(matches(if (count == 0) isDisplayed() else not(isDisplayed())))
    }

    private fun assertReminderRow(
        reminder: ReviewReminder,
        deckName: String,
    ) {
        assertReminderCount(1)
        onView(withId(R.id.reminders_list_deck_text)).checkWithTimeout(matches(withText(deckName)))
        onView(withId(R.id.reminders_list_time_text)).check(matches(withText(reminder.time.toFormattedString(testContext))))
        onView(withId(R.id.reminders_list_switch)).check(matches(if (reminder.enabled) isChecked() else not(isChecked())))
    }
}

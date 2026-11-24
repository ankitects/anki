// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 Eric Li <ericli3690@gmail.com>

package com.ichi2.anki.reviewreminders

import android.app.NotificationManager
import androidx.annotation.IdRes
import androidx.core.content.edit
import androidx.core.content.getSystemService
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.test.core.app.ActivityScenario
import com.google.android.material.appbar.AppBarLayout
import com.ichi2.anki.OptionalPermissionSet
import com.ichi2.anki.R
import com.ichi2.anki.ScreenshotTest
import com.ichi2.anki.StudyOptionsActivity
import com.ichi2.anki.common.destinations.StudyOptionsDestination
import com.ichi2.anki.common.destinations.launchActivity
import com.ichi2.anki.common.utils.dp
import com.ichi2.anki.databinding.FragmentReminderTroubleshootingBinding
import com.ichi2.anki.databinding.FragmentScheduleRemindersBinding
import com.ichi2.anki.preferences.PreferencesActivity
import com.ichi2.anki.preferences.PreferencesFragment
import com.ichi2.anki.reviewreminders.ScheduleRemindersFragment.FragmentHost
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.ui.windows.permissions.PermissionsBottomSheet
import com.ichi2.anki.ui.windows.permissions.PermissionsFragment
import com.ichi2.anki.withDeckPicker
import com.ichi2.testutils.BackupManagerTestUtilities
import com.ichi2.testutils.positiveButton
import com.ichi2.testutils.scrollToLastPosition
import com.ichi2.testutils.simulateSystemBars
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * Covers all [FragmentHost] configurations of the fragment.
 */
class ReviewRemindersScreenshotTest : ScreenshotTest() {
    @Before
    @After
    fun clearReminders() {
        // The database retains its own SharedPreferences instance across Robolectric test cases.
        ReviewRemindersDatabase.remindersSharedPrefs.edit { clear() }
    }

    @Test
    fun `settings host`() {
        // overflow the list: the toolbar only collapses once the list can scroll
        insertReminders(count = 12)
        captureSettingsHost("settingsHost")
    }

    @Test
    fun `settings host tablet`() {
        setTabletQualifiers()
        // The toolbar is not collapsible on wide screens
        captureSettingsHost("settingsHostTablet", captureScrolled = false)
    }

    private fun captureSettingsHost(
        prefix: String,
        captureScrolled: Boolean = true,
    ) {
        withSettingsScheduleReminders { _, fm ->
            captureScreen("${prefix}_scheduleReminders")
            if (captureScrolled) {
                fm.collapseToolbar()
                captureScreen("${prefix}_scheduleReminders_scrolled")
            }
            commitTroubleshootingAndCapture(
                fragmentManager = fm,
                containerId = R.id.settings_container,
                host = FragmentHost.SETTINGS,
                prefix = prefix,
            )
        }
    }

    @Test
    fun `settings host with a landscape display cutout`() {
        insertReminders(count = 6)
        RuntimeEnvironment.setQualifiers("+land")
        withSettingsScheduleReminders { activity, fm ->
            activity.simulateSystemBars(cutoutLeft = 32.dp)
            captureScreen("settingsHost_landscapeCutout")

            // collapsed: the content scrim must extend behind the cutout band while the
            // toolbar content stays clear of it
            fm.collapseToolbar()
            captureScreen("settingsHost_landscapeCutout_collapsed")
        }
    }

    @Test
    fun `settings host with a landscape display cutout - RTL`() {
        insertReminders(count = 6)
        RuntimeEnvironment.setQualifiers("+ar")
        RuntimeEnvironment.setQualifiers("+land")
        withSettingsScheduleReminders { activity, _ ->
            // the cutout is physically on the left; the expanded title starts at the right
            // in RTL, so it is inset via its end margin
            activity.simulateSystemBars(cutoutLeft = 32.dp)
            captureScreen("settingsHost_landscapeCutout_rtl")
        }
    }

    @Test
    fun `study options fragment host`() {
        setTabletQualifiers()
        withDeckPicker(deckCount = 1, withCards = true) { deckPicker ->
            val deckId = addDeck("Test Deck")
            commitScheduleRemindersAndCapture(
                fragmentManager = deckPicker.supportFragmentManager,
                containerId = R.id.studyoptions_fragment,
                host = FragmentHost.STUDY_OPTIONS_FRAGMENT,
                scope = ReviewReminderScope.DeckSpecific(deckId),
                prefix = "studyOptionsFragmentHost",
            )
            commitTroubleshootingAndCapture(
                fragmentManager = deckPicker.supportFragmentManager,
                containerId = R.id.studyoptions_fragment,
                host = FragmentHost.STUDY_OPTIONS_FRAGMENT,
                prefix = "studyOptionsFragmentHost",
            )
        }
        BackupManagerTestUtilities.reset()
    }

    @Test
    fun `study options frame host`() {
        val deckId = addDeck("Test Deck")
        launchActivity<StudyOptionsActivity>(StudyOptionsDestination).use { scenario ->
            scenario.onActivity { activity ->
                commitScheduleRemindersAndCapture(
                    fragmentManager = activity.supportFragmentManager,
                    containerId = R.id.studyoptions_frame,
                    host = FragmentHost.STUDY_OPTIONS_FRAME,
                    scope = ReviewReminderScope.DeckSpecific(deckId),
                    prefix = "studyOptionsFrameHost",
                )
                commitTroubleshootingAndCapture(
                    fragmentManager = activity.supportFragmentManager,
                    containerId = R.id.studyoptions_frame,
                    host = FragmentHost.STUDY_OPTIONS_FRAME,
                    prefix = "studyOptionsFrameHost",
                )
            }
        }
    }

    @Test
    fun `standalone activity host`() =
        withStandaloneScheduleReminders { activity ->
            captureScreen("standaloneActivityHost_scheduleReminders")
            commitTroubleshootingAndCapture(
                fragmentManager = activity.supportFragmentManager,
                containerId = R.id.fragment_container,
                host = FragmentHost.STANDALONE_ACTIVITY,
                prefix = "standaloneActivityHost",
            )
        }

    @Test
    fun `enabled and disabled global and deck reminders`() {
        val deckScope = ReviewReminderScope.DeckSpecific(addDeck("Japanese::Vocabulary"))
        insertReminder(ReviewReminderTime(8, 0))
        insertReminder(ReviewReminderTime(9, 30), enabled = false)
        insertReminder(ReviewReminderTime(18, 15), scope = deckScope)
        insertReminder(ReviewReminderTime(21, 45), scope = deckScope, enabled = false)

        withStandaloneScheduleReminders {
            captureScreen("globalAndDeckReminders_enabledAndDisabled")
        }
    }

    @Test
    fun `reminders for a deleted deck`() {
        val deckId = addDeck("Deleted deck")
        val deckScope = ReviewReminderScope.DeckSpecific(deckId)
        insertReminder(ReviewReminderTime(8, 0))
        insertReminder(ReviewReminderTime(9, 30), scope = deckScope)
        insertReminder(ReviewReminderTime(18, 15), scope = deckScope, enabled = false)
        col.decks.remove(listOf(deckId))

        withStandaloneScheduleReminders {
            captureScreen("deletedDeckReminders")
        }
    }

    @Test
    fun `notification permission bottom sheet after adding a reminder`() {
        shadowOf(targetContext.getSystemService<NotificationManager>()!!).setNotificationsEnabled(false)
        Prefs.reminderNotifsRequestShown = false
        Prefs.notificationsPermissionRequested = false

        withScheduleRemindersFragment { fragment ->
            fragment.binding.floatingActionButtonAdd.performClick()
            advanceRobolectricLooper()

            fragment.addEditReminderDialog.positiveButton.performClick()
            advanceRobolectricLooperUntil {
                fragment.permissionsBottomSheet?.permissionsFragment?.view != null &&
                    fragment.reminderCount == 1
            }
            captureScreen("notificationPermissionBottomSheet")
        }
    }

    @Test
    fun `legacy notification permission bottom sheet`() {
        shadowOf(targetContext.getSystemService<NotificationManager>()!!).setNotificationsEnabled(false)
        withScheduleRemindersFragment { fragment ->
            // Capture the legacy content on the suite's SDK; mixing SDKs cannot share the native backend.
            PermissionsBottomSheet.launch(fragment.childFragmentManager, OptionalPermissionSet.LEGACY_NOTIFICATIONS)
            advanceRobolectricLooper()
            captureScreen("legacyNotificationPermissionBottomSheet")
        }
    }

    @Test
    fun `standalone activity host with system bars`() =
        withStandaloneScheduleReminders { activity ->
            activity.simulateSystemBars()
            captureScreen("standaloneActivityHost_systemBars")
        }

    @Test
    fun `standalone activity host with system bars and a scrollable list`() {
        insertReminders(count = 12)
        withScheduleRemindersFragment { fragment ->
            fragment.requireActivity().simulateSystemBars()
            val binding = fragment.binding
            // scrolled to the end: the last reminder must clear the navigation bar band
            binding.recyclerView.scrollToLastPosition()
            advanceRobolectricLooper()
            captureScreen("standaloneActivityHost_systemBars_scrolledToEnd")
        }
    }

    @Test
    fun `standalone activity host troubleshooting with system bars`() {
        // landscape: the checks overflow the screen, so the end of the content must scroll
        // clear of the navigation bar band
        RuntimeEnvironment.setQualifiers("+land")
        withStandaloneScheduleReminders { activity ->
            activity.supportFragmentManager.commit {
                replace(
                    R.id.fragment_container,
                    ReminderTroubleshootingFragment.newInstance(FragmentHost.STANDALONE_ACTIVITY),
                )
            }
            advanceRobolectricLooper()
            activity.simulateSystemBars()
            val binding =
                FragmentReminderTroubleshootingBinding.bind(
                    activity.supportFragmentManager
                        .findFragmentById(R.id.fragment_container)!!
                        .requireView(),
                )
            // scrolled to the end: the last check must clear the navigation bar band
            binding.scrollView.scrollTo(0, binding.scrollView.getChildAt(0).bottom)
            advanceRobolectricLooper()
            captureScreen("standaloneActivityHost_troubleshooting_systemBars")
        }
    }

    @Test
    fun `study options frame host with system bars and a scrollable list`() {
        val deckId = addDeck("Test Deck")
        val scope = ReviewReminderScope.DeckSpecific(deckId)
        insertReminders(count = 12, scope = scope)
        launchActivity<StudyOptionsActivity>(StudyOptionsDestination).use { scenario ->
            scenario.onActivity { activity ->
                val binding =
                    commitScheduleReminders(
                        fragmentManager = activity.supportFragmentManager,
                        containerId = R.id.studyoptions_frame,
                        host = FragmentHost.STUDY_OPTIONS_FRAME,
                        scope = scope,
                    )
                activity.simulateSystemBars()
                // the list continues underneath the navigation bar band
                captureScreen("studyOptionsFrameHost_systemBars")
                // scrolled to the end: the last reminder must clear the navigation bar band
                binding.recyclerView.scrollToLastPosition()
                advanceRobolectricLooper()
                captureScreen("studyOptionsFrameHost_systemBars_scrolledToEnd")
            }
        }
    }

    @Test
    fun `study options frame host troubleshooting with system bars`() {
        // landscape: the checks overflow the screen, so the end of the content must scroll
        // clear of the navigation bar band
        RuntimeEnvironment.setQualifiers("+land")
        launchActivity<StudyOptionsActivity>(StudyOptionsDestination).use { scenario ->
            scenario.onActivity { activity ->
                val binding =
                    commitTroubleshooting(
                        fragmentManager = activity.supportFragmentManager,
                        containerId = R.id.studyoptions_frame,
                        host = FragmentHost.STUDY_OPTIONS_FRAME,
                    )
                activity.simulateSystemBars()
                binding.scrollView.scrollTo(0, binding.scrollView.getChildAt(0).bottom)
                advanceRobolectricLooper()
                captureScreen("studyOptionsFrameHost_troubleshooting_systemBars")
            }
        }
    }

    @Test
    fun `study options fragment host with system bars and a scrollable list`() {
        setTabletQualifiers()
        withDeckPicker(deckCount = 1, withCards = true) { deckPicker ->
            val scope = ReviewReminderScope.DeckSpecific(addDeck("Test Deck"))
            insertReminders(count = 12, scope = scope)
            val binding =
                commitScheduleReminders(
                    fragmentManager = deckPicker.supportFragmentManager,
                    containerId = R.id.studyoptions_fragment,
                    host = FragmentHost.STUDY_OPTIONS_FRAGMENT,
                    scope = scope,
                )
            // a camera cutout beside the deck list: the deck list clears it, the panel must not
            // clear it a second time
            deckPicker.simulateSystemBars(cutoutLeft = 32.dp)
            captureScreen("studyOptionsFragmentHost_systemBars")
            // scrolled to the end: the last reminder must clear the navigation bar band
            binding.recyclerView.scrollToLastPosition()
            advanceRobolectricLooper()
            captureScreen("studyOptionsFragmentHost_systemBars_scrolledToEnd")
        }
        BackupManagerTestUtilities.reset()
    }

    @Test
    fun `study options fragment host troubleshooting with system bars`() {
        setTabletQualifiers()
        withDeckPicker(deckCount = 1, withCards = true) { deckPicker ->
            val binding =
                commitTroubleshooting(
                    fragmentManager = deckPicker.supportFragmentManager,
                    containerId = R.id.studyoptions_fragment,
                    host = FragmentHost.STUDY_OPTIONS_FRAGMENT,
                )
            deckPicker.simulateSystemBars(cutoutLeft = 32.dp)
            binding.scrollView.scrollTo(0, binding.scrollView.getChildAt(0).bottom)
            advanceRobolectricLooper()
            captureScreen("studyOptionsFragmentHost_troubleshooting_systemBars")
        }
        BackupManagerTestUtilities.reset()
    }

    private fun insertReminder(
        time: ReviewReminderTime,
        scope: ReviewReminderScope = ReviewReminderScope.Global,
        enabled: Boolean = true,
    ) = runBlocking {
        ReviewRemindersDatabase.insertReminder(
            ReviewReminder.createReviewReminder(time = time, scope = scope, enabled = enabled),
        )
    }

    /** Inserts [count] reminders so the list has content to render behind the simulated bars */
    private fun insertReminders(
        count: Int,
        scope: ReviewReminderScope = ReviewReminderScope.Global,
    ) {
        repeat(count) { index ->
            insertReminder(ReviewReminderTime(hour = 8 + index, minute = 0), scope = scope)
        }
    }

    /** Shows [ScheduleRemindersFragment] in [containerId] and returns its laid-out views */
    private fun commitScheduleReminders(
        fragmentManager: FragmentManager,
        @IdRes containerId: Int,
        host: FragmentHost,
        scope: ReviewReminderScope,
    ): FragmentScheduleRemindersBinding {
        fragmentManager.commit {
            replace(containerId, ScheduleRemindersFragment.newInstance(scope, host))
        }
        advanceRobolectricLooper()
        return (fragmentManager.findFragmentById(containerId) as ScheduleRemindersFragment).binding
    }

    private fun commitScheduleRemindersAndCapture(
        fragmentManager: FragmentManager,
        @IdRes containerId: Int,
        host: FragmentHost,
        scope: ReviewReminderScope,
        prefix: String,
    ) {
        commitScheduleReminders(fragmentManager, containerId, host, scope)
        captureScreen("${prefix}_scheduleReminders")
    }

    /** Shows [ReminderTroubleshootingFragment] in [containerId] and returns its laid-out views */
    private fun commitTroubleshooting(
        fragmentManager: FragmentManager,
        @IdRes containerId: Int,
        host: FragmentHost,
    ): FragmentReminderTroubleshootingBinding {
        fragmentManager.commit {
            replace(containerId, ReminderTroubleshootingFragment.newInstance(host))
            addToBackStack(null)
        }
        advanceRobolectricLooper()
        return (fragmentManager.findFragmentById(containerId) as ReminderTroubleshootingFragment).binding
    }

    private fun commitTroubleshootingAndCapture(
        fragmentManager: FragmentManager,
        @IdRes containerId: Int,
        host: FragmentHost,
        prefix: String,
    ) {
        commitTroubleshooting(fragmentManager, containerId, host)
        captureScreen("${prefix}_troubleshooting")
    }

    /** Launches [ScheduleRemindersFragment] hosted in the settings screen */
    private fun withSettingsScheduleReminders(block: (PreferencesActivity, FragmentManager) -> Unit) {
        ActivityScenario.launch<PreferencesActivity>(PreferencesActivity.getIntent(targetContext)).use { scenario ->
            scenario.onActivity { activity ->
                val fm = (activity.fragment as PreferencesFragment).childFragmentManager
                fm.commit {
                    replace(
                        R.id.settings_container,
                        ScheduleRemindersFragment.newInstance(ReviewReminderScope.Global, FragmentHost.SETTINGS),
                    )
                }
                advanceRobolectricLooper()
                block(activity, fm)
            }
        }
    }

    /** Collapses the settings host's toolbar, as when the list has been scrolled */
    private fun FragmentManager.collapseToolbar() {
        findFragmentById(R.id.settings_container)
            ?.view
            ?.findViewById<AppBarLayout>(R.id.appbar)
            ?.setExpanded(false, false)
        advanceRobolectricLooper()
    }

    private val ScheduleRemindersFragment.addEditReminderDialog: AddEditReminderDialog
        get() = childFragmentManager.fragments.filterIsInstance<AddEditReminderDialog>().single()

    private val ScheduleRemindersFragment.permissionsBottomSheet: PermissionsBottomSheet?
        get() = childFragmentManager.fragments.filterIsInstance<PermissionsBottomSheet>().singleOrNull()

    /** The number of reminders in the list */
    private val ScheduleRemindersFragment.reminderCount: Int
        get() = binding.recyclerView.adapter!!.itemCount

    /** The content the sheet hosts, once it has been committed */
    private val PermissionsBottomSheet.permissionsFragment: PermissionsFragment?
        get() = childFragmentManager.fragments.filterIsInstance<PermissionsFragment>().singleOrNull()
}

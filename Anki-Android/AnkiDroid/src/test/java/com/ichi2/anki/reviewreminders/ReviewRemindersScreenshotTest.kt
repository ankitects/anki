// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 Eric Li <ericli3690@gmail.com>

package com.ichi2.anki.reviewreminders

import android.content.Intent
import androidx.annotation.IdRes
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.test.core.app.ActivityScenario
import com.google.android.material.appbar.AppBarLayout
import com.ichi2.anki.R
import com.ichi2.anki.ScreenshotTest
import com.ichi2.anki.StudyOptionsActivity
import com.ichi2.anki.preferences.PreferencesActivity
import com.ichi2.anki.preferences.PreferencesFragment
import com.ichi2.anki.reviewreminders.ScheduleRemindersFragment.FragmentHost
import com.ichi2.anki.utils.ConfigAwareSingleFragmentActivity
import com.ichi2.anki.withDeckPicker
import com.ichi2.testutils.BackupManagerTestUtilities
import org.junit.Test

/**
 * Covers all [FragmentHost] configurations of the fragment.
 */
class ReviewRemindersScreenshotTest : ScreenshotTest() {
    @Test
    fun `settings host`() {
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
        ActivityScenario.launch<PreferencesActivity>(PreferencesActivity.getIntent(targetContext)).use { scenario ->
            scenario.onActivity { activity ->
                val fm = (activity.fragment as PreferencesFragment).childFragmentManager
                commitScheduleRemindersAndCapture(
                    fragmentManager = fm,
                    containerId = R.id.settings_container,
                    host = FragmentHost.SETTINGS,
                    scope = ReviewReminderScope.Global,
                    prefix = prefix,
                )
                if (captureScrolled) {
                    fm
                        .findFragmentById(R.id.settings_container)
                        ?.view
                        ?.findViewById<AppBarLayout>(R.id.appbar)
                        ?.setExpanded(false, false)
                    advanceRobolectricLooper()
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
        ActivityScenario
            .launch<StudyOptionsActivity>(
                Intent(targetContext, StudyOptionsActivity::class.java),
            ).use { scenario ->
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
    fun `standalone activity host`() {
        val intent = ScheduleRemindersFragment.getIntent(targetContext, ReviewReminderScope.Global)
        ActivityScenario.launch<ConfigAwareSingleFragmentActivity>(intent).use { scenario ->
            advanceRobolectricLooper()
            scenario.onActivity { activity ->
                captureScreen("standaloneActivityHost_scheduleReminders")
                commitTroubleshootingAndCapture(
                    fragmentManager = activity.supportFragmentManager,
                    containerId = R.id.fragment_container,
                    host = FragmentHost.STANDALONE_ACTIVITY,
                    prefix = "standaloneActivityHost",
                )
            }
        }
    }

    private fun commitScheduleRemindersAndCapture(
        fragmentManager: FragmentManager,
        @IdRes containerId: Int,
        host: FragmentHost,
        scope: ReviewReminderScope,
        prefix: String,
    ) {
        fragmentManager.commit {
            replace(containerId, ScheduleRemindersFragment.newInstance(scope, host))
        }
        advanceRobolectricLooper()
        captureScreen("${prefix}_scheduleReminders")
    }

    private fun commitTroubleshootingAndCapture(
        fragmentManager: FragmentManager,
        @IdRes containerId: Int,
        host: FragmentHost,
        prefix: String,
    ) {
        fragmentManager.commit {
            replace(containerId, ReminderTroubleshootingFragment.newInstance(host))
            addToBackStack(null)
        }
        advanceRobolectricLooper()
        captureScreen("${prefix}_troubleshooting")
    }
}

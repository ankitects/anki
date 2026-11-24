// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.reviewreminders

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import androidx.core.net.toUri
import androidx.core.view.children
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CommonString
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.databinding.ItemTroubleshootingCheckBinding
import com.ichi2.testutils.withRequestIgnoreBatteryOptimizationsInManifest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertNotNull

/**
 * The actions offered by [ReminderTroubleshootingFragment] to resolve a failing check
 */
@RunWith(AndroidJUnit4::class)
class ReminderTroubleshootingResolveActionTest : RobolectricTest() {
    /** 'Optimized': the battery optimization check is a warning, so its resolve action is displayed */
    @Before
    fun enableBatteryOptimization() = targetContext.setTroubleshootingChecks(batteryOptimization = CheckResult.Warning)

    @Test
    fun `battery optimization asks for an exemption when the permission is in the manifest`() {
        withRequestIgnoreBatteryOptimizationsInManifest(true) {
            withTroubleshootingFragment {
                batteryOptimizationCheck.actionLink.performClick()

                val intent = assertNotNull(shadowOf(requireActivity()).nextStartedActivity)
                assertThat(intent.action, equalTo(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS))
                assertThat(intent.data, equalTo("package:${targetContext.packageName}".toUri()))
            }
        }
    }

    @Test
    fun `battery optimization opens the settings list when the permission is not in the manifest`() {
        withRequestIgnoreBatteryOptimizationsInManifest(false) {
            withTroubleshootingFragment {
                batteryOptimizationCheck.actionLink.performClick()

                val intent = assertNotNull(shadowOf(requireActivity()).nextStartedActivity)
                assertThat(intent.action, equalTo(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
    }

    @Test
    fun `battery optimization falls back to the settings list when exemption fails`() {
        throwOnAllSettingsExcept(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

        withRequestIgnoreBatteryOptimizationsInManifest(true) {
            withTroubleshootingFragment {
                batteryOptimizationCheck.actionLink.performClick()

                val intent = assertNotNull(shadowOf(requireActivity()).nextStartedActivity)
                assertThat(intent.action, equalTo(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
    }

    /**
     * Starting any activity other than the settings screen for [action] throws
     * `ActivityNotFoundException`
     */
    private fun throwOnAllSettingsExcept(
        @Suppress("SameParameterValue") action: String,
    ) {
        val settingsScreen = ComponentName("com.android.settings", action)
        shadowOf(targetContext.packageManager).apply {
            addActivityIfNotPresent(settingsScreen)
            addIntentFilterForActivity(settingsScreen, IntentFilter(action).apply { addCategory(Intent.CATEGORY_DEFAULT) })
        }
        shadowOf(ApplicationProvider.getApplicationContext<Application>()).checkActivities(true)
    }
}

/** The displayed 'Battery optimization' check */
private val ReminderTroubleshootingFragment.batteryOptimizationCheck: ItemTroubleshootingCheckBinding
    get() {
        val title = requireContext().getString(CommonString.reminder_troubleshooting_check_battery_optimization)
        return binding.checksList.children
            .map { ItemTroubleshootingCheckBinding.bind(it) }
            .single { it.title.text.toString() == title }
    }

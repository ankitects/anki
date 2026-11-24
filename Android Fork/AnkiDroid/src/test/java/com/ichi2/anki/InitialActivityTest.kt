// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.servicelayer.PreferenceUpgradeService
import com.ichi2.testutils.EmptyApplication
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.times
import org.mockito.kotlin.never
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class) // no point in Application init if we don't use it
class InitialActivityTest : RobolectricTest() {
    private lateinit var sharedPreferences: SharedPreferences
    private val appContext: Context
        get() = ApplicationProvider.getApplicationContext()

    @Before
    fun before() {
        sharedPreferences = appContext.sharedPrefs()
    }

    @Test
    fun perform_setup_returns_true_after_first_launch_or_data_wipe() {
        val result = InitialActivity.performSetupFromFreshInstallOrClearedPreferences(sharedPreferences)
        assertThat(result, equalTo(true))
    }

    @Test
    fun perform_setup_returns_false_after_setup() {
        InitialActivity.setUpgradedToLatestVersion(sharedPreferences)

        val resultAfterUpgrade = InitialActivity.performSetupFromFreshInstallOrClearedPreferences(sharedPreferences)
        assertThat("should not perform initial setup if setup has already occurred", resultAfterUpgrade, equalTo(false))
    }

    @Test
    fun initially_not_latest_version() {
        assertThat(InitialActivity.isLatestVersion(sharedPreferences), equalTo(false))
    }

    @Test
    fun not_latest_version_with_valid_value() {
        sharedPreferences.edit { putString("lastVersion", "0.1") }
        assertThat(InitialActivity.isLatestVersion(sharedPreferences), equalTo(false))
    }

    @Test
    fun latest_version_upgrade_is_now_latest_version() {
        InitialActivity.setUpgradedToLatestVersion(sharedPreferences)
        assertThat(InitialActivity.isLatestVersion(sharedPreferences), equalTo(true))
    }

    @Test
    @SuppressLint("CheckResult") // performSetupFromFreshInstallOrClearedPreferences
    fun new_install_or_preference_data_wipe_means_preferences_up_to_date() {
        mockStatic(PreferenceUpgradeService::class.java).use { mocked ->
            InitialActivity.performSetupFromFreshInstallOrClearedPreferences(sharedPreferences)
            mocked.verify({ PreferenceUpgradeService.setPreferencesUpToDate(sharedPreferences) }, times(1))
        }
    }

    @Test
    fun prefs_may_not_be_up_to_date_if_upgraded() {
        mockStatic(PreferenceUpgradeService::class.java).use { mocked ->
            InitialActivity.setUpgradedToLatestVersion(sharedPreferences)
            assertThat(InitialActivity.performSetupFromFreshInstallOrClearedPreferences(sharedPreferences), equalTo(false))
            mocked.verify({ PreferenceUpgradeService.setPreferencesUpToDate(sharedPreferences) }, never())
        }
    }

    @Test
    fun perform_setup_integration_test() {
        val sharedPrefs = appContext.sharedPrefs()
        val initialSetupResult =
            InitialActivity.performSetupFromFreshInstallOrClearedPreferences(
                appContext.sharedPrefs(),
            )
        assertThat(initialSetupResult, equalTo(true))
        val secondResult =
            InitialActivity.performSetupFromFreshInstallOrClearedPreferences(sharedPrefs)
        assertThat(
            "should not perform initial setup if setup has already occurred",
            secondResult,
            equalTo(false),
        )
    }
}

// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.res.Configuration
import android.util.TypedValue
import androidx.appcompat.app.AppCompatDelegate
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ichi2.anki.settings.PrefsRepository
import com.ichi2.anki.settings.enums.AppTheme
import com.ichi2.anki.settings.enums.DayTheme
import com.ichi2.anki.settings.enums.NightTheme
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.anki.testutil.disableIntroductionSlide
import com.ichi2.widget.cardanalysis.CardAnalysisWidgetConfig
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import com.ichi2.anki.common.android.R as CommonR

@RunWith(AndroidJUnit4::class)
class WidgetThemeInstrumentationTest : InstrumentedTest() {
    @Test
    fun coldWidgetConfigurationUsesForcedAppTheme() {
        emptyCol
        addNoteUsingBasicNoteType("front", "back")
        disableIntroductionSlide()
        val prefs = PrefsRepository(testContext)
        val previousAppTheme = prefs.appTheme
        val previousDayTheme = prefs.dayTheme
        val previousNightTheme = prefs.nightTheme
        val previousDefaultMode = AppCompatDelegate.getDefaultNightMode()
        val systemIsDark =
            testContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
        val expectedBackground =
            testContext.getColor(if (systemIsDark) android.R.color.white else CommonR.color.material_theme_grey)
        try {
            prefs.appTheme = if (systemIsDark) AppTheme.DAY else AppTheme.NIGHT
            prefs.dayTheme = DayTheme.LIGHT
            prefs.nightTheme = NightTheme.DARK
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                // A cold process has not initialized AppCompat from the app's theme preference.
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
            val intent =
                Intent(testContext, CardAnalysisWidgetConfig::class.java)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 1)
            ActivityScenario.launch<CardAnalysisWidgetConfig>(intent).use { scenario ->
                scenario.onActivity { activity ->
                    val background = TypedValue()
                    activity.theme.resolveAttribute(android.R.attr.windowBackground, background, true)
                    assertEquals(expectedBackground, background.data)
                }
            }
        } finally {
            prefs.appTheme = previousAppTheme
            prefs.dayTheme = previousDayTheme
            prefs.nightTheme = previousNightTheme
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                AppCompatDelegate.setDefaultNightMode(previousDefaultMode)
            }
        }
    }
}

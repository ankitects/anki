// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.app.Activity
import android.util.TypedValue
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.settings.PrefsRepository
import com.ichi2.anki.settings.enums.AppTheme
import com.ichi2.anki.settings.enums.DayTheme
import com.ichi2.anki.settings.enums.NightTheme
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import kotlin.test.assertEquals
import com.ichi2.anki.common.android.R as CommonR

/** The code [IntentHandler] and [IntentHandler2] run: a plain [Activity], not AppCompat. */
class TestIntentHandler : AbstractIntentHandler()

/** The user's theme must not depend on the system's dark mode. */
@RunWith(AndroidJUnit4::class)
class ThemeNightModeMismatchTest : RobolectricTest() {
    private val darkBackground get() = ContextCompat.getColor(targetContext, CommonR.color.material_theme_grey)
    private val lightBackground get() = ContextCompat.getColor(targetContext, android.R.color.white)

    @After
    fun resetNightMode() = AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

    @Test
    fun `intent handler is dark when the app is Dark and the system is light`() {
        useTheme(AppTheme.NIGHT, systemQualifier = "+notnight")
        assertColor(darkBackground, intentHandlerBackground())
    }

    @Test
    fun `intent handler is light when the app is Light and the system is dark`() {
        useTheme(AppTheme.DAY, systemQualifier = "+night")
        assertColor(lightBackground, intentHandlerBackground())
    }

    private fun useTheme(
        appTheme: AppTheme,
        systemQualifier: String,
    ) {
        RuntimeEnvironment.setQualifiers(systemQualifier)
        PrefsRepository(targetContext).apply {
            this.appTheme = appTheme
            dayTheme = DayTheme.LIGHT
            nightTheme = NightTheme.DARK
        }
    }

    private fun intentHandlerBackground(): Int =
        Robolectric.buildActivity(TestIntentHandler::class.java).use { controller ->
            controller.create().get().themeColor(android.R.attr.colorBackground)
        }

    private fun Activity.themeColor(attr: Int): Int {
        val value = TypedValue()
        theme.resolveAttribute(attr, value, true)
        return value.data
    }

    private fun assertColor(
        expected: Int,
        actual: Int?,
    ) = assertEquals(expected.hex(), actual?.hex())

    private fun Int.hex() = "#%08X".format(this)
}

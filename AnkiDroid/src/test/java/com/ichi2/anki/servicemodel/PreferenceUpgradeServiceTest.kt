/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.servicemodel

import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.cardviewer.Gesture
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.utils.HashUtil
import com.ichi2.anki.libanki.Consts
import com.ichi2.anki.noteeditor.CustomToolbarButton
import com.ichi2.anki.reviewer.Binding
import com.ichi2.anki.reviewer.CardSide
import com.ichi2.anki.reviewer.MappableBinding.Companion.toPreferenceString
import com.ichi2.anki.reviewer.ReviewerBinding
import com.ichi2.anki.servicelayer.PreferenceUpgradeService
import com.ichi2.anki.servicelayer.PreferenceUpgradeService.PreferenceUpgrade
import com.ichi2.anki.servicelayer.PreferenceUpgradeService.PreferenceUpgrade.UpgradeThemes
import com.ichi2.anki.servicelayer.RemovedPreferences
import com.ichi2.utils.LanguageUtil
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.lessThan
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import kotlin.test.assertNotNull

@RunWith(AndroidJUnit4::class)
class PreferenceUpgradeServiceTest : RobolectricTest() {
    private lateinit var prefs: SharedPreferences

    @Before
    override fun setUp() {
        super.setUp()
        prefs = targetContext.sharedPrefs()
    }

    @Test
    fun first_app_load_performs_no_upgrades() {
        PreferenceUpgradeService.setPreferencesUpToDate(prefs)
        val result = PreferenceUpgradeService.upgradePreferences(prefs, 0)
        assertThat("no upgrade should have taken place", result, equalTo(false))
    }

    @Test
    fun preference_upgrade_leads_to_max_version_in_preferences() {
        val result = PreferenceUpgradeService.upgradePreferences(prefs, 0)
        assertThat("preferences were upgraded", result, equalTo(true))
        val version = PreferenceUpgrade.getPreferenceVersion(prefs)
        PreferenceUpgradeService.setPreferencesUpToDate(prefs)
        val secondVersion = PreferenceUpgrade.getPreferenceVersion(prefs)
        assertThat("setPreferencesUpToDate should not change the version", secondVersion, equalTo(version))
    }

    @Test
    fun two_upgrades_does_nothing() {
        val result = PreferenceUpgradeService.upgradePreferences(prefs, 0)
        assertThat("preferences were upgraded", result, equalTo(true))
        val secondResult = PreferenceUpgradeService.upgradePreferences(prefs, 0)
        assertThat("a second preference upgrade does nothing", secondResult, equalTo(false))
    }

    @Test
    fun each_version_code_is_distinct() {
        val codes = PreferenceUpgrade.getAllVersionIdentifiers().toList()
        assertThat("all version IDs should be distinct", codes.size, equalTo(codes.distinct().size))
    }

    @Test
    fun version_codes_do_not_decrease() {
        // in this test, we ensure that version codes are monotonically increasing.
        val codes = PreferenceUpgrade.getAllVersionIdentifiers()

        codes.zip(codes.drop(1)).forEach {
            assertThat(
                "versions should be increasing, but found (${it.first}) before (${it.second})",
                it.first,
                lessThan(it.second),
            )
        }
    }

    @Test
    fun one_version_code_per_nested_class() {
        val nestedClasses = PreferenceUpgrade::class.nestedClasses.filter { it.simpleName != "Companion" }
        val nestedClassCount = nestedClasses.size
        val upgradeCount = PreferenceUpgrade.getAllVersionIdentifiers().toList().size

        assertThat(
            "Different count of nested classes ($nestedClassCount) and upgrades ($upgradeCount). \n" +
                "nested classes:\n ${nestedClasses.joinToString("\n") { it.simpleName.toString() }}",
            nestedClassCount,
            equalTo(upgradeCount),
        )
    }

    @Test
    fun note_editor_toolbar_button_text() {
        // add two example toolbar buttons
        val buttons = HashUtil.hashSetInit<String>(2)

        var values = arrayOf("0", "<h1>", "</h1>")
        buttons.add(values.joinToString(Consts.FIELD_SEPARATOR))

        values = arrayOf("1", "<p>", "</p>")
        buttons.add(values.joinToString(Consts.FIELD_SEPARATOR))

        prefs.edit {
            putStringSet("note_editor_custom_buttons", buttons)
        }

        // now update it and check it
        PreferenceUpgrade.UpdateNoteEditorToolbarPrefs().performUpgrade(prefs)

        val set = prefs.getStringSet("note_editor_custom_buttons", HashUtil.hashSetInit<String>(0)) as Set<String?>
        val toolbarButtons = CustomToolbarButton.fromStringSet(set)

        assertEquals("Set size", 2, set.size)
        assertEquals("Toolbar buttons size", 2, toolbarButtons.size)

        assertEquals("Button text prefs", "1", toolbarButtons[0].buttonText)
        assertEquals("Button text prefs", "2", toolbarButtons[1].buttonText)
    }

    @Test
    fun day_and_night_themes() {
        // Plain and Dark
        prefs.edit {
            putString("dayTheme", "1")
            putString("nightTheme", "1")
            putBoolean("invertedColors", true)
        }
        PreferenceUpgrade.UpgradeDayAndNightThemes().performUpgrade(prefs)

        assertThat(prefs.getString("dayTheme", "0"), equalTo("2"))
        assertThat(prefs.getString("nightTheme", "0"), equalTo("4"))
        assertThat(prefs.contains("invertedColors"), equalTo(false))

        // Light and Black
        prefs.edit {
            putString("dayTheme", "0")
            putString("nightTheme", "0")
        }
        PreferenceUpgrade.UpgradeDayAndNightThemes().performUpgrade(prefs)

        assertThat(prefs.getString("dayTheme", "1"), equalTo("1"))
        assertThat(prefs.getString("nightTheme", "1"), equalTo("3"))
        assertThat(prefs.contains("invertedColors"), equalTo(false))
    }

    @Test
    fun `Fetch media pref's values are converted to 'always' if enabled and 'never' if disabled`() {
        // enabled -> always
        prefs.edit { putBoolean(RemovedPreferences.SYNC_FETCHES_MEDIA, true) }
        PreferenceUpgrade.UpgradeFetchMedia().performUpgrade(prefs)
        assertThat(prefs.getString("syncFetchMedia", null), equalTo("always"))

        // disabled -> never
        prefs.edit { putBoolean(RemovedPreferences.SYNC_FETCHES_MEDIA, false) }
        PreferenceUpgrade.UpgradeFetchMedia().performUpgrade(prefs)
        assertThat(prefs.getString("syncFetchMedia", null), equalTo("never"))
    }

    @Test
    fun `Double tap timeout is converted correctly`() {
        fun testValue(
            oldValue: Int,
            expectedValue: Int,
        ) {
            prefs.edit { putInt("doubleTapTimeInterval", oldValue) }
            PreferenceUpgrade.UpgradeDoubleTapTimeout().performUpgrade(prefs)
            assertThat(prefs.getInt("doubleTapTimeout", -1), equalTo(expectedValue))
        }
        testValue(oldValue = 395, expectedValue = 400)
        testValue(oldValue = 25, expectedValue = 20)
        testValue(oldValue = 200, expectedValue = 200)
        testValue(oldValue = 0, expectedValue = 0)
        testValue(oldValue = 1350, expectedValue = 1000)
    }

    // ############################
    // ##### UpgradeAppLocale #####
    // ############################
    @Test
    fun `Language preference value is updated to use language tags`() {
        val upgradeAppLocale = PreferenceUpgrade.UpgradeAppLocale()
        for (languageTag in LanguageUtil.APP_LANGUAGES.values) {
            prefs.edit {
                putString("language", Locale.forLanguageTag(languageTag).toString())
            }
            upgradeAppLocale.performUpgrade(prefs)
            val correctLanguage = prefs.getString("language", null)
            assertThat(languageTag, equalTo(correctLanguage))
            // The following assertion broke when updating targetSdk from 33->34 / robolectric from 32->34
            // However, a manual verification on an API33 and API34 emulator worked as follows:
            // - call sites are in AnkiDroidApp to show different manuals *if* the manual is translated
            // - follow app use path: get help / using / ankidroid manual -> it should send you to English manual
            // - manual is translated in Japanese, so set app language preference to Japanese
            // - set app language back to english, verify it goes to english manual again
            // assertThat(LanguageUtil.getCurrentLocaleTag(), equalTo(languageTag))
        }
    }

    @Test
    fun `Language preference value is set to system default correctly if it hasn't been set`() {
        PreferenceUpgrade.UpgradeAppLocale().performUpgrade(prefs)

        assertNotNull(prefs.getString("language", null))
        assertThat(LanguageUtil.getCurrentLocaleTag(), equalTo(""))
    }

    @Test
    fun `Language preference value is set to system default correctly`() {
        prefs.edit { putString("language", "") }
        PreferenceUpgrade.UpgradeAppLocale().performUpgrade(prefs)

        assertThat(prefs.getString("language", null), equalTo(""))
        assertThat(LanguageUtil.getCurrentLocaleTag(), equalTo(""))
    }

    @Test
    fun upgradeThemes() {
        val upgrade = UpgradeThemes()

        // Constants should have the correct values
        assertEquals(UpgradeThemes.THEME_FOLLOW_SYSTEM, targetContext.getString(R.string.theme_follow_system_value))
        assertEquals(UpgradeThemes.THEME_LIGHT, targetContext.getString(R.string.theme_light_value))
        assertEquals(UpgradeThemes.THEME_PLAIN, targetContext.getString(R.string.theme_plain_value))
        assertEquals(UpgradeThemes.THEME_BLACK, targetContext.getString(R.string.theme_black_value))
        assertEquals(UpgradeThemes.THEME_DARK, targetContext.getString(R.string.theme_dark_value))
        assertEquals(UpgradeThemes.THEME_DAY, targetContext.getString(R.string.theme_day_scheme_value))
        assertEquals(UpgradeThemes.THEME_NIGHT, targetContext.getString(R.string.theme_night_scheme_value))

        // Follow System -> Should remain Follow System, no sub-themes set
        prefs.edit { putString(UpgradeThemes.KEY_APP_THEME, UpgradeThemes.THEME_FOLLOW_SYSTEM) }
        upgrade.performUpgrade(prefs)
        assertThat(prefs.getString(UpgradeThemes.KEY_APP_THEME, null), equalTo(UpgradeThemes.THEME_FOLLOW_SYSTEM))
        assertThat(prefs.contains(UpgradeThemes.KEY_DAY_THEME), equalTo(false))
        assertThat(prefs.contains(UpgradeThemes.KEY_NIGHT_THEME), equalTo(false))

        // Light -> Day + dayTheme: Light
        prefs.edit {
            putString(UpgradeThemes.KEY_APP_THEME, UpgradeThemes.THEME_LIGHT)
            remove(UpgradeThemes.KEY_DAY_THEME)
        }
        upgrade.performUpgrade(prefs)
        assertThat(prefs.getString(UpgradeThemes.KEY_APP_THEME, null), equalTo(UpgradeThemes.THEME_DAY))
        assertThat(prefs.getString(UpgradeThemes.KEY_DAY_THEME, null), equalTo(UpgradeThemes.THEME_LIGHT))

        // Plain -> Day + dayTheme: Plain
        prefs.edit {
            putString(UpgradeThemes.KEY_APP_THEME, UpgradeThemes.THEME_PLAIN)
            remove(UpgradeThemes.KEY_DAY_THEME)
        }
        upgrade.performUpgrade(prefs)
        assertThat(prefs.getString(UpgradeThemes.KEY_APP_THEME, null), equalTo(UpgradeThemes.THEME_DAY))
        assertThat(prefs.getString(UpgradeThemes.KEY_DAY_THEME, null), equalTo(UpgradeThemes.THEME_PLAIN))

        // Black -> Night + nightTheme: Black
        prefs.edit {
            putString(UpgradeThemes.KEY_APP_THEME, UpgradeThemes.THEME_BLACK)
            remove(UpgradeThemes.KEY_NIGHT_THEME)
        }
        upgrade.performUpgrade(prefs)
        assertThat(prefs.getString(UpgradeThemes.KEY_APP_THEME, null), equalTo(UpgradeThemes.THEME_NIGHT))
        assertThat(prefs.getString(UpgradeThemes.KEY_NIGHT_THEME, null), equalTo(UpgradeThemes.THEME_BLACK))

        // Dark -> Night + nightTheme: Dark
        prefs.edit {
            putString(UpgradeThemes.KEY_APP_THEME, UpgradeThemes.THEME_DARK)
            remove(UpgradeThemes.KEY_NIGHT_THEME)
        }
        upgrade.performUpgrade(prefs)
        assertThat(prefs.getString(UpgradeThemes.KEY_APP_THEME, null), equalTo(UpgradeThemes.THEME_NIGHT))
        assertThat(prefs.getString(UpgradeThemes.KEY_NIGHT_THEME, null), equalTo(UpgradeThemes.THEME_DARK))
    }

    @Test
    fun upgradeAnswerControls() {
        val questionBinding = Binding.gesture(Gesture.TAP_LEFT)
        val answerBinding = Binding.gesture(Gesture.TAP_RIGHT)
        val bothBinding = Binding.gesture(Gesture.SWIPE_UP)
        val existingShowBinding = Binding.gesture(Gesture.SWIPE_DOWN)

        val againBindings =
            listOf(
                ReviewerBinding(questionBinding, CardSide.QUESTION),
                ReviewerBinding(answerBinding, CardSide.ANSWER),
            )
        val goodBindings =
            listOf(
                ReviewerBinding(bothBinding, CardSide.BOTH),
            )
        val showAnswerBindings =
            listOf(
                ReviewerBinding(existingShowBinding, CardSide.QUESTION),
            )
        prefs.edit {
            putString("binding_FLIP_OR_ANSWER_EASE1", againBindings.toPreferenceString())
            putString("binding_FLIP_OR_ANSWER_EASE3", goodBindings.toPreferenceString())
            putString("binding_SHOW_ANSWER", showAnswerBindings.toPreferenceString())
        }

        PreferenceUpgrade.UpgradeAnswerControls().performUpgrade(prefs)

        assertThat(prefs.contains("binding_FLIP_OR_ANSWER_EASE1"), equalTo(false))
        assertThat(prefs.contains("binding_FLIP_OR_ANSWER_EASE3"), equalTo(false))

        val newAgainBindings = ReviewerBinding.fromPreferenceString(prefs.getString("binding_ANSWER_AGAIN", ""))
        assertThat("Should have 1 binding", newAgainBindings.size, equalTo(1))
        assertThat(newAgainBindings[0].binding, equalTo(answerBinding))
        assertThat(newAgainBindings[0].side, equalTo(CardSide.ANSWER))

        val newGoodBindings = ReviewerBinding.fromPreferenceString(prefs.getString("binding_ANSWER_GOOD", ""))
        assertThat("Should have 1 binding", newGoodBindings.size, equalTo(1))
        assertThat(newGoodBindings[0].binding, equalTo(bothBinding))
        assertThat(newGoodBindings[0].side, equalTo(CardSide.ANSWER))

        val newShowBindings = ReviewerBinding.fromPreferenceString(prefs.getString("binding_SHOW_ANSWER", ""))
        assertThat("Should have 3 bindings", newShowBindings.size, equalTo(3))

        val bindings = newShowBindings.map { it.binding }
        assertThat("Preserves SHOW_ANSWER binding", bindings.contains(existingShowBinding), equalTo(true))
        assertThat("Migrates 'Again' question binding", bindings.contains(questionBinding), equalTo(true))
        assertThat("Splits 'Good' Both binding to Question side", bindings.contains(bothBinding), equalTo(true))
    }
}

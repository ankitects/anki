/*
 *  Copyright (c) 2022 Brayan Oliveira <brayandso.dev@gmail.com>
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
package com.ichi2.anki.preferences

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.preference.ListPreference
import androidx.preference.SwitchPreferenceCompat
import anki.config.ConfigKey
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.contextmenu.AnkiCardContextMenu
import com.ichi2.anki.contextmenu.CardBrowserContextMenu
import com.ichi2.anki.launchCatchingTask
import com.ichi2.utils.LanguageUtil
import com.ichi2.utils.LanguageUtil.getStringByLocale
import com.ichi2.utils.LanguageUtil.getSystemLocale
import kotlinx.coroutines.runBlocking

class GeneralSettingsFragment : SettingsFragment() {
    override val preferenceResource: Int
        get() = R.xml.preferences_general
    override val analyticsScreenNameConstant: String
        get() = "prefs.general"

    override fun initSubscreen() {
        // Build languages
        initializeLanguagePref()

        // Deck for new cards
        // Represents in the collections pref "addToCur": i.e.
        // if true, then add note to current decks, otherwise let the note type's configuration decide
        // Note that "addToCur" is a boolean while USE_CURRENT is "0" or "1"
        requirePreference<ListPreference>(R.string.deck_for_new_cards_key).apply {
            launchCatchingTask {
                val valueIndex = if (withCol { config.getBool(ConfigKey.Bool.ADDING_DEFAULTS_TO_CURRENT_DECK) }) 0 else 1
                setValueIndex(valueIndex)
            }
            setOnPreferenceChangeListener { newValue ->
                launchCatchingTask { withCol { config.setBool(ConfigKey.Bool.ADDING_DEFAULTS_TO_CURRENT_DECK, "0" == newValue) } }
            }
        }
        // Paste PNG
        // Represents in the collection's pref "pastePNG" , i.e.
        // whether to convert clipboard uri to png format or not.
        requirePreference<SwitchPreferenceCompat>(R.string.paste_png_key).apply {
            title = TR.preferencesPasteClipboardImagesAsPng()
            launchCatchingTask { isChecked = withCol { config.getBool(ConfigKey.Bool.PASTE_IMAGES_AS_PNG) } }
            setOnPreferenceChangeListener { newValue ->
                launchCatchingTask { withCol { config.setBool(ConfigKey.Bool.PASTE_IMAGES_AS_PNG, newValue) } }
            }
        }
        // Error reporting mode
        requirePreference<ListPreference>(R.string.error_reporting_mode_key).setOnPreferenceChangeListener { newValue ->
            CrashReportService.onPreferenceChanged(requireContext(), newValue)
        }
        // Anki card context menu
        requirePreference<SwitchPreferenceCompat>(R.string.anki_card_external_context_menu_key).apply {
            title = getString(R.string.card_browser_enable_external_context_menu, getString(R.string.context_menu_anki_card_label))
            summary =
                getString(
                    R.string.card_browser_enable_external_context_menu_summary,
                    getString(R.string.context_menu_anki_card_label),
                )
            setOnPreferenceChangeListener { newValue ->
                AnkiCardContextMenu.ensureConsistentStateWithPreferenceStatus(requireContext(), newValue)
            }
        }
        // Card browser context menu
        requirePreference<SwitchPreferenceCompat>(R.string.card_browser_external_context_menu_key).apply {
            title = getString(R.string.card_browser_enable_external_context_menu, getString(R.string.card_browser_context_menu))
            summary = getString(R.string.card_browser_enable_external_context_menu_summary, getString(R.string.card_browser_context_menu))
            setOnPreferenceChangeListener { newValue ->
                CardBrowserContextMenu.ensureConsistentStateWithPreferenceStatus(requireContext(), newValue)
            }
        }
    }

    private fun initializeLanguagePref() {
        val sortedLanguages = LanguageUtil.APP_LANGUAGES.toSortedMap(java.lang.String.CASE_INSENSITIVE_ORDER)
        val systemLocale = getSystemLocale()
        requirePreference<ListPreference>(R.string.pref_language_key).apply {
            entries = arrayOf(getStringByLocale(R.string.language_system, systemLocale), *sortedLanguages.keys.toTypedArray())
            entryValues = arrayOf(LanguageUtil.SYSTEM_LANGUAGE_TAG, *sortedLanguages.values.toTypedArray())
            setOnPreferenceChangeListener { selectedLanguage ->
                LanguageUtil.setDefaultBackendLanguages(selectedLanguage)
                runBlocking { CollectionManager.discardBackend() }

                val localeCode =
                    if (selectedLanguage != LanguageUtil.SYSTEM_LANGUAGE_TAG) {
                        selectedLanguage
                    } else {
                        null
                    }
                val localeList = LocaleListCompat.forLanguageTags(localeCode)
                AppCompatDelegate.setApplicationLocales(localeList)
            }
        }
    }
}

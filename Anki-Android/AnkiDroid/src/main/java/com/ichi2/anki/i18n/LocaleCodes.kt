/*
 *  Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.i18n

import android.icu.util.ULocale
import androidx.annotation.CheckResult
import com.ichi2.anki.common.utils.android.isRobolectric
import timber.log.Timber
import java.util.Locale

/*
 * Handles mappings between [Locales][Locale] and Anki-compatible codes
 *
 * Android's [Locale] provides no validation, and device/TTS providers may provide nonstandard data
 *
 * Anki on other platforms expects standardized locales: `{{tts es_ES:field}}`, rather than `spa`
 */
private val twoLetterSystemLocaleMapping: Map<String, Locale> =
    run {
        val locales = Locale.getAvailableLocales()
        val validLocales = mutableMapOf<String, Locale>()
        for (locale in locales) {
            val code = locale.iso3Code ?: continue
            validLocales.putIfAbsent(code, locale)
        }
        validLocales
    }

/**
 * Returns an Anki-compatible 'two letter' code (ISO-639-1 + ISO 3166-1 [alpha-2 preferred])
 * ```
 * Locale("spa", "MEX", "001") => "es_MX"
 * Locale("ar", "") => "ar"
 * ```
 *
 * This differs from [Locale.toLanguageTag]:
 * * [Locale.variant][Locale.getVariant] is not output
 * * A "_" is used instead of a "-" to match Anki Desktop
 */
@CheckResult
fun Locale.toAnkiTwoLetterCode(): String =
    this.normalize().run {
        return if (country.isBlank()) language else "${language}_$country"
    }

/**
 * Converts a locale to a 'two letter' code (ISO-639-1 + ISO 3166-1 alpha-2)
 * Locale("spa", "MEX", "001") => Locale("es", "MX", "001")
 */
@CheckResult
fun Locale.normalize(): Locale {
    // ULocale isn't currently handled by Robolectric
    if (isRobolectric) {
        // normalises to "spa_MEX"
        val iso3Code = this.iso3Code ?: return this
        // convert back from this key to a two-letter mapping
        return twoLetterSystemLocaleMapping[iso3Code] ?: this
    }
    return try {
        val uLocale = ULocale(language, country, variant)
        Locale.forLanguageTag(uLocale.language + '-' + uLocale.country + '-' + uLocale.variant)
    } catch (e: Exception) {
        Timber.w(e, "Failed to normalize locale %s", this)
        this
    }
}

/**
 * Returns the ISO 3 code of the locale, with optional country specifier if provided.
 * Or `null` if the device does not have a mapping from the ISO-2 to ISO-3 code.
 *
 * ```
 * // "spa"
 * // "spa_MX"
 * // "cz" => `null` (on some devices)
 * ```
 */
val Locale.iso3Code: String?
    get() =
        try {
            // use Java-style 'get' methods; `val` properties are badly named: 'isO3Language'
            val language = this.getISO3Language()
            if (this.country.isBlank()) language else "${language}_${this.getISO3Country()}"
        } catch (_: Exception) {
            // MissingResourceException can be thrown, in which case return null
            // example: MissingResourceException: Couldn't find 3-letter language code for cz
            null
        }

/**
 * Returns a three-letter abbreviation of this locale's language, or `null` if a three-letter
 * language abbreviation is not available for this locale.
 *
 * WARN: This is lossy, as the two-letter-code was usable.
 *
 * If the language matches an ISO 639-1 two-letter code, the corresponding ISO 639-2/T three-letter
 * lowercase code is returned.
 *
 * The ISO 639-2 language codes can be found on-line, see "Codes for the Representation of Names of
 * Languages Part 2: Alpha-3 Code".
 *
 * If the locale specifies a three-letter language, the language is returned as is.
 *
 * If the locale does not specify a language the empty string is returned.
 */
fun Locale.getIso3LanguageOrNull(): String? =
    try {
        // use Java-style 'get' methods; `val` properties are badly named: 'isO3Language'
        this.getISO3Language()
    } catch (_: Exception) {
        // MissingResourceException can be thrown, in which case return null
        // example: MissingResourceException: Couldn't find 3-letter language code for cz
        null
    }

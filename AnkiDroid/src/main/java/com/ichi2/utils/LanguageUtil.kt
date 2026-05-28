/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package com.ichi2.utils

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.ConfigurationCompat
import androidx.fragment.app.Fragment
import com.ichi2.anki.R
import com.ichi2.anki.common.android.appContext
import com.ichi2.anki.compat.CompatHelper
import com.ichi2.anki.preferences.sharedPrefs
import net.ankiweb.rsdroid.BackendFactory
import timber.log.Timber
import java.util.Locale

/**
 * Utility call for proving language related functionality.
 */
object LanguageUtil {
    const val SYSTEM_LANGUAGE_TAG = ""

    /** A list of all languages supported by AnkiDroid
     * Please modify LanguageUtilsTest if changing
     * Please note 'yue' is special, it is 'yu' on CrowdIn, and mapped in import specially to 'yue' */
    val APP_LANGUAGES =
        mapOf(
            // Afrikaans
            "Afrikaans" to "af",
            // Amharic
            "አማርኛ" to "am",
            // Arabic
            "العربية" to "ar",
            // Azerbaijani
            "Azərbaycan" to "az",
            // Belarusian
            "Беларуская" to "be",
            // Bulgarian
            "Български" to "bg",
            // Bangla
            "বাংলা" to "bn",
            // Catalan
            "Català" to "ca",
            // Central Kurdish
            "کوردیی ناوەندی" to "ckb",
            // Czech
            "Čeština" to "cs",
            // Danish
            "Dansk" to "da",
            // German
            "Deutsch" to "de",
            // Greek
            "Ελληνικά" to "el",
            // English
            "English" to "en",
            // Esperanto
            "Esperanto" to "eo",
            // Spanish (Argentina)
            "Español (Argentina)" to "es-AR",
            // Spanish (Spain)
            "Español (España)" to "es-ES",
            // Estonian
            "Eesti" to "et",
            // Basque
            "Euskara" to "eu",
            // Persian
            "فارسی" to "fa",
            // Finnish
            "Suomi" to "fi",
            // Filipino
            "Filipino" to "fil",
            // French
            "Français" to "fr",
            // Western Frisian (Netherlands)
            "Frysk (Nederlân)" to "fy-NL",
            // Irish (Ireland)
            "Gaeilge (Éire)" to "ga-IE",
            // Galician
            "Galego" to "gl",
            // Gothic
            "Gothic" to "got",
            // Gujarati (India)
            "ગુજરાતી (ભારત)" to "gu-IN",
            // Hebrew
            "עברית" to "heb",
            // Hindi
            "हिन्दी" to "hi",
            // Croatian
            "Hrvatski" to "hr",
            // Hungarian
            "Magyar" to "hu",
            // Armenian (Armenia)
            "Հայերեն (Հայաստան)" to "hy-AM",
            // Indonesian
            "Indonesia" to "ind",
            // Italian
            "Italiano" to "it",
            // Japanese
            "日本語" to "ja",
            // Georgian
            "ქართული" to "ka",
            // Kazakh
            "Қазақ тілі" to "kk",
            // Khmer
            "ខ្មែរ" to "km",
            // Kannada
            "ಕನ್ನಡ" to "kn",
            // Korean
            "한국어" to "ko",
            // Kurdish
            "Kurdî" to "ku",
            // Kyrgyz
            "Кыргызча" to "ky",
            // Lithuanian
            "Lietuvių" to "lt",
            // Latvian
            "Latviešu" to "lv",
            // Macedonian
            "Македонски" to "mk",
            // Malayalam (India)
            "മലയാളം (ഇന്ത്യ)" to "ml-IN",
            // Mongolian
            "Монгол" to "mn",
            // Marathi
            "मराठी" to "mr",
            // Malay
            "Melayu" to "ms",
            // Burmese
            "မြန်မာ" to "my",
            // Dutch
            "Nederlands" to "nl",
            // Norwegian Nynorsk (Norway)
            "Nynorsk (Noreg)" to "nn-NO",
            // Norwegian
            "Norsk" to "no",
            // Odia
            "ଓଡ଼ିଆ" to "or",
            // Punjabi (India)
            "ਪੰਜਾਬੀ (ਭਾਰਤ)" to "pa-IN",
            // Polish
            "Polski" to "pl",
            // Portuguese (Brazil)
            "Português (Brasil)" to "pt-BR",
            // Portuguese (Portugal)
            "Português (Portugal)" to "pt-PT",
            // Romanian
            "Română" to "ro",
            // Russian
            "Русский" to "ru",
            // Santali
            "ᱥᱟᱱᱛᱟᱲᱤ" to "sat",
            // Sardinian
            "Sardu" to "sc",
            // Slovak
            "Slovenčina" to "sk",
            // Slovenian
            "Slovenščina" to "sl",
            // Albanian
            "Shqip" to "sq",
            // Serbian
            "Српски" to "sr",
            // Swedish (Sweden)
            "Svenska (Sverige)" to "sv-SE",
            // Tamil
            "தமிழ்" to "ta",
            // Telugu
            "తెలుగు" to "te",
            // Tagalog
            "Tagalog" to "tgl",
            // Thai
            "ไทย" to "th",
            // Tigrinya
            "ትግርኛ" to "ti",
            // Tswana
            "Türkçe" to "tr",
            // Tatar (Russia)
            "Татар (Россия)" to "tt-RU",
            // Uyghur
            "ئۇيغۇرچە" to "ug",
            // Ukrainian
            "Українська" to "uk",
            // Urdu (Pakistan)
            "اردو (پاکستان)" to "ur-PK",
            // Uzbek
            "Oʻzbekcha" to "uz",
            // Vietnamese
            "Tiếng Việt" to "vi",
            // Chinese (China)
            "中文 (中国)" to "zh-CN",
            // Chinese (Taiwan)
            "中文 (台灣)" to "zh-TW",
        )

    /** Backend languages; may not include recently added ones.
     * Found at https://i18n.ankiweb.net/teams/ */
    @Suppress("unused")
    val BACKEND_LANGS =
        listOf(
            // Afrikaans
            "af",
            // العربية
            "ar",
            // Беларуская мова
            "be",
            // Български
            "bg",
            // Català
            "ca",
            // Čeština
            "cs",
            // Dansk
            "da",
            // Deutsch
            "de",
            // Ελληνικά
            "el",
            // English (United States)
            "en",
            // English (United Kingdom)
            "en-GB",
            // Esperanto
            "eo",
            // Español
            "es",
            // Eesti
            "et",
            // Euskara
            "eu",
            // فارسی
            "fa",
            // Suomi
            "fi",
            // Français
            "fr",
            // Gaeilge
            "ga-IE",
            // Galego
            "gl",
            // עִבְרִית
            "he",
            // Hindi
            "hi-IN",
            // Hrvatski
            "hr",
            // Magyar
            "hu",
            // Հայերեն
            "hy-AM",
            // Indonesia
            "id",
            // Italiano
            "it",
            // 日本語
            "ja",
            // lo jbobau
            "jbo",
            // 한국어
            "ko",
            // Latin
            "la",
            // Монгол хэл
            "mn",
            // Bahasa Melayu
            "ms",
            // Norsk
            "nb",
            // norwegian
            "nb-NO",
            // Nederlands
            "nl",
            // norwegian
            "nn-NO",
            // Lenga d'òc
            "oc",
            // ଓଡ଼ିଆ
            "or",
            // Polski
            "pl",
            // Português Brasileiro
            "pt-BR",
            // Português
            "pt-PT",
            // Română
            "ro",
            // Pусский язык
            "ru",
            // Slovenčina
            "sk",
            // Slovenščina
            "sl",
            // Српски
            "sr",
            // Svenska
            "sv-SE",
            // ภาษาไทย
            "th",
            // Türkçe
            "tr",
            // ئۇيغۇرچە
            "ug",
            // Yкраїнська мова
            "uk",
            // Tiếng Việt
            "vi",
            // 简体中文
            "zh-CN",
            // 繁體中文
            "zh-TW",
        )

    fun getLocaleCompat(resources: Resources): Locale? = ConfigurationCompat.getLocales(resources.configuration)[0]

    fun getSystemLocale(): Locale = getLocaleCompat(Resources.getSystem())!!

    /** If locale is not provided, the current locale will be used. */
    fun setDefaultBackendLanguages(languageTag: String? = null) {
        val langCode =
            languageTag ?: appContext
                .sharedPrefs()
                .getString("language", SYSTEM_LANGUAGE_TAG)!!

        val localeLanguage =
            if (langCode == SYSTEM_LANGUAGE_TAG) {
                getSystemLocale().toLanguageTag()
            } else {
                langCode
            }
        BackendFactory.defaultLanguages = listOf(languageTagToBackendCode(localeLanguage))
    }

    private fun languageTagToBackendCode(languageTag: String): String =
        when (languageTag) {
            "heb" -> "he"
            "ind" -> "id"
            "tgl" -> "tl"
            "hi" -> "hi-IN"
            else -> languageTag
        }

    /** @return string defined with [stringRes] on the specified [locale] */
    fun Context.getStringByLocale(
        @StringRes stringRes: Int,
        locale: Locale,
        vararg formatArgs: Any,
    ): String {
        val configuration = Configuration(resources.configuration)
        configuration.setLocale(locale)
        return createConfigurationContext(configuration).resources.getString(stringRes, *formatArgs)
    }

    /**
     * Returns a [Context] with resources using the app language.
     *
     * Needed for resources accessed outside an Activity (e.g. from a [BroadcastReceiver][android.content.BroadcastReceiver]
     * or [Service][android.app.Service]):
     *
     * On API < 33, [AppCompatDelegate.setApplicationLocales] only applies to Activity contexts, so
     *  resources resolve in the system locale.
     *
     * Returns [this] unchanged (System language) when no in-app language is set.
     *
     * This method will not throw.
     */
    fun Context.withAppLocale(): Context =
        try {
            val tag = getCurrentLocaleTag()
            if (tag.isEmpty()) return this
            val configuration = Configuration(resources.configuration)
            configuration.setLocale(Locale.forLanguageTag(tag))
            return createConfigurationContext(configuration)
        } catch (e: Exception) {
            Timber.w(e, "withAppLocale")
            return this
        }

    /** @return string defined with [stringRes] on the specified [locale] */
    fun Fragment.getStringByLocale(
        @StringRes stringRes: Int,
        locale: Locale,
        vararg formatArgs: Any,
    ): String = requireContext().getStringByLocale(stringRes, locale, *formatArgs)

    /**
     * This should always be called after Activity.onCreate()
     * @return locale language tag of the app configured language
     */
    fun getCurrentLocaleTag(): String = AppCompatDelegate.getApplicationLocales().toLanguageTags()

    /**
     * Returns the character to use when separating a list; `, ` in English
     * Uses ListFormatter on API 26+ to dynamically get the locale-specific separator
     */
    fun getListSeparator(context: Context): String =
        CompatHelper.compat.getListSeparator(context, context.getString(R.string.list_separator))
}

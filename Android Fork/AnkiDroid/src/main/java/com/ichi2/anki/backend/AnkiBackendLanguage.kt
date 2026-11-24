// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.backend

import java.util.IllformedLocaleException
import java.util.Locale

/**
 * A well-formed Unicode language identifier used by Anki's backend for translations.
 *
 * `rslib` parses preferred languages with `unic_langid::LanguageIdentifier`: a language, optional
 * script and region, and variants, separated by `-` or `_`.
 *
 * Locale extensions are not supported.
 *
 * Identifiers that fail to parse are ignored, and English is always included as a fallback.
 *
 * See its pinned `parser` for the accepted syntax, including nonstandard variant forms.
 *
 * Examples:
 * - `pl`: language only.
 * - `zh-Hant-TW`: language, script and region.
 * - `de-1901`: language and variant.
 * - `pt_BR`: underscore separator.
 * - `en-US-Latn`: nonstandard variant.
 *
 * `rslib`: https://github.com/ankitects/anki/blob/29bb700b951e3f0c0cb69b77c0180fc1fe33e6ba/rslib/i18n/src/lib.rs#L255-L306
 * `parser`: https://github.com/zbraniecki/unic-locale/blob/4f37b35b55ab2354319abe11db6e84fe83abe895/unic-langid-impl/src/parser/mod.rs
 */
@JvmInline
value class AnkiBackendLanguage private constructor(
    val value: String,
) {
    companion object {
        // Conventional Unicode syntax; rslib's parser also accepts some nonstandard variants.
        private val LANGUAGE_IDENTIFIER =
            Regex(
                "(?:[a-zA-Z]{2,3}|[a-zA-Z]{5,8})" +
                    "(?:[-_][a-zA-Z]{4})?" +
                    "(?:[-_](?:[a-zA-Z]{2}|[0-9]{3}))?" +
                    "(?:[-_](?:[a-zA-Z0-9]{5,8}|[0-9][a-zA-Z0-9]{3}))*",
            )

        /**
         * Converts an Android language tag, removing locale extensions and mapping app aliases.
         * Existing identifiers are preserved before trying Java locale conversion. For example,
         * rslib accepts `zh-guoyu` as Chinese, while Java would replace its language with `cmn`.
         * Extensions, including `x-lvariant` private use subtags, are cleared before building the locale.
         *
         * @return null if the tag is blank or cannot be converted to a conventional Unicode identifier.
         * Some identifiers accepted by rslib, such as `pl-PL-abcd`, fail Java conversion. Callers should
         * pass the original tag to rslib on failure, preserving its parsing and language fallback.
         */
        fun fromLanguageTag(languageTag: String): AnkiBackendLanguage? {
            if (languageTag.isBlank()) return null
            fromIdentifier(languageTag)?.let { return it }
            val language =
                try {
                    Locale
                        .Builder()
                        .setLanguageTag(languageTag.replace('_', '-'))
                        .clearExtensions() // Locale.stripExtensions() requires API 26.
                        .build()
                        .toLanguageTag()
                } catch (_: IllformedLocaleException) {
                    return null
                }
            return fromIdentifier(language)
        }

        private fun fromIdentifier(language: String): AnkiBackendLanguage? {
            val backendLanguage =
                when (language) {
                    "heb" -> "he"
                    "ind" -> "id"
                    "tgl" -> "tl"
                    "hi" -> "hi-IN"
                    else -> language
                }
            return backendLanguage.takeIf { LANGUAGE_IDENTIFIER.matches(it) }?.let { AnkiBackendLanguage(it) }
        }
    }
}

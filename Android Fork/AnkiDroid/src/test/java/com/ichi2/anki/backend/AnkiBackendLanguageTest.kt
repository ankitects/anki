// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.backend

import com.ichi2.utils.LanguageUtil
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AnkiBackendLanguageTest {
    @Test
    fun `accepts language identifiers with scripts regions and variants`() {
        val languages =
            listOf(
                "pl",
                "pl-PL",
                "zh-Hant",
                "zh-Hant-TW",
                "es-419",
                "ca-Latn-ES-valencia",
                "de-1901",
                "sl-rozaj-biske",
                "und",
                "qaa",
            )
        for (language in languages) {
            assertEquals(language, AnkiBackendLanguage.fromLanguageTag(language)?.value, language)
        }
    }

    @Test
    fun `removes locale extensions`() {
        for (language in listOf("pl-PL-u-fw-mon", "pl-PL-u-mu-celsius", "pl-PL-x-private", "pl-PL-a-example")) {
            assertEquals("pl-PL", AnkiBackendLanguage.fromLanguageTag(language)?.value, language)
        }
    }

    @Test
    fun `returns null for tags that cannot be normalized`() {
        val languages =
            listOf(
                "",
                " ",
                "e",
                "abcd",
                "123",
                "en-",
                "en--US",
                "en-US-Latn",
                "en-1234a5678",
                "en-12",
                "en US",
                "pl-PL-!",
            )
        for (language in languages) {
            assertNull(AnkiBackendLanguage.fromLanguageTag(language), language)
        }
    }

    @Test
    fun `removes private use before Java creates legacy variants`() {
        val languages =
            mapOf(
                "en-US-x-lvariant-WIN" to "en-US",
                "ja-JP-x-lvariant-JP" to "ja-JP",
                "th-TH-x-lvariant-TH" to "th-TH",
            )
        for ((language, expected) in languages) {
            assertEquals(expected, AnkiBackendLanguage.fromLanguageTag(language)?.value, language)
        }
    }

    @Test
    fun `accepts underscore separators supported by the backend`() {
        assertEquals("pt_BR", AnkiBackendLanguage.fromLanguageTag("pt_BR")?.value)
    }

    @Test
    fun `preserves identifiers which Java would map to another language`() {
        for (language in listOf("zh-guoyu", "zh-hakka", "zh-xiang")) {
            assertEquals(language, AnkiBackendLanguage.fromLanguageTag(language)?.value, language)
        }
    }

    @Test
    fun `all configured app languages can be converted`() {
        for (language in LanguageUtil.APP_LANGUAGES.values) {
            assertNotNull(AnkiBackendLanguage.fromLanguageTag(language), language)
        }
    }

    @Test
    fun `all configured backend languages are valid`() {
        for (language in LanguageUtil.BACKEND_LANGS) {
            assertEquals(language, AnkiBackendLanguage.fromLanguageTag(language)?.value, language)
        }
    }
}

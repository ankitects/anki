// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.backend

import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.testing.RustBackendLoader
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class BackendLocalesTest {
    @Before
    fun setUp() {
        RustBackendLoader.ensureSetup()
    }

    @Test
    fun `returns all active locales in preference order and the primary locale`() {
        Backend(langs = listOf("pl", "fr")).use { backend ->
            val locales = backend.getLocales()
            assertEquals(listOf("pl", "fr", "en-US"), locales.all)
            assertEquals("pl", locales.primary)
        }
    }

    @Test
    fun `skips unsupported preferred languages`() {
        Backend(langs = listOf("zz", "fr")).use { backend ->
            val locales = backend.getLocales()
            assertEquals(listOf("fr", "en-US"), locales.all)
            assertEquals("fr", locales.primary)
        }
    }

    @Test
    fun `falls back to English when no preferred language is available`() {
        for (languages in listOf(emptyList(), listOf("zz"), listOf("invalid_locale"))) {
            Backend(langs = languages).use { backend ->
                val locales = backend.getLocales()
                assertEquals(listOf("en-US"), locales.all, "Requested languages: $languages")
                assertEquals("en-US", locales.primary, "Requested languages: $languages")
            }
        }
    }
}

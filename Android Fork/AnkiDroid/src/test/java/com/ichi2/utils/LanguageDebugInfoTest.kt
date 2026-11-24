// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.utils

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.servicelayer.DebugInfoService
import kotlinx.coroutines.runBlocking
import net.ankiweb.rsdroid.BackendFactory
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertContains
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class LanguageDebugInfoTest : RobolectricTest() {
    @Before
    override fun setUp() {
        super.setUp()
        LanguageUtil.setDefaultBackendLanguages()
        runBlocking { CollectionManager.discardBackend() }
    }

    @Test
    @Config(qualifiers = "zn")
    fun `language debug info reports the backend fallback locale`() {
        assertEquals(listOf("en-US"), getLanguageDebugInfo(targetContext).backendLocales)
    }

    @Test
    @Config(qualifiers = "pl")
    fun `copied debug info includes the backend fallback order`() =
        runTest {
            assertContains(DebugInfoService.getDebugInfo(targetContext), "| Backend: pl, en-US")
        }

    @Test
    @Config(qualifiers = "pl")
    fun `language debug info uses the active backend instead of defaults for future backends`() {
        CollectionManager.getBackend()
        val originalLanguages = BackendFactory.defaultLanguages
        try {
            BackendFactory.defaultLanguages = listOf("fr")

            assertEquals(listOf("pl", "en-US"), getLanguageDebugInfo(targetContext).backendLocales)
        } finally {
            BackendFactory.defaultLanguages = originalLanguages
        }
    }
}

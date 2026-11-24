// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import com.ichi2.anki.CollectionManager
import com.ichi2.anki.backend.getLocales
import com.ichi2.anki.exception.CollectionLockedException
import com.ichi2.testutils.ext.reopenWithLanguage
import net.ankiweb.rsdroid.BackendFactory
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CollectionLanguageTest : JvmTest() {
    @Test
    fun `reopening changes the backend language without changing factory defaults`() =
        runTest {
            val previous = BackendFactory.defaultLanguages
            try {
                col.reopenWithLanguage("de")

                assertEquals("de", CollectionManager.getBackend().getLocales().primary)
                assertEquals(previous.toList(), BackendFactory.defaultLanguages.toList())
            } finally {
                BackendFactory.defaultLanguages = previous
            }
        }

    @Test
    fun `failed reopening also restores factory defaults`() =
        runTest {
            val collection = col
            val previousLanguages = BackendFactory.defaultLanguages
            val previousFailure = CollectionManager.emulatedOpenFailure
            try {
                CollectionManager.emulatedOpenFailure = CollectionManager.CollectionOpenFailure.LOCKED

                assertFailsWith<CollectionLockedException> { collection.reopenWithLanguage("de") }

                assertEquals(previousLanguages.toList(), BackendFactory.defaultLanguages.toList())
            } finally {
                CollectionManager.emulatedOpenFailure = previousFailure
                BackendFactory.defaultLanguages = previousLanguages
            }
        }
}

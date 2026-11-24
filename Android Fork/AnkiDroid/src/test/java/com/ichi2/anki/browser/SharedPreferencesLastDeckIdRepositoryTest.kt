// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import com.github.ivanshafran.sharedpreferencesmock.SPMockBuilder
import com.ichi2.anki.libanki.Decks
import com.ichi2.anki.preferences.SharedPreferencesProvider
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SharedPreferencesLastDeckIdRepositoryTest {
    private val preferences = SPMockBuilder().createSharedPreferences()
    private val provider = SharedPreferencesProvider { preferences }

    @Test
    fun `missing and sentinel deck IDs return null`() {
        val repository = SharedPreferencesLastDeckIdRepository(provider)
        assertNull(repository.lastDeckId)
        repository.lastDeckId = Decks.NOT_FOUND_DECK_ID
        assertNull(repository.lastDeckId)
    }

    @Test
    fun `deck ID persists between repository instances without losing precision`() {
        SharedPreferencesLastDeckIdRepository(provider).lastDeckId = Long.MAX_VALUE

        assertEquals(Long.MAX_VALUE, SharedPreferencesLastDeckIdRepository(provider).lastDeckId)
    }

    @Test
    fun `clearing the selection removes it from the injected preferences`() {
        val repository = SharedPreferencesLastDeckIdRepository(provider)
        repository.lastDeckId = 42L

        repository.lastDeckId = null

        assertNull(SharedPreferencesLastDeckIdRepository(provider).lastDeckId)
        assertTrue(preferences.all.isEmpty())
    }
}

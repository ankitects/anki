// SPDX-License-Identifier: LGPL-3.0-or-later

package com.ichi2.anki

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertContentEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@Suppress("DEPRECATION") // Verify compatibility with the original public fields.
class FlashCardsContractTest {
    @Test
    fun `note default columns are independent`() {
        assertIndependentColumns({ FlashCardsContract.Note.DEFAULT_COLUMNS }, FlashCardsContract.Note.DEFAULT_PROJECTION)
    }

    @Test
    fun `model default columns are independent`() {
        assertIndependentColumns({ FlashCardsContract.Model.DEFAULT_COLUMNS }, FlashCardsContract.Model.DEFAULT_PROJECTION)
    }

    @Test
    fun `card template default columns are independent`() {
        assertIndependentColumns({ FlashCardsContract.CardTemplate.DEFAULT_COLUMNS }, FlashCardsContract.CardTemplate.DEFAULT_PROJECTION)
    }

    @Test
    fun `card default columns are independent`() {
        assertIndependentColumns({ FlashCardsContract.Card.DEFAULT_COLUMNS }, FlashCardsContract.Card.DEFAULT_PROJECTION)
    }

    @Test
    fun `review info default columns are independent`() {
        assertIndependentColumns({ FlashCardsContract.ReviewInfo.DEFAULT_COLUMNS }, FlashCardsContract.ReviewInfo.DEFAULT_PROJECTION)
    }

    @Test
    fun `deck default columns are independent`() {
        assertIndependentColumns({ FlashCardsContract.Deck.DEFAULT_COLUMNS }, FlashCardsContract.Deck.DEFAULT_PROJECTION)
    }

    private fun assertIndependentColumns(
        defaultColumns: () -> Array<String>,
        legacyProjection: Array<String>,
    ) {
        val expected = legacyProjection.clone()
        val columns = defaultColumns()
        assertContentEquals(expected, columns)

        columns[0] = "modified column"
        assertContentEquals(expected, defaultColumns())
        assertContentEquals(expected, legacyProjection)

        try {
            legacyProjection[0] = "modified legacy column"
            assertContentEquals(expected, defaultColumns())
        } finally {
            expected.copyInto(legacyProjection)
        }
    }
}

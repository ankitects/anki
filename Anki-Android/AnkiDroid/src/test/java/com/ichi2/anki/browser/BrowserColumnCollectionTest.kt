/*
 *  Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.browser

import androidx.annotation.CheckResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.browser.CardBrowserColumn.DECK
import com.ichi2.anki.browser.CardBrowserColumn.FSRS_STABILITY
import com.ichi2.anki.browser.CardBrowserColumn.SFLD
import com.ichi2.anki.model.CardsOrNotes
import com.ichi2.anki.model.CardsOrNotes.CARDS
import com.ichi2.anki.model.CardsOrNotes.NOTES
import com.ichi2.testutils.getSharedPrefs
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasSize
import org.junit.Test
import org.junit.runner.RunWith

/** @see BrowserColumnCollection */
@RunWith(AndroidJUnit4::class)
class BrowserColumnCollectionTest : RobolectricTest() {
    val prefs = this.getSharedPrefs()

    @Test
    fun `cards - ensure sensible defaults`() {
        val default = load(CARDS)
        assertThat(default.toKeyArray(), equalTo(listOf("noteFld", "template", "cardDue", "deck")))
    }

    @Test
    fun `notes - ensure sensible default`() {
        val default = load(NOTES)
        assertThat(default.toKeyArray(), equalTo(listOf("noteFld", "note", "template", "noteTags")))
    }

    @Test
    fun `cards - update`() {
        updateColumns(CARDS) { columns -> columns.add(DECK) }
        val updated = load(CARDS)
        assertThat(updated.columns.last(), equalTo(DECK))
    }

    @Test
    fun `notes - update`() {
        updateColumns(NOTES) { columns -> columns.add(DECK) }
        val updated = load(NOTES)
        assertThat(updated.columns.last(), equalTo(DECK))
    }

    @Test
    fun `update with null column`() {
        // in AnkiMobile, if you update a column to 'none', the values are rearranged
        updateColumns(CARDS) { columns ->
            columns.clear()
            columns.add(SFLD)
            columns.add(null)
            columns.add(FSRS_STABILITY)
            assertThat("size of updated columns", columns, hasSize(3))
        }

        val updated = load(CARDS)
        assertThat("column size", updated.columns, hasSize(2))
        assertThat("first columns is unchanged", updated[0], equalTo(SFLD))
        assertThat("null column is replaced with third", updated[1], equalTo(FSRS_STABILITY))
    }

    private fun BrowserColumnCollection.toKeyArray() = columns.map { it.ankiColumnKey }

    @CheckResult
    private fun load(cardsOrNotes: CardsOrNotes) = BrowserColumnCollection.load(prefs, cardsOrNotes)

    private fun updateColumns(
        cardsOrNotes: CardsOrNotes,
        block: (MutableList<CardBrowserColumn?>) -> Unit,
    ) {
        BrowserColumnCollection.update(prefs, cardsOrNotes) {
            block(it)
            return@update true
        }
    }
}

/*
 *  Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>
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

import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.libanki.SortOrder
import com.ichi2.anki.model.CardsOrNotes
import com.ichi2.anki.model.SortType
import com.ichi2.anki.model.SortType.CollectionOrdering
import com.ichi2.anki.model.SortType.NoOrdering
import com.ichi2.anki.model.cardBrowserNoSorting
import com.ichi2.anki.settings.Prefs
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.jupiter.api.assertInstanceOf
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class SortTypeTest : RobolectricTest() {
    @Before
    override fun setUp() {
        super.setUp()
        Prefs.sharedPrefs.edit { remove("cardBrowserNoSorting") }
    }

    @Test
    fun `default sort type - CARDS`() =
        runTest {
            val defaultCards = SortType.build(cardsOrNotes = CardsOrNotes.CARDS)

            val ordering = assertInstanceOf<CollectionOrdering>(defaultCards)

            assertEquals("noteFld", ordering.key.value)
            assertEquals(false, ordering.reverse)
        }

    @Test
    fun `default sort type - NOTES`() =
        runTest {
            val defaultNotes = SortType.build(cardsOrNotes = CardsOrNotes.NOTES)

            val ordering = assertInstanceOf<CollectionOrdering>(defaultNotes)

            assertEquals("noteFld", ordering.key.value)
            assertEquals(false, ordering.reverse)
        }

    @Test
    fun `build produces no ordering when column is corrupt`() =
        runTest {
            col.config.set("noteSortType", "blah")

            val result = SortType.build(cardsOrNotes = CardsOrNotes.NOTES)

            assertInstanceOf<NoOrdering>(result)
        }

    @Test
    fun `cardBrowserNoSorting controls build type`() =
        runTest {
            Prefs.cardBrowserNoSorting = true

            SortType.build(cardsOrNotes = CardsOrNotes.NOTES).apply {
                assertInstanceOf<NoOrdering>(this)
            }

            Prefs.cardBrowserNoSorting = false

            SortType.build(cardsOrNotes = CardsOrNotes.NOTES).apply {
                assertInstanceOf<CollectionOrdering>(this)
            }
        }

    @Test
    fun `collection controls build() results - NOTES`() =
        runTest {
            col.config.set("noteSortType", "noteTags")
            col.config.set("browserNoteSortBackwards", true)

            val sortType = SortType.build(cardsOrNotes = CardsOrNotes.NOTES)
            val collectionSortType = assertInstanceOf<CollectionOrdering>(sortType)

            assertEquals("noteTags", collectionSortType.key.value)
            assertEquals(true, collectionSortType.reverse)
        }

    @Test
    fun `collection controls build() results - CARDS`() =
        runTest {
            col.config.set("sortType", "noteTags")
            col.config.set("sortBackwards", true)

            val sortType = SortType.build(cardsOrNotes = CardsOrNotes.CARDS)
            val collectionSortType = assertInstanceOf<CollectionOrdering>(sortType)

            assertEquals("noteTags", collectionSortType.key.value)
            assertEquals(true, collectionSortType.reverse)
        }

    @Test
    fun `test buildSortOrder`() =
        runTest {
            NoOrdering.save(CardsOrNotes.NOTES)
            assertInstanceOf<SortOrder.NoOrdering>(SortType.buildSortOrder())

            CollectionOrdering(BrowserColumnKey("noteTags"), true).save(CardsOrNotes.CARDS)
            assertInstanceOf<SortOrder.UseCollectionOrdering>(SortType.buildSortOrder())

            NoOrdering.save(CardsOrNotes.CARDS)
            assertInstanceOf<SortOrder.NoOrdering>(SortType.buildSortOrder())

            CollectionOrdering(BrowserColumnKey("noteTags"), true).save(CardsOrNotes.NOTES)
            assertInstanceOf<SortOrder.UseCollectionOrdering>(SortType.buildSortOrder())
        }

    @Test
    fun `save no ordering - CARDS`() =
        runTest {
            NoOrdering.save(CardsOrNotes.CARDS)

            val sortType = SortType.build(cardsOrNotes = CardsOrNotes.CARDS)
            assertInstanceOf<NoOrdering>(sortType)
        }

    @Test
    fun `save no ordering - NOTES`() =
        runTest {
            NoOrdering.save(CardsOrNotes.NOTES)

            val sortType = SortType.build(cardsOrNotes = CardsOrNotes.NOTES)
            assertInstanceOf<NoOrdering>(sortType)
        }

    @Test
    fun `save collection - CARDS`() =
        runTest {
            CollectionOrdering(BrowserColumnKey("noteTags"), true).save(CardsOrNotes.NOTES)

            val notesSortType = SortType.build(cardsOrNotes = CardsOrNotes.NOTES)
            assertInstanceOf<CollectionOrdering>(notesSortType).apply {
                assertEquals("noteTags", key.value, "notes - key")
                assertEquals(true, reverse, "notes - reverse")
            }
        }

    @Test
    fun `save collection - NOTES`() =
        runTest {
            CollectionOrdering(BrowserColumnKey("noteCrt"), false).save(CardsOrNotes.CARDS)

            val cardsSortType = SortType.build(cardsOrNotes = CardsOrNotes.CARDS)
            assertInstanceOf<CollectionOrdering>(cardsSortType).apply {
                assertEquals("noteCrt", key.value, "cards - key")
                assertEquals(false, reverse, "cards - reverse")
            }
        }

    @Test
    fun `save different column - NOTES and CARDS`() =
        runTest {
            CollectionOrdering(BrowserColumnKey("noteTags"), true).save(CardsOrNotes.NOTES)
            CollectionOrdering(BrowserColumnKey("noteCrt"), false).save(CardsOrNotes.CARDS)

            val notesSortType = SortType.build(cardsOrNotes = CardsOrNotes.NOTES)
            assertInstanceOf<CollectionOrdering>(notesSortType).apply {
                assertEquals("noteTags", key.value, "notes - key")
                assertEquals(true, reverse, "notes - reverse")
            }

            val cardsSortType = SortType.build(cardsOrNotes = CardsOrNotes.CARDS)
            assertInstanceOf<CollectionOrdering>(cardsSortType).apply {
                assertEquals("noteCrt", key.value, "cards - key")
                assertEquals(false, reverse, "cards - reverse")
            }
        }

    @Test
    @Ignore("TODO: NoOrdering should not affect both")
    fun `save types different - NOTES and CARDS`() =
        runTest {
            NoOrdering.save(CardsOrNotes.NOTES)

            val notesSortType = SortType.build(cardsOrNotes = CardsOrNotes.NOTES)
            assertInstanceOf<NoOrdering>(notesSortType)

            val cardsSortType = SortType.build(cardsOrNotes = CardsOrNotes.CARDS)
            assertInstanceOf<CollectionOrdering>(cardsSortType)
        }
}

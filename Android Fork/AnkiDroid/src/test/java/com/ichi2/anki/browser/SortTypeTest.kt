// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import com.ichi2.anki.libanki.SortOrder
import com.ichi2.anki.model.CardsOrNotes
import com.ichi2.anki.model.SortType
import com.ichi2.anki.model.SortType.CollectionOrdering
import com.ichi2.anki.model.SortType.NoOrdering
import com.ichi2.anki.settings.Prefs
import com.ichi2.testutils.JvmPreferencesRule
import com.ichi2.testutils.JvmTest
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.assertInstanceOf
import kotlin.test.assertEquals

class SortTypeTest : JvmTest() {
    @get:Rule(order = 0)
    val preferences = JvmPreferencesRule()

    @Before
    override fun setUp() {
        super.setUp()
        Prefs.cardBrowserNoSorting = false
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

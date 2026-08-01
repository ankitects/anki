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

package com.ichi2.anki.browser.search

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.EmptyApplicationCategory
import com.ichi2.anki.Flag
import com.ichi2.anki.browser.SearchHistory.SearchHistoryEntry
import com.ichi2.anki.libanki.DeckNameId
import com.ichi2.anki.libanki.NoteTypeNameID
import com.ichi2.anki.libanki.exception.InvalidSearchException
import com.ichi2.anki.libanki.testutils.AnkiTest
import com.ichi2.testutils.EmptyApplication
import com.ichi2.testutils.JvmTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertFailsWith

/** Tests for [SearchRequest] */
@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class SearchRequestTest : JvmTest() {
    @Test
    fun `search string generation - empty`() {
        val entry = SearchRequest(query = "")
        assertThat(entry.toValidSearchString(), equalTo("deck:*"))
    }

    @Test
    fun `search string generation - simple`() {
        val entry = SearchRequest(query = "hello")
        assertThat(entry.toValidSearchString(), equalTo("hello"))
    }

    @Test
    fun `search string generation - filter only`() {
        val entry = SearchRequest(query = "", filters = SearchFilters.partial(decks = col.decks.allNamesAndIds()))
        assertThat(entry.toValidSearchString(), equalTo("deck:Default"))
    }

    @Test
    fun `search string generation - complex`() {
        assertThat(
            validEntry().toValidSearchString(),
            equalTo(
                "queryString " +
                    "(deck:aa OR deck:Default OR \"deck:with space\") " +
                    "(flag:1 OR flag:4) (tag:Hello::World OR tag:tag) " +
                    "(note:Basic OR note:Cloze) " +
                    "(is:new OR is:learn OR is:review OR is:buried OR is:suspended)",
            ),
        )
    }

    @Test
    fun `search string generation - invalid query`() =
        runTest {
            val invalidRequest = SearchRequest("and")
            val searchStringResult = invalidRequest.toSearchStringResult()
            assertFailsWith<InvalidSearchException> { searchStringResult.getOrThrow() }
        }

    /**
     * Returns a [SearchRequest] with all filters set
     *
     * Use [validEntry] if the Ids must match objects in the collection
     */
    fun complexRequest() =
        SearchRequest(
            query = "queryString",
            filters =
                SearchFilters(
                    decks = listOf(DeckNameId("Default", 1), DeckNameId("Custom", 2)),
                    flags = listOf(Flag.RED, Flag.BLUE),
                    tags = listOf("Hello::World", "tag"),
                    noteTypes = listOf(NoteTypeNameID("Basic", 3), NoteTypeNameID("Advanced", 4)),
                    cardStates =
                        listOf(
                            CardState.New,
                            CardState.Learning,
                            CardState.Review,
                            CardState.Buried,
                            CardState.Suspended,
                        ),
                ),
        )

    /**
     * Builds a valid [SearchHistoryEntry]
     *
     * Use [complexRequest] if you don't need a valid entry, as this method performs DB access
     */
    fun validEntry(): SearchRequest {
        addDeck("aa")
        addDeck("with space")
        return complexRequest().copyFilters {
            it.copy(
                // ensure the deck Id constants point to valid decks
                decks = col.decks.allNamesAndIds(),
                noteTypes =
                    listOf(
                        col.notetypes.basic,
                        col.notetypes.cloze,
                    ).map { NoteTypeNameID.fromNoteTypeJson(it) },
            )
        }
    }
}

context(test: AnkiTest)
fun SearchRequest.toValidSearchString(): String =
    with(test.col) {
        return this@toValidSearchString.toSearchString().getOrThrow().value
    }

suspend fun SearchRequest.toSearchString(): String? = withCol { toSearchString() }.getOrNull()?.value

suspend fun SearchRequest.toSearchStringResult(): Result<SearchString> = withCol { toSearchString() }

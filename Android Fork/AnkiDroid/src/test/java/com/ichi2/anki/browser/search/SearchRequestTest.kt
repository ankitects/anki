// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser.search

import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.Flag
import com.ichi2.anki.browser.SearchHistory.SearchHistoryEntry
import com.ichi2.anki.libanki.DeckNameId
import com.ichi2.anki.libanki.NoteTypeNameID
import com.ichi2.anki.libanki.exception.InvalidSearchException
import com.ichi2.anki.libanki.testutils.AnkiTest
import com.ichi2.testutils.JvmTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsInAnyOrder
import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import kotlin.test.assertFailsWith

/** Tests for [SearchRequest] */
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
    fun `deck filter - a deck named underscore star is not a wildcard`() {
        // unescaped, '_*' matches every deck
        val selected = addBasicNote("selected").apply { moveToDeck("_*") }
        addBasicNote("unrelated").apply { moveToDeck("Other") }

        val entry = deckSearch("_*")

        assertThat(entry.toValidSearchString(), equalTo("""deck:\_\*"""))
        assertThat(entry.cardsFound(), containsInAnyOrder(selected.firstCard().id))
    }

    @Test
    fun `deck filter - wildcards in the deck name are escaped`() {
        // '_' is a single character wildcard, so an unescaped name also matches 'Chapter11'
        val selected = addBasicNote("selected").apply { moveToDeck("Chapter_1") }
        addBasicNote("unrelated").apply { moveToDeck("Chapter11") }

        assertThat(deckSearch("Chapter_1").cardsFound(), containsInAnyOrder(selected.firstCard().id))
    }

    @Test
    fun `deck filter - OR alternatives stay within the deck`() {
        val first = addBasicNote("first").apply { moveToDeck("Selected") }
        val second = addBasicNote("second").apply { moveToDeck("Selected") }
        addBasicNote("second").apply { moveToDeck("Other") }

        val entry = deckSearch("Selected", query = "first OR second")

        assertThat(entry.cardsFound(), containsInAnyOrder(first.firstCard().id, second.firstCard().id))
    }

    @Test
    fun `deck filter - a deck in the query does not replace the deck filter`() {
        addBasicNote("selected").apply { moveToDeck("Selected") }
        addBasicNote("unrelated").apply { moveToDeck("Other") }

        assertThat(deckSearch("Selected", query = "deck:Other").cardsFound(), empty())
    }

    @Test
    fun `deck filter - a renamed deck uses its current name`() {
        val selected = addBasicNote("selected").apply { moveToDeck("Selected") }
        val entry = deckSearch("Selected")

        // the request keeps the old name, which is then reused by another deck
        col.decks.rename(requireNotNull(col.decks.idForName("Selected")), "Renamed")
        addBasicNote("unrelated").apply { moveToDeck("Selected") }

        assertThat(entry.cardsFound(), containsInAnyOrder(selected.firstCard().id))
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

    /** A search for [query] in the existing deck named [deckName] */
    private fun deckSearch(
        deckName: String,
        query: String = "",
    ): SearchRequest {
        val deck = DeckNameId(deckName, requireNotNull(col.decks.idForName(deckName)))
        return SearchRequest(query = query, filters = SearchFilters.partial(decks = listOf(deck)))
    }

    private fun SearchRequest.cardsFound() = col.findCards(toValidSearchString())

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

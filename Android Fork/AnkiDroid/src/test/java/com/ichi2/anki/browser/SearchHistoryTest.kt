// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import androidx.annotation.CheckResult
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.EmptyApplicationCategory
import com.ichi2.anki.Flag
import com.ichi2.anki.R
import com.ichi2.anki.browser.SearchHistory.SearchHistoryEntry
import com.ichi2.anki.browser.search.CardState
import com.ichi2.anki.browser.search.SearchFilters
import com.ichi2.anki.browser.search.SearchRequest
import com.ichi2.anki.browser.search.from
import com.ichi2.anki.libanki.Consts.DEFAULT_DECK_ID
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.NoteTypeId
import com.ichi2.anki.settings.Prefs
import com.ichi2.testutils.AndroidTest
import com.ichi2.testutils.EmptyApplication
import com.ichi2.testutils.JvmPreferencesRule
import com.ichi2.testutils.JvmTest
import com.ichi2.testutils.getString
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.equalTo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Tests for [SearchHistory]
 */
class SearchHistoryTest : JvmTest() {
    @get:Rule
    val preferences = JvmPreferencesRule()

    private val history by lazy { SearchHistory(maxEntries = 5) }

    @Test
    fun `entries is empty if no key is set`() {
        assertThat(history.entries, empty())
    }

    @Test
    fun `entries is empty if corrupt`() {
        writeSearchHistoryRaw("A")
        assertThat(history.entries, empty())
    }

    @Test
    fun `entries returns written value`() {
        history.addRecent(SearchHistoryEntry("A"))
        assertEntriesEquals("A")
    }

    @Test
    fun `entries skips duplicate values`() {
        history.addRecent(SearchHistoryEntry("A"))
        history.addRecent(SearchHistoryEntry("A"))
        assertEntriesEquals("A")
    }

    @Test
    fun `entries returns latest values first`() {
        history.addRecent(SearchHistoryEntry("A"))
        history.addRecent(SearchHistoryEntry("B"))
        assertEntriesEquals("B", "A")
    }

    @Test
    fun `entries truncates least recently used`() {
        addNumberedEntries(6)
        assertEntriesEquals("6", "5", "4", "3", "2")

        // no more truncation occurs
        history.addRecent(SearchHistoryEntry("2"))
        assertEntriesEquals("2", "6", "5", "4", "3")
    }

    @Test
    fun `clear on empty list does nothing`() {
        history.clear()
        assertThat(history.entries, empty())
    }

    @Test
    fun `clear on full list empties list`() {
        addNumberedEntries(6)
        history.clear()
        assertThat(history.entries, empty())
    }

    @Test
    fun `remove non-existing entry`() {
        assertFalse(history.removeEntry(SearchHistoryEntry("AA")))
    }

    @Test
    fun `remove existing entry`() {
        addNumberedEntries(6)
        assertTrue(history.removeEntry(SearchHistoryEntry("5")))
        assertEntriesEquals("6", "4", "3", "2")
    }

    @Test
    fun `v1 serialization is unchanged`() {
        // additional properties will be added; make sure we don't corrupt past entries
        addNumberedEntries(1)
        assertThat(readSearchHistoryRaw(), equalTo("""[{"q":"1"}]"""))
        with(history.entries.single()) {
            assertThat(query, equalTo("1"))
            assertThat(deckIds, empty())
            assertThat(flags, empty())
            assertThat(tags, empty())
            assertThat(noteTypes, empty())
            assertThat(cardStates, empty())
        }
    }

    @Test
    fun `v2 serialization is unchanged`() {
        // additional properties will be added; make sure we don't corrupt past entries
        history.addRecent(complexEntry())
        assertThat(
            readSearchHistoryRaw(),
            equalTo(
                """[{"q":"queryString","did":[1,2],"f":[1,4],"t":["Hello::World","tag"],"ntid":[3,4],"s":[0,1,2,3,4]}]""",
            ),
        )

        with(history.entries.single()) {
            assertThat(query, equalTo("queryString"))
            assertThat(deckIds, equalTo(listOf<DeckId>(1, 2)))
            assertThat(flags, equalTo(listOf(Flag.RED, Flag.BLUE)))
            assertThat(tags, equalTo(listOf("Hello::World", "tag")))
            assertThat(noteTypes, equalTo(listOf<NoteTypeId>(3, 4)))
            assertThat(
                cardStates,
                equalTo(
                    listOf(
                        CardState.New,
                        CardState.Learning,
                        CardState.Review,
                        CardState.Buried,
                        CardState.Suspended,
                    ),
                ),
            )
        }
    }

    @Test
    fun `blank entries are not persisted`() {
        history.addRecent(SearchHistoryEntry(""))
        history.addRecent(SearchHistoryEntry(" "))
        assertThat(history.entries, empty())
    }

    @Test
    fun `search string generation - empty`() =
        runTest {
            val entry = SearchHistoryEntry(query = "")
            assertThat(entry.toSearchString(), equalTo("deck:*"))
        }

    @Test
    fun `search string generation - simple`() =
        runTest {
            val entry = SearchHistoryEntry(query = "hello")
            assertThat(entry.toSearchString(), equalTo("hello"))
        }

    @Test
    fun `search string generation - filter only`() =
        runTest {
            val entry = SearchHistoryEntry(query = "", deckIds = listOf(1))
            assertThat(entry.toSearchString(), equalTo("deck:Default"))
        }

    @Test
    fun `search string generation - complex`() =
        runTest {
            assertThat(
                validEntry().toSearchString(),
                equalTo(
                    "queryString " +
                        "(deck:Default OR deck:aa OR \"deck:with space\") " +
                        "(flag:1 OR flag:4) (tag:Hello::World OR tag:tag) " +
                        "(note:Basic OR note:Cloze) " +
                        "(is:new OR is:learn OR is:review OR is:buried OR is:suspended)",
                ),
            )
        }

    @Test
    fun `search string generation - invalid deck`() =
        runTest {
            val invalidEntry = validEntry().copy(deckIds = listOf(42, 83))
            assertThat(invalidEntry.toSearchString(), equalTo(null))
        }

    @Test
    fun `search string generation - invalid note type`() =
        runTest {
            val invalidEntry = validEntry().copy(noteTypes = listOf(42, 83))
            assertThat(invalidEntry.toSearchString(), equalTo(null))
        }

    /**
     * Returns a [SearchHistoryEntry] with all filters set
     *
     * Use [validEntry] if the Ids must match objects in the collection
     */
    fun complexEntry() =
        SearchHistoryEntry(
            query = "queryString",
            deckIds = listOf(1, 2),
            flags = listOf(Flag.RED, Flag.BLUE),
            tags = listOf("Hello::World", "tag"),
            noteTypes = listOf(3, 4),
            cardStates =
                listOf(
                    CardState.New,
                    CardState.Learning,
                    CardState.Review,
                    CardState.Buried,
                    CardState.Suspended,
                ),
        )

    /**
     * Builds a valid [SearchHistoryEntry]
     *
     * Use [complexEntry] if you don't need a valid entry, as this method performs DB access
     */
    fun validEntry() =
        complexEntry().copy(
            // ensure the deck Id constants point to valid decks
            deckIds = listOf(DEFAULT_DECK_ID, addDeck("aa"), addDeck("with space")),
            noteTypes = listOf(col.notetypes.basic.id, col.notetypes.cloze.id),
        )

    /** Adds numbered entries from 1 to [count] inclusive */
    fun addNumberedEntries(count: Int) =
        repeat(count) {
            history.addRecent(SearchHistoryEntry((it + 1).toString()))
        }

    fun assertEntriesEquals(vararg entries: String) {
        val listOfEntities = entries.map(::SearchHistoryEntry)
        assertThat(history.entries, equalTo(listOfEntities))
    }
}

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class SearchHistoryPreferenceKeyTest : AndroidTest {
    @Test
    fun `pref key is unchanged`() {
        // this is checked in CrashReportService to ensure user data isn't sent to our servers.
        assertThat(getString(R.string.pref_browser_search_history), equalTo("browser_search_history"))
    }
}

fun writeSearchHistoryRaw(value: String?) = Prefs.sharedPrefs.edit { putString(Prefs.key(R.string.pref_browser_search_history), value) }

@CheckResult
fun readSearchHistoryRaw() = Prefs.sharedPrefs.getString(Prefs.key(R.string.pref_browser_search_history), null)!!

suspend fun SearchHistoryEntry.toSearchString(): String? {
    val filters = SearchFilters.from(this@toSearchString) ?: return null
    val request = SearchRequest(this.query, filters)
    return withCol { request.toSearchString() }.getOrNull()?.value
}

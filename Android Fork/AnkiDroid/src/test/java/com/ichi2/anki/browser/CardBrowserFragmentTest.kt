// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import android.content.Intent
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.android.material.chip.Chip
import com.ichi2.anki.CardBrowser
import com.ichi2.anki.Flag
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.browser.search.AdvancedSearchFragment
import com.ichi2.anki.browser.search.CardState
import com.ichi2.anki.browser.search.SavedSearch
import com.ichi2.anki.browser.search.SearchFilters
import com.ichi2.anki.browser.search.StandardSearchFragment
import com.ichi2.anki.browser.search.requireChildFragment
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.common.crashreporting.CrashReporter
import com.ichi2.anki.filtered.FilteredDeckOptionsFragment
import com.ichi2.anki.getVisibleRows
import com.ichi2.anki.libanki.DeckNameId
import com.ichi2.anki.model.SelectableDeck
import com.ichi2.anki.model.SortType
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.throwOnShowError
import com.ichi2.anki.utils.ext.hasCheckedBackground
import com.ichi2.testutils.withPref
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.sameInstance
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsInAnyOrder
import org.hamcrest.Matchers.empty
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Test of [CardBrowserFragment] */
@RunWith(AndroidJUnit4::class)
class CardBrowserFragmentTest : RobolectricTest() {
    @Test
    fun `search results completed while stopped are displayed on restart - Issue 17759`() =
        runTest {
            val removedNote = addBasicNote("dog", "barks")
            val matchingRow = CardOrNoteId(addBasicNote("cat", "meows").firstCard().id)

            withStartedBrowser { browser ->
                val adapter = browser.cardBrowserFragment.cardsAdapter
                assertThat("initial search results", adapter.itemCount, equalTo(2))

                // The browser stops collecting search updates while the note editor is open.
                stopBrowser()
                col.removeNotes(noteIds = listOf(removedNote.id))
                browser.refreshSearchAndWait()

                assertThat(browser.viewModel.cards.toList(), equalTo(listOf(matchingRow)))
                assertThat("the stopped adapter has not received the new results", adapter.itemCount, equalTo(2))

                val completedSearch = browser.viewModel.searchJob
                resumeBrowser { resumedBrowser ->
                    assertThat(resumedBrowser.cardBrowserFragment.cardsAdapter, sameInstance(adapter))
                    assertThat("resuming does not run another search", resumedBrowser.viewModel.searchJob, sameInstance(completedSearch))
                    assertThat("the adapter displays the completed search", adapter.itemCount, equalTo(1))
                    assertThat(resumedBrowser.getVisibleRows().map { it.id }, equalTo(listOf(matchingRow)))
                }
            }
        }

    @Test
    fun `searchView EditText submit via IME_ACTION_SEARCH`() =
        withCardBrowserFragment(useSearchView = true) {
            searchViewModel.submittedSearchFlow.test {
                searchViewModel.isScreenOpenFlow.value = true
                searchView!!.editText.onEditorAction(IME_ACTION_SEARCH)
                expectMostRecentItem()
            }
        }

    @Test
    fun `searchView EditText submit via KEYCODE_ENTER`() =
        withCardBrowserFragment(useSearchView = true) {
            searchViewModel.submittedSearchFlow.test {
                searchViewModel.isScreenOpenFlow.value = true
                searchView!!.editText.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                expectMostRecentItem()
            }
        }

    @Test
    fun `selecting a saved search keeps its query when current chips already match`() =
        withCardBrowserFragment(useSearchView = true) {
            activityViewModel.setSelectedDeck(SelectableDeck.AllDecks)
            activityViewModel.searchJob?.join()
            advanceRobolectricLooper()
            searchViewModel.setFlagsFilter(listOf(Flag.BLUE))
            activityViewModel.searchJob?.join()
            advanceRobolectricLooper()
            assertThat(activityViewModel.searchRequestFlow.value.query, equalTo(""))
            assertThat(activityViewModel.searchRequestFlow.value.filters.flags, equalTo(listOf(Flag.BLUE)))
            val completedSearch = assertNotNull(activityViewModel.searchJob)

            assertNotNull(searchView).show()
            advanceRobolectricLooper()
            searchViewModel.submitSavedSearch(SavedSearch("Blue cards", "flag:4"))
            activityViewModel.searchJob?.join()
            advanceRobolectricLooper()

            assertThat(activityViewModel.searchRequestFlow.value.query, equalTo("flag:4"))
            assertThat(activityViewModel.searchRequestFlow.value.filters, equalTo(SearchFilters.EMPTY))
            assertThat("equivalent searches reuse the results", activityViewModel.searchJob, sameInstance(completedSearch))
            assertNotNull(searchView).show()
            advanceRobolectricLooper()
            assertThat(assertNotNull(searchView).editText.text.toString(), equalTo("flag:4"))
        }

    @Test
    fun `new search view leaves Search tools disabled by default`() =
        withCardBrowserFragment(useSearchView = true) {
            searchView!!.show()
            advanceRobolectricLooper()

            val basic = requireChildFragment<StandardSearchFragment>(StandardSearchFragment.TAG)
            assertThat("Basic search remains available", basic.isVisible, equalTo(true))
            assertFalse(basic.binding.toggleAdvancedSearch.isVisible)
            assertSearchMode(advancedSearch = false)
            assertNull(childFragmentManager.findFragmentByTag(AdvancedSearchFragment.TAG))
        }

    @Test
    fun `switching search modes preserves draft text and selection`() =
        withCardBrowserFragment(useSearchView = true, useSearchTools = true) {
            val search = searchView!!
            search.show()
            advanceRobolectricLooper()
            assertSearchMode(advancedSearch = false)
            val basicSearch = requireChildFragment<StandardSearchFragment>(StandardSearchFragment.TAG)
            assertTrue(basicSearch.binding.toggleAdvancedSearch.isVisible)

            val queries = listOf("  basic draft  ", "tag:example draft", "")
            for (query in queries) {
                val selections = listOf(0 to 0, 5 to 5, 3 to 8, query.length to query.length)
                for ((start, end) in selections) {
                    search.editText.setText(query)
                    val selectionStart = start.coerceAtMost(query.length)
                    val selectionEnd = end.coerceAtMost(query.length)
                    search.editText.setSelection(selectionStart, selectionEnd)
                    advanceRobolectricLooper()

                    for (advancedSearch in listOf(true, false)) {
                        search.findViewById<View>(R.id.toggle_advanced_search).performClick()
                        advanceRobolectricLooper()
                        assertSearchMode(advancedSearch)

                        assertThat(search.editText.text.toString(), equalTo(query))
                        assertThat(search.editText.selectionStart, equalTo(selectionStart))
                        assertThat(search.editText.selectionEnd, equalTo(selectionEnd))
                    }
                }
            }
        }

    @Test
    fun `edits in Search tools replace the Basic draft`() =
        withCardBrowserFragment(useSearchView = true, useSearchTools = true) {
            val search = searchView!!
            search.show()
            advanceRobolectricLooper()
            assertSearchMode(advancedSearch = false)

            for (query in listOf("  tag:edited draft  ", "")) {
                search.editText.setText("basic draft")
                search.findViewById<View>(R.id.toggle_advanced_search).performClick()
                advanceRobolectricLooper()
                assertSearchMode(advancedSearch = true)

                search.editText.setText(query)
                search.editText.setSelection(query.length)
                advanceRobolectricLooper()

                search.findViewById<View>(R.id.toggle_advanced_search).performClick()
                advanceRobolectricLooper()
                assertSearchMode(advancedSearch = false)

                assertThat(search.editText.text.toString(), equalTo(query))
                assertThat(search.editText.selectionStart, equalTo(query.length))
                assertThat(search.editText.selectionEnd, equalTo(query.length))
            }
        }

    @Test
    fun `filter chips remain visible in Search tools - Issue 23116`() =
        withCardBrowserFragment(useSearchView = true, useSearchTools = true) {
            val search = assertNotNull(searchView)
            search.show()
            advanceRobolectricLooper()

            searchViewModel.setDecksFilter(col.decks.allNamesAndIds())
            searchViewModel.setTagsFilter(listOf("example"))
            searchViewModel.setCardStateFilter(listOf(CardState.New))
            searchViewModel.setFlagsFilter(listOf(Flag.BLUE))
            advanceRobolectricLooper()

            val chips =
                listOf(R.id.decks_chip, R.id.tags_chip, R.id.card_state_chip, R.id.flags_chip).map {
                    search.findViewById<Chip>(it)
                }
            val labels = chips.map { it.text.toString() }

            for (advancedSearch in listOf(false, true, false)) {
                if (searchViewModel.advancedSearchFlow.value != advancedSearch) {
                    search.findViewById<View>(R.id.toggle_advanced_search).performClick()
                    advanceRobolectricLooper()
                }
                assertSearchMode(advancedSearch)
                for ((chip, label) in chips.zip(labels)) {
                    assertThat("$label chip visibility", chip.isShown, equalTo(true))
                    assertThat("$label chip selection", chip.hasCheckedBackground, equalTo(true))
                    assertThat(chip.text.toString(), equalTo(label))
                }
            }
        }

    @Test
    fun `filtered deck search is the browser search`() =
        withCardBrowserFragment {
            // 'OR' binds looser than the selected deck, and '_' is a wildcard which also matches 'Chapter11'
            val first = addBasicNote("first").apply { moveToDeck("Chapter_1") }
            val second = addBasicNote("second").apply { moveToDeck("Chapter_1") }
            addBasicNote("second").apply { moveToDeck("Chapter11") }
            addBasicNote("first").apply { moveToDeck("Other") }
            val deckId = requireNotNull(col.decks.idForName("Chapter_1"))
            activityViewModel.searchRequestFlow.update { request ->
                request.copy(query = "first OR second").copyFilters { it.copy(decks = listOf(DeckNameId("Chapter_1", deckId))) }
            }

            val search = filteredDeckSearch()

            assertThat(col.findCards(search), containsInAnyOrder(first.firstCard().id, second.firstCard().id))
        }

    @Test
    fun `invalid browser query is not reported as a crash when creating a filtered deck`() {
        val originalReporter = CrashReportService.getReporter()
        val originalThrowOnShowError = throwOnShowError
        val reporter = mock<CrashReporter>()
        CrashReportService.setReporter(reporter)
        try {
            withCardBrowserFragment {
                throwOnShowError = false
                activityViewModel.setQuery("and").join()
                advanceRobolectricLooper()

                showFilteredDeckScreen()
                advanceRobolectricLooper()
                // The error handler sends its crash report when the dialog is dismissed.
                requireNotNull(ShadowDialog.getLatestDialog()) { "the error is shown" }.dismiss()
                advanceRobolectricLooper()

                verify(reporter, never()).sendExceptionReport(any<Throwable>(), anyOrNull(), anyOrNull(), any())
            }
        } finally {
            CrashReportService.setReporter(originalReporter)
            throwOnShowError = originalThrowOnShowError
        }
    }

    @Test
    fun `search view stays open after leaving and returning once a search was submitted`() =
        withPref(Prefs::devUsingCardBrowserSearchView, true) {
            withPref(Prefs::devUsingCardBrowserSearchTools, true) {
                runTest {
                    withStartedBrowser { browser ->
                        val fragment = browser.cardBrowserFragment
                        val search = assertNotNull(fragment.searchView)
                        search.show()
                        advanceRobolectricLooper()
                        fragment.searchViewModel.onSearchTextChanged("a")
                        fragment.searchViewModel.submitCurrentSearch()
                        browser.viewModel.searchJob?.join()
                        advanceRobolectricLooper()
                        assertThat("submitting closes the search view", search.isShowing, equalTo(false))

                        search.show()
                        advanceRobolectricLooper()
                        fragment
                            .requireChildFragment<StandardSearchFragment>(StandardSearchFragment.TAG)
                            .binding.toggleAdvancedSearch
                            .performClick()
                        advanceRobolectricLooper()
                        fragment.assertSearchMode(advancedSearch = true)
                        assertThat("search view open before leaving", search.isShowing, equalTo(true))

                        stopBrowser()
                        resumeBrowser { }
                        advanceRobolectricLooper()

                        assertThat("search view is still open", search.isShowing, equalTo(true))
                        assertThat("Search tools still enabled", fragment.searchViewModel.advancedSearchFlow.value, equalTo(true))
                    }
                }
            }
        }

    @Test
    fun `shortcut labels use Sentence case`() =
        withCardBrowserFragment {
            val offenders = shortcuts.shortcuts.map { it.label }.filterNot { it.isSentenceCase }
            assertThat("shortcut labels should be Sentence case, not Title Case", offenders, empty())
        }

    @Test
    fun `sort chip describes the saved sort order`() {
        col.config.set("sortType", "noteCrt")
        col.config.set("sortBackwards", true)

        withCardBrowserFragment(useSearchView = true) {
            advanceRobolectricLooper()
            assertThat(sortChipDescription, equalTo("Sort by Created · Newest first"))
        }
    }

    @Test
    fun `sort chip description follows sort changes`() =
        withCardBrowserFragment(useSearchView = true) {
            activityViewModel.setSortType(SortType.CollectionOrdering(BrowserColumnKey("cardEase"), reverse = false)).join()
            advanceRobolectricLooper()
            assertThat(sortChipDescription, equalTo("Sort by Ease · Low to high"))

            activityViewModel.setSortType(SortType.NoOrdering).join()
            advanceRobolectricLooper()
            assertThat(sortChipDescription, equalTo("No sorting"))
        }

    private fun CardBrowserFragment.assertSearchMode(advancedSearch: Boolean) {
        assertThat("active search mode", searchViewModel.advancedSearchFlow.value, equalTo(advancedSearch))
        val basic = requireChildFragment<StandardSearchFragment>(StandardSearchFragment.TAG)
        val tools = basic.requireChildFragment<AdvancedSearchFragment>(AdvancedSearchFragment.TAG)
        assertThat("Basic search visibility", basic.binding.basicSearchContent.isShown, equalTo(!advancedSearch))
        assertThat("Search tools visibility", tools.requireView().isShown, equalTo(advancedSearch))
    }

    /** Opens the filtered deck screen, returning the search it was opened with */
    private fun CardBrowserFragment.filteredDeckSearch(): String {
        showFilteredDeckScreen()

        val activity = shadowOf(requireActivity())
        advanceRobolectricLooperUntil { activity.peekNextStartedActivity() != null }
        val intent = requireNotNull(activity.nextStartedActivity)
        assertThat(
            intent.getStringExtra(SingleFragmentActivity.EXTRA_FRAGMENT_NAME),
            equalTo(FilteredDeckOptionsFragment::class.java.name),
        )
        val arguments = requireNotNull(intent.getBundleExtra(SingleFragmentActivity.EXTRA_FRAGMENT_ARGS))
        return requireNotNull(arguments.getString(FilteredDeckOptionsFragment.ARG_SEARCH))
    }

    private suspend fun withStartedBrowser(block: suspend ActivityScenario<CardBrowser>.(CardBrowser) -> Unit) {
        ActivityScenario.launch<CardBrowser>(Intent(targetContext, CardBrowser::class.java)).use { scenario ->
            lateinit var browser: CardBrowser
            scenario.onActivity { browser = it }
            browser.viewModel.searchJob?.join()
            advanceRobolectricLooper()
            scenario.block(browser)
        }
    }

    private fun ActivityScenario<CardBrowser>.stopBrowser() {
        moveToState(Lifecycle.State.CREATED)
        onActivity { browser ->
            assertThat(browser.cardBrowserFragment.lifecycle.currentState, equalTo(Lifecycle.State.CREATED))
        }
    }

    private suspend fun CardBrowser.refreshSearchAndWait() {
        viewModel.launchSearchForCards()
        requireNotNull(viewModel.searchJob).join()
        advanceRobolectricLooper()
    }

    private fun ActivityScenario<CardBrowser>.resumeBrowser(block: (CardBrowser) -> Unit) {
        moveToState(Lifecycle.State.RESUMED)
        advanceRobolectricLooper()
        onActivity(block)
    }
}

private val CardBrowserFragment.sortChipDescription: String?
    get() = requireView().findViewById<Chip>(R.id.sort_chip).contentDescription?.toString()

private val capitalizedFollowingWord = Regex("""\s\p{Lu}\p{Ll}""")

// Material Design sentence case: the first word is capitalized; later words are not
private val String.isSentenceCase: Boolean
    get() = first().isUpperCase() && !capitalizedFollowingWord.containsMatchIn(this)

context(test: RobolectricTest)
fun withCardBrowserFragment(
    useSearchView: Boolean = false,
    useSearchTools: Boolean = false,
    block: suspend CardBrowserFragment.() -> Unit,
) = test.runTest {
    try {
        Prefs.devUsingCardBrowserSearchView = useSearchView
        Prefs.devUsingCardBrowserSearchTools = useSearchTools

        val cardBrowserIntent = Intent(test.targetContext, CardBrowser::class.java)

        ActivityScenario.launch<CardBrowser>(cardBrowserIntent).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)

            scenario.onActivity { browser ->
                assertThat("Activity is not finishing", !browser.isFinishing)
                assertThat(browser.useSearchView, equalTo(useSearchView))

                runBlocking { block(browser.cardBrowserFragment) }
            }
        }
    } finally {
        Prefs.devUsingCardBrowserSearchView = false
        Prefs.devUsingCardBrowserSearchTools = false
    }
}

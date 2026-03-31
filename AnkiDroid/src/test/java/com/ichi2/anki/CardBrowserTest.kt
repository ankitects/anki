/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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
package com.ichi2.anki

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.Menu
import android.view.View
import android.widget.Spinner
import android.widget.SpinnerAdapter
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import androidx.core.os.bundleOf
import androidx.core.view.children
import androidx.core.view.get
import androidx.core.view.size
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollCompletelyTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isNotChecked
import androidx.test.espresso.matcher.ViewMatchers.isNotEnabled
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withSpinnerText
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.search.BrowserRow
import anki.search.BrowserRow.Color
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.IntentHandler.Companion.grantedStoragePermissions
import com.ichi2.anki.RobolectricTest.Companion.advanceRobolectricLooper
import com.ichi2.anki.browser.BrowserMultiColumnAdapter
import com.ichi2.anki.browser.BrowserMultiColumnAdapter.Companion.LINES_VISIBLE_WHEN_COLLAPSED
import com.ichi2.anki.browser.CardBrowserColumn
import com.ichi2.anki.browser.CardBrowserColumn.DECK
import com.ichi2.anki.browser.CardBrowserColumn.QUESTION
import com.ichi2.anki.browser.CardBrowserColumn.SFLD
import com.ichi2.anki.browser.CardBrowserColumn.TAGS
import com.ichi2.anki.browser.CardBrowserViewModel
import com.ichi2.anki.browser.CardBrowserViewModelTest
import com.ichi2.anki.browser.CardOrNoteId
import com.ichi2.anki.browser.FindAndReplaceDialogFragment
import com.ichi2.anki.browser.FindAndReplaceDialogFragment.Companion.ALL_FIELDS_AS_FIELD
import com.ichi2.anki.browser.FindAndReplaceDialogFragment.Companion.ARG_FIELD
import com.ichi2.anki.browser.FindAndReplaceDialogFragment.Companion.ARG_MATCH_CASE
import com.ichi2.anki.browser.FindAndReplaceDialogFragment.Companion.ARG_ONLY_SELECTED_NOTES
import com.ichi2.anki.browser.FindAndReplaceDialogFragment.Companion.ARG_REGEX
import com.ichi2.anki.browser.FindAndReplaceDialogFragment.Companion.ARG_REPLACEMENT
import com.ichi2.anki.browser.FindAndReplaceDialogFragment.Companion.ARG_SEARCH
import com.ichi2.anki.browser.FindAndReplaceDialogFragment.Companion.REQUEST_FIND_AND_REPLACE
import com.ichi2.anki.browser.FindAndReplaceDialogFragment.Companion.TAGS_AS_FIELD
import com.ichi2.anki.browser.column1
import com.ichi2.anki.browser.selectRowAtPosition
import com.ichi2.anki.browser.setColumn
import com.ichi2.anki.browser.setSelectedDeck
import com.ichi2.anki.browser.toRowSelection
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.common.utils.isRunningAsUnitTest
import com.ichi2.anki.libanki.BrowserConfig
import com.ichi2.anki.libanki.CardId
import com.ichi2.anki.libanki.CardType
import com.ichi2.anki.libanki.DeckNameId
import com.ichi2.anki.libanki.Note
import com.ichi2.anki.libanki.NotetypeJson
import com.ichi2.anki.libanki.QueueType
import com.ichi2.anki.libanki.testutils.AnkiTest
import com.ichi2.anki.model.CardsOrNotes.CARDS
import com.ichi2.anki.model.CardsOrNotes.NOTES
import com.ichi2.anki.model.LegacySortType
import com.ichi2.anki.model.SelectableDeck
import com.ichi2.anki.scheduling.ForgetCardsDialog
import com.ichi2.anki.servicelayer.PreferenceUpgradeService
import com.ichi2.anki.servicelayer.PreferenceUpgradeService.PreferenceUpgrade.UpgradeBrowserColumns.Companion.LEGACY_COLUMN1_KEYS
import com.ichi2.anki.servicelayer.PreferenceUpgradeService.PreferenceUpgrade.UpgradeBrowserColumns.Companion.LEGACY_COLUMN2_KEYS
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.ui.internationalization.toSentenceCase
import com.ichi2.anki.utils.ext.getCurrentDialogFragment
import com.ichi2.anki.utils.ext.showDialogFragment
import com.ichi2.testutils.IntentAssert
import com.ichi2.testutils.common.Flaky
import com.ichi2.testutils.common.OS
import com.ichi2.testutils.ext.menu
import com.ichi2.testutils.getSharedPrefs
import com.ichi2.testutils.withSplitPaneUiAsync
import com.ichi2.utils.LanguageUtil
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.greaterThan
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.nullValue
import org.hamcrest.Matchers.startsWith
import org.junit.Assume.assumeThat
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Random
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.fail

@RunWith(AndroidJUnit4::class)
class CardBrowserTest : RobolectricTest() {
    @Before
    override fun setUp() {
        super.setUp()
        setCardBrowserFragmented(false)
        setUsingSearchView(false)
    }

    @Test
    fun browserIsNotInitiallyInMultiSelectModeWithNoCards() =
        runTest {
            val browser = browserWithNoNewCards
            assertThat(browser.viewModel.isInMultiSelectMode, equalTo(false))
        }

    @Test
    fun browserIsNotInitiallyInMultiSelectModeWithCards() =
        runTest {
            val browser = browserWithMultipleNotes
            assertThat(browser.viewModel.isInMultiSelectMode, equalTo(false))
        }

    @Test
    fun selectAllIsNotVisibleWhenNoCardsInDeck() =
        runTest {
            val browser = browserWithNoNewCards
            assertThat(browser.isShowingSelectAll, equalTo(false))
        }

    @Test
    fun selectAllIsVisibleWhenCardsInDeck() =
        runTest {
            val browser = browserWithMultipleNotes
            assertThat(browser.viewModel.rowCount, greaterThan(0))
            assertThat(browser.isShowingSelectAll, equalTo(true))
        }

    @Test
    fun selectAllIsNotVisibleOnceCalled() =
        runTest {
            val browser = browserWithMultipleNotes
            selectMenuItem(browser, R.id.action_select_all)
            assertThat(browser.isShowingSelectAll, equalTo(false))
        }

    @Test
    fun testOnDeckSelected() =
        withBrowser(noteCount = 1) {
            // Arrange
            val deckId = 123L
            val selectableDeck = SelectableDeck.Deck(deckId, "Test Deck")

            // Act
            this.onDeckSelected(selectableDeck)

            // Assert
            assertEquals(deckId, this.lastDeckId)

            // Act again: select the same deck
            this.onDeckSelected(selectableDeck)

            // Assert again: the deck selection should not change
            assertEquals(deckId, this.lastDeckId)
        }

    @Test
    fun `can select deck with escaped name - issue 20279`() {
        val deckId = addDeck("test\\s")
        withBrowser(noteCount = 1) {
            val selectableDeck = SelectableDeck.Deck(deckId, "test\\s")

            this.onDeckSelected(selectableDeck)

            assertDoesNotThrow { advanceRobolectricLooper() }
        }
    }

    @Test
    @Flaky(os = OS.WINDOWS, "Index 0 out of bounds for length 0")
    fun browserIsInMultiSelectModeWhenSelectingOne() {
        val browser = browserWithMultipleNotes
        selectOneOfManyCards(browser)
        assertThat(browser.viewModel.isInMultiSelectMode, equalTo(true))
    }

    @Test
    fun browserDoesNotFailWhenSelectingANonExistingCard() =
        runTest {
            // #5900
            val browser = getBrowserWithNotes(6)
            // Sometimes an async operation deletes a card, we clear the data and rerender it to simulate this
            deleteCardAtPosition(browser, 0)
            browser.rerenderAllCards()
            assertThat("the row stays visible", browser.viewModel.rowCount, equalTo(6))
            assertThat(
                "the row is displayed as deleted",
                browser
                    .getVisibleRows()
                    .first()
                    .columnViews[0]
                    .text,
                equalTo("(deleted)"),
            )
        }

    @Test
    @Ignore("Not yet implemented, feature has performance implications in large collections, instead we remove selections")
    fun selectionsAreCorrectWhenNonExistingCardIsRemoved() =
        runTest {
            val browser = getBrowserWithNotes(7)
            browser.selectRowsWithPositions(1, 3, 5, 6)
            deleteCardAtPosition(browser, 2) // delete non-selected
            deleteCardAtPosition(browser, 3) // delete selected, ensure it's not still selected

            // ACT
            browser.rerenderAllCards()
            // ASSERT
            assertThat(browser.viewModel.rowCount, equalTo(6L))
            assertThat(
                "A checked card should have been removed",
                browser.viewModel.selectedRowCount(),
                equalTo(3),
            )
            assertThat(
                "Checked card before should not have changed",
                browser.hasSelectedCardAtPosition(1),
                equalTo(true),
            )
            assertThat(
                "Checked card after should have changed by 2 places",
                browser.hasSelectedCardAtPosition(3),
                equalTo(true),
            )
            assertThat(
                "Checked card after should have changed by 2 places",
                browser.hasSelectedCardAtPosition(4),
                equalTo(true),
            )
        }

    @Test
    fun canChangeDeckToRegularDeck() =
        runTest {
            addDeck("Hello")
            val b = getBrowserWithNotes(5)

            val decks = b.validDecksForChangeDeck

            for (d in decks) {
                if (d.name == "Hello") {
                    return@runTest
                }
            }
            fail("Added deck was not found in the Card Browser")
        }

    @Test
    fun cannotChangeDeckToDynamicDeck() =
        runTest {
            // 5932 - dynamic decks are meant to have cards added to them through "Rebuild".
            addDynamicDeck("World")
            val b = getBrowserWithNotes(5)

            val decks = b.validDecksForChangeDeck

            if (decks.any { it.name == "World" }) {
                fail("Dynamic decks should not be transferred to by the browser.")
            }
        }

    @Test
    fun changeDeckIntegrationTestDynamicAndNon() =
        runTest {
            addDeck("Hello")
            addDynamicDeck("World")

            val validNames = hashSetOf("Default", "Hello")

            val b = getBrowserWithNotes(5)

            val decks = b.validDecksForChangeDeck
            for (d in decks) {
                assertThat(validNames, hasItem(d.name))
            }
            assertThat("Additional unexpected decks were present", decks.size, equalTo(2))
        }

    @Test
    fun moveToNonDynamicDeckWorks() =
        runTest {
            addDeck("Foo")
            addDynamicDeck("Bar")
            val deckIdToChangeTo = addDeck("Hello")
            addDeck("ZZ")
            selectDefaultDeck()
            val b = getBrowserWithNotes(5)
            b.selectRowsWithPositions(0, 2)

            val cardIds = b.viewModel.queryAllSelectedCardIds()

            for (cardId in cardIds) {
                assertThat(
                    "Deck should have been changed yet",
                    col.getCard(cardId).did,
                    not(deckIdToChangeTo),
                )
            }

            // act
            assertDoesNotThrow { b.cardBrowserFragment.moveSelectedCardsToDeck(deckIdToChangeTo) }

            // assert
            for (cardId in cardIds) {
                assertThat("Deck should be changed", col.getCard(cardId).did, equalTo(deckIdToChangeTo))
            }
        }

    @Test
    fun `change deck does not work for dynamic decks`() =
        runTest {
            throwOnShowError = false

            val dynId = addDynamicDeck("World")
            selectDefaultDeck()
            val b = getBrowserWithNotes(5)
            b.selectRowsWithPositions(0, 2)

            val cardIds = b.viewModel.queryAllSelectedCardIds()

            b.cardBrowserFragment.moveSelectedCardsToDeck(dynId).join()

            for (cardId in cardIds) {
                assertThat("Deck should not be changed", col.getCard(cardId).did, not(dynId))
            }
        }

    @Test // see #13391
    fun newlyCreatedDeckIsShownAsOptionInBrowser() =
        runTest {
            val deckOneId = addDeck("one")
            val browser = browserWithNoNewCards
            assertEquals(2, browser.validDecksForChangeDeck.size) // 1 added + default deck
            assertEquals(1, browser.validDecksForChangeDeck.count { it.deckId == deckOneId })
            val deckTwoId = addDeck("two")
            assertEquals(3, browser.validDecksForChangeDeck.size) // 2 added + default deck
            assertEquals(1, browser.validDecksForChangeDeck.count { it.deckId == deckOneId })
            assertEquals(1, browser.validDecksForChangeDeck.count { it.deckId == deckTwoId })
        }

    @Test
    @Ignore("Fails locally as well on macOS, not just CI. Blocks testing.")
    @Flaky(os = OS.ALL, message = "Fails mostly on Mac and occasionally Windows")
    fun flagsAreShownInBigDecksTest() =
        runTest {
            val numberOfNotes = 75
            val cardBrowser = getBrowserWithNotes(numberOfNotes)

            // select a random card
            val random = Random(1)
            val cardPosition = random.nextInt(numberOfNotes)
            assumeThat("card position to select is 60", cardPosition, equalTo(60))
            cardBrowser.selectRowsWithPositions(cardPosition)
            assumeTrue(
                "card at position 60 is selected",
                cardBrowser.hasSelectedCardAtPosition(cardPosition),
            )

            // flag the selected card
            cardBrowser.cardBrowserFragment.updateFlagForSelectedRows(Flag.RED)
            // check if card is red
            assertThat(
                "Card should be flagged",
                getCheckedCard(cardBrowser).color,
                equalTo(Color.COLOR_FLAG_RED),
            )

            // unflag the selected card
            cardBrowser.cardBrowserFragment.updateFlagForSelectedRows(Flag.NONE)
            // check if card flag is removed
            assertThat(
                "Card flag should be removed",
                getCheckedCard(cardBrowser).color,
                equalTo(Color.COLOR_DEFAULT),
            )

            // deselect and select all cards
            cardBrowser.viewModel.selectNone()
            cardBrowser.viewModel.selectAll()
            // flag all the cards as Green
            cardBrowser.cardBrowserFragment.updateFlagForSelectedRows(Flag.GREEN)
            // check if all card flags turned green
            assertThat(
                "All cards should be flagged",
                cardBrowser.viewModel
                    .queryAllCardIds()
                    .all { cardId -> getCardFlagAfterFlagChangeDone(cardId) == Flag.GREEN },
            )
        }

    @Test
    fun flagValueIsShownOnCard() {
        val n = addBasicNote("1", "back")
        flagCardForNote(n, Flag.RED)

        val cardId = n.cids()[0]

        col.backend.setActiveBrowserColumns(listOf("cardDue")) // arbitrary value
        val actualFlag = getCardFlagAfterFlagChangeDone(cardId)

        assertThat("The card flag value should be reflected in the UI", actualFlag, equalTo(Flag.RED))
    }

    private fun getCardFlagAfterFlagChangeDone(cardId: CardId): Flag {
        val data = col.browserRowForId(cardId)

        return when (data.color) {
            Color.COLOR_FLAG_BLUE -> Flag.BLUE
            Color.COLOR_FLAG_RED -> Flag.RED
            Color.COLOR_FLAG_GREEN -> Flag.GREEN
            Color.COLOR_DEFAULT -> throw IllegalStateException("no flag selected")
            else -> TODO("unhandled: ${data.color}")
        }
    }

    @Test
    fun startupFromCardBrowserActionItemShouldEndActivityIfNoPermissions() {
        try {
            mockkStatic(::isRunningAsUnitTest)
            mockkObject(IntentHandler)

            every { grantedStoragePermissions(any(), any()) } returns false
            every { isRunningAsUnitTest } returns false

            val browserController = Robolectric.buildActivity(CardBrowser::class.java).create()
            val cardBrowser = browserController.get()
            saveControllerForCleanup(browserController)

            assertThat("Activity should be finishing", cardBrowser.isFinishing)
        } finally {
            unmockkStatic(::isRunningAsUnitTest)
            unmockkObject(IntentHandler)
        }
    }

    @Test
    fun tagWithBracketsDisplaysProperly() =
        runTest {
            val n = addBasicNote("Hello", "World")
            n.addTag("sketchy::(1)")
            n.flush()

            val b = browserWithNoNewCards
            b.filterByTagSync("sketchy::(1)")

            assertThat("tagged card should be returned", b.viewModel.rowCount, equalTo(1))
        }

    @Test
    @Flaky(os = OS.WINDOWS, "IllegalStateException: Card '1596783600440' not found")
    fun previewWorksAfterSort() =
        runTest {
            // #7286
            val cid1 = addBasicNote("Hello", "World").cards()[0].id
            val cid2 = addBasicNote("Hello2", "World2").cards()[0].id

            val b = browserWithNoNewCards

            b.selectRowsWithPositions(0)
            val previewIntent = b.viewModel.queryPreviewIntentData()
            assertThat("before: index", previewIntent.currentIndex, equalTo(0))
            assertThat(
                "before: cards",
                previewIntent.idsFile.getIds(),
                equalTo(listOf(cid1, cid2)),
            )

            // reverse
            b.viewModel.changeCardOrder(LegacySortType.SORT_FIELD)

            b.replaceSelectionWith(intArrayOf(0))
            val intentAfterReverse = b.viewModel.queryPreviewIntentData()
            assertThat("after: index", intentAfterReverse.currentIndex, equalTo(0))
            assertThat(
                "after: cards",
                intentAfterReverse.idsFile.getIds(),
                equalTo(listOf(cid2, cid1)),
            )
        }

    /** 7420  */
    @Test
    fun addCardDeckISetIfDeckIsSelected() =
        runTest {
            val targetDid = addDeck("NotDefault")

            val b = browserWithNoNewCards

            assertThat(
                "The target deck should not yet be selected",
                b.lastDeckId,
                not(equalTo(targetDid)),
            )

            b.viewModel.setSelectedDeck(targetDid)

            assertThat("The target deck should be selected", b.lastDeckId, equalTo(targetDid))

            val addIntent = b.addNoteLauncher.toIntent(targetContext)
            val bundle = addIntent.getBundleExtra(NoteEditorActivity.FRAGMENT_ARGS_EXTRA)
            IntentAssert.hasExtra(bundle, NoteEditorFragment.EXTRA_DID, targetDid)
        }

    /** 7420  */
    @Test
    fun addCardDeckISetIfDeckIsSelectedOnOpen() {
        val initialDid = addDeck("NotDefault", setAsSelected = true)

        val b = browserWithNoNewCards

        assertThat("The initial deck should be selected", b.lastDeckId, equalTo(initialDid))

        val addIntent = b.addNoteLauncher.toIntent(targetContext)
        val bundle = addIntent.getBundleExtra(NoteEditorActivity.FRAGMENT_ARGS_EXTRA)
        IntentAssert.hasExtra(bundle, NoteEditorFragment.EXTRA_DID, initialDid)
    }

    @Test
    fun repositionDataTest() =
        runTest {
            val b = getBrowserWithNotes(1)

            b.selectRowsWithPositions(0)

            val card = getCheckedCard(b)

            assertThat(
                "Initial position of checked card",
                card.getColumnHeaderText(CardBrowserColumn.DUE),
                startsWith("New #\u20681\u2069"),
            )

            b.viewModel.repositionSelectedRows(2, 1, shuffle = false, shift = false)

            assertThat(
                "Position of checked card after reposition",
                card.getColumnHeaderText(CardBrowserColumn.DUE),
                equalTo("New #\u20682\u2069"),
            )
        }

    @Test
    fun `reposition without shift has expected outcome`() =
        runTest {
            val browser = getBrowserWithNotes(5)
            // without shifting when repositioning we end up with the same due if there's already a
            // card with that value
            // see https://docs.ankiweb.net/browsing.html#cards
            browser.assertRepositionOutcomeFor(
                selectedBrowserPosition = 3,
                currentDuePosition = 4,
                targetDuePosition = 1,
                expectedCardsDuePositions = listOf(1, 2, 3, 1, 5),
                shift = false,
            )
            browser.viewModel.selectNone()
            // same due because we don't shift
            browser.assertRepositionOutcomeFor(
                selectedBrowserPosition = 4,
                currentDuePosition = 5,
                targetDuePosition = 1,
                expectedCardsDuePositions = listOf(1, 2, 3, 1, 1),
                shift = false,
            )
        }

    @Test
    fun `reposition with shift has expected outcome`() =
        runTest {
            val browser = getBrowserWithNotes(5)
            browser.assertRepositionOutcomeFor(
                selectedBrowserPosition = 4,
                currentDuePosition = 5,
                targetDuePosition = 2,
                expectedCardsDuePositions = listOf(1, 3, 4, 5, 2),
                shift = true,
            )
            browser.viewModel.selectNone()
            // dues are shifted for new repositioning with shift
            browser.assertRepositionOutcomeFor(
                selectedBrowserPosition = 3,
                currentDuePosition = 5,
                targetDuePosition = 1,
                expectedCardsDuePositions = listOf(2, 4, 5, 1, 3),
                shift = true,
            )
        }

    private suspend fun CardBrowser.assertRepositionOutcomeFor(
        selectedBrowserPosition: Int,
        currentDuePosition: Int,
        targetDuePosition: Int,
        expectedCardsDuePositions: List<Int>,
        shift: Boolean,
    ) {
        selectRowsWithPositions(selectedBrowserPosition)
        val card = getCheckedCard(this)

        assertThat(
            "Unexpected due for currently selected card",
            card.getColumnHeaderText(CardBrowserColumn.DUE),
            startsWith("New #\u2068$currentDuePosition\u2069"),
        )

        viewModel.repositionSelectedRows(targetDuePosition, 1, shuffle = false, shift = shift)
        // with shift, moving to a position will not result in the same due as the cards with
        // that position will be shifted
        viewModel.cards.forEachIndexed { index, entry ->
            assertThat(
                "Unexpected due position at index: $index",
                getDueHeaderText(entry.cardOrNoteId),
                startsWith("New #\u2068${expectedCardsDuePositions[index]}\u2069"),
            )
        }
    }

    private fun getDueHeaderText(cardOrNoteId: Long): String? {
        // There's currently a minimum of 2 columns
        col.backend.setActiveBrowserColumns(listOf(CardBrowserColumn.DUE.ankiColumnKey, "answer"))
        return col
            .browserRowForId(cardOrNoteId)
            .getCells(0)
            .text
    }

    @Test
    @Config(qualifiers = "en")
    @SuppressLint("DirectCalendarInstanceUsage")
    fun resetDataTest() =
        runTest {
            TimeManager.reset()
            addBasicNote("Hello", "World").firstCard().update {
                due = 5
                queue = QueueType.Rev
                type = CardType.Rev
            }
            val cal = Calendar.getInstance()
            cal.add(Calendar.DATE, 5)
            val expectedDate = SimpleDateFormat("yyyy-MM-dd").format(Date(cal.timeInMillis))

            val b = browserWithNoNewCards
            b.selectRowsWithPositions(0)

            val card = getCheckedCard(b)

            assertThat(
                "Initial due of checked card",
                card.getColumnHeaderText(CardBrowserColumn.DUE),
                equalTo(expectedDate),
            )

            // simulate the user using the ForgetCardsDialog to start the cards reset process
            b.supportFragmentManager.setFragmentResult(
                ForgetCardsDialog.REQUEST_KEY_FORGET,
                bundleOf(
                    ForgetCardsDialog.ARG_RESTORE_ORIGINAL to true,
                    ForgetCardsDialog.ARG_RESET_REPETITION to false,
                ),
            )

            assertThat(
                "Position of checked card after reset",
                card.getColumnHeaderText(CardBrowserColumn.DUE),
                equalTo("New #\u20682\u2069"),
            )
        }

    @Test
    @Ignore("Doesn't work - but should")
    fun dataUpdatesAfterUndoReposition() =
        runTest {
            val b = getBrowserWithNotes(1)

            b.selectRowsWithPositions(0)

            val card = getCheckedCard(b)

            assertThat(
                "Initial position of checked card",
                card.getColumnHeaderText(CardBrowserColumn.DUE),
                equalTo("1"),
            )

            b.cardBrowserFragment.repositionCardsNoValidation(
                2,
                1,
                shuffle = false,
                shift = false,
            )

            assertThat(
                "Position of checked card after reposition",
                card.getColumnHeaderText(CardBrowserColumn.DUE),
                equalTo("2"),
            )

            b.onUndo()

            assertThat(
                "Position of checked card after undo should be reset",
                card.getColumnHeaderText(CardBrowserColumn.DUE),
                equalTo("1"),
            )
        }

    @Test
    fun change_deck_dialog_is_dismissed_on_activity_recreation() {
        val cardBrowser = browserWithNoNewCards

        val dialog = cardBrowser.cardBrowserFragment.getChangeDeckDialog(listOf())
        cardBrowser.showDialogFragment(dialog)

        val shownDialog: Fragment? = cardBrowser.getCurrentDialogFragment()
        assertNotNull(shownDialog)

        ActivityCompat.recreate(cardBrowser)
        advanceRobolectricLooper()
        val dialogAfterRecreate: Fragment? = cardBrowser.getCurrentDialogFragment()
        assertNull(dialogAfterRecreate)
    }

    /** 8027  */
    @Test
    fun checkSearchString() =
        runTest {
            addBasicNote("Hello", "John")
            addBasicNote("New", "world").firstCard().update {
                did = addDeck("Deck 1", setAsSelected = true)
            }

            val cardBrowser = browserWithNoNewCards
            cardBrowser.searchCardsSync("world or hello")

            assertThat(
                "Cardbrowser has Deck 1 as selected deck",
                cardBrowser.selectedDeckNameForUi,
                equalTo("Deck 1"),
            )
            assertThat(
                "Results should only be from the selected deck",
                cardBrowser.viewModel.rowCount,
                equalTo(1),
            )
        }

    /** PR #8553  */
    @Test
    fun checkDisplayOrderPersistence() {
        // Start the Card Browser with Basic Note Type
        ensureCollectionLoadIsSynchronous()
        var cardBrowserController =
            Robolectric
                .buildActivity(CardBrowser::class.java, Intent())
                .create()
                .start()
                .resume()
                .visible()
        saveControllerForCleanup(cardBrowserController)

        // Make sure card has default value in sortType field
        assertThat(
            "Initially Card Browser has order = noteFld",
            col.config.get<String>("sortType"),
            equalTo("noteFld"),
        )

        // Change the display order of the card browser
        cardBrowserController.get().viewModel.changeCardOrder(LegacySortType.EASE)

        // Kill and restart the activity and ensure that display order is preserved
        val outBundle = Bundle()
        cardBrowserController.saveInstanceState(outBundle)
        cardBrowserController.pause().stop()
        // fix Robolectric bug with launchCollectionInLifecycleScope
        // method running after onStart without context
        advanceRobolectricLooper()
        cardBrowserController.destroy()
        cardBrowserController =
            Robolectric
                .buildActivity(CardBrowser::class.java)
                .create(outBundle)
                .start()
                .resume()
                .visible()
        saveControllerForCleanup(cardBrowserController)

        // Find the current (after database has been changed) Mod time

        val updatedMod = col.mod
        assertThat(
            "Card Browser has the new sortType field",
            col.config.get<String>("sortType"),
            equalTo("cardEase"),
        )
        assertNotEquals(0, updatedMod)
    }

    @Test
    fun checkIfLongSelectChecksAllCardsInBetween() =
        runTest {
            // #8467 - selecting cards outside the view pane (20) caused a crash as we were using view-based positions
            val browser = getBrowserWithNotes(25)
            selectOneOfManyCards(browser, 7) // HACK: Fix a bug in tests by choosing a value < 8
            selectOneOfManyCards(browser, 24)
            assertThat(browser.viewModel.selectedRowCount(), equalTo(18))
        }

    @Test
    fun checkIfSearchAllDecksWorks() =
        runTest {
            addBasicNote("Hello", "World")
            addBasicNote("Front", "Back").firstCard().update {
                did = addDeck("Test Deck", setAsSelected = true)
            }

            val cardBrowser = browserWithNoNewCards
            cardBrowser.searchCards("Hello")
            assertThat(
                "Card browser should have Test Deck as the selected deck",
                cardBrowser.selectedDeckNameForUi,
                equalTo("Test Deck"),
            )
            assertThat("Result should be empty", cardBrowser.viewModel.rowCount, equalTo(0))

            advanceRobolectricLooper()
            cardBrowser.searchAllDecks()
            advanceRobolectricLooper()
            assertThat("Result should contain one card", cardBrowser.viewModel.rowCount, equalTo(1))
        }

    @Test
    fun `'notes-only mode' returns one card from each note`() =
        runTest {
            // #14623: The functionality was broken
            addBasicAndReversedNote("Hello", "World")
            addBasicAndReversedNote("Hello", "Anki")

            browserWithNoNewCards.apply {
                searchAllDecks()
                advanceRobolectricLooper()
                with(viewModel) {
                    assertThat("Result should contain 4 cards", rowCount, equalTo(4))
                    setCardsOrNotes(NOTES).join()
                    advanceRobolectricLooper()
                    assertThat("Result should contain 2 cards (one per note)", rowCount, equalTo(2))
                }
            }
        }

    /** PR #14859  */
    @Test
    fun checkDisplayOrderAfterTogglingCardsToNotes() =
        withBrowser {
            viewModel.changeCardOrder(LegacySortType.EASE) // order no. 7 corresponds to "cardEase"

            viewModel.changeCardOrder(LegacySortType.EASE) // reverse the list

            viewModel.setCardsOrNotes(NOTES)
            searchCards()

            assertThat(
                "Card Browser has the new noteSortType field",
                col.config.get<String>("noteSortType"),
                equalTo("cardEase"),
            )
            assertThat(
                "Card Browser has the new browserNoteSortBackwards field",
                col.config.get<Boolean>("browserNoteSortBackwards"),
                equalTo(true),
            )
        }

    data class CheckedCardResult(
        val row: BrowserRow,
        val id: CardOrNoteId,
    ) {
        val color: Color get() = row.color
        val cardOrNoteId = id.cardOrNoteId
    }

    private fun getCheckedCard(b: CardBrowser): CheckedCardResult {
        assertThat(
            "only one card expected to be checked",
            b.viewModel.selectedRowCount(),
            equalTo(1),
        )
        val id = b.viewModel.selectedRows.single()
        val row = CollectionManager.getBackend().browserRowForId(id.cardOrNoteId)
        return CheckedCardResult(row, id)
    }

    private suspend fun deleteCardAtPosition(
        browser: CardBrowser,
        positionToCorrupt: Int,
    ) {
        val cid = browser.viewModel.queryCardIdAtPosition(positionToCorrupt)
        col.removeNotes(cardIds = listOf(cid))
    }

    private fun selectOneOfManyCards(
        browser: CardBrowser,
        position: Int = 0,
    ) {
        Timber.d("Selecting single card")
        browser.longClickRowAtPosition(position)
    }

    private fun selectMenuItem(
        browser: CardBrowser,
        actionSelectAll: Int,
    ) {
        Timber.d("Selecting menu item")
        // select seems to run an infinite loop :/
        val shadowActivity = shadowOf(browser)
        shadowActivity.clickMenuItem(actionSelectAll)
        advanceRobolectricLooper()
    }

    /** Returns an instance of [CardBrowser] containing [noteCount] notes */
    private fun getBrowserWithNotes(
        noteCount: Int,
        reversed: Boolean = false,
    ): CardBrowser {
        ensureCollectionLoadIsSynchronous()
        if (reversed) {
            for (i in 0 until noteCount) {
                addBasicAndReversedNote(i.toString(), "back")
            }
        } else {
            for (i in 0 until noteCount) {
                addBasicNote(i.toString(), "back")
            }
        }
        return super.startRegularActivity<CardBrowser>(Intent()).also {
            advanceRobolectricLooper() // may be a fix for flaky tests
        }
    }

    private val browserWithNoNewCards: CardBrowser
        get() = getBrowserWithNotes(0)

    private val browserWithMultipleNotes: CardBrowser
        get() = getBrowserWithNotes(3)

    @Test
    fun truncateAndExpand() =
        runTest {
            val cardBrowser = getBrowserWithNotes(3)
            cardBrowser.viewModel.setTruncated(true)
            advanceRobolectricLooper()

            // Testing whether each card is truncated and ellipsized
            for (row in cardBrowser.getVisibleRows()) {
                val column1 = row.columnViews[0]
                val column2 = row.columnViews[1]

                // Testing truncation
                assertThat("col 1 max lines", column1.maxLines, equalTo(LINES_VISIBLE_WHEN_COLLAPSED))
                assertThat("col 2 max lines", column2.maxLines, equalTo(LINES_VISIBLE_WHEN_COLLAPSED))

                // Testing ellipses
                assertThat("column 1 ellipses", column1.ellipsize, equalTo(TextUtils.TruncateAt.END))
                assertThat("column 2 ellipses", column2.ellipsize, equalTo(TextUtils.TruncateAt.END))
            }

            cardBrowser.viewModel.setTruncated(false)
            advanceRobolectricLooper()

            // Testing whether each card is expanded and not ellipsized
            for (row in cardBrowser.getVisibleRows()) {
                val column1 = row.columnViews[0]
                val column2 = row.columnViews[1]

                // Testing expansion
                assertThat("column 1 max lines", column1.maxLines, equalTo(Integer.MAX_VALUE))
                assertThat("column 2 max lines", column2.maxLines, equalTo(Integer.MAX_VALUE))

                // Testing not ellipsized
                assertThat("column 1 ellipsize", column1.ellipsize, nullValue())
                assertThat("column 2 ellipsize", column2.ellipsize, nullValue())
            }
        }

    @Test
    @Ignore("flaky")
    fun checkCardsNotesMode() =
        runTest {
            val cardBrowser = getBrowserWithNotes(3, true)

            cardBrowser.viewModel.setCardsOrNotes(CARDS)
            cardBrowser.searchCards()

            advanceRobolectricLooper()
            // check if we get both cards of each note
            assertThat(cardBrowser.viewModel.rowCount, equalTo(6))

            cardBrowser.viewModel.setCardsOrNotes(NOTES)
            cardBrowser.searchCards()

            // check if we get one card per note
            advanceRobolectricLooper()
            assertThat(cardBrowser.viewModel.rowCount, equalTo(3))
        }

    @Test
    fun `column spinner positions are set if no preferences exist`() =
        runBlocking {
            // GIVEN: No shared preferences exist for display column selections
            getSharedPrefs().edit {
                remove("cardBrowserColumn1")
                remove("cardBrowserColumn2")
                remove(BrowserConfig.ACTIVE_CARD_COLUMNS_KEY)
                remove(BrowserConfig.ACTIVE_NOTE_COLUMNS_KEY)
            }

            // WHEN: CardBrowser is created
            val cardBrowser: CardBrowser = getBrowserWithNotes(5)

            // THEN: Display column selections should default to position 0
            assertThat(cardBrowser.columnHeadings[0], equalTo("Sort Field"))
            assertThat(cardBrowser.columnHeadings[1], equalTo("Card Type"))
        }

    @Test
    fun `column spinner positions are initially set from existing preferences`() =
        runTest {
            // GIVEN: Shared preferences exists for display column selections
            getSharedPrefs().edit {
                putString(BrowserConfig.ACTIVE_CARD_COLUMNS_KEY, "question|cardEase")
            }

            // WHEN: CardBrowser is created
            val cardBrowser: CardBrowser = getBrowserWithNotes(7)

            // THEN: The display column selections should match the shared preferences values
            assertThat(cardBrowser.columnHeadings[0], equalTo("Question"))
            assertThat(cardBrowser.columnHeadings[1], equalTo("Ease"))
        }

    @Test
    fun `column spinner positions are upgraded`() =
        runTest {
            // GIVEN: Shared preferences exists for display column selections

            // using legacy keys - test of PreferenceUpgradeService
            getSharedPrefs().edit {
                putInt("cardBrowserColumn1", 1)
                putInt("cardBrowserColumn2", 5)
            }

            // meta test
            assertThat(LEGACY_COLUMN1_KEYS[1], equalTo(SFLD))
            assertThat(LEGACY_COLUMN2_KEYS[5], equalTo(TAGS))

            PreferenceUpgradeService.upgradePreferences(getSharedPrefs(), 20300130)

            // WHEN: CardBrowser is created
            val cardBrowser: CardBrowser = getBrowserWithNotes(7)

            // THEN: The display column selections should match the shared preferences values
            assertThat(cardBrowser.columnHeadings[0], equalTo("Sort Field"))
            assertThat(cardBrowser.columnHeadings[1], equalTo("Tags"))

            assertThat("column 1 is cleared", !getSharedPrefs().all.containsKey("cardBrowserColumn1"))
            assertThat("column 2 is cleared", !getSharedPrefs().all.containsKey("cardBrowserColumn2"))
        }

    @Test
    fun `loading corrupt columns returns default`() {
        // GIVEN: Shared preferences exists for display column selections
        // with a corrupt value
        getSharedPrefs().edit {
            putString(BrowserConfig.ACTIVE_CARD_COLUMNS_KEY, "question|corrupt")
        }

        // WHEN: CardBrowser is created
        val cardBrowser: CardBrowser = getBrowserWithNotes(7)

        // In future, we may want to keep the 'question' value and only reset
        // the corrupt column.
        assertThat("column 1 reset to default", cardBrowser.columnHeadings[0], equalTo("Sort Field"))
        assertThat("column 2 reset to default", cardBrowser.columnHeadings[1], equalTo("Card Type"))
    }

    @Test
    fun `loading duplicate columns returns distinct`() {
        // GIVEN: Shared preferences exists for display column selections
        // with duplicate values
        getSharedPrefs().edit {
            putString(BrowserConfig.ACTIVE_CARD_COLUMNS_KEY, "question|cardDue|cardDue|deck")
        }

        // WHEN: CardBrowser is created
        val cardBrowser: CardBrowser = getBrowserWithNotes(7)

        // THEN: CardBrowser only shows distinct columns.
        for ((i, column) in listOf("Question", "Due", "Deck").withIndex()) {
            assertThat(
                "column ${i + 1} reset to default",
                cardBrowser.columnHeadings[i],
                equalTo(column),
            )
        }
    }

    @Test
    @Ignore("issues with launchCollectionInLifecycleScope")
    fun `column titles update when moving to notes mode`() =
        withBrowser {
            viewModel.setColumn(0, CardBrowserColumn.INTERVAL)

            assertThat("spinner title: cards", columnHeadings[1], equalTo("Interval"))

            viewModel.setCardsOrNotes(NOTES)
            advanceRobolectricLooper()

            assertThat("spinner title: notes", columnHeadings[1], equalTo("Avg. Interval"))
        }

    @Test
    fun `tapping row toggles state - Issue 14952`() =
        runTest {
            // tapping the row was broken, checkbox was fine
            browserWithMultipleNotes.apply {
                longClickRowAtPosition(0)
                assertThat("select first row: long press", viewModel.selectedRowCount(), equalTo(1))
                clickRowAtPosition(1)
                assertThat("select row 2: tap", viewModel.selectedRowCount(), equalTo(2))
                clickRowAtPosition(0)
                assertThat("deselect row: tap", viewModel.selectedRowCount(), equalTo(1))
            }
        }

    @Test
    fun `selecting rows changes the toolbar title`() {
        val cardBrowser = getBrowserWithNotes(2)
        val toolbarTitle =
            assertNotNull(
                cardBrowser.findViewById<TextView>(R.id.toolbar_title),
                "toolbar title view should exist",
            )
        val locale = LanguageUtil.getLocaleCompat(cardBrowser.resources)

        assertEquals(View.GONE, toolbarTitle.visibility, "toolbar title should be hidden initially")

        cardBrowser.viewModel.selectRowAtPosition(0)
        advanceRobolectricLooper()
        assertEquals(View.VISIBLE, toolbarTitle.visibility, "toolbar title should be visible")
        assertEquals(String.format(locale, "%d", 1), toolbarTitle.text, "selecting one row should count 1")

        cardBrowser.viewModel.selectRowAtPosition(1)
        advanceRobolectricLooper()
        assertEquals(String.format(locale, "%d", 2), toolbarTitle.text, "selecting two rows should count 2")
    }

    @Test
    fun `deck id is remembered - issue 15072`() =
        runTest {
            // WARN: This doesn't mirror reality due to the use of coroutines
            // in the issue, selectDeckAndSave() was called AFTER the search had been performed
            // due to this being called immediately in a test-based context

            // We're going to move this functionality entirely to the ViewModel over the next few weeks
            // so this test should be updated and working after the refactorings are completed
            addBasicNote().moveToDeck("First")
            addBasicNote().moveToDeck("Second")

            val secondDeckId = requireNotNull(col.decks.idForName("Second"))

            browserWithNoNewCards.apply {
                viewModel.setSelectedDeck(secondDeckId)
                assertThat(viewModel.deckId, equalTo(secondDeckId))
                finish()
            }

            browserWithNoNewCards.apply {
                assertThat("deckId is remembered", viewModel.deckId, equalTo(secondDeckId))
                assertThat("deckId is searched", viewModel.rowCount, equalTo(1))
            }
        }

    @Test
    fun `tts tags are parsed`() {
        addStandardNoteType(
            "test",
            arrayOf("Front", "Back"),
            "[anki:tts lang=de_DE voices=com.google.android.tts-de-DE-language]{{Front}}[/anki:tts]",
            "",
        ).let { name ->
            col.notetypes.byName(name)!!
        }.addNote("Test", "Blank")

        // PERF: the browser is not necessary
        val card =
            browserWithNoNewCards.let {
                it.viewModel.selectRowAtPosition(0)
                getCheckedCard(it)
            }
        val question = card.getColumnHeaderText(QUESTION)

        assertThat(question, equalTo(CardBrowserViewModelTest.EXPECTED_TTS))
    }

    @Test
    @Ignore("temporarily broken")
    fun `initial value is correct column`() {
        // Column 1 is [QUESTION, SFLD], the values when [SFLD] is selected

        addBasicAndReversedNote("Hello", "World")

        withBrowser {
            assertThat(viewModel.column1, equalTo(SFLD))

            assertThat(column1Text(row = 0), equalTo("Hello"))
            assertThat(column1Text(row = 1), equalTo("Hello"))
        }
    }

    @Test
    @Ignore(
        "issues with launchCollectionInLifecycleScope - provided value is not current" +
            " use an integration test",
    )
    fun `column text is updated - cardsOrNotes and column change`() {
        addBasicAndReversedNote("Hello", "World")

        withBrowser {
            assertThat("cards: original column", columnHeadings[1], equalTo("Card Type"))

            viewModel.setColumn(1, DECK)
            assertThat("cards: changed column", columnHeadings[1], equalTo("Deck"))

            viewModel.setCardsOrNotes(NOTES)
            advanceRobolectricLooper()

            assertThat("notes: default column", columnHeadings[1], equalTo("Note Type"))
            viewModel.setColumn(1, DECK)
            assertThat("notes: changed column", columnHeadings[1], equalTo("Avg. Due"))

            viewModel.setCardsOrNotes(CARDS)
            assertThat("cards: updated column used", columnHeadings[1], equalTo("Deck"))
        }
    }

    @Test
    fun `FindReplace - dialog has expected ui at start`() {
        withBrowser {
            showFindAndReplaceDialog()
            // nothing selected so checkbox 'Only selected notes' is not available
            onView(withId(R.id.only_selected_notes_check_box)).inRoot(isDialog()).check(matches(isNotEnabled()))
            onView(withId(R.id.only_selected_notes_check_box)).inRoot(isDialog()).check(matches(isNotChecked()))
            val fieldSelectorAdapter = getFindReplaceFieldsAdapter()
            onView(withId(R.id.ignore_case_check_box)).inRoot(isDialog()).check(matches(isChecked()))
            onView(withId(R.id.input_as_regex_check_box)).inRoot(isDialog()).check(matches(isNotChecked()))
            // as nothing is selected the fields selector has only the two default options
            assertNotNull(fieldSelectorAdapter, "Fields adapter was not set")
            assertEquals(2, fieldSelectorAdapter.count)
            assertEquals(
                TR.browsingAllFields().toSentenceCase(targetContext, R.string.sentence_all_fields),
                fieldSelectorAdapter.getItem(0),
            )
            assertEquals(TR.editingTags(), fieldSelectorAdapter.getItem(1))
        }
    }

    @Test
    fun `FindReplace - shows expected fields target based on browser selection`() {
        fun SpinnerAdapter.getAdapterData(): List<String> =
            mutableListOf<String>().apply {
                for (position in 0 until count) {
                    add(getItem(position) as String)
                }
            }

        createFindReplaceTestNote("A", "Car", "Lion")
        createFindReplaceTestNote("B", "Train", "Chicken")
        withBrowser {
            assertEquals(2, viewModel.rowCount)
            viewModel.selectRowAtPosition(1)
            openFindAndReplace()
            var fieldSelectorAdapter = getFindReplaceFieldsAdapter()
            // 2 default field options + 2 fields from the one note selected
            assertEquals(4, fieldSelectorAdapter.count)
            val defaultFields =
                listOf(
                    TR.browsingAllFields().toSentenceCase(targetContext, R.string.sentence_all_fields),
                    TR.editingTags(),
                )
            assertEquals(defaultFields + listOf("Bfield0", "Bfield1"), fieldSelectorAdapter.getAdapterData())
            closeFindAndReplace()
            viewModel.selectAll() // 2 notes above
            openFindAndReplace()
            fieldSelectorAdapter = getFindReplaceFieldsAdapter()
            // 2 default field options + 4 from the two notes
            assertEquals(6, fieldSelectorAdapter.count)
            assertEquals(defaultFields + listOf("Afield0", "Afield1", "Bfield0", "Bfield1"), fieldSelectorAdapter.getAdapterData())
            closeFindAndReplace()
            viewModel.selectNone() // 2 notes above
            openFindAndReplace()
            fieldSelectorAdapter = getFindReplaceFieldsAdapter()
            // selection is reset just 2 default field options
            assertEquals(2, fieldSelectorAdapter.count)
            assertEquals(defaultFields, fieldSelectorAdapter.getAdapterData())
            closeFindAndReplace()
        }
    }

    @Test
    fun `FindReplace - dialog handles correctly match case checkbox set to true`() {
        val note0 = createFindReplaceTestNote("A", "kchicKen", "kilogram")
        val note1 = createFindReplaceTestNote("A", "keK", "kontra")
        withBrowser {
            assertEquals(2, viewModel.rowCount)
            viewModel.selectRowAtPosition(0)
            // by default 'match case' is set to false
            openFindAndReplace()
            onView(withId(R.id.input_search)).inRoot(isDialog()).perform(ViewActions.typeText("k"))
            onView(withId(R.id.input_replace)).inRoot(isDialog()).perform(ViewActions.typeText("X"))
            onView(withId(R.id.input_as_regex_check_box)).inRoot(isDialog()).perform(scrollCompletelyTo())
            onData(allOf(`is`(instanceOf(String::class.java)), `is`("Afield0")))
                .inAdapterView(withId(R.id.fields_selector))
                .perform(click())
            onView(withId(R.id.fields_selector)).check(matches(withSpinnerText(containsString("Afield0"))))
            onView(withId(R.id.ignore_case_check_box)).inRoot(isDialog()).check(matches(isChecked()))
            // although the positive button exists, clicking it with Espresso doesn't work
            //     onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
            // so simulate clicking the positive button by running the associated method directly
            (supportFragmentManager.findFragmentByTag(FindAndReplaceDialogFragment.TAG) as FindAndReplaceDialogFragment)
                .startFindReplace()
            // clicking the positive button would have also closed the dialog
            closeFindAndReplace()
            val colNote0 = withCol { getNote(note0.id) }
            val colNote1 = withCol { getNote(note1.id) }
            assertEquals("keK", colNote1.fields[0])
            assertEquals("kontra", colNote1.fields[1])
            // didn't modify field 1
            assertEquals("kilogram", colNote0.fields[1])
            // replaces both occurrences ignoring case
            assertEquals("XchicXen", colNote0.fields[0])
        }
    }

    @Test
    fun `FindReplace - dialog handles correctly match case checkbox set to false`() {
        val note0 = createFindReplaceTestNote("A", "kchicKen", "kilogram")
        val note1 = createFindReplaceTestNote("A", "keK", "kontra")
        withBrowser {
            assertEquals(2, viewModel.rowCount)
            viewModel.selectRowAtPosition(1)
            openFindAndReplace()
            onView(withId(R.id.input_search)).inRoot(isDialog()).perform(ViewActions.typeText("k"))
            onView(withId(R.id.input_replace)).inRoot(isDialog()).perform(ViewActions.typeText("X"))
            onView(withId(R.id.input_as_regex_check_box)).inRoot(isDialog()).perform(scrollCompletelyTo())
            onData(allOf(`is`(instanceOf(String::class.java)), `is`("Afield0")))
                .inAdapterView(withId(R.id.fields_selector))
                .perform(click())
            onView(withId(R.id.ignore_case_check_box)).inRoot(isDialog()).perform(scrollCompletelyTo())
            onView(withId(R.id.ignore_case_check_box)).inRoot(isDialog()).check(matches(isChecked()))
            onView(withId(R.id.ignore_case_check_box)).inRoot(isDialog()).perform(click())
            onView(withId(R.id.ignore_case_check_box)).inRoot(isDialog()).check(matches(isNotChecked()))
            // although the positive button exists, clicking it with Espresso doesn't work
            //     onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
            // so simulate clicking the positive button by running the associated method directly
            (supportFragmentManager.findFragmentByTag(FindAndReplaceDialogFragment.TAG) as FindAndReplaceDialogFragment)
                .startFindReplace()
            // clicking the positive button would have also closed the dialog
            closeFindAndReplace()
            val colNote0 = withCol { getNote(note0.id) }
            val colNote1 = withCol { getNote(note1.id) }
            assertEquals("kchicKen", colNote0.fields[0])
            assertEquals("kilogram", colNote0.fields[1])
            // didn't modify field 1
            assertEquals("kontra", colNote1.fields[1])
            // replaced only the proper case
            assertEquals("XeK", colNote1.fields[0])
        }
    }

    @Test
    @Flaky(OS.ALL)
    @Ignore("flaking locally as well now; see 19021")
    fun `FindReplace - replaces text only for the field in the selected note`() {
        val note0 = createFindReplaceTestNote("A", "kart", "kilogram")
        val note1 = createFindReplaceTestNote("B", "pink", "chicken")
        withBrowser {
            viewModel.selectRowAtPosition(1)
            createFindReplaceRequest("BField1", "k", "X")
            val colNote0 = withCol { getNote(note0.id) }
            // didn't modify other unselected notes
            assertEquals("kart", colNote0.fields[0])
            assertEquals("kilogram", colNote0.fields[1])
            val colNote1 = withCol { getNote(note1.id) }
            // only modified the specified field, not other fields as well
            assertEquals("pink", colNote1.fields[0])
            assertEquals("chicXen", colNote1.fields[1])
            onView(withText(TR.browsingNotesUpdated(1))).check(matches(withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE)))
        }
    }

    @Test
    @Flaky(OS.ALL)
    @Ignore("flaking locally as well now; see 19021")
    fun `FindReplace - replaces text based on regular expression`() {
        val note0 = createFindReplaceTestNote("A", "kart", "ki1logram")
        val note1 = createFindReplaceTestNote("B", "pink", "chicken")
        withBrowser {
            viewModel.selectRowAtPosition(0)
            createFindReplaceRequest("AField1", "\\d", "X", regex = true)
            val colNote0 = withCol { getNote(note0.id) }
            val colNote1 = withCol { getNote(note1.id) }
            // didn't modify other unselected notes or unselected fields
            assertEquals("kart", colNote0.fields[0])
            assertEquals("pink", colNote1.fields[0])
            assertEquals("chicken", colNote1.fields[1])
            // modified the specified field
            assertEquals("kiXlogram", colNote0.fields[1])
            onView(withText(TR.browsingNotesUpdated(1))).check(matches(withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE)))
        }
    }

    @Test
    @Flaky(OS.ALL)
    @Ignore("flaking locally as well now; see 19021")
    fun `FindReplace - replaces text in all notes if 'Only in selected notes' is unchecked`() {
        val note0 = createFindReplaceTestNote("A", "kart", "kilogram")
        val note1 = createFindReplaceTestNote("B", "pink", "chicken")
        val note2 = createFindReplaceTestNote("B", "kinetik", "kotlin")
        val note3 = createFindReplaceTestNote("C", "klean", "kip")
        withBrowser {
            viewModel.selectRowAtPosition(1)
            createFindReplaceRequest("BField1", "k", "X", onlyInSelectedNotes = false)
            val colNote0 = withCol { getNote(note0.id) }
            val colNote1 = withCol { getNote(note1.id) }
            val colNote2 = withCol { getNote(note2.id) }
            val colNote3 = withCol { getNote(note3.id) }
            // didn't modify other unselected fields
            assertEquals("kart", colNote0.fields[0])
            assertEquals("kilogram", colNote0.fields[1]) // other field name
            assertEquals("pink", colNote1.fields[0])
            assertEquals("kinetik", colNote2.fields[0])
            assertEquals("klean", colNote3.fields[0])
            // all modified
            assertEquals("chicXen", colNote1.fields[1])
            assertEquals("Xotlin", colNote2.fields[1])
            assertEquals("Xip", colNote3.fields[1])
            onView(withText(TR.browsingNotesUpdated(3))).check(matches(withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE)))
        }
    }

    @Test
    @Flaky(OS.ALL)
    @Ignore("flaking locally as well now; see 19021")
    fun `FindReplace - replaces text in all fields of selected note if 'All fields' is selected`() {
        val note0 = createFindReplaceTestNote("A", "kart", "kilogram")
        val note1 = createFindReplaceTestNote("B", "pink", "chicken")
        withBrowser {
            viewModel.selectRowAtPosition(1)
            createFindReplaceRequest(ALL_FIELDS_AS_FIELD, "k", "X")
            val colNote0 = withCol { getNote(note0.id) }
            // didn't modify other unselected notes
            assertEquals("kart", colNote0.fields[0])
            assertEquals("kilogram", colNote0.fields[1])
            val colNote1 = withCol { getNote(note1.id) }
            // only modified the specified field, not other fields as well
            assertEquals("pinX", colNote1.fields[0])
            assertEquals("chicXen", colNote1.fields[1])
            // two fields modified but one note is actually updated
            onView(withText(TR.browsingNotesUpdated(1))).check(matches(withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE)))
        }
    }

    @Test
    @Flaky(OS.ALL)
    @Ignore("flaking locally as well now; see 19021")
    fun `FindReplace - replaces text of tags as expected if 'Tags' is selected`() {
        val note0 = createFindReplaceTestNote("A", "kart", "kilogram")
        val note1 = createFindReplaceTestNote("A", "pink", "chicken")
        withBrowser {
            withCol { tags.bulkAdd(listOf(note0.id, note1.id), "JoJo") }
            viewModel.selectRowAtPosition(0)
            createFindReplaceRequest(TAGS_AS_FIELD, "JoJo", "KoKo")
            val colNote0 = withCol { getNote(note0.id) }
            val colNote1 = withCol { getNote(note1.id) }
            // didn't modify other unselected notes
            assertEquals("JoJo", colNote1.tags[0])
            // changed tag
            assertEquals("KoKo", colNote0.tags[0])
            // doesn't have the previous tags
            assertEquals(1, colNote0.tags.size)
            onView(withText(TR.browsingNotesUpdated(1))).check(matches(withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE)))
        }
    }

    @Test
    @Flaky(OS.ALL)
    @Ignore("flaking locally as well now; see 19021")
    fun `FindReplace - replaces text as expected when set to match case`() {
        val note0 = createFindReplaceTestNote("A", "kart", "kilogram")
        val note1 = createFindReplaceTestNote("B", "krate", "chicKen")
        withBrowser {
            viewModel.selectRowAtPosition(1)
            createFindReplaceRequest(ALL_FIELDS_AS_FIELD, "k", "X", matchCase = true)
            val colNote0 = withCol { getNote(note0.id) }
            val colNote1 = withCol { getNote(note1.id) }
            // didn't modify other unselected notes
            assertEquals("kart", colNote0.fields[0])
            assertEquals("kilogram", colNote0.fields[1])
            assertEquals("Xrate", colNote1.fields[0]) // matches case
            assertEquals("chicKen", colNote1.fields[1]) // doesn't match case
            onView(withText(TR.browsingNotesUpdated(1))).check(matches(withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE)))
        }
    }

    @Test
    fun `options menu test - standard`() =
        withOptionsMenu(
            OptionsMenuType(
                fragmented = false,
                mutliselect = false,
            ),
        ) {
            val expectedMenuItems =
                listOf(
                    R.id.action_add_note_from_card_browser to true,
                    R.id.action_search to true,
                    R.id.action_save_search to false,
                    R.id.action_list_my_searches to false,
                    R.id.action_sort_by_size to true,
                    R.id.action_show_marked to true,
                    R.id.action_show_suspended to true,
                    R.id.action_search_by_tag to true,
                    R.id.action_search_by_flag to true,
                    // true due to 'add note'
                    R.id.action_undo to true,
                    R.id.action_preview_many to true,
                    R.id.action_select_all to true,
                    R.id.action_open_options to true,
                    R.id.action_create_filtered_deck to true,
                    R.id.action_find_replace to true,
                )

            assertMenusEqual(expectedMenuItems, menu)
        }

    @Test
    fun `options menu test - new ui - standard`() =
        withOptionsMenu(
            OptionsMenuType(
                fragmented = false,
                mutliselect = false,
                newUi = true,
            ),
        ) {
            val expectedMenuItems =
                listOf(
                    R.id.action_add_note_from_card_browser to true,
                    R.id.action_search to false,
                    R.id.action_save_search to false,
                    R.id.action_list_my_searches to false,
                    R.id.action_sort_by_size to false,
                    R.id.action_show_marked to false,
                    R.id.action_show_suspended to false,
                    R.id.action_search_by_tag to false,
                    R.id.action_search_by_flag to false,
                    // true due to 'add note'
                    R.id.action_undo to true,
                    R.id.action_preview_many to true,
                    R.id.action_select_all to true,
                    R.id.action_open_options to true,
                    R.id.action_create_filtered_deck to true,
                    R.id.action_find_replace to true,
                )

            assertMenusEqual(expectedMenuItems, menu)
        }

    @Test
    fun `options menu test - mutliselect`() =
        withOptionsMenu(
            OptionsMenuType(
                fragmented = false,
                mutliselect = true,
            ),
        ) {
            val expectedMenuItems =
                listOf(
                    R.id.action_edit_note to true,
                    R.id.action_view_card_info to true,
                    R.id.action_flag to true,
                    R.id.action_mark_card to true,
                    R.id.action_suspend_card to true,
                    R.id.action_toggle_bury to true,
                    R.id.action_change_note_type to true,
                    R.id.action_change_deck to true,
                    R.id.action_reposition_cards to true,
                    R.id.action_reschedule_cards to true,
                    R.id.action_edit_tags to true,
                    R.id.action_grade_now to true,
                    R.id.action_reset_cards_progress to true,
                    R.id.action_preview_many to true,
                    R.id.action_export_selected to true,
                    R.id.action_find_replace to true,
                    R.id.action_delete_card to true,
                    R.id.action_undo to true,
                )

            assertMenusEqual(expectedMenuItems, menu)
        }

    @Test
    fun `options menu test - mutliselect with no selection`() =
        withOptionsMenu(
            OptionsMenuType(
                fragmented = false,
                mutliselect = true,
            ),
        ) {
            viewModel.selectNone()
            advanceRobolectricLooper()
            assertEquals(true, viewModel.isInMultiSelectMode, "still in multi-select mode")
            assertEquals(0, viewModel.selectedRowCount(), "no rows selected")

            val expectedMenuItems =
                listOf(
                    R.id.action_edit_note to false,
                    R.id.action_view_card_info to false,
                    R.id.action_flag to false,
                    R.id.action_mark_card to false,
                    R.id.action_suspend_card to false,
                    R.id.action_toggle_bury to false,
                    R.id.action_change_note_type to false,
                    R.id.action_change_deck to false,
                    R.id.action_reposition_cards to false,
                    R.id.action_reschedule_cards to false,
                    R.id.action_edit_tags to false,
                    R.id.action_grade_now to false,
                    R.id.action_reset_cards_progress to false,
                    R.id.action_preview_many to true,
                    R.id.action_export_selected to false,
                    R.id.action_find_replace to true,
                    R.id.action_delete_card to false,
                    R.id.action_undo to true,
                )

            assertMenusEqual(expectedMenuItems, menu)
        }

    @Test
    fun `options menu test - fragmented`() =
        withOptionsMenu(
            OptionsMenuType(
                fragmented = true,
                mutliselect = false,
            ),
        ) {
            val expectedMenuItems =
                listOf(
                    R.id.action_add_note_from_card_browser to true,
                    R.id.action_search to true,
                    // 'deck:"Default"' may occur
                    R.id.action_save_search to null,
                    R.id.action_list_my_searches to false,
                    R.id.action_sort_by_size to true,
                    R.id.action_show_marked to true,
                    R.id.action_show_suspended to true,
                    R.id.action_search_by_tag to true,
                    R.id.action_search_by_flag to true,
                    // true due to 'add note'
                    R.id.action_undo to true,
                    R.id.action_preview_many to false,
                    R.id.action_select_all to true,
                    R.id.action_open_options to true,
                    R.id.action_create_filtered_deck to true,
                    R.id.action_find_replace to true,
                    // Note Editor
                    R.id.action_save to true,
                    R.id.action_preview to true,
                    R.id.action_add_note_from_note_editor to false,
                    R.id.action_copy_note to true,
                    R.id.action_font_size to true,
                    R.id.action_capitalize to true,
                    R.id.action_show_toolbar to true,
                    R.id.action_scroll_toolbar to true,
                )

            assertMenusEqual(expectedMenuItems, menu)
        }

    @Test
    fun `options menu test - fragmented mutliselect`() =
        withOptionsMenu(
            OptionsMenuType(
                fragmented = true,
                mutliselect = true,
            ),
        ) {
            val expectedMenuItems =
                listOf(
                    // should never be enabled, the fragment handles the editing
                    R.id.action_edit_note to false,
                    R.id.action_view_card_info to true,
                    R.id.action_flag to true,
                    R.id.action_mark_card to true,
                    R.id.action_suspend_card to true,
                    R.id.action_toggle_bury to true,
                    R.id.action_change_note_type to true,
                    R.id.action_change_deck to true,
                    R.id.action_reposition_cards to true,
                    R.id.action_reschedule_cards to true,
                    R.id.action_edit_tags to true,
                    R.id.action_grade_now to true,
                    R.id.action_reset_cards_progress to true,
                    R.id.action_preview_many to false,
                    R.id.action_export_selected to true,
                    R.id.action_find_replace to true,
                    R.id.action_delete_card to true,
                    R.id.action_undo to true,
                    // Note Editor
                    R.id.action_save to true,
                    R.id.action_preview to true,
                    R.id.action_add_note_from_note_editor to false,
                    R.id.action_copy_note to true,
                    R.id.action_font_size to true,
                    R.id.action_capitalize to true,
                    R.id.action_show_toolbar to true,
                    R.id.action_scroll_toolbar to true,
                )

            assertMenusEqual(expectedMenuItems, menu)
        }

    @Test
    fun `deck chip performs a search`() {
        // The deck chip uses `DeckSelectionListener`, which uses a different code path
        val did = addDeck("AA")
        addDeck("BB")

        addBasicNote().firstCard().update {
            this.did = did
        }

        withBrowser(newUi = true) {
            assertThat(viewModel.cards.isEmpty(), equalTo(true))

            viewModel.setSelectedDeck(SelectableDeck.Deck(DeckNameId("AA", did)))

            assertThat(viewModel.cards.isEmpty(), equalTo(false))
        }
    }

    @Test
    fun `options menu - new ui - no notes`() =
        withOptionsMenu(
            OptionsMenuType(
                fragmented = false,
                mutliselect = false,
                newUi = true,
            ),
            noteCount = 0,
        ) {
            val expectedMenuItems =
                listOf(
                    R.id.action_add_note_from_card_browser to true,
                    R.id.action_search to false,
                    R.id.action_save_search to false,
                    R.id.action_list_my_searches to false,
                    R.id.action_sort_by_size to false,
                    R.id.action_show_marked to false,
                    R.id.action_show_suspended to false,
                    R.id.action_search_by_tag to false,
                    R.id.action_search_by_flag to false,
                    R.id.action_undo to false,
                    R.id.action_preview_many to false,
                    R.id.action_select_all to false,
                    R.id.action_open_options to true,
                    R.id.action_create_filtered_deck to true,
                    R.id.action_find_replace to true,
                )

            assertMenusEqual(expectedMenuItems, menu)
        }

    @Test
    fun `options menu - new ui - add is first if no results`() =
        withOptionsMenu(
            OptionsMenuType(
                fragmented = false,
                mutliselect = false,
                newUi = true,
            ),
            noteCount = 0,
        ) {
            assertEquals(0, viewModel.cards.size, "no cards")

            val item = assertNotNull(menu[0])
            val expectedId = R.id.action_add_note_from_card_browser

            val name = resources.getResourceName(item.itemId)
            val expectedResourceName = resources.getResourceName(expectedId)

            assertEquals(expectedResourceName, name, "resource name")
            assertEquals(expectedId, item.itemId, "$name.itemId")
            assertEquals(true, item.isVisible, "$name.isVisible")
        }

    @Test
    @Ignore("[0] is not guaranteed to be the first item. Can be fixed when legacy menu is removed")
    fun `options menu - new ui - preview is first if results`() =
        withOptionsMenu(
            OptionsMenuType(
                fragmented = false,
                mutliselect = false,
                newUi = true,
            ),
        ) {
            assertEquals(1, viewModel.cards.size, "cards exist")

            val item = assertNotNull(menu[0])
            val expectedId = R.id.action_preview_many

            val name = resources.getResourceName(item.itemId)
            val expectedResourceName = resources.getResourceName(expectedId)

            assertEquals(expectedResourceName, name, "resource name")
            assertEquals(expectedId, item.itemId, "$name.itemId")
            assertEquals(true, item.isVisible, "$name.isVisible")
        }

    fun assertMenusEqual(
        expectedMenuItems: List<Pair<Int, Boolean?>>,
        menu: Menu,
    ) {
        val resources = targetContext.resources
        for ((index, expectedData) in expectedMenuItems.withIndex().filter { it.value.second != null }) {
            val (expectedId, expectedIsVisible) = expectedData
            val item = assertNotNull(menu[index], "[$index]")
            val name = resources.getResourceName(item.itemId)

            val expectedResourceName = resources.getResourceName(expectedId)
            assertEquals(expectedResourceName, name, "[$index]: resource name")
            assertEquals(expectedId, item.itemId, "$name.itemId")
            assertEquals(expectedIsVisible, item.isVisible, "$name.isVisible")
        }

        assertEquals(expectedMenuItems.size, menu.size, "menu size")
    }

    /**
     * 3 notetypes available(named A, B and C) each with two fields.
     * Fields names follow the pattern: "${NotetypeName}field${0/1}" (ex: "Afield1").
     * "C" notetype has the same name for field 1 as notetype B!
     * second is a [Pair] representing the field data(the note has only two fields)
     */
    private fun createFindReplaceTestNote(
        notetypeName: String,
        field0: String,
        field1: String,
    ): Note {
        addStandardNoteType("A", arrayOf("Afield0", "Afield1"), "", "")
        addStandardNoteType("B", arrayOf("Bfield0", "Bfield1"), "", "")
        addStandardNoteType("C", arrayOf("Cfield0", "Bfield1"), "", "")
        return addNoteUsingNoteTypeName(notetypeName, field0, field1)
    }

    /** Simulates the user using the dialog **/
    private fun CardBrowser.createFindReplaceRequest(
        field: String,
        search: String,
        replacement: String,
        onlyInSelectedNotes: Boolean = true,
        matchCase: Boolean = false,
        regex: Boolean = false,
    ) {
        supportFragmentManager.setFragmentResult(
            REQUEST_FIND_AND_REPLACE,
            bundleOf(
                ARG_SEARCH to search,
                ARG_REPLACEMENT to replacement,
                ARG_FIELD to field,
                ARG_ONLY_SELECTED_NOTES to onlyInSelectedNotes,
                // "Ignore case" checkbox text => when it's checked we pass false to the backend
                ARG_MATCH_CASE to matchCase,
                ARG_REGEX to regex,
            ),
        )
    }

    private fun CardBrowser.openFindAndReplace() {
        showFindAndReplaceDialog()
        advanceRobolectricLooper()
    }

    private fun CardBrowser.closeFindAndReplace() {
        val findReplaceDialog = supportFragmentManager.findFragmentByTag(FindAndReplaceDialogFragment.TAG) as? DialogFragment
        assertNotNull(findReplaceDialog, "Find and replace dialog is not available")
        findReplaceDialog.dismissNow()
        advanceRobolectricLooper()
    }

    private fun CardBrowser.getFindReplaceFieldsAdapter(): SpinnerAdapter {
        val findReplaceDialog = supportFragmentManager.findFragmentByTag(FindAndReplaceDialogFragment.TAG) as? DialogFragment
        assertNotNull(findReplaceDialog, "Find and replace dialog is not available")
        val adapter = findReplaceDialog.dialog?.findViewById<Spinner>(R.id.fields_selector)?.adapter
        assertNotNull(adapter, "Find and replace fields adapter is not available")
        return adapter
    }

    fun NotetypeJson.addNote(
        field: String,
        vararg fields: String,
    ): Note = addNoteUsingNoteTypeName(this.name, field, *fields)

    private fun CheckedCardResult.getColumnHeaderText(header: CardBrowserColumn): String? {
        // There's currently a minimum of 2 columns
        col.backend.setActiveBrowserColumns(listOf(header.ankiColumnKey, "answer"))
        return col
            .browserRowForId(this.cardOrNoteId)
            .getCells(0)
            .text
    }

    @Suppress("SameParameterValue")
    private fun withBrowser(
        noteCount: Int = 0,
        fragmented: Boolean = false,
        newUi: Boolean = false,
        block: suspend CardBrowser.() -> Unit,
    ) = runTest {
        suspend fun run(block: suspend () -> Unit) {
            if (fragmented) withSplitPaneUiAsync { block() } else block()
        }

        setCardBrowserFragmented(fragmented)
        setUsingSearchView(newUi)

        run {
            getBrowserWithNotes(noteCount).apply {
                if (fragmented) {
                    viewModel.setQuery("deck:\"Default\"", forceRefresh = true)
                    advanceRobolectricLooper()
                    assertNotNull(fragment, message = "note editor fragment")
                }

                block(this)
            }
        }
    }

    data class OptionsMenuType(
        val fragmented: Boolean,
        val mutliselect: Boolean,
        val newUi: Boolean = false,
    )

    fun withOptionsMenu(
        type: OptionsMenuType,
        noteCount: Int = 1,
        block: suspend CardBrowser.() -> Unit,
    ) = withBrowser(noteCount = noteCount, fragmented = type.fragmented, newUi = type.newUi) {
        if (type.mutliselect) selectAll()
        block()
    }
}

fun setCardBrowserFragmented(value: Boolean) = Prefs.putBoolean(R.string.dev_card_browser_fragmented, value)

fun setUsingSearchView(value: Boolean) = Prefs.putBoolean(R.string.dev_card_browser_search_view, value)

private fun CardBrowser.rerenderAllCards() {
    cardBrowserFragment.cardsAdapter.notifyDataSetChanged()
    advanceRobolectricLooper()
}

fun CardBrowser.hasSelectedCardAtPosition(i: Int): Boolean = viewModel.selectedRows.contains(viewModel.getRowAtPosition(i))

fun CardBrowser.replaceSelectionWith(positions: IntArray) {
    viewModel.selectNone()
    selectRowsWithPositions(*positions)
}

fun CardBrowser.column1Text(row: Int): CharSequence? = getVisibleRows()[row].columnViews[0].text

fun CardBrowser.selectRowsWithPositions(vararg positions: Int) {
    // PREF: inefficient as the card flow is updated each iteration
    positions.forEach { pos ->
        check(pos < viewModel.rowCount) {
            "Attempted to check row at index $pos. ${viewModel.rowCount} rows available"
        }
        viewModel.selectRowAtPosition(pos)
    }
}

fun CardBrowser.clickRowAtPosition(pos: Int) = cardBrowserFragment.onTap(viewModel.cards[pos])

fun CardBrowser.longClickRowAtPosition(pos: Int) = viewModel.handleRowLongPress(viewModel.cards[pos].toRowSelection())

val CardBrowser.lastDeckId
    get() = viewModel.lastDeckId

val CardBrowser.validDecksForChangeDeck
    get() = runBlocking { viewModel.getAvailableDecks() }

suspend fun CardBrowser.searchCardsSync(query: String) {
    searchCards(query)
    viewModel.searchJob?.join()
}

suspend fun CardBrowser.filterByTagSync(vararg tags: String) {
    cardBrowserFragment.filterByTag(*tags)
    viewModel.searchJob?.join()
}

suspend fun CardBrowserViewModel.setFlagFilterSync(flag: Flag) {
    setFlagFilter(flag)
    searchJob?.join()
}

fun AnkiTest.flagCardForNote(
    n: Note,
    flag: Flag,
) {
    n.firstCard().update {
        setUserFlag(flag.code)
    }
}

fun CardBrowser.getVisibleRows() =
    sequence {
        val cardsListView = cardBrowserFragment.cardsListView
        for (i in 0 until (cardsListView.childCount)) {
            val row =
                cardsListView.getChildViewHolder(
                    cardsListView.getChildAt(
                        i,
                    ),
                )
            yield(row as BrowserMultiColumnAdapter.MultiColumnViewHolder)
        }
    }.toList().also {
        assertThat("has visible rows", it.any())
    }

val CardBrowser.isShowingSelectAll: Boolean
    get() {
        advanceRobolectricLooper()
        return this.menu().findItem(R.id.action_select_all)?.isVisible == true
    }

val CardBrowser.columnHeadingViews
    get() =
        this.cardBrowserFragment.browserColumnHeadings.children
            .filterIsInstance<TextView>()
            .toList()

val CardBrowser.columnHeadings
    get() =
        columnHeadingViews.map { it.text.toString() }

suspend fun CardBrowser.searchCards(search: String? = null) {
    if (search != null) {
        viewModel.setQuery(search)
    } else {
        viewModel.launchSearchForCards()
    }
    viewModel.searchJob?.join()
}

fun CardBrowser.showFindAndReplaceDialog() = cardBrowserFragment.showFindAndReplaceDialog()

suspend fun CardBrowser.selectAll() {
    viewModel.selectAll()?.join()
    advanceRobolectricLooper()
}

val CardBrowser.menu: Menu
    get() = if (this.useSearchView) cardBrowserFragment.searchBar!!.menu else shadowOf(this).optionsMenu!!

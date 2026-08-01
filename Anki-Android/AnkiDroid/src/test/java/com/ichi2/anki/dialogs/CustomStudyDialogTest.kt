// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs

import android.os.Bundle
import android.widget.AdapterView
import android.widget.ListView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.SavedStateHandle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.scheduler.CustomStudyDefaultsResponse
import anki.scheduler.CustomStudyRequest.Cram.CramKind
import anki.scheduler.customStudyDefaultsResponse
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.ContextMenuOption
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.CustomStudyDefaults.Companion.toDomainModel
import com.ichi2.anki.dialogs.customstudy.CustomStudyViewModel
import com.ichi2.anki.dialogs.tags.TagsDialogListener.Companion.ON_SELECTED_TAGS_KEY
import com.ichi2.anki.dialogs.tags.TagsDialogListener.Companion.ON_SELECTED_TAGS__SELECTED_TAGS
import com.ichi2.anki.dialogs.utils.performPositiveClick
import com.ichi2.anki.libanki.CardType
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.Consts
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.Note
import com.ichi2.anki.libanki.QueueType
import com.ichi2.anki.libanki.sched.Scheduler
import com.ichi2.testutils.AnkiFragmentScenario
import com.ichi2.testutils.isJsonEqual
import com.ichi2.testutils.uninitializeField
import io.mockk.every
import io.mockk.mockk
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.hamcrest.Matchers.lessThan
import org.intellij.lang.annotations.Language
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class CustomStudyDialogTest : RobolectricTest() {
    @Before
    override fun setUp() {
        super.setUp()
        uninitializeField<CustomStudyDialog>("deferredDefaults")
    }

    @Test
    fun `new custom study decks have expected structure - issue 6289`() =
        runTest {
            val studyType = ContextMenuOption.STUDY_PREVIEW
            // we need a non-empty deck to custom study
            addBasicNote()

            withCustomStudyFragment(
                args = argumentsDisplayingSubscreen(studyType),
            ) { dialogFragment: CustomStudyDialog ->
                dialogFragment.submitSubscreenData()
            }

            val customStudy = col.decks.current()
            assertThat("Custom Study should be filtered", customStudy.isFiltered)

            // remove timestamps to allow us to compare JSON
            customStudy.remove("id")
            customStudy.remove("mod")
            customStudy.remove("name")

            // compare JSON
            @Language("json")
            val expected =
                """
                {
                    "browserCollapsed": true,
                    "collapsed": true,
                    "delays": null,
                    "desc": "",
                    "dyn": 1,
                    "lrnToday": [0, 0],
                    "newToday": [0, 0],
                    "previewDelay": 10,
                    "previewAgainSecs": 60,
                    "previewHardSecs": 600,
                    "previewGoodSecs": 0,
                    "resched": false,
                    "revToday": [0, 0],
                    "separate": true,
                    "terms": [
                        ["is:new added:1 deck:Default", 99999, 5]
                    ],
                    "timeToday": [0, 0],
                    "usn": -1
                }
                """.trimIndent()
            assertThat(customStudy, isJsonEqual(expected))
        }

    @Test
    fun `previous value for 'increase new card limit' is suggested`() {
        // add cards to be sure we can extend successfully. Needs to be > 20
        repeat(23) {
            addBasicNote()
        }
        val newExtendByValue = 1

        assertThat("'new' default value", defaultsOfDefaultDeck.extendNew.initialValue, equalTo(0))

        // extend limits with a value of '1'
        withCustomStudyFragment(
            args = argumentsDisplayingSubscreen(ContextMenuOption.EXTEND_NEW),
        ) { dialogFragment: CustomStudyDialog ->

            onSubscreenEditText()
                .perform(replaceText(newExtendByValue.toString()))

            dialogFragment.submitSubscreenData()
        }

        // ensure backend is updated
        assertThat(
            "'new' updated value",
            defaultsOfDefaultDeck.extendNew.initialValue,
            equalTo(newExtendByValue),
        )

        // ensure 'newExtendByValue' is used by our UI
        withCustomStudyFragment(
            args = argumentsDisplayingSubscreen(ContextMenuOption.EXTEND_NEW),
        ) {
            onSubscreenEditText()
                .check(matches(withText(newExtendByValue.toString())))
        }
    }

    @Test
    fun `previous value for 'increase review card limit' is suggested`() {
        // Reduce review limit to 0, so we can successfully extend with just 1 review card.
        updateDeckConfig(Consts.DEFAULT_DECK_ID) { rev.perDay = 0 }
        addRevBasicNoteDueToday("Review", "Today")

        val reviewExtendByValue = 1
        assertThat("'review' default value", defaultsOfDefaultDeck.extendReview.initialValue, equalTo(0))

        // Extend reviews by 'reviewExtendByValue'.
        withCustomStudyFragment(
            args = argumentsDisplayingSubscreen(ContextMenuOption.EXTEND_REV),
        ) { dialogFragment: CustomStudyDialog ->
            onSubscreenEditText()
                .perform(replaceText(reviewExtendByValue.toString()))
            dialogFragment.submitSubscreenData()
        }

        // Ensure backend is updated.
        assertThat(
            "'review' updated value",
            defaultsOfDefaultDeck.extendReview.initialValue,
            equalTo(reviewExtendByValue),
        )

        // Ensure 'reviewExtendByValue' is used in our UI.
        withCustomStudyFragment(
            args = argumentsDisplayingSubscreen(ContextMenuOption.EXTEND_REV),
        ) {
            onSubscreenEditText()
                .check(matches(withText(reviewExtendByValue.toString())))
        }
    }

    @Test
    fun `creating a tags custom session uses selected card state`() =
        runTest {
            val testDeckId = addDeck("A")
            val n1 = addNoteToDeckA { addTag("testTag") }
            val n2 = addNoteToDeckA { addTag("anotherTag") }
            val n3 = addNoteToDeckA { addTag("testTag") }
            // target this specific card for custom studying
            // TODO use Card.moveToReviewQueue when it's refactored
            n3.firstCard().update {
                due = col.sched.today
                queue = QueueType.Rev
                type = CardType.Rev
            }
            col.updateCard(n3.firstCard())
            val dueNow = col.findCards("is:due")
            assertThat(dueNow.size, equalTo(1))
            assertThat(dueNow[0], equalTo(n3.firstCard().id))
            // make sure there isn't a 'Custom Study Session' already present
            assertNull(col.decks.customStudySession)
            val args = argumentsDisplayingSubscreen(ContextMenuOption.STUDY_TAGS, deckId = testDeckId)
            withCustomStudyFragment(args = args) { studyDialog ->
                val d = studyDialog.dialog
                assertNotNull(d)
                // the first item is automatically selected at start
                assertThat(studyDialog.viewModel.selectedCardStateIndex, equalTo(0))

                studyDialog.setCardStateSelectionTo(CustomStudyDialog.CustomStudyCardState.DueCardsOnly)

                // create list of selected tags
                // Note: using an ArrayList because that is how it's stored in the passed Bundle
                val selectedTags = ArrayList<String>(1).apply { add("testTag") }
                // simulate tag selection
                studyDialog.parentFragmentManager.setFragmentResult(
                    ON_SELECTED_TAGS_KEY,
                    Bundle().apply { putStringArrayList(ON_SELECTED_TAGS__SELECTED_TAGS, selectedTags) },
                )
                val customStudyDeck = col.decks.customStudySession
                assertNotNull(customStudyDeck)
                assertThat(col.decks.cardCount(customStudyDeck.id), equalTo(1))
                assertThat(n1.firstCard().did, equalTo(testDeckId))
                assertThat(n2.firstCard().did, equalTo(testDeckId))
                assertThat(n3.firstCard().did, equalTo(customStudyDeck.id))
                assertThat(n3.firstCard().oDid, equalTo(testDeckId))
            }
        }

    private fun addNoteToDeckA(setup: Note.() -> Unit): Note =
        addBasicNote().update {
            moveToDeck("A", false)
            setup()
        }

    @Test
    @Ignore("disabled while we confirm/diagnose issues")
    @Config(qualifiers = "en")
    fun `'increase new limit' is shown when there are new cards`() {
        val studyDefaults = customStudyDefaultsResponse { availableNew = 1 }
        CollectionManager.setColForTests(mockCollectionWithSchedulerReturning(studyDefaults))

        withCustomStudyFragment(args = argumentsDisplayingMainScreen()) {
            onView(withText(TR.customStudyIncreaseTodaysNewCardLimit()))
                .inRoot(isDialog())
                .check(matches(isEnabled()))
        }
    }

    @Test
    @Ignore("disabled while we confirm/diagnose issues")
    @Config(qualifiers = "en")
    fun `'increase new limit' is not shown when there are no new cards`() {
        val studyDefaults = customStudyDefaultsResponse { availableNew = 0 }
        CollectionManager.setColForTests(mockCollectionWithSchedulerReturning(studyDefaults))

        withCustomStudyFragment(args = argumentsDisplayingMainScreen()) {
            onView(withText(TR.customStudyIncreaseTodaysNewCardLimit()))
                .inRoot(isDialog())
                .check(matches(not(isEnabled())))
        }
    }

    @Test
    @Ignore("disabled while we confirm/diagnose issues")
    @Config(qualifiers = "en")
    fun `'increase review limit' is shown when there are new cards`() {
        val studyDefaults = customStudyDefaultsResponse { availableReview = 1 }
        CollectionManager.setColForTests(mockCollectionWithSchedulerReturning(studyDefaults))

        withCustomStudyFragment(args = argumentsDisplayingMainScreen()) {
            onView(withText(TR.customStudyIncreaseTodaysReviewCardLimit()))
                .inRoot(isDialog())
                .check(matches(isEnabled()))
        }
    }

    @Test
    @Ignore("disabled while we confirm/diagnose issues")
    @Config(qualifiers = "en")
    fun `'increase review limit' is not shown when there are no new cards`() {
        val studyDefaults = customStudyDefaultsResponse { availableReview = 0 }
        CollectionManager.setColForTests(mockCollectionWithSchedulerReturning(studyDefaults))

        withCustomStudyFragment(args = argumentsDisplayingMainScreen()) {
            onView(withText(TR.customStudyIncreaseTodaysReviewCardLimit()))
                .inRoot(isDialog())
                .check(matches(not(isEnabled())))
        }
    }

    @Test
    fun `subscreens are ignored when restoring from process death`() {
        withCustomStudyFragment(args = argumentsDisplayingSubscreen(ContextMenuOption.EXTEND_REV, restoreFromProcessDeath = true)) {
            // ensure we're on the main screen
            onView(withText(TR.customStudyIncreaseTodaysReviewCardLimit()))
                .inRoot(isDialog())
                .check(matches(isEnabled()))
        }
    }

    @Test
    fun `selectedKind maps selected card state index to cram kind`() {
        val viewModel = CustomStudyViewModel(SavedStateHandle())

        CustomStudyDialog.CustomStudyCardState.entries.forEachIndexed { index, cardState ->
            viewModel.selectedCardStateIndex = index
            assertThat(viewModel.selectedKind, equalTo(cardState.kind))
        }
    }

    @Test
    fun `selectedKind defaults to new cards when no card state is selected`() {
        val viewModel = CustomStudyViewModel(SavedStateHandle())

        viewModel.selectedCardStateIndex = AdapterView.INVALID_POSITION

        assertThat(viewModel.selectedKind, equalTo(CramKind.CRAM_KIND_NEW))
    }

    /**
     * Runs [block] on a [CustomStudyDialog]
     */
    private fun withCustomStudyFragment(
        args: Bundle,
        block: (CustomStudyDialog) -> Unit,
    ) {
        AnkiFragmentScenario.launch(CustomStudyDialog::class.java, args).use { scenario ->
            scenario.onFragment { dialogFragment: CustomStudyDialog ->
                block(dialogFragment)
            }
        }
    }

    private fun mockCollectionWithSchedulerReturning(response: CustomStudyDefaultsResponse) =
        mockk<Collection>(relaxed = true) {
            every { sched } returns
                mockk<Scheduler> {
                    every { customStudyDefaults(Consts.DEFAULT_DECK_ID) } returns response
                }
        }

    private fun argumentsDisplayingSubscreen(
        subscreen: ContextMenuOption,
        deckId: DeckId = Consts.DEFAULT_DECK_ID,
        restoreFromProcessDeath: Boolean = false,
    ): Bundle {
        @Suppress("RedundantValueArgument")
        fun setupDefaultValuesSingleton() {
            withCustomStudyFragment(argumentsDisplayingMainScreen(deckId = deckId)) { }
        }

        if (!restoreFromProcessDeath) {
            setupDefaultValuesSingleton()
        }

        return requireNotNull(
            CustomStudyDialog
                .createSubDialog(
                    deckId = deckId,
                    contextMenuAttribute = subscreen,
                ).arguments,
        )
    }

    private fun argumentsDisplayingMainScreen(deckId: DeckId = Consts.DEFAULT_DECK_ID) =
        requireNotNull(
            CustomStudyDialog
                .createInstance(
                    deckId = deckId,
                ).arguments,
        )

    private fun onSubscreenEditText() =
        onView(withId(R.id.details_edit_text_2))
            .inRoot(isDialog())

    private fun CustomStudyDialog.submitSubscreenData() =
        assertNotNull(dialog as? AlertDialog?, "dialog").also { dialog ->
            dialog.performPositiveClick()
        }

    /** Set the card state to [state] and also verify the selection in the fragment's ViewModel */
    private fun CustomStudyDialog.setCardStateSelectionTo(state: CustomStudyDialog.CustomStudyCardState) {
        assertThat(
            state.ordinal,
            allOf(
                greaterThanOrEqualTo(0),
                lessThan(CustomStudyDialog.CustomStudyCardState.entries.size),
            ),
        )
        // can't use MaterialAutoCompleteTextView.listSelection as the popup is closed and updates
        // to 'listSelection' are ignored so we trigger the listener directly with dummy data but a
        // correct position(what we actually use in the listener)
        val adapterView = ListView(targetContext) // dummy view, fills AdapterView first param requirement
        binding.cardsStateSelector.onItemClickListener
            .onItemClick(adapterView, adapterView, state.ordinal, 1)
        assertThat(viewModel.selectedCardStateIndex, equalTo(state.ordinal))
    }

    /**
     * The current backend value of [CustomStudyDialog.CustomStudyDefaults] for the default deck
     * */
    private val defaultsOfDefaultDeck
        get() = col.sched.customStudyDefaults(Consts.DEFAULT_DECK_ID).toDomainModel()
}

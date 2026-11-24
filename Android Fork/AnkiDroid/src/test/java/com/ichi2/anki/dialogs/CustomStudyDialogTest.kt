// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs

import android.os.Bundle
import android.os.Looper
import android.view.View
import android.widget.AdapterView
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.scheduler.CustomStudyDefaultsResponse
import anki.scheduler.CustomStudyRequest.Cram.CramKind
import anki.scheduler.customStudyDefaultsResponse
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.ContextMenuOption
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.CustomStudyAction
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.CustomStudyDefaults.Companion.toDomainModel
import com.ichi2.anki.dialogs.customstudy.CustomStudyViewModel
import com.ichi2.anki.dialogs.tags.TagsDialogListener.Companion.REQUEST_SELECT_TAGS
import com.ichi2.anki.dialogs.tags.TagsDialogListener.Companion.RESULT_SELECTED_TAGS
import com.ichi2.anki.dialogs.utils.performPositiveClick
import com.ichi2.anki.ioDispatcher
import com.ichi2.anki.libanki.CardType
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.Consts
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.Note
import com.ichi2.anki.libanki.QueueType
import com.ichi2.anki.libanki.sched.Scheduler
import com.ichi2.anki.utils.ext.DIALOG_FRAGMENT_TAG
import com.ichi2.anki.utils.ext.showDialogFragment
import com.ichi2.testutils.AnkiFragmentScenario
import com.ichi2.testutils.EmptyAnkiActivity
import com.ichi2.testutils.ext.snackbarText
import com.ichi2.testutils.isJsonHolderEqual
import com.ichi2.testutils.launchFragment
import com.ichi2.testutils.uninitializeField
import com.ichi2.utils.positiveButton
import com.ichi2.utils.textAsIntOrNull
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.withContext
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.greaterThan
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.hamcrest.Matchers.lessThan
import org.hamcrest.Matchers.nullValue
import org.intellij.lang.annotations.Language
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import com.ichi2.anki.common.android.R as CommonR

@RunWith(AndroidJUnit4::class)
class CustomStudyDialogTest : RobolectricTest() {
    @Before
    override fun setUp() {
        super.setUp()
        uninitializeField<CustomStudyDialog>("deferredInitialization")
    }

    @Test
    fun `cancelling a defaults read prevents late deck selection`() =
        runTest {
            val response = customStudyDefaultsResponse { availableNew = 1 }
            val collection = mockCollectionWithSchedulerReturning(response)
            val decks = collection.decks
            val initialization = Job()
            every { collection.sched.customStudyDefaults(Consts.DEFAULT_DECK_ID) } answers {
                initialization.cancel()
                response
            }
            CollectionManager.setColForTests(collection)
            val viewModel = CustomStudyViewModel(SavedStateHandle(mapOf(CustomStudyViewModel.ARG_DECK_ID to Consts.DEFAULT_DECK_ID)))

            assertFailsWith<CancellationException> {
                withContext(initialization) { viewModel.initialize() }
            }

            verify(exactly = 0) { decks.select(Consts.DEFAULT_DECK_ID) }
        }

    @Test
    fun `empty deck initialization does not block the dialog`() =
        runTest {
            val other = addDeck("Other")
            col.setDeck(listOf(addBasicNote().firstCard().id), other)
            val undoStatus = col.undoStatus()
            val previousDispatcher = ioDispatcher
            ioDispatcher = StandardTestDispatcher(testScheduler)
            try {
                createHostActivity().use { controller ->
                    val activity = controller.get()
                    val fragment = CustomStudyDialog.createInstance(Consts.DEFAULT_DECK_ID)
                    activity.showDialogFragment(fragment)
                    val dialog = fragment.requireDialog()
                    assertTrue(assertNotNull(dialog.findViewById<View>(R.id.progress_bar)).isVisible)

                    runCurrent()

                    assertUnavailableMenu(fragment)
                    assertEquals(Consts.DEFAULT_DECK_ID, col.decks.selected())
                    assertEquals(undoStatus, col.undoStatus())
                }
            } finally {
                ioDispatcher = previousDispatcher
            }
        }

    @Test
    fun `populated deck selection waits for initialization`() =
        runTest {
            val target = addDeck("Target")
            col.setDeck(listOf(addBasicNote().firstCard().id), target)
            val previousDispatcher = ioDispatcher
            ioDispatcher = StandardTestDispatcher(testScheduler)
            try {
                AnkiFragmentScenario.launch(CustomStudyDialog::class.java, argumentsDisplayingMainScreen(target)).use { scenario ->
                    assertEquals(Consts.DEFAULT_DECK_ID, col.decks.selected())

                    runCurrent()

                    assertEquals(target, col.decks.selected())
                    scenario.onFragment { fragment ->
                        assertFalse(assertNotNull(fragment.dialog?.findViewById<View>(R.id.progress_bar)).isVisible)
                        onView(withText(TR.customStudyStudyByCardStateOrTag()))
                            .inRoot(isDialog())
                            .check(matches(isEnabled()))
                    }
                }
            } finally {
                ioDispatcher = previousDispatcher
            }
        }

    @Test
    fun `cancelling initialization does not select the deck`() =
        runTest {
            val target = addDeck("Target")
            col.setDeck(listOf(addBasicNote().firstCard().id), target)
            val undoStatus = col.undoStatus()
            val previousDispatcher = ioDispatcher
            ioDispatcher = StandardTestDispatcher(testScheduler)
            try {
                AnkiFragmentScenario.launch(CustomStudyDialog::class.java, argumentsDisplayingMainScreen(target)).use { scenario ->
                    scenario.moveToState(Lifecycle.State.DESTROYED)

                    runCurrent()

                    assertEquals(Consts.DEFAULT_DECK_ID, col.decks.selected())
                    assertEquals(undoStatus, col.undoStatus())
                }
            } finally {
                ioDispatcher = previousDispatcher
            }
        }

    @Test
    fun `empty initialization waits for resume before showing feedback`() =
        runTest {
            val previousDispatcher = ioDispatcher
            ioDispatcher = StandardTestDispatcher(testScheduler)
            try {
                createHostActivity().use { controller ->
                    val activity = controller.get()
                    val fragment = CustomStudyDialog.createInstance(Consts.DEFAULT_DECK_ID)
                    activity.showDialogFragment(fragment)
                    controller.pause().saveInstanceState(Bundle()).stop()

                    runCurrent()

                    assertTrue(fragment.isAdded)
                    assertTrue(activity.supportFragmentManager.isStateSaved)
                    assertNull(activity.snackbarText)

                    controller.start().resume().visible()
                    runCurrent()

                    assertUnavailableMenu(fragment)
                }
            } finally {
                ioDispatcher = previousDispatcher
            }
        }

    @Test
    fun `cancelled loading dialog does not show empty-deck feedback`() =
        runTest {
            val previousDispatcher = ioDispatcher
            ioDispatcher = StandardTestDispatcher(testScheduler)
            try {
                createHostActivity().use { controller ->
                    val activity = controller.get()
                    val fragment = CustomStudyDialog.createInstance(Consts.DEFAULT_DECK_ID)
                    activity.showDialogFragment(fragment)
                    fragment.requireDialog().cancel()

                    runCurrent()
                    shadowOf(Looper.getMainLooper()).idleFor(300, TimeUnit.MILLISECONDS)
                    activity.supportFragmentManager.executePendingTransactions()

                    assertFalse(fragment.isAdded)
                    assertNull(activity.snackbarText)
                }
            } finally {
                ioDispatcher = previousDispatcher
            }
        }

    @Test
    fun `stale custom study menu explains that the deck is now empty`() =
        runTest {
            val card = addBasicNote().firstCard()
            withCustomStudyFragment(argumentsDisplayingMainScreen()) { fragment ->
                val fragmentManager = fragment.parentFragmentManager
                col.removeNotes(cardIds = listOf(card.id))

                onView(withText(TR.customStudyReviewAhead())).inRoot(isDialog()).perform(click())
                advanceUntilIdle()

                assertUnavailableMenu(fragment)
                assertEquals(
                    0,
                    fragmentManager.fragments
                        .filterIsInstance<CustomStudyDialog>()
                        .size,
                )
            }
        }

    @Test
    fun `stale menu becomes unavailable when its deck is deleted`() =
        runTest {
            val target = addDeck("Target")
            col.setDeck(listOf(addBasicNote().firstCard().id), target)
            withCustomStudyFragment(argumentsDisplayingMainScreen(target)) { fragment ->
                val dialog = assertNotNull(fragment.dialog as? AlertDialog)
                col.decks.remove(listOf(target))
                val selectedDeck = col.decks.selected()
                val undoStatus = col.undoStatus()

                onView(withText(TR.customStudyReviewAhead())).inRoot(isDialog()).perform(click())
                advanceUntilIdle()

                assertFalse(dialog.isShowing)
                assertUnavailableMenu(fragment)
                assertEquals(selectedDeck, col.decks.selected())
                assertEquals(undoStatus, col.undoStatus())
            }
        }

    @Test
    fun `deleted deck initialization explains why custom study is unavailable`() {
        val target = addDeck("Target")
        col.setDeck(listOf(addBasicNote().firstCard().id), target)
        col.decks.remove(listOf(target))
        val selectedDeck = col.decks.selected()
        val undoStatus = col.undoStatus()

        withCustomStudyFragment(argumentsDisplayingMainScreen(target)) { fragment ->
            assertUnavailableMenu(fragment)
        }

        assertEquals(selectedDeck, col.decks.selected())
        assertEquals(undoStatus, col.undoStatus())
    }

    @Test
    fun `menu check does not navigate after state saving and can be retried on resume`() =
        runTest {
            addBasicNote()
            val controller = Robolectric.buildActivity(EmptyAnkiActivity::class.java).setup()
            saveControllerForCleanup(controller)
            advanceUntilIdle()
            advanceRobolectricLooper()
            val activity = controller.get()
            activity.showDialogFragment(CustomStudyDialog.createInstance(Consts.DEFAULT_DECK_ID))
            val fragment = activity.supportFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG) as CustomStudyDialog
            advanceRobolectricLooperUntil { fragment.dialog?.findViewById<View>(R.id.progress_bar)?.isVisible != true }

            // Exercise the production queue's suspension instead of Robolectric's synchronous lock.
            val previousIoDispatcher = ioDispatcher
            ioDispatcher = StandardTestDispatcher(testScheduler)
            val collectionScheduler = TestCoroutineScheduler()
            val previousDispatcher =
                CollectionManager.setTestDispatcher(StandardTestDispatcher(collectionScheduler), useReentrantLock = false)
            try {
                onView(withText(TR.customStudyReviewAhead())).inRoot(isDialog()).perform(click())
                runCurrent()
                assertSame(fragment, activity.supportFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG))
                controller.pause().saveInstanceState(Bundle()).stop()

                collectionScheduler.runCurrent()
                runCurrent()
                shadowOf(Looper.getMainLooper()).idle()

                assertSame(fragment, activity.supportFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG))
                controller.start().resume().visible()
                advanceUntilIdle()
                advanceRobolectricLooper()
                assertSame(fragment, activity.supportFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG))

                onView(withText(TR.customStudyReviewAhead())).inRoot(isDialog()).perform(click())
                runCurrent()
                collectionScheduler.runCurrent()
                runCurrent()
                shadowOf(Looper.getMainLooper()).idle()
                collectionScheduler.runCurrent()
                runCurrent()

                onSubscreenEditText().check(matches(isDisplayed()))
                assertEquals(
                    1,
                    activity.supportFragmentManager.fragments
                        .filterIsInstance<CustomStudyDialog>()
                        .size,
                )
            } finally {
                CollectionManager.setTestDispatcher(previousDispatcher)
                ioDispatcher = previousIoDispatcher
            }
        }

    @Test
    fun `hidden empty Default explains why custom study is unavailable and preserves redo`() {
        val other = addDeck("Other")
        col.setDeck(listOf(addBasicNote().firstCard().id), other)
        addBasicNote()
        col.undo()
        assertNull(col.sched.deckDueTree().find(Consts.DEFAULT_DECK_ID))
        val undoStatus = col.undoStatus()
        assertNotNull(undoStatus.redo)

        withCustomStudyFragment(argumentsDisplayingMainScreen()) { fragment ->
            assertUnavailableMenu(fragment)
        }

        assertEquals(Consts.DEFAULT_DECK_ID, col.decks.selected())
        assertEquals(undoStatus, col.undoStatus())
        col.redo()
        withCustomStudyFragment(argumentsDisplayingMainScreen()) {
            onView(withText(TR.customStudyStudyByCardStateOrTag()))
                .inRoot(isDialog())
                .check(matches(isEnabled()))
        }
    }

    @Test
    fun `hidden Default with borrowed cards avoids loading defaults and preserves selection`() {
        addBasicNote()
        val filtered = addDynamicDeck("Filtered", "")
        assertNull(col.sched.deckDueTree().find(Consts.DEFAULT_DECK_ID))
        assertEquals(1, col.decks.cardCount(Consts.DEFAULT_DECK_ID, includeSubdecks = true))
        val undoStatus = col.undoStatus()

        withCustomStudyFragment(argumentsDisplayingMainScreen()) { fragment ->
            assertUnavailableMenu(fragment)
        }

        assertEquals(filtered, col.decks.selected())
        assertEquals(undoStatus, col.undoStatus())

        col.sched.emptyFilteredDeck(filtered)
        withCustomStudyFragment(argumentsDisplayingMainScreen()) {
            onView(withText(TR.customStudyStudyByCardStateOrTag()))
                .inRoot(isDialog())
                .check(matches(isEnabled()))
        }
    }

    @Test
    fun `stale menu becomes unavailable when a filtered deck borrows all Default cards`() =
        runTest {
            addBasicNote()
            withCustomStudyFragment(argumentsDisplayingMainScreen()) { fragment ->
                val fragmentManager = fragment.parentFragmentManager
                val filtered = addDynamicDeck("Filtered", "")
                assertNull(col.sched.deckDueTree().find(Consts.DEFAULT_DECK_ID))
                assertEquals(1, col.decks.cardCount(Consts.DEFAULT_DECK_ID, includeSubdecks = true))
                val undoStatus = col.undoStatus()

                onView(withText(TR.customStudyReviewAhead())).inRoot(isDialog()).perform(click())
                advanceUntilIdle()

                assertUnavailableMenu(fragment)
                assertEquals(
                    0,
                    fragmentManager.fragments
                        .filterIsInstance<CustomStudyDialog>()
                        .size,
                )
                assertEquals(filtered, col.decks.selected())
                assertEquals(undoStatus, col.undoStatus())
            }
        }

    @Test
    fun `empty deck explains why custom study is unavailable without selecting it`() {
        addBasicNote()
        val emptyDeck = addDeck("Empty")
        val undoStatus = col.undoStatus()

        withCustomStudyFragment(argumentsDisplayingMainScreen(emptyDeck)) { fragment ->
            assertUnavailableMenu(fragment)
        }

        assertEquals(Consts.DEFAULT_DECK_ID, col.decks.selected())
        assertEquals(undoStatus, col.undoStatus())
    }

    @Test
    fun `custom study remains available when only a child deck has cards`() {
        val child = addDeck("Default::Child")
        col.setDeck(listOf(addBasicNote().firstCard().id), child)

        withCustomStudyFragment(argumentsDisplayingMainScreen()) {
            onView(withText(TR.customStudyStudyByCardStateOrTag()))
                .inRoot(isDialog())
                .check(matches(isEnabled()))
        }
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
            customStudy.jsonObject.remove("id")
            customStudy.jsonObject.remove("mod")
            customStudy.jsonObject.remove("name")

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
            assertThat(customStudy, isJsonHolderEqual(expected))
        }

    /** Issue 22801: the activity owns the operation, so it can finish after the dialog is removed. */
    @Test
    fun `custom study completes after its dialog is dismissed`() =
        runTest {
            addBasicNote()
            withCustomStudyFragment(argumentsDisplayingSubscreen(ContextMenuOption.STUDY_PREVIEW)) { fragment ->
                val activity = fragment.requireActivity()
                val manager = fragment.parentFragmentManager
                val preferences = activity.sharedPrefs()
                val collection = col
                var result: CustomStudyAction? = null
                manager.setFragmentResultListener(CustomStudyAction.REQUEST_KEY, activity) { _, bundle ->
                    result = CustomStudyAction.fromBundle(bundle)
                }
                val collectionScheduler = TestCoroutineScheduler()
                val previousDispatcher =
                    CollectionManager.setTestDispatcher(StandardTestDispatcher(collectionScheduler), useReentrantLock = false)
                try {
                    fragment.submitSubscreenData()
                    runCurrent()
                    assertFalse(collection.decks.current().isFiltered)

                    fragment.dismissNow()
                    assertNull(fragment.activity)
                    collectionScheduler.runCurrent()
                    runCurrent()
                    advanceRobolectricLooper()

                    assertTrue(collection.decks.current().isFiltered)
                    assertEquals(CustomStudyAction.CUSTOM_STUDY_SESSION, result)
                    assertEquals(1, preferences.getInt("previewDays", -1))
                } finally {
                    CollectionManager.setTestDispatcher(previousDispatcher)
                }
            }
        }

    @Test
    fun `previous value for 'increase new card limit' is suggested`() =
        runTest {
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
    fun `previous value for 'increase review card limit' is suggested`() =
        runTest {
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
    @Config(qualifiers = "en")
    fun `'extend limits' dialogs are titled 'Extend daily limits' and confirm with 'Increase'`() {
        listOf(ContextMenuOption.EXTEND_NEW, ContextMenuOption.EXTEND_REV).forEach { option ->
            withExtendLimitsDialog(option) { dialog ->
                onView(withText("Extend daily limits"))
                    .inRoot(isDialog())
                    .check(matches(isDisplayed()))
                assertThat("$option positive button", dialog.positiveButton.text.toString(), equalTo("Increase"))
            }
        }
    }

    @Test
    @Config(qualifiers = "en")
    fun `'extend limits' descriptions do not mention decreasing the limit`() {
        listOf(ContextMenuOption.EXTEND_NEW, ContextMenuOption.EXTEND_REV).forEach { option ->
            withExtendLimitsDialog(option) {
                assertThat("$option description", binding.detailsText2.text.toString(), not(containsString("decrease")))
            }
        }
    }

    @Test
    fun `'extend limits' dialogs show the available cards below the input`() {
        mapOf(
            ContextMenuOption.EXTEND_NEW to { defaultsOfDefaultDeck.labelForNewQueueAvailable() },
            ContextMenuOption.EXTEND_REV to { defaultsOfDefaultDeck.labelForReviewQueueAvailable() },
        ).forEach { (option, expectedLabel) ->
            withExtendLimitsDialog(option) {
                val root = binding.root
                val availableCards =
                    assertNotNull(
                        root.children.firstOrNull { it is TextView && it.isVisible && it.text.toString() == expectedLabel() },
                        "$option available cards label",
                    )
                val input =
                    generateSequence<View>(binding.detailsEditText2Layout) { it.parent as? View }
                        .first { it.parent == root }
                assertThat(
                    "$option available cards label is below the input",
                    root.indexOfChild(availableCards),
                    greaterThan(root.indexOfChild(input)),
                )
            }
        }
    }

    @Test
    fun `'extend limits' steppers change the value by 1`() {
        listOf(ContextMenuOption.EXTEND_NEW, ContextMenuOption.EXTEND_REV).forEach { option ->
            withExtendLimitsDialog(option) {
                val initial = assertNotNull(binding.detailsEditText2.textAsIntOrNull(), "$option initial value")

                onStepper(CommonString.plus_sign).perform(click())
                assertThat("$option after +", binding.detailsEditText2.text.toString(), equalTo("${initial + 1}"))

                onStepper(CommonString.minus_sign).perform(click())
                onStepper(CommonString.minus_sign).perform(click())
                assertThat("$option after -", binding.detailsEditText2.text.toString(), equalTo("${initial - 1}"))
            }
        }
    }

    @Test
    fun `'extend limits' stepper treats an empty value as 0`() {
        withExtendLimitsDialog(ContextMenuOption.EXTEND_NEW) {
            onSubscreenEditText().perform(replaceText(""))
            onStepper(CommonString.plus_sign).perform(click())
            assertThat(binding.detailsEditText2.text.toString(), equalTo("1"))
        }
    }

    @Test
    fun `steppers are not shown outside of 'extend limits'`() {
        withCustomStudyFragment(args = argumentsDisplayingSubscreen(ContextMenuOption.STUDY_FORGOT)) {
            shadowOf(Looper.getMainLooper()).idle()
            onView(allOf(withContentDescription(CommonString.plus_sign), isDisplayed()))
                .inRoot(isDialog())
                .check(doesNotExist())
            onView(allOf(withContentDescription(CommonString.minus_sign), isDisplayed()))
                .inRoot(isDialog())
                .check(doesNotExist())
        }
    }

    @Test
    fun `boundary values validate correctly across study modes`() {
        // STUDY_FORGOT boundary values: 0 (invalid), 1 (valid), 30 (valid), 31 (invalid)
        withCustomStudyFragment(args = argumentsDisplayingSubscreen(ContextMenuOption.STUDY_FORGOT)) { fragment ->
            val dialog = fragment.dialog as AlertDialog

            onSubscreenEditText().perform(replaceText("0"))
            assertThat("0 in STUDY_FORGOT is disabled", dialog.positiveButton.isEnabled, equalTo(false))
            assertThat(
                "0 error in STUDY_FORGOT",
                fragment.binding.detailsEditText2Layout.error
                    ?.toString(),
                containsString("1"),
            )

            onSubscreenEditText().perform(replaceText("1"))
            assertThat("1 in STUDY_FORGOT is enabled", dialog.positiveButton.isEnabled, equalTo(true))
            assertThat("1 error in STUDY_FORGOT is null", fragment.binding.detailsEditText2Layout.error, nullValue())

            onSubscreenEditText().perform(replaceText("30"))
            assertThat("30 in STUDY_FORGOT is enabled", dialog.positiveButton.isEnabled, equalTo(true))
            assertThat("30 error in STUDY_FORGOT is null", fragment.binding.detailsEditText2Layout.error, nullValue())

            onSubscreenEditText().perform(replaceText("31"))
            assertThat("31 in STUDY_FORGOT is disabled", dialog.positiveButton.isEnabled, equalTo(false))
            assertThat(
                "31 error in STUDY_FORGOT",
                fragment.binding.detailsEditText2Layout.error
                    ?.toString(),
                containsString("30"),
            )
        }

        // EXTEND_NEW boundary values: -99999 (valid), 0 (invalid), 99999 (valid), 100000 (invalid)
        withExtendLimitsDialog(ContextMenuOption.EXTEND_NEW) { dialog ->
            onSubscreenEditText().perform(replaceText("-99999"))
            assertThat("-99999 in EXTEND_NEW is enabled", dialog.positiveButton.isEnabled, equalTo(true))
            assertThat("-99999 error is null", binding.detailsEditText2Layout.error, nullValue())

            onSubscreenEditText().perform(replaceText("0"))
            assertThat("0 in EXTEND_NEW is disabled", dialog.positiveButton.isEnabled, equalTo(false))

            onSubscreenEditText().perform(replaceText("99999"))
            assertThat("99999 in EXTEND_NEW is enabled", dialog.positiveButton.isEnabled, equalTo(true))
            assertThat("99999 error is null", binding.detailsEditText2Layout.error, nullValue())

            onSubscreenEditText().perform(replaceText("100000"))
            assertThat("100000 in EXTEND_NEW is disabled", dialog.positiveButton.isEnabled, equalTo(false))
            assertThat("100000 error in EXTEND_NEW", binding.detailsEditText2Layout.error?.toString(), containsString("99999"))
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
                    REQUEST_SELECT_TAGS,
                    Bundle().apply { putStringArrayList(RESULT_SELECTED_TAGS, selectedTags) },
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
        addBasicNote()
        withCustomStudyFragment(args = argumentsDisplayingSubscreen(ContextMenuOption.EXTEND_REV, restoreFromProcessDeath = true)) {
            // ensure we're on the main screen
            onView(withText(TR.customStudyIncreaseTodaysReviewCardLimit()))
                .inRoot(isDialog())
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun `tags chosen after recreation start a custom study session`() =
        runTest {
            addBasicNote().update { addTag("testTag") }

            launchFragment<Fragment>().use { scenario ->
                scenario.onFragment {
                    it.requireActivity().showDialogFragment(CustomStudyDialog.createInstance(Consts.DEFAULT_DECK_ID))
                }
                onView(withText(TR.customStudyStudyByCardStateOrTag()))
                    .inRoot(isDialog())
                    .perform(scrollTo(), click())

                scenario.recreate()

                scenario.onFragment {
                    it.parentFragmentManager.setFragmentResult(
                        REQUEST_SELECT_TAGS,
                        Bundle().apply { putStringArrayList(RESULT_SELECTED_TAGS, arrayListOf("testTag")) },
                    )
                }
                assertNotNull(col.decks.customStudySession)
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

    @Test
    @Config(qualifiers = "en")
    fun `'review ahead' shows a day or days suffix`() {
        addBasicNote()
        withStudyAheadDialog {
            val layout = binding.detailsEditText2Layout
            assertThat("default of 1 day", layout.suffixText.toString(), equalTo("day"))

            onSubscreenEditText().perform(replaceText("2"))
            assertThat(layout.suffixText.toString(), equalTo("days"))

            onSubscreenEditText().perform(replaceText("1"))
            assertThat(layout.suffixText.toString(), equalTo("day"))
        }
    }

    @Test
    fun `'review ahead' rejects leading zeros and more than 5 digits`() {
        addBasicNote()
        withStudyAheadDialog {
            val editText = binding.detailsEditText2

            onSubscreenEditText().perform(replaceText("0"))
            editText.append("5")
            assertThat("a digit typed after '0' is rejected", editText.text.toString(), equalTo("0"))

            onSubscreenEditText().perform(replaceText("123456"))
            assertThat(editText.text.toString(), equalTo("12345"))
        }
    }

    @Test
    fun `'review ahead' disables Create when no cards are due in the period`() =
        runTest {
            // a card due tomorrow matches 'review ahead by 1 day'
            val card = addBasicNote().firstCard()
            card.update {
                queue = QueueType.Rev
                type = CardType.Rev
                due = col.sched.today + 1
            }

            withStudyAheadDialog { dialog ->
                val layout = binding.detailsEditText2Layout

                onSubscreenEditText().perform(replaceText("1"))
                shadowOf(Looper.getMainLooper()).idle()
                assertThat("enabled when a card matches", dialog.positiveButton.isEnabled, equalTo(true))
                assertNull(layout.error)

                onSubscreenEditText().perform(replaceText("0"))
                shadowOf(Looper.getMainLooper()).idle()
                assertThat("disabled for 0 days", dialog.positiveButton.isEnabled, equalTo(false))

                card.update { due = col.sched.today + 30 }

                onSubscreenEditText().perform(replaceText("1"))
                shadowOf(Looper.getMainLooper()).idle()
                assertThat("disabled when nothing matches", dialog.positiveButton.isEnabled, equalTo(false))
                assertThat(layout.error?.toString(), equalTo(TR.customStudyNoCardsMatchedTheCriteriaYou()))
            }
        }

    @Test
    fun `'review ahead' search does not treat the deck name as a pattern`() =
        runTest {
            // '_' is a single-character wildcard in a search: "A_B" must not match "AXB"
            val deckId = addDeck("A_B")
            addBasicNote().update { moveToDeck("A_B", false) }.firstCard().update {
                queue = QueueType.Rev
                type = CardType.Rev
                due = col.sched.today + 30
            }
            addDeck("AXB")
            addNoteDueTomorrow(deckName = "AXB")

            withStudyAheadDialog(deckId = deckId) { dialog ->
                onSubscreenEditText().perform(replaceText("1"))
                shadowOf(Looper.getMainLooper()).idle()
                assertThat("A_B has no cards due tomorrow", dialog.positiveButton.isEnabled, equalTo(false))
            }
        }

    @Test
    fun `'review ahead' search escapes the deck name`() =
        runTest {
            // '\' starts an escape sequence in a search
            val deckId = addDeck("""A\B""")
            addNoteDueTomorrow(deckName = """A\B""")

            withStudyAheadDialog(deckId = deckId) { dialog ->
                onSubscreenEditText().perform(replaceText("1"))
                shadowOf(Looper.getMainLooper()).idle()
                assertThat(dialog.positiveButton.isEnabled, equalTo(true))
            }
        }

    @Test
    @Config(qualifiers = "en")
    fun `'review ahead' warns when 0 days is entered`() =
        runTest {
            addBasicNote()
            withStudyAheadDialog {
                val layout = binding.detailsEditText2Layout

                onSubscreenEditText().perform(replaceText("0"))
                shadowOf(Looper.getMainLooper()).idle()
                assertThat(layout.error?.toString(), equalTo("Minimum value is 1"))

                onSubscreenEditText().perform(replaceText(""))
                shadowOf(Looper.getMainLooper()).idle()
                assertNull(layout.error, "an empty field is not an error yet")
            }
        }

    @Test
    fun `'review ahead' validates the default value on open`() =
        runTest {
            // the default is 1 day; the only card is due in 30 days
            addBasicNote().firstCard().update {
                queue = QueueType.Rev
                type = CardType.Rev
                due = col.sched.today + 30
            }

            withStudyAheadDialog { dialog ->
                assertThat("nothing to review ahead", dialog.positiveButton.isEnabled, equalTo(false))
                assertThat(binding.detailsEditText2Layout.error?.toString(), equalTo(TR.customStudyNoCardsMatchedTheCriteriaYou()))
            }
        }

    @Test
    fun `typed value is kept after a dialog on top closes - issue 22731`() =
        withTypedInputCoveredByAnotherDialog(ContextMenuOption.EXTEND_NEW, typed = "7") {
            onSubscreenEditText().check(matches(withText("7")))
        }

    @Test
    fun `'preview' positive button is disabled on open when no cards were added recently`() =
        runTest {
            addBasicNote()
            col.db.execute(
                "UPDATE cards SET id = id - ${40L * 24 * 60 * 60 * 1000} WHERE id > 0",
            )

            withStudyPreviewDialog { dialog ->
                assertThat(
                    "disabled on open when nothing matches",
                    dialog.positiveButton.isEnabled,
                    equalTo(false),
                )
                assertThat(
                    binding.detailsEditText2Layout.error?.toString(),
                    equalTo(TR.customStudyNoCardsMatchedTheCriteriaYou()),
                )
            }
        }

    @Test
    @Config(qualifiers = "en")
    fun `'review ahead' suffix matches the typed value after a dialog on top closes`() =
        withTypedInputCoveredByAnotherDialog(ContextMenuOption.STUDY_AHEAD, typed = "2") {
            assertThat(binding.detailsEditText2Layout.suffixText.toString(), equalTo("days"))
        }

    @Test
    @Config(qualifiers = "en")
    fun `'preview' shows a day or days suffix`() {
        withStudyPreviewDialog {
            val layout = binding.detailsEditText2Layout
            assertThat("default of 1 day", layout.suffixText.toString(), equalTo("day"))

            onSubscreenEditText().perform(replaceText("2"))
            assertThat(layout.suffixText.toString(), equalTo("days"))

            onSubscreenEditText().perform(replaceText("1"))
            assertThat(layout.suffixText.toString(), equalTo("day"))
        }
    }

    @Test
    @Config(qualifiers = "en")
    fun `'preview' positive button text is 'Create'`() {
        withStudyPreviewDialog { dialog ->
            assertThat(dialog.positiveButton.text.toString(), equalTo("Create"))
        }
    }

    @Test
    fun `'preview' and 'review ahead' hide the empty first label`() {
        withStudyPreviewDialog {
            assertThat("preview", binding.detailsText1.isVisible, equalTo(false))
        }
        withStudyAheadDialog {
            assertThat("review ahead", binding.detailsText1.isVisible, equalTo(false))
        }
    }

    @Test
    fun `'preview' disables Create when no cards match the criteria`() =
        runTest {
            addBasicNote()

            withStudyPreviewDialog { dialog ->
                onSubscreenEditText().perform(replaceText("1"))
                shadowOf(Looper.getMainLooper()).idle()
                assertThat("enabled for a recently added card", dialog.positiveButton.isEnabled, equalTo(true))
                assertThat(binding.detailsEditText2Layout.error, nullValue())

                onSubscreenEditText().perform(replaceText("0"))
                shadowOf(Looper.getMainLooper()).idle()
                assertThat("disabled for 0 days", dialog.positiveButton.isEnabled, equalTo(false))
            }
        }

    @Test
    fun `'review ahead' validates the default value on open - shared path guard`() =
        runTest {
            addBasicNote().firstCard().update {
                queue = QueueType.Rev
                type = CardType.Rev
                due = col.sched.today + 30
            }

            withStudyAheadDialog { dialog ->
                assertThat("review-ahead: disabled when nothing matches", dialog.positiveButton.isEnabled, equalTo(false))
            }
        }

    /** Adds a note to [deckName] whose card is due tomorrow */
    private fun addNoteDueTomorrow(deckName: String) {
        addBasicNote().update { moveToDeck(deckName, false) }.firstCard().update {
            queue = QueueType.Rev
            type = CardType.Rev
            due = col.sched.today + 1
        }
    }

    /**
     * Runs [block] on a [CustomStudyDialog]
     */
    private fun withCustomStudyFragment(
        args: Bundle,
        block: (CustomStudyDialog) -> Unit,
    ) {
        createHostActivity().use { controller ->
            val fragment = CustomStudyDialog().apply { arguments = args }
            controller.get().showDialogFragment(fragment)
            val start = System.nanoTime()
            while (fragment.dialog?.findViewById<View>(R.id.progress_bar)?.isVisible == true) {
                check(System.nanoTime() - start < TimeUnit.SECONDS.toNanos(10)) { "Custom Study did not finish loading" }
                Thread.sleep(10)
                shadowOf(Looper.getMainLooper()).idleFor(16, TimeUnit.MILLISECONDS)
            }
            shadowOf(Looper.getMainLooper()).idleFor(300, TimeUnit.MILLISECONDS)
            block(fragment)
        }
    }

    /** Opens the 'review ahead' subscreen for [deckId] and runs [block] once the dialog is shown and its buttons exist */
    private fun withStudyAheadDialog(
        deckId: DeckId = Consts.DEFAULT_DECK_ID,
        block: CustomStudyDialog.(dialog: AlertDialog) -> Unit,
    ) = withCustomStudyFragment(args = argumentsDisplayingSubscreen(ContextMenuOption.STUDY_AHEAD, deckId = deckId)) { fragment ->
        shadowOf(Looper.getMainLooper()).idle()
        fragment.block(fragment.dialog as AlertDialog)
    }

    private fun withTypedInputCoveredByAnotherDialog(
        option: ContextMenuOption,
        typed: String,
        block: CustomStudyDialog.() -> Unit,
    ) {
        addBasicNote()
        val customStudy = CustomStudyDialog().apply { arguments = argumentsDisplayingSubscreen(option) }
        Robolectric.buildActivity(FragmentActivity::class.java).use { controller ->
            controller.get().setTheme(CommonR.style.Theme_AnkiDroid)
            val activity = controller.setup().get()
            activity.showDialogFragment(customStudy)
            onSubscreenEditText().perform(replaceText(typed))

            val dialogOnTop = DialogFragment()
            activity.showDialogFragment(dialogOnTop)
            shadowOf(Looper.getMainLooper()).idle()
            dialogOnTop.dismiss()
            shadowOf(Looper.getMainLooper()).idle()

            customStudy.block()
        }
    }

    /** Opens the 'preview' subscreen for [deckId] and runs [block] once the dialog is shown */
    private fun withStudyPreviewDialog(
        deckId: DeckId = Consts.DEFAULT_DECK_ID,
        block: CustomStudyDialog.(dialog: AlertDialog) -> Unit,
    ) = withCustomStudyFragment(args = argumentsDisplayingSubscreen(ContextMenuOption.STUDY_PREVIEW, deckId = deckId)) { fragment ->
        shadowOf(Looper.getMainLooper()).idle()
        fragment.block(fragment.dialog as AlertDialog)
    }

    private fun withExtendLimitsDialog(
        option: ContextMenuOption,
        block: CustomStudyDialog.(dialog: AlertDialog) -> Unit,
    ) {
        // Main dismisses custom study when the deck is empty. These dialogs need a card so the
        // subscreen is shown instead of the empty-deck snackbar.
        addBasicNote()
        withCustomStudyFragment(args = argumentsDisplayingSubscreen(option)) { fragment ->
            shadowOf(Looper.getMainLooper()).idle()
            fragment.block(fragment.dialog as AlertDialog)
        }
    }

    private fun mockCollectionWithSchedulerReturning(response: CustomStudyDefaultsResponse): Collection {
        val tree = col.sched.deckTree(includeCounts = false)
        return mockk<Collection>(relaxed = true) {
            every { decks.cardCount(Consts.DEFAULT_DECK_ID, includeSubdecks = true) } returns 1
            every { sched } returns
                mockk<Scheduler> {
                    every { deckTree(includeCounts = false) } returns tree
                    every { customStudyDefaults(Consts.DEFAULT_DECK_ID) } returns response
                }
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

    private fun createHostActivity() =
        Robolectric.buildActivity(EmptyAnkiActivity::class.java).setup().apply {
            val activity = get()
            activity.setContentView(CoordinatorLayout(activity).apply { id = R.id.root_layout })
        }

    private fun assertUnavailableMenu(fragment: CustomStudyDialog) {
        shadowOf(Looper.getMainLooper()).idleFor(300, TimeUnit.MILLISECONDS)
        assertFalse(fragment.dialog?.isShowing == true)
        onView(withId(com.google.android.material.R.id.snackbar_text))
            .check(matches(allOf(isDisplayed(), withText(CommonString.empty_deck))))
    }

    private fun onStepper(contentDescription: Int) =
        onView(withContentDescription(contentDescription))
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

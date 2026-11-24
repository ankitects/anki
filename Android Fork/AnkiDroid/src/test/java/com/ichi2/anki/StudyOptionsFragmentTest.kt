// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki

import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.view.menu.MenuBuilder
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.commit
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.databinding.FragmentStudyOptionsBinding
import com.ichi2.anki.libanki.Consts.DEFAULT_DECK_ID
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.QueueType
import com.ichi2.anki.observability.undoableOp
import com.ichi2.anki.progress.ViewModelProgress
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.testutils.ext.menu
import com.ichi2.testutils.ext.triggerDeckNotFoundInLimitsMap
import com.ichi2.testutils.launchFragmentInContainer
import com.ichi2.testutils.withFragment
import com.ichi2.utils.neutralButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Tests for [StudyOptionsFragment].
 */
@RunWith(AndroidJUnit4::class)
class StudyOptionsFragmentTest : RobolectricTest() {
    /** Issue 22249: backend deletion can leave the tablet showing hidden Default. */
    @Test
    @Config(qualifiers = "xlarge")
    fun `tablet shows empty Default after backend deletion and Undo restores custom study`() {
        val keep = addDeck("Keep")
        col.setDeck(listOf(addBasicNote().firstCard().id), keep)
        val deleted = addDeck("Delete me", setAsSelected = true)
        col.setDeck(listOf(addBasicNote().firstCard().id), deleted)

        deckPicker {
            advanceRobolectricLooperUntil { fragment?.viewModel?.selectedDeckId == deleted }
            undoableOp { decks.remove(listOf(deleted)) }
            advanceRobolectricLooperUntil { fragment?.viewModel?.state is StudyOptionsState.Empty }
            advanceRobolectricLooper()

            assertEquals(DEFAULT_DECK_ID, col.decks.selected())
            assertEquals(DEFAULT_DECK_ID, fragment!!.viewModel.selectedDeckId)
            assertEquals("Default", assertIs<StudyOptionsState.Empty>(fragment!!.viewModel.state).deckName)
            assertNull(col.sched.deckDueTree().find(DEFAULT_DECK_ID))
            val undoStatus = col.undoStatus()
            val menu = menu()
            fragment!!.onPrepareMenu(menu)
            updateMenuFromState(menu)
            assertFalse(assertNotNull(menu.findItem(R.id.action_custom_study)).isVisible)
            assertTrue(assertNotNull(menu.findItem(R.id.action_deck_or_study_options)).isVisible)
            assertEquals(undoStatus, col.undoStatus())

            undoAndShowSnackbar()
            advanceRobolectricLooperUntil { fragment?.viewModel?.selectedDeckId == deleted }
            assertIs<StudyOptionsState.StudyOptions>(fragment!!.viewModel.state)
            fragment!!.onPrepareMenu(menu)
            assertTrue(menu.findItem(R.id.action_custom_study).isVisible)
            assertNotNull(col.undoStatus().redo)

            redoAndShowSnackbar()
            advanceRobolectricLooperUntil { fragment?.viewModel?.state is StudyOptionsState.Empty }
            fragment!!.onPrepareMenu(menu)
            assertFalse(menu.findItem(R.id.action_custom_study).isVisible)
            assertEquals(DEFAULT_DECK_ID, fragment!!.viewModel.selectedDeckId)
        }
    }

    @Test
    fun `empty filtered deck shows counts after a finished regular deck`() =
        runTest {
            val note = addBasicNote()
            col.sched.suspendCards(note.cardIds(col))
            withStudyOptions {
                assertIs<StudyOptionsState.Congrats>(viewModel.state)
                val counts = requireView().findViewById<View>(R.id.group_counts)
                assertEquals(View.GONE, counts.visibility)

                col.decks.select(col.decks.newFiltered("Filtered deck"))
                refreshAndAwait()

                assertTrue(assertIs<StudyOptionsState.Congrats>(viewModel.state).isDynamic)
                assertEquals(View.VISIBLE, counts.visibility)
            }
        }

    @Test
    fun `bury counts stay hidden without buried cards`() =
        runTest {
            addBasicNote()
            withStudyOptions {
                assertIs<StudyOptionsState.StudyOptions>(viewModel.state)
                assertBuryCountsHidden()

                reattachView()
                assertBuryCountsHidden()
            }
        }

    @Test
    fun `bury count is shown next to the counts`() =
        runTest {
            addBasicAndReversedNote("Front", "Back")
            val config = col.decks.configDictForDeckId(col.decks.current().id)
            config.new.bury = true
            col.decks.save(config)
            withStudyOptions {
                assertIs<StudyOptionsState.StudyOptions>(viewModel.state)
                val newBury = binding.studyoptionsNewBury
                assertEquals(View.VISIBLE, newBury.visibility)

                reattachView()
                assertEquals(View.VISIBLE, newBury.visibility)
                assertEquals("+1 buried", newBury.text.toString())
            }
        }

    @Test
    fun `scroll position is retained on refresh and reset for another deck`() =
        runTest {
            val anotherDeck = addDeck("Another deck")
            withStudyOptions {
                val scroll = requireView().findViewById<ScrollView>(R.id.studyoptions_scrollview)
                // Give the pane scrollable content without depending on font metrics.
                scroll.getChildAt(0).minimumHeight = 2000
                scroll.measure(MeasureSpec.makeMeasureSpec(400, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(300, MeasureSpec.EXACTLY))
                scroll.layout(0, 0, 400, 300)
                scroll.scrollTo(0, 200)
                assertEquals(200, scroll.scrollY)

                addBasicNote()
                refreshAndAwait()
                assertEquals(200, scroll.scrollY)

                col.decks.select(anotherDeck)
                refreshAndAwait()
                assertEquals(0, scroll.scrollY)
            }
        }

    @Test
    fun `returning from another panel keeps a single progress observer`() =
        runTest {
            withStudyOptions {
                val firstView = requireView()

                parentFragmentManager.commit {
                    replace(android.R.id.content, Fragment())
                    addToBackStack(null)
                }
                parentFragmentManager.executePendingTransactions()
                parentFragmentManager.popBackStackImmediate()

                assertNotSame(firstView, requireView())
                val progress = viewModel.progressManager.progress as MutableStateFlow<*>
                assertEquals(1, progress.subscriptionCount.value)
            }
        }

    /** Issue 22348: replacing the tablet panel can detach it before its initial refresh starts. */
    @Test
    @Config(qualifiers = "xlarge")
    fun `replacing study options before its refresh starts skips the stale refresh`() =
        runTest {
            setIntroductionSlidesShown(true)
            addBasicNote()
            val controller = Robolectric.buildActivity(DeckPicker::class.java).setup()
            saveControllerForCleanup(controller)
            advanceUntilIdle()
            advanceRobolectricLooper()
            val activity = controller.get()
            assertTrue(activity.fragmented)
            val removedFragment = StudyOptionsFragment()
            val replacement = StudyOptionsFragment()
            lateinit var removedViewModel: StudyOptionsViewModel

            activity.lifecycleScope
                .launch {
                    // Main.immediate queues the nested refresh until this coroutine yields.
                    activity.supportFragmentManager.commitNow {
                        replace(R.id.studyoptions_fragment, removedFragment)
                    }
                    removedViewModel = removedFragment.viewModel
                    assertIs<StudyOptionsState.Loading>(removedViewModel.state)
                    activity.supportFragmentManager.commitNow {
                        replace(R.id.studyoptions_fragment, replacement)
                    }
                }.join()
            advanceUntilIdle()
            advanceRobolectricLooper()

            assertNull(removedFragment.activity)
            assertFalse(activity.isFinishing)
            assertIs<StudyOptionsState.Loading>(removedViewModel.state)
            assertSame(replacement, activity.fragment)
            assertEquals(Lifecycle.State.RESUMED, replacement.lifecycle.currentState)
            val state = assertIs<StudyOptionsState.StudyOptions>(replacement.viewModel.state)
            assertEquals(1, state.data.newCardsToday)
            val newCount = replacement.requireView().findViewById<TextView>(R.id.studyoptions_new_count)
            assertEquals("1", newCount.text.toString())
        }

    @Test
    fun `study options refresh queued before finishing exposes lifecycle misuse`() =
        assertLifecycleFailure("activity is finishing") {
            addBasicNote()
            val controller = Robolectric.buildActivity(StudyOptionsActivity::class.java).setup()
            saveControllerForCleanup(controller)
            advanceUntilIdle()
            advanceRobolectricLooper()
            val activity = controller.get()
            val fragment = assertIs<StudyOptionsFragment>(activity.supportFragmentManager.findFragmentById(R.id.studyoptions_frame))
            val initialState = assertIs<StudyOptionsState.StudyOptions>(fragment.viewModel.state)
            assertEquals(1, initialState.data.newCardsToday)
            addBasicNote()

            activity.lifecycleScope
                .launch {
                    fragment.refreshInterface()
                    activity.finish()
                }.join()
            advanceUntilIdle()
            advanceRobolectricLooper()

            assertTrue(activity.isFinishing)
            assertSame(activity, fragment.activity)
            assertSame(initialState, fragment.viewModel.state)
        }

    /** Issue 22065: the host can replace the fragment before its queued refresh starts. */
    @Test
    fun `refresh queued by view creation is skipped after synchronous replacement`() {
        withNullCollection {
            val controller = Robolectric.buildActivity(FragmentActivity::class.java).setup()
            saveControllerForCleanup(controller)
            val activity = controller.get()
            val first = StudyOptionsFragment()
            val second = StudyOptionsFragment()

            activity.lifecycleScope.launch {
                activity.supportFragmentManager.commitNow { replace(android.R.id.content, first) }
                activity.supportFragmentManager.commitNow { replace(android.R.id.content, second) }
            }
            advanceRobolectricLooper()

            assertNull(first.activity)
            assertSame(second, activity.supportFragmentManager.findFragmentById(android.R.id.content))
            assertTrue(second.isAdded)
        }
    }

    /** Issue 21981: a refresh must offer recovery when the deck hierarchy is corrupt. */
    @Test
    fun `corrupt deck hierarchy offers Check database instead of crashing`() =
        runTest {
            triggerDeckNotFoundInLimitsMap()
            throwOnShowError = false

            withStudyOptions {
                val dialog = assertIs<AlertDialog>(ShadowDialog.getLatestDialog())
                assertTrue(dialog.isShowing)
                assertEquals("deck not found in limits map", getAlertDialogText(checkDismissed = true))
                assertEquals(TR.sentenceCase.checkDatabase, dialog.neutralButton?.text)

                dialog.dismiss()
                col.fixIntegrity()
                refreshAndAwait()
                val state = assertIs<StudyOptionsState.StudyOptions>(viewModel.state)
                assertEquals(1, state.data.newCardsToday)
            }
        }

    @Test
    fun `fragment reaches RESUMED with a closed collection`() {
        withNullCollection {
            launchFragmentInContainer<StudyOptionsFragment>(
                initialState = Lifecycle.State.RESUMED,
            ).use { scenario ->
                scenario.onFragment { fragment ->
                    assertEquals(Lifecycle.State.RESUMED, fragment.lifecycle.currentState)
                }
            }
        }
    }

    @Test
    fun `onResume after STARTED with a closed collection does not crash`() {
        withNullCollection {
            launchFragmentInContainer<StudyOptionsFragment>(
                initialState = Lifecycle.State.STARTED,
            ).use { scenario ->
                scenario.moveToState(Lifecycle.State.RESUMED)
                scenario.onFragment { fragment ->
                    assertEquals(Lifecycle.State.RESUMED, fragment.lifecycle.currentState)
                }
            }
        }
    }

    @Test
    fun `recreate with a closed collection survives lifecycle restart`() {
        withNullCollection {
            launchFragmentInContainer<StudyOptionsFragment>(
                initialState = Lifecycle.State.RESUMED,
            ).use { scenario ->
                scenario.recreate()
                scenario.onFragment { fragment ->
                    assertEquals(Lifecycle.State.RESUMED, fragment.lifecycle.currentState)
                }
            }
        }
    }

    @Test
    fun `onPrepareMenu does not crash with a closed collection from start`() {
        withNullCollection {
            launchFragmentInContainer<StudyOptionsFragment>(
                initialState = Lifecycle.State.RESUMED,
            ).use { scenario ->
                scenario.onFragment { fragment ->
                    val menu = MenuBuilder(fragment.requireContext())
                    fragment.onCreateMenu(menu, fragment.requireActivity().menuInflater)
                    fragment.onPrepareMenu(menu)
                }
            }
        }
    }

    @Test
    fun `onPrepareMenu does not crash when collection closes after population`() {
        col

        launchFragmentInContainer<StudyOptionsFragment>(
            initialState = Lifecycle.State.RESUMED,
        ).use { scenario ->
            scenario.onFragment { fragment ->
                runBlocking { fragment.viewModel.refreshData() }
                assertIs<StudyOptionsState.Empty>(fragment.viewModel.state)
            }
            withNullCollection {
                scenario.onFragment { fragment ->
                    @Suppress("RestrictedApi")
                    val menu = MenuBuilder(fragment.requireContext())
                    fragment.onCreateMenu(menu, fragment.requireActivity().menuInflater)
                    fragment.onPrepareMenu(menu)
                }
            }
        }
    }

    @Test
    fun `study options gradient spans from top to bottom of parent`() =
        runTest {
            withStudyOptions {
                val gradient = requireView().findViewById<View>(R.id.studyoptions_gradient)
                val params = gradient.layoutParams as ConstraintLayout.LayoutParams
                assertEquals(ConstraintLayout.LayoutParams.PARENT_ID, params.topToTop)
                assertEquals(ConstraintLayout.LayoutParams.PARENT_ID, params.bottomToBottom)
                assertEquals(ConstraintLayout.LayoutParams.PARENT_ID, params.startToStart)
            }
        }

    @Test
    fun `shadow_left drawable mirrors in RTL`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val ltrConfig =
            Configuration(context.resources.configuration).apply {
                setLayoutDirection(Locale.US)
            }
        val ltrContext = context.createConfigurationContext(ltrConfig)
        val ltrDrawable =
            AppCompatResources.getDrawable(ltrContext, R.drawable.shadow_left) as GradientDrawable
        assertEquals(GradientDrawable.Orientation.LEFT_RIGHT, ltrDrawable.orientation)

        val rtlConfig =
            Configuration(context.resources.configuration).apply {
                setLayoutDirection(Locale.forLanguageTag("ar"))
            }
        val rtlContext = context.createConfigurationContext(rtlConfig)
        val rtlDrawable =
            AppCompatResources.getDrawable(rtlContext, R.drawable.shadow_left) as GradientDrawable
        assertEquals(GradientDrawable.Orientation.RIGHT_LEFT, rtlDrawable.orientation)
    }

    @Test
    @Config(qualifiers = "xlarge")
    fun `Empty acts on the shown deck before a refresh redraws the panel`() {
        val (shown, other) = addFilteredDecksWithACardEach()

        withPanelBehindSelection(
            shown,
            other,
            whileBehind = { selectPanelItem(R.id.action_empty) },
            doneWhen = { col.decks.cardCount(shown) + col.decks.cardCount(other) == 1 },
        )

        assertEquals(0, col.decks.cardCount(shown))
        assertEquals(1, col.decks.cardCount(other))
    }

    @Test
    @Config(qualifiers = "xlarge")
    fun `Rebuild acts on the shown deck before a refresh redraws the panel`() {
        val (shown, other) = addFilteredDecksWithACardEach()
        col.sched.emptyFilteredDeck(shown)
        col.sched.emptyFilteredDeck(other)

        withPanelBehindSelection(
            shown,
            other,
            whileBehind = { selectPanelItem(R.id.action_rebuild) },
            doneWhen = { col.decks.cardCount(shown) + col.decks.cardCount(other) == 1 },
        )

        assertEquals(1, col.decks.cardCount(shown))
        assertEquals(0, col.decks.cardCount(other))
    }

    @Test
    @Config(qualifiers = "xlarge")
    fun `Unbury acts on the shown deck before a refresh redraws the panel`() {
        val shown = addDeck("Shown", setAsSelected = true)
        val other = addDeck("Other")
        col.sched.deckDueTree()
        addNoteToDeck(shown, queueType = QueueType.ManuallyBuried)
        addNoteToDeck(other, queueType = QueueType.ManuallyBuried)
        val queueIn = { name: String -> col.getCard(col.findCards("deck:$name").single()).queue }

        withPanelBehindSelection(
            shown,
            other,
            whileBehind = { selectPanelItem(R.id.action_unbury) },
            doneWhen = { queueIn("Shown") == QueueType.New || queueIn("Other") == QueueType.New },
        )

        assertEquals(QueueType.New, queueIn("Shown"))
        assertEquals(QueueType.ManuallyBuried, queueIn("Other"))
    }

    @Test
    @Config(qualifiers = "xlarge")
    fun `Rebuild and Empty stay hidden until the panel shows the filtered deck`() {
        val shown = addDeck("Shown", setAsSelected = true)
        addNoteToDeck(shown)
        addNoteToDeck(addDeck("OtherHome"))
        val other = addDynamicDeck("Other", search = "deck:OtherHome")
        col.decks.select(shown)

        withPanelBehindSelection(
            shown,
            other,
            whileBehind = {
                val menu = menu()
                fragment!!.onPrepareMenu(menu)
                assertFalse(menu.findItem(R.id.action_rebuild).isVisible)
                assertFalse(menu.findItem(R.id.action_empty).isVisible)
            },
            doneWhen = { true },
        )
    }

    @Test
    @Config(qualifiers = "xlarge")
    fun `Rebuild offered for a filtered deck does nothing once the panel shows a regular deck`() =
        assertFilteredActionIgnoredAfterRedraw(R.id.action_rebuild)

    @Test
    @Config(qualifiers = "xlarge")
    fun `Empty offered for a filtered deck does nothing once the panel shows a regular deck`() =
        assertFilteredActionIgnoredAfterRedraw(R.id.action_empty)

    private fun assertFilteredActionIgnoredAfterRedraw(itemId: Int) {
        addNoteToDeck(addDeck("Home"))
        val filtered = addDynamicDeck("Filtered", search = "deck:Home")
        val regular = addDeck("Regular")
        addNoteToDeck(regular)
        col.decks.select(filtered)

        withDeckPicker(deckCount = 0) { deckPicker ->
            advanceRobolectricLooperUntil {
                deckPicker.fragment?.shownDeckId == filtered &&
                    deckPicker.viewModel.loadDeckCounts?.isCompleted == true
            }
            advanceRobolectricLooper()
            val panel = assertNotNull(deckPicker.fragment)
            val menu = MenuBuilder(deckPicker)
            panel.onCreateMenu(menu, deckPicker.menuInflater)
            panel.onPrepareMenu(menu)
            val item = menu.findItem(itemId)
            assertTrue(item.isVisible)

            col.decks.select(regular)
            panel.refreshInterface()
            advanceRobolectricLooperUntil { panel.shownDeckId == regular }
            val undoStatus = col.undoStatus()

            panel.onMenuItemSelected(item)
            runBlocking { CollectionManager.withCol { } }
            advanceRobolectricLooperUntil { panel.viewModel.progressManager.progress.value is ViewModelProgress.Idle }
            advanceRobolectricLooper()

            assertEquals(undoStatus, col.undoStatus())
        }
    }

    private fun addFilteredDecksWithACardEach(): Pair<DeckId, DeckId> {
        addNoteToDeck(addDeck("ShownHome"))
        addNoteToDeck(addDeck("OtherHome"))
        val other = addDynamicDeck("Other", search = "deck:OtherHome")
        val shown = addDynamicDeck("Shown", search = "deck:ShownHome")
        col.decks.select(shown)
        return shown to other
    }

    private fun withPanelBehindSelection(
        shown: DeckId,
        selected: DeckId,
        whileBehind: DeckPicker.() -> Unit,
        doneWhen: DeckPicker.() -> Boolean,
    ) = withDeckPicker(deckCount = 0) { deckPicker ->
        advanceRobolectricLooperUntil {
            deckPicker.fragment?.shownDeckId == shown &&
                deckPicker.viewModel.loadDeckCounts?.isCompleted == true
        }
        advanceRobolectricLooper()
        val panel = assertNotNull(deckPicker.fragment)
        col.decks.select(selected)
        val published = CountDownLatch(1)
        val release = CountDownLatch(1)
        val observer =
            CoroutineScope(Dispatchers.Unconfined).launch {
                panel.viewModel.flowOfSelectedDeckId.collect { id ->
                    if (id == selected) {
                        published.countDown()
                        check(release.await(10, TimeUnit.SECONDS)) { "Timed out waiting to release the study options refresh" }
                    }
                }
            }
        val previousQueue = CollectionManager.setTestDispatcher(Dispatchers.IO.limitedParallelism(1), useReentrantLock = false)
        val refresh = thread(name = "study-options-refresh") { runBlocking { panel.viewModel.refreshData() } }
        try {
            assertTrue(published.await(5, TimeUnit.SECONDS), "The refresh did not read the selected deck")
            assertEquals(shown, panel.shownDeckId)
            deckPicker.whileBehind()
            release.countDown()
            refresh.join()
            advanceRobolectricLooperUntil {
                deckPicker.doneWhen() && panel.viewModel.progressManager.progress.value is ViewModelProgress.Idle
            }
            runBlocking { CollectionManager.withCol { } }
            advanceRobolectricLooper()
        } finally {
            release.countDown()
            refresh.join()
            observer.cancel()
            CollectionManager.setTestDispatcher(previousQueue)
        }
    }

    private fun DeckPicker.selectPanelItem(itemId: Int) = fragment!!.onMenuItemSelected(menu().findItem(itemId))

    private fun assertLifecycleFailure(
        message: String,
        block: suspend TestScope.() -> Unit,
    ) {
        val failure = assertFailsWith<IllegalStateException> { runTest(testBody = block) }
        assertContains(failure.message.orEmpty(), message)
    }

    private fun TestScope.withStudyOptions(block: StudyOptionsFragment.() -> Unit) =
        launchFragmentInContainer<StudyOptionsFragment>().use { scenario ->
            advanceUntilIdle()
            advanceRobolectricLooper()
            scenario.withFragment(block)
        }

    private val StudyOptionsFragment.binding
        get() = FragmentStudyOptionsBinding.bind(requireView())

    private fun StudyOptionsFragment.assertBuryCountsHidden() =
        with(binding) {
            for (bury in listOf(studyoptionsNewBury, studyoptionsLearningBury, studyoptionsReviewBury)) {
                assertEquals(View.GONE, bury.visibility, resources.getResourceEntryName(bury.id))
            }
        }

    private fun StudyOptionsFragment.reattachView() {
        val view = requireView()
        val parent = view.parent as ViewGroup
        val index = parent.indexOfChild(view)
        parent.removeView(view)
        parent.addView(view, index)
    }

    context(scope: TestScope)
    private fun StudyOptionsFragment.refreshAndAwait() {
        refreshInterface()
        scope.advanceUntilIdle()
        advanceRobolectricLooper()
    }
}

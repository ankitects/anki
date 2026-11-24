// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Intent
import android.os.Looper
import android.view.View.MeasureSpec
import androidx.core.content.edit
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.deckpicker.DeckPickerViewModel
import com.ichi2.anki.deckpicker.DeckPickerViewModel.FlattenedDeckList
import com.ichi2.anki.widgets.DeckAdapter
import com.ichi2.testutils.BackupManagerTestUtilities
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(ParameterizedRobolectricTestRunner::class)
class DeckPickerScrollStateTest : RobolectricTest() {
    @ParameterizedRobolectricTestRunner.Parameter
    @JvmField
    var qualifiers: String = ""

    @Before
    fun prepareCollection() {
        RuntimeEnvironment.setQualifiers(qualifiers)
        setIntroductionSlidesShown(true)
        addBasicNote()
        repeat(80) { addDeck("Test Deck %02d".format(it)) }
        col.decks.select(1L)
        BackupManagerTestUtilities.setupSpaceForBackup(targetContext)
        InitialActivity.setUpgradedToLatestVersion(targetContext.sharedPrefs())
        getPreferences().edit { putBoolean("backupPromptDisabled", true) }
    }

    @After
    fun resetSpace() = BackupManagerTestUtilities.reset()

    @Test
    fun `recreation preserves the visible row and offset`() {
        val controller = launch()
        val viewModel = controller.get().viewModel
        scrollAwayFromSelection(controller.get())
        val position = visiblePosition(controller.get())

        repeat(2) {
            val previous = controller.get()
            controller.recreate()
            settle(controller.get())
            assertNotSame(previous, controller.get())
            assertSame(viewModel, controller.get().viewModel)
            assertEquals(position, visiblePosition(controller.get()))
        }
    }

    @Test
    fun `restoration waits for a delayed first list`() {
        val controller = launch()
        scrollAwayFromSelection(controller.get())
        val position = visiblePosition(controller.get())
        val viewModel = controller.get().viewModel
        val decks = viewModel.flowOfDeckList.value
        val focusedDeck = viewModel.flowOfFocusedDeck.value
        val delayedList = MutableStateFlow(FlattenedDeckList.empty)
        val delayedFocus = MutableStateFlow<Long?>(null)
        controller.pause().stop()
        mockkObject(viewModel)
        every { viewModel.flowOfDeckList } returns delayedList
        every { viewModel.flowOfFocusedDeck } returns delayedFocus
        try {
            controller
                .recreate()
                .start()
                .resume()
                .visible()
            viewModel.awaitStartup()
            advanceRobolectricLooper()
            assertEquals(0, list(controller.get()).adapter!!.itemCount)

            delayedList.value = decks
            delayedFocus.value = focusedDeck
            settle(controller.get())
            assertEquals(position, visiblePosition(controller.get()))
        } finally {
            unmockkObject(viewModel)
        }
    }

    @Test
    fun `resuming and refreshing the list preserves the visible position`() {
        val controller = launch()
        scrollAwayFromSelection(controller.get())
        val position = visiblePosition(controller.get())

        controller
            .pause()
            .stop()
            .start()
            .resume()
            .visible()
        val refresh = controller.get().viewModel.updateDeckList()
        advanceRobolectricLooperUntil { refresh.isCompleted }
        settle(controller.get())

        assertEquals(position, visiblePosition(controller.get()))
    }

    @Test
    fun `selecting another deck after recreation brings it into view`() {
        val controller = launch()
        scrollAwayFromSelection(controller.get())
        controller.recreate()
        settle(controller.get())

        val activity = controller.get()
        val target = col.decks.id("Test Deck 03")
        val selection = activity.viewModel.selectDeck(target)
        advanceRobolectricLooperUntil { selection.isCompleted }
        settle(activity)

        assertDeckVisible(activity, target)
    }

    @Test
    fun `pending selection scroll survives immediate recreation`() {
        // The tablet panel refresh adds a separate layout interaction; isolate this race on phones.
        assumeTrue("This reproduction uses the phone layout", qualifiers.startsWith("sw400"))
        val controller = launch()
        val original = controller.get()
        scrollAwayFromSelection(original)
        val target = col.decks.id("Test Deck 03")
        val selection = original.viewModel.selectDeck(target)

        // Deliver due callbacks without advancing the clock to the 10 ms scroll task.
        repeat(1000) {
            if (!selection.isCompleted) {
                shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(1)
            }
        }
        assertTrue(selection.isCompleted)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(target, original.viewModel.focusedDeck)
        assertEquals(30 to -17, visiblePosition(original))

        controller.recreate()
        settle(controller.get())

        assertDeckVisible(controller.get(), target)
    }

    @Test
    fun `returning to the previous selection before a pending scroll reveals it`() {
        val initial = col.decks.selected()
        val intermediate = col.decks.id("Test Deck 03")
        val activity = launch().get()
        val viewModel = activity.viewModel
        val adapter = list(activity).adapter as DeckAdapter
        val initialRefresh = viewModel.updateDeckList()
        advanceRobolectricLooperUntil {
            initialRefresh.isCompleted && adapter.itemCount == 81 &&
                adapter.currentList == viewModel.flowOfDeckList.value.data && viewModel.focusedDeck == initial
        }
        scrollAwayFromSelection(activity)

        deliverSelectionWithoutDelayedScroll(viewModel, intermediate)
        deliverSelectionWithoutDelayedScroll(viewModel, initial)
        advanceRobolectricLooperUntil {
            adapter.currentList == viewModel.flowOfDeckList.value.data &&
                adapter.currentList.any { it.did == initial && it.isSelected }
        }

        assertDeckVisible(activity, initial)
    }

    private fun deliverSelectionWithoutDelayedScroll(
        viewModel: DeckPickerViewModel,
        deckId: Long,
    ) {
        val selection = viewModel.selectDeck(deckId)
        repeat(1000) {
            shadowOf(Looper.getMainLooper()).idle()
            if (selection.isCompleted) {
                assertEquals(deckId, viewModel.focusedDeck)
                return
            }
            Thread.sleep(1)
        }
        assertTrue(selection.isCompleted, "Selection did not complete")
    }

    @Test
    fun `selection before the first list overrides the saved scroll position`() {
        val controller = launch()
        scrollAwayFromSelection(controller.get())
        val target = col.decks.id("Test Deck 03")
        val viewModel = controller.get().viewModel
        val decks = viewModel.flowOfDeckList.value
        val delayedList = MutableStateFlow(FlattenedDeckList.empty)
        val delayedFocus = MutableStateFlow<Long?>(null)
        controller.pause().stop()
        mockkObject(viewModel)
        every { viewModel.flowOfDeckList } returns delayedList
        every { viewModel.flowOfFocusedDeck } returns delayedFocus
        try {
            controller
                .recreate()
                .start()
                .resume()
                .visible()
            viewModel.awaitStartup()
            advanceRobolectricLooper()
            assertEquals(0, list(controller.get()).adapter!!.itemCount)

            col.decks.select(target)
            delayedFocus.value = target
            advanceRobolectricLooper()

            // Render an empty-list frame before data arrives, as can happen on a slow load.
            val recycler = list(controller.get())
            assertTrue(recycler.width > 0 && recycler.height > 0)
            recycler.requestLayout()
            recycler.measure(
                MeasureSpec.makeMeasureSpec(recycler.width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(recycler.height, MeasureSpec.EXACTLY),
            )
            recycler.layout(recycler.left, recycler.top, recycler.right, recycler.bottom)
            advanceRobolectricLooper()

            delayedList.value = decks.copy(data = decks.data.map { it.withUpdatedDeckId(target) })
            settle(controller.get())

            assertDeckVisible(controller.get(), target)
        } finally {
            unmockkObject(viewModel)
        }
    }

    private fun assertDeckVisible(
        activity: DeckPicker,
        target: Long,
    ) {
        val manager = list(activity).layoutManager as LinearLayoutManager
        val position = activity.viewModel.findDeckPosition(target)
        advanceRobolectricLooperUntil {
            position in manager.findFirstVisibleItemPosition()..manager.findLastVisibleItemPosition()
        }
        assertTrue(
            position in manager.findFirstVisibleItemPosition()..manager.findLastVisibleItemPosition(),
            "Selected position $position is outside ${manager.findFirstVisibleItemPosition()}..${manager.findLastVisibleItemPosition()}",
        )
    }

    private fun launch(): ActivityController<DeckPicker> =
        startActivityControllerNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent()).also { settle(it.get()) }

    private fun settle(activity: DeckPicker) {
        activity.viewModel.awaitStartup()
        advanceRobolectricLooperUntil { list(activity).adapter!!.itemCount >= 80 }
        advanceRobolectricLooper()
    }

    private fun scrollAwayFromSelection(activity: DeckPicker) {
        (list(activity).layoutManager as LinearLayoutManager).scrollToPositionWithOffset(30, -17)
        advanceRobolectricLooper()
        assertEquals(30 to -17, visiblePosition(activity))
    }

    private fun list(activity: DeckPicker): RecyclerView = activity.findViewById(R.id.decks)

    private fun visiblePosition(activity: DeckPicker): Pair<Int, Int> {
        val manager = list(activity).layoutManager as LinearLayoutManager
        val position = manager.findFirstVisibleItemPosition()
        return position to (manager.getDecoratedTop(manager.findViewByPosition(position)!!) - manager.paddingTop)
    }

    companion object {
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        @JvmStatic
        fun configurations() = listOf("sw400dp-w400dp-h800dp-port", "sw800dp-w800dp-h1200dp-port")
    }
}

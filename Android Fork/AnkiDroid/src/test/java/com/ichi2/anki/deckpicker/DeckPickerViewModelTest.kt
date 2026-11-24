// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.deckpicker

import android.annotation.SuppressLint
import android.content.SharedPreferences
import androidx.annotation.CheckResult
import androidx.core.content.edit
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.backendError
import anki.card_rendering.EmptyCardsReport
import anki.card_rendering.emptyCardsReport
import anki.collection.OpChanges
import anki.decks.SetDeckCollapsedRequest
import app.cash.turbine.test
import com.google.protobuf.ByteString
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.InitialActivity
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.StoragePermissionSet
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.anki.common.storage.StorageDecision
import com.ichi2.anki.deckpicker.DeckPickerViewModel.DeleteDeckConfirmationRequest
import com.ichi2.anki.libanki.Consts
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.Note
import com.ichi2.anki.libanki.emptyCids
import com.ichi2.anki.observability.ensureOpsExecuted
import com.ichi2.anki.progress.ViewModelProgress
import com.ichi2.testutils.JvmTest
import com.ichi2.testutils.publicCollectionPath
import com.ichi2.testutils.rules.BackendOverrideRule
import com.ichi2.testutils.withAllFilesAccess
import com.ichi2.testutils.withManageExternalStorageInManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.exceptions.BackendInvalidInputException
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.lessThan
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

/** Test of [DeckPickerViewModel] */
class DeckPickerViewModelTest : JvmTest() {
    @get:Rule
    val backendOverride = BackendOverrideRule()

    private val viewModels = ViewModelStore()
    private var viewModelCount = 0
    private val viewModel: DeckPickerViewModel by lazy { buildViewModel() }

    private fun buildViewModel() = DeckPickerViewModel().also { viewModels.put("deck-picker-${viewModelCount++}", it) }

    @After
    override fun tearDown() {
        try {
            viewModels.clear()
        } finally {
            super.tearDown()
        }
    }

    @Test
    fun `delete confirmation includes cards in subdecks`() =
        runTest {
            val deckId = addDeck("Parent")
            val childDeckId = addDeck("Parent::Child")
            val parentCard = addBasicNote("Parent card").firstCard()
            val childCard = addBasicNote("Child card").firstCard()
            addBasicNote("Unrelated card")
            col.setDeck(listOf(parentCard.id), deckId)
            col.setDeck(listOf(childCard.id), childDeckId)
            col.decks.select(deckId)
            viewModel.focusedDeck = deckId

            viewModel.flowOfDeleteDeckConfirmation.test {
                viewModel.requestDeleteDeckConfirmation().join()

                assertEquals(DeleteDeckConfirmationRequest(deckId, "Parent", 2, false), awaitItem())
                assertEquals(deckId, col.decks.byName("Parent")?.id)
                assertEquals(childDeckId, col.decks.byName("Parent::Child")?.id)
                assertEquals(3, col.cardCount())
            }
        }

    @Test
    fun `delete confirmation identifies filtered decks without returning their cards`() =
        runTest {
            val note = addBasicNote()
            val deckId = moveAllCardsToFilteredDeck(note)
            viewModel.focusedDeck = deckId

            viewModel.flowOfDeleteDeckConfirmation.test {
                viewModel.requestDeleteDeckConfirmation().join()

                assertEquals(DeleteDeckConfirmationRequest(deckId, "Filtered", 1, true), awaitItem())
                assertEquals(deckId, col.decks.byName("Filtered")?.id)
                assertEquals(deckId, note.firstCard().did)
            }
        }

    @Test
    fun `delete confirmation supports an empty deck`() =
        runTest {
            val deckId = addDeck("Empty", setAsSelected = true)
            viewModel.focusedDeck = deckId

            viewModel.flowOfDeleteDeckConfirmation.test {
                viewModel.requestDeleteDeckConfirmation().join()

                assertEquals(DeleteDeckConfirmationRequest(deckId, "Empty", 0, false), awaitItem())
            }
        }

    @Test
    fun `rename and study requests use the resolved selection despite focus search and collapsed parents`() =
        runTest {
            val parent = addDeck("Parent")
            val selected = addDeck("Parent::Selected", setAsSelected = true)
            val other = addDeck("Other")
            col.decks.collapse(parent)
            viewModel.focusedDeck = other
            viewModel.updateDeckFilter("No matching decks")

            for ((flow, request) in listOf(
                viewModel.flowOfRenameDeck to viewModel::requestRenameSelectedDeck,
                viewModel.flowOfStudyDeck to viewModel::requestStudySelectedDeck,
            )) {
                col.decks.select(selected)
                val undoStatus = col.undoStatus()
                flow.test {
                    request().join()
                    assertEquals(undoStatus, col.undoStatus())
                    // The request keeps its target even if selection changes before it is handled.
                    col.decks.select(other)
                    assertEquals(selected, awaitItem())
                    expectNoEvents()
                }
            }
        }

    @Test
    fun `empty cards - flow`() =
        runTest {
            val cardsToEmpty = createEmptyCards()

            viewModel.emptyCardsNotification.test {
                // test a 'normal' deletion
                viewModel.deleteEmptyCards(cardsToEmpty).join()

                expectMostRecentItem().also {
                    assertThat("cards deleted", it.cardsDeleted, equalTo(EXPECTED_CARDS))
                }

                // ensure a duplicate output is displayed to the user
                val newCardsToEmpty = createEmptyCards()
                viewModel.deleteEmptyCards(newCardsToEmpty).join()

                expectMostRecentItem().also {
                    assertThat("cards deleted: duplicate output", it.cardsDeleted, equalTo(EXPECTED_CARDS))
                }

                // test an empty list: a no-op should inform the user, rather than do nothing
                viewModel.deleteEmptyCards(emptyCardsReport { }).join()

                expectMostRecentItem().also {
                    assertThat("'no cards deleted' is notified", it.cardsDeleted, equalTo(0))
                }
            }
        }

    @Test
    fun `empty cards - undoable`() =
        runTest {
            val cardsToEmpty = createEmptyCards()

            // ChangeManager assert
            ensureOpsExecuted(1) {
                viewModel.deleteEmptyCards(cardsToEmpty).join()
            }

            // backend assert
            assertThat("col undo status", col.undoStatus().undo, equalTo("Empty Cards"))
        }

    @Test
    fun `empty cards - keep notes`() =
        runTest {
            val emptyCardsReport = createEmptyCards()

            viewModel.emptyCardsNotification.test {
                viewModel.deleteEmptyCards(emptyCardsReport, preserveNotes = true).join()

                expectMostRecentItem().also {
                    assertThat("note is retained", it.cardsDeleted, equalTo(EXPECTED_CARDS - 1))
                }

                viewModel.deleteEmptyCards(emptyCardsReport, preserveNotes = false).join()

                expectMostRecentItem().also {
                    assertThat("note is deleted", it.cardsDeleted, equalTo(1))
                }
            }
        }

    @Test
    fun `empty filtered - functionality`() {
        runTest {
            val note = addBasicNote("To", "Filtered")
            val filteredDeckId = moveAllCardsToFilteredDeck(assertOn = note)

            viewModel.emptyFilteredDeck(filteredDeckId).join()

            assertThat("deck was reset", note.firstCard().did, equalTo(Consts.DEFAULT_DECK_ID))
        }
    }

    @Test
    fun `empty filtered - flows`() {
        runTest {
            viewModel.flowOfDeckCountsChanged.test {
                val filteredDeckId = moveAllCardsToFilteredDeck()
                expectNoEvents()
                viewModel.emptyFilteredDeck(filteredDeckId).join()
                awaitItem()
                expectNoEvents()
                viewModel.emptyFilteredDeck(filteredDeckId).join()
                awaitItem()
            }
        }
    }

    @Test
    fun `empty filtered - undoable`() {
        runTest {
            val filteredDeckId = moveAllCardsToFilteredDeck()

            // ChangeManager assert
            ensureOpsExecuted(1) {
                viewModel.emptyFilteredDeck(filteredDeckId).join()
            }

            // backend assert
            assertThat("col undo status", col.undoStatus().undo, equalTo("Empty"))
        }
    }

    @Test
    fun `deleteDeck reports progress`() =
        runProgressTest {
            val deckId = addDeck("Deleted")

            assertProgressAround(DeckPickerProgress.DELETING_DECK) { deleteDeck(deckId).join() }
        }

    @Test
    fun `deleteSelectedDeck reports progress before reading the selected deck`() =
        runTest {
            col.decks.select(addDeck("Deleted"))

            buildViewModel().run {
                withQueuedCollectionAccess {
                    val deletion = deleteSelectedDeck()
                    val active = assertIs<ViewModelProgress.Active<DeckPickerProgress>>(progressManager.progress.value)
                    assertEquals(DeckPickerProgress.DELETING_DECK, active.message)
                    deletion.join()
                }
                assertIs<ViewModelProgress.Idle>(progressManager.progress.value)
            }
        }

    @Test
    fun `rebuildFilteredDeck reports progress`() =
        runProgressTest {
            val filteredDeckId = moveAllCardsToFilteredDeck()

            assertProgressAround(DeckPickerProgress.REBUILDING_FILTERED_DECK) { rebuildFilteredDeck(filteredDeckId).join() }
        }

    @Test
    fun `emptyFilteredDeck reports progress`() =
        runProgressTest {
            val filteredDeckId = moveAllCardsToFilteredDeck()

            assertProgressAround { emptyFilteredDeck(filteredDeckId).join() }
        }

    @Test
    fun `deleteEmptyCards reports progress`() =
        runProgressTest {
            val cardsToEmpty = createEmptyCards()

            assertProgressAround(DeckPickerProgress.DELETING_EMPTY_CARDS) { deleteEmptyCards(cardsToEmpty).join() }
        }

    /**
     * [runTest] with a ViewModel built after Main becomes a [StandardTestDispatcher]. The ViewModel's
     * scope captures Main when it is built, and an inline Main would hide the Active state.
     */
    private fun runProgressTest(testBody: suspend DeckPickerViewModel.() -> Unit) =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            buildViewModel().testBody()
        }

    private suspend fun TestScope.withQueuedCollectionAccess(block: suspend () -> Unit) {
        val previousQueue = CollectionManager.setTestDispatcher(StandardTestDispatcher(testScheduler), useReentrantLock = false)
        try {
            block()
        } finally {
            CollectionManager.setTestDispatcher(previousQueue)
        }
    }

    private suspend fun DeckPickerViewModel.assertProgressAround(
        message: DeckPickerProgress? = null,
        op: suspend () -> Unit,
    ) {
        progressManager.progress.test {
            assertIs<ViewModelProgress.Idle>(awaitItem())
            op()
            val active = assertIs<ViewModelProgress.Active<DeckPickerProgress>>(awaitItem())
            assertEquals(message, active.message)
            assertIs<ViewModelProgress.Idle>(awaitItem())
            expectNoEvents()
        }
    }

    /**
     * Creates a note with 3 cards, all empty
     *
     * This allows us to test the 'keep note' functionality only affects the first card
     */
    @CheckResult
    @SuppressLint("CheckResult")
    private suspend fun createEmptyCards(): EmptyCardsReport {
        addNoteUsingNoteTypeName("Cloze", "{{c1::Hello}} {{c3::There}} {{c2::World}}", "").apply {
            setField(0, "No cloze")
            col.updateNote(this)
        }
        return withCol { getEmptyCards() }.also { report ->
            assertThat("there are empty cards", report.emptyCids(), not(report.emptyCids().isEmpty()))
            Timber.d("created %d empty cards: [%s]", report.emptyCids().size, report.emptyCids())
        }
    }

    /** test helper to use [deleteEmptyCards] with the original test `preserveNotes` value */
    private fun DeckPickerViewModel.deleteEmptyCards(report: EmptyCardsReport) = deleteEmptyCards(report, preserveNotes = false)

    /**
     * Moves all cards to a deck named "Filtered"
     *
     * If there are no notes, one is created
     * @return The [DeckId] of the filtered deck
     */
    private fun moveAllCardsToFilteredDeck(assertOn: Note = addBasicNote("To", "Filtered")): DeckId =
        addDynamicDeck("Filtered", "").also { did ->
            assertThat("filter - did", assertOn.firstCard().did, equalTo(did))
            assertThat("filter - odid", assertOn.firstCard().oDid, equalTo(Consts.DEFAULT_DECK_ID))
        }

    companion object {
        private const val EXPECTED_CARDS: Int = 3
    }

    @Test
    fun `ensure collapsed decks are also deleted`() {
        runTest {
            val deckIdA = addDeck("A")
            val subDeckIdA1 = addDeck("A::A1")
            val subDeckIdA2 = addDeck("A::A2")
            // add other decks as well as control
            addDeck("B")
            addDeck("B:B1")
            viewModel.flowOfDisableShortcuts.test {
                viewModel.reloadDeckCounts().join()
                viewModel.disableDeckAndChildrenShortcuts(deckIdA)
                val actual = awaitItem()
                val expected =
                    listOf(
                        deckIdA.toString(),
                        subDeckIdA1.toString(),
                        subDeckIdA2.toString(),
                    )
                assertEquals(expected, actual)
            }
        }
    }

    @Test
    fun `request context menu - flow`() {
        runTest {
            val deckId = addDeck("Deck A")

            viewModel.flowOfShowContextMenu.test {
                viewModel.requestContextMenu(deckId).join()

                assertEquals(deckId, awaitItem())
            }
        }
    }

    @Test
    fun `request right click context menu - flow`() {
        runTest {
            val deckId = addDeck("Deck B")
            val x = 10f
            val y = 20f

            viewModel.flowOfShowRightClickContextMenu.test {
                viewModel.requestRightClickContextMenu(deckId, x, y).join()

                val item = awaitItem()
                assertEquals(deckId, item.deckId)
                assertEquals(x, item.x)
                assertEquals(y, item.y)
            }
        }
    }

    @Test
    fun `deck search recovers after a failed deck read`() {
        val failNextDeckRead = AtomicBoolean(false)
        val readError = BackendInvalidInputException(backendError { message = "Could not read the current deck" })
        backendOverride.replaceWith {
            object : Backend() {
                override fun getDeckLegacy(did: Long): ByteString {
                    if (failNextDeckRead.compareAndSet(true, false)) throw readError
                    return super.getDeckLegacy(did)
                }
            }
        }
        val first = addDeck("First")
        val second = addDeck("Second")

        runTest {
            viewModel.reloadDeckCounts().join()
            runCurrent()
            val initialList = viewModel.flowOfDeckList.value
            assertEquals(listOf(first, second), initialList.data.map { it.did })

            failNextDeckRead.set(true)
            viewModel.updateDeckFilter("First")
            runCurrent()

            assertSame(readError, viewModel.onError.tryReceive().getOrNull())
            assertEquals(initialList, viewModel.flowOfDeckList.value)

            viewModel.updateDeckFilter("Second")
            runCurrent()

            val filteredList = viewModel.flowOfDeckList.value
            assertEquals(listOf(second), filteredList.data.map { it.did })
        }
    }

    @Test
    fun `deleteDeck selects the next visible deck matching the filter`() =
        runTest {
            // 'A hidden' is first alphabetically but does not match the filter.
            // The 'skip empty default' fallback would select it, so this test fails
            // if the visible deck list is not applied
            addDeck("A hidden")
            val deleted = addDeck("B match", setAsSelected = true)
            val expected = addDeck("C match")

            viewModel.updateDeckFilter("match")
            viewModel.reloadDeckCounts().join()
            // the deck list is built from a flow, so await a value containing the deck to delete
            viewModel.flowOfDeckList.first { list -> list.data.any { it.did == deleted } }

            ensureOpsExecuted(1) {
                viewModel.deleteDeck(deleted).join()
            }

            assertEquals(expected, col.decks.selected(), "a filtered deck list is not respected")
            assertEquals(expected, viewModel.focusedDeck)
            assertEquals("Delete Deck", col.undoStatus().undo)
        }

    @Test
    fun `deleteDeck does not select the hidden Default when its last child is deleted`() =
        runTest {
            val expected = addDeck("Other")
            // Default is only visible because it is the parent of 'Default::Child'
            val child = addDeck("Default::Child", setAsSelected = true)
            // a fresh deck tree reports decks as collapsed: expand Default so its child is visible
            col.decks.setCollapsed(Consts.DEFAULT_DECK_ID, collapsed = false, SetDeckCollapsedRequest.Scope.REVIEWER)

            viewModel.reloadDeckCounts().join()
            viewModel.flowOfDeckList.first { list -> list.data.any { it.did == child } }

            ensureOpsExecuted(1) {
                viewModel.deleteDeck(child).join()
            }

            assertEquals(
                expected,
                col.decks.selected(),
                "Default is empty after its last child is deleted, so it must not be selected",
            )
            assertEquals(expected, viewModel.focusedDeck)
        }

    @Test
    fun `deleteDeck skips a parent that no longer matches the filter`() =
        runTest {
            addDeck("A hidden")
            val expected = addDeck("B match")
            val parent = addDeck("C")
            val deleted = addDeck("C::Child match", setAsSelected = true)

            viewModel.updateDeckFilter("match")
            viewModel.reloadDeckCounts().join()
            val displayed = viewModel.flowOfDeckList.first { list -> list.data.any { it.did == deleted } }
            assertEquals(listOf(expected, parent, deleted), displayed.data.map { it.did })

            ensureOpsExecuted(1) {
                viewModel.deleteDeck(deleted).join()
            }

            // C was visible only because its child matched. An empty filter would select C.
            assertEquals(expected, col.decks.selected())
            assertEquals(expected, viewModel.focusedDeck)
        }

    @Test
    fun `deleteDeck skips hidden Default when selecting the previous deck`() =
        runTest {
            val expected = addDeck("A")
            val child = addDeck("Default::Child", setAsSelected = true)
            col.decks.setCollapsed(Consts.DEFAULT_DECK_ID, collapsed = false, SetDeckCollapsedRequest.Scope.REVIEWER)

            viewModel.reloadDeckCounts().join()
            val displayed = viewModel.flowOfDeckList.first { list -> list.data.any { it.did == child } }
            assertEquals(listOf(expected, Consts.DEFAULT_DECK_ID, child), displayed.data.map { it.did })

            ensureOpsExecuted(1) {
                viewModel.deleteDeck(child).join()
            }

            // Default is the nearest previous row, but deleting its last child hides it.
            assertEquals(expected, col.decks.selected())
            assertEquals(expected, viewModel.focusedDeck)
        }
}

@RunWith(AndroidJUnit4::class)
class DeckPickerStartupViewModelTest : RobolectricTest() {
    @get:Rule
    val backendOverride = BackendOverrideRule()

    private val viewModels = ViewModelStore()
    private val viewModel by lazy { DeckPickerViewModel().also { viewModels.put("deck-picker", it) } }

    @After
    override fun tearDown() {
        try {
            viewModels.clear()
        } finally {
            super.tearDown()
        }
    }

    @Test // Issue 13574: the storage decision may be deferred until permissions are granted
    @SuppressLint("NewApi") // EXTERNAL_MANAGER requires R: Robolectric runs at API 35
    fun `startup decides a collection path once permissions are granted`() =
        runTest {
            targetContext.sharedPrefs().edit { remove(CollectionHelper.PREF_COLLECTION_PATH) }

            withManageExternalStorageInManifest {
                withAllFilesAccess {
                    viewModel.handleStartup(externalManagerEnvironment())
                    advanceRobolectricLooperUntil { viewModel.startupJob?.isCompleted == true }
                }
            }

            assertEquals(publicCollectionPath, targetContext.sharedPrefs().getString(CollectionHelper.PREF_COLLECTION_PATH, null))
            assertEquals(DeckPickerViewModel.StartupResponse.Success, viewModel.flowOfStartupResponse.value)
        }

    @Test // Issue 13574: the storage decision must not be made before the user grants or skips
    @SuppressLint("NewApi") // EXTERNAL_MANAGER requires R: Robolectric runs at API 35
    fun `startup requests permissions without deciding a collection path`() {
        targetContext.sharedPrefs().edit { remove(CollectionHelper.PREF_COLLECTION_PATH) }

        withManageExternalStorageInManifest { viewModel.handleStartup(externalManagerEnvironment()) }

        assertEquals(
            DeckPickerViewModel.StartupResponse.RequestPermissions(StoragePermissionSet.EXTERNAL_MANAGER),
            viewModel.flowOfStartupResponse.value,
        )
        assertThat(
            "no collection path is decided before permissions are granted",
            !targetContext.sharedPrefs().contains(CollectionHelper.PREF_COLLECTION_PATH),
        )
    }

    /**
     * `DeckPicker`'s environment on a build declaring `MANAGE_EXTERNAL_STORAGE`, on Android 11+.
     *
     * Not `@Config(sdk = R)`: startup opens the backend, which is unavailable in Robolectric's
     * per-SDK sandbox. The default SDK satisfies `EXTERNAL_MANAGER`'s API requirement.
     */
    @SuppressLint("NewApi") // EXTERNAL_MANAGER requires R: Robolectric runs at API 35
    private fun externalManagerEnvironment() =
        object : DeckPickerViewModel.AnkiDroidEnvironment {
            override val requiredPermissions = StoragePermissionSet.EXTERNAL_MANAGER

            override fun hasRequiredPermissions() = requiredPermissions.hasRequiredPermissions(targetContext)

            override val preferences: SharedPreferences
                get() = targetContext.sharedPrefs()

            override fun decideStorageIfUndecided() {
                check(hasRequiredPermissions()) { "the storage decision requires granted permissions" }
                InitialActivity.decideStorageIfUndecided(targetContext)
            }

            override fun initializeAnkiDroidFolder(): Boolean = CollectionHelper.isCurrentAnkiDroidDirAccessible(targetContext)
        }

    @Test
    fun `handleStartup does not block while a hung sync holds the collection queue`() {
        col
        val queueHeld = CountDownLatch(1)
        val releaseQueue = CountDownLatch(1)
        // a sync against an unresponsive server runs `withCol { syncCollection(...) }`,
        // holding the collection queue for the whole network call
        val hungSync =
            thread(name = "hung-sync") {
                runBlocking {
                    withCol {
                        queueHeld.countDown()
                        releaseQueue.await()
                    }
                }
            }
        try {
            // in-memory tests don't set a collection path, so force the storage gate open;
            // otherwise getStartupFailureType returns StorageUndecided before reaching the
            // getColUnsafe() call that waits on the collection queue
            CollectionHelper.storageDecisionTestOverride = StorageDecision.Decided

            assertThat("sync is holding the collection queue", queueHeld.await(5.seconds), equalTo(true))

            // if handleStartup blocks on the queue, free it after a delay so the test fails with a message instead of hanging
            thread(name = "watchdog") {
                if (!releaseQueue.await(WATCHDOG_TIMEOUT)) {
                    releaseQueue.countDown()
                }
            }

            val elapsed = measureTime { viewModel.handleStartup(grantedPermissionsEnvironment) }

            assertThat(
                "handleStartup waited $elapsed for the collection queue. On a device this ANRs when DeckPicker is recreated while a sync is stuck on an unresponsive server",
                elapsed,
                lessThan(WATCHDOG_TIMEOUT),
            )
        } finally {
            CollectionHelper.storageDecisionTestOverride = null
            releaseQueue.countDown()
            hungSync.join()
        }
    }

    @Test
    fun `startup loads the deck list once and skips earlier refreshes`() =
        runTest {
            col
            CollectionHelper.storageDecisionTestOverride = StorageDecision.Decided
            val releaseStartup = CountDownLatch(1)
            try {
                viewModel.flowOfDecksReloaded.test {
                    viewModel.handleStartup(startupEnvironmentHeldUntil(releaseStartup))
                    viewModel.updateDeckList().join()
                    expectNoEvents()

                    releaseStartup.countDown()
                    advanceRobolectricLooperUntil { viewModel.startupJob?.isCompleted == true }
                    awaitItem()
                    expectNoEvents()
                }
            } finally {
                CollectionHelper.storageDecisionTestOverride = null
                releaseStartup.countDown()
            }
        }

    @Test
    fun `a refresh after the screen resumed reloads the deck list`() =
        runTest {
            col
            CollectionHelper.storageDecisionTestOverride = StorageDecision.Decided
            val releaseStartup = CountDownLatch(1)
            try {
                viewModel.flowOfDecksReloaded.test {
                    viewModel.handleStartup(startupEnvironmentHeldUntil(releaseStartup))
                    viewModel.onScreenResumed()

                    releaseStartup.countDown()
                    advanceRobolectricLooperUntil { viewModel.startupJob?.isCompleted == true }
                    awaitItem()

                    viewModel.updateDeckList().join()
                    awaitItem()
                }
            } finally {
                CollectionHelper.storageDecisionTestOverride = null
                releaseStartup.countDown()
            }
        }

    @Test
    fun `emptying a filtered deck empties it even if another deck is selected meanwhile`() {
        val selectOther = AtomicBoolean(false)
        val emptied = AtomicLong(0)
        var filtered = 0L
        var other = 0L
        var selection: Job? = null
        backendOverride.replaceWith {
            object : Backend() {
                override fun setCurrentDeck(did: Long): OpChanges {
                    val changes = super.setCurrentDeck(did)
                    if (did == filtered && selectOther.compareAndSet(true, false)) selection = viewModel.selectDeck(other)
                    return changes
                }

                override fun emptyFilteredDeck(did: Long): OpChanges {
                    emptied.set(did)
                    return super.emptyFilteredDeck(did)
                }
            }
        }
        filtered = addDynamicDeck("Filtered")
        other = addDeck("Other")

        withCollectionThread {
            selectOther.set(true)
            val empty = viewModel.emptyFilteredDeck(filtered)
            advanceRobolectricLooperUntil { empty.isCompleted && selection?.isCompleted == true }
        }

        assertEquals(filtered, emptied.get())
    }

    private fun withCollectionThread(block: () -> Unit) {
        val previousQueue = CollectionManager.setTestDispatcher(Dispatchers.IO.limitedParallelism(1), useReentrantLock = false)
        try {
            block()
        } finally {
            CollectionManager.setTestDispatcher(previousQueue)
        }
    }

    private fun startupEnvironmentHeldUntil(release: CountDownLatch) =
        object : DeckPickerViewModel.AnkiDroidEnvironment by grantedPermissionsEnvironment {
            override val preferences: SharedPreferences
                get() {
                    release.await(WATCHDOG_TIMEOUT)
                    return grantedPermissionsEnvironment.preferences
                }
        }

    private val grantedPermissionsEnvironment =
        object : DeckPickerViewModel.AnkiDroidEnvironment {
            override fun hasRequiredPermissions() = true

            override val requiredPermissions: StoragePermissionSet
                get() = error("unused: permissions are granted")

            override val preferences: SharedPreferences
                get() = getPreferences()

            override fun decideStorageIfUndecided() = Unit

            override fun initializeAnkiDroidFolder() = true
        }
}

/** how long the watchdog lets a blocked [DeckPickerViewModel.handleStartup] hold the test before freeing the queue */
private val WATCHDOG_TIMEOUT = 2.seconds

private fun CountDownLatch.await(timeout: Duration): Boolean = await(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)

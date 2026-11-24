// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Akshay Jadhav <jadhavakshay0701@gmail.com>

package com.ichi2.anki.dialogs

import android.content.ContextWrapper
import android.os.Looper
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.CommonString
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.IntroductionActivity
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.dialogs.CreateDeckDialog.DeckDialogType
import com.ichi2.anki.dialogs.utils.input
import com.ichi2.anki.libanki.DeckId
import com.ichi2.testutils.ext.snackbarText
import com.ichi2.utils.getInputField
import com.ichi2.utils.getInputTextLayout
import com.ichi2.utils.positiveButton
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import okhttp3.internal.closeQuietly
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.Matcher
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowToast
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

@RunWith(RobolectricTestRunner::class)
class CreateDeckDialogTest : RobolectricTest() {
    private lateinit var activityScenario: ActivityScenario<DeckPicker>

    override fun setUp() {
        super.setUp()
        getPreferences().edit { putBoolean(IntroductionActivity.PREF_INTRODUCTION_SLIDES_SHOWN, true) }
        ensureCollectionLoadIsSynchronous()
        activityScenario =
            ActivityScenario.launch(DeckPicker::class.java).apply {
                moveToState(Lifecycle.State.STARTED)
            }
    }

    override fun tearDown() {
        activityScenario.closeQuietly()
        super.tearDown()
    }

    @Test
    fun testCreateSubDeckFunction() {
        val deckParentId = col.decks.id("Deck Name")
        val deckName = "filteredDeck"
        ensureExecutionOfScenario(DeckDialogType.SUB_DECK, deckParentId) { createDeckDialog, assertionCalled ->
            createDeckDialog.onNewDeckCreated = { id: DeckId ->
                val deckNameWithParentName = col.decks.getSubdeckName(deckParentId, deckName)
                assertThat(id, equalTo(col.decks.id(deckNameWithParentName!!)))
                assertionCalled()
            }
            createDeckDialog.createSubDeck(deckParentId, deckName)
        }
    }

    @Test
    fun testCreateDeckFunction() {
        val deckName = "Deck Name"
        ensureExecutionOfScenario(DeckDialogType.DECK) { createDeckDialog, assertionCalled ->
            createDeckDialog.onNewDeckCreated = { id: DeckId ->
                // a deck was created
                assertThat(id, equalTo(col.decks.byName(deckName)!!.id))
                assertionCalled()
            }
            createDeckDialog.createDeck(deckName)
        }
    }

    @Test
    fun testCreateDeckWithQuotes() {
        val deckNameWithQuotes = "New \"Quoted\" Deck"
        ensureExecutionOfScenario(DeckDialogType.DECK) { createDeckDialog, assertionCalled ->
            createDeckDialog.onNewDeckCreated = { id: DeckId ->
                // Verify that quotes are preserved in deck names when creating
                assertThat(
                    "Quotes should be preserved in deck names when creating",
                    col.decks.name(id),
                    equalTo(deckNameWithQuotes),
                )
                assertionCalled()
            }
            createDeckDialog.createDeck(deckNameWithQuotes)
        }
    }

    @Test
    fun `creating a child of a filtered deck shows an error`() {
        addDynamicDeck("Filtered Deck 1")
        val deckCount = col.decks.count()
        withCreateDeckDialog(DeckDialogType.DECK) {
            onNewDeckCreated = { fail("the deck was not created") }
            showDialog().apply {
                input = "Filtered Deck 1::Child"
                positiveButton.performClick()
            }
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertThat("no deck was created", col.decks.count(), equalTo(deckCount))
        activityScenario.onActivity { activity ->
            assertThat(
                activity.snackbarText,
                equalTo(TR.errorsFilteredParentDeck()),
            )
        }
    }

    @Test
    fun `creating a descendant of a filtered deck with non-activity context shows an error`() {
        addDynamicDeck("Filtered Deck 1")
        val deckCount = col.decks.count()
        activityScenario.onActivity { activity ->
            val dialog = CreateDeckDialog(ContextWrapper(activity), "Create deck", DeckDialogType.DECK, null)
            dialog.onNewDeckCreated = { fail("the deck was not created") }
            dialog.createDeck("filtered deck 1::Parent::Child")

            assertThat("no deck was created", col.decks.count(), equalTo(deckCount))
            assertThat(
                ShadowToast.getTextOfLatestToast(),
                equalTo(TR.errorsFilteredParentDeck()),
            )
        }
    }

    @Test
    fun `creating a subdeck below a filtered deck shows an error`() {
        val parentId = addDeck("Parent")
        addDynamicDeck("Parent::Filtered")
        val deckCount = col.decks.count()
        withCreateDeckDialog(DeckDialogType.SUB_DECK, parentId) {
            onNewDeckCreated = { fail("the deck was not created") }
            showDialog().apply {
                input = "Filtered::Child"
                positiveButton.performClick()
            }
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertThat("no deck was created", col.decks.count(), equalTo(deckCount))
        activityScenario.onActivity { activity ->
            assertThat(
                activity.snackbarText,
                equalTo(TR.errorsFilteredParentDeck()),
            )
        }
    }

    @Test
    fun testRenameDeckFunction() {
        val deckName = "Deck Name"
        val deckNewName = "New Deck Name"
        val deckId = col.decks.id(deckName)
        ensureExecutionOfScenario(DeckDialogType.RENAME_DECK, renamedDeckId = deckId) { createDeckDialog, assertionCalled ->
            createDeckDialog.deckName = deckName
            createDeckDialog.onNewDeckCreated = { id: DeckId ->
                // a deck name was renamed
                assertThat(deckNewName, equalTo(col.decks.name(id)))
                assertionCalled()
            }
            createDeckDialog.renameDeck(deckNewName)
        }
    }

    @Test
    fun testRenameDeckWithQuotes() {
        val deckName = "Deck Name"
        val deckNewNameWithQuotes = "New \"Quoted\" Deck Name"
        val deckId = col.decks.id(deckName)
        ensureExecutionOfScenario(DeckDialogType.RENAME_DECK, renamedDeckId = deckId) { createDeckDialog, assertionCalled ->
            createDeckDialog.deckName = deckName
            createDeckDialog.onNewDeckCreated = { id: DeckId ->
                // Verify that the quotes are preserved in the renamed deck
                assertThat(
                    "Quotes should be preserved in deck names when renaming",
                    col.decks.name(id),
                    equalTo(deckNewNameWithQuotes),
                )
                assertionCalled()
            }
            createDeckDialog.renameDeck(deckNewNameWithQuotes)
        }
    }

    @Test
    fun `deck ordering hint`() {
        // The correct way to order a deck is ['01', '02', '10']
        val expectedText = "If you have deck ordering issues (e.g. ‘10’ appears before ‘2’), replace ‘2’ with ‘02’"
        testDialog(DeckDialogType.DECK) {
            fun assertHelperText(
                reason: String?,
                matcher: Matcher<in CharSequence?>,
            ) = assertThat(reason, getInputTextLayout().helperText, matcher)

            input = "test"
            assertHelperText("no number suggestion if text-only", nullValue())
            input = "1. Cheese"
            assertHelperText("no number suggestion if number is less than 10", nullValue())
            input = "10. Cheese"
            assertHelperText(
                "Number suggestion if number is greater than or equal to 10",
                equalTo(expectedText),
            )
            input = "1. Cheese"
            assertHelperText("hint is removed if the number is removed", nullValue())
        }
    }

    @Test
    fun nameMayNotBeZeroLength() {
        testDialog(DeckDialogType.DECK) {
            assertThat("Ok is disabled if zero length input", positiveButton.isEnabled, equalTo(false))
            input = "NotEmpty"
            assertThat("Ok is enabled if not zero length input", positiveButton.isEnabled, equalTo(true))
            input = "A::B"
            assertThat("OK is enabled if fully qualified input provided ('A::B')", positiveButton.isEnabled, equalTo(true))
        }
    }

    @Test
    fun `subdeck name may not be blank`() {
        val parentId = addDeck("Parent")
        testDialog(DeckDialogType.SUB_DECK, parentId) {
            input = "   "
            assertThat("Create is disabled if the subdeck name is blank", positiveButton.isEnabled, equalTo(false))
            input = "Child"
            assertThat("Create is enabled if the subdeck name is not blank", positiveButton.isEnabled, equalTo(true))
        }
    }

    @Test
    fun searchDecksIconVisibilityDeckCreationTest() =
        runTest {
            // await deckpicker
            val deckPicker =
                suspendCoroutine { coro ->
                    activityScenario.onActivity { deckPicker ->
                        coro.resume(deckPicker)
                    }
                }

            suspend fun decksCount() = withCol { decks.count() }
            val deckCounter = AtomicInteger(1)

            for (i in 0 until 10) {
                val createDeckDialog =
                    CreateDeckDialog(
                        context = deckPicker,
                        title = "Create deck",
                        deckDialogType = DeckDialogType.DECK,
                        parentId = null,
                    )
                val did =
                    suspendCoroutine { coro ->
                        createDeckDialog.onNewDeckCreated = { did: DeckId ->
                            coro.resume(did)
                        }
                        createDeckDialog.createDeck("Deck$i")
                    }
                assertEquals(deckCounter.incrementAndGet(), decksCount())

                assertEquals(deckCounter.get(), decksCount())

                updateSearchDecksIcon(deckPicker)
                assertEquals(
                    deckPicker.viewModel.optionsMenuState?.searchIcon,
                    decksCount() >= 10,
                )

                // After the last deck was created, delete a deck
                if (decksCount() >= 10) {
                    deckPicker.viewModel.deleteDeck(did).join()
                    assertEquals(deckCounter.decrementAndGet(), decksCount())

                    assertEquals(deckCounter.get(), decksCount())

                    updateSearchDecksIcon(deckPicker)
                    assertFalse(deckPicker.viewModel.optionsMenuState?.searchIcon ?: true)
                }
            }
        }

    private suspend fun updateSearchDecksIcon(deckPicker: DeckPicker) {
        // the icon update requires a call to refreshState() and subsequent menu
        // rebuild; access it directly instead so the test passes
        deckPicker.viewModel.refreshMenuState()
    }

    @Test
    fun searchDecksIconVisibilitySubdeckCreationTest() =
        runTest {
            val deckPicker =
                suspendCoroutine { coro -> activityScenario.onActivity { coro.resume(it) } }
            deckPicker.viewModel.refreshMenuState()
            assertEquals(deckPicker.viewModel.optionsMenuState!!.searchIcon, false)
            // a single top-level deck with lots of subdecks should turn the icon on
            withCol {
                decks.id(deckTreeName(0, 10, "Deck"))
            }
            deckPicker.viewModel.refreshMenuState()
            assertEquals(deckPicker.viewModel.optionsMenuState!!.searchIcon, true)
        }

    @Test
    fun positiveButtonEnabledOnMatchingDeckNames() {
        val previousDeckName = "Deck Name"
        testRenameDialog(currentName = previousDeckName) {
            input = previousDeckName
            assertThat("no error is displayed", getInputTextLayout().error, nullValue())
        }
    }

    @Test
    fun `renaming a deck with an extra colon does not conflict with itself`() {
        testRenameDialog(currentName = "a::b") {
            input = "a:::b"
            assertThat("no error is displayed", getInputTextLayout().error, nullValue())
            assertThat("rename is enabled", positiveButton.isEnabled, equalTo(true))
        }
    }

    @Test
    fun `renaming a deck to an existing deck name shows an error`() {
        col.decks.id("c")
        testRenameDialog(currentName = "a::b") {
            input = "c"
            assertThat(
                "error is displayed",
                getInputTextLayout().error?.toString(),
                equalTo(getResourceString(CommonString.error_name_exists)),
            )
            assertThat("rename is disabled", positiveButton.isEnabled, equalTo(false))
        }
    }

    @Test
    fun `renaming a deck to an equivalent name changes nothing`() {
        val deckId = col.decks.id("a::b")
        val deckCount = col.decks.count()
        withRenameDeckDialog(deckId) {
            deckName = "a::b"
            onNewDeckCreated = { fail("the deck was not renamed") }
            renameDeck("a:::b")
        }
        assertThat("the deck kept its name", col.decks.name(deckId), equalTo("a::b"))
        assertThat("no deck was created", col.decks.count(), equalTo(deckCount))
        activityScenario.onActivity { activity ->
            assertThat("no rename is reported", activity.snackbarText, nullValue())
        }
    }

    @Test
    fun `renaming a deck which no longer exists does not create a deck`() {
        val deckId = col.decks.id("Old Deck")
        col.decks.remove(listOf(deckId))
        val deckCount = col.decks.count()
        withRenameDeckDialog(deckId) {
            deckName = "Old Deck"
            onNewDeckCreated = { fail("the deck was not renamed") }
            renameDeck("New Deck")
        }
        assertThat("no deck was created", col.decks.count(), equalTo(deckCount))
    }

    private fun testRenameDialog(
        currentName: String,
        callback: (AlertDialog.() -> Unit),
    ) {
        val deckId = col.decks.id(currentName)
        withRenameDeckDialog(deckId) {
            deckName = currentName
            callback(this.showDialog())
        }
    }

    /**
     * Executes [callback] on the [AlertDialog] created from [CreateDeckDialog]
     */
    private fun testDialog(
        deckDialogType: DeckDialogType,
        parentId: DeckId? = null,
        renamedDeckId: DeckId? = null,
        callback: (AlertDialog.() -> Unit),
    ) {
        withCreateDeckDialog(deckDialogType, parentId, renamedDeckId) {
            callback(this.showDialog())
        }
    }

    /**
     * Creates a test instance of [CreateDeckDialog]
     */
    private fun withCreateDeckDialog(
        deckDialogType: DeckDialogType,
        parentId: DeckId? = null,
        renamedDeckId: DeckId? = null,
        callback: (CreateDeckDialog.() -> Unit),
    ) {
        activityScenario.onActivity { activity: DeckPicker ->
            val createDeckDialog =
                CreateDeckDialog(
                    context = activity,
                    title = "Create deck",
                    deckDialogType = deckDialogType,
                    parentId = parentId,
                    renamedDeckId = renamedDeckId,
                )
            callback(createDeckDialog)
        }
    }

    /**
     * Creates a test instance of [CreateDeckDialog] which renames [renamedDeckId]
     */
    private fun withRenameDeckDialog(
        renamedDeckId: DeckId,
        callback: (CreateDeckDialog.() -> Unit),
    ) = withCreateDeckDialog(DeckDialogType.RENAME_DECK, renamedDeckId = renamedDeckId, callback = callback)

    /*
     * The next 8 testcases test every permutation of
     * {createDeck, renameDeck}, {validName, invalidName}, [activityContext, nonActivityContext]
     * Shadows.shadowOf(Looper.getMainLooper()).idle()
     * is used to flush the queue and get the latest snackbar, since asking for it immediately fails the test
     * as the display time is LENGTH_LONG
     */
    @Test
    fun `createDeck with activity context shows snackbar for valid name`() {
        ensureExecutionOfScenario(DeckDialogType.DECK) { dialog, assertionCalled ->
            dialog.onNewDeckCreated = { _: DeckId -> assertionCalled() }
            dialog.createDeck("Create Deck")

            activityScenario.onActivity { activity ->
                assertThat(
                    "Snackbar should confirm deck creation for valid name",
                    activity.snackbarText,
                    equalTo(getResourceString(CommonString.deck_created)),
                )
            }
        }
    }

    @Test
    fun `createDeck with activity context shows snackbar for invalid name`() {
        activityScenario.onActivity { activity ->
            val dialog = CreateDeckDialog(activity, "Create deck", DeckDialogType.DECK, null)
            dialog.onNewDeckCreated = { _: DeckId -> }
            dialog.createDeck("   ")
            Shadows.shadowOf(Looper.getMainLooper()).idle()

            assertThat(
                "Snackbar should show invalid name error for blank name",
                activity.snackbarText,
                equalTo(getResourceString(CommonString.invalid_deck_name)),
            )
        }
    }

    @Test
    fun `createSubDeck with a blank name does not create a deck`() {
        val parentId = addDeck("Parent")
        val deckCount = col.decks.count()
        activityScenario.onActivity { activity ->
            val dialog = CreateDeckDialog(activity, "Create subdeck", DeckDialogType.SUB_DECK, parentId)
            dialog.onNewDeckCreated = { fail("the deck was not created") }
            dialog.createSubDeck(parentId, "   ")
            Shadows.shadowOf(Looper.getMainLooper()).idle()

            assertThat("no deck was created", col.decks.count(), equalTo(deckCount))
            assertThat(
                "Snackbar should show invalid name error for blank name",
                activity.snackbarText,
                equalTo(getResourceString(CommonString.invalid_deck_name)),
            )
        }
    }

    @Test
    fun `createDeck with non-activity context shows toast for valid name`() {
        activityScenario.onActivity { activity ->
            val dialog =
                CreateDeckDialog(ContextWrapper(activity), "Create deck", DeckDialogType.DECK, null)
            dialog.onNewDeckCreated = { _: DeckId -> }
            dialog.createDeck("Create Deck")

            assertThat(
                "Toast should confirm deck creation for valid name",
                ShadowToast.getTextOfLatestToast(),
                equalTo(getResourceString(CommonString.deck_created)),
            )
        }
    }

    @Test
    fun `createDeck with non-activity context shows toast for invalid name`() {
        activityScenario.onActivity { activity ->
            val dialog =
                CreateDeckDialog(ContextWrapper(activity), "Create deck", DeckDialogType.DECK, null)
            dialog.onNewDeckCreated = { _: DeckId -> }
            dialog.createDeck("   ")

            assertThat(
                "Toast should show invalid name error for blank name",
                ShadowToast.getTextOfLatestToast(),
                equalTo(getResourceString(CommonString.invalid_deck_name)),
            )
        }
    }

    @Test
    fun `renameDeck with activity context shows snackbar for valid name`() {
        val deckId = col.decks.id("Old Deck")
        ensureExecutionOfScenario(DeckDialogType.RENAME_DECK, renamedDeckId = deckId) { dialog, assertionCalled ->
            dialog.deckName = "Old Deck"
            dialog.onNewDeckCreated = { _: DeckId -> assertionCalled() }
            dialog.renameDeck("Rename Deck")

            activityScenario.onActivity { activity ->
                assertThat(
                    "Snackbar should confirm rename for valid name",
                    activity.snackbarText,
                    equalTo(getResourceString(CommonString.deck_renamed)),
                )
            }
        }
    }

    @Test
    fun `renameDeck with activity context shows snackbar for invalid name`() {
        activityScenario.onActivity { activity ->
            val dialog = CreateDeckDialog(activity, "Create deck", DeckDialogType.RENAME_DECK, null, col.decks.id("Old Deck"))
            dialog.deckName = "Old Deck"
            dialog.onNewDeckCreated = { _: DeckId -> }
            dialog.renameDeck("   ")
            Shadows.shadowOf(Looper.getMainLooper()).idle()

            assertThat(
                "Snackbar should show invalid name error for blank name",
                activity.snackbarText,
                equalTo(getResourceString(CommonString.invalid_deck_name)),
            )
        }
    }

    @Test
    fun `renameDeck with non-activity context shows toast for valid name`() {
        activityScenario.onActivity { activity ->
            val dialog =
                CreateDeckDialog(
                    ContextWrapper(activity),
                    "Create deck",
                    DeckDialogType.RENAME_DECK,
                    null,
                    col.decks.id("Old Deck"),
                )
            dialog.deckName = "Old Deck"
            dialog.onNewDeckCreated = { _: DeckId -> }
            dialog.renameDeck("Rename Deck")

            assertThat(
                "Toast should confirm rename for valid name",
                ShadowToast.getTextOfLatestToast(),
                equalTo(getResourceString(CommonString.deck_renamed)),
            )
        }
    }

    @Test
    fun `renameDeck with non-activity context shows toast for invalid name`() {
        activityScenario.onActivity { activity ->
            val dialog =
                CreateDeckDialog(
                    ContextWrapper(activity),
                    "Create deck",
                    DeckDialogType.RENAME_DECK,
                    null,
                    col.decks.id("Old Deck"),
                )
            dialog.deckName = "Old Deck"
            dialog.onNewDeckCreated = { _: DeckId -> }
            dialog.renameDeck("   ")

            assertThat(
                "Toast should show invalid name error for blank name",
                ShadowToast.getTextOfLatestToast(),
                equalTo(getResourceString(CommonString.invalid_deck_name)),
            )
        }
    }

    @Test
    fun `create is disabled while the name check is pending`() {
        activityScenario.onActivity { activity ->
            val dialog = CreateDeckDialog(activity, "Create deck", DeckDialogType.DECK, null).apply { onNewDeckCreated = {} }.showDialog()
            withPendingNameChecks {
                dialog.input = "New"
                assertFalse(dialog.positiveButton.isEnabled, "Create is enabled before the name is checked")
            }
            assertTrue(dialog.positiveButton.isEnabled, "Create is enabled once the name is checked")
        }
    }

    @Test
    fun `renaming before the name check finishes does not rename onto an existing deck`() {
        addDeck("Existing")
        val did = addDeck("Old")
        activityScenario.onActivity { activity ->
            val dialog =
                CreateDeckDialog(activity, "Rename deck", DeckDialogType.RENAME_DECK, null, did)
                    .apply {
                        deckName = "Old"
                        onNewDeckCreated = {}
                    }.showDialog()
            withPendingNameChecks { scheduler ->
                dialog.input = "Existing"
                dialog.getInputField().onEditorAction(EditorInfo.IME_ACTION_DONE)
                scheduler.advanceUntilIdle()
            }
        }
        assertEquals("Old", col.decks.name(did))
    }

    @Test
    fun `a name check finishing after the name is cleared does not enable create`() {
        activityScenario.onActivity { activity ->
            val dialog = CreateDeckDialog(activity, "Create deck", DeckDialogType.DECK, null).apply { onNewDeckCreated = {} }.showDialog()
            withPendingNameChecks { scheduler ->
                dialog.input = "New"
                dialog.input = ""
                scheduler.advanceUntilIdle()
                assertFalse(dialog.positiveButton.isEnabled, "Create is enabled for an empty name")
            }
        }
    }

    @Test
    fun `a name check finishing after the name is cleared shows no error`() {
        addDeck("Existing")
        activityScenario.onActivity { activity ->
            val dialog = CreateDeckDialog(activity, "Create deck", DeckDialogType.DECK, null).apply { onNewDeckCreated = {} }.showDialog()
            withPendingNameChecks { scheduler ->
                dialog.input = "Existing"
                dialog.input = ""
                scheduler.advanceUntilIdle()
                assertNull(dialog.getInputTextLayout().error, "an error is shown for an empty name")
            }
        }
    }

    private fun withPendingNameChecks(block: (TestCoroutineScheduler) -> Unit) {
        val queue = StandardTestDispatcher()
        val previousQueue = CollectionManager.setTestDispatcher(queue, useReentrantLock = false)
        try {
            block(queue.scheduler)
        } finally {
            queue.scheduler.advanceUntilIdle()
            CollectionManager.setTestDispatcher(previousQueue)
        }
    }

    /**
     * Tests a scenario with a [DeckPicker] hosting a [CreateDeckDialog].
     * The second parameter of the callback ('assertionCalled') must be called for this to pass
     */
    private fun ensureExecutionOfScenario(
        deckDialogType: DeckDialogType,
        parentId: DeckId? = null,
        renamedDeckId: DeckId? = null,
        callback: ((CreateDeckDialog, (() -> Unit)) -> Unit),
    ) {
        activityScenario.onActivity { activity: DeckPicker ->
            val assertionCalled = AtomicReference(false)
            callback(CreateDeckDialog(activity, "Create deck", deckDialogType, parentId, renamedDeckId)) {
                assertionCalled.set(true)
            }
            assertThat("no call to assertionCalled()", assertionCalled.get(), equalTo(true))
        }
    }

    @Suppress("SameParameterValue")
    private fun deckTreeName(
        start: Int,
        end: Int,
        prefix: String,
    ): String =
        List(end - start + 1) { "${prefix}${it + start}" }
            .joinToString("::")
}

/** Test of [CreateDeckDialog] */
class CreateDeckDialogNonAndroidTest {
    @Test
    fun `number larger than nine detection`() {
        fun assertLargerThanNine(
            reason: String?,
            input: String,
            result: Boolean,
        ) = assertThat(reason, input.containsNumberLargerThanNine(), equalTo(result))

        assertLargerThanNine("empty string", "", false)
        assertLargerThanNine("text", "deck name", false)
        assertLargerThanNine("less than ten", "9. - Chemicals", false)
        assertLargerThanNine("Ten or greater", "10. - Chemicals", true)
        assertLargerThanNine("Ten or greater", "99. - Chemicals", true)
        assertLargerThanNine("zero prefix", "09. - Chemicals", false)
        assertLargerThanNine("time", "10:50:59", false)
        assertLargerThanNine("time", "Filtered Deck 22:34", false)
        assertLargerThanNine("suffix", "Deck 34", true)
    }
}

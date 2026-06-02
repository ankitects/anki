// noinspection MissingCopyrightHeader #8659
package com.ichi2.anki

import android.Manifest.permission.INTERNET
import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.view.Menu
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.IntentCompat
import androidx.core.content.edit
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.view.children
import androidx.test.core.app.ActivityScenario
import anki.collection.opChanges
import anki.scheduler.CardAnswer.Rating
import app.cash.turbine.test
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.common.utils.annotation.KotlinCleanup
import com.ichi2.anki.deckpicker.DeckPickerViewModel
import com.ichi2.anki.dialogs.DatabaseErrorDialog
import com.ichi2.anki.dialogs.DatabaseErrorDialog.DatabaseErrorDialogType
import com.ichi2.anki.dialogs.DeckPickerContextMenu.DeckPickerContextMenuOption
import com.ichi2.anki.dialogs.DeckPickerContextMenuResult
import com.ichi2.anki.dialogs.setDeckPickerContextMenuResult
import com.ichi2.anki.dialogs.utils.title
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.navigation.AnkiDroidNavigator
import com.ichi2.anki.observability.ChangeManager
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.ui.windows.permissions.PermissionsActivity
import com.ichi2.anki.ui.windows.permissions.PermissionsActivity.Companion.PERMISSIONS_SET_EXTRA
import com.ichi2.anki.utils.Destination
import com.ichi2.anki.utils.ext.defaultConfig
import com.ichi2.anki.utils.ext.dismissAllDialogFragments
import com.ichi2.testutils.BackendEmulatingOpenConflict
import com.ichi2.testutils.BackupManagerTestUtilities
import com.ichi2.testutils.common.Flaky
import com.ichi2.testutils.common.OS
import com.ichi2.testutils.ext.addBasicNoteWithOp
import com.ichi2.testutils.ext.menu
import com.ichi2.testutils.grantWritePermissions
import com.ichi2.testutils.revokeWritePermissions
import com.ichi2.testutils.withDeniedPermissions
import com.ichi2.testutils.withWritePermissions
import kotlinx.coroutines.flow.merge
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsInAnyOrder
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.notNullValue
import org.hamcrest.Matchers.nullValue
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.whenever
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper
import timber.log.Timber
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

typealias ContextMenuOption = DeckPickerContextMenuOption

@KotlinCleanup("SPMockBuilder")
@RunWith(ParameterizedRobolectricTestRunner::class)
class DeckPickerTest : RobolectricTest() {
    @ParameterizedRobolectricTestRunner.Parameter
    @JvmField // required for Parameter
    var qualifiers: String? = null

    companion object {
        @ParameterizedRobolectricTestRunner.Parameters
        @JvmStatic // required for initParameters
        fun initParameters(): Collection<String> = listOf("normal", "xlarge")
    }

    @Before
    fun before() {
        RuntimeEnvironment.setQualifiers(qualifiers)
        setIntroductionSlidesShown(true)
    }

    @Test
    fun `receiving opExecuted call doesn't crash if ViewModel is not yet initialized`() {
        // Instantiate DeckPicker directly to simulate a state where the object exists,
        // but Android lifecycle callback (onCreate) have not yet executed.
        DeckPicker()

        assertDoesNotThrow { ChangeManager.notifySubscribers(opChanges { studyQueues = true }, null) }
    }

    @Test
    @SuppressLint("UseKtx")
    fun getPreviousVersionUpgradeFrom201to292() {
        val newVersion = 20900302 // 2.9.2
        val preferences = mock(SharedPreferences::class.java)
        whenever(preferences.getLong(DeckPickerViewModel.UPGRADE_VERSION_KEY, newVersion.toLong()))
            .thenThrow(ClassCastException::class.java)
        whenever(preferences.getInt(DeckPickerViewModel.UPGRADE_VERSION_KEY, newVersion))
            .thenThrow(ClassCastException::class.java)
        whenever(preferences.getString(DeckPickerViewModel.UPGRADE_VERSION_KEY, ""))
            .thenReturn("2.0.1")
        val editor = mock(SharedPreferences.Editor::class.java)
        whenever(preferences.edit()).thenReturn(editor)
        val updated = mock(SharedPreferences.Editor::class.java)
        whenever(editor.remove(DeckPickerViewModel.UPGRADE_VERSION_KEY)).thenReturn(updated)
        ActivityScenario.launch(DeckPicker::class.java).use { scenario ->
            scenario.onActivity { deckPicker: DeckPicker ->
                val previousVersion =
                    deckPicker.viewModel.getPreviousVersion(preferences, newVersion.toLong())
                assertEquals(0, previousVersion)
            }
        }
        verify(editor, times(1)).remove(DeckPickerViewModel.UPGRADE_VERSION_KEY)
        verify(updated, times(1)).apply()
    }

    @Test
    @SuppressLint("UseKtx")
    fun getPreviousVersionUpgradeFrom202to292() {
        val newVersion: Long = 20900302 // 2.9.2
        val preferences = mock(SharedPreferences::class.java)
        whenever(preferences.getLong(DeckPickerViewModel.UPGRADE_VERSION_KEY, newVersion))
            .thenThrow(ClassCastException::class.java)
        whenever(preferences.getInt(DeckPickerViewModel.UPGRADE_VERSION_KEY, 20900203))
            .thenThrow(ClassCastException::class.java)
        whenever(preferences.getString(DeckPickerViewModel.UPGRADE_VERSION_KEY, ""))
            .thenReturn("2.0.2")
        val editor = mock(SharedPreferences.Editor::class.java)
        whenever(preferences.edit()).thenReturn(editor)
        val updated = mock(SharedPreferences.Editor::class.java)
        whenever(editor.remove(DeckPickerViewModel.UPGRADE_VERSION_KEY)).thenReturn(updated)
        ActivityScenario.launch(DeckPicker::class.java).use { scenario ->
            scenario.onActivity { deckPicker: DeckPicker ->
                val previousVersion = deckPicker.viewModel.getPreviousVersion(preferences, newVersion)
                assertEquals(40, previousVersion)
            }
        }
        verify(editor, times(1)).remove(DeckPickerViewModel.UPGRADE_VERSION_KEY)
        verify(updated, times(1)).apply()
    }

    @Test
    @SuppressLint("UseKtx")
    fun getPreviousVersionUpgradeFrom281to291() {
        val prevVersion = 20800301 // 2.8.1
        val newVersion: Long = 20900301 // 2.9.1
        val preferences = mock(SharedPreferences::class.java)
        whenever(preferences.getLong(DeckPickerViewModel.UPGRADE_VERSION_KEY, newVersion))
            .thenThrow(ClassCastException::class.java)
        whenever(preferences.getInt(DeckPickerViewModel.UPGRADE_VERSION_KEY, 20900203))
            .thenReturn(prevVersion)
        val editor = mock(SharedPreferences.Editor::class.java)
        whenever(preferences.edit()).thenReturn(editor)
        val updated = mock(SharedPreferences.Editor::class.java)
        whenever(editor.remove(DeckPickerViewModel.UPGRADE_VERSION_KEY)).thenReturn(updated)
        ActivityScenario.launch(DeckPicker::class.java).use { scenario ->
            scenario.onActivity { deckPicker: DeckPicker ->
                val previousVersion = deckPicker.viewModel.getPreviousVersion(preferences, newVersion)
                assertEquals(prevVersion.toLong(), previousVersion)
            }
        }
        verify(editor, times(1)).remove(DeckPickerViewModel.UPGRADE_VERSION_KEY)
        verify(updated, times(1)).apply()
    }

    @Test
    fun getPreviousVersionUpgradeFrom291to292() {
        val prevVersion: Long = 20900301 // 2.9.1
        val newVersion: Long = 20900302 // 2.9.2
        val preferences = mock(SharedPreferences::class.java)
        whenever(preferences.getLong(DeckPickerViewModel.UPGRADE_VERSION_KEY, newVersion))
            .thenReturn(prevVersion)
        val editor = mock(SharedPreferences.Editor::class.java)
        whenever(preferences.edit()).thenReturn(editor)
        ActivityScenario.launch(DeckPicker::class.java).use { scenario ->
            scenario.onActivity { deckPicker: DeckPicker ->
                val previousVersion = deckPicker.viewModel.getPreviousVersion(preferences, newVersion)
                assertEquals(prevVersion, previousVersion)
            }
        }
        verify(editor, never()).remove(DeckPickerViewModel.UPGRADE_VERSION_KEY)
    }

    @Test
    fun limitAppliedAfterReview() {
        val sched = col.sched
        val dconf = col.decks.defaultConfig
        assertNotNull(dconf)
        dconf.new.perDay = 10
        col.decks.save(dconf)
        for (i in 0..10) {
            addBasicNote("Which card is this ?", i.toString())
        }
        // This set a card as current card
        sched.card
        ensureCollectionLoadIsSynchronous()

        deckPicker {
            assertEquals(
                10,
                dueTree!!
                    .children[0]
                    .newCount
                    .toLong(),
            )
        }
    }

    @Test
    fun confirmDeckDeletionDeletesEmptyDeck() {
        val did = addDeck("Hello World")
        assertThat("Deck was added", col.decks.count(), equalTo(2))
        deckPicker {
            viewModel.deleteDeck(did).join()
            assertThat("deck was deleted", col.decks.count(), equalTo(1))
        }
    }

    @Test
    fun databaseLockedTest() {
        // don't call .onCreate
        val deckPicker = Robolectric.buildActivity(DeckPickerEx::class.java, Intent()).get()
        deckPicker.handleStartupFailure(InitialActivity.StartupFailure.DatabaseLocked)
        assertThat(
            deckPicker.databaseErrorDialog,
            equalTo(DatabaseErrorDialogType.DIALOG_DB_LOCKED),
        )
    }

    @Test
    fun databaseLockedWithPermissionIntegrationTest() {
        AnkiDroidApp.sentExceptionReportHack = false
        try {
            BackendEmulatingOpenConflict.enable()
            InitialActivityWithConflictTest.setupForDatabaseConflict()
            val d =
                super.startActivityNormallyOpenCollectionWithIntent(
                    DeckPickerEx::class.java,
                    Intent(),
                )
            assertThat(
                "A specific dialog for a conflict should be shown",
                d.databaseErrorDialog,
                equalTo(DatabaseErrorDialogType.DIALOG_DB_LOCKED),
            )
            assertThat(
                "No exception reports should be thrown",
                AnkiDroidApp.sentExceptionReportHack,
                equalTo(false),
            )
        } finally {
            BackendEmulatingOpenConflict.disable()
            InitialActivityWithConflictTest.setupForDefault()
        }
    }

    @Test
    @Ignore("Flaky. Try to unflake now we're using coroutines")
    fun databaseLockedNoPermissionIntegrationTest() {
        // no permissions -> grant permissions -> db locked
        try {
            InitialActivityWithConflictTest.setupForDefault()
            BackendEmulatingOpenConflict.enable()

            deckPickerEx {
                // grant permissions
                InitialActivityWithConflictTest.setupForDatabaseConflict()
                onStoragePermissionGranted()
                assertThat(
                    "A specific dialog for a conflict should be shown",
                    databaseErrorDialog,
                    equalTo(DatabaseErrorDialogType.DIALOG_DB_LOCKED),
                )
            }
        } finally {
            BackendEmulatingOpenConflict.disable()
            InitialActivityWithConflictTest.setupForDefault()
        }
    }

    @Test
    fun deckPickerOpensWithHelpMakeAnkiDroidBetterDialog() {
        // Refactor: It would be much better to use a spy - see if we can get this into Robolectric
        try {
            grantWritePermissions()
            BackupManagerTestUtilities.setupSpaceForBackup(targetContext)
            // We don't show it if the user is new.
            targetContext
                .sharedPrefs()
                .edit { putString("lastVersion", "0.1") }
            val d =
                super.startActivityNormallyOpenCollectionWithIntent(
                    DeckPickerEx::class.java,
                    Intent(),
                )
            assertThat(
                "Analytics opt-in should be displayed",
                d.displayedAnalyticsOptIn,
                equalTo(true),
            )
        } finally {
            revokeWritePermissions()
            BackupManagerTestUtilities.reset()
        }
    }

    @Test
    fun doNotShowOptionsMenuWhenCollectionInaccessible() =
        withNullCollection {
            deckPicker {
                viewModel.refreshMenuState()
                assertThat(
                    "Options menu not displayed when collection is inaccessible",
                    viewModel.optionsMenuState,
                    equalTo(null),
                )
            }
        }

    @Test
    fun showOptionsMenuWhenCollectionAccessible() =
        withWritePermissions {
            deckPicker {
                viewModel.refreshMenuState()
                assertThat(
                    "Options menu displayed when collection is accessible",
                    viewModel.optionsMenuState,
                    notNullValue(),
                )
            }
        }

    @Test
    fun onResumeLoadCollectionFailureWithInaccessibleCollection() {
        revokeWritePermissions()
        withNullCollection {
            deckPicker {
                // Neither collection, not its models will be initialized without storage permission

                // assert: Lazy Collection initialization CollectionTask.LoadCollectionComplete fails
                assertFailsWith<Exception> { getColUnsafe }
            }
        }
    }

    @Test
    fun onResumeLoadCollectionSuccessWithAccessibleCollection() =
        withWritePermissions {
            deckPicker {
                assertThat(
                    "Collection initialization ensured by CollectionTask.LoadCollectionComplete",
                    getColUnsafe,
                    notNullValue(),
                )
                assertThat(
                    "Collection Models Loaded",
                    getColUnsafe.notetypes,
                    notNullValue(),
                )
            }
        }

    @Test
    fun `ContextMenu starts expected dialogs when specific options are selected`() =
        deckPicker {
            val didA = addDeck("Deck 1")

            selectContextMenuOption(ContextMenuOption.RENAME_DECK, didA)
            assertDialogTitleEquals("Rename deck")
            dismissAllDialogFragments()

            selectContextMenuOption(ContextMenuOption.CREATE_SUBDECK, didA)
            assertDialogTitleEquals("Create subdeck")
            dismissAllDialogFragments()

            selectContextMenuOption(ContextMenuOption.CUSTOM_STUDY, didA)
            assertDialogTitleEquals("Custom study")
            dismissAllDialogFragments()

//            TODO test code enters in a recursion in BasicItemSelectedListener inside ExportDialog
//            supportFragmentManager.selectContextMenuOption(DeckPickerContextMenuOption.EXPORT_DECK, didA)
//            assertAlertDialogTitleEquals("Export")
//            dismissAllDialogFragments()
        }

    /** Simulates a selection in the context menu by setting the specific result in FragmentManager */
    private fun DeckPicker.selectContextMenuOption(
        option: DeckPickerContextMenuOption,
        deckId: DeckId,
    ) = supportFragmentManager.setDeckPickerContextMenuResult(
        DeckPickerContextMenuResult(deckId = deckId, option = option),
    )

    private fun assertDialogTitleEquals(expectedTitle: String) {
        val actualTitle = (ShadowDialog.getLatestDialog() as AlertDialog).title
        Timber.d("titles = \"$actualTitle\", \"$expectedTitle\"")
        assertEquals(expectedTitle, actualTitle)
    }

    @Test
    fun `ContextMenu starts expected activities when specific options are selected`() =
        deckPicker {
            suspend fun DeckPicker.selectContextMenuOptionForActivity(
                option: ContextMenuOption,
                deckId: DeckId,
            ): Intent {
                var result: Any? = null
                merge(viewModel.flowOfDestination, viewModel.flowOfNavigate).test(1.seconds) {
                    selectContextMenuOption(option, deckId)
                    result = awaitItem()
                }
                return when (val emitted = result!!) {
                    is Destination -> emitted.toIntent(this)
                    is com.ichi2.anki.common.destinations.Destination -> AnkiDroidNavigator.toIntent(emitted)
                    else -> error("Unexpected destination type: $emitted")
                }
            }

            val didA = addDeck("Deck 1")
            val didDynamicA = addDynamicDeck("Deck Dynamic 1")

            val noteEditor = selectContextMenuOptionForActivity(DeckPickerContextMenuOption.ADD_CARD, didA)
            assertEquals("com.ichi2.anki.NoteEditorActivity", noteEditor.component!!.className)
            onBackPressedDispatcher.onBackPressed()

            val browser = selectContextMenuOptionForActivity(DeckPickerContextMenuOption.BROWSE_CARDS, didA)
            assertEquals("com.ichi2.anki.CardBrowser", browser.component!!.className)
            onBackPressedDispatcher.onBackPressed()

            // select deck options for a normal deck
            val deckOptionsNormal = selectContextMenuOptionForActivity(DeckPickerContextMenuOption.DECK_OPTIONS, didA)
            assertEquals("com.ichi2.anki.SingleFragmentActivity", deckOptionsNormal.component!!.className)
            onBackPressedDispatcher.onBackPressed()

            // select deck options for a dynamic deck
            val deckOptionsDynamic = selectContextMenuOptionForActivity(DeckPickerContextMenuOption.DECK_OPTIONS, didDynamicA)
            assertEquals("com.ichi2.anki.utils.ConfigAwareSingleFragmentActivity", deckOptionsDynamic.component!!.className)
            onBackPressedDispatcher.onBackPressed()

            Prefs.newReviewRemindersEnabled = true
            val scheduleReminders = selectContextMenuOptionForActivity(DeckPickerContextMenuOption.SCHEDULE_REMINDERS, didA)
            assertEquals("com.ichi2.anki.SingleFragmentActivity", scheduleReminders.component!!.className)
            onBackPressedDispatcher.onBackPressed()
        }

    @Test
    fun `ContextMenu deletes deck when selecting DELETE_DECK`() =
        deckPicker {
            val didA = addDeck("Deck 1")
            selectContextMenuOption(ContextMenuOption.DELETE_DECK, didA)
            assertThat(getColUnsafe.decks.allNamesAndIds().map { it.id }, not(containsInAnyOrder(didA)))
        }

    @Test
    fun `ContextMenu creates deck shortcut when selecting CREATE_SHORTCUT`() =
        deckPicker {
            val didA = addDeck("Deck 1")
            selectContextMenuOption(ContextMenuOption.CREATE_SHORTCUT, didA)
            // Wait for the shortcut creation to complete
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            assertEquals(
                "Deck 1",
                ShortcutManagerCompat.getShortcuts(this, ShortcutManagerCompat.FLAG_MATCH_PINNED).first().shortLabel,
            )
        }

    @Test
    @Flaky(OS.ALL)
    fun `ContextMenu unburied cards when selecting UNBURY`() =
        deckPicker {
            TimeManager.reset()
            // stop 'next day' code running, which calls 'unbury'
            updateDeckList()
            val deckId = addDeck("Deck 1")
            getColUnsafe.decks.select(deckId)
            getColUnsafe.notetypes.byName("Basic")!!.did = deckId
            val card = addBasicNote("front", "back").firstCard()
            getColUnsafe.sched.buryCards(listOf(card.id))
            updateDeckList()
            advanceRobolectricLooper()
            assertEquals(1, visibleDeckCount)
            assertTrue(getColUnsafe.sched.haveBuried(), "Deck should have buried cards")
            selectContextMenuOption(ContextMenuOption.UNBURY, deckId)
            kotlin.test.assertFalse(getColUnsafe.sched.haveBuried())
        }

    @Test
    fun `ContextMenu testDynRebuildAndEmpty`() =
        deckPicker {
            val cardIds =
                (0..3)
                    .map { addBasicNote("$it", "").firstCard().id }
            assertTrue(allCardsInSameDeck(cardIds, 1))
            val deckId = addDynamicDeck("Deck 1")
            getColUnsafe.sched.rebuildFilteredDeck(deckId)
            assertTrue(allCardsInSameDeck(cardIds, deckId))
            updateDeckList()
            assertEquals(1, visibleDeckCount)

            selectContextMenuOption(ContextMenuOption.CUSTOM_STUDY_EMPTY, deckId)

            assertTrue(allCardsInSameDeck(cardIds, 1))

            selectContextMenuOption(ContextMenuOption.CUSTOM_STUDY_REBUILD, deckId)

            assertTrue(allCardsInSameDeck(cardIds, deckId))
        }

    private fun allCardsInSameDeck(
        cardIds: List<Long>,
        deckId: DeckId,
    ): Boolean = cardIds.all { col.getCard(it).did == deckId }

    @Test
    fun checkDisplayOfStudyOptionsOnTablet() {
        assumeTrue("We are running on a tablet", qualifiers!!.contains("xlarge"))
        val deckPickerEx =
            super.startActivityNormallyOpenCollectionWithIntent(
                DeckPickerEx::class.java,
                Intent(),
            )
        val studyOptionsFragment =
            deckPickerEx.supportFragmentManager.findFragmentById(R.id.studyoptions_fragment) as StudyOptionsFragment?
        assertThat(
            "Study options should show on start on tablet",
            studyOptionsFragment,
            notNullValue(),
        )
    }

    @Test
    fun checkIfReturnsTrueWhenAtLeastOneDeckIsDisplayed() {
        addDeck("Hello World")
        // Reason for using 2 as the number of decks -> This deck + Default deck
        assertThat("Deck added", col.decks.count(), equalTo(2))

        deckPicker {
            assertThat(
                "Deck is being displayed",
                hasAtLeastOneDeckBeingDisplayed(),
                equalTo(true),
            )
        }
    }

    @Test
    fun checkIfReturnsFalseWhenNoDeckIsDisplayed() {
        // Only default deck would be there in the count, hence using the value as 1.
        // Default deck does not get displayed in the DeckPicker if the default deck is empty.
        assertThat("Contains only default deck", col.decks.count(), equalTo(1))

        deckPicker {
            assertThat(
                "No deck is being displayed",
                hasAtLeastOneDeckBeingDisplayed(),
                equalTo(false),
            )
        }
    }

    @Test
    fun `unbury is usable - Issue 15050`() {
        // We had an issue where 'Unbury' was not visible
        // This was because the deck selection was not changed when a long press occurred

        // one empty deck to be initially selected, one with cards to check 'unbury' status
        val emptyDeck = addDeck("No Cards")
        val deckWithCards = addDeck("With Cards")
        updateDeckConfig(deckWithCards) { new.bury = true }

        // Add a note with 2 cards in deck "With Cards", one of these cards is to be buried
        col.notetypes.byName("Basic (and reversed card)")!!.also { noteType ->
            col.notetypes.save(noteType.apply { did = deckWithCards })
        }
        addBasicAndReversedNote()

        // Answer 'Easy' for one of the cards, burying the other
        col.decks.select(deckWithCards)
        col.sched.deckDueTree() // ? if not called, decks.select(toSelect) un-buries a card
        col.sched.answerCard(col.sched.card!!, Rating.EASY)
        assertThat("the other card is buried", col.sched.card, nullValue())

        // select a deck with no cards
        col.decks.select(emptyDeck)
        assertThat("unbury is not visible: deck has no cards", !col.sched.haveBuried())

        deckPicker {
            assertThat("deck focus is set", viewModel.focusedDeck, equalTo(emptyDeck))

            // ACT: open up the Deck Context Menu
            val deckToClick =
                deckPickerBinding.decks.children.single {
                    it.findViewById<TextView>(R.id.deck_name).text == "With Cards"
                }
            deckToClick.performLongClick()

            // ASSERT
            advanceRobolectricLooper() // ensure that 'focusedDeck' is current
            assertThat("unbury is visible: one card is buried", col.sched.haveBuried())
            assertThat("deck focus has changed", viewModel.focusedDeck, equalTo(deckWithCards))
        }
    }

    @Test
    fun `undo menu item is updated after undoableOp call`() =
        deckPicker {
            fun DeckPicker.getUndoTitle() = menu().findItem(R.id.action_undo).title.toString()

            fun waitForMenu() = ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

            suspend fun DeckPicker.undo() {
                undoAndShowSnackbar()
                waitForMenu()
            }

            // enqueue two actions, neither of which affect the study queues
            val note = addBasicNoteWithOp()
            note.updateOp { this.fields[0] = "baz" }

            waitForMenu()
            assertThat(getUndoTitle(), containsString("Update Note"))
            undo()
            assertThat(getUndoTitle(), containsString("Add Note"))
        }

    @Test
    fun `baseSnackbarBuilder has no anchor when FAB is hidden`() =
        deckPicker {
            val fab = findViewById<View>(R.id.fab_main)
            fab.visibility = View.GONE

            val snackbar = showSnackbar("test")

            snackbar?.let { baseSnackbarBuilder.invoke(it) }

            assertThat(
                "anchorView must be null when FAB is not visible",
                snackbar?.anchorView,
                nullValue(),
            )
        }

    @Test
    fun `baseSnackbarBuilder anchors to FAB when visible`() =
        deckPicker {
            val fab = findViewById<View>(R.id.fab_main)
            fab.visibility = View.VISIBLE

            val snackbar = showSnackbar("test")
            snackbar?.let { baseSnackbarBuilder.invoke(it) }

            assertThat(
                "anchorView is the FAB when visible",
                snackbar?.anchorView,
                equalTo(fab),
            )
        }

    @Test
    fun `On a new startup, the App Intro is displayed`() =
        deckPicker(skipIntroduction = false) {
            val nextIntent = Shadows.shadowOf(this).nextStartedActivity

            assertThat(
                "App Intro should be started on a new startup",
                nextIntent.component?.className,
                equalTo(IntroductionActivity::class.java.name),
            )
        }

    @Suppress("RedundantValueArgument")
    @Test
    fun `On not a new startup, the App Intro is not displayed`() =
        deckPicker(skipIntroduction = true) {
            val nextIntent = Shadows.shadowOf(this).nextStartedActivity

            assertThat(
                "No other activity should be started when not a new startup",
                nextIntent,
                equalTo(null),
            )
        }

    @Test
    fun `startup response is cleared after handling so it does not re-run on resume`() =
        deckPicker {
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            assertThat(
                "startup response cleared after handling so it does not re-run on resume",
                viewModel.flowOfStartupResponse.value,
                nullValue(),
            )
        }

    /** Regression test for [#20712](https://github.com/ankidroid/Anki-Android/issues/20712) */
    @Test
    fun `SQLiteDatabaseCorruptException in runCatching shows database error dialog`() =
        deckPickerEx {
            runCatching { throw SQLiteDatabaseCorruptException() }
            assertThat(databaseErrorDialog, equalTo(DatabaseErrorDialogType.DIALOG_LOAD_FAILED))
        }

    /**
     * Emulates a null collection and a `BackendDbLockedException`
     *
     * @see enableNullCollection
     */
    private fun withNullCollection(block: () -> Unit) =
        try {
            enableNullCollection()
            block()
        } finally {
            disableNullCollection()
        }

    @Test
    fun `when INTERNET is denied, PermissionsActivity is shown`() =
        runTest {
            withDeniedPermissions(INTERNET) {
                deckPicker {
                    val intent = assertNotNull(shadowOf(this@deckPicker).nextStartedActivity)

                    assertThat(
                        intent.component?.shortClassName,
                        equalTo(PermissionsActivity::class.java.name),
                    )

                    val extra = IntentCompat.getParcelableExtra(intent, PERMISSIONS_SET_EXTRA, PermissionSet::class.java)

                    assertNotNull(extra)
                    assertThat(extra.permissions, equalTo(listOf(INTERNET)))
                }
            }
        }

    enum class CollectionType(
        val assetFile: String,
        private val deckName: String,
    ) {
        SCHEMA_V_16("schema16.anki2", "ThisIsSchema16"),
        SCHEMA_V_250(
            "schema250.anki2",
            "ThisIsSchema250",
        ),
        ;

        fun isCollection(col: com.ichi2.anki.libanki.Collection): Boolean = col.decks.byName(deckName) != null
    }

    internal class DeckPickerEx : DeckPicker() {
        var databaseErrorDialog: DatabaseErrorDialogType? = null
        var displayedAnalyticsOptIn = false
        var optionsMenu: Menu? = null

        override fun showDatabaseErrorDialog(
            errorDialogType: DatabaseErrorDialogType,
            exceptionData: DatabaseErrorDialog.CustomExceptionData?,
        ) {
            databaseErrorDialog = errorDialogType
        }

        fun onStoragePermissionGranted() {
            onRequestPermissionsResult(
                REQUEST_STORAGE_PERMISSION,
                arrayOf(""),
                intArrayOf(PackageManager.PERMISSION_GRANTED),
            )
        }

        override fun displayAnalyticsOptInDialog() {
            displayedAnalyticsOptIn = true
            super.displayAnalyticsOptInDialog()
        }

        override fun onPrepareOptionsMenu(menu: Menu): Boolean {
            optionsMenu = menu
            return super.onPrepareOptionsMenu(menu)
        }
    }
}

fun RobolectricTest.setIntroductionSlidesShown(shown: Boolean) {
    getPreferences().edit {
        putBoolean(IntroductionActivity.INTRODUCTION_SLIDES_SHOWN, shown)
    }
}

fun RobolectricTest.deckPicker(
    exposeTestData: Boolean = false,
    skipIntroduction: Boolean = true,
    function: suspend DeckPicker.() -> Unit,
) = runTest {
    setIntroductionSlidesShown(skipIntroduction)
    val deckPicker =
        startActivityNormallyOpenCollectionWithIntent(
            if (exposeTestData) DeckPickerTest.DeckPickerEx::class.java else DeckPicker::class.java,
            Intent(),
        )
    function(deckPicker)
}

/**
 * Runs [function], providing it access to test-only properties from [DeckPickerTest.DeckPickerEx]
 *
 * @see DeckPickerTest.DeckPickerEx
 */
internal fun RobolectricTest.deckPickerEx(
    skipIntroduction: Boolean = true,
    function: suspend DeckPickerTest.DeckPickerEx.() -> Unit,
) = deckPicker(exposeTestData = true, skipIntroduction = skipIntroduction) {
    function(this as DeckPickerTest.DeckPickerEx)
}

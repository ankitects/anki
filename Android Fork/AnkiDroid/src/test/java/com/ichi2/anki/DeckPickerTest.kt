// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.os.Bundle
import android.os.SystemClock
import android.text.Layout
import android.view.KeyEvent
import android.view.Menu
import android.view.MotionEvent
import android.view.View
import android.view.View.MeasureSpec
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.core.content.edit
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.Insets
import androidx.core.net.toUri
import androidx.core.view.ContentInfoCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.core.view.size
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.filters.SdkSuppress
import anki.backend.backendError
import anki.collection.opChanges
import anki.scheduler.CardAnswer.Rating
import app.cash.turbine.test
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.DeckPickerFloatingActionMenu.CloseAnimation
import com.ichi2.anki.browser.CardBrowserFragment
import com.ichi2.anki.browser.CardBrowserViewModel.RowSelection
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.common.utils.annotation.KotlinCleanup
import com.ichi2.anki.databinding.ActivityHomescreenBinding
import com.ichi2.anki.deckpicker.DeckFilters
import com.ichi2.anki.deckpicker.DeckPickerViewModel
import com.ichi2.anki.deckpicker.SyncIconState
import com.ichi2.anki.deckpicker.filterAndFlattenDisplay
import com.ichi2.anki.dialogs.DatabaseErrorDialog
import com.ichi2.anki.dialogs.DatabaseErrorDialog.DatabaseErrorDialogType
import com.ichi2.anki.dialogs.DeckPickerConfirmDeleteDeckDialog
import com.ichi2.anki.dialogs.DeckPickerContextMenu.DeckPickerContextMenuOption
import com.ichi2.anki.dialogs.DeckPickerContextMenuResult
import com.ichi2.anki.dialogs.DeckSelectionDialog
import com.ichi2.anki.dialogs.SyncErrorDialog
import com.ichi2.anki.dialogs.setDeckPickerContextMenuResult
import com.ichi2.anki.dialogs.utils.input
import com.ichi2.anki.dialogs.utils.message
import com.ichi2.anki.dialogs.utils.performPositiveClick
import com.ichi2.anki.dialogs.utils.title
import com.ichi2.anki.libanki.Consts.DEFAULT_DECK_ID
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.model.SelectableDeck
import com.ichi2.anki.navigation.AnkiDroidNavigator
import com.ichi2.anki.observability.ChangeManager
import com.ichi2.anki.pages.CongratsPage
import com.ichi2.anki.preferences.PreferencesActivity
import com.ichi2.anki.reviewreminders.ScheduleRemindersFragment
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.ui.RecyclerFastScroller
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.anki.utils.Destination
import com.ichi2.anki.utils.ext.defaultConfig
import com.ichi2.anki.utils.ext.dismissAllDialogFragments
import com.ichi2.anki.widgets.DeckAdapter
import com.ichi2.testutils.BackendEmulatingOpenConflict
import com.ichi2.testutils.BackupManagerTestUtilities
import com.ichi2.testutils.common.Flaky
import com.ichi2.testutils.common.OS
import com.ichi2.testutils.dispatchTouch
import com.ichi2.testutils.ext.addBasicNoteWithOp
import com.ichi2.testutils.ext.menu
import com.ichi2.testutils.ext.snackbarText
import com.ichi2.testutils.ext.text
import com.ichi2.testutils.grantWritePermissions
import com.ichi2.testutils.revokeWritePermissions
import com.ichi2.testutils.withPref
import com.ichi2.testutils.withWritePermissions
import com.ichi2.ui.AccessibleSearchView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import net.ankiweb.rsdroid.BackendException.BackendDbException.BackendDbCorruptException
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsInAnyOrder
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.greaterThan
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.notNullValue
import org.hamcrest.Matchers.nullValue
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertAll
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import com.ichi2.anki.common.android.R as CommonR

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
    fun `study shortcut on hidden Default shows the empty deck message`() {
        val other = addDeck("Other")
        col.setDeck(listOf(addBasicNote().firstCard().id), other)
        assertEquals(DEFAULT_DECK_ID, col.decks.selected())
        assertThat(col.sched.deckDueTree().find(DEFAULT_DECK_ID), nullValue())

        deckPicker {
            advanceRobolectricLooperUntil { deckPickerBinding.decks.adapter?.itemCount == 1 }
            shadowOf(this).clearNextStartedActivities()

            assertTrue(onKeyUp(KeyEvent.KEYCODE_S, KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_S)))
            advanceRobolectricLooperUntil { snackbarText == getString(CommonString.empty_deck) }

            assertEquals(DEFAULT_DECK_ID, col.decks.selected())
            assertThat(shadowOf(this).nextStartedActivity, nullValue())
        }
    }

    @Test
    fun `study shortcut on an empty filtered deck opens the finished screen`() {
        addBasicNote()
        val filtered = col.decks.newFiltered("Filtered")

        deckPicker {
            advanceRobolectricLooperUntil { deckPickerBinding.decks.adapter?.itemCount == 2 }
            shadowOf(this).clearNextStartedActivities()

            assertTrue(onKeyUp(KeyEvent.KEYCODE_S, KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_S)))
            advanceRobolectricLooperUntil { shadowOf(this).peekNextStartedActivity() != null }

            val intent = assertNotNull(shadowOf(this).nextStartedActivity)
            assertEquals(CongratsPage::class.java.name, intent.getStringExtra(SingleFragmentActivity.EXTRA_FRAGMENT_NAME))
            assertEquals(filtered, col.decks.selected())
        }
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
        whenever(preferences.getLong(DeckPickerViewModel.PREF_LAST_UPGRADE_VERSION, newVersion.toLong()))
            .thenThrow(ClassCastException::class.java)
        whenever(preferences.getInt(DeckPickerViewModel.PREF_LAST_UPGRADE_VERSION, newVersion))
            .thenThrow(ClassCastException::class.java)
        whenever(preferences.getString(DeckPickerViewModel.PREF_LAST_UPGRADE_VERSION, ""))
            .thenReturn("2.0.1")
        val editor = mock(SharedPreferences.Editor::class.java)
        whenever(preferences.edit()).thenReturn(editor)
        val updated = mock(SharedPreferences.Editor::class.java)
        whenever(editor.remove(DeckPickerViewModel.PREF_LAST_UPGRADE_VERSION)).thenReturn(updated)
        ActivityScenario.launch(DeckPicker::class.java).use { scenario ->
            scenario.onActivity { deckPicker: DeckPicker ->
                val previousVersion =
                    deckPicker.viewModel.getPreviousVersion(preferences, newVersion.toLong())
                assertEquals(0, previousVersion)
            }
        }
        verify(editor, times(1)).remove(DeckPickerViewModel.PREF_LAST_UPGRADE_VERSION)
        verify(updated, times(1)).apply()
    }

    @Test
    @SuppressLint("UseKtx")
    fun getPreviousVersionUpgradeFrom202to292() {
        val newVersion: Long = 20900302 // 2.9.2
        val preferences = mock(SharedPreferences::class.java)
        whenever(preferences.getLong(DeckPickerViewModel.PREF_LAST_UPGRADE_VERSION, newVersion))
            .thenThrow(ClassCastException::class.java)
        whenever(preferences.getInt(DeckPickerViewModel.PREF_LAST_UPGRADE_VERSION, 20900203))
            .thenThrow(ClassCastException::class.java)
        whenever(preferences.getString(DeckPickerViewModel.PREF_LAST_UPGRADE_VERSION, ""))
            .thenReturn("2.0.2")
        val editor = mock(SharedPreferences.Editor::class.java)
        whenever(preferences.edit()).thenReturn(editor)
        val updated = mock(SharedPreferences.Editor::class.java)
        whenever(editor.remove(DeckPickerViewModel.PREF_LAST_UPGRADE_VERSION)).thenReturn(updated)
        ActivityScenario.launch(DeckPicker::class.java).use { scenario ->
            scenario.onActivity { deckPicker: DeckPicker ->
                val previousVersion = deckPicker.viewModel.getPreviousVersion(preferences, newVersion)
                assertEquals(40, previousVersion)
            }
        }
        verify(editor, times(1)).remove(DeckPickerViewModel.PREF_LAST_UPGRADE_VERSION)
        verify(updated, times(1)).apply()
    }

    @Test
    @SuppressLint("UseKtx")
    fun getPreviousVersionUpgradeFrom281to291() {
        val prevVersion = 20800301 // 2.8.1
        val newVersion: Long = 20900301 // 2.9.1
        val preferences = mock(SharedPreferences::class.java)
        whenever(preferences.getLong(DeckPickerViewModel.PREF_LAST_UPGRADE_VERSION, newVersion))
            .thenThrow(ClassCastException::class.java)
        whenever(preferences.getInt(DeckPickerViewModel.PREF_LAST_UPGRADE_VERSION, 20900203))
            .thenReturn(prevVersion)
        val editor = mock(SharedPreferences.Editor::class.java)
        whenever(preferences.edit()).thenReturn(editor)
        val updated = mock(SharedPreferences.Editor::class.java)
        whenever(editor.remove(DeckPickerViewModel.PREF_LAST_UPGRADE_VERSION)).thenReturn(updated)
        ActivityScenario.launch(DeckPicker::class.java).use { scenario ->
            scenario.onActivity { deckPicker: DeckPicker ->
                val previousVersion = deckPicker.viewModel.getPreviousVersion(preferences, newVersion)
                assertEquals(prevVersion.toLong(), previousVersion)
            }
        }
        verify(editor, times(1)).remove(DeckPickerViewModel.PREF_LAST_UPGRADE_VERSION)
        verify(updated, times(1)).apply()
    }

    @Test
    fun getPreviousVersionUpgradeFrom291to292() {
        val prevVersion: Long = 20900301 // 2.9.1
        val newVersion: Long = 20900302 // 2.9.2
        val preferences = mock(SharedPreferences::class.java)
        whenever(preferences.getLong(DeckPickerViewModel.PREF_LAST_UPGRADE_VERSION, newVersion))
            .thenReturn(prevVersion)
        val editor = mock(SharedPreferences.Editor::class.java)
        whenever(preferences.edit()).thenReturn(editor)
        ActivityScenario.launch(DeckPicker::class.java).use { scenario ->
            scenario.onActivity { deckPicker: DeckPicker ->
                val previousVersion = deckPicker.viewModel.getPreviousVersion(preferences, newVersion)
                assertEquals(prevVersion, previousVersion)
            }
        }
        verify(editor, never()).remove(DeckPickerViewModel.PREF_LAST_UPGRADE_VERSION)
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
    fun `deleting a deck blocks deck selection while collection access is queued`() =
        runTest {
            val deleted = addDeck("Deleted")
            val other = addDeck("Other")
            val selected = col.decks.selected()
            lateinit var deckPicker: DeckPicker
            withDeckPicker(deckCount = 0) { deckPicker = it }
            deckPicker.awaitVisibleDecks(listOf(deleted, other))

            val deletion =
                withQueuedCollectionAccess {
                    deckPicker.deleteDeck(deleted).also {
                        onView(withText("Other")).perform(click())
                    }
                }
            deletion.join()

            assertEquals("a deck was selected while deleting", selected, col.decks.selected())
        }

    @Test
    fun `Delete shortcut requests confirmation only in tablet layout`() {
        val deckName = "Deck to delete"
        val deckId = addDeck(deckName, setAsSelected = true)
        col.setDeck(listOf(addBasicNote().firstCard().id), deckId)

        deckPicker {
            val handled = onKeyUp(KeyEvent.KEYCODE_DEL, KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
            if (!fragmented) {
                assertFalse(handled)
                assertTrue(supportFragmentManager.fragments.none { it is DeckPickerConfirmDeleteDeckDialog })
                return@deckPicker
            }

            assertTrue(handled)
            advanceRobolectricLooperUntil {
                supportFragmentManager.fragments.any { it is DeckPickerConfirmDeleteDeckDialog && it.dialog?.isShowing == true }
            }
            val dialog =
                supportFragmentManager.fragments
                    .filterIsInstance<DeckPickerConfirmDeleteDeckDialog>()
                    .single()
                    .requireDialog() as AlertDialog
            assertEquals(getString(CommonString.delete_deck_title), dialog.title)
            assertThat(dialog.message, containsString(deckName))
            assertEquals(deckId, col.decks.byName(deckName)?.id)
            assertEquals(1, col.cardCount())

            dialog.performPositiveClick()
            advanceRobolectricLooperUntil { col.decks.byName(deckName) == null }
            assertEquals(0, col.cardCount())
        }
    }

    @Test
    fun databaseLockedTest() {
        // don't call .onCreate
        val controller = Robolectric.buildActivity(DeckPickerEx::class.java, Intent())
        saveControllerForCleanup(controller)
        val deckPicker = controller.get()
        deckPicker.handleStartupFailure(InitialActivity.StartupFailure.DatabaseLocked)
        assertThat(
            deckPicker.databaseErrorDialog,
            equalTo(DatabaseErrorDialogType.DIALOG_DB_LOCKED),
        )
    }

    /**
     * The 'Database Locked' dialog offers Settings, opened at the Advanced screen, where
     * changing the 'AnkiDroid directory' resolves the conflict (#21051). Without it, the
     * dialog's only button quits the app, contradicting the guidance in its own message.
     */
    @Test
    fun `database locked dialog links to Advanced settings`() {
        val deckPicker = startRegularActivity<DeckPicker>()
        deckPicker.showDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_DB_LOCKED)
        deckPicker.supportFragmentManager.executePendingTransactions()
        advanceRobolectricLooper()

        val settingsButton = (ShadowDialog.getLatestDialog() as AlertDialog).getButton(DialogInterface.BUTTON_NEUTRAL)
        assertEquals("Settings", settingsButton.text.toString())

        shadowOf(deckPicker).clearNextStartedActivities()
        settingsButton.performClick()
        advanceRobolectricLooper()

        val settings = shadowOf(deckPicker).nextStartedActivity
        assertNotNull(settings, "Advanced settings should be opened")
        assertEquals(PreferencesActivity::class.qualifiedName, settings.component?.className)
    }

    /** Until the storage setup flow exists (#19552), the user gets recovery options, not a crash */
    @Test
    fun `storage undecided shows load-failure options rather than crashing`() {
        // don't call .onCreate
        val controller = Robolectric.buildActivity(DeckPickerEx::class.java, Intent())
        saveControllerForCleanup(controller)
        val deckPicker = controller.get()
        deckPicker.handleStartupFailure(InitialActivity.StartupFailure.StorageUndecided)
        assertThat(
            deckPicker.databaseErrorDialog,
            equalTo(DatabaseErrorDialogType.DIALOG_LOAD_FAILED),
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

    private fun withBottomNavigationEnabled(action: () -> Unit) = withPref(Prefs::devBottomNavEnabled, true, action)

    private fun DeckPicker.openEmbeddedCardBrowser(): CardBrowserFragment {
        findViewById<BottomNavigationView>(R.id.bottom_navigation).selectedItemId =
            BottomNavController.NavigationItem.BROWSER.id
        supportFragmentManager.executePendingTransactions()
        return supportFragmentManager.findFragmentByTag("browser") as CardBrowserFragment
    }

    private fun DeckPicker.longPressDeck(name: String): View {
        val decks = deckPickerBinding.decks
        val adapter = decks.adapter as DeckAdapter
        // Background list diffs can finish after the initial main looper drain.
        advanceRobolectricLooperUntil(lazyMessage = { "Deck '$name' was not laid out" }) {
            val position = adapter.currentList.indexOfFirst { it.lastDeckNameComponent == name }
            position >= 0 && decks.findViewHolderForAdapterPosition(position) != null
        }
        val deck = adapter.currentList.single { it.lastDeckNameComponent == name }
        val position = adapter.currentList.indexOf(deck)
        decks.findViewHolderForAdapterPosition(position)!!.itemView.performLongClick()
        advanceRobolectricLooperUntil { adapter.currentList[position].isSelected }
        return decks.findViewHolderForAdapterPosition(position)!!.itemView
    }

    private fun keyDownEvent(
        keyCode: Int,
        modifiers: Int,
    ): KeyEvent = KeyEvent(0, 0, KeyEvent.ACTION_DOWN, keyCode, 0, modifiers)

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
            assertEquals("com.ichi2.anki.utils.ConfigAwareSingleFragmentActivity", scheduleReminders.component!!.className)
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
    fun `ContextMenu testDynRebuildAndEmpty`() {
        val cardIds = (0..3).map { addBasicNote("$it", "").firstCard().id }
        assertTrue(allCardsInSameDeck(cardIds, 1))
        val deckId = addDynamicDeck("Deck 1")
        col.sched.rebuildFilteredDeck(deckId)
        assertTrue(allCardsInSameDeck(cardIds, deckId))

        deckPicker(expectedDeckIds = listOf(deckId)) {
            selectContextMenuOptionAndAwait(ContextMenuOption.CUSTOM_STUDY_EMPTY, deckId) {
                allCardsInSameDeck(cardIds, 1)
            }

            selectContextMenuOptionAndAwait(ContextMenuOption.CUSTOM_STUDY_REBUILD, deckId) {
                allCardsInSameDeck(cardIds, deckId)
            }
        }
    }

    @Test
    fun `rebuilding a filtered deck refreshes the deck list - issue 14285`() =
        deckPicker {
            repeat(4) { addBasicNote("$it", "") }
            val deckId = addDynamicDeck("Filtered", search = "")
            selectContextMenuOption(ContextMenuOption.CUSTOM_STUDY_EMPTY, deckId)
            advanceRobolectricLooperUntil { dueTree?.find(deckId)?.newCount == 0 }

            selectContextMenuOption(ContextMenuOption.CUSTOM_STUDY_REBUILD, deckId)

            advanceRobolectricLooperUntil { dueTree?.find(deckId)?.newCount == 4 }
        }

    private suspend fun <T> TestScope.withQueuedCollectionAccess(block: suspend () -> T): T {
        val previousQueue = CollectionManager.setTestDispatcher(StandardTestDispatcher(testScheduler), useReentrantLock = false)
        try {
            return block()
        } finally {
            CollectionManager.setTestDispatcher(previousQueue)
        }
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
        assertThat(
            "DeckPicker should use the fragmented layout on tablet",
            deckPickerEx.fragmented,
            equalTo(true),
        )
    }

    // Menu invalidation can replace the action view, so resolve it on each access.
    private val DeckPicker.searchView: AccessibleSearchView
        get() = menu().findItem(R.id.deck_picker_action_filter).actionView as AccessibleSearchView

    @Test
    fun `dragging next to the tablet divider resizes the panes`() {
        assumeTrue("We are running on a tablet", qualifiers!!.contains("xlarge"))
        addBasicNote()
        val deckPicker = super.startActivityNormallyOpenCollectionWithIntent(DeckPickerEx::class.java, Intent())
        advanceRobolectricLooper()
        val divider = deckPicker.findViewById<View>(R.id.resizing_divider)
        val deckPickerPane = deckPicker.findViewById<View>(R.id.deck_picker_pane)
        val weightBefore = (deckPickerPane.layoutParams as LinearLayout.LayoutParams).weight
        val location = IntArray(2).also { divider.getLocationInWindow(it) }
        val startX = location[0] + divider.width / 2f - 16 * divider.resources.displayMetrics.density
        val y = location[1] + divider.height / 2f
        val window = deckPicker.window.decorView
        val downTime = SystemClock.uptimeMillis()

        window.dispatchTouchEvent(MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, startX, y, 0))
        for (step in 1..10) {
            window.dispatchTouchEvent(
                MotionEvent.obtain(downTime, downTime + step * 10L, MotionEvent.ACTION_MOVE, startX + step * 10f, y, 0),
            )
        }
        window.dispatchTouchEvent(MotionEvent.obtain(downTime, downTime + 200, MotionEvent.ACTION_UP, startX + 100f, y, 0))

        val weightAfter = (deckPickerPane.layoutParams as LinearLayout.LayoutParams).weight
        assertThat("the deck list grows when dragged towards the end", weightAfter, greaterThan(weightBefore))
    }

    @Test
    fun `deck search stays open while filtering`() {
        addBasicNote()
        repeat(10) { addDeck("Test Deck $it") }

        deckPicker {
            val searchItem = menu().findItem(R.id.deck_picker_action_filter)
            assertTrue(searchItem.isVisible)
            assertTrue(searchItem.expandActionView())

            for ((query, expectedCount) in listOf("s" to 10, "Test Deck 1" to 1, "missing" to 0, "" to 11)) {
                searchView.setQuery(query, false)
                advanceRobolectricLooperUntil { visibleDeckCount == expectedCount }

                val currentSearchItem = menu().findItem(R.id.deck_picker_action_filter)
                assertTrue(currentSearchItem.isActionViewExpanded, "Search should stay open for '$query'")
                assertEquals(query, searchView.query.toString())
            }
        }
    }

    @Test
    fun `deck search query is restored after recreation`() {
        addBasicNote()
        repeat(10) { addDeck("Test Deck $it") }

        ActivityScenario.launch(DeckPicker::class.java).use { scenario ->
            lateinit var retainedViewModel: DeckPickerViewModel
            scenario.onActivity { deckPicker ->
                advanceRobolectricLooper()
                retainedViewModel = deckPicker.viewModel
                val searchItem = deckPicker.menu().findItem(R.id.deck_picker_action_filter)
                assertTrue(searchItem.expandActionView())
                deckPicker.searchView.setQuery("Test Deck 1", false)
                advanceRobolectricLooperUntil { deckPicker.visibleDeckCount == 1 }
            }

            scenario.recreate()
            advanceRobolectricLooper()

            scenario.onActivity { deckPicker ->
                assertSame(retainedViewModel, deckPicker.viewModel)
                assertEquals("Test Deck 1", deckPicker.viewModel.deckSearchQuery)
                val searchItem = deckPicker.menu().findItem(R.id.deck_picker_action_filter)
                assertTrue(searchItem.isActionViewExpanded)
                assertEquals("Test Deck 1", deckPicker.searchView.query.toString())
                advanceRobolectricLooperUntil { deckPicker.visibleDeckCount == 1 }
                deckPicker.invalidateOptionsMenu()
            }
            advanceRobolectricLooper()

            scenario.onActivity { deckPicker ->
                val searchItem = deckPicker.menu().findItem(R.id.deck_picker_action_filter)
                assertTrue(searchItem.isActionViewExpanded)
                assertEquals("Test Deck 1", deckPicker.searchView.query.toString())
                assertEquals(1, deckPicker.visibleDeckCount)
                assertTrue(searchItem.collapseActionView())
                assertFalse(deckPicker.viewModel.isDeckSearchExpanded)
                assertEquals("", deckPicker.viewModel.deckSearchQuery)
                advanceRobolectricLooperUntil { deckPicker.visibleDeckCount == 11 }
            }
        }
    }

    @Test
    fun `deck search query is restored before the queued menu refresh`() =
        runTest {
            addBasicNote()
            repeat(10) { addDeck("Test Deck $it") }
            val deckPicker = startRegularActivity<DeckPicker>()
            advanceRobolectricLooper()
            val menu = deckPicker.menu()
            val searchItem = menu.findItem(R.id.deck_picker_action_filter)
            assertTrue(searchItem.expandActionView())
            deckPicker.searchView.setQuery("Test Deck 1", false)
            advanceRobolectricLooperUntil { deckPicker.visibleDeckCount == 1 }

            // A synchronous collection refresh restores the query a second time, masking
            // SearchView clearing it during expansion. Exercise the production queue instead.
            val previousQueue = CollectionManager.setTestDispatcher(StandardTestDispatcher(testScheduler), useReentrantLock = false)
            try {
                menu.clear()
                deckPicker.onCreateOptionsMenu(menu)
                assertFalse(assertNotNull(deckPicker.createMenuJob).isCompleted)

                val restoredSearchItem = menu.findItem(R.id.deck_picker_action_filter)
                assertTrue(restoredSearchItem.isActionViewExpanded)
                assertEquals("Test Deck 1", deckPicker.searchView.query.toString())
                advanceRobolectricLooper()
                assertEquals("Test Deck 1", deckPicker.viewModel.deckSearchQuery)

                advanceUntilIdle()
                advanceRobolectricLooperUntil { deckPicker.visibleDeckCount == 1 }
            } finally {
                CollectionManager.setTestDispatcher(previousQueue)
            }
        }

    @Test
    fun `empty expanded deck search is restored after recreation`() {
        addBasicNote()
        repeat(10) { addDeck("Test Deck $it") }

        ActivityScenario.launch(DeckPicker::class.java).use { scenario ->
            scenario.onActivity { deckPicker ->
                advanceRobolectricLooper()
                assertTrue(deckPicker.menu().findItem(R.id.deck_picker_action_filter).expandActionView())
            }

            scenario.recreate()
            advanceRobolectricLooper()

            scenario.onActivity { deckPicker ->
                val searchItem = deckPicker.menu().findItem(R.id.deck_picker_action_filter)
                assertTrue(searchItem.isActionViewExpanded)
                assertEquals("", deckPicker.searchView.query.toString())
                assertTrue(searchItem.collapseActionView())
            }

            scenario.recreate()
            advanceRobolectricLooper()

            scenario.onActivity { deckPicker ->
                val searchItem = deckPicker.menu().findItem(R.id.deck_picker_action_filter)
                assertFalse(searchItem.isActionViewExpanded)
                advanceRobolectricLooperUntil { deckPicker.visibleDeckCount == 11 }
            }
        }
    }

    @Test
    fun `study options follows the selected deck`() {
        assumeTrue("We are running on a tablet", qualifiers!!.contains("xlarge"))
        addBasicNote()
        val deckId = addDeck("Another Deck")
        addNoteToDeck(deckId, count = 2)

        deckPicker {
            val studyOptions = assertNotNull(fragment)
            val studyOptionsView = studyOptions.requireView()
            viewModel.selectDeck(deckId).join()
            advanceRobolectricLooper()

            assertSame(studyOptions, fragment)
            assertSame(studyOptionsView, assertNotNull(fragment).requireView())
            assertEquals(deckId, assertNotNull(fragment).viewModel.selectedDeckId)
            assertEquals("Another Deck", findViewById<TextView>(R.id.studyoptions_deck_name).text.toString())
            assertEquals("2", findViewById<TextView>(R.id.studyoptions_new_count).text.toString())

            viewModel.selectDeck(1L).join()
            advanceRobolectricLooper()

            assertSame(studyOptionsView, assertNotNull(fragment).requireView())
            assertEquals(1L, studyOptions.viewModel.selectedDeckId)
            assertEquals("1", findViewById<TextView>(R.id.studyoptions_new_count).text.toString())
        }
    }

    @Test
    fun `description wrapping is restored after releasing or cancelling divider drag`() {
        assumeTrue("We are running on a tablet", qualifiers!!.contains("xlarge"))
        addBasicNote()

        deckPicker {
            val description = findViewById<TextView>(R.id.studyoptions_deck_description)
            val defaultDescription =
                layoutInflater.inflate(R.layout.fragment_study_options, null).findViewById<TextView>(R.id.studyoptions_deck_description)
            assertEquals(defaultDescription.breakStrategy, description.breakStrategy)
            assertEquals(defaultDescription.hyphenationFrequency, description.hyphenationFrequency)
            val divider = findViewById<View>(R.id.resizing_divider)

            for (endAction in listOf(MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL)) {
                // Restore the view's own settings, including values different from the theme defaults.
                @SuppressLint("WrongConstant")
                description.breakStrategy = Layout.BREAK_STRATEGY_BALANCED
                description.hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_FULL

                divider.dispatchTouch(MotionEvent.ACTION_DOWN)
                repeat(2) { divider.dispatchTouch(MotionEvent.ACTION_MOVE) }
                assertEquals(Layout.BREAK_STRATEGY_SIMPLE, description.breakStrategy)
                assertEquals(Layout.HYPHENATION_FREQUENCY_NONE, description.hyphenationFrequency)

                divider.dispatchTouch(endAction)
                assertEquals(Layout.BREAK_STRATEGY_BALANCED, description.breakStrategy)
                assertEquals(Layout.HYPHENATION_FREQUENCY_FULL, description.hyphenationFrequency)
            }
        }
    }

    @Test
    fun `study options updates after reloading deck counts`() {
        assumeTrue("We are running on a tablet", qualifiers!!.contains("xlarge"))
        addBasicNote()

        deckPicker {
            val studyOptions = assertNotNull(fragment)
            val initialState = studyOptions.viewModel.state
            assertEquals(1, initialState.dataOrNull()?.numberOfCardsInDeck)

            addBasicNote()
            reloadDecks()

            assertSame(studyOptions, fragment)
            val updatedState = assertNotNull(fragment).viewModel.state
            assertEquals(2, updatedState.dataOrNull()?.numberOfCardsInDeck)
        }
    }

    @Test
    fun `deck reload preserves reminder panel and its dialog`() =
        withReminderPanel { reminders ->
            val dialog = showSyncConflict()

            reloadDecks()

            assertSame(reminders, sidePanel)
            assertTrue(dialog.isShowing)
        }

    @Test
    fun `cancelling sync conflict after reload returns to reminders`() =
        withReminderPanel { reminders ->
            val dialog = showSyncConflict()
            reloadDecks()

            dialog.getButton(DialogInterface.BUTTON_NEUTRAL).performClick()
            advanceRobolectricLooper()

            assertFalse(dialog.isShowing)
            assertSame(reminders, sidePanel)
        }

    @Test
    fun `reminder toolbar returns to study options after reload`() =
        withReminderPanel { reminders ->
            reloadDecks()

            reminders.navigateUp()

            assertNotNull(fragment)
            assertEquals(0, supportFragmentManager.backStackEntryCount)
        }

    @Test
    fun `selecting another deck closes reminders and clears pane navigation history`() =
        withReminderPanel {
            val anotherDeck = addDeck("Another Deck")

            viewModel.selectDeck(anotherDeck).join()
            advanceRobolectricLooper()

            assertEquals(anotherDeck, assertNotNull(fragment).viewModel.selectedDeckId)
            assertEquals(0, supportFragmentManager.backStackEntryCount)
        }

    @Test
    fun `returning from reminders does not accumulate study options subscriptions`() {
        assumeTrue("We are running on a tablet", qualifiers!!.contains("xlarge"))
        addBasicNote()
        Prefs.newReviewRemindersEnabled = true

        deckPicker {
            val studyOptions = assertNotNull(fragment)
            val state = studyOptions.viewModel.flowOfState as MutableStateFlow<StudyOptionsState>
            val initialSubscriberCount = ChangeManager.subscriberCount
            assertEquals(1, state.subscriptionCount.value)

            repeat(2) {
                selectContextMenuOption(ContextMenuOption.SCHEDULE_REMINDERS, studyOptions.viewModel.selectedDeckId)
                advanceRobolectricLooper()
                val reminders = requireNotNull(sidePanel as? ScheduleRemindersFragment)
                reminders.navigateUp()

                assertSame(studyOptions, fragment)
                assertAll(
                    { assertEquals("Active study options state collectors", 1, state.subscriptionCount.value) },
                    { assertEquals("ChangeManager subscriptions", initialSubscriberCount, ChangeManager.subscriberCount) },
                )
            }
        }
    }

    @Test
    fun `returning from reminders restores scroll only for the same deck`() {
        assumeTrue("We are running on a tablet", qualifiers!!.contains("xlarge"))
        addBasicNote()
        val anotherDeck = addDeck("Another deck")
        addNoteToDeck(anotherDeck, count = 2)
        Prefs.newReviewRemindersEnabled = true

        deckPicker {
            makeStudyOptionsScrollable()
            val studyOptions = assertNotNull(fragment)
            for (selectAnotherDeck in listOf(false, true)) {
                val scroll = studyOptions.layoutScroll()
                scroll.scrollTo(0, 200)
                assertEquals(200, scroll.scrollY)

                selectContextMenuOption(ContextMenuOption.SCHEDULE_REMINDERS, studyOptions.viewModel.selectedDeckId)
                advanceRobolectricLooper()
                val reminders = assertNotNull(sidePanel as? ScheduleRemindersFragment)
                if (selectAnotherDeck) {
                    viewModel.selectDeck(anotherDeck).join()
                    advanceRobolectricLooper()
                } else {
                    reminders.navigateUp()
                }

                assertSame(studyOptions, fragment)
                assertEquals(if (selectAnotherDeck) anotherDeck else 1L, studyOptions.viewModel.selectedDeckId)
                val restoredScroll = studyOptions.layoutScroll()
                assertEquals(if (selectAnotherDeck) 0 else 200, restoredScroll.scrollY)
            }
        }
    }

    @Test
    fun `restored study options associates saved scroll with its deck`() {
        assumeTrue("We are running on a tablet", qualifiers!!.contains("xlarge"))
        addBasicNote()
        val anotherDeck = addDeck("Another deck")
        addNoteToDeck(anotherDeck, count = 2)

        deckPicker {
            makeStudyOptionsScrollable()
            for (deckId in listOf(1L, anotherDeck)) {
                val studyOptions = assertNotNull(fragment)
                studyOptions.layoutScroll().scrollTo(0, 200)
                val savedState = supportFragmentManager.saveFragmentInstanceState(studyOptions)
                viewModel.selectDeck(deckId).join()
                advanceRobolectricLooper()

                // Recreate the fragment from its saved state, including its view hierarchy.
                val restored = StudyOptionsFragment().apply { setInitialSavedState(savedState) }
                supportFragmentManager.commitNow { replace(R.id.studyoptions_fragment, restored) }
                advanceRobolectricLooper()

                assertEquals(deckId, restored.viewModel.selectedDeckId)
                assertEquals(if (deckId == 1L) 200 else 0, restored.layoutScroll().scrollY)
            }
        }
    }

    /** Keep original and restored panes scrollable without relying on font metrics. */
    private fun DeckPicker.makeStudyOptionsScrollable() {
        val studyOptionsView = assertNotNull(fragment).requireView()
        studyOptionsView.findViewById<ScrollView>(R.id.studyoptions_scrollview).getChildAt(0).minimumHeight = 10000
        supportFragmentManager.registerFragmentLifecycleCallbacks(
            object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentViewCreated(
                    fm: FragmentManager,
                    f: Fragment,
                    v: View,
                    savedInstanceState: Bundle?,
                ) {
                    if (f is StudyOptionsFragment) {
                        v.findViewById<ScrollView>(R.id.studyoptions_scrollview).getChildAt(0).minimumHeight = 10000
                    }
                }
            },
            false,
        )
    }

    private fun StudyOptionsFragment.layoutScroll(): ScrollView =
        requireView().findViewById<ScrollView>(R.id.studyoptions_scrollview).apply {
            measure(MeasureSpec.makeMeasureSpec(400, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(300, MeasureSpec.EXACTLY))
            layout(0, 0, 400, 300)
        }

    private fun withReminderPanel(test: suspend DeckPicker.(ScheduleRemindersFragment) -> Unit) {
        assumeTrue("We are running on a tablet", qualifiers!!.contains("xlarge"))
        addBasicNote()
        Prefs.newReviewRemindersEnabled = true
        deckPicker {
            selectContextMenuOption(ContextMenuOption.SCHEDULE_REMINDERS, getColUnsafe.decks.selected())
            advanceRobolectricLooper()
            test(requireNotNull(sidePanel as? ScheduleRemindersFragment))
        }
    }

    private val DeckPicker.sidePanel
        get() = supportFragmentManager.findFragmentById(R.id.studyoptions_fragment)

    private suspend fun DeckPicker.reloadDecks() {
        viewModel.reloadDeckCounts().join()
        advanceRobolectricLooper()
    }

    private fun DeckPicker.showSyncConflict(): AlertDialog {
        showSyncErrorDialog(SyncErrorDialog.Type.DIALOG_SYNC_CONFLICT_RESOLUTION)
        advanceRobolectricLooper()
        return ShadowDialog.getLatestDialog() as AlertDialog
    }

    private fun ScheduleRemindersFragment.navigateUp() {
        val toolbar = requireView().findViewById<Toolbar>(R.id.non_collapsible_toolbar)
        toolbar.children.first { it.contentDescription == toolbar.navigationContentDescription }.performClick()
        advanceRobolectricLooper()
    }

    @Test
    fun `study options menu items are only displayed in fragmented mode`() {
        deckPickerEx {
            val isTablet = fragmented
            val menu = menu()
            if (isTablet) {
                // Ossifies the split-pane menu decision: the side panel fragment contributes
                // its items to this activity's toolbar via its MenuProvider.
                assertThat(
                    "custom study should be displayed in fragmented mode",
                    menu.findItem(R.id.action_custom_study),
                    notNullValue(),
                )
                assertThat(
                    "deck options should be displayed in fragmented mode",
                    menu.findItem(R.id.action_deck_or_study_options),
                    notNullValue(),
                )
            } else {
                // No side panel fragment exists: its items must not appear.
                assertThat(
                    "custom study must not be displayed outside fragmented mode",
                    menu.findItem(R.id.action_custom_study),
                    nullValue(),
                )
                assertThat(
                    "deck options must not be displayed outside fragmented mode",
                    menu.findItem(R.id.action_deck_or_study_options),
                    nullValue(),
                )
            }
        }
    }

    @Test
    fun `restored study options fragment is pruned when recreated into single pane`() {
        assumeTrue("We are running on a tablet", qualifiers!!.contains("xlarge"))
        ActivityScenario.launch(DeckPicker::class.java).use { scenario ->
            lateinit var viewModel: DeckPickerViewModel
            scenario.onActivity { viewModel = it.viewModel }
            viewModel.awaitStartup()
            scenario.onActivity { deckPicker ->
                assertThat(
                    "side panel fragment should be displayed on tablet",
                    deckPicker.supportFragmentManager.findFragmentById(R.id.studyoptions_fragment),
                    notNullValue(),
                )
            }
            // Fold the device: the activity recreates into the single-pane layout, while
            // FragmentManager restores the saved side panel fragment into it.
            RuntimeEnvironment.setQualifiers("sw320dp")
            scenario.recreate()
            advanceRobolectricLooper()
            scenario.onActivity { deckPicker ->
                assertThat(
                    "restored side panel fragment must be pruned in single-pane layout",
                    deckPicker.supportFragmentManager.findFragmentById(R.id.studyoptions_fragment),
                    nullValue(),
                )
                assertThat(
                    "study options menu items must not leak into the single-pane toolbar",
                    deckPicker.menu().findItem(R.id.action_custom_study),
                    nullValue(),
                )
            }
        }
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
    fun `long pressed deck is highlighted on phones`() {
        val initiallySelectedDeck = addDeck("Initially selected")
        addDeck("Long pressed")
        col.decks.select(initiallySelectedDeck)

        deckPicker {
            assumeTrue("Not running on tablet", !fragmented)
            val selectedDeck = longPressDeck("Long pressed")

            assertThat(
                shadowOf(selectedDeck.background).createdFromResId,
                equalTo(CommonR.drawable.item_background_selected),
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
            longPressDeck("With Cards")

            // ASSERT
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
    fun `FAB opens menu on accessibility click`() =
        deckPicker {
            val fab = findViewById<View>(R.id.fab_main)
            assertThat("menu starts closed", floatingActionMenu.isFABOpen, equalTo(false))
            // TalkBack activate a focused control, which routes to [View.performClick]
            fab.performClick()
            assertThat("FAB menu opens on click", floatingActionMenu.isFABOpen, equalTo(true))
        }

    @Test
    fun `adding a note by single tap preserves the visible FAB menu`() = assertAddingNoteByTouchPreservesMenu(doubleTap = false)

    @Test
    fun `adding a note by double tap preserves the visible FAB menu`() = assertAddingNoteByTouchPreservesMenu(doubleTap = true)

    private fun assertAddingNoteByTouchPreservesMenu(doubleTap: Boolean) =
        deckPicker {
            val fab = floatingActionButtonBinding.fabMain
            val startTime = SystemClock.uptimeMillis()

            fun tap(offset: Long) {
                val downTime = startTime + offset
                for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                    val eventTime = downTime + if (action == MotionEvent.ACTION_UP) 50 else 0
                    val event = MotionEvent.obtain(downTime, eventTime, action, 0f, 0f, 0)
                    try {
                        fab.dispatchTouchEvent(event)
                    } finally {
                        event.recycle()
                    }
                }
            }

            if (!doubleTap) floatingActionMenu.showFloatingActionMenu()
            viewModel.flowOfNavigate.test {
                if (doubleTap) {
                    tap(0)
                    assertTrue(floatingActionMenu.isFABOpen)
                }
                tap(100)
                val intent = AnkiDroidNavigator.toIntent(awaitItem())
                assertEquals("com.ichi2.anki.NoteEditorActivity", intent.component!!.className)
                assertFalse(floatingActionMenu.isFABOpen, "Adding a note must save a closed menu state")
                assertTrue(floatingActionButtonBinding.fabMain.isExtended)
                assertEquals(View.VISIBLE, floatingActionButtonBinding.fabBGLayout.visibility)
            }
        }

    @Test
    fun `selecting Add during FAB reopening keeps menu buttons visible`() =
        deckPicker {
            floatingActionMenu.showFloatingActionMenu()
            shadowOf(mainLooper).idleFor(java.time.Duration.ofMillis(200))
            floatingActionMenu.closeFloatingActionMenu(style = CloseAnimation.SHORT)
            shadowOf(mainLooper).idleFor(java.time.Duration.ofMillis(200))
            assertEquals(View.GONE, floatingActionButtonBinding.addDeckButton.visibility)

            floatingActionMenu.showFloatingActionMenu()
            // Select Add before the opening animations have finished.
            floatingActionButtonBinding.fabMain.performClick()
            shadowOf(mainLooper).idleFor(java.time.Duration.ofMillis(200))

            with(floatingActionButtonBinding) {
                assertTrue(fabMain.isExtended)
                assertEquals(View.VISIBLE, addDeckButton.visibility)
                assertEquals(View.VISIBLE, addFilteredDeckButton.visibility)
                assertEquals(View.VISIBLE, addSharedButton.visibility)
            }
        }

    @Test
    fun `Add menu resets on resume without stopping in multi-window`() {
        ActivityScenario.launch(DeckPicker::class.java).use { scenario ->
            val events = mutableListOf<Lifecycle.Event>()
            scenario.onActivity { deckPicker ->
                deckPicker.lifecycle.addObserver(LifecycleEventObserver { _, event -> events.add(event) })
                deckPicker.floatingActionMenu.showFloatingActionMenu()
                advanceRobolectricLooper()
                with(deckPicker.floatingActionButtonBinding) {
                    val expandedColor = fabMain.backgroundTintList
                    fabMain.performClick()
                    assertTrue(fabMain.isExtended)
                    assertEquals(expandedColor, fabMain.backgroundTintList)
                    assertEquals(View.VISIBLE, fabBGLayout.visibility)
                }
            }

            // A visible activity can be paused without being stopped in multi-window mode.
            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.onActivity { deckPicker ->
                assertTrue(deckPicker.floatingActionButtonBinding.fabMain.isExtended)
            }
            scenario.moveToState(Lifecycle.State.RESUMED)

            scenario.onActivity { deckPicker ->
                assertTrue(Lifecycle.Event.ON_PAUSE in events)
                assertFalse(Lifecycle.Event.ON_STOP in events)
                with(deckPicker.floatingActionButtonBinding) {
                    assertFalse(fabMain.isExtended)
                    assertEquals(View.GONE, fabBGLayout.visibility)
                    assertEquals(View.GONE, addDeckButton.visibility)
                    assertEquals(View.GONE, addFilteredDeckButton.visibility)
                    assertEquals(View.GONE, addSharedButton.visibility)
                    fabMain.performClick()
                    assertTrue(deckPicker.floatingActionMenu.isFABOpen)
                    assertEquals(View.VISIBLE, fabBGLayout.visibility)
                }
            }
        }
    }

    @Test
    fun `FAB menu opens on ENTER key`() =
        deckPicker {
            val fab = findViewById<View>(R.id.fab_main)

            assertThat("menu starts closed", floatingActionMenu.isFABOpen, equalTo(false))

            fab.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))

            assertThat("ENTER key opens the FAB menu", floatingActionMenu.isFABOpen, equalTo(true))
        }

    @Test
    fun `FAB menu closes on ESCAPE key`() =
        deckPicker {
            val fab = findViewById<View>(R.id.fab_main)
            floatingActionMenu.showFloatingActionMenu()
            assertThat("menu is open", floatingActionMenu.isFABOpen, equalTo(true))

            fab.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ESCAPE))

            assertThat(
                "ESCAPE key closes the FAB menu",
                floatingActionMenu.isFABOpen,
                equalTo(false),
            )
        }

    @Test
    fun `FAB menu closes when clicking background inside menu container`() =
        deckPicker {
            floatingActionMenu.showFloatingActionMenu()
            assertThat("menu is open", floatingActionMenu.isFABOpen, equalTo(true))

            // Click the background inside the FAB menu container
            // (simulating, for example, tapping to the left of narrower buttons such as the "Add" button)
            val linearLayout = floatingActionButtonBinding.fabLinearLayout
            linearLayout.performClick()

            assertThat(
                "clicking the background inside the FAB menu container closes the menu",
                floatingActionMenu.isFABOpen,
                equalTo(false),
            )
        }

    @Test
    fun `expanded FAB menu is restored after recreation`() {
        ActivityScenario.launch(DeckPicker::class.java).use { scenario ->
            lateinit var originalActivity: DeckPicker
            scenario.onActivity { deckPicker ->
                originalActivity = deckPicker
                deckPicker.floatingActionMenu.showFloatingActionMenu()
            }

            scenario.recreate()
            advanceRobolectricLooper()

            scenario.onActivity { deckPicker ->
                assertNotSame(originalActivity, deckPicker)
                assertTrue(deckPicker.floatingActionMenu.isFABOpen)
                with(deckPicker.floatingActionButtonBinding) {
                    assertEquals(View.VISIBLE, addSharedButton.visibility)
                    assertEquals(View.VISIBLE, addDeckButton.visibility)
                    assertEquals(View.VISIBLE, addFilteredDeckButton.visibility)
                }

                deckPicker.invalidateOptionsMenu()
                advanceRobolectricLooper()
                assertTrue(deckPicker.floatingActionMenu.isFABOpen)

                deckPicker.onBackPressedDispatcher.onBackPressed()

                assertFalse(deckPicker.floatingActionMenu.isFABOpen)
            }
        }
    }

    @Test
    fun `expanding the FAB menu shows the correct labels`() =
        deckPicker {
            floatingActionMenu.showFloatingActionMenu()
            advanceRobolectricLooper()

            val binding = floatingActionButtonBinding
            assertThat(binding.fabMain.text.toString(), equalTo(getString(CommonString.menu_add)))
            assertThat(
                binding.addSharedButton.text.toString(),
                equalTo(getString(CommonString.menu_get_shared_decks)),
            )
            assertThat(
                binding.addFilteredDeckButton.text.toString(),
                equalTo(getString(CommonString.new_dynamic_deck)),
            )
            // 'Create deck' uses a backend string rather than an android:text resource
            assertThat(
                binding.addDeckButton.text.toString(),
                equalTo(with(targetContext) { TR.sentenceCase.createDeck }),
            )
        }

    @Test
    fun `expanding the FAB menu re-extends the Create deck button`() =
        deckPicker {
            val addDeckButton = floatingActionButtonBinding.addDeckButton
            addDeckButton.isExtended = false

            floatingActionMenu.showFloatingActionMenu()
            advanceRobolectricLooper()

            assertTrue(!addDeckButton.text.isNullOrBlank(), "Create deck button must have a label")
            assertTrue(
                addDeckButton.isExtended,
                "Create deck button must be extended so its label is visible",
            )
        }

    @Test
    fun `bottom navigation features are disabled on tablets`() =
        withBottomNavigationEnabled {
            deckPicker {
                assumeTrue("Running on tablet", fragmented)

                assertThat(Prefs.devBottomNavEnabled, equalTo(true))
                assertThat(bottomNavigationEnabled, equalTo(false))
                assertThat(resources.getBoolean(R.bool.bottom_navigation_available), equalTo(false))
                val bottomNavigation = ActivityHomescreenBinding.bind(findViewById(R.id.root_layout)).bottomNavigation
                assertThat(bottomNavigation, nullValue())
                assertThat(deckPickerBinding.decks.itemDecorationCount, equalTo(0))
            }
        }

    @Test
    fun `bottom navigation follows the single pane layout on large screens`() =
        withBottomNavigationEnabled {
            assumeTrue("Only run once", qualifiers == "normal")
            RuntimeEnvironment.setQualifiers("sw480dp-large")
            deckPicker {
                assertThat(fragmented, equalTo(false))
                assertThat(Prefs.devBottomNavEnabled, equalTo(true))
                assertThat(bottomNavigationEnabled, equalTo(true))
                assertThat(resources.getBoolean(R.bool.bottom_navigation_available), equalTo(true))
                assertThat(binding.bottomNavigation, notNullValue())
                assertThat(binding.bottomNavigation!!.visibility, equalTo(View.VISIBLE))
                assertThat(deckPickerBinding.decks.itemDecorationCount, equalTo(1))
            }
        }

    @Test
    fun `bottom navigation has correct labels`() =
        withBottomNavigationEnabled {
            assumeTrue("Not running on tablet", qualifiers != "xlarge")
            deckPicker {
                val menu = ActivityHomescreenBinding.bind(findViewById(R.id.root_layout)).bottomNavigation!!.menu
                assertThat(menu.findItem(R.id.nav_home)?.title.toString(), equalTo("Decks"))
                assertThat(menu.findItem(R.id.nav_browser)?.title.toString(), equalTo("Browse"))
                assertThat(menu.findItem(R.id.nav_stats)?.title.toString(), equalTo("Statistics"))
                assertThat(menu.findItem(R.id.nav_more)?.title.toString(), equalTo("More"))
            }
        }

    @Test
    fun `Alt number shortcuts navigate between bottom navigation destinations`() =
        withBottomNavigationEnabled {
            assumeTrue("Not running on tablet", qualifiers != "xlarge")
            deckPicker {
                val bottomNav = ActivityHomescreenBinding.bind(findViewById(R.id.root_layout)).bottomNavigation!!
                val shortcuts =
                    listOf(
                        KeyEvent.KEYCODE_1 to BottomNavController.NavigationItem.HOME,
                        KeyEvent.KEYCODE_2 to BottomNavController.NavigationItem.BROWSER,
                        KeyEvent.KEYCODE_3 to BottomNavController.NavigationItem.STATS,
                        KeyEvent.KEYCODE_4 to BottomNavController.NavigationItem.MORE,
                    )

                shortcuts.forEach { (keyCode, destination) ->
                    val handled = dispatchKeyEvent(keyDownEvent(keyCode, KeyEvent.META_ALT_ON))

                    assertThat("Alt shortcut is handled", handled, equalTo(true))
                    assertThat(bottomNav.selectedItemId, equalTo(destination.id))
                }
            }
        }

    @Test
    @SdkSuppress(minSdkVersion = 26)
    fun `bottom navigation exposes a long title as a tooltip`() =
        withBottomNavigationEnabled {
            assumeTrue("Not running on tablet", qualifiers != "xlarge")
            deckPicker {
                val bottomNav = ActivityHomescreenBinding.bind(findViewById(R.id.root_layout)).bottomNavigation!!
                val longTitle = "Statistikenübersicht"

                bottomNav.menu.findItem(R.id.nav_stats).title = longTitle

                assertThat(bottomNav.findViewById<View>(R.id.nav_stats).tooltipText.toString(), equalTo(longTitle))
            }
        }

    @Test
    fun `bottom navigation supports the public card browser`() =
        withBottomNavigationEnabled {
            withPref(Prefs::devUsingCardBrowserSearchView, false) {
                assumeTrue("Not running on tablet", qualifiers != "xlarge")
                deckPicker {
                    val browser = openEmbeddedCardBrowser()
                    val browserView = browser.requireView()
                    val browserToolbar = browserView.findViewById<Toolbar>(R.id.toolbar)
                    assertThat(browser.legacySearchView, notNullValue())
                    assertThat(browserToolbar.menu.findItem(R.id.action_search), notNullValue())
                }
            }
        }

    @Test
    fun `tapping a card in the embedded card browser opens the note editor`() =
        withBottomNavigationEnabled {
            assumeTrue("Not running on tablet", qualifiers != "xlarge")
            addBasicNote()
            deckPicker {
                val browser = openEmbeddedCardBrowser()
                advanceRobolectricLooperUntil { browser.cardsListView.findViewHolderForAdapterPosition(0) != null }
                shadowOf(this).clearNextStartedActivities()

                browser.cardsListView
                    .findViewHolderForAdapterPosition(0)!!
                    .itemView
                    .performClick()
                advanceRobolectricLooperUntil(lazyMessage = { "tapping a card opened nothing" }) {
                    shadowOf(this).peekNextStartedActivity() != null
                }

                val intent = assertNotNull(shadowOf(this).nextStartedActivity)
                assertEquals(NoteEditorActivity::class.java.name, intent.component?.className)
            }
        }

    @Test
    fun `embedded card browser accounts for system bar insets`() =
        withBottomNavigationEnabled {
            withPref(Prefs::devUsingCardBrowserSearchView, false) {
                assumeTrue("Not running on tablet", qualifiers != "xlarge")
                deckPicker {
                    val browserView = openEmbeddedCardBrowser().requireView()

                    val statusBarHeight = 24
                    val insets =
                        WindowInsetsCompat
                            .Builder()
                            .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, statusBarHeight, 0, 0))
                            .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, 48))
                            .build()
                    ViewCompat.dispatchApplyWindowInsets(browserView, insets)

                    assertThat(browserView.findViewById<View>(R.id.toolbar_container).paddingTop, equalTo(statusBarHeight))
                    assertThat(browserView.findViewById<RecyclerFastScroller>(R.id.browser_scroller).handleBottomInset, equalTo(0))
                }
            }
        }

    @Test
    fun `embedded card browser updates selected row count after deselection`() =
        withBottomNavigationEnabled {
            withPref(Prefs::devUsingCardBrowserSearchView, false) {
                assumeTrue("Not running on tablet", qualifiers != "xlarge")
                deckPicker {
                    addBasicNoteWithOp(fields = listOf("first", "one"))
                    addBasicNoteWithOp(fields = listOf("second", "two"))
                    val browser = openEmbeddedCardBrowser()
                    advanceRobolectricLooper()
                    val viewModel = browser.activityViewModel
                    val toolbarTitle = browser.requireView().findViewById<TextView>(R.id.toolbar_title)
                    val firstRow = RowSelection(viewModel.getRowAtPosition(0), topOffset = 0)
                    val secondRow = RowSelection(viewModel.getRowAtPosition(1), topOffset = 0)

                    viewModel.handleRowLongPress(firstRow).join()
                    viewModel.onTap(secondRow).join()
                    advanceRobolectricLooper()
                    assertThat(toolbarTitle.text.toString(), equalTo("2"))

                    viewModel.onTap(firstRow).join()
                    advanceRobolectricLooper()
                    assertThat(toolbarTitle.text.toString(), equalTo("1"))
                }
            }
        }

    @Test
    fun `embedded card browser can select all decks`() =
        withBottomNavigationEnabled {
            withPref(Prefs::devUsingCardBrowserSearchView, false) {
                assumeTrue("Not running on tablet", qualifiers != "xlarge")
                deckPicker {
                    val browser = openEmbeddedCardBrowser()

                    browser.childFragmentManager.setFragmentResult(
                        DeckSelectionDialog.REQUEST_SELECT_DECK,
                        Bundle().apply {
                            putParcelable(DeckSelectionDialog.RESULT_SELECTED_DECK, SelectableDeck.AllDecks)
                        },
                    )
                }
            }
        }

    @Test
    fun `bottom navigation shortcuts are registered in keyboard shortcut help`() =
        withBottomNavigationEnabled {
            assumeTrue("Not running on tablet", qualifiers != "xlarge")
            deckPicker {
                val bottomNavigationShortcuts = shortcuts.shortcuts.filter { it.shortcut.startsWith("Alt+") }

                assertThat(
                    bottomNavigationShortcuts.associate { it.shortcut to it.label },
                    equalTo(
                        mapOf(
                            "Alt+1" to "Deck picker",
                            "Alt+2" to "Card browser",
                            "Alt+3" to "Open statistics",
                            "Alt+4" to "More",
                        ),
                    ),
                )
            }
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
    fun `rotating while finishing to show the App Intro does not crash`() {
        setIntroductionSlidesShown(false)
        val controller = Robolectric.buildActivity(DeckPicker::class.java, Intent()).create()
        saveControllerForCleanup(controller)
        val deckPicker = controller.get()
        assertTrue(deckPicker.isFinishing, "DeckPicker should finish to show the App Intro")

        val current = deckPicker.resources.configuration
        val rotated =
            Configuration(current).apply {
                orientation = Configuration.ORIENTATION_LANDSCAPE
                screenWidthDp = current.screenHeightDp
                screenHeightDp = current.screenWidthDp
            }
        assertDoesNotThrow { controller.configurationChange(rotated, deckPicker.resources.displayMetrics) }
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

    @Test
    fun `menu state refresh tolerates a cleared menu`() =
        deckPicker {
            createMenuJob?.join()
            assertNotNull(viewModel.optionsMenuState)
            val menu = menu()
            // Menu invalidation can clear the menu while refreshMenuState is suspended.
            menu.clear()

            updateMenuFromState(menu)

            assertEquals(0, menu.size)
        }

    @Test
    fun `menu state refresh tolerates a removed sync item`() =
        deckPicker {
            createMenuJob?.join()
            assertNotNull(viewModel.optionsMenuState)
            val menu = menu()
            assertNotNull(menu.findItem(R.id.action_undo))
            menu.removeItem(R.id.action_sync)

            updateMenuFromState(menu)

            assertThat(menu.findItem(R.id.action_sync), nullValue())
        }

    @Test
    fun `fatal error arriving after menu creation blanks the options menu`() =
        deckPickerEx {
            assertThat("menu was built normally on startup", lastCreateOptionsMenuResult, equalTo(true))

            // a slow startup check delivers a fatal error once the menu already exists
            viewModel.flowOfStartupResponse.value =
                DeckPickerViewModel.StartupResponse.FatalError(InitialActivity.StartupFailure.DatabaseLocked)
            advanceRobolectricLooper()
            advanceRobolectricLooper()

            assertThat("the failure was handled", databaseErrorDialog, equalTo(DatabaseErrorDialogType.DIALOG_DB_LOCKED))
            assertThat("the menu is rebuilt and blanked", lastCreateOptionsMenuResult, equalTo(false))
        }

    @Test
    fun `success arriving after menu creation refreshes the options menu`() =
        deckPickerEx {
            CollectionManager.closeCollectionBlocking()
            invalidateOptionsMenu()
            advanceRobolectricLooper()
            createMenuJob?.join()
            assertThat("menu built while the collection was closed", viewModel.optionsMenuState, nullValue())

            CollectionManager.getColUnsafe()
            viewModel.flowOfStartupResponse.value = DeckPickerViewModel.StartupResponse.Success
            advanceRobolectricLooper()
            advanceRobolectricLooper()
            createMenuJob?.join()

            assertThat(
                "the menu is rebuilt once startup succeeds",
                viewModel.optionsMenuState?.syncIcon,
                equalTo(SyncIconState.NotLoggedIn),
            )
        }

    /** Regression test for [#20712](https://github.com/ankidroid/Anki-Android/issues/20712) */
    @Test
    fun `SQLiteDatabaseCorruptException in runCatching shows database error dialog`() =
        deckPickerEx {
            runCatching { throw SQLiteDatabaseCorruptException() }
            assertThat(databaseErrorDialog, equalTo(DatabaseErrorDialogType.DIALOG_LOAD_FAILED))
        }

    /** The backend may raise corruption directly, without conversion to a SQLite exception */
    @Test
    fun `BackendDbCorruptException in runCatching shows database error dialog`() =
        deckPickerEx {
            runCatching { throw BackendDbCorruptException(backendError {}) }
            assertThat(databaseErrorDialog, equalTo(DatabaseErrorDialogType.DIALOG_LOAD_FAILED))
        }

    @Test
    fun `creating a deck selects it`() =
        deckPicker {
            showCreateDeckDialog()
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            dialog.input = "My Deck"
            dialog.performPositiveClick()
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

            val newDeckId = col.decks.byName("My Deck")!!.id
            assertThat(
                "the newly created deck should become the current deck",
                col.decks.current().id,
                equalTo(newDeckId),
            )
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
        var integrityCheckCalled = false

        override fun integrityCheck() {
            integrityCheckCalled = true
        }

        /** result of the last [onCreateOptionsMenu] call: false means the menu was blanked */
        var lastCreateOptionsMenuResult: Boolean? = null

        override fun onCreateOptionsMenu(menu: Menu): Boolean = super.onCreateOptionsMenu(menu).also { lastCreateOptionsMenuResult = it }

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

    @Test
    fun draggingUnsupportedFileShowsImportError() =
        deckPicker {
            val clipData = android.content.ClipData.newRawUri("unsupported", "file:///path/to/image.jpg".toUri())
            val payload =
                ContentInfoCompat
                    .Builder(clipData, ContentInfoCompat.SOURCE_DRAG_AND_DROP)
                    .build()
            ViewCompat.performReceiveContent(findViewById(R.id.pull_to_sync_wrapper), payload)

            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            assertThat(dialog.isShowing, equalTo(true))
            assertThat(dialog.title, equalTo(getString(CommonString.import_title_error)))
        }
}

fun RobolectricTest.setIntroductionSlidesShown(shown: Boolean) {
    getPreferences().edit {
        putBoolean(IntroductionActivity.PREF_INTRODUCTION_SLIDES_SHOWN, shown)
    }
}

/**
 * Launches the deck picker, waiting for the initial deck list after successful startup.
 * When supplied, also waits for [expectedDeckIds] in adapter order before running [function].
 * Prepare collection data before calling this helper.
 */
fun RobolectricTest.deckPicker(
    exposeTestData: Boolean = false,
    skipIntroduction: Boolean = true,
    expectedDeckIds: List<DeckId>? = null,
    function: suspend DeckPicker.() -> Unit,
) = runTest {
    setIntroductionSlidesShown(skipIntroduction)
    val deckPicker =
        startActivityNormallyOpenCollectionWithIntent(
            if (exposeTestData) DeckPickerTest.DeckPickerEx::class.java else DeckPicker::class.java,
            Intent(),
        )
    val viewModel = deckPicker.viewModel
    // Startup can stop at permissions, an error dialog, or the app introduction.
    if (!deckPicker.isFinishing && viewModel.startupJob?.isCompleted == true && viewModel.flowOfStartupResponse.value == null) {
        val expectedCount =
            withCol {
                sched.deckDueTree().filterAndFlattenDisplay(DeckFilters.create(viewModel.deckSearchQuery), decks.selected()).size
            }
        RobolectricTest.advanceRobolectricLooperUntil(
            lazyMessage = { "Expected $expectedCount visible decks, got ${deckPicker.visibleDeckCount}" },
        ) { deckPicker.visibleDeckCount == expectedCount }
    }
    expectedDeckIds?.let { deckPicker.awaitVisibleDecks(it) }
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

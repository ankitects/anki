// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Intent
import android.view.View
import androidx.core.content.edit
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.android.view.locationInWindow
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.utils.dp
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.widgets.DeckAdapter
import com.ichi2.testutils.BackupManagerTestUtilities
import com.ichi2.testutils.dispatchInsets
import com.ichi2.testutils.lastItemView
import com.ichi2.testutils.scrollToEnd
import com.ichi2.testutils.simulateKeyboard
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.robolectric.shadows.ShadowDialog

/**
 * Screenshot tests for [DeckPicker]
 *
 * `./gradlew :AnkiDroid:verifyRoborazziPlayDebug -Pscreenshot --tests "com.ichi2.anki.DeckPickerScreenshotTest"`
 */
class DeckPickerScreenshotTest : ScreenshotTest() {
    @Before
    override fun setUp() {
        super.setUp()
        setPhoneQualifiers()
        ensureCollectionLoadIsSynchronous()
        setIntroductionSlidesShown(true)
        BackupManagerTestUtilities.setupSpaceForBackup(targetContext)
        // suppress the periodic 'backup your collection' prompt so the screenshot is just the activity
        targetContext.sharedPrefs().edit { putBoolean("backupPromptDisabled", true) }
    }

    @After
    override fun tearDown() {
        super.tearDown()
        BackupManagerTestUtilities.reset()
        Prefs.devBottomNavEnabled = false
    }

    @Test
    fun baseState_and_fabExpanded() =
        withDeckPicker(deckCount = 0) { deckPicker ->
            captureScreen("baseState")

            deckPicker.floatingActionMenu.showFloatingActionMenu()
            captureScreen("fabExpanded")
        }

    @Test
    fun edgeToEdge_30_decks() =
        withDeckPicker(deckCount = 30) { deckPicker ->
            deckPicker.simulateEdgeToEdge()
            captureScreen("edgeToEdge_30_decks")
        }

    @Test
    fun deck_list_after_dismissing_create_deck_dialog() =
        withDeckPicker(deckCount = 12) { deckPicker ->
            deckPicker.simulateEdgeToEdge()
            deckPicker.showCreateDeckDialog()
            deckPicker.dispatchInsets(navBarBottom = 48.dp, imeBottom = 300.dp)
            advanceRobolectricLooper()

            ShadowDialog.getLatestDialog().cancel()
            deckPicker.dispatchInsets(navBarBottom = 48.dp)
            advanceRobolectricLooper()
            deckPicker.deckPickerBinding.decks.scrollToEnd()

            captureScreen("deck_list_after_dismissing_create_deck_dialog")
        }

    @Test
    fun snackbar_follows_keyboard_visibility() =
        withDeckPicker(deckCount = 30) { deckPicker ->
            // Deck creation displays feedback before the dialog's keyboard closes.
            deckPicker.dispatchInsets(navBarBottom = 48.dp, imeBottom = 300.dp)
            advanceRobolectricLooper()
            val snackbar = checkNotNull(deckPicker.showSnackbar(TR.errorsFilteredParentDeck(), Snackbar.LENGTH_INDEFINITE))
            advanceRobolectricLooper()

            deckPicker.dispatchInsets(navBarBottom = 48.dp)
            advanceRobolectricLooper()
            captureScreen("snackbar_keyboard_closed")

            deckPicker.simulateKeyboard()
            captureScreen("snackbar_keyboard_open")
            snackbar.dismiss()
        }

    /** Ensure that 'studied today' overlaying a deck name works when the IME is open */
    @Test
    fun keyboard_studied_line_overlaying_deck_name() =
        withDeckPicker(deckCount = 30) { deckPicker ->
            deckPicker.searchWithKeyboard()
            deckPicker.scrollLastDeckOntoStudiedLine()

            captureScreen("keyboard_studied_line_overlaying_deck_name")
        }

    @Test
    fun keyboard_open_scrolled_to_bottom() =
        withDeckPicker(deckCount = 30) { deckPicker ->
            deckPicker.searchWithKeyboard()
            deckPicker.deckPickerBinding.decks.scrollToEnd()

            captureScreen("keyboard_open_scrolled_to_bottom")
        }

    /** The studied summary and final deck must remain above the keyboard with bottom navigation enabled. */
    @Test
    fun bottom_navigation_keyboard_open_scrolled_to_bottom() {
        enableBottomNavigation()
        withDeckPicker(deckCount = 30) { deckPicker ->
            deckPicker.searchWithKeyboard()
            deckPicker.deckPickerBinding.decks.scrollToEnd()

            captureScreen("bottom_navigation_keyboard_open_scrolled_to_bottom")
        }
    }

    @Test
    fun bottom_navigation_statistics_selected() {
        enableBottomNavigation()
        withDeckPicker(deckCount = 0) { deckPicker ->
            val bottomNavigation = requireNotNull(deckPicker.binding.bottomNavigation)
            bottomNavigation.selectedItemId = R.id.nav_stats
            advanceRobolectricLooper()
            check(bottomNavigation.selectedItemId == R.id.nav_stats)

            captureScreen("bottom_navigation_statistics_selected")
        }
    }

    @Test
    fun hierarchy_lines() =
        runTest {
            enableBottomNavigation()

            val root1 = addDeck("Math")
            val mathAlg = addDeck("Math::Algebra")
            addDeck("Math::Algebra::Linear")
            addDeck("Math::Geometry")
            val root2 = addDeck("Science")
            val sciPhys = addDeck("Science::Physics")
            addDeck("Science::Physics::Kinematics")
            addDeck("Science::Chemistry")

            val deckPicker = startDeckPicker()
            expandDecks(deckPicker, root1, root2, mathAlg, sciPhys)

            captureScreen("hierarchy_lines")
        }

    @Test
    fun hierarchy_lines_collapsed() =
        runTest {
            enableBottomNavigation()

            val root = addDeck("Math")
            addDeck("Math::Algebra")
            addDeck("Math::Algebra::Linear")
            addDeck("Math::Geometry")
            addDeck("Science::Physics::Kinematics")

            val deckPicker = startDeckPicker()
            // Expand Math to see Algebra and Geometry, but leave Algebra collapsed
            expandDecks(deckPicker, root)

            captureScreen("hierarchy_lines_collapsed")
        }

    @Test
    fun hierarchy_lines_deep_nesting() =
        runTest {
            enableBottomNavigation()

            addDeck("Level1")
            addDeck("Level1::Level2")
            addDeck("Level1::Level2::Level3")
            addDeck("Level1::Level2::Level3::Level4")
            addDeck("Level1::Level2::Level3::Level4::Level5")
            addDeck("Level1::Level2::Level3::Level4::Level5::Level6")
            addDeck("Level1::Level2::Level3::Level4::Level5::Level6::Level7")
            addDeck("Level1::Level2::Sibling")
            addDeck("Level1::Sibling")

            val deckPicker = startDeckPicker()
            val expandedDecks =
                (1..6).map { depth ->
                    val name = (1..depth).joinToString("::") { "Level$it" }
                    col.decks.id(name)
                }
            expandDecks(deckPicker, *expandedDecks.toLongArray())

            captureScreen("hierarchy_lines_deep_nesting")
        }

    @Test
    fun hierarchy_lines_many_siblings() =
        runTest {
            enableBottomNavigation()

            val root = addDeck("Parent")
            addDeck("Parent::Child1")
            addDeck("Parent::Child2")
            val child3 = addDeck("Parent::Child3")
            addDeck("Parent::Child3::Subchild")
            addDeck("Parent::Child4")
            addDeck("Parent::Child5")

            val deckPicker = startDeckPicker()
            expandDecks(deckPicker, root, child3)

            captureScreen("hierarchy_lines_many_siblings")
        }

    /** Expands the deck search, hiding the FAB, and opens the keyboard over the navigation bar */
    private fun DeckPicker.searchWithKeyboard() {
        requireNotNull(searchDecksIcon).expandActionView()
        advanceRobolectricLooper()
        // settles layout: the list's bottom padding follows the summary line's layout pass
        simulateKeyboard()
    }

    /** Scrolls to the end, then back so the last deck's name is centered on the 'Studied' line */
    private fun DeckPicker.scrollLastDeckOntoStudiedLine() {
        val list = deckPickerBinding.decks
        list.scrollToEnd()
        val lastDeck = list.lastItemView
        val studiedLine = deckPickerBinding.reviewSummaryTextView
        list.scrollBy(0, lastDeck.contentCenterYInWindow - studiedLine.contentCenterYInWindow)
        advanceRobolectricLooper()
    }

    /**
     * The vertical center of the receiver's content, relative to its window.
     *
     * Padding is excluded: the 'Studied' line's bottom padding holds the keyboard inset.
     */
    private val View.contentCenterYInWindow: Int
        get() = locationInWindow().y + paddingTop + (height - paddingTop - paddingBottom) / 2

    private fun enableBottomNavigation() {
        Prefs.devBottomNavEnabled = true
    }

    private suspend fun startDeckPicker(): DeckPicker =
        startActivityNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent()).also {
            it.awaitDeckList()
        }

    private suspend fun DeckPicker.awaitDeckList() {
        viewModel.flowOfDeckList.first { it.data.isNotEmpty() }
        advanceRobolectricLooper()
    }

    private suspend fun expandDecks(
        deckPicker: DeckPicker,
        vararg deckIds: DeckId,
    ) {
        deckIds.forEach { deckPicker.viewModel.toggleDeckExpand(it).join() }
        deckPicker.awaitDecksRenderedExpanded(*deckIds)
    }

    /**
     * Waits until the deck list's [RecyclerView] has rendered [deckIds] as expanded.
     */
    private fun DeckPicker.awaitDecksRenderedExpanded(vararg deckIds: DeckId) {
        val adapter = this.deckPickerBinding.decks.adapter as DeckAdapter
        advanceRobolectricLooperUntil(
            lazyMessage = {
                "Decks ${deckIds.toList()} not rendered as expanded. Displayed: " +
                    adapter.currentList.map { "${it.did}(collapsed=${it.collapsed})" }
            },
        ) {
            deckIds.all { id -> adapter.currentList.any { it.did == id && !it.collapsed } }
        }
    }
}

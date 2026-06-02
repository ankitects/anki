/*
 *  Copyright (c) 2022 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.dialogs

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.core.os.bundleOf
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.FragmentScenario.Companion.launch
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.IntroductionActivity
import com.ichi2.anki.R
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.testutils.BackupManagerTestUtilities.setupSpaceForBackup
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class DeckPickerContextMenuTest {
    private val scenariosForCleanup = ArrayList<FragmentScenario<*>>()

    @Before
    fun before() {
        val context = ApplicationProvider.getApplicationContext<AnkiDroidApp>()
        context.sharedPrefs().edit { putBoolean(IntroductionActivity.INTRODUCTION_SLIDES_SHOWN, true) }
        setupSpaceForBackup(context)
    }

    @After
    fun after() {
        for (scenario in scenariosForCleanup) {
            try {
                scenario.close()
            } catch (e: Exception) {
                Timber.e(e, "ignored")
            }
        }
    }

    @Test
    fun ensure_cannot_be_instantiated_without_expected_arguments() {
        // fails on deck id missing from arguments
        assertFailsWith<IllegalArgumentException> { startContextMenuWithMissingArgument("id") }
        // fails on deck name missing from arguments
        assertFailsWith<IllegalArgumentException> { startContextMenuWithMissingArgument("name") }
        // fails on deck dynamic status missing from arguments
        assertFailsWith<IllegalArgumentException> { startContextMenuWithMissingArgument("dynamic") }
        // fails on deck having buried status missing from arguments
        assertFailsWith<IllegalArgumentException> { startContextMenuWithMissingArgument("hasBuried") }
    }

    /**
     * Create a [Bundle] with static data to be passed as arguments for [DeckPickerContextMenu].
     * One option is excluded to simulate the argument missing.
     *
     * @param excluded a value from [id, name, dynamic, hasBuried] to be removed from the returned
     * bundle. See source code for [DeckPickerContextMenu] for options meaning.
     */
    private fun startContextMenuWithMissingArgument(excluded: String) {
        val arguments =
            Bundle().apply {
                if (excluded != "id") {
                    DeckPickerContextMenu.ARG_DECK_ID to 1000L
                }
                if (excluded != "name") {
                    DeckPickerContextMenu.ARG_DECK_NAME to "Deck"
                }
                if (excluded != "dynamic") {
                    DeckPickerContextMenu.ARG_DECK_IS_DYN to false
                }
                if (excluded != "hasBuried") {
                    DeckPickerContextMenu.ARG_DECK_HAS_BURIED_IN_DECK to false
                }
            }
        launch(arguments)
    }

    @Test
    fun `Shows standard options`() {
        launch(withArguments()).onFragment { fragment ->
            fragment.assertOptionPresent(R.string.menu_add)
            fragment.assertOptionPresent(R.string.browse_cards)
            fragment.assertOptionPresent(R.string.rename_deck)
            fragment.assertOptionPresent(R.string.menu__deck_options)
            fragment.assertOptionPresent(R.string.export_deck)
            fragment.assertOptionPresent(R.string.create_shortcut)
            fragment.assertOptionPresent(with(fragment.requireContext()) { TR.sentenceCase.deleteDeck })
        }
    }

    private fun DeckPickerContextMenu.assertOptionPresent(optionStringRes: Int) {
        assertOptionPresent(getString(optionStringRes))
    }

    private fun DeckPickerContextMenu.assertOptionPresent(optionTitle: String) {
        assertTrue(
            foundOptions().contains(optionTitle),
            "'$optionTitle' should be present",
        )
    }

    @Test
    fun `DELETE_DECK is the last option in the menu(issue 10283)`() {
        // "Delete deck" was previously close to "Custom study" which caused misclicks.
        // This is less likely at the bottom of the list
        launch(withArguments()).onFragment { fragment ->
            MatcherAssert.assertThat(
                "'Delete deck' should be last item in the menu",
                fragment.foundOptions().last(),
                equalTo(with(fragment.requireContext()) { TR.sentenceCase.deleteDeck }),
            )
        }
    }

    @Test
    fun `Shows options to empty and rebuild when deck is dynamic`() {
        launch(withArguments(isDynamic = true)).onFragment { fragment ->
            assertTrue(
                fragment.foundOptions().contains(fragment.getString(R.string.empty_cram_label)),
                "'Empty' should be present when deck is dynamic",
            )
            assertTrue(
                fragment.foundOptions().contains(TR.actionsRebuild()),
                "'Rebuild' should be present when deck is dynamic",
            )
        }
    }

    @Test
    fun `Shows option to create subdeck when deck is not dynamic`() {
        launch(withArguments()).onFragment { fragment ->
            assertTrue(
                fragment.foundOptions().contains(fragment.getString(R.string.create_subdeck)),
                "'Create subdeck' should be present when deck is not dynamic",
            )
        }
    }

    @Test
    fun `Shows option to unbury if deck has buried cards`() {
        launch(withArguments(hasBuriedCards = true)).onFragment { fragment ->
            assertTrue(
                fragment.foundOptions().contains(TR.studyingUnbury()),
                "'Unbury' should be present when deck has buried cards",
            )
        }
    }

    private fun launch(arguments: Bundle) =
        launch(DeckPickerContextMenu::class.java, arguments, R.style.Theme_Light).also {
            scenariosForCleanup.add(it)
        }

    private fun DeckPickerContextMenu.foundOptions(): List<String> {
        val foundOptions = mutableListOf<String>()
        val menuAdapter = (dialog as AlertDialog).listView.adapter
        for (index in 0 until menuAdapter.count) {
            foundOptions.add(menuAdapter.getItem(index).toString())
        }
        return foundOptions
    }

    private fun withArguments(
        deckId: DeckId = 1000L,
        deckName: String = "Deck 1",
        isDynamic: Boolean = false,
        hasBuriedCards: Boolean = false,
    ) = bundleOf(
        DeckPickerContextMenu.ARG_DECK_ID to deckId,
        DeckPickerContextMenu.ARG_DECK_NAME to deckName,
        DeckPickerContextMenu.ARG_DECK_IS_DYN to isDynamic,
        DeckPickerContextMenu.ARG_DECK_HAS_BURIED_IN_DECK to hasBuriedCards,
    )
}

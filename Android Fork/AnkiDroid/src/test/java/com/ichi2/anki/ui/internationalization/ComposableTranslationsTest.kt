// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.internationalization

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.espresso.matcher.ViewMatchers.assertThat
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.i18n.PreviewTranslations
import com.ichi2.anki.RobolectricTest
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.instanceOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComposableTranslationsTest : RobolectricTest() {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `proper translations implementation is used in composables`() {
        composeTestRule.setContent {
            // uses expected implementation for preview mode
            CompositionLocalProvider(LocalInspectionMode provides true) {
                assertThat(tr(), instanceOf(PreviewTranslations::class.java))
            }
            // uses expected implementation for production code
            CompositionLocalProvider(LocalInspectionMode provides false) {
                assertThat(tr(), instanceOf(BackendTranslations::class.java))
            }
        }
    }

    @Test
    fun `English is converted to sentence case`() {
        composeTestRule.setContent {
            assertThat(trCase().addNoteType(), equalTo("Add note type"))
            assertThat(trCase().toggleSuspend(), equalTo("Toggle suspend"))
            assertThat(trCase().toggleBury(), equalTo("Toggle bury"))
            assertThat(trCase().customStudy(), equalTo("Custom study"))
            assertThat(trCase().clearUnusedTags(), equalTo("Clear unused tags"))
            assertThat(trCase().emptyCardsTitle(), equalTo("Empty cards"))
            assertThat(trCase().emptyTrash(), equalTo("Empty trash"))
            assertThat(trCase().restoreDeleted(), equalTo("Restore deleted"))
            assertThat(trCase().changeNoteType(), equalTo("Change note type"))
            assertThat(trCase().gradeNow(), equalTo("Grade now"))
            assertThat(trCase().browserOptions(), equalTo("Browser options"))
            assertThat(trCase().changeDeck(), equalTo("Change deck"))
            assertThat(trCase().toggleMark(), equalTo("Toggle mark"))
            assertThat(trCase().selectImage(), equalTo("Select image"))
            assertThat(trCase().deckOptions(), equalTo("Deck options"))
            assertThat(trCase().answerAgain(), equalTo("Answer again"))
            assertThat(trCase().answerHard(), equalTo("Answer hard"))
            assertThat(trCase().answerGood(), equalTo("Answer good"))
            assertThat(trCase().selectiveStudy(), equalTo("Selective study"))
            assertThat(trCase().chooseTags(), equalTo("Choose tags"))
            assertThat(trCase().repositionNewCards(), equalTo("Reposition new cards"))
            assertThat(trCase().allFields(), equalTo("All fields"))
            assertThat(trCase().tagMissing(), equalTo("Tag missing"))
            assertThat(trCase().checkMediaDeleteUnused(), equalTo("Delete unused"))
            assertThat(trCase().ankiCollectionPackage(), equalTo("Anki collection package"))
            assertThat(trCase().ankiDeckPackage(), equalTo("Anki deck package"))
            assertThat(trCase().notesInPlainText(), equalTo("Notes in plain text"))
            assertThat(trCase().cardsInPlainText(), equalTo("Cards in plain text"))
            assertThat(trCase().setDueDate(), equalTo("Set due date"))
            assertThat(trCase().restoreToDefault(), equalTo("Restore to default"))
            assertThat(trCase().checkDatabase(), equalTo("Check database"))
            assertThat(trCase().checkMediaTitle(), equalTo("Check media"))
            assertThat(trCase().checkMediaAction(), equalTo("Check media"))
            assertThat(trCase().emptyCards(), equalTo("Empty cards"))
            assertThat(trCase().flagCard(), equalTo("Flag card"))
            assertThat(trCase().noFlag(), equalTo("No flag"))
            assertThat(trCase().keepEditing(), equalTo("Keep editing"))
            assertThat(trCase().copyToClipboard(), equalTo("Copy to clipboard"))
            assertThat(trCase().frontTemplate(), equalTo("Front template"))
            assertThat(trCase().backTemplate(), equalTo("Back template"))
            assertThat(trCase().renameDeck(), equalTo("Rename deck"))
            assertThat(trCase().renameTag(), equalTo("Rename tag"))
            assertThat(trCase().deleteDeck(), equalTo("Delete deck"))
            assertThat(trCase().createDeck(), equalTo("Create deck"))
            assertThat(trCase().selectDeck(), equalTo("Select deck"))
            assertThat(trCase().logIn(), equalTo("Log in"))
            assertThat(trCase().logOut(), equalTo("Log out"))
            assertThat(trCase().cardInfo(), equalTo("Card info"))
            assertThat(trCase().buryNote(), equalTo("Bury note"))
            assertThat(trCase().buryCard(), equalTo("Bury card"))
            assertThat(trCase().suspendNote(), equalTo("Suspend note"))
            assertThat(trCase().suspendCard(), equalTo("Suspend card"))
            assertThat(trCase().markNote(), equalTo("Mark note"))
            assertThat(trCase().deleteNote(), equalTo("Delete note"))
            assertThat(trCase().previousCardInfo(), equalTo("Previous card info"))
            assertThat(trCase().ankiWebAccount(), equalTo("AnkiWeb account"))
            assertThat(trCase().browserAppearance(), equalTo("Browser appearance"))
            assertThat(trCase().copyDebugInfo(), equalTo("Copy debug info"))
            assertThat(trCase().addField(), equalTo("Add field"))
            assertThat(trCase().allDecks(), equalTo("All decks"))
            assertThat(trCase().mediaSyncLog(), equalTo("Media sync log"))
            assertThat(trCase().creatingBackup(), equalTo("Creating backup…"))

            // TODO these three backend strings come with extra FSI and PDI character types which
            //  fail the case check, there should be a general mechanism to remove these extra
            //  characters(other backend strings also have extra characters) in toSentence()
            // assertThat(trCase().cardStatsCurrentCardStudy(), equalTo("Current card (study)"))
            // assertThat(trCase().cardStatsCurrentCardBrowse(), equalTo("Current card (browse)"))
            // assertThat(trCase().cardStatsPreviousCardStudy(), equalTo("Previous card (study)"))
        }
    }
}

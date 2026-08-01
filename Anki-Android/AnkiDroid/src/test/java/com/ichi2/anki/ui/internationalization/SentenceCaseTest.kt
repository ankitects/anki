// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.internationalization

import androidx.fragment.app.Fragment
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.EmptyApplicationCategory
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.testutils.EmptyAnkiActivity
import com.ichi2.testutils.EmptyApplication
import com.ichi2.testutils.launchFragmentInContainer
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class SentenceCaseTest : RobolectricTest() {
    @Test
    fun `English is converted to sentence case`() {
        ensureCollectionLoadIsSynchronous()

        launchFragmentInContainer<Fragment>()
            .onFragment { fragment ->
                with(fragment) {
                    assertThat(TR.sentenceCase.toggleSuspend, equalTo("Toggle suspend"))
                    assertThat(TR.sentenceCase.toggleBury, equalTo("Toggle bury"))
                    assertThat(TR.sentenceCase.customStudy, equalTo("Custom study"))
                    assertThat(TR.sentenceCase.emptyCardsTitle, equalTo("Empty cards"))
                    assertThat(TR.sentenceCase.emptyTrash, equalTo("Empty trash"))
                    assertThat(TR.sentenceCase.restoreDeleted, equalTo("Restore deleted"))
                    assertThat(TR.sentenceCase.changeNoteType, equalTo("Change note type"))
                    assertThat(TR.sentenceCase.gradeNow, equalTo("Grade now"))
                    assertThat(TR.sentenceCase.browserOptions, equalTo("Browser options"))
                    assertThat(TR.sentenceCase.changeDeck, equalTo("Change deck"))
                    assertThat(TR.sentenceCase.toggleMark, equalTo("Toggle mark"))
                    assertThat(TR.sentenceCase.selectImage, equalTo("Select image"))
                    assertThat(TR.sentenceCase.deckOptions, equalTo("Deck options"))
                    assertThat(TR.sentenceCase.answerAgain, equalTo("Answer again"))
                    assertThat(TR.sentenceCase.answerHard, equalTo("Answer hard"))
                    assertThat(TR.sentenceCase.answerGood, equalTo("Answer good"))
                    assertThat(TR.sentenceCase.selectiveStudy, equalTo("Selective study"))
                    assertThat(TR.sentenceCase.chooseTags, equalTo("Choose tags"))
                    assertThat(TR.sentenceCase.repositionNewCards, equalTo("Reposition new cards"))
                    assertThat(TR.sentenceCase.allFields, equalTo("All fields"))
                    assertThat(TR.sentenceCase.tagMissing, equalTo("Tag missing"))
                    assertThat(TR.sentenceCase.checkMediaDeleteUnused, equalTo("Delete unused"))

                    // input-taking accessors: a Title Case input only maps to the sentence form
                    // if the correct sentence-case resource is wired
                    assertThat(TR.sentenceCase.gestureToggleWhiteboard("Toggle Whiteboard"), equalTo("Toggle whiteboard"))
                    assertThat(TR.sentenceCase.gestureFlagRed("Toggle Red Flag"), equalTo("Toggle red flag"))
                    assertThat(TR.sentenceCase.gestureFlagOrange("Toggle Orange Flag"), equalTo("Toggle orange flag"))
                    assertThat(TR.sentenceCase.gestureFlagGreen("Toggle Green Flag"), equalTo("Toggle green flag"))
                    assertThat(TR.sentenceCase.gestureFlagBlue("Toggle Blue Flag"), equalTo("Toggle blue flag"))
                    assertThat(TR.sentenceCase.gestureFlagPink("Toggle Pink Flag"), equalTo("Toggle pink flag"))
                    assertThat(TR.sentenceCase.gestureFlagTurquoise("Toggle Turquoise Flag"), equalTo("Toggle turquoise flag"))
                    assertThat(TR.sentenceCase.gestureFlagPurple("Toggle Purple Flag"), equalTo("Toggle purple flag"))
                    assertThat(TR.sentenceCase.gestureFlagRemove("Remove Flag"), equalTo("Remove flag"))
                }
            }.close()

        ActivityScenario
            .launch(EmptyAnkiActivity::class.java)
            .onActivity { activity ->
                with(activity) {
                    assertThat(TR.sentenceCase.setDueDate, equalTo("Set due date"))
                    assertThat(TR.sentenceCase.addNoteType, equalTo("Add note type"))
                    assertThat(TR.sentenceCase.restoreToDefault, equalTo("Restore to default"))
                    assertThat(TR.sentenceCase.checkDatabase, equalTo("Check database"))
                    assertThat(TR.sentenceCase.checkMediaTitle, equalTo("Check media"))
                    assertThat(TR.sentenceCase.checkMediaAction, equalTo("Check media"))
                    assertThat(TR.sentenceCase.emptyCards, equalTo("Empty cards"))
                    assertThat(TR.sentenceCase.flagCard, equalTo("Flag card"))
                    assertThat(TR.sentenceCase.frontTemplate, equalTo("Front template"))
                    assertThat(TR.sentenceCase.backTemplate, equalTo("Back template"))
                    assertThat(TR.sentenceCase.renameDeck, equalTo("Rename deck"))
                    assertThat(TR.sentenceCase.customStudy, equalTo("Custom study"))
                    assertThat(TR.sentenceCase.deckOptions, equalTo("Deck options"))
                    assertThat(TR.sentenceCase.deleteDeck, equalTo("Delete deck"))
                    assertThat(TR.sentenceCase.createDeck, equalTo("Create deck"))
                    assertThat(TR.sentenceCase.selectDeck, equalTo("Select deck"))
                    assertThat(TR.sentenceCase.logIn, equalTo("Log in"))
                    assertThat(TR.sentenceCase.logOut, equalTo("Log out"))
                    assertThat(TR.sentenceCase.cardInfo, equalTo("Card info"))
                    assertThat(TR.sentenceCase.buryNote, equalTo("Bury note"))
                    assertThat(TR.sentenceCase.buryCard, equalTo("Bury card"))
                    assertThat(TR.sentenceCase.suspendNote, equalTo("Suspend note"))
                    assertThat(TR.sentenceCase.suspendCard, equalTo("Suspend card"))
                    assertThat(TR.sentenceCase.markNote, equalTo("Mark note"))
                    assertThat(TR.sentenceCase.deleteNote, equalTo("Delete note"))
                    assertThat(TR.sentenceCase.previousCardInfo, equalTo("Previous card info"))
                    assertThat(TR.sentenceCase.ankiWebAccount, equalTo("AnkiWeb account"))
                    assertThat(TR.sentenceCase.browserAppearance, equalTo("Browser appearance"))
                    assertThat(TR.sentenceCase.copyDebugInfo, equalTo("Copy debug info"))
                    assertThat(TR.sentenceCase.addField, equalTo("Add field"))
                    assertThat(TR.sentenceCase.allDecks, equalTo("All decks"))
                    assertThat(TR.sentenceCase.cardStatsCurrentCardStudy("Current Card (Study)"), equalTo("Current card (study)"))
                    assertThat(TR.sentenceCase.cardStatsCurrentCardBrowse("Current Card (Browse)"), equalTo("Current card (browse)"))
                    assertThat(TR.sentenceCase.cardStatsPreviousCardStudy("Previous Card (Study)"), equalTo("Previous card (study)"))
                    assertThat(TR.sentenceCase.gradeNow, equalTo("Grade now"))

                    assertThat("syncMediaLogTitle", TR.syncMediaLogTitle(), equalTo("Media Sync Log"))
                    assertThat(TR.sentenceCase.mediaSyncLog, equalTo("Media sync log"))

                    assertThat("Toggle Suspend".toSentenceCase(this, R.string.sentence_toggle_suspend), equalTo("Toggle suspend"))
                    assertThat("Ook? Ook?".toSentenceCase(this, R.string.sentence_toggle_suspend), equalTo("Ook? Ook?"))
                }
            }.close()
    }
}

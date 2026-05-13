/*
 *  Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
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

        launchFragmentInContainer<Fragment>().onFragment { fragment ->
            with(fragment) {
                assertThat(TR.sentenceCase.toggleSuspend, equalTo("Toggle suspend"))
                assertThat(TR.sentenceCase.toggleBury, equalTo("Toggle bury"))
                assertThat(TR.sentenceCase.customStudy, equalTo("Custom study"))
                assertThat(TR.sentenceCase.emptyCards, equalTo("Empty cards"))
                assertThat(TR.sentenceCase.emptyTrash, equalTo("Empty trash"))
                assertThat(TR.sentenceCase.restoreDeleted, equalTo("Restore deleted"))
                assertThat(TR.sentenceCase.changeNoteType, equalTo("Change note type"))
                assertThat(TR.sentenceCase.gradeNow, equalTo("Grade now"))
            }
        }

        ActivityScenario.launch(EmptyAnkiActivity::class.java).onActivity { activity ->
            with(activity) {
                assertThat(TR.sentenceCase.setDueDate, equalTo("Set due date"))
                assertThat(TR.sentenceCase.addNoteType, equalTo("Add note type"))
                assertThat(TR.sentenceCase.restoreToDefault, equalTo("Restore to default"))
                assertThat(TR.sentenceCase.checkDatabase, equalTo("Check database"))
                assertThat(TR.sentenceCase.checkMediaTitle, equalTo("Check media"))
                assertThat(TR.sentenceCase.checkMediaAction, equalTo("Check media"))
                assertThat(TR.sentenceCase.frontTemplate, equalTo("Front template"))
                assertThat(TR.sentenceCase.backTemplate, equalTo("Back template"))

                assertThat("syncMediaLogTitle", TR.syncMediaLogTitle(), equalTo("Media Sync Log"))
                assertThat(TR.sentenceCase.mediaSyncLog, equalTo("Media sync log"))

                assertThat("Toggle Suspend".toSentenceCase(this, R.string.sentence_toggle_suspend), equalTo("Toggle suspend"))
                assertThat("Ook? Ook?".toSentenceCase(this, R.string.sentence_toggle_suspend), equalTo("Ook? Ook?"))
            }
        }
    }
}

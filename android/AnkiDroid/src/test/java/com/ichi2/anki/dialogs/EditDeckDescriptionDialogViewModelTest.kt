/*
 *  Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>
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

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.ichi2.anki.dialogs.EditDeckDescriptionDialogViewModel.Companion.ARG_DECK_ID
import com.ichi2.anki.dialogs.EditDeckDescriptionDialogViewModel.Companion.STATE_DESCRIPTION
import com.ichi2.anki.dialogs.EditDeckDescriptionDialogViewModel.Companion.STATE_FORMAT_AS_MARKDOWN
import com.ichi2.anki.dialogs.EditDeckDescriptionDialogViewModel.Companion.STATE_USER_MADE_CHANGES
import com.ichi2.anki.dialogs.EditDeckDescriptionDialogViewModel.DismissType.ClosedWithoutSaving
import com.ichi2.anki.dialogs.EditDeckDescriptionDialogViewModel.DismissType.Saved
import com.ichi2.anki.libanki.Consts.DEFAULT_DECK_ID
import com.ichi2.anki.libanki.testutils.AnkiTest
import com.ichi2.testutils.JvmTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests [EditDeckDescriptionDialogViewModel]
 */
@RunWith(AndroidJUnit4::class)
class EditDeckDescriptionDialogViewModelTest : JvmTest() {
    @Test
    fun `title is set`() =
        runViewModelTest {
            assertThat(windowTitle, equalTo("Default"))
        }

    @Test
    fun `default description is empty`() =
        runViewModelTest {
            assertThat(description, equalTo(""))
        }

    @Test
    fun `description is updated in database`() =
        runViewModelTest {
            description = "foo"
            assertThat("description not updated before save", defaultDeck.description, equalTo(""))

            saveAndExit()

            assertThat("description updated after save", defaultDeck.description, equalTo("foo"))
        }

    @Test
    fun `format as markdown is updated in database`() =
        runViewModelTest {
            formatAsMarkdown = true
            assertThat("format as markdown not updated before save", defaultDeck.descriptionAsMarkdown, equalTo(false))

            saveAndExit()

            assertThat("format as markdown updated after save", defaultDeck.descriptionAsMarkdown, equalTo(true))
        }

    @Test
    fun `dialog dismissed if no changes`() =
        runViewModelTest {
            flowOfDismissDialog.test {
                onBackRequested()
                assertThat("dialog should be dismissed", expectMostRecentItem(), equalTo(ClosedWithoutSaving))
            }
        }

    @Test
    fun `dialog not immediately dismissed if going back with changes`() =
        runViewModelTest {
            flowOfDismissDialog.test {
                description = "foo"
                onBackRequested()
                assertThat("dialog should not be dismissed", expectMostRecentItem(), equalTo(null))
            }
        }

    @Test
    fun `'discard changes' shown if going back with changes`() =
        runViewModelTest {
            flowOfShowDiscardChanges.test {
                description = "foo"
                onBackRequested()
                assertThat("discard should be shown", expectMostRecentItem(), equalTo(Unit))
            }
        }

    @Test
    fun `'close without saving' closes even if changes are made`() =
        runViewModelTest {
            flowOfDismissDialog.test {
                description = "foo"
                closeWithoutSaving()
                assertThat("dialog should be dismissed", expectMostRecentItem(), equalTo(ClosedWithoutSaving))
            }
        }

    @Test
    fun `dialog is dismissed as saved`() =
        runViewModelTest {
            flowOfDismissDialog.test {
                description = "foo"
                saveAndExit()
                assertThat("dialog should be dismissed", expectMostRecentItem(), equalTo(Saved))
            }
        }

    @Test
    fun `'format as markdown' also triggers changes`() =
        runViewModelTest {
            flowOfShowDiscardChanges.test {
                formatAsMarkdown = true
                onBackRequested()

                expectMostRecentItem()
            }
        }

    @Test
    fun `test state restoration`() =
        runViewModelTest(updatedDescription = "foo", updatedFormatAsMarkdown = true) {
            assertThat("database is unchanged", defaultDeck.description, equalTo(""))
            assertThat("dialog state is maintained", description, equalTo("foo"))
            assertThat("dialog state is maintained", formatAsMarkdown, equalTo(true))
        }

    @Test
    fun `no changes initially`() =
        runViewModelTest {
            assertThat(userHasMadeChanges, equalTo(false))
        }

    @Test
    fun `description is a change`() =
        runViewModelTest {
            description = "foo"
            assertThat(userHasMadeChanges, equalTo(true))
        }

    @Test
    fun `format as markdown is a change`() =
        runViewModelTest {
            formatAsMarkdown = false
            assertThat(userHasMadeChanges, equalTo(true))
        }

    @Test
    fun `saved changes update`() =
        runViewModelTest {
            flowOfHasChanges.test {
                assertThat("initial state", expectMostRecentItem(), equalTo(false))
                description = "foo"
                assertThat("has changes after change", expectMostRecentItem(), equalTo(true))
                description = ""
                assertThat("no changes if all reverted", expectMostRecentItem(), equalTo(false))
            }
        }

    val AnkiTest.defaultDeck
        get() = col.decks.getLegacy(DEFAULT_DECK_ID)!!

    private fun runViewModelTest(
        updatedDescription: String? = null,
        updatedFormatAsMarkdown: Boolean? = null,
        testBody: suspend EditDeckDescriptionDialogViewModel.() -> Unit,
    ) = runTest {
        val viewModel =
            EditDeckDescriptionDialogViewModel(
                savedStateHandleOf(
                    ARG_DECK_ID to DEFAULT_DECK_ID,
                    STATE_DESCRIPTION to updatedDescription,
                    STATE_FORMAT_AS_MARKDOWN to updatedFormatAsMarkdown,
                    STATE_USER_MADE_CHANGES to (updatedDescription != null || updatedFormatAsMarkdown != null),
                ),
            )
        testBody(viewModel)
    }
}

fun savedStateHandleOf(vararg pairs: Pair<String, Any?>): SavedStateHandle = SavedStateHandle(mapOf(*pairs))

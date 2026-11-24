// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.previewer

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.NotetypeFile
import com.ichi2.testutils.JvmTest
import com.ichi2.testutils.common.Flaky
import com.ichi2.testutils.common.OS
import io.mockk.coEvery
import io.mockk.spyk
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import kotlin.test.assertNotEquals

@RunWith(AndroidJUnit4::class)
class TemplatePreviewerViewModelTest : JvmTest() {
    @get:Rule
    val tempDirectory = TemporaryFolder()

    private fun createViewModel(arguments: TemplatePreviewerArguments): TemplatePreviewerViewModel {
        val savedStateHandle = SavedStateHandle(mapOf(TemplatePreviewerFragment.ARG_KEY to arguments))
        val viewModel = TemplatePreviewerViewModel(savedStateHandle)
        return spyk(viewModel).apply {
            // the default implementation requires the Collection media directory,
            // which needs a Robolectric setup with CollectionStorageMode.IN_MEMORY_WITH_MEDIA or ON_DISK
            coEvery { prepareCardTextForDisplay(any()) } answers { firstArg() }
        }
    }

    @Test
    fun `getCurrentTabIndex returns the correct tab if the first cloze isn't 1 and ord isn't 0`() =
        runClozeTest(ord = 6, fields = listOf("{{c7::foo}} {{c4::bar}} {{c9::ha}}")) {
            val expectedTab = 1 // 0 will be c4 (ord 3), 1: c7 (ord 6), 2: c9 (ord 8)
            assertThat(getCurrentTabIndex(), equalTo(expectedTab))
        }

    @Test
    @Flaky(OS.ALL)
    fun `correct cloze ord is shown for tab`() =
        runClozeTest(ord = 8, fields = listOf("{{c7::foo}} {{c4::bar}} {{c9::ha}}")) {
            onTabSelected(0) // 0 will be c4 (ord 3), 1: c7 (ord 6), 2: c9 (ord 8)
            assertThat(ordFlow.value, equalTo(3))
        }

    @Test
    fun `empty front field detected correctly for tab badge`() =
        runOptionalReversedTest(
            fields =
                listOf(
                    "we have two normal fields",
                    "and purposefully leave the third blank",
                    "",
                ),
        ) {
            onPageFinished(false)
            assertThat(this.cardsWithEmptyFronts!!.await()[0], equalTo(false))
            assertThat(this.cardsWithEmptyFronts.await()[1], equalTo(true))
        }

    @Test
    fun `card ords are changed`() {
        runClozeTest(tempDirectory = tempDirectory, fields = listOf("{{c1::one}} {{c2::bar}}")) {
            onPageFinished(false)
            val ord1 = currentCard.await().ord
            onTabSelected(1)
            val ord2 = currentCard.await().ord
            assertNotEquals(ord1, ord2)
        }
    }

    @Test
    fun `empty cloze cards of an existing note are shown - Issue 20919`() =
        runTest {
            val note = addClozeNote("{{c1::foo}} {{c2::bar}}")
            // c2 is removed in the note editor, but its card still exists
            val arguments =
                TemplatePreviewerArguments(
                    notetypeFile = NotetypeFile(tempDirectory.root, note.notetype),
                    fields = listOf("{{c1::foo}} bar", ""),
                    tags = emptyList(),
                    id = note.id,
                )
            createViewModel(arguments).run {
                assertThat(getTemplateNames(), equalTo(cardNames(1, 2)))
                assertThat(cardsWithEmptyFronts!!.await(), equalTo(listOf(false, true)))
            }
        }

    @Test
    fun `selected tab matches the previewed card if a cloze was removed - Issue 20919`() =
        runTest {
            val note = addClozeNote("{{c1::foo}} {{c2::bar}}").update { setItem("Text", "foo {{c2::bar}}") }
            // the template editor previews the first card of cloze note types
            val arguments =
                TemplatePreviewerArguments(
                    notetypeFile = NotetypeFile(tempDirectory.root, note.notetype),
                    fields = note.fields,
                    tags = note.tags,
                    id = note.id,
                    ord = 0,
                    fillEmpty = true,
                )
            createViewModel(arguments).run {
                assertThat(getTemplateNames(), equalTo(cardNames(1, 2)))
                assertThat(getCurrentTabIndex(), equalTo(0))
                assertThat(cardsWithEmptyFronts!!.await(), equalTo(listOf(true, false)))
            }
        }

    @Test
    fun `previewed card is listed after card 1 is deleted - Issue 20919`() =
        runTest {
            createViewModel(previewWithDeletedCard1()).run {
                assertThat(getTemplateNames(), equalTo(cardNames(2)))
                assertThat(getCurrentTabIndex(), equalTo(0))
                assertThat(currentCard.await().ord, equalTo(1))
            }
        }

    @Test
    fun `page shows a listed card if it loads before the cloze cards - Issue 20919`() =
        runTest {
            val arguments = previewWithDeletedCard1()
            // collection work waits for the scheduler, so the page finishes loading first
            val previousQueue = CollectionManager.setTestDispatcher(StandardTestDispatcher(testScheduler), useReentrantLock = false)
            try {
                val viewModel = createViewModel(arguments)
                viewModel.onPageFinished(isAfterRecreation = false)
                val shownCard = viewModel.currentCard
                advanceUntilIdle()
                assertThat(shownCard.await().ord, equalTo(1))
            } finally {
                CollectionManager.setTestDispatcher(previousQueue)
            }
        }

    /** The template editor previewing a cloze note whose card 1 was deleted by 'Empty Cards' */
    private fun previewWithDeletedCard1(): TemplatePreviewerArguments {
        val note = addClozeNote("{{c1::foo}} {{c2::bar}}").update { setItem("Text", "foo {{c2::bar}}") }
        col.removeCardsAndOrphanedNotes(listOf(note.cards(col).single { it.ord == 0 }.id))
        // the template editor previews the first card of cloze note types
        return TemplatePreviewerArguments(
            notetypeFile = NotetypeFile(tempDirectory.root, note.notetype),
            fields = note.fields,
            tags = note.tags,
            id = note.id,
            ord = 0,
            fillEmpty = true,
        )
    }

    private fun cardNames(vararg cardNumbers: Int) = cardNumbers.map { TR.cardTemplatesCard(it) }

    private fun runClozeTest(
        ord: Int = 0,
        fields: List<String>? = null,
        block: suspend TemplatePreviewerViewModel.() -> Unit,
    ) = runClozeTest(ord, tempDirectory, fields, block)

    private fun runOptionalReversedTest(
        ord: Int = 0,
        fields: List<String>? = null,
        block: suspend TemplatePreviewerViewModel.() -> Unit,
    ) = runTest {
        val notetype = col.notetypes.basicOptionalReversed
        val arguments =
            TemplatePreviewerArguments(
                notetypeFile = NotetypeFile(tempDirectory.root, notetype),
                fields = fields ?: listOf("question text", "answer text", "y"),
                tags = emptyList(),
                ord = ord,
            )
        val viewModel = createViewModel(arguments)
        block(viewModel)
    }

    private fun runClozeTest(
        ord: Int = 0,
        tempDirectory: TemporaryFolder,
        fields: List<String>? = null,
        block: suspend TemplatePreviewerViewModel.() -> Unit,
    ) = runTest {
        val notetype = col.notetypes.cloze
        val arguments =
            TemplatePreviewerArguments(
                notetypeFile = NotetypeFile(tempDirectory.root, notetype),
                fields = fields ?: listOf("{{c1::foo}} {{c2::bar}}", "anki"),
                tags = emptyList(),
                ord = ord,
            )
        val viewModel = createViewModel(arguments)
        block(viewModel)
    }
}

// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.previewer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.Flag
import com.ichi2.anki.pages.PostRequestBridge
import com.ichi2.anki.servicelayer.NoteService
import com.ichi2.anki.utils.ext.flag
import com.ichi2.testutils.JvmTest
import com.ichi2.testutils.mockWebResourceRequest
import io.mockk.coEvery
import io.mockk.spyk
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.seconds

@RunWith(AndroidJUnit4::class)
class PreviewerViewModelTest : JvmTest() {
    private lateinit var viewModel: PreviewerViewModel

    private fun TestScope.onNextButtonClick() {
        viewModel.onNextButtonClick()
        advanceUntilIdle()
    }

    private fun TestScope.onPreviousButtonClick() {
        viewModel.onPreviousButtonClick()
        advanceUntilIdle()
    }

    private fun TestScope.onSliderChange(sliderPosition: Int) {
        viewModel.onSliderChange(sliderPosition = sliderPosition)
        advanceUntilIdle()
    }

    private fun TestScope.toggleBackSideOnly() {
        viewModel.toggleBackSideOnly()
        advanceUntilIdle()
    }

    @Before
    override fun setUp() {
        super.setUp()
        val cardIds =
            (0..3).flatMap {
                val note = addBasicNote()
                note.cards().map { it.id }
            }
        updateDeckConfig(col.decks.selected()) { autoplay = false }
        viewModel = spyk(PreviewerViewModel(SavedStateHandle(), cardIds, initialIndex = 0))
        // the default implementation requires the Collection media directory,
        // which needs Robolectric with CollectionStorageMode.IN_MEMORY_WITH_MEDIA or ON_DISK
        coEvery { viewModel.prepareCardTextForDisplay(any()) } answers { firstArg() }
        runBlocking { withTimeout(10.seconds) { viewModel.currentCard.await() } }
    }

    @After
    override fun tearDown() {
        viewModel.viewModelScope.cancel()
        super.tearDown()
    }

    @Test
    fun `a missing image on the previous answer does not suppress the next question warning`() =
        runTest {
            onNextButtonClick()
            assertTrue(viewModel.showingAnswer.value)
            val reportedFiles = mutableListOf<String>()
            viewModel.mediaErrorHandler.processFailure(
                mockWebResourceRequest("${PostRequestBridge.BASE_URL}first-missing.jpg", mainFrame = false),
            ) { reportedFiles.add(it) }
            assertEquals(listOf("first-missing.jpg"), reportedFiles)

            onNextButtonClick()
            assertEquals(1, viewModel.currentIndex.value)
            assertFalse(viewModel.showingAnswer.value)
            viewModel.mediaErrorHandler.processFailure(
                mockWebResourceRequest("${PostRequestBridge.BASE_URL}second-missing.jpg", mainFrame = false),
            ) { reportedFiles.add(it) }

            assertEquals(listOf("first-missing.jpg", "second-missing.jpg"), reportedFiles)
        }

    @Test
    fun `next button`() =
        runTest {
            viewModel.onPageFinished(false)
            viewModel.showingAnswer.value = false

            // Click Next -> Should show Answer
            onNextButtonClick()
            assertTrue("Should be showing answer", viewModel.showingAnswer.value)
            assertEquals("Index should still be 0", 0, viewModel.currentIndex.value)

            // Click Next again -> Should move to Next Card (Index 1), Question Side
            onNextButtonClick()
            assertFalse("Should be showing question of next card", viewModel.showingAnswer.value)
            assertEquals("Index should be 1", 1, viewModel.currentIndex.value)
        }

    @Test
    fun `previous button`() =
        runTest {
            // Start at Index 1
            onSliderChange(sliderPosition = 2)
            assertEquals(1, viewModel.currentIndex.value)
            viewModel.showingAnswer.value = false

            // Click Prev -> Should move to Index 0
            onPreviousButtonClick()
            assertEquals(0, viewModel.currentIndex.value)

            // Click Prev on Index 0 (Question) -> Do nothing
            onPreviousButtonClick()
            assertFalse(viewModel.showingAnswer.value)
            assertEquals(0, viewModel.currentIndex.value)
        }

    @Test
    fun `toggle back side only`() =
        runTest {
            assertFalse(viewModel.backSideOnly.value) // initial state should be false

            toggleBackSideOnly()
            assertTrue(viewModel.backSideOnly.value)

            toggleBackSideOnly()
            assertFalse(viewModel.backSideOnly.value)
        }

    @Test
    fun `toggle flag`() =
        runTest {
            val newFlag = Flag.RED
            viewModel.toggleFlag(newFlag)
            assertEquals(newFlag, viewModel.flag.value)
            assertTrue(viewModel.currentCard.await().flag == newFlag)

            // Toggling the same flag should unset it
            viewModel.toggleFlag(newFlag)
            assertEquals(Flag.NONE, viewModel.flag.value)
        }

    @Test
    fun `slider change updates index`() =
        runTest {
            onSliderChange(sliderPosition = 3)

            assertEquals(2, viewModel.currentIndex.value)
            assertFalse(viewModel.showingAnswer.value)
        }

    @Test
    fun `Next button is disabled on last card answer`() =
        runTest {
            assertTrue(viewModel.isNextButtonEnabled.first())

            onSliderChange(sliderPosition = 4)
            viewModel.showingAnswer.value = true

            assertFalse("Next button should be disabled on last card answer", viewModel.isNextButtonEnabled.first())
        }

    @Test
    fun `toggle mark`() =
        runTest {
            viewModel.toggleMark()
            assertTrue(viewModel.isMarked.value)

            val note = viewModel.currentCard.await().note()
            assertTrue(NoteService.isMarked(note))
        }

    @Test
    fun `next, slider and previous navigation integration`() =
        runTest {
            // 1. Start at Index 0 (Question)
            viewModel.onPageFinished(false)
            assertEquals(0, viewModel.currentIndex.value)

            // 2. Next -> Index 0 (Answer)
            onNextButtonClick()
            assertTrue("Should be showing answer", viewModel.showingAnswer.value)

            // 3. Next -> Index 1 (Question)
            onNextButtonClick()
            assertEquals("Should move to next card", 1, viewModel.currentIndex.value)
            assertFalse("Should be showing question", viewModel.showingAnswer.value)

            // 4. Slider -> Jump to last card (Card 4 / Index 3)
            onSliderChange(sliderPosition = 4)
            assertEquals("Should jump to index 3", 3, viewModel.currentIndex.value)
            assertFalse("Slider jump should show question", viewModel.showingAnswer.value)

            // 5. Previous -> Should move back to Index 2 (Question)
            onPreviousButtonClick()
            assertEquals("Should move back to index 2", 2, viewModel.currentIndex.value)

            // 6. Show Answer on Index 2
            onNextButtonClick()
            assertTrue(viewModel.showingAnswer.value)

            // 7. Previous while showing Answer -> Should move to Index 1
            onPreviousButtonClick()
            assertEquals("Should move back to index 1", 1, viewModel.currentIndex.value)
            assertFalse("Should reset to question side on move", viewModel.showingAnswer.value)
        }

    @Test
    fun `backSideOnly with navigation`() =
        runTest {
            viewModel.onPageFinished(false)
            // 1. Initial State: Index 0, Question
            assertFalse(viewModel.backSideOnly.value)
            assertFalse(viewModel.showingAnswer.value)

            // 2. Toggle BackSideOnly ON -> Should immediately flip to Answer
            toggleBackSideOnly()
            assertTrue("BackSideOnly should be enabled", viewModel.backSideOnly.value)
            assertTrue("Should immediately show answer upon enabling", viewModel.showingAnswer.value)

            // 3. Next -> Should move to Index 1 and go straight to Answer (Skipping Question)
            onNextButtonClick()
            assertEquals("Should move to index 1", 1, viewModel.currentIndex.value)
            assertTrue("Should show answer (skipped question)", viewModel.showingAnswer.value)

            // 4. Slider -> Jump to Index 2 -> Should go straight to Answer
            onSliderChange(sliderPosition = 3) // Card 3 -> Index 2
            assertEquals("Should jump to index 2", 2, viewModel.currentIndex.value)
            assertTrue("Should show answer on jump", viewModel.showingAnswer.value)

            // 5. Previous -> Should move to Index 1 and stay on Answer
            onPreviousButtonClick()
            assertEquals("Should move back to index 1", 1, viewModel.currentIndex.value)
            assertTrue("Should show answer on prev", viewModel.showingAnswer.value)

            // 6. Toggle BackSideOnly OFF -> Should flip current card to Question
            toggleBackSideOnly()
            assertFalse("BackSideOnly should be disabled", viewModel.backSideOnly.value)
            assertFalse("Should flip to question upon disabling while on answer", viewModel.showingAnswer.value)

            // 7. Verify standard behavior is restored: Next stays on Index 1 but shows Answer
            onNextButtonClick()
            assertTrue("Should show answer", viewModel.showingAnswer.value)
            assertEquals("Should remain on index 1", 1, viewModel.currentIndex.value)
        }

    @Test
    fun `slider change ignores out of bounds values`() =
        runTest {
            // There are 4 cards and the current is the first one
            assertEquals(4, viewModel.selectedCardIds.size)
            assertEquals(0, viewModel.currentIndex.value)

            // 1. Upper Bound (Input 5 -> Index 4)
            onSliderChange(sliderPosition = 5)
            assertEquals("Index should not change for upper bound overflow", 0, viewModel.currentIndex.value)

            // 2. Lower Bound (Input 0 -> Index -1)
            onSliderChange(sliderPosition = 0)
            assertEquals("Index should not change for lower bound underflow", 0, viewModel.currentIndex.value)

            // 3. Valid input still works (Input 2 -> Index 1)
            onSliderChange(sliderPosition = 2)
            assertEquals("Index should update for valid input", 1, viewModel.currentIndex.value)
        }

    @Test
    fun `showing the answer stops the question audio`() =
        runTest {
            viewModel.onPageFinished(false)
            val audio = audioPlaying()

            onNextButtonClick()

            assertTrue("question audio should stop", audio.isCancelled)
        }

    @Test
    fun `going back to the question stops the answer audio`() =
        runTest {
            viewModel.onPageFinished(false)
            onNextButtonClick()
            val audio = audioPlaying()

            onPreviousButtonClick()

            assertTrue("answer audio should stop", audio.isCancelled)
        }

    @Test
    fun `showing only the back side stops the question audio`() =
        runTest {
            viewModel.onPageFinished(false)
            val audio = audioPlaying()

            toggleBackSideOnly()

            assertTrue("question audio should stop", audio.isCancelled)
        }

    @Test
    fun `audio keeps playing after a configuration change`() =
        runTest {
            viewModel.onPageFinished(false)
            val audio = audioPlaying()

            viewModel.onPageFinished(isAfterRecreation = true)
            advanceUntilIdle()

            assertFalse("audio should keep playing", audio.isCancelled)
        }

    private fun audioPlaying() = Job().also { viewModel.cardMediaPlayer.playAvTagsJob = it }
}

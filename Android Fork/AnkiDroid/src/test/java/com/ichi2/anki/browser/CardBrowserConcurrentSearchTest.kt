// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.github.ivanshafran.sharedpreferencesmock.SPMockBuilder
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.preferences.SharedPreferencesProvider
import com.ichi2.testutils.JvmPreferencesRule
import com.ichi2.testutils.JvmTest
import com.ichi2.testutils.createTransientDirectory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class CardBrowserConcurrentSearchTest : JvmTest() {
    @get:Rule(order = 0)
    val preferences = JvmPreferencesRule()

    private val lastDeckPreferences = SPMockBuilder().createSharedPreferences()
    private val viewModels = ViewModelStore()

    @After
    override fun tearDown() {
        try {
            viewModels.clear()
        } finally {
            super.tearDown()
        }
    }

    @Test
    fun `queued searches cancel the earlier search after UI notification resumes`() =
        runTest {
            addBasicNote("cat", "meows")
            val viewModel =
                CardBrowserViewModel(
                    lastDeckIdRepository = SharedPreferencesLastDeckIdRepository(SharedPreferencesProvider { lastDeckPreferences }),
                    idsFileDirectory = createTransientDirectory(),
                    options = null,
                    preferences = AnkiDroidApp.sharedPreferencesProvider,
                    isFragmented = true,
                    initialDeckId = null,
                    savedStateHandle = SavedStateHandle(),
                    manualInit = true,
                ).also { viewModels.put("browser", it) }
            viewModel.manualInit()
            viewModel.searchJob?.join()
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))

            val editorGate = CompletableDeferred<Unit>()
            val firstClearGate = CompletableDeferred<Unit>()
            val secondClearGate = CompletableDeferred<Unit>()
            var notifications = 0
            val editorCollector =
                backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
                    viewModel.flowOfNoteEditorCommand.collect { editorGate.await() }
                }
            val cardsCollector =
                backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
                    viewModel.flowOfCardsUpdated.collect {
                        when (notifications++) {
                            0 -> firstClearGate.await()
                            1 -> secondClearGate.await()
                        }
                    }
                }
            try {
                // Keep both UI subscribers busy so two requests suspend while clearing the rows.
                viewModel.flowOfNoteEditorCommand.emit(CardBrowserViewModel.NoteEditorCommand.HidePane)
                viewModel.flowOfCardsUpdated.emit(Unit)

                viewModel.launchSearchForCards(feedbackMode = BrowserSearchFeedbackMode.COUNT_ONLY)
                viewModel.launchSearchForCards()
                testScheduler.runCurrent()

                // Start the first search, keeping it active until the editor can receive its command.
                firstClearGate.complete(Unit)
                testScheduler.runCurrent()
                val earlierSearch = assertNotNull(viewModel.searchJob)
                assertFalse(earlierSearch.isCompleted, "the first search waits for the editor callback")

                secondClearGate.complete(Unit)
                testScheduler.runCurrent()
                val newerSearch = assertNotNull(viewModel.searchJob)
                assertNotSame(earlierSearch, newerSearch, "the second request starts another search")
                assertTrue(earlierSearch.isCancelled, "the newer request must cancel the earlier search")
            } finally {
                viewModel.searchJob?.cancel()
                editorCollector.cancel()
                cardsCollector.cancel()
                editorGate.complete(Unit)
                firstClearGate.complete(Unit)
                secondClearGate.complete(Unit)
                testScheduler.runCurrent()
            }
        }
}

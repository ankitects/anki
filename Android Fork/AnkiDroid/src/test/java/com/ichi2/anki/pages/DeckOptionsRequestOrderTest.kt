// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.deck_config.UpdateDeckConfigsMode
import anki.deck_config.copy
import anki.deck_config.updateDeckConfigsRequest
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.libanki.Consts
import com.ichi2.testutils.ext.defaultDeckNewCardsPerDay
import com.ichi2.testutils.withCollectionAccessOn
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class DeckOptionsRequestOrderTest : RobolectricTest() {
    @Test
    fun `queued close waits for the preceding deck options save`() = assertCloseWaitsForSave(stopped = true)

    @Test
    fun `close waits for a save while deck options is visible`() = assertCloseWaitsForSave(stopped = false)

    @Test
    fun `close waits for Save and Optimize`() =
        assertCloseWaitsForSave(stopped = false, mode = UpdateDeckConfigsMode.UPDATE_DECK_CONFIGS_MODE_COMPUTE_ALL_PARAMS)

    @Test
    fun `a failed save releases the queued close`() =
        runTest {
            val activity = startDeckOptionsController().get()
            val page = activity.fragment as DeckOptions

            // Invalid protobuf: the save must fail without leaving the close locked out.
            withPendingSaveAndClose(page, byteArrayOf(0)) { save, close ->
                assertFalse(close.isCompleted)
                assertFalse(activity.isFinishing)
                assertTrue(save.await().isFailure)
                close.await()
                assertTrue(activity.isFinishing)
            }
        }

    private fun assertCloseWaitsForSave(
        stopped: Boolean,
        mode: UpdateDeckConfigsMode = UpdateDeckConfigsMode.UPDATE_DECK_CONFIGS_MODE_NORMAL,
    ) = runTest {
        val controller = startDeckOptionsController()
        val page = controller.get().fragment as DeckOptions
        val input = deckOptionsSaveRequest(mode, newCardsPerDay = 42)
        if (stopped) controller.pause().stop()

        withPendingSaveAndClose(page, input) { save, close ->
            assertFalse(controller.get().isFinishing)
            assertFalse(close.isCompleted)

            // Other page requests must still be handled while the save is pending.
            page.handlePostRequest(PostRequestUri("/_anki/deckOptionsReady"), byteArrayOf())
            assertFalse(save.isCompleted)

            if (stopped) controller.start().resume().visible()
            save.await().getOrThrow()
            close.await()

            assertEquals(42, withCol { defaultDeckNewCardsPerDay })
            assertTrue(controller.get().isFinishing)
        }
    }

    private fun deckOptionsSaveRequest(
        mode: UpdateDeckConfigsMode,
        @Suppress("SameParameterValue") newCardsPerDay: Int,
    ): ByteArray {
        val data = col.backend.getDeckConfigsForUpdate(Consts.DEFAULT_DECK_ID)
        val preset =
            data.allConfigList.single().config.copy {
                config = config.copy { newPerDay = newCardsPerDay }
            }
        return updateDeckConfigsRequest {
            targetDeckId = Consts.DEFAULT_DECK_ID
            this.mode = mode
            fsrs = true
            configs += preset
        }.toByteArray()
    }

    /**
     * Simulate a close request arriving while a save is waiting for collection access.
     *
     * Provides 'save' and 'close' to the receiver so deferred work can be started.
     *
     * Fails if the close request completes before the save.
     */
    private suspend fun TestScope.withPendingSaveAndClose(
        page: DeckOptions,
        input: ByteArray,
        assertions: suspend (save: Deferred<Result<ByteArray>>, close: Deferred<Unit>) -> Unit,
    ) {
        withCollectionAccessOn(StandardTestDispatcher(testScheduler)) {
            coroutineScope {
                val save =
                    async(start = CoroutineStart.UNDISPATCHED) {
                        runCatching { page.handlePostRequest(PostRequestUri("/_anki/updateDeckConfigs"), input) }
                    }
                val close =
                    async(start = CoroutineStart.UNDISPATCHED) {
                        page.handlePostRequest(PostRequestUri("/_anki/deckOptionsRequireClose"), byteArrayOf())
                        assertTrue(save.isCompleted, "Close replied before the preceding save")
                    }
                assertions(save, close)
            }
        }
    }
}

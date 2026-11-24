// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import com.ichi2.anki.progress.ViewModelProgress
import com.ichi2.testutils.withCollectionAccessOn
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CardBrowserPreviewTest : JvmCardBrowserViewModelTestBase() {
    @Test
    fun `preview preparation reports progress until the destination is ready`() =
        runViewModelNotesTest(notes = 1) {
            withCollectionAccessOn(StandardTestDispatcher(currentCoroutineContext()[TestCoroutineScheduler])) {
                coroutineScope {
                    val preparation = async(start = CoroutineStart.UNDISPATCHED) { queryPreviewIntentData() }

                    assertFalse(preparation.isCompleted, "preview preparation is still waiting for the collection")
                    assertIs<ViewModelProgress.Active<CardBrowserProgress>>(progressManager.progress.value)

                    val destination = preparation.await()
                    assertEquals(1, assertNotNull(destination.idsFile.getIdsOrNull()).size)
                    assertIs<ViewModelProgress.Idle>(progressManager.progress.value)
                }
            }
        }

    @Test
    fun `cancelled preview preparation clears progress and can be retried`() =
        runViewModelNotesTest(notes = 1) {
            withCollectionAccessOn(StandardTestDispatcher(currentCoroutineContext()[TestCoroutineScheduler])) {
                coroutineScope {
                    val preparation = async(start = CoroutineStart.UNDISPATCHED) { queryPreviewIntentData() }

                    assertFalse(preparation.isCompleted, "preview preparation is still waiting for the collection")
                    assertIs<ViewModelProgress.Active<CardBrowserProgress>>(progressManager.progress.value)
                    preparation.cancelAndJoin()
                    assertTrue(preparation.isCancelled)
                    assertIs<ViewModelProgress.Idle>(progressManager.progress.value)

                    val retry = async(start = CoroutineStart.UNDISPATCHED) { queryPreviewIntentData() }
                    assertIs<ViewModelProgress.Active<CardBrowserProgress>>(progressManager.progress.value)
                    assertEquals(1, assertNotNull(retry.await().idsFile.getIdsOrNull()).size)
                    assertIs<ViewModelProgress.Idle>(progressManager.progress.value)
                }
            }
        }
}

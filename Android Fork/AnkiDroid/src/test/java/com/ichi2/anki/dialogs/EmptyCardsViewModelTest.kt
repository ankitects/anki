// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs

import android.annotation.SuppressLint
import anki.card_rendering.emptyCardsReport
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.libanki.Collection
import com.ichi2.testutils.JvmTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Test

class EmptyCardsViewModelTest : JvmTest() {
    @Test
    @SuppressLint("CheckResult")
    fun `search is reused when the view model is retained`() =
        runTest {
            val collection =
                mockk<Collection>(relaxed = true) {
                    every { dbClosed } returns false
                    every { getEmptyCards() } returns emptyCardsReport { }
                }
            CollectionManager.setColForTests(collection)
            val viewModel = EmptyCardsViewModel()

            viewModel.searchForEmptyCards()
            viewModel.searchForEmptyCards()
            advanceUntilIdle()
            viewModel.searchForEmptyCards()
            advanceUntilIdle()

            verify(exactly = 1) { collection.getEmptyCards() }
        }
}

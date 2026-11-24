// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Alok Srivastava <alok020505@gmail.com>

package com.ichi2.anki.browser

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Test

/** Test of [CardBrowserFragmentViewModel] */
class CardBrowserFragmentViewModelTest {
    @Test
    fun `openDeckSelectionDialog emits from flowOfSearchForDecks`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val viewModel = CardBrowserFragmentViewModel()

                viewModel.flowOfSearchForDecks.test {
                    viewModel.openDeckSelectionDialog()
                    awaitItem()
                }
            } finally {
                Dispatchers.resetMain()
            }
        }
}

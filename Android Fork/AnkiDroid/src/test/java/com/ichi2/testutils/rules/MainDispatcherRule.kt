// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils.rules

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.ExternalResource

/** Shares Main's test scheduler with `runTest` and restores the platform dispatcher after each test. */
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : ExternalResource() {
    override fun before() {
        Dispatchers.setMain(dispatcher)
    }

    override fun after() {
        Dispatchers.resetMain()
    }
}

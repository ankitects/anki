// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2023 Ankitects Pty Ltd <http://apps.ankiweb.net>

package com.ichi2.testutils

import androidx.annotation.CallSuper
import com.ichi2.anki.ioDispatcher
import com.ichi2.anki.libanki.testutils.InMemoryAnkiTest
import com.ichi2.anki.observability.ChangeManager
import com.ichi2.testutils.common.IgnoreFlakyTestsInCIRule
import kotlinx.coroutines.test.TestDispatcher
import org.junit.Before
import org.junit.Rule

open class JvmTest : InMemoryAnkiTest() {
    /** Allows [com.ichi2.testutils.common.Flaky] to annotate tests in subclasses */
    @get:Rule
    val ignoreFlakyTests = IgnoreFlakyTestsInCIRule()

    @Before
    @CallSuper
    override fun setUp() {
        super.setUp()
        ChangeManager.resetForTesting()
    }

    override fun setupTestDispatcher(dispatcher: TestDispatcher) {
        super.setupTestDispatcher(dispatcher)
        ioDispatcher = dispatcher
    }
}

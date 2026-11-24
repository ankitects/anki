// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2023 Ankitects Pty Ltd <http://apps.ankiweb.net>

package com.ichi2.testutils

import androidx.annotation.CallSuper
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.ioDispatcher
import com.ichi2.anki.libanki.CollectionFiles
import com.ichi2.anki.libanki.testutils.InMemoryAnkiTest
import com.ichi2.anki.observability.ChangeManager
import com.ichi2.testutils.common.IgnoreFlakyTestsInCIRule
import com.ichi2.testutils.rules.CollectionStorageRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestDispatcher
import org.junit.After
import org.junit.Before
import org.junit.Rule

open class JvmTest : InMemoryAnkiTest() {
    /** Allows [com.ichi2.testutils.common.Flaky] to annotate tests in subclasses */
    @get:Rule
    val ignoreFlakyTests = IgnoreFlakyTestsInCIRule()

    protected open val collectionFiles: CollectionFiles get() = CollectionFiles.InMemory

    @get:Rule(order = 1)
    val collectionStorage = CollectionStorageRule { collectionFiles }

    override val collectionManager = ProductionCollectionManager

    override val col get() = collectionManager.getColUnsafe()

    @Before
    @CallSuper
    override fun setUp() {
        super.setUp()
        ChangeManager.resetForTesting()
    }

    @After
    @CallSuper
    override fun tearDown() {
        try {
            if (CollectionManager.isOpenUnsafe()) {
                col.debugEnsureNoOpenPointers()
            }
        } finally {
            try {
                super.tearDown()
            } finally {
                ioDispatcher = Dispatchers.IO
            }
        }
    }

    override fun setupTestDispatcher(dispatcher: TestDispatcher) {
        super.setupTestDispatcher(dispatcher)
        ioDispatcher = dispatcher
    }

    override fun withTestDispatcher(
        dispatcher: TestDispatcher,
        block: () -> Unit,
    ) = withCollectionTestDispatcher(dispatcher, block)
}

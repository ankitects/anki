// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import com.ichi2.anki.exception.CollectionLockedException
import com.ichi2.testutils.JvmTest
import kotlinx.coroutines.test.StandardTestDispatcher
import net.ankiweb.rsdroid.BackendException.BackendDbException.BackendDbLockedException
import org.junit.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CollectionManagerStackTraceTest : JvmTest() {
    @Test
    fun `opening failure records the withCol caller`() =
        runTest {
            // Queue collection access so the failure occurs after the caller suspends.
            val previousQueue =
                CollectionManager.setTestDispatcher(StandardTestDispatcher(testScheduler), useReentrantLock = false)
            try {
                CollectionManager.emulatedOpenFailure = CollectionManager.CollectionOpenFailure.LOCKED

                val failure = assertFailsWith<CollectionLockedException> { requestCollection() }

                assertIs<BackendDbLockedException>(failure.cause)
                assertFalse(failure.stackTrace.any { it.methodName == "requestCollection" })
                val caller = failure.suppressed.single()
                assertTrue(caller.stackTrace.any { it.methodName == "requestCollection" })
            } finally {
                CollectionManager.emulatedOpenFailure = null
                CollectionManager.setTestDispatcher(previousQueue)
            }
        }

    private suspend fun requestCollection(): Nothing = CollectionManager.withCol { error("opening should fail first") }
}

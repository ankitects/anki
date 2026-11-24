// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import com.ichi2.anki.CollectionManager
import com.ichi2.anki.ioDispatcher
import kotlinx.coroutines.Dispatchers
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.JUnitCore
import org.junit.runners.BlockJUnit4ClassRunner
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class JvmTestCleanupTest {
    @Test
    fun `a leaked query fails the test and still cleans up the collection`() {
        val result = JUnitCore().run(BlockJUnit4ClassRunner(LeakedQueryTest::class.java))

        assertEquals(1, result.runCount)
        assertEquals(1, result.failureCount, result.failures.toString())
        val failure = assertIs<IllegalStateException>(result.failures.single().exception)
        assertContains(failure.message!!, "Contained unclosed sequence numbers")
        assertFalse(CollectionManager.isOpenUnsafe())
        assertSame(Dispatchers.IO, ioDispatcher)

        val nextTest = JUnitCore().run(BlockJUnit4ClassRunner(ClosedQueryTest::class.java))
        assertEquals(1, nextTest.runCount)
        assertEquals(0, nextTest.failureCount, nextTest.failures.toString())
    }

    @Ignore("Run explicitly by the enclosing test to verify teardown failures")
    class LeakedQueryTest : JvmTest() {
        @Test
        fun `leaves a paged query open`() =
            runTest {
                col.backend.setPageSize(9)
                col.db.query("select 1 union all select 2 union all select 3")
                assertTrue(col.backend.getActiveSequenceNumbers().isNotEmpty())
            }
    }

    @Ignore("Run explicitly by the enclosing test to verify cleanup after a failure")
    class ClosedQueryTest : JvmTest() {
        @Test
        fun `closes a paged query`() =
            runTest {
                col.backend.setPageSize(9)
                col.db.query("select 1 union all select 2 union all select 3").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(1, cursor.getInt(0))
                }
            }
    }
}

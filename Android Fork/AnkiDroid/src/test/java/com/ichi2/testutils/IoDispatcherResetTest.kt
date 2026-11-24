// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.ioDispatcher
import kotlinx.coroutines.Dispatchers
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.JUnitCore
import org.junit.runner.RunWith
import org.junit.runners.BlockJUnit4ClassRunner
import kotlin.test.assertEquals
import kotlin.test.assertSame

@RunWith(AndroidJUnit4::class)
class IoDispatcherResetTest {
    @Test
    fun `RobolectricTest resets ioDispatcher after runTest`() = assertResetsIoDispatcher(RobolectricRunTest::class.java)

    @Test
    fun `JvmTest resets ioDispatcher after runTest`() = assertResetsIoDispatcher(JvmRunTest::class.java)

    private fun assertResetsIoDispatcher(testClass: Class<*>) {
        val result = JUnitCore().run(BlockJUnit4ClassRunner(testClass))

        assertEquals(1, result.runCount)
        assertEquals(0, result.failureCount, result.failures.toString())
        assertSame(Dispatchers.IO, ioDispatcher)
    }

    @Ignore("Run explicitly by the enclosing test inside its Robolectric sandbox")
    class RobolectricRunTest : RobolectricTest() {
        @Test
        fun `uses runTest`() = runTest { }
    }

    @Ignore("Run explicitly by the enclosing test inside its Robolectric sandbox")
    class JvmRunTest : JvmTest() {
        @Test
        fun `uses runTest`() = runTest { }
    }
}

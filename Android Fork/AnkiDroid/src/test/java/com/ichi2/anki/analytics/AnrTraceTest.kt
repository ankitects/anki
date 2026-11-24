// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.analytics

import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnrTraceTest {
    @Test
    fun `extracts main thread frames from the captured Android 16 ANR`() {
        val frames =
            javaClass.getResourceAsStream("/anr/real-anr-excerpt.txt")!!.reader().use {
                assertNotNull(it.readAnrMainThreadStackTrace())
            }

        assertEquals(16, frames.size)
        assertEquals(StackTraceElement("jdk.internal.misc.Unsafe", "park", null, -2), frames.first())
        assertTrue(frames.first().isNativeMethod)
        assertEquals(
            StackTraceElement("com.ichi2.anki.analytics.AnrReproActivity", "deliberatelyBlockMainThread", "AnrReproActivity.kt", 58),
            frames[5],
        )
        assertEquals("D8\$\$SyntheticClass", frames[7].fileName)
        assertEquals(0, frames[7].lineNumber)
        assertEquals(StackTraceElement("com.android.internal.os.ZygoteInit", "main", "ZygoteInit.java", 932), frames.last())
        assertTrue(frames.none { "Finalizer" in it.className })
    }

    @Test
    fun `selects main rather than similarly named workers and stops at the next thread`() {
        val trace =
            """
            "main-worker" prio=5 tid=2 Runnable
              at com.example.Worker.run(Worker.kt:12)

            "main" prio=5 tid=1 Waiting
              | sysTid=1234
              at com.example.Activity.waitForWork(Activity.kt:42)
              - waiting to lock <0x1234> held by thread 2
            "worker" prio=5 tid=3 Runnable
              at com.example.Worker.run(Worker.kt:13)
            """.trimIndent()

        assertContentEquals(
            arrayOf(StackTraceElement("com.example.Activity", "waitForWork", "Activity.kt", 42)),
            trace.reader().readAnrMainThreadStackTrace(),
        )
    }

    @Test
    fun `preserves native and unknown source locations`() {
        val trace =
            """
            "main" prio=5 tid=1 Native
              native: #00 pc 0000000000000000 /apex/example.so (example+4)
              at com.example.Native.call(Native Method)
              at com.example.Hidden.call(Unknown Source)
              at com.example.Unresolved.call(Unresolved)
              at com.example.NoLine.call(Source.kt)
            """.trimIndent()

        assertContentEquals(
            arrayOf(
                StackTraceElement("com.example.Native", "call", null, -2),
                StackTraceElement("com.example.Hidden", "call", null, -1),
                StackTraceElement("com.example.Unresolved", "call", null, -1),
                StackTraceElement("com.example.NoLine", "call", "Source.kt", -1),
            ),
            trace.reader().readAnrMainThreadStackTrace(),
        )
    }

    @Test
    fun `does not fall back to the whole dump when main frames are missing`() {
        for (trace in listOf(
            "",
            "unrecognized dump",
            "\"worker\" prio=5 tid=2 Runnable\n  at com.example.Worker.run(Worker.kt:12)",
            "\"main\" prio=5 tid=1 Native\n  native: #00 pc 0000 example.so",
        )) {
            assertNull(trace.reader().readAnrMainThreadStackTrace())
        }
    }

    @Test
    fun `stops at a blank line or process boundary`() {
        for (boundary in listOf("", "----- end 1234 -----")) {
            val trace = "$MAIN_TRACE\n$boundary\n  at com.example.Unrelated.call(Unrelated.kt:99)"
            assertContentEquals(arrayOf(MAIN_FRAME), trace.reader().readAnrMainThreadStackTrace())
        }
    }

    @Test
    fun `reads main frames even when the remaining dump exceeds the scan limit`() {
        val trace = "$MAIN_TRACE\n\n" + "other thread diagnostics\n".repeat(20_000)
        assertContentEquals(arrayOf(MAIN_FRAME), trace.reader().readAnrMainThreadStackTrace())
    }

    @Test
    fun `bounds the input scanned when the main thread cannot be found`() {
        val trace = "preamble\n".repeat(40_000) + MAIN_TRACE
        assertNull(trace.reader().readAnrMainThreadStackTrace())
    }

    @Test
    fun `limits deeply recursive stacks to 64 frames`() {
        val trace = "\"main\" prio=5 tid=1 Runnable\n" + (1..100).joinToString("\n") { "  at com.example.Recursive.call(Recursive.kt:$it)" }
        val frames = assertNotNull(trace.reader().readAnrMainThreadStackTrace())
        assertEquals(64, frames.size)
        assertEquals(1, frames.first().lineNumber)
        assertEquals(64, frames.last().lineNumber)
    }

    @Test
    fun `bounds the captured frame text as well as the frame count`() {
        val oversizedFrame = "  at com.example.${"x".repeat(16 * 1024)}.call(Source.kt:1)"
        assertContentEquals(arrayOf(MAIN_FRAME), "$MAIN_TRACE\n$oversizedFrame".reader().readAnrMainThreadStackTrace())
        assertNull("\"main\" prio=5 tid=1 Runnable\n$oversizedFrame".reader().readAnrMainThreadStackTrace())
    }
}

private val MAIN_FRAME = StackTraceElement("com.example.Activity", "block", "Activity.kt", 42)
private const val MAIN_TRACE = "\"main\" prio=5 tid=1 Waiting\n  at com.example.Activity.block(Activity.kt:42)"

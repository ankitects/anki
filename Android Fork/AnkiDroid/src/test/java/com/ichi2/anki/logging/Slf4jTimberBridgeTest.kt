// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.logging

import android.util.Log
import com.ichi2.anki.common.utils.isRunningAsUnitTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Isolated
import org.slf4j.LoggerFactory
import timber.log.Timber
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Isolated
class Slf4jTimberBridgeTest {
    private data class LogEntry(
        val priority: Int,
        val tag: String?,
        val message: String,
        val throwable: Throwable?,
    )

    private val logs = CopyOnWriteArrayList<LogEntry>()

    private val recordingTree =
        object : Timber.Tree() {
            override fun log(
                priority: Int,
                tag: String?,
                message: String,
                t: Throwable?,
            ) {
                logs.add(LogEntry(priority, tag, message, t))
            }
        }

    private val logger = LoggerFactory.getLogger(Slf4jTimberBridgeTest::class.java)

    @BeforeEach
    fun setUp() {
        Timber.plant(recordingTree)
    }

    @AfterEach
    fun tearDown() {
        Timber.uproot(recordingTree)
    }

    @Test
    fun `logs from common reach Timber`() {
        assertTrue(isRunningAsUnitTest)

        val log = logs.single { it.tag == "TestUtils" }
        assertEquals(Log.DEBUG, log.priority)
        assertEquals("isRunningAsUnitTest: true", log.message)
    }

    @Test
    fun `SLF4J exceptions reach Timber`() {
        val exception = IllegalStateException("boom")

        logger.error("failed", exception)

        val log = logs.single { it.throwable === exception }
        assertEquals(Log.ERROR, log.priority)
    }
}

// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2023 Ankitects Pty Ltd <http://apps.ankiweb.net>

package com.ichi2.anki.libanki.testutils

import com.ichi2.anki.common.time.MockTime
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.libanki.Collection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import net.ankiweb.rsdroid.BackendException
import net.ankiweb.rsdroid.testing.RustBackendLoader
import org.hamcrest.Matcher
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TestName
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(InMemoryAnkiTest::class.java)

@Suppress("RestrictedApi", "VisibleForTests")
abstract class InMemoryAnkiTest : AnkiTest {
    @get:Rule
    val testName = TestName()

    private fun maybeSetupBackend() {
        RustBackendLoader.ensureSetup()
    }

    override val col: Collection
        get() {
            if (_col == null) {
                _col = collectionManager.getColUnsafe()
            }
            return _col!!
        }
    override val collectionManager: TestCollectionManager = InMemoryCollectionManager()

    private var _col: Collection? = null

    @Before
    open fun setUp() {
        println("""-- executing test "${testName.methodName}"""")
        TimeManager.resetWith(MockTime(2020, 7, 7, 7, 0, 0, 0, 10))

        maybeSetupBackend()
        // access 'col' ensuring that it's set up using the correct CollectionManager
        // PERF: This makes tests which do not need the collection less efficient, as this
        // opens a collection
        ensureCollectionLoadIsSynchronous()
    }

    @After
    open fun tearDown() {
        try {
            // If you don't tear down the database you'll get unexpected IllegalStateExceptions related to connections
            _col?.close()
        } catch (ex: BackendException) {
            if ("CollectionNotOpen" == ex.message) {
                logger.warn("Collection was already disposed - may have been a problem", ex)
            } else {
                throw ex
            }
        } finally {
            TimeManager.reset()
        }
        _col = null
        Dispatchers.resetMain()
        runBlocking { collectionManager.discardBackend() }
        println("""-- completed test "${testName.methodName}"""")
    }

    fun <T> assumeThat(
        actual: T,
        matcher: Matcher<T>?,
    ) {
        Assume.assumeThat(actual, matcher)
    }
}

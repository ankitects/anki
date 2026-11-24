// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager.CollectionOpenFailure
import com.ichi2.anki.InitialActivity.StartupFailure
import com.ichi2.anki.InitialActivity.getStartupFailureType
import com.ichi2.anki.InitialActivityWithConflictTest.Companion.setupForDatabaseConflict
import com.ichi2.anki.InitialActivityWithConflictTest.Companion.setupForDefault
import com.ichi2.anki.backend.DatabaseCorruption
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.MatcherAssert.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Failures raised while opening the collection.
 */
@RunWith(AndroidJUnit4::class)
class InitialActivityDatabaseFailureTest : RobolectricTest() {
    @Before
    override fun setUp() {
        super.setUp()
        DatabaseCorruption.isDetected = false
        setupForDatabaseConflict()
    }

    @After
    override fun tearDown() {
        CollectionManager.emulatedOpenFailure = null
        DatabaseCorruption.isDetected = false
        super.tearDown()
        setupForDefault()
    }

    @Test
    fun `a full disk is reported as DiskFull`() {
        CollectionManager.emulatedOpenFailure = CollectionOpenFailure.DISK_FULL

        val failure = getStartupFailureType(targetContext)

        assertThat("a full disk should be reported", failure, equalTo(StartupFailure.DiskFull))
    }

    @Test
    fun `a corrupt collection is flagged as corruption`() {
        CollectionManager.emulatedOpenFailure = CollectionOpenFailure.DATABASE_CORRUPT

        val failure = getStartupFailureType(targetContext)

        assertThat("a corrupt collection is a DB error", failure, instanceOf(StartupFailure.DBError::class.java))
        assertThat("corruption should be detected", DatabaseCorruption.isDetected, equalTo(true))
    }
}

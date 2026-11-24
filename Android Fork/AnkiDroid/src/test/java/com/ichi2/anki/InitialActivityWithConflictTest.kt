// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.InitialActivity.StartupFailure
import com.ichi2.anki.InitialActivity.getStartupFailureType
import com.ichi2.testutils.BackendEmulatingOpenConflict
import com.ichi2.testutils.BackupManagerTestUtilities
import com.ichi2.testutils.grantWritePermissions
import com.ichi2.testutils.revokeWritePermissions
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowEnvironment

@RunWith(AndroidJUnit4::class)
class InitialActivityWithConflictTest : RobolectricTest() {
    @Before
    override fun setUp() {
        super.setUp()
        BackendEmulatingOpenConflict.enable()
    }

    @After
    override fun tearDown() {
        super.tearDown()
        BackendEmulatingOpenConflict.disable()
    }

    @Test
    fun testInitialActivityResult() {
        try {
            setupForDatabaseConflict()

            val f = getStartupFailureType(targetContext)

            assertThat("A conflict should be returned", f, equalTo(StartupFailure.DatabaseLocked))
        } finally {
            setupForDefault()
        }
    }

    companion object {
        fun setupForDatabaseConflict() {
            grantWritePermissions()
            ShadowEnvironment.setExternalStorageState("mounted")
        }

        fun setupForValid(context: Context) {
            grantWritePermissions()
            ShadowEnvironment.setExternalStorageState("mounted")
            BackupManagerTestUtilities.setupSpaceForBackup(context)
        }

        fun setupForDefault() {
            revokeWritePermissions()
            ShadowEnvironment.setExternalStorageState("removed")
        }
    }
}

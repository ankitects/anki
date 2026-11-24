// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.exception.CollectionLockedException
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackendEmulatingOpenConflictTest : RobolectricTest() {
    @After
    override fun tearDown() {
        super.tearDown()
        BackendEmulatingOpenConflict.disable()
    }

    @Test
    fun `enabling conflicts replaces an already initialized backend`() {
        CollectionManager.getBackend()
        BackendEmulatingOpenConflict.enable()

        assertThrows(
            CollectionLockedException::class.java,
        ) { CollectionManager.getColUnsafe() }
    }
}

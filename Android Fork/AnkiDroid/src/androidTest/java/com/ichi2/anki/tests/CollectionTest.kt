// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>

package com.ichi2.anki.tests

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.testutil.GrantStoragePermission
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.junit.JUnitAsserter.assertNotNull

/**
 * This test case verifies that the directory initialization works even if the app is not yet fully initialized.
 */
@RunWith(AndroidJUnit4::class)
class CollectionTest : InstrumentedTest() {
    @get:Rule
    val runtimePermissionRule = GrantStoragePermission.instance

    @Test
    fun testOpenCollection() {
        assertNotNull("Collection could not be opened", col)
    }
}

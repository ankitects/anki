/*
 * Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
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

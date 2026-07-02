/*
 * Copyright (c) 2025 Alex Odorico <alex.odorico03@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.dialogs

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionHelper.getCurrentAnkiDroidDirectory
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.R
import com.ichi2.anki.backend.DatabaseCorruption
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.anki.tests.Shared
import com.ichi2.anki.testutil.discardPreliminaryViews
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber
import java.io.File

@RunWith(AndroidJUnit4::class)
class DatabaseCorruptDialogTest : InstrumentedTest() {
    private lateinit var targetColFile: File
    private lateinit var corruptColFile: File

    @Before
    fun setup() {
        val dbDir = getCurrentAnkiDroidDirectory(testContext)
        targetColFile =
            File(dbDir, "collection.anki2").apply {
                mkdirs()
                deleteOnExit()
            }
        corruptColFile = Shared.getTestFile(testContext, "initial_version_2_12_1_corrupt_regular.anki2")

        corruptColFile.copyTo(targetColFile, overwrite = true)
        Timber.i("Copied %d bytes to target", targetColFile.length())

        ActivityScenario.launch(DeckPicker::class.java)
        discardPreliminaryViews()
    }

    @Test
    fun testCorruptDialogFlagSet() {
        Assert.assertTrue(DatabaseCorruption.isDetected)
    }

    @Test
    fun testOpenCollectionFailedDialog() {
        onView(withText(R.string.open_collection_failed_title))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
    }

    @Test
    fun testCorruptCollectionDialog() {
        val corruptMsg =
            testContext.getString(
                R.string.corrupt_db_message,
                testContext.getString(R.string.repair_deck),
            )
        onView(withText(corruptMsg))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
    }

    @After
    fun cleanupCorruptColFile() {
        CollectionManager.closeCollectionBlocking()
        if (!targetColFile.delete()) {
            Timber.e("Cleanup: Failed to delete %s", targetColFile.path)
        }
    }
}

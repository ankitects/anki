// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Sumit Singh <sumitsinghkoranga7@gmail.com>

package com.ichi2.anki.dialogs

import androidx.core.content.edit
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionHelper
import com.ichi2.anki.CollectionHelper.getDefaultAnkiDroidDirectory
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.R
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.anki.testutil.disableIntroductionSlide
import com.ichi2.anki.testutil.discardPreliminaryViews
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * When the stored collection path is wrong or missing, the error-handling dialog must offer
 * "Use default collection folder" so the user can recover.
 */
@RunWith(AndroidJUnit4::class)
class CollectionPathMismatchRecoveryTest : InstrumentedTest() {
    private lateinit var nonDefaultDir: File
    private lateinit var effectiveCollectionDir: File
    private var savedPath: String? = null

    @Before
    fun setUp() {
        disableIntroductionSlide()
        CollectionManager.closeCollectionBlocking()
        val defaultDir = getDefaultAnkiDroidDirectory(testContext)
        nonDefaultDir = File(testContext.cacheDir, "CollectionPathMismatchRecoveryTest").apply { mkdirs() }
        require(nonDefaultDir.absolutePath != defaultDir.absolutePath) {
            "Need non-default dir; default=$defaultDir, test=$nonDefaultDir"
        }
        val prefs = testContext.applicationContext.sharedPrefs()
        savedPath = prefs.getString(CollectionHelper.PREF_COLLECTION_PATH, null)
        prefs.edit {
            putString(CollectionHelper.PREF_COLLECTION_PATH, nonDefaultDir.absolutePath)
        }
        effectiveCollectionDir = File(nonDefaultDir, "androidTest").apply { mkdirs() }
        // Use invalid bytes so opening this "collection" fails and triggers the recovery dialog
        File(effectiveCollectionDir, "collection.anki2").writeBytes("not_sqlite".toByteArray())

        ActivityScenario.launch(DeckPicker::class.java)
        discardPreliminaryViews()
    }

    @Test
    fun errorHandlingShowsUseDefaultFolderOption() {
        onView(withText(R.string.open_collection_failed_title))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
        onView(withText(R.string.error_handling_options)).inRoot(isDialog()).perform(click())
        onView(withText(R.string.error_handling_title))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
        onView(withText(R.string.backup_use_default_folder))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
    }

    @After
    fun tearDown() {
        CollectionManager.closeCollectionBlocking()
        testContext.applicationContext.sharedPrefs().edit {
            // Restore original collection path preference (or clear it if it didn't exist)
            savedPath?.let { putString(CollectionHelper.PREF_COLLECTION_PATH, it) }
                ?: remove(CollectionHelper.PREF_COLLECTION_PATH)
        }
        File(effectiveCollectionDir, "collection.anki2").takeIf { it.exists() }?.delete()
        effectiveCollectionDir.takeIf { it.exists() }?.delete()
    }
}

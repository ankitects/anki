// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Shaan Narendran <shaannaren06@gmail.com>

package com.ichi2.preferences

import androidx.core.content.edit
import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.EmptyApplicationCategory
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.anki.startup.getDefaultAnkiDroidDirectory
import com.ichi2.testutils.AndroidTest
import com.ichi2.testutils.EmptyApplication
import com.ichi2.testutils.targetContext
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.instanceOf
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * Tests that [ExternalDirectorySelectionPreference] offers the directories a user
 * would expect to pick from.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class ExternalDirectorySelectionPreferenceTest : AndroidTest {
    @Test
    fun `the default directory is offered`() {
        val pref = buildPreference()

        assertThat(pref.entryPaths(), hasItem(getDefaultAnkiDroidDirectory(targetContext).absolutePath))
    }

    @Test
    fun `the current directory is offered`() {
        val current = File(targetContext.filesDir, "AnkiDroidTest").absolutePath
        targetContext.sharedPrefs().edit(commit = true) {
            putString(CollectionHelper.PREF_COLLECTION_PATH, current)
        }

        val pref = buildPreference()

        assertThat(pref.entryPaths(), hasItem(current))
    }

    @Test
    fun `each entry has a label`() {
        val pref = buildPreference()
        pref.makeDialogFragment()

        assertThat(pref.entries.size, equalTo(pref.entryValues.size))
    }

    @Test
    fun `the dialog is the full width list`() {
        val pref = buildPreference()

        assertThat(pref.makeDialogFragment(), instanceOf(FullWidthListPreferenceDialogFragment::class.java))
    }

    /** Builds the preference attached to a screen, as the settings screen does */
    private fun buildPreference(): ExternalDirectorySelectionPreference {
        val pref = ExternalDirectorySelectionPreference(targetContext, null)
        pref.key = CollectionHelper.PREF_COLLECTION_PATH
        val preferenceManager = PreferenceManager(targetContext)
        val screen = preferenceManager.createPreferenceScreen(targetContext)
        preferenceManager.setPreferences(screen)
        screen.addPreference(pref)
        return pref
    }

    /** The paths offered by the dialog */
    private fun ExternalDirectorySelectionPreference.entryPaths(): List<String> {
        makeDialogFragment()
        return entryValues.map { it.toString() }
    }
}

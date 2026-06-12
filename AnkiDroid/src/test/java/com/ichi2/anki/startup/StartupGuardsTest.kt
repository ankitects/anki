// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.startup

import android.app.Activity
import android.content.Intent
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionHelper
import com.ichi2.anki.IntentHandler
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.storage.StorageDecision
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class StartupGuardsTest : RobolectricTest() {
    @After
    fun resetStorageDecision() {
        CollectionHelper.storageDecisionTestOverride = null
    }

    private fun buildActivity(): Activity = Robolectric.buildActivity(Activity::class.java).create().get()

    @Test
    fun `passes when storage is decided and accessible`() {
        val activity = buildActivity()

        assertTrue(activity.ensureStorageIsReady())

        assertFalse(activity.isFinishing, "activity should continue to start")
        assertNull(shadowOf(activity).nextStartedActivity, "no redirect expected")
    }

    @Test
    fun `redirects to the main entry point when storage is undecided`() {
        CollectionHelper.storageDecisionTestOverride = StorageDecision.Undecided
        val activity = buildActivity()

        assertFalse(activity.ensureStorageIsReady())

        assertTrue(activity.isFinishing, "activity should finish")
        val redirect = shadowOf(activity).nextStartedActivity
        assertNotNull(redirect, "the main entry point should be opened")
        assertEquals(IntentHandler::class.qualifiedName, redirect.component?.className)
        assertEquals(Intent.ACTION_MAIN, redirect.action)
        val expectedFlags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        assertEquals(expectedFlags, redirect.flags and expectedFlags, "task should be cleared")
    }

    @Test
    fun `finishes without redirect when a legacy folder is inaccessible`() {
        // a legacy collection folder requires storage permissions, which tests do not hold
        targetContext.sharedPrefs().edit {
            putString(CollectionHelper.PREF_COLLECTION_PATH, "/storage/emulated/0/AnkiDroid")
        }
        val activity = buildActivity()

        assertFalse(activity.ensureStorageIsReady())

        assertTrue(activity.isFinishing, "activity should finish")
        assertNull(shadowOf(activity).nextStartedActivity, "no redirect expected")
    }
}

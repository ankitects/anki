// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.fragment.app.FragmentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.exception.StorageNotConfiguredException
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class CoroutineHelpersRobolectricTest : RobolectricTest() {
    /**
     * A [StorageNotConfiguredException] escaping a coroutine means an activity raced or
     * outlived its [ensureStorageIsReady][com.ichi2.anki.startup.ensureStorageIsReady] check:
     * the user should be sent to the main entry point, which handles storage setup.
     */
    @Test
    fun `launchCatchingTask redirects to main entry point when storage is not configured`() {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).create().get()

        activity.launchCatchingTask { throw StorageNotConfiguredException() }
        advanceRobolectricLooper()

        assertTrue(activity.isFinishing, "activity should finish")
        val redirect = shadowOf(activity).nextStartedActivity
        assertNotNull(redirect, "the main entry point should be opened")
        assertEquals(IntentHandler::class.qualifiedName, redirect.component?.className)
    }
}

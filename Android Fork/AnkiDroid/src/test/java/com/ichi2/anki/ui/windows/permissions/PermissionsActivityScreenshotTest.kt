// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.permissions

import com.google.testing.junit.testparameterinjector.TestParameter
import com.ichi2.anki.R
import com.ichi2.anki.ScreenshotTest
import com.ichi2.anki.StoragePermissionSet
import com.ichi2.anki.simulateEdgeToEdge
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs

/** Baseline permission-screen screenshots, with and without simulated system-bar insets. */
class PermissionsActivityScreenshotTest : ScreenshotTest() {
    @Test
    fun permissions(
        @TestParameter isEdgeToEdge: Boolean,
    ) {
        val activity =
            startActivityNormallyOpenCollectionWithIntent(
                PermissionsActivity::class.java,
                PermissionsActivity.getIntent(targetContext, StoragePermissionSet.LEGACY_ACCESS),
            )
        assertFalse(activity.isFinishing, "The permissions screen must remain open for capture")
        assertIs<PermissionsUntil29Fragment>(activity.supportFragmentManager.findFragmentById(R.id.fragment_container))

        if (isEdgeToEdge) {
            activity.simulateEdgeToEdge()
        }

        captureScreen("permissions" + if (isEdgeToEdge) "_edgeToEdge" else "")
    }
}

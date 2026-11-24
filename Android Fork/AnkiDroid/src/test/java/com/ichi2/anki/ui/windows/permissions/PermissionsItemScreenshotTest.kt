// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.permissions

import android.graphics.Color
import android.graphics.Rect
import androidx.core.content.edit
import androidx.core.view.drawToBitmap
import com.google.testing.junit.testparameterinjector.TestParameter
import com.ichi2.anki.R
import com.ichi2.anki.ScreenshotTest
import com.ichi2.anki.StoragePermissionSet
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.testutils.publicCollectionPath
import com.ichi2.testutils.simulateSystemBars
import org.junit.Test
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Regression coverage for configured permission icons at normal and enlarged text sizes. */
class PermissionsItemScreenshotTest : ScreenshotTest() {
    @Test
    fun `configured permission icon is visible`(
        @TestParameter("1.0", "2.0") fontScale: Float,
        @TestParameter("EXTERNAL_MANAGER") permissionSet: StoragePermissionSet,
    ) {
        RuntimeEnvironment.setFontScale(fontScale)
        targetContext.sharedPrefs().edit { putString(CollectionHelper.PREF_COLLECTION_PATH, publicCollectionPath) }
        val intent = PermissionsActivity.getIntent(targetContext, permissionSet)
        Robolectric.buildActivity(PermissionsActivity::class.java, intent).use { controller ->
            val activity = controller.setup().get()
            activity.simulateSystemBars()
            advanceRobolectricLooper()
            captureScreen("permission_icon_font_$fontScale")

            val icon = activity.findViewById<PermissionsItem>(R.id.all_files_permission).binding.icon
            val visibleBounds = Rect()
            assertTrue(
                icon.getGlobalVisibleRect(visibleBounds),
                "The configured icon must be visible; size=${icon.width}x${icon.height}, bounds=${icon.left},${icon.top},${icon.right},${icon.bottom}",
            )
            assertEquals(icon.width, visibleBounds.width())
            assertEquals(icon.height, visibleBounds.height())
            val bitmap = icon.drawToBitmap()
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            assertTrue(pixels.any { Color.alpha(it) > 0 }, "The configured icon must draw visible pixels")
        }
    }
}

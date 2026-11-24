// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Build
import android.view.WindowInsets
import android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
import android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
import android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
import android.view.WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST
import android.view.WindowManager.LayoutParams.SOFT_INPUT_MASK_STATE
import android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.EmptyApplicationCategory
import com.ichi2.testutils.EmptyApplication
import com.ichi2.utils.DisplayUtils.setDialogKeyboardResize
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import com.ichi2.anki.common.android.R as CommonR

@RunWith(AndroidJUnit4::class)
@Config(sdk = [29, 30, 36], application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class DisplayUtilsTest {
    @Test
    fun `resizing preserves keyboard visibility settings before and after show`() =
        withDialog { dialog ->
            val window = dialog.window!!
            window.setSoftInputMode(SOFT_INPUT_STATE_ALWAYS_HIDDEN or SOFT_INPUT_ADJUST_PAN)
            repeat(2) {
                setDialogKeyboardResize(dialog)
                assertEquals(SOFT_INPUT_STATE_ALWAYS_HIDDEN, window.attributes.softInputMode and SOFT_INPUT_MASK_STATE)
                @Suppress("DEPRECATION")
                val expectedAdjustment = if (Build.VERSION.SDK_INT >= 30) SOFT_INPUT_ADJUST_NOTHING else SOFT_INPUT_ADJUST_RESIZE
                assertEquals(expectedAdjustment, window.attributes.softInputMode and SOFT_INPUT_MASK_ADJUST)
                if (Build.VERSION.SDK_INT >= 30) {
                    assertEquals(WindowInsets.Type.ime(), window.attributes.fitInsetsTypes and WindowInsets.Type.ime())
                }
                dialog.show()
            }
        }

    @Test
    @Config(sdk = [30, 36])
    @SuppressLint("NewApi") // @Config restricts this test to API 30+.
    fun `switching to pan removes IME fitting and retains other inset types`() =
        withDialog { dialog ->
            val window = dialog.window!!
            val initialTypes = WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
            window.attributes = window.attributes.apply { fitInsetsTypes = initialTypes }
            dialog.show()
            repeat(2) {
                setDialogKeyboardResize(dialog)
                assertEquals(initialTypes or WindowInsets.Type.ime(), window.attributes.fitInsetsTypes)
                setDialogKeyboardResize(dialog, resize = false)
                assertEquals(initialTypes, window.attributes.fitInsetsTypes)
                assertEquals(SOFT_INPUT_ADJUST_PAN, window.attributes.softInputMode and SOFT_INPUT_MASK_ADJUST)
            }
        }

    private fun withDialog(block: (AlertDialog) -> Unit) {
        Robolectric.buildActivity(Activity::class.java).use { controller ->
            val activity = controller.get()
            activity.setTheme(CommonR.style.Theme_AnkiDroid)
            controller.setup()
            val dialog = AlertDialog.Builder(activity).setView(EditText(activity)).create()
            try {
                block(dialog)
            } finally {
                dialog.dismiss()
            }
        }
    }
}

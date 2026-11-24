// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.utils

import android.app.Dialog
import android.content.Context
import android.graphics.Insets
import android.graphics.Point
import android.os.Build
import android.view.WindowInsets
import android.view.WindowManager
import com.ichi2.anki.compat.CompatHelper

object DisplayUtils {
    @Suppress("DEPRECATION") // #9333: defaultDisplay & getSize
    fun getDisplayDimensions(wm: WindowManager): Point {
        val point = Point()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            val insets: Insets =
                wm.currentWindowMetrics
                    .getWindowInsets()
                    .getInsetsIgnoringVisibility(
                        WindowInsets.Type.navigationBars()
                            or WindowInsets.Type.displayCutout(),
                    )
            point.x = bounds.width() - (insets.right + insets.left)
            point.y = bounds.height() - (insets.top + insets.bottom)
        } else {
            val display = wm.defaultDisplay
            display.getSize(point)
        }
        return point
    }

    fun getDisplayDimensions(context: Context): Point {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return getDisplayDimensions(wm)
    }

    /**
     * Fit a floating dialog above the keyboard, or pan to its focused input when [resize] is false.
     *
     * Activities with edge-to-edge content should handle IME insets in their own layout.
     */
    fun setDialogKeyboardResize(
        dialog: Dialog,
        resize: Boolean = true,
    ) {
        val window = dialog.window ?: return
        CompatHelper.compat.setDialogKeyboardResize(window, resize)
    }
}

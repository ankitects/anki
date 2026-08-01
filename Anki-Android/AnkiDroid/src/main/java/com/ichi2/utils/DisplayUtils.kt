/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.utils

import android.content.Context
import android.graphics.Insets
import android.graphics.Point
import android.os.Build
import android.view.Window
import android.view.WindowInsets
import android.view.WindowManager

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

    /** Allow the window to be resized when an input method is shown,
     * so that its contents are not covered by the input method */
    @Suppress("DEPRECATION") // 7110: SOFT_INPUT_ADJUST_RESIZE
    fun resizeWhenSoftInputShown(window: Window) {
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }
}

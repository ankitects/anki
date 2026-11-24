// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.view.View
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.color.MaterialColors
import com.google.android.material.search.SearchView
import com.ichi2.anki.R

/**
 * Fades app-bar controls beneath the status icons while preserving the app-bar surface color.
 *
 * The fade only affects the app bar; results are shown in full.
 *
 * @param root View which hosts contains the search view.
 * @param appBar Scrolling app bar whose position determines how much of the status-bar area is faded.
 */
class BrowserStatusBarDrawable(
    private val root: View,
    private val appBar: AppBarLayout,
) : ColorDrawable(MaterialColors.getColor(root, com.google.android.material.R.attr.colorSurface)) {
    private val rootLocation = IntArray(2)
    private val appBarLocation = IntArray(2)
    private val paint = Paint()
    private val searchView = root.findViewById<SearchView>(R.id.search_view)

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun draw(canvas: Canvas) {
        if (searchView.currentTransitionState != SearchView.TransitionState.HIDDEN) return
        root.getLocationInWindow(rootLocation)
        appBar.getLocationInWindow(appBarLocation)
        val appBarBottom = (appBarLocation[1] - rootLocation[1] + appBar.height).coerceIn(bounds.top, bounds.bottom)

        // Controls already sit on the app-bar surface, so blending with that same color keeps it solid.
        paint.color = color
        paint.alpha = SCRIM_ALPHA
        canvas.drawRect(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), appBarBottom.toFloat(), paint)
    }

    companion object {
        private const val SCRIM_ALPHA = 204
    }
}

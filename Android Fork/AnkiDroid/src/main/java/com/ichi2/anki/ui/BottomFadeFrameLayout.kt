// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.Window
import android.widget.FrameLayout
import androidx.activity.SystemBarStyle
import com.ichi2.anki.android.view.locationOnScreen
import com.ichi2.anki.compat.setDstOutBlendCompat
import com.ichi2.themes.Themes

/**
 * A container which emulates the 'fade' effect which is applied to content when underneath the
 * button navigation.
 *
 * Some Android versions have a transparent nav, which applied a transparency filter to elements
 * underneath it.
 * Different Android versions implement this differently, and the colors were non-public, this
 * class applies this effect in a standardized manner.
 *
 * @see anchorView
 *
 * BUG: This applies transparency twice on API <= 25 - not a huge deal.
 */
class BottomFadeFrameLayout
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : FrameLayout(context, attrs, defStyleAttr) {
        /** buffer used by [View.screenY] */
        private val locBuf = IntArray(2)

        /** The fade applies to this view, and all views below it vertically */
        var anchorView: View? = null
            set(value) {
                if (field !== value) {
                    field = value
                    invalidate()
                }
            }

        private val paint =
            Paint().apply {
                color = FADE_ALPHA shl 24
                setDstOutBlendCompat()
            }

        override fun dispatchDraw(canvas: Canvas) {
            val target = anchorView
            if (target == null || target.height == 0 || width == 0 || height == 0) {
                super.dispatchDraw(canvas)
                return
            }
            val targetTopInSelf = target.screenY - screenY
            val bandTop = (targetTopInSelf + target.paddingTop).toFloat()
            val bandBottom = height.toFloat()
            if (bandBottom <= bandTop) {
                super.dispatchDraw(canvas)
                return
            }
            // Draw children into a scratch buffer so the fade only affects them
            val saveCount = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
            super.dispatchDraw(canvas)
            canvas.drawRect(0f, bandTop, width.toFloat(), bandBottom, paint)
            canvas.restoreToCount(saveCount)
        }

        private inline val View.screenY: Int
            get() = locationOnScreen(locBuf).y

        /** Configures [window] for use with this view */
        fun setup(window: Window) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
        }

        companion object {
            /** Alpha removed from each child pixel inside the fade. */
            // 90% - standard Android, so "Studied in" isn't obscured when overlapping a deck name
            const val FADE_ALPHA: Int = 0xE6

            /** `navigationBarStyle` to pass to [androidx.activity.enableEdgeToEdge] */
            fun navigationBarStyle(): SystemBarStyle =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    SystemBarStyle.auto(
                        lightScrim = Color.TRANSPARENT,
                        darkScrim = Color.TRANSPARENT,
                    ) { Themes.isNightTheme }
                } else {
                    // Dark nav bar icons are not supported by the platform
                    // Maintain a dark nav, rather than just using fade
                    // androidx.activity.EdgeToEdge.DefaultDarkScrim
                    SystemBarStyle.dark(Color.argb(0x80, 0x1b, 0x1b, 0x1b))
                }
        }
    }

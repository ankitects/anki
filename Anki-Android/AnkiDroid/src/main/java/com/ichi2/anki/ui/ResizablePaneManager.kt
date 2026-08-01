/*
 *  Copyright (c) 2025 Hari Srinivasan <harisrini21@gmail.com>
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

package com.ichi2.anki.ui

import android.content.SharedPreferences
import android.view.MotionEvent
import android.view.PointerIcon
import android.view.View
import android.widget.LinearLayout
import androidx.core.content.edit
import com.ichi2.anki.R
import timber.log.Timber

/**
 * Helper class to manage resizable panes in a X-large layouts
 * Allows for dragging to resize panes and saves the pane states in SharedPreferences
 */
class ResizablePaneManager(
    private val parentLayout: LinearLayout,
    private val divider: View,
    private val leftPane: View,
    private val rightPane: View,
    private val sharedPrefs: SharedPreferences,
    private val leftPaneWeightKey: String,
    private val rightPaneWeightKey: String,
    private val minWeight: Float = 0.5f, // Minimum weight for each pane
    private val dragColor: Int = divider.context.getColor(R.color.drag_divider_color),
    private val idleColor: Int = divider.context.getColor(R.color.idle_divider_color),
) {
    init {
        setupResizableDivider()
    }

    private fun setupResizableDivider() {
        // Load saved weights if available
        loadSavedWeights()

        var initialTouchX = 0f
        var initialLeftWeight = 0f
        var initialRightWeight = 0f

        divider.setOnHoverListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_HOVER_ENTER -> {
                    divider.pointerIcon =
                        PointerIcon.getSystemIcon(
                            divider.context,
                            PointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW,
                        )
                    divider.setBackgroundColor(dragColor)
                    true
                }
                MotionEvent.ACTION_HOVER_EXIT -> {
                    divider.pointerIcon = null
                    divider.setBackgroundColor(idleColor)
                    true
                }
                else -> false
            }
        }

        divider.setOnTouchListener { v, event ->
            val leftParams = leftPane.layoutParams as LinearLayout.LayoutParams
            val rightParams = rightPane.layoutParams as LinearLayout.LayoutParams

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    /*
                        Request parent to not intercept touch events so that the divider does not get intercepted by
                        the parent layout, when Full screen navigation drawer setting is enabled
                     */
                    v.parent.requestDisallowInterceptTouchEvent(true)

                    v.setBackgroundColor(dragColor)
                    initialTouchX = event.rawX
                    initialLeftWeight = leftParams.weight
                    initialRightWeight = rightParams.weight
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    v.parent.requestDisallowInterceptTouchEvent(true)

                    val deltaX = event.rawX - initialTouchX
                    val totalParentWidth = parentLayout.width.toFloat()

                    if (totalParentWidth > 0) { // Avoid division by zero
                        val sumOfInitialWeights = initialLeftWeight + initialRightWeight

                        // Calculate the change in weight based on the drag distance
                        val weightDelta = (deltaX / totalParentWidth) * sumOfInitialWeights

                        var newLeftWeight = initialLeftWeight + weightDelta

                        // Clamp the new weight for the left pane
                        // Ensures it's not too small and not too large (leaving space for the other pane's minWeight)
                        newLeftWeight = newLeftWeight.coerceIn(minWeight, sumOfInitialWeights - minWeight)

                        val newRightWeight = sumOfInitialWeights - newLeftWeight

                        // Apply the new weights
                        leftParams.weight = newLeftWeight
                        rightParams.weight = newRightWeight

                        leftPane.layoutParams = leftParams
                        rightPane.layoutParams = rightParams

                        // Request layout update for the parent
                        parentLayout.requestLayout()
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.setBackgroundColor(idleColor)

                    // Save the new weights to SharedPreferences
                    sharedPrefs.edit {
                        putFloat(leftPaneWeightKey, leftParams.weight)
                        putFloat(rightPaneWeightKey, rightParams.weight)
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun loadSavedWeights() {
        try {
            val leftParams = leftPane.layoutParams as LinearLayout.LayoutParams
            val rightParams = rightPane.layoutParams as LinearLayout.LayoutParams

            // Load saved weights from SharedPreferences
            val savedLeftWeight = sharedPrefs.getFloat(leftPaneWeightKey, leftParams.weight)
            val savedRightWeight = sharedPrefs.getFloat(rightPaneWeightKey, rightParams.weight)

            // Apply the saved weights
            leftParams.weight = savedLeftWeight
            rightParams.weight = savedRightWeight

            leftPane.layoutParams = leftParams
            rightPane.layoutParams = rightParams

            // Request layout update for the parent
            parentLayout.requestLayout()
        } catch (e: Exception) {
            Timber.w(e, "Failed to load saved pane weights")
        }
    }
}

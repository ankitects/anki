/*
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.snackbar

import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.annotation.VisibleForTesting
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.marginLeft
import androidx.core.view.marginRight
import androidx.customview.widget.ViewDragHelper
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.common.android.appContext
import com.ichi2.utils.dp
import kotlin.math.absoluteValue
import kotlin.math.sign

/**
 * This is more a fix for the inconsistent behavior of `SwipeDismissBehavior`,
 * rather than a custom implementation. This addresses the following issues:
 *
 *   * When the snackbar is swiped to the right, its opacity changes,
 *     but not when swiped to the left;
 *
 *   * When moving the snackbar to dismiss it, the target distance calculation does not take
 *     the margins into account, which makes the snackbar briefly appear stuck near the edge;
 *
 *   * Any amount of horizontal velocity will dismiss the snackbar,
 *     which can result in user dismissing the snackbar even though they didn't want to;
 *
 *   * If you drag the snackbar to the right, and then flinging it to the left,
 *     it will suddenly change course and start moving to the right.
 */
open class SensibleSwipeDismissBehavior : BaseTransientBottomBar.Behavior() {
    private var viewDragHelper: ViewDragHelper? = null

    override fun onInterceptTouchEvent(
        parent: CoordinatorLayout,
        child: View,
        event: MotionEvent,
    ): Boolean {
        ensureViewDragHelper(parent)
        return viewDragHelper!!.shouldInterceptTouchEvent(event)
    }

    override fun onTouchEvent(
        parent: CoordinatorLayout,
        child: View,
        event: MotionEvent,
    ): Boolean {
        viewDragHelper?.processTouchEvent(event)
        return viewDragHelper != null
    }

    private fun ensureViewDragHelper(parent: ViewGroup) {
        if (viewDragHelper == null) {
            viewDragHelper = ViewDragHelper.create(parent, ViewDragHelperCallback())
        }
    }

    /** See [com.google.android.material.behavior.SwipeDismissBehavior.dragCallback] */
    inner class ViewDragHelperCallback : ViewDragHelper.Callback() {
        @VisibleForTesting var initialChildLeft = Int.MIN_VALUE

        override fun getViewHorizontalDragRange(child: View) = child.width

        override fun clampViewPositionHorizontal(
            child: View,
            left: Int,
            dx: Int,
        ) = left

        override fun clampViewPositionVertical(
            child: View,
            top: Int,
            dy: Int,
        ) = child.top

        override fun onViewDragStateChanged(state: Int) {
            listener?.onDragStateChanged(state)
        }

        override fun tryCaptureView(
            child: View,
            pointerId: Int,
        ) = child is Snackbar.SnackbarLayout

        override fun onViewCaptured(
            child: View,
            pointerId: Int,
        ) {
            if (initialChildLeft == Int.MIN_VALUE) {
                initialChildLeft = child.left
            }
            child.parent?.requestDisallowInterceptTouchEvent(true)
            child.startIgnoringExternalChangesOfHorizontalPosition()
        }

        override fun onViewReleased(
            child: View,
            xvel: Float,
            yvel: Float,
        ) {
            val dismiss = shouldDismiss(child, xvel)

            val targetChildLeft =
                when (dismiss) {
                    Dismiss.DoNotDismiss -> initialChildLeft
                    Dismiss.ToTheRight -> initialChildLeft + child.width + child.marginRight
                    Dismiss.ToTheLeft -> initialChildLeft - child.width - child.marginLeft
                }

            fun onViewSettled() {
                if (dismiss != Dismiss.DoNotDismiss) {
                    listener?.onDismiss(child)
                }
                child.stopIgnoringExternalChangesOfHorizontalPosition()
            }

            if (viewDragHelper?.settleCapturedViewAt(targetChildLeft, child.top) == true) {
                child.postOnAnimation(
                    object : Runnable {
                        override fun run() {
                            if (viewDragHelper?.continueSettling(true) == true) {
                                child.postOnAnimation(this)
                            } else {
                                onViewSettled()
                            }
                        }
                    },
                )
            } else {
                onViewSettled()
            }
        }

        /**
         * The finger was lifted off the snackbar, which may still have horizontal velocity.
         * This decides whether the snackbar should be dismissed.
         *
         *   * If current snackbar speed is high, dismiss to the direction of fling;
         *
         *   * If snackbar traveled a lot, and currently is either stationary,
         *     or is moving away from the original position,
         *     dismiss to the direction of the closest edge;
         *
         *   * Else do not dismiss, and return to original position.
         */
        @VisibleForTesting fun shouldDismiss(
            child: View,
            xvel: Float,
        ): Dismiss =
            if (xvel.absoluteValue > FLING_TO_DISMISS_SPEED_THRESHOLD) {
                if (xvel > 0f) Dismiss.ToTheRight else Dismiss.ToTheLeft
            } else {
                val distanceTraveled = child.left - initialChildLeft
                val distanceTraveledRatio = distanceTraveled.absoluteValue.toFloat() / child.width
                val shouldDismiss =
                    distanceTraveledRatio > DRAG_TO_DISMISS_DISTANCE_RATIO &&
                        (xvel == 0f || xvel.sign.toInt() == distanceTraveled.sign)
                when {
                    !shouldDismiss -> Dismiss.DoNotDismiss
                    distanceTraveled > 0 -> Dismiss.ToTheRight
                    else -> Dismiss.ToTheLeft
                }
            }

        /**
         * `CoordinatorLayout` may try to layout its children while the snackbar is settling,
         * for instance, if you touch other views after dragging and releasing the snackbar,
         * or if you try dragging a snackbar right after flicking the card list in Card browser.
         * This makes it briefly flicker in the original position--
         * especially if the alpha of the snackbar isn't changed.
         *
         * While this glitch is quite rare in practice, there's a straightforward workaround.
         * When such a layout event occurs, we are undoing any horizontal changes. There's probably
         * a more decent way of resolving this, but--again--this is an extremely rare glitch.
         */
        @Suppress("UNUSED_ANONYMOUS_PARAMETER") // just to make parameter list readable
        private val horizontalLayoutChangeUndoingLayoutChangeListener =
            View.OnLayoutChangeListener { view, newLeft, newTop, newRight, newBottom, oldLeft, oldTop, oldRight, oldBottom ->
                if (newLeft != oldLeft && newLeft == initialChildLeft) {
                    view.layout(oldLeft, newTop, oldRight, newBottom)
                }
            }

        private fun View.startIgnoringExternalChangesOfHorizontalPosition() {
            addOnLayoutChangeListener(horizontalLayoutChangeUndoingLayoutChangeListener)
        }

        private fun View.stopIgnoringExternalChangesOfHorizontalPosition() {
            removeOnLayoutChangeListener(horizontalLayoutChangeUndoingLayoutChangeListener)
        }
    }
}

@VisibleForTesting enum class Dismiss { DoNotDismiss, ToTheLeft, ToTheRight }

private val FLING_TO_DISMISS_SPEED_THRESHOLD = 1000.dp.toPx(appContext)

private const val DRAG_TO_DISMISS_DISTANCE_RATIO = .5f

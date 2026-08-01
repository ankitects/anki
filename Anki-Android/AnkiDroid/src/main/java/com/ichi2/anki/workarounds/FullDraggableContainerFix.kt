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

package com.ichi2.anki.workarounds

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.MotionEvent.ACTION_DOWN
import com.drakeet.drawer.FullDraggableContainer

/**
 * [FullDraggableContainer] is the thing that allows opening the navigation drawer
 * by swiping right anywhere on the screen.
 * It has an issue in that it will intercept all rightwards swipes,
 * even if a view inside it also wants these events. For instance,
 *
 *   * You can't swipe snackbars to the right to dismiss them, or
 *
 *   * You can't drag card browser search field cursor by dragging finger right across the field.
 *     (Dragging the finger left, and dragging the cursor *handle* right works, however.)
 *
 * The problem is twofold;
 *
 *   * The container does not listen for children asking it not to intercept touch, and
 *
 *   * The container eats up the motion events, not passing them to children in the first place.
 *
 * This solves the issue in the following way:
 *
 *   * When the container wants to intercept motion events, we skip *this one* interception event,
 *     allowing it to propagate to children.
 *
 *   * The child, which hopefully has a similar threshold for catching motion events,
 *     will capture the view, and request its parent to not intercept touch,
 *     which will propagate to us as well.
 *
 *   * We then skip all events until the child removes the request
 *     (it actually does not do that in practice),
 *     or when we receive another finger press, which signifies a new gesture.
 *
 * (It may seem that it would be possible to solve this by having a larger interception distance
 * on parent than on child. In practice, however, since the devices are slow and fingers are fast,
 * the interception usually happens on the second or the first motion event,
 * and finger travel distance is usually much higher than the usual threshold of about 8 dp.)
 */

class FullDraggableContainerFix(
    context: Context,
    attrs: AttributeSet? = null,
) : FullDraggableContainer(context, attrs) {
    private var childRequestedNoTouchInterception = false
    private var lastWeWantToIntercept = false

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        super.requestDisallowInterceptTouchEvent(disallowIntercept)
        childRequestedNoTouchInterception = disallowIntercept
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (childRequestedNoTouchInterception) {
            if (event.actionMasked == ACTION_DOWN) {
                childRequestedNoTouchInterception = false
            } else {
                return false
            }
        }

        val weWantToIntercept = super.onInterceptTouchEvent(event)
        val shouldSkipThisInterception = weWantToIntercept && !lastWeWantToIntercept
        lastWeWantToIntercept = weWantToIntercept
        return if (shouldSkipThisInterception) false else weWantToIntercept
    }
}

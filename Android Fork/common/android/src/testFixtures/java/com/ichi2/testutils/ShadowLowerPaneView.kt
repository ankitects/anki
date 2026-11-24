// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import android.graphics.Rect
import android.view.ViewGroup
import android.view.WindowManager
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.RealObject
import org.robolectric.shadows.ShadowViewGroup

/** Reports dialog frames in screen coordinates for a 400px pane below a 440px top pane. */
@Implements(ViewGroup::class)
class ShadowLowerPaneView : ShadowViewGroup() {
    @RealObject
    private lateinit var view: ViewGroup

    @Implementation
    override fun getWindowVisibleDisplayFrame(outRect: Rect) {
        val type = (view.rootView.layoutParams as? WindowManager.LayoutParams)?.type
        if (type == WindowManager.LayoutParams.TYPE_APPLICATION) {
            outRect.set(0, 440, 411, 840)
        } else {
            super.getWindowVisibleDisplayFrame(outRect)
        }
    }
}

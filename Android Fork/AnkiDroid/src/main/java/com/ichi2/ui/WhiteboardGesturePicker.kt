// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.ui

import android.content.Context
import android.util.AttributeSet
import androidx.core.view.isVisible
import com.ichi2.anki.cardviewer.Gesture
import com.ichi2.ui.GestureDisplay.Companion.MULTI_FINGER_GESTURES

class WhiteboardGesturePicker(
    ctx: Context,
    attributeSet: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : GesturePicker(ctx, attributeSet, defStyleAttr) {
    init {
        binding.gestureDisplay.isVisible = false
    }

    override fun availableGestures(): List<Gesture> = MULTI_FINGER_GESTURES + Gesture.SHAKE
}

// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import android.view.View
import android.view.inputmethod.InputMethodManager
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowInputMethodManager

/** Records input restart requests, which the default Robolectric shadow ignores. */
@Implements(InputMethodManager::class)
class RecordingInputMethodManager : ShadowInputMethodManager() {
    val restartedViews = mutableListOf<View>()

    @Implementation
    override fun restartInput(view: View) {
        restartedViews.add(view)
        super.restartInput(view)
    }
}

// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.compat

import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.annotation.RequiresApi

@RequiresApi(36)
open class CompatV36 : CompatV34() {
    // From API36, SHOW_IMPLICIT is a no-op (and deprecated in API37)
    override fun showSoftInput(view: View): Boolean {
        val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        return imm.showSoftInput(view, 0)
    }
}

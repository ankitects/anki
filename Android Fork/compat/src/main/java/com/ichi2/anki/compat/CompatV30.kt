// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Cranberry Platypus <cranberryplatypus968@gmail.com>

package com.ichi2.anki.compat

import android.view.Window
import android.view.WindowInsets.Type.ime
import android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
import android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
import androidx.annotation.RequiresApi
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat.Type.statusBars
import com.ichi2.anki.common.utils.ext.setBitFlag

@RequiresApi(30)
@Suppress("ktlint:standard:property-naming")
open class CompatV30 : CompatV29() {
    override fun setDialogKeyboardResize(
        window: Window,
        resize: Boolean,
    ) {
        window.attributes =
            window.attributes.apply {
                fitInsetsTypes = fitInsetsTypes.setBitFlag(ime(), enabled = resize)
            }
        // ADJUST_NOTHING => disable legacy adjustment and let the insets handle the work
        window.setSoftInputAdjustment(if (resize) SOFT_INPUT_ADJUST_NOTHING else SOFT_INPUT_ADJUST_PAN)
    }

    // As of API30, insetsController is the correct way to hide the status bar
    @Suppress("SENSELESS_COMPARISON", "FoldInitializerAndIfToElvis")
    override fun hideStatusBar(window: Window) {
        if (window == null) {
            return
        }
        val view = window.decorView
        // Despite the type listed for window.decorView, it can, in fact, be null.
        if (view == null) {
            return
        }
        WindowCompat.getInsetsController(window, view).hide(statusBars())
    }
}

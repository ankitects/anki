// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Cranberry Platypus <cranberryplatypus968@gmail.com>

package com.ichi2.anki.compat

import android.view.Window
import androidx.annotation.RequiresApi
import androidx.core.view.WindowInsetsCompat

@RequiresApi(30)
@Suppress("ktlint:standard:property-naming")
open class CompatV30 : CompatV29() {
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
        val controller = view.windowInsetsController
        controller?.hide(WindowInsetsCompat.Type.statusBars())
    }
}

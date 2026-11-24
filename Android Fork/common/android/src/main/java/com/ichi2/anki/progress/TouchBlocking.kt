// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.progress

import android.view.Window
import android.view.WindowManager
import java.util.WeakHashMap

private val touchBlockCounts = WeakHashMap<Window, Int>()

fun Window.blockTouches() {
    val count = touchBlockCounts[this] ?: 0
    if (count == 0) addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
    touchBlockCounts[this] = count + 1
}

fun Window.unblockTouches() {
    val count = touchBlockCounts[this] ?: return
    if (count > 1) {
        touchBlockCounts[this] = count - 1
        return
    }
    touchBlockCounts.remove(this)
    clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
}

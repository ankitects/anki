// SPDX-License-Identifier: GPL-3.0-or-later

@file:Suppress("PackageDirectoryMismatch")
@file:CoverageOptIn

package com.google.android.material.snackbar

import com.ichi2.anki.common.annotations.CoverageOptIn

/*
 * This only exists so that we can call onAttachedToWindow, which is package-private.
 */
fun Snackbar.onAttachedToWindow2() {
    onAttachedToWindow()
}

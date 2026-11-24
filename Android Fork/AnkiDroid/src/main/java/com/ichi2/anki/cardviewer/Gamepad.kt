// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.cardviewer

import android.view.KeyEvent
import android.webkit.WebView

/**
 * Pass gamepad buttons to the WebView, after which `navigator.getGamepads()` displays the gamepad
 *
 * Issue 14975
 *
 * @return
 * * `false` if the provided [WebView] is null
 * * `false` if [keyCode] is NOT a gamepad button
 * * `webView.onKeyUp(keyCode, keyEvent)` otherwise
 */
fun WebView?.handledGamepadKeyUp(
    keyCode: Int,
    keyEvent: KeyEvent,
): Boolean {
    if (this == null) return false
    if (!KeyEvent.isGamepadButton(keyCode)) return false
    return this.onKeyUp(keyCode, keyEvent)
}

/**
 * Pass gamepad buttons to the WebView, after which `navigator.getGamepads()` displays the gamepad
 *
 * Issue 14975
 *
 * @return
 * * `false` if the provided [WebView] is null
 * * `false` if [keyCode] is NOT a gamepad button
 * * `webView.onKeyDown(keyCode, keyEvent)` otherwise
 */
fun WebView?.handledGamepadKeyDown(
    keyCode: Int,
    keyEvent: KeyEvent,
): Boolean {
    if (this == null) return false
    if (!KeyEvent.isGamepadButton(keyCode)) return false
    return this.onKeyDown(keyCode, keyEvent)
}

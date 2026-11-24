// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.utils

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import com.ichi2.anki.compat.showSoftInput

object AndroidUiUtils {
    /**
     * Shows the soft keyboard for the current focused view in the activity.
     *
     * It's a good practice to ensure that the `currentFocus` is an
     * appropriate input field (e.g., EditText) before calling this function
     */
    fun Activity?.showSoftInput() {
        val currentFocus = this?.currentFocus ?: return
        currentFocus.showSoftInput()
    }

    /**
     * Focuses on View and opens the soft keyboard.
     * @param view The View which requires the focus to be set (typically an EditText).
     * @param runnable The Optional Runnable that will be executed at the end.
     */
    fun setFocusAndOpenKeyboard(
        view: View,
        runnable: Runnable? = null,
    ) {
        //  Required on some Android 9, 10 devices to show keyboard: https://stackoverflow.com/a/7784904
        view.postDelayed({
            view.requestFocus()
            view.showSoftInput()
            runnable?.run()
        }, 200)
    }

    /**
     * Sends a request to hide the soft keyboard for this view.
     */
    fun View.hideKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(windowToken, 0)
    }
}

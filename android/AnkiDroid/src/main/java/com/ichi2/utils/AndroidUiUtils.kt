/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.utils

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager

object AndroidUiUtils {
    /**
     * Shows the soft keyboard for the current focused view in the activity.
     *
     * It's a good practice to ensure that the `currentFocus` is an
     * appropriate input field (e.g., EditText) before calling this function
     */
    fun Activity?.showSoftInput() {
        val currentFocus = this?.currentFocus ?: return
        val imm = this.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(currentFocus, InputMethodManager.SHOW_IMPLICIT)
    }

    /**
     * This method is used for setting the focus on an EditText which is used in a dialog
     * and for opening the keyboard.
     * @param view The EditText which requires the focus to be set.
     * @param window The window where the view is present.
     */
    fun setFocusAndOpenKeyboard(
        view: View,
        window: Window,
    ) {
        view.requestFocus()
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
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
            val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
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

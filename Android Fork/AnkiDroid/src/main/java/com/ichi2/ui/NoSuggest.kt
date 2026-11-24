// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.ui

import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection

/**
 * Disable suggestions using TYPE_NULL, following the Reword app's approach (Issue 10352).
 * Other suggestion settings did not work with Gboard, and password input types could
 * prompt the password manager.
 *
 * Apply this after creating the input connection. Only [outAttrs] is changed, preserving the
 * view's cursor, input connection, language hints and Done action.
 */
internal fun InputConnection?.applyNoSuggest(
    outAttrs: EditorInfo,
    noSuggest: Boolean,
): InputConnection? =
    also { connection ->
        if (connection != null && noSuggest) outAttrs.inputType = InputType.TYPE_NULL
    }

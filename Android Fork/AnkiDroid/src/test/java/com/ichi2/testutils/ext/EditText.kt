// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils.ext

import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.EditText
import kotlin.test.assertNotNull

/** Create an input connection and populate [editorInfo], failing if the field cannot provide one. */
fun EditText.createInputConnection(editorInfo: EditorInfo = EditorInfo()): InputConnection =
    assertNotNull(onCreateInputConnection(editorInfo), "${javaClass.simpleName} must provide an input connection")

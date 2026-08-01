// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki.common.utils.android

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes

fun showThemedToast(
    context: Context,
    text: String,
    shortLength: Boolean,
) {
    Toast.makeText(context, text, if (shortLength) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
}

fun showThemedToast(
    context: Context,
    text: CharSequence,
    shortLength: Boolean,
) {
    showThemedToast(context, text.toString(), shortLength)
}

fun showThemedToast(
    context: Context,
    @StringRes textResource: Int,
    shortLength: Boolean,
) {
    Toast.makeText(context, textResource, if (shortLength) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
}

// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.utils

import android.app.Dialog
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.view.ViewGroup
import android.widget.Spinner

object UiUtil {
    fun makeBold(s: String): Spannable {
        val str = SpannableStringBuilder(s)
        str.setSpan(StyleSpan(Typeface.BOLD), 0, s.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        return str
    }

    /** Returns [string] as a [Spannable] with an underline applied. */
    // BUG: this may not work if the user's system font does not support underlines.
    // Reported as an issue with zFont
    fun underline(string: String): Spannable =
        SpannableString(string).apply {
            setSpan(UnderlineSpan(), 0, length, 0)
        }

    fun Spinner.setSelectedValue(value: Any?) {
        for (position in 0 until this.adapter.count) {
            if (this.adapter.getItem(position) != value) continue
            this.setSelection(position)
            return
        }
    }

    fun Dialog.makeFullscreen() {
        window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
    }
}

/**
 * Appends [strings] to the builder, separated by [separator]. Only the strings are bolded
 */
fun SpannableStringBuilder.boldList(
    strings: List<String>,
    separator: String,
): SpannableStringBuilder {
    var isFirst = true
    for (element in strings) {
        if (!isFirst) append(separator)
        appendBold(element)
        isFirst = false
    }
    return this
}

/**
 * Appends [text] in bold to the receiver
 */
fun SpannableStringBuilder.appendBold(text: String): SpannableStringBuilder {
    val start = length
    append(text)
    setSpan(StyleSpan(Typeface.BOLD), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    return this
}

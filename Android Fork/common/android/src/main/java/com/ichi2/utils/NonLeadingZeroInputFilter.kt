// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.utils

import android.text.InputFilter
import android.text.Spanned

object NonLeadingZeroInputFilter : InputFilter {
    override fun filter(
        source: CharSequence,
        start: Int,
        end: Int,
        dest: Spanned,
        dstart: Int,
        dend: Int,
    ): CharSequence? {
        val result =
            StringBuilder(dest)
                .replace(dstart, dend, source.subSequence(start, end).toString())
        return if (result.length > 1 && result.startsWith("0")) "" else null
    }
}

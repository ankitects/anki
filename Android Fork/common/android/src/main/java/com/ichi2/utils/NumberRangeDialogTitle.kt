// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.utils

import android.content.Context
import android.util.TypedValue
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.TextViewCompat
import com.google.android.material.textview.MaterialTextView
import com.ichi2.anki.common.utils.dp
import com.google.android.material.R as MaterialR

/** Preserve the title appearance of number range preferences, including the host theme. */
fun AlertDialog.Builder.numberRangeTitle(
    title: CharSequence?,
    hostContext: Context,
): AlertDialog.Builder {
    if (title.isNullOrEmpty()) return this
    val tv =
        MaterialTextView(hostContext).apply {
            text = title
            setPaddingRelative(start = 24.dp, top = 20.dp, end = 24.dp, bottom = 8.dp)
            val outValue = TypedValue()
            val hasStyle = context.theme.resolveAttribute(MaterialR.attr.materialAlertDialogTitleTextStyle, outValue, true)
            val styleRes = if (hasStyle) outValue.resourceId else 0
            if (styleRes != 0) {
                TextViewCompat.setTextAppearance(this, styleRes)
            } else {
                TextViewCompat.setTextAppearance(this, MaterialR.style.TextAppearance_Material3_HeadlineSmall)
            }
        }
    return setCustomTitle(tv)
}

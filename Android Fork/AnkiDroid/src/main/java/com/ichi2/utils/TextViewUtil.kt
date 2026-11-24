// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.utils

import android.os.Build
import android.util.TypedValue
import android.widget.TextView

object TextViewUtil {
    fun getTextSizeSp(first: TextView): Float =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            TypedValue.deriveDimension(TypedValue.COMPLEX_UNIT_SP, first.textSize, first.resources.displayMetrics)
        } else {
            @Suppress("DEPRECATION", "remove this branch and collapse conditional when minSdkVersion is >= 34")
            first.textSize / first.resources.displayMetrics.scaledDensity
        }
}

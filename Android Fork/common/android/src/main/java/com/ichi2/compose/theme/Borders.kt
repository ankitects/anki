// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.content.withStyledAttributes
import com.ichi2.anki.common.android.R

/** The XML theme's widget outline; e-ink uses it to distinguish white surfaces. */
val MaterialTheme.widgetBorder: BorderStroke?
    @Composable
    get() {
        val context = LocalContext.current
        val density = LocalDensity.current
        return remember(context, density) {
            var border: BorderStroke? = null
            context.withStyledAttributes(attrs = intArrayOf(R.attr.widgetStrokeWidth, R.attr.widgetStrokeColor)) {
                val width = with(density) { getDimension(0, 0f).toDp() }
                if (width > 0.dp) {
                    border = BorderStroke(width, Color(getColor(1, android.graphics.Color.TRANSPARENT)))
                }
            }
            border
        }
    }

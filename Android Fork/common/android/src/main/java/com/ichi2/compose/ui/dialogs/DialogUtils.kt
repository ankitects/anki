// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.ui.dialogs

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.appcompat.app.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.Dp
import androidx.core.content.res.use
import androidx.appcompat.R as AppCompatR

/** Resolve the View dialog's themed minimum width before window insets are consumed. */
@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun alertDialogWidth(): Dp {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val resources = LocalResources.current
    // Re-read on rotation or resizing: the theme has orientation and screen size variants.
    return remember(context, configuration, density, resources) {
        val metrics = resources.displayMetrics
        // DecorView resolves fractions against the configured screen width. Compose's
        // available width can already exclude a landscape cutout or system bar.
        val availableWidth = configuration.screenWidthDp * metrics.density
        // The width attributes come from the alert dialog overlay rather than the Activity theme.
        val dialogTheme = TypedValue()
        context.theme.resolveAttribute(AppCompatR.attr.alertDialogTheme, dialogTheme, true)
        context.obtainStyledAttributes(dialogTheme.resourceId, intArrayOf(configuration.alertDialogMinWidthAttribute)).use {
            val value = it.peekValue(0)
            val pixels =
                when (value?.type) {
                    TypedValue.TYPE_DIMENSION -> value.getDimension(metrics)
                    TypedValue.TYPE_FRACTION -> value.getFraction(availableWidth, availableWidth)
                    else -> availableWidth
                }
            // DecorView truncates to whole pixels before measuring the dialog.
            with(density) { pixels.toInt().toDp() }
        }
    }
}

/** The View dialog's shadow elevation, including the zero-elevation e-ink theme. */
@Composable
fun ankiDroidDialogElevation(): Dp {
    val context = LocalContext.current
    val density = LocalDensity.current
    return remember(context, density) {
        AlertDialog.Builder(context).context.obtainStyledAttributes(intArrayOf(android.R.attr.windowElevation)).use {
            with(density) { it.getDimension(0, 0f).toDp() }
        }
    }
}

@get:AttrRes
private val Configuration.alertDialogMinWidthAttribute: Int
    get() =
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            android.R.attr.windowMinWidthMajor
        } else {
            android.R.attr.windowMinWidthMinor
        }

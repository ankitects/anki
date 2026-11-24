// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.widget

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.widget.RemoteViews
import androidx.core.os.BundleCompat

/**
 * @return An [AppWidgetManager] for the provided context, or `null`
 *
 * @see AppWidgetManager.getInstance
 */

@SuppressLint("DirectAppWidgetManagerInstanceUsage")
fun getAppWidgetManager(context: Context): AppWidgetManager? {
    // The call returns null on a Supernote A5X, but as the underlying platform call is in Java,
    // the result is assumed to be non-null in Kotlin
    return AppWidgetManager.getInstance(context)
}

/** Builds the widget's views once for each size the launcher may show it at, from its [options]. */
inline fun sizedRemoteViews(
    options: Bundle,
    build: (SizeF) -> RemoteViews,
): RemoteViews {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val sizes = BundleCompat.getParcelableArrayList(options, AppWidgetManager.OPTION_APPWIDGET_SIZES, SizeF::class.java)
        if (!sizes.isNullOrEmpty()) {
            return RemoteViews(sizes.associateWith { build(it) })
        }
    }
    // Older launchers report a size range: landscape is shorter, portrait is taller.
    return RemoteViews(
        build(
            SizeF(
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH).toFloat(),
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT).toFloat(),
            ),
        ),
        build(
            SizeF(
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).toFloat(),
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).toFloat(),
            ),
        ),
    )
}

// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.widget

import android.appwidget.AppWidgetManager
import android.content.Intent

fun widgetUpdateIntent(appWidgetId: Int = 1) =
    Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
    }
